# Variable-Tempo Sidecar — Design & Phased Plan

> Design captured for later implementation. Not yet built.

## Goal

Reintroduce **variable-tempo expression** (rit., accel., section
tempo changes) without modifying the core `Piece` shape:

- A `Piece` keeps its existing single `Tempo` — the *authored
  default*.
- A separate **`TempoArrangement`** sidecar can override timing,
  supplied either by the piece's content provider (authored
  expression) or by the user via the UI (override layer).
- The data model is bar-anchored, JSON-serialisable, and
  round-trips for both internal save/load and MIDI import/export.

## Three-tier precedence

```
user-edited override (UI session)
        │ falls back to
piece-authored arrangement  (PieceContentProvider supplies)
        │ falls back to
piece flat tempo            (Piece.tempo() — current behavior)
```

If neither override nor authored arrangement is present → flat
playback at `Piece.tempo()` (today's behavior, unchanged). This
keeps the change strictly additive.

## Data model

### Records (in `notation-core/.../structure/`)

```java
public enum Transition {
    STEP,                       // instant jump (default)
    LINEAR_FROM_PREVIOUS        // linear ramp from the previous change to this one
}

public record TempoChange(
        int barIndex,           // 0-based bar number
        int sixtyFourthInBar,   // 0..barSf-1, position within the bar
        int bpm,                // target BPM at this point
        Transition from         // how we arrive at this change
) { }

public record TempoArrangement(List<TempoChange> changes) {
    // Invariants:
    //   - changes sorted by (barIndex, sixtyFourthInBar)
    //   - first change at (0, 0) — the starting tempo
    //   - bpm in 30..300 sanity range
    public int  bpmAtBar(int barIndex, int sfInBar) { ... }
    public Stream<TempoChange> spanning(int fromBar, int toBar) { ... }
}
```

### Why bar/beat anchoring (not milliseconds)

For pieces with bar structure, the natural language is *"ritardando
at bar 12, beat 4"* — not *"slow down at 14523 ms."* Authored
arrangements bind to musical positions; ms positions are derived
later via the bar-walk math `PieceConcretizer` already does.

### Anchor units inside a bar

**Sixty-fourths in the model** — matches the system's atomic
sub-division everywhere else. UI can present them as beat
positions (1.0, 1.5, 2.0, …) for human input.

### Transition modes

- **`STEP`** (default): instant tempo jump at the change point.
  Matches how MIDI tempo events work natively; covers "switch to
  half-time at the bridge."
- **`LINEAR_FROM_PREVIOUS`**: linear interpolation from the
  previous change to this one, distributed across the intervening
  beats. Covers "rit. into the cadence."

Two modes is enough for ≥ 95% of authored music. Curves
(exponential, ease-in/out) can be added later if anything actually
needs them.

## Where each piece lives

| Layer | Change | Risk |
|---|---|---|
| `notation-core/.../structure/TempoChange.java` | New record | None — additive |
| `notation-core/.../structure/Transition.java` | New enum | None |
| `notation-core/.../structure/TempoArrangement.java` | New record | None |
| `notation-core/.../structure/PieceContentProvider.java` | Add default-method `Optional<TempoArrangement> defaultTempoArrangement() { return Optional.empty(); }` | Backward-compatible — existing providers gain a no-op |
| `notation-play/.../PieceConcretizer.java` | If arrangement present, build `TempoTrack` from it; else use `Piece.tempo()` flat (current path) | Single branch — keeps current behavior intact when arrangement is absent |
| `notation-play/.../MidiPlayer.java` | New `applyTempoArrangement(TempoArrangement)` — rebuilds the upcoming sequencer-tempo timeline from the cursor forward | Live mid-playback updates |
| `notation-play/.../TempoArrangementJson.java` | JSON round-trip; mirrors `PerformanceJson`'s style | New |
| `notation-ui/...` | "Tempo Editor" pane: list of change points + add/delete/edit + Apply/Reset/Save buttons | Phase 3, defer |

## JSON serialisation shape

```json
{
  "version": 1,
  "changes": [
    { "bar": 0,  "sf": 0,  "bpm": 64,  "from": "STEP" },
    { "bar": 7,  "sf": 32, "bpm": 60,  "from": "LINEAR_FROM_PREVIOUS" },
    { "bar": 8,  "sf": 0,  "bpm": 64,  "from": "STEP" },
    { "bar": 23, "sf": 32, "bpm": 50,  "from": "LINEAR_FROM_PREVIOUS" },
    { "bar": 24, "sf": 0,  "bpm": 64,  "from": "STEP" }
  ]
}
```

The shape above expresses *"rit. into the cadence at bar 7-8 then
back to tempo, repeat at bar 23-24"* — the kind of expression
`PianoZaiNaYaoYuan` would benefit from.

**Save location**: `~/.config/notation/tempo/<piece-id>.tempo.json`
(matches existing `Preferences`-based persistence patterns; can
become picker-discoverable later).

## How it interacts with the existing BPM slider

The current `TempoSetup` BPM slider **becomes a scaling factor on
top of the arrangement**:

- Authored arrangement says: `bar 7→60, bar 8→64`.
- Slider drag to 120% → all change points scale by 1.2 → effective
  `bar 7→72, bar 8→77`.

Preserves the slider's current "play faster/slower than authored"
behavior without conflicting with the arrangement. The slider is
not editing the arrangement; it's a global multiplier.

## Player integration flow

When playback starts:

1. **UI assembles effective arrangement**: `override > authored > flat`.
2. **Effective arrangement → `TempoTrack`** via bar-walk math:
   - For `STEP`: emit a `TempoChange(ms, bpm)` at the converted ms
     position.
   - For `LINEAR_FROM_PREVIOUS`: emit several intermediate
     `TempoChange` events (e.g. one per quarter-note) with
     interpolated BPMs spanning the previous change to this one.
3. `MidiPlayer.start(piece, channelSetup, tempoSetup, swing, tempoArrangement)`
   passes it through to the sequencer's tempo events.
4. **Mid-playback updates**: `applyTempoArrangement(...)` rebuilds
   the upcoming portion of the sequencer's tempo timeline from the
   current cursor forward.

The `javax.sound.midi.Sequencer` already supports tempo events
natively (via `MetaMessage` set-tempo events), so no new MIDI
plumbing is required — only the bar-walk → ms-position conversion.

## MIDI roundtrip

### Export

`PieceConcretizer` honoring the arrangement → `Performance` with
the right `TempoTrack` → MIDI export already emits the tempo events
correctly. **No new export code needed.**

### Import

For MIDI imports, `Performance.TempoTrack` already preserves multi-
tempo (as ms-anchored events). To present those as a
`TempoArrangement` (bar-anchored) in the UI, we need to convert
ms positions to bar positions.

The math is doable but has subtleties: bar boundaries in absolute
time depend on the cumulative effect of tempo changes (chicken-and-
egg with bar lengths varying as tempo varies). The clean way:

```
walk source bars in tick-space (constant ticks per bar from MIDI PPQ)
for each tempo event in the source MIDI:
    compute its tick position
    locate which bar contains that tick
    emit a TempoChange(barIndex, sfInBar, bpm, STEP)
```

This requires preserving slightly more MIDI context than `MidiCodec`
currently does (we'd need to keep the source tempo events' tick
positions, not just their derived ms positions).

## Phased rollout

### Phase 1 — pure data model (≈ 200 LOC, no behavior change)

- `TempoChange`, `TempoArrangement`, `Transition` records in
  `notation-core`.
- `PieceContentProvider.defaultTempoArrangement()` default-method.
- `TempoArrangementJson` round-trip serialiser.
- Unit tests for the data model + JSON round-trip.

**Ship-able by itself.** No visible behavior change yet — this is
just the canvas.

### Phase 2 — playback integration (≈ 150 LOC)

- `PieceConcretizer` reads `Piece.tempoArrangement()` (or rather,
  the provider's `defaultTempoArrangement()` plumbed through) when
  present.
- `MidiPlayer.start(..., tempoArrangement)` overload.
- `MidiPlayer.applyTempoArrangement(...)` for live updates.
- One authored example: add `defaultTempoArrangement()` to
  `PianoZaiNaYaoYuan` with a rit. at bars 9–10 so the difference
  is audible immediately.

### Phase 3 — UI tempo editor (≈ 250 LOC)

- Editor pane (table-style: bar | beat | bpm | transition + delete).
- "Add change point" button.
- "Apply", "Reset to authored", "Save…" / "Load…" buttons.
- Per-piece persistence via `Preferences` or sidecar `.tempo.json`
  files.

### Phase 4 — MIDI import roundtrip (≈ 100 LOC + bar-walk math)

- `MidiCodec.fromMidi` preserves source tempo events' tick
  positions (or `MidiImport` carries them separately).
- `PerformanceImporter` converts ms-anchored `TempoTrack` →
  bar-anchored `TempoArrangement` and attaches it to the imported
  Piece.
- The user can then edit imported tempo arrangements in the UI
  just like authored ones.

## Decisions already made (don't re-litigate)

- **Anchor**: bar/beat (sixty-fourths internally), not milliseconds.
- **Transitions**: `STEP` and `LINEAR_FROM_PREVIOUS` only — no
  curves in v1.
- **Default transition**: `STEP` (matches MIDI/native semantics).
- **Slider relationship**: BPM slider becomes a multiplier on top
  of the arrangement, not an editor.
- **Imports**: keep flat-BPM behavior in v1; bar-anchored
  conversion is Phase 4.

## Open questions to revisit when starting Phase 1

- Should `Piece` itself gain an `Optional<TempoArrangement>`
  field, or stay shape-stable with the arrangement always living
  on the provider side? **Lean: keep `Piece` stable; provider
  supplies.** Less churn through the codebase.
- Should the JSON `version` field be required or optional?
  **Lean: required** — gives us a clean upgrade path if the
  schema ever changes.
- File extension: `.tempo.json` vs `.tempoarr.json` vs
  `.tempo-arrangement.json`? Cosmetic — pick when implementing.

## When to start

When variable-tempo expression becomes a meaningful authoring
or playback request — typically signaled by the user wanting a
"rit." or "accel." or "tempo change at bar X" that the current
flat `Tempo` can't express. Until then, defer.
