# Restoring Aux Voices — Design Note (Future Reference)

## Context

Phase 4d cutover finished with `Bar(expectedSixtyFourths, nodes)` —
single-voice bars only. The legacy aux-voice feature (`.aux(a -> ...)` on
`BarBuilderTyped`) was preserved at the *builder* surface but no longer
populates anything; the resulting overlay content is dropped before it
reaches the new `Phrase` tree.

Many songs need it back: TianHeiHei Main2 had `.aux(a -> a.o5(HALF,C)
.o4(QUARTER,G).o5(QUARTER,C))`, InternationaleTracks chord phrase had
~33 aux calls, etc. This doc captures the agreed plan for re-introducing
aux voices when we get to it.

## Recommended approach: polyphonic Bar (Option A)

`Bar` gains an aux-voices field. Each aux voice is a list of phrase
nodes that sums to the bar's `expectedSixtyFourths`. Voice 0 is the
existing primary line; voices 1..N are parallel.

```java
public record Bar(
        int expectedSixtyFourths,
        List<PhraseNode> nodes,                  // primary voice
        List<List<PhraseNode>> auxVoices         // 0..N parallel voices
) {
    public Bar {
        // validate primary AND each aux voice sums to expectedSixtyFourths
    }

    public static Bar of(int sf, PhraseNode... nodes) {
        return new Bar(sf, List.of(nodes), List.of());
    }
}
```

Walker shape (same template for `PieceConcretizer`, `PitchScrollData`,
`TUIPianoRoll`):

```java
for (Bar bar : track.bars()) {
    long barStart = tick;
    for (PhraseNode node : bar.nodes()) tick = walkNode(node, tick, voice=0);
    long barEnd = tick;
    for (int v = 0; v < bar.auxVoices().size(); v++) {
        tick = barStart;
        for (PhraseNode node : bar.auxVoices().get(v)) tick = walkNode(node, tick, voice=v+1);
    }
    tick = barEnd;
}
```

`NoteRect` already carries a `voice` field — just plumb the index back
through. TUI's `Hit` does too.

## Validation rules

- Each aux voice must sum to exactly `expectedSixtyFourths`. **Fail loud**
  on mismatch — composers explicitly `.pad(...)` if needed.
- Empty `auxVoices == []` is the default; existing code untouched.

## Elision interaction

When `JoinedPhrase` merges `[last][middle_gap][first]` into one bar at
an `ELIDED` boundary, what happens to aux voices?

Two options:
1. **Drop aux voices in merged bars** (simple, lossy).
2. **Merge aux voices the same way** (recurse: each aux voice gets
   its own `[audible_last_v][middle_gap][audible_first_v]` layout —
   walker treats per-voice trail/lead pad symmetrically).

**Start with option 1** (drop). Document the limitation. Upgrade to
option 2 only if a real song needs polyphony preserved across an
elision boundary.

## Implementation steps

1. **Phase 4e.1** — extend `Bar` with `auxVoices` field + validation;
   update `Bar.of(...)` factory; existing Bars use `auxVoices = []`
   (no behavioural change). Reactor stays green.
2. **Phase 4e.2** — wire `BarBuilderTyped.aux(...)` to populate
   `auxVoices` on the constructed Bar. The kept `AuxBarBuilderTyped`
   already produces a node list; route it into the new field instead
   of dropping it.
3. **Phase 4e.3** — update `PieceConcretizer.walkTrack` to iterate
   aux voices with the bar-start tick rewind shown above.
4. **Phase 4e.4** — same update in `PitchScrollData.extractNoteRects`
   and `TUIPianoRoll.walkTrack`. Plumb the voice index through to
   `NoteRect.voice` and `Hit.voice`.
5. **Phase 4e.5** — re-enable `.aux(...)` in
   `PianoTianHeiHei.buildMelodyMain2` and re-add the song aux content.
   Verify visually in UI and via `TUIPianoRoll` test.
6. **Phase 4e.6** — when songs are regenerated post-Phase-4d cutover,
   their aux content survives migration end-to-end.

## Tests to add

- `BarAuxVoicesTest` (`notation-core`): construction validates aux
  voice sums; rejects mismatched sums.
- `PieceConcretizerAuxTest` (`notation-play`): two-voice bar emits
  notes for both voices at the right ticks; voices play simultaneously
  with independent durations.
- `TUIPianoRollAuxTest` (`notation-play`): aux voice notes appear at
  bar-start positions in the rendered grid; voice index distinguishes
  them.
- `JoinedPhraseElisionAuxTest` (`notation-core`): elision merge with
  aux voices on the merged bar — locks the chosen option (drop vs
  merge).

## Alternatives considered (rejected)

- **Option B — sibling tracks via `Track.auxTracks`**: too coarse;
  forces full-length parallel content for what's often single-bar aux.
- **Option C — `ConnectingMode.PARALLEL`**: requires matching bar
  counts/sizes between children; conceptually mixes time-sequence
  semantics (ATTACCA/ELIDED) with parallelism. Awkward.
- **Option D — `AbstractNote` with absolute onset**: cleanest
  long-term shape but a Phase-5-scale refactor. Revisit if the model
  is under heavy iteration.

Option B remains complementary — useful for genuine always-on
parallel-instrument cases (e.g. a backing harp playing through the
whole piece). Different use case from per-bar aux.
