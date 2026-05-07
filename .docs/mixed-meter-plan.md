# Mixed-Meter Support — Design & Phased Plan

> Design captured for later implementation. Not yet built.
> Depends on the [duration-abstraction-plan](duration-abstraction-plan.md)
> (specifically `BarDuration`).

---

## Goal

Support pieces whose bars **don't all share the same meter**:
mixed-meter folk songs, prog-rock with 7/8↔4/4 alternation,
sacred music with chant-like irregular phrasing, Stravinsky-style
rhythmic shifts. Currently the *data model* allows this but the
*surrounding system* silently assumes uniform meter.

---

## Current state

### What already works

- `Bar` carries its own `expectedSixtyFourths` (will become
  `expectedDuration : BarDuration` after the duration-abstraction
  plan lands).
- `Phrase` is `List<Bar>` — no constraint that bars share a size.
- The `Bar` constructor validates each bar's nodes against *its
  own* expected size, not a global one.
- `PieceConcretizer` walks per-bar via `bar.expectedSixtyFourths()`
  — already correct for mixed meter.

So you can already build a `Piece` with mixed-meter bars, and
playback (via concretize → MIDI) honours the actual sizes
correctly.

### What silently assumes uniform meter

| Layer | Assumption | What breaks |
|---|---|---|
| `Piece.timeSig()` | One `TimeSignature` for the whole piece | UI shows "4/4" even when bar 3 is 7/8; no API for "what meter is bar N?" |
| `PerformanceImporter` | `barSf = ts.beats() × (64/ts.beatValue())` — single value for the whole pipeline | `BarBuilder` packs everything into uniform-size bars; mixed-meter MIDI imports get destroyed |
| `BarBuilder` / `DrumBarBuilder` | `Config(barSf, bpm)` | Same as above |
| `TUIPianoRoll` | `barSize = bars.get(0).expectedSixtyFourths()` — uses the *first* bar's size for the whole render | Bar lines drift from the second mixed-meter change onward |
| `PitchScrollData` | Uniform bar width | Bar lines visually drift in the piano roll |
| MIDI emission | One `TIME_SIGNATURE` meta-event at tick 0 | Notation software reading the export sees only the initial meter |

---

## Four-phase plan

### Phase 1 — `Bar` carries `BarDuration` (not `int`)

Touches only `notation-core`. Replace `Bar.expectedSixtyFourths`
with `Bar.expectedDuration : BarDuration`. Provide a derived
`expectedSixtyFourths()` getter for converters that still need it.

**✅ DONE** — landed as part of Step 2 of the
[duration-abstraction plan](duration-abstraction-plan.md). `Bar`'s
record component is now `BarDuration expectedDuration`; the
backward-compat `expectedSixtyFourths()` getter returns
`expectedDuration.sixtyFourths()`. Legacy `Bar.of(int, …)` calls
work via reverse-math through `BarDuration.fromSixtyFourths(int)`.

### Phase 2 — Piece exposes per-bar meter

Two interface options to choose from when implementing:

**A. Method-based**
```java
public TimeSignature meterAt(int barIndex) { … }
public boolean hasMixedMeter() { … }
```
Keeps `Piece.timeSig()` as the *primary* (most common) meter for
backward-compat, with `meterAt` for queries.

**B. Sidecar `MeterArrangement`** (parallel to `TempoArrangement`)
```java
public record MeterArrangement(List<MeterChange> changes) { … }
public record MeterChange(int barIndex, BarDuration meter) { … }
```
Optional sidecar; piece falls back to `timeSig()` when absent.
Consistent with how the tempo plan handles arrangement vs flat.

**Lean: B (sidecar)** — for the same reason `TempoArrangement` is
a sidecar: keeps `Piece` shape stable, makes the multi-meter case
opt-in, and serialises naturally.

About 80 LOC plus serialization.

### Phase 3 — Renderers and converters honour per-bar meter

- `TUIPianoRoll.format()` — walk `bar.expectedDuration()` per bar
  for column counts and bar-line placement.
- `PitchScrollData.fromPiece` — accumulate per-bar widths instead
  of multiplying by a single `barSf`.
- `PerformanceImporter` / `BarBuilder` — config becomes
  `(MeterArrangement, bpm)`; per-bar quantization uses the *current*
  bar's size.
- `MidiPlayer` tempo events — beat positions inside a bar depend
  on bar size; needs per-bar walk.

About 200–300 LOC across visitor sites. Each site is small but
together they add up.

### Phase 4 — MIDI roundtrip

- **Export**: emit `MetaMessage.TIME_SIGNATURE` events at bar
  boundaries when the meter changes. (Currently we emit one at
  tick 0; the loop just needs to fire whenever the meter at the
  upcoming bar differs from the current.)
- **Import**: `MidiCodec.fromMidi` parses time-sig meta events
  with their tick positions; `PerformanceImporter` builds a
  `MeterArrangement` from them and attaches it to the imported
  Piece.

About 80 LOC plus the bar-boundary tick-walk math (similar
chicken-and-egg to the tempo import case — bar sizes vary as you
walk).

---

## When to start

Wait until a real song requires it. Triggers:

- Authoring a mixed-meter piece (Take Five aside — that's just
  5/4; need actual *changes*: 5/4 → 4/4, or 7/8 → 4/4 → 7/8).
- A user-supplied MIDI import has time-signature changes that
  the current single-`TimeSignature` flattening destroys.
- A score-rendering / notation-export feature that needs to draw
  the meter changes correctly.

Until then, the duration-abstraction plan's Phase 1 (Bar carries
`BarDuration`) is enough — the model is mixed-meter ready even
when the renderers aren't.

---

## Key decisions already made

- **`BarDuration` is the per-bar meter type** — preserves
  (count, unit) so 6/8 vs 3/4 is distinguishable, not just total
  duration.
- **Sidecar `MeterArrangement`** is the preferred shape (over
  per-bar method on `Piece`), matching the tempo design.
- **`Piece.timeSig()` stays** as the primary/most-common meter for
  backward-compat; arrangement overrides.

---

## Status

- **Phase 1 ✅ done** — folded into Step 2 of the
  [duration-abstraction plan](duration-abstraction-plan.md).
  `Bar` now carries `BarDuration expectedDuration`; the model is
  mixed-meter ready at the structural level. Authoring a mixed-meter
  piece is *technically* possible today — bars of different sizes
  can be packed into one phrase — but renderers and converters still
  assume uniform meter (Phase 3 not done), so visual / playback
  correctness for actually-mixed bars is not guaranteed yet.
- **Phases 2–4 deferred** until triggered by a real mixed-meter
  use case.
