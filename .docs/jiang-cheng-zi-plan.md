# Implement *Jiang Cheng Zi · Buying a Watermelon* — Blues Arrangement

> **Status: Plan only. No phase implemented.**
> Updated against the current codebase — the pitch-expression model
> is the long pole; everything else builds on infrastructure that
> already exists ([roadmap.md](roadmap.md), [velocity-model-plan.md](velocity-model-plan.md),
> [auto-pedal.md](auto-pedal.md)).

---

## Context

Source: `R:\Music_works\Lyrics\gua\JiangChengZi_Watermelon_Blues_Arrangement_EN.md` —
a fully-arranged 3-stanza Chinese ci poem set as a slow E-minor electric
blues. 12-bar / 8-bar / 12-bar form, 12/8 meter, multiple instrument tracks
(lead electric guitar with overdrive, acoustic rhythm guitar, harmonica, bass,
brushed drums, Hammond organ pad, piano, vocal + female backing). Six
specific guitar licks notated in TAB.

Closest existing reference: `BluesZaiNaYaoYuan` (`notation-songs/.../folk/zainayaoyuan/`).
Same form-family, similar instrumentation — a structural template for *Jiang
Cheng Zi*, not a substitute for the licks/bends/tempo curve that give this
arrangement its character.

### Pivotal infrastructure decisions

The four authoring choices are unchanged from the original plan:

- **Module**: bootstrap `notation-music-china-folk` as its own Maven module
  per [`collections-architecture.md`](collections-architecture.md); migrate
  the existing five China-folk pieces into it.
- **Vocal melody**: skip in v1, piano carries the melodic line.
- **Tempo**: needs variable-tempo expression (slow → tighter → slowest).
- **Expression**: needs pitch-bend / glissando / vibrato so the licks don't
  collapse to discrete pitches.

---

## What's already in place — what each phase can lean on

The recent expressivity work (velocity side-channel, tempo-aware codec,
module-split mechanics) reduces the original plan's surface area:

| Existing capability | Phase that benefits |
|---|---|
| `notation-expressivity` module + side-channel doctrine | **Phase A** — `PitchBend`/`Vibrato` should ship as side-channels here, not as record-field extensions. Re-decided below. |
| `TempoTrack` + `TempoConversion` (Performance-layer tempo) | **Phase B** — the new `TempoArrangement` (Piece-layer, bar-anchored) compiles down to `TempoTrack` for the codec. |
| Module-split procedure (proved by `notation-expressivity`) | **Phase C** — bootstrap is now a checklist, not exploratory. |
| Auto-pedal V2 + AutoVelocity + slot-velocity drum strategies | **Phase D** — Piano LH gets bass-aware auto-pedal automatically; brushed-drum approximation via slot velocities is workable. |

---

## Phasing — ordered prerequisites then authoring

Four phases, executable in strict order. Each ships green tests; downstream
phases depend on the upstream model.

### Phase A — Pitch expression (revised: side-channel)

**Design pivot from the original plan.** The original called for an
`OrnamentedNote(MelodicNote inner, PitchExpression expr)` wrapper PhraseNode.
Now that the side-channel doctrine has been validated by `Velocities`, it's
the natural shape here too: `PitchBend` and friends become per-track sparse
timelines parallel to `Velocities`, keyed by `TrackId` + `tickMs`. No
`PhraseNode` change at all.

**Trade-off**: with the wrapper approach, an author writes
`o5(EIGHTH, B).bend(2).vibrato()` and the bend is owned by *that note*. With
side-channels, the bend is at a `(track, tickMs)` — bound to a moment, not
to a note. For typical guitar bends ("the B-natural at this onset bends up
to C#"), the moment-binding is fine — there's only one note at that onset
on the lead track. Polyphonic bend cases (chord-with-one-string-bent) are
rare in this arrangement and out of scope.

**Scope**:

- New side-channel records in `notation-expressivity`:
  - `PitchBendChange(long tickMs, int semitones, BendShape shape)` —
    `BendShape` ∈ `{HOLD, RAMP_FROM_ZERO, RAMP_TO_ZERO, PRE_BEND_RELEASE}`
  - `PitchBendControl(List<PitchBendChange>)` — sparse, sorted, dedup'd
  - `PitchBends(Map<TrackId, PitchBendControl>)`
  - `VibratoRegion(long startMs, long endMs, int cents, double rateHz)` —
    a span, not a point (vibrato is a region-modifier)
  - `VibratoControl(List<VibratoRegion>)`
  - `Vibratos(Map<TrackId, VibratoControl>)`
- Extend `Performance` to a 9th + 10th field with backwards-compat constructors
  (same migration shape that worked for `Pedaling` and `Velocities`).
- `MidiCodec.toMidi`:
  - `PitchBendControl` → ramped `PITCH_BEND` events (CC class 0xE0). For
    `RAMP_FROM_ZERO` over duration D, emit ~10 intermediate values across
    the note's life. For `HOLD`, single event at start. Reset to centre at
    note-off.
  - `VibratoRegion` → low-rate `PITCH_BEND` oscillation across the region.
- `MidiCodec.fromMidi`: discard `PITCH_BEND` (one-way write, like CC #7
  Volume — preserves the round-trip-empty contract).
- DSL helpers in the cookbook (whichever module owns the bar-builder):
  - `.bend(int semitones)` / `.preBend(int)` / `.slide(int, Direction)`
    — attach a `PitchBendChange` at the previous note's tickMs to the
    being-built `PitchBendControl` for the current track.
  - `.vibrato()` / `.vibrato(int cents, double rateHz)` — attach a
    `VibratoRegion` spanning the previous note's duration.

  The DSL builder accumulates side-channel entries during `joinMelodicPhrases`
  bake, then attaches them to the resulting `Performance` at the right
  tick offsets.

- Tests:
  - Round-trip a one-bar lick with a `Bend(2, RAMP_FROM_ZERO)` through
    `MidiCodec.toMidi` and verify ≥1 `PITCH_BEND` event lands in the
    output Sequence at the right channel and tick.
  - Vibrato region of 500ms produces an oscillating sequence of
    `PITCH_BEND` events.

**Out of scope for Phase A**:
- Score rendering of bend curves (no score view exists)
- Microtonal bends — semitone-quantised only
- Bend on chord notes — single-onset bends only

**Why side-channel beats wrapper here**:

| Concern | Wrapper (`OrnamentedNote`) | Side-channel (`PitchBends`) |
|---|---|---|
| Existing pieces | Untouched (additive PhraseNode subclass) | Untouched (new fields default to empty) |
| Cookbook ergonomics | `note.bend(2)` returns a wrapped node | Builder collects, attaches at bake time. Slightly more plumbing in the bar-builder. |
| Codec model | `MidiMapper` recognises new node type | Codec reads side-channel — same shape it already does for Volume / Pedaling / Velocities |
| Future polyphonic bends | Natural — wrapper per inner pitch | Per-(track, tickMs) — needs disambiguation if two notes share a tickMs |
| Doctrine consistency | Adds a 6th `PhraseNode` variant | Adds 2 more side-channels — matches existing pattern |

The polyphonic-bends concern is theoretical for this arrangement.
Doctrine consistency wins.

**Verification**:
- `mvn test` green for `notation-expressivity`, `notation-performance`,
  `notation-play`
- Manual: a 4-bar blues lick fragment plays with audible bend in `NotationApp`

### Phase B — Variable tempo (`TempoArrangement`)

**Design clarification from the original plan.** A new `TempoArrangement`
sits at the Piece layer, bar-anchored. At concretization time it compiles
down to the existing `TempoTrack` (Performance layer, ms-anchored) which
the codec already consumes. This is purely additive — pieces without a
`TempoArrangement` keep using `Piece.tempo()` flat, exactly as today.

**Scope** (per the existing [tempo-arrangement-plan.md](tempo-arrangement-plan.md)
+ updates):

- `notation-core/.../structure/TempoArrangement.java` — bar-anchored
  records (`TempoChange(barIndex, sixtyFourthInBar, bpm, transition)`,
  `TempoArrangement(List<TempoChange>)`, `Transition` enum).
- `Piece` gains an optional `TempoArrangement` accessor (nullable, or
  `Optional` — matches the plan doc's "three-tier precedence: user
  override > authored > flat").
- `PieceConcretizer` walks `TempoArrangement` (when present) into the
  output `Performance`'s `TempoTrack`. Linear ramps fan out into N
  intermediate `TempoChange(tickMs, bpm)` entries (one per beat
  resolution).
- `MidiPlayer.applyTempoArrangement(TempoArrangement)` — live-restages
  the running Sequence with the new tempo curve. Same restart pattern
  as `applySwing` / `applyHumanizer`.
- UI: a `TempoArrangementPanel` (or compact variant) in `ControlsPanel`
  for editing `(bar, beat, bpm, transition)` rows. Sticky-prefs the
  user's last edits per piece (sidecar `.tempo.json`).
- JSON sidecar — `MxlSplitJsonReader/Writer` add a `tempo-arrangement.json`
  alongside the existing `tempo.json`.

**Out of scope for Phase B**:
- Score rendering of tempo arc / rit. markings
- Tempo curves driven by a melodic feature (e.g. "rit. on every bar
  with a fermata") — that's an autonomous-tempo idea for later

**Verification**: per [tempo-arrangement-plan.md](tempo-arrangement-plan.md)
plus a smoke test: a 4-bar piece with a `LINEAR` ramp from 60 → 120 bpm
produces an audibly accelerating playback.

### Phase C — Bootstrap `notation-music-china-folk`

**Design unchanged from the original plan.** Module-split mechanics are
now well-understood — `notation-expressivity` proved the procedure.

**Scope**:

- New directory: `notation-music-china-folk/` sibling to `notation-songs/`.
- `pom.xml` declaring dependencies on `notation-core`, `notation-expressivity`,
  `notation-performance`, `notation-play`. Match `notation-songs`'s shape.
- Reactor `pom.xml` (root) gains `<module>notation-music-china-folk</module>`.
- Package root: `music.notation.collections.china_folk`.
- New `ChinaFolkCollection` class — a `Collection` impl whose `entries()`
  registers every piece in the module.
- Migrate from `notation-songs/.../folk/`:
  - `XiaoHongMao` (in `xiaohongmao/`)
  - `XuWeiXiaoHongMao` (in `xuwei/`)
  - `ZaiNaYaoYuan` (in `zainayaoyuan/`)
  - `BluesZaiNaYaoYuan` (in `zainayaoyuan/`)
  - `PianoZaiNaYaoYuan` (in `zainayaoyuan/`)
- Update `notation-songs/.../DefaultCollection.java` — drop the migrated
  pieces, keep only the explicitly-retained built-in examples per
  [collections-architecture.md](collections-architecture.md).
- Update the example `music.collections` JSON snippet in
  [collections-architecture.md](collections-architecture.md).

**Migration procedure (battle-tested by `notation-expressivity` split)**:

1. Create module skeleton + `pom.xml` + register in reactor pom.
2. Move source files; rewrite `package` declaration; preserve
   sub-package shape.
3. Update imports across consumers — single sed pass on the move-list
   prefix (e.g. `music\.notation\.songs\.folk\.` → `music.notation.collections.china_folk.`).
4. Add explicit dependency on the new module wherever direct imports
   landed (transitive deps from `notation-songs` won't carry once
   removed from `DefaultCollection`).
5. Build + run full test suite. Catch any stragglers.

**Verification**:
- `mvn install` from reactor root succeeds; the new module compiles;
  all tests in moved files still pass in their new location.
- `notation-songs`'s built-in-examples test still passes after the
  migration.
- Launch `NotationApp` with `-Dmusic.collections=<json-with-both>` —
  both Built-in Examples and China Folk appear in the picker; the
  migrated five pieces play identically to before.

### Phase D — Author *Jiang Cheng Zi*

Now the actual song. Lives in
`notation-music-china-folk/.../jiang_cheng_zi/`.

**Two files**:

- `JiangChengZi.java` — identity record (title, composer, MusicalPiece impl).
- `BluesJiangChengZi.java` — `PieceContentProvider<JiangChengZi>` modelled
  on `BluesZaiNaYaoYuan`.

**Track layout** (mirroring `BluesZaiNaYaoYuan`):

```java
var pianoLead = joinMelodicPhrases("Piano",       ACOUSTIC_GRAND_PIANO,   pianoLeadPhrases());
var lead      = joinMelodicPhrases("Lead Guitar", OVERDRIVEN_GUITAR,      leadGuitarPhrases());
var acoustic  = joinMelodicPhrases("Acoustic",    ACOUSTIC_GUITAR_STEEL,  acousticPhrases());
var organ     = joinMelodicPhrases("Organ Pad",   HAMMOND_ORGAN,          organPhrases());
var bass      = joinMelodicPhrases("Bass",        ELECTRIC_BASS_FINGER,   bassPhrases());
var harmonica = joinMelodicPhrases("Harmonica",   HARMONICA,              harmonicaPhrases());
var drums     = new DrumTrack("Drums", Phrase.of(buildDrumBars()));
```

No vocal track in v1. Piano carries melody, lyric correspondence in code
comments per phrase.

**Section helpers** — one method per stanza per track, returning
`MelodicPhrase`. Six featured licks land in `leadGuitarPhrases()`:

- Lick 1 — intro walk-down (stanza 1)
- Lick 2 — response after `询摊主,熟乎成?` (stanza 1)
- Lick 3 — turnaround (stanza 1)
- Lick 4 — power lick on `一击西瓜飞作雪` (stanza 2)
- Lick 5 — chromatic walk-up Em → F° → F#m7 → B7 (stanza 2)
- Lick 6 — final pre-bend release (stanza 3)

TAB → ADT conversion: walk fret + string → MIDI pitch (standard tuning
E2 A2 D3 G3 B3 E4 + fret). Use Phase A's DSL:
- Plain notes: `o<oct>(<dur>, <pitch>)`
- `b14` (bend): `o<oct>(<dur>, <starting_pitch>).bend(<semitones>)`
- `~~` (vibrato): `.vibrato()`
- `/` (slide up): `.slide(<semitones>, UP)`
- Pre-bend release: `.preBendRelease(<semitones>)`

**Drum track** — `buildDrumBars()` ArrayList:

- Stanza 1 + 3: 12 bars each of `slowShuffleBar()` — brushed-style via
  `RIDE_CYMBAL` + soft `BASS_DRUM` + `ACOUSTIC_SNARE` ghost notes.
  **NEW**: leverage Phase-A-shipped slot velocities — declare
  `slotVelocities` on the brushed pattern (kick ≈ 60, ride ≈ 70, ghost
  snare ≈ 45) instead of accepting "audition won't sound brushed."
  Open Question #2 from the original plan: resolved.
- Stanza 2: 8 bars of `straightBar()` — sticks, loud `ACOUSTIC_SNARE`
  backbeat, `CLOSED_HI_HAT` straight 8ths.
- Special bar on `一击西瓜飞作雪`: `crashBackbeatBar()`.
- Stop-time bar on `方才之诺,何以负斯盟?`: `silentBar()`.
- Outro: 48 bars of `slowShuffleBar()` + one `finalCrashBar()` at the
  end.

**Tempo arrangement** (Phase B's `TempoArrangement`, declared inline):

```
Bar 1   : 60 BPM (stanza 1 baseline)
Bar 13  : 80 BPM linear ramp into stanza 2 (stretches over 2 bars)
Bar 21  : ritardando to 50 BPM into stanza 3 (over 4 bars)
Bar 33  : 50 BPM held for stanza 3
Bar 45  : 60 BPM into outro jam, holds to end
```

Inline declaration preferred — one fewer file, type-checked. Externalise
later if hand-tweaking becomes common.

**Auto-X already covered**:

- **Auto-pedal** — Piano LH gets bass-aware auto-pedal automatically.
  No authoring needed for sustain.
- **Auto-velocity** — pitched parts get beat-position accents +
  jitter automatically. Stanzas-1+3 vs stanza-2 don't need to
  hand-shape dynamics for the basic pulse.
- **Source-aware kick alignment** — when bass and kick land on the
  same beat, kick auto-boosts. Free.

What still needs hand-authored expression:
- The six bends (Phase A's pitch expression — that's the whole point)
- The tempo curve (Phase B)
- The stop-time bar (rests in non-lead tracks)
- The hanging B7 ending — no Em resolution

**Verification per Phase D**:

- `mvn test` green for the new piece (a `PieceTestBase` subclass
  exercising bar-count, total duration, key/meter, no `Bar` assembly
  errors).
- Run in `NotationApp`: piece loads, all tracks render, audible
  playback matches the source's structural beats — feel shift between
  stanzas, stop-time silence, audible bend on Lick 4, ritardando into
  stanza 3, hanging B7 ending.
- Specifically verify: last bar is B7, no Em resolution; final state
  is the soft open-E note from Lick 6.

---

## Critical files

| File | Phase | Change |
|---|---|---|
| `notation-expressivity/.../PitchBendChange.java` | A | **New** record |
| `notation-expressivity/.../PitchBendControl.java` | A | **New** record |
| `notation-expressivity/.../PitchBends.java` | A | **New** side-channel |
| `notation-expressivity/.../VibratoRegion.java` | A | **New** record (span-typed) |
| `notation-expressivity/.../VibratoControl.java` | A | **New** record |
| `notation-expressivity/.../Vibratos.java` | A | **New** side-channel |
| `notation-performance/.../Performance.java` | A | Add 9th + 10th fields, backwards-compat constructors |
| `notation-performance/.../MidiCodec.java` | A | Emit `PITCH_BEND` ramps from `PitchBends` and oscillation from `Vibratos`; reset to centre at NOTE_OFF |
| `notation-songs/.../<bar-builder>` | A | Add `.bend(int)` / `.preBend(int)` / `.slide(int, Direction)` / `.vibrato(...)` post-fix helpers |
| `notation-core/.../structure/TempoArrangement.java` | B | **New** records (`Transition`, `TempoChange`, `TempoArrangement`) |
| `notation-core/.../structure/Piece.java` | B | Optional `TempoArrangement` accessor |
| `notation-play/.../PieceConcretizer.java` | B | Compile `TempoArrangement` → `TempoTrack` for the codec |
| `notation-play/.../MidiPlayer.java` | B | `applyTempoArrangement(...)` live restage |
| `notation-ui/.../TempoArrangementPanel.java` | B | UI for editing `(bar, beat, bpm, transition)` |
| `notation-mxl/.../MxlSplitJsonWriter.java` + Reader | B | `tempo-arrangement.json` sidecar |
| `notation-music-china-folk/pom.xml` | C | **New** module |
| Reactor `pom.xml` | C | Register new module |
| `notation-music-china-folk/.../ChinaFolkCollection.java` | C | **New** collection impl |
| Five existing China-folk pieces | C | Move from `notation-songs/.../folk/` to new module; package + import updates |
| `notation-songs/.../DefaultCollection.java` | C | Drop migrated pieces |
| `notation-music-china-folk/.../jiang_cheng_zi/JiangChengZi.java` | D | **New** identity record |
| `notation-music-china-folk/.../jiang_cheng_zi/BluesJiangChengZi.java` | D | **New** content provider |
| `notation-music-china-folk/.../jiang_cheng_zi/BluesJiangChengZiTest.java` | D | **New** `PieceTestBase` subclass |

---

## Reused infrastructure

- **Structural template**: `BluesZaiNaYaoYuan`
  (`notation-songs/.../folk/zainayaoyuan/BluesZaiNaYaoYuan.java`)
- **Cookbook helpers**: `joinMelodicPhrases`, `silentSection`, `attacca()`
  (per [arrangement-cookbook.md](arrangement-cookbook.md))
- **Drum primitives**: `silentBar()`, `crashBackbeatBar()`, `finalCrashBar()`
  (cookbook)
- **`Bar.of(BarDuration, ...)`** with `new BarDuration(12, EIGHTH)` for 12/8 bars
- **`Triplet.EIGHTH`** for stanza 1+3 shuffle subdivisions; `BaseValue.EIGHTH`
  for stanza 2 straight subdivisions
- **Auto-pedal V2** (bass-aware) — automatic for Piano LH
- **AutoVelocity** (pitched) — automatic for all pitched tracks
- **Slot velocities** on a future "BrushedShuffle" `DrumStrategy`, OR
  hand-authored brushed pattern with explicit per-slot velocities
- Existing types: `Tempo`, `KeySignature`, `TimeSignature`, `Mode.MINOR`,
  percussion enum (`BASS_DRUM`, `ACOUSTIC_SNARE`, `CLOSED_HI_HAT`,
  `RIDE_CYMBAL`, `CRASH_CYMBAL`)

---

## Verification (end-to-end after Phase D)

1. `mvn install` from reactor root — green across all modules.
2. `mvn test -pl notation-music-china-folk` — green; specifically
   `BluesJiangChengZiTest` validates bar counts (12 + 8 + 12 + 48 = 80
   bars total), correct meter, correct total duration.
3. Launch `NotationApp` with the China-folk collection on the
   `music.collections` JSON path; confirm *Jiang Cheng Zi* appears in
   the piece picker.
4. Audition end-to-end. Listen for:
   - Distinct shuffle feel in stanzas 1 + 3
   - Audible feel-shift to straight subdivisions in stanza 2
   - Tempo curve: stanza 2 noticeably tighter, stanza 3 dragged
   - Stop-time bar — full band drops out
   - Crash + power lick on `一击西瓜飞作雪`
   - Six featured licks audibly bend / vibrato (Phase A working)
   - Hanging B7 ending — no Em resolution; soft open-E fade
5. Open the piece in the IDE, review emitted code for cookbook-idiomatic
   readability — phrase factory methods named for lyric lines, comments
   linking source-doc bullet points to specific bars.

---

## Open questions to settle during execution

1. **`PitchBend` as side-channel — confirm during Phase A.** The pivot
   from wrapper to side-channel is the biggest design departure from
   the original plan. If polyphonic bends turn out to matter more
   than expected, revisit. Default: side-channel.
2. **Brushed drum — resolved.** Slot velocities (Phase A of velocity
   model, shipped) make brushed-style audible. Open Question #2 from
   the original plan: settled.
3. **Female backing vocal patch** — `VOICE_OOHS` (program 54) or
   `SYNTH_VOICE` (program 55)? Try both, pick by ear. Same as before.
4. **Inline `TempoArrangement` vs sidecar `.tempo.json`** — lean inline
   for v1 (one fewer file, type-checked). Externalise later if
   hand-tweaking becomes common.
5. **Stop-time encoding** — pure rests in non-lead tracks, normal notes
   in lead. Structural "stop-time" marker is a future feature if it
   earns its keep. Same as before.
6. **Outro jam — single track swap or four parallel tracks?** Lean:
   single track per instrument with notes-and-rests timing the swap.
   Avoids new track count. Same as before.

---

## Estimated effort

| Phase | Days | Cumulative |
|---|---:|---:|
| A — Pitch expression (side-channel) | 5–7 | 7 |
| B — Variable tempo | 5–7 | 14 |
| C — Module bootstrap + migration | 1–2 | 16 |
| D — Authoring *Jiang Cheng Zi* | 3–5 | 21 |

Roughly **3–4 weeks of solo work**. Phase A is shorter than the original
estimate (8–10 days) because the side-channel pattern is more mechanical
than the wrapper approach — same shape as `Velocities` / `Pedaling` which
already work. Phase B unchanged. Phase C unchanged (procedure now well-
understood from `notation-expressivity` split). Phase D unchanged.

The pivotal question remains: is the song the goal, or the *infrastructure
the song demands*? Choosing all four "do it the right way" options means
the latter. This piece is the forcing function for two remaining
infrastructure plans (pitch expression + variable tempo) and the first
real consumer of the multi-project collection pattern that
[`collections-architecture.md`](collections-architecture.md) describes.
