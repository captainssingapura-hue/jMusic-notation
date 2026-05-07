# Tier 0 — Onset Grouping

> **Always-on. Coalesces simultaneously-struck notes into a single chord
> event. Strictly speaking, not voice separation — but every other tier
> assumes its input has been through this step.**

---

## Problem it solves

A MIDI editor that lets a user enter a chord usually emits one
`note-on` event per pitch, all at the same tick:

```
tick=480  note-on  C4
tick=480  note-on  E4
tick=480  note-on  G4
tick=720  note-off C4
tick=720  note-off E4
tick=720  note-off G4
```

Three events, but one *intentional* musical object: a C-major
triad held for one beat. Treating these as three separate voices
would corrupt every tier above. So before any separation work
happens, we **fold same-tick same-duration notes into a single
chord event** — `PolyPitchNode` in our model.

## Algorithm sketch

```
input:  notes = list of (onset_tick, duration_ticks, pitch), sorted by onset
output: events = list of single-pitch or chord events

i = 0
while i < len(notes):
    n = notes[i]
    chord = [n.pitch]
    j = i + 1
    while j < len(notes)
          and notes[j].onset_tick == n.onset_tick
          and notes[j].duration_ticks == n.duration_ticks:
        chord.append(notes[j].pitch)
        j += 1
    if len(chord) == 1:
        events.append(SingleNote(n.onset_tick, n.duration_ticks, n.pitch))
    else:
        events.append(Chord(n.onset_tick, n.duration_ticks, sorted(chord)))
    i = j
```

The `duration_ticks` equality check is important: two notes that
start together but have *different* durations are not a chord —
they're two voices that happen to attack at the same moment, and
should be left for the higher tiers to handle.

## Diagram

Before grouping (raw MIDI):

```
pitch
G4   ─┐
     ████
E4   ─┐
     ████
C4   ─┐
     ████
     │      │
   tick   tick
   480    720
```

Three notes, three events.

After Tier 0:

```
pitch
G4 ┐
E4 │── one PolyPitchNode { pitches=[C4,E4,G4], duration=quarter }
C4 ┘
   │
 tick 480
```

One event, three pitches.

## Why it works

- **Authoring tools always emit chords this way.** Every score
  editor (MuseScore, Sibelius, Finale, Logic, Cubase) writes a
  chord as N simultaneous `note-on`s with identical durations.
  Tier 0 simply reverses the lossy MIDI encoding.

- **Trivially correct when the duration check matches.** If three
  notes share onset *and* duration, there's no plausible reading
  in which they're separate voices.

- **No false positives.** When the duration check fails, the notes
  are passed through unchanged for higher tiers to interpret.

## Where it fails (corner cases)

| Situation | What Tier 0 does | What's actually intended |
|---|---|---|
| Same onset, slightly different durations from human performance (within ~5 ms) | Treats as separate notes | Probably a chord; needs jitter tolerance |
| Hand-rolled "broken chord" deliberately spread across 3 ticks | Treats as 3 separate notes | Correct — they *are* separate articulations |
| Pedal-sustained chord where notes are released at slightly different times | Treats as separate notes | Usually fine — the release times don't carry musical meaning |

The first case suggests adding a `JITTER_TICKS` tolerance (e.g., 10
ticks at PPQ 480 ≈ 20 ms). For score-derived MIDI it can be 0; for
performance MIDI, ≈ 1/64 of a beat is a safe default.

## Implementation notes

Lives in `notation-performance/.../OnsetGrouper.java`. Pure
function over `List<PitchedNote>`:

```java
public final class OnsetGrouper {
    public record GroupedEvent(long onsetTick, long durationTicks, List<Integer> pitches) {}

    public static List<GroupedEvent> group(List<PitchedNote> notes, int jitterTicks) { ... }
}
```

- Input must be sorted by `onsetTick`.
- Returns events sorted by onset; pitches inside each event sorted ascending.
- `jitterTicks = 0` for score-derived MIDI; tunable per import.

## Test cases

```
1.  three same-onset same-duration notes → one chord of 3 pitches
2.  three same-onset different-duration notes → three separate notes
3.  zero notes → empty list
4.  one note → one single-pitch event
5.  jitterTicks=10, two notes at ticks 100 and 105 with same duration → one chord
6.  jitterTicks=0, same input → two separate notes
7.  pre-existing chord (already a single record) passes through untouched
```

## Status in this codebase

Already done partially: `MidiExtractor` (line 277–283) coalesces
same-tick notes into chords for its text emit. The logic is
**not yet extracted** as a reusable function. Step 1 of the
unification plan didn't touch it; step 2 will need to.
