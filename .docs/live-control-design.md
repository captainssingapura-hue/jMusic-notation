# Live MIDI Control — Functional Design (Phase 5.1)

Saved 2026-04-30 mid-deliberation. Resume here tomorrow.

## Core idea

Replace the imperative `setProgram(...)` / `setVolume(...)` API on
`MidiPlayer` with a value-object + apply-function pattern. The "MIDI
creator" is a pair of pure functions:

1. `Piece → Sequence` (notes only, instrument-agnostic)
2. `(instruments, volumes) → ChannelSetup` (program/volume map per channel)

Live changes = build a fresh `ChannelSetup`, hand it to the player.
The player's role is to *apply* the setup; it owns no live-control
state of its own.

## Why functional vs imperative

| concern | imperative `setProgram/setVolume` | functional `applySetup(ChannelSetup)` |
|---|---|---|
| live change | one method per control type | rebuild `ChannelSetup`, call `applySetup` |
| state consistency | mental tracking — what's the synth's current view? | always = `currentSetup`. Re-apply anytime is idempotent. |
| loop / seek-back | tick-0 baked PC/CC reasserts — live change lost | sequence has *no* PC/CC; loop is harmless. |
| extensibility | one method per control type (pan, reverb, …) | extend `ChannelSetup` with new fields; one path. |
| testability | needs running synth + observed side effects | `ChannelSetup.from(...)` is pure; trivial to unit-test. |
| audit | "what's playing now" requires inspecting synth | "what's playing now" = `currentSetup`. Print it. |

## Concrete shapes

### `ChannelSetup` value object

```java
public record ChannelSetup(
        Map<Integer, Integer> programs,    // channel → program (0–127)
        Map<Integer, Integer> volumes      // channel → CC#7 (0–127)
) {
    public static ChannelSetup from(
            Piece piece,
            List<Instrument> instruments,
            List<Integer> volumes
    ) {
        // Reuse MidiCodec's channel-allocation logic (or a small replica):
        // pitched tracks fill 0–8 then 10–15; drum tracks → channel 9.
        // Build the maps. Pure. No I/O.
    }

    /** Apply this setup to a synth. Idempotent — call any time. */
    public void apply(Synthesizer synth) {
        for (var e : programs.entrySet()) {
            synth.getChannels()[e.getKey()].programChange(e.getValue());
        }
        for (var e : volumes.entrySet()) {
            synth.getChannels()[e.getKey()].controlChange(7, e.getValue());
        }
    }
}
```

### `MidiPlayer` changes

```java
public void start(Piece piece, List<Instrument> ins, List<Integer> vol) {
    Sequence seq = buildNoteSequence(piece);     // notes only — no PC/CC
    sequencer.setSequence(seq);
    applySetup(ChannelSetup.from(piece, ins, vol));
    sequencer.start();
}

private ChannelSetup currentSetup;
public void applySetup(ChannelSetup s) {
    this.currentSetup = s;
    if (synthesizer != null) s.apply(synthesizer);
}
public ChannelSetup currentSetup() { return currentSetup; }
```

`buildNoteSequence(piece)` reuses `PieceConcretizer` + `MidiCodec.toMidi`
but **strips program-change and CC#7 events** from the codec output.
Either:
- Add a flag on `MidiCodec.toMidi(performance, omitProgramAndVolume)`.
- Or post-process the resulting `Sequence` and remove those events
  (identifiable by `ShortMessage` status bytes).

### NotationApp wiring (Phase 5.2)

```java
// On any instrument or volume change:
var setup = ChannelSetup.from(currentPiece, selectedInstruments, selectedVolumes);
player.applySetup(setup);   // works whether playing or not
```

That's it — the UI just rebuilds the setup and hands it to the
player. **No `setProgram` / `setVolume` methods needed on `MidiPlayer`.**
The player exposes one function-shaped entry point: `applySetup`.

## Tradeoffs

**Wins**:
- Single concept (`ChannelSetup`) replaces multiple imperative controls.
- Idempotent — call before, during, after playback. Same semantics.
- Loop / seek-back doesn't reset overrides (no baked events).
- Extends naturally (add `pan`, `reverb`, `pitchBend` fields to
  `ChannelSetup`; same `apply`).
- Pure functions — easy to test headless.

**Costs**:
- Need to strip PC/CC from the codec-built Sequence (or change codec
  to optionally skip them).
- Tiny redundancy: both `MidiCodec` and `ChannelSetup.from` know the
  channel-allocation rule. Acceptable — one is "what to put in the
  file", the other is "what to push to the synth"; same logic,
  different consumers. Alternative: expose
  `MidiCodec.allocateChannels` as package-public and share.

## Phase 5.1 implementation steps (ordered)

1. **`ChannelSetup` value object** with `from(piece, ins, vol)` factory
   + `apply(synth)`.
2. **`MidiPlayer.buildNoteSequence(piece)`** — note-only Sequence.
   Decision: strip in codec (clean) vs post-process (less invasive).
   Pick post-process for the first cut.
3. **`MidiPlayer.applySetup(ChannelSetup)`** — single entry point.
4. **`MidiPlayer.start(piece, ins, vol)`** — builds note sequence,
   builds setup, applies, starts.
5. Headless test: `ChannelSetupTest` — asserts `from(piece, ins, vol)`
   produces correct channel→program/volume maps for the 4 surviving
   songs (channels 0/1/9/etc. per `MidiCodec.assignChannels`).

## Phase 5.2 (after 5.1)

NotationApp changes:
- Add a private helper `rebuildAndApplySetup()` that builds
  `ChannelSetup.from(currentPiece, selectedInstruments,
  selectedVolumes)` and calls `player.applySetup(setup)`.
- Wire it from:
  - The instrument-button's `ifPresent(picked -> ...)` callback
    (replacing the `// 5.2 will hook player.setProgram here` TODO).
  - The volume slider's `valueProperty` listener (replacing the
    `// 5.2 will hook player.setVolume here` TODO).

Both call sites become a single line.

## Open questions to confirm tomorrow

- **Strip approach**: post-process the Sequence (simpler, less
  invasive on codec) vs add a flag to `MidiCodec.toMidi(..., boolean
  omitChannelControl)` (cleaner, but touches the codec API).
  Recommendation: post-process first; promote to codec flag if the
  post-process gets messy.

- **Channel-allocation source of truth**: replicate the 10-line rule
  in `ChannelSetup.from` vs expose `MidiCodec.assignChannels` as
  package-public. Recommendation: replicate. The rule is short and
  simple ("pitched fill 0–8 then 10–15, drum on 9").

- **Empty/missing channels**: if a track name has no entry in the
  user's `selectedInstruments`, fall back to the `MelodicTrack`'s
  `defaultInstrument()`. Same for volume → 100. (Same fallback the
  current `collectInstrumentAssignments` uses.)

## File export — same generator, different output

Per follow-up deliberation: file export should reuse the same
event-generating function. The only thing that differs is *where*
events end up.

### Pipeline

```
                  ┌─→  Sequencer →  Synth          (live; ChannelSetup applied to channels)
Piece, ins, vol ─┤
                  └─→  freezeForExport → File      (locked snapshot; ChannelSetup baked into Sequence)
```

Three pure projections + two terminal output paths:

| stage | input | output | nature |
|---|---|---|---|
| `buildNoteSequence(piece)` | Piece | Sequence (notes only) | pure |
| `ChannelSetup.from(piece, ins, vol)` | Piece + UI state | ChannelSetup | pure |
| `freezeForExport(noteSeq, setup)` | Sequence + ChannelSetup | Sequence (with PC/CC at tick 0) | pure |
| `setup.apply(synth)` | ChannelSetup + Synth | (side-effect on synth channels) | terminal |
| `MidiSystem.write(seq, type, file)` | Sequence + File | (file written) | terminal |

### Concrete API

```java
public static Sequence buildNoteSequence(Piece piece);
public static Sequence freezeForExport(Sequence noteSeq, ChannelSetup setup);

public void start(Piece piece, ChannelSetup setup);
public void applySetup(ChannelSetup setup);
public void exportTo(File file) throws IOException;
```

`exportTo` becomes a tiny composition:

```java
public void exportTo(File file) throws IOException {
    if (currentNoteSequence == null || currentSetup == null) {
        throw new IllegalStateException("Nothing to export.");
    }
    Sequence frozen = freezeForExport(currentNoteSequence, currentSetup);
    int type = MidiSystem.getMidiFileTypes(frozen)[0];
    MidiSystem.write(frozen, type, file);
}
```

### Critical properties

- **Live-tweak fidelity**: the exported file reflects whatever live
  tweaks the user made (because `currentSetup` is the live setup).
- **No path divergence**: one event generator, used by two terminal
  outputs. No "playback emits one thing, export emits another".
- **Locked output**: `freezeForExport` is the snapshot — it produces
  a self-contained Sequence at the moment of export.
- **Tests stay headless**: `freezeForExport` is pure on Sequence +
  ChannelSetup. Test that the right MidiEvents appear at tick 0.
  No synth, no file I/O, no playback.

### Edge cases (tomorrow)

- **Tempo overrides**: today's `MidiPlayer.start` accepts a tempo
  parameter and the codec emits a tempo meta-event. Tempo can move
  into a `TempoSetup` value object alongside `ChannelSetup`, or stay
  baked into the noteSequence. Recommendation: keep it baked
  (composer-authored tempo is part of the piece, not a runtime
  control).
- **MIDI file Type 0 vs Type 1**: noteSequence is already multi-track
  (one Track per voice/channel). Export writes Type 1. Same as today.
- **Partial export (mid-song)**: not in scope. Today's export is
  full-song; that stays.
- **PC/CC at non-zero ticks**: if a song's source has program changes
  mid-way (instrument switch within a track), they'd live in the
  noteSequence as data, not in ChannelSetup. None of the surviving
  4 songs do this, so deferred.

## Resume next session at: implement step 1 (ChannelSetup value object).
