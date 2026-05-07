# Auto Drum — Overview

> **Status: Tier 0 + density awareness + humaniser shipped.** See
> [auto-drum-strategies.md](auto-drum-strategies.md) for adding new
> styles, [auto-drum-realism-plan.md](auto-drum-realism-plan.md) for
> the work still queued.

A pluggable drum-accompaniment generator that overlays a `DrumTrack`
onto the loaded `Piece` for **live playback only** — the source
`savedPiece` (used for export and JSON sidecars) stays drum-free.

## What it does

Given a non-drum piece, produces a percussion accompaniment that:

1. Picks a **style** (Gentle Classical / Rock 8ths / Disco / Shuffle /
   Funk / Jazz / Metal / …) from a catalogue of `DrumStrategy`
   implementations.
2. Applies an **energy** level (Low / Medium / High) for overall
   intensity.
3. **Adapts to the melody** per bar — sparse melody → busier drums,
   dense melody → thinner drums, silent bars → kick-on-1 fill.
4. Optionally **humanises** the timing — Gaussian-distributed jitter on
   drum-channel events to soften the metronomic feel.

UI surfaces the controls as three paired combos in the right-edge
controls drawer:

```
Auto Drum: [None | Gentle Classical | Rock 8ths | Disco | Shuffle | Funk | Jazz | Metal]
Energy:    [Low | Medium | High]              ← gated by Auto Drum
Humanize:  [Off | Light | Medium | Loose]     ← gated by Auto Drum
```

The picker is automatically disabled when the source piece already
carries a hand-authored drum track (so library pieces with their own
drums stay untouched).

## Modules

```
notation-autodrum/                   ← strategy SPI + implementations
  DrumStrategy.java                  ← @FunctionalInterface
  DrumStrategies.java                ← built-in registry
  Energy.java                        ← LOW / MEDIUM / HIGH
  PatternSpec.java                   ← (unit, sound[]) declarative pattern
  Patterns.java                      ← bar-walking helper + fallback bar
  PatternResolver.java               ← (BarDuration, Energy, BarFeatures, idx) → spec
  BarFeatures.java                   ← per-bar density + activity ratio
  DensityBucket.java                 ← EMPTY / SPARSE / STANDARD / DENSE
  SourceAnalysis.java                ← Piece pre-scan
  strategies/
    NoStrategy.java                  ← "off" sentinel
    GentleClassicalStrategy.java     ← classical-friendly default
    RockBeatStrategy.java            ← 8th-note rock + 16th-hat fills
    DiscoStrategy.java               ← four-on-the-floor
    ShuffleStrategy.java             ← compound-time triplet feel
    FunkStrategy.java                ← syncopated 8ths / 16th ghost notes
    JazzStrategy.java                ← ride + chick comping
    MetalStrategy.java               ← double-bass 16ths

notation-play/
  HumanizerSetup.java                ← Sequence-level microtiming jitter
  MidiPlayer.java                    ← applies Humanizer + Swing post-build

notation-ui/
  ControlsPanel.java                 ← three combos
  NotationApp.java                   ← currentDrumStrategy / currentEnergy / Humanizer
```

## Data flow at play time

```
currentPiece (drum-free source)
   │
   ▼
augmentWithAutoDrum(piece)
   │     ├─ if strategy == NONE:        return piece unchanged
   │     ├─ strategy.generate(piece, energy)
   │     │     └─ uses SourceAnalysis to bucket each source bar
   │     │     └─ for each bar: PatternSpec → Bar via Patterns helper
   │     │     └─ unmatched meters: Patterns.fallbackBar (kick on 1)
   │     └─ append DrumTrack to a new Piece (currentPiece + drums)
   ▼
augmented Piece — used for both lane factory (display) and player (sound)
   │
   ▼
MidiPlayer.start(piece, channelSetup, tempoSetup, swingSetup)
   ├─ buildNoteSequence(piece)         ← Piece → MIDI Sequence (PieceConcretizer)
   ├─ swingSetup.apply(seq)            ← off-beat shift if non-OFF
   ├─ currentHumanizer.apply(seq)      ← Gaussian jitter on drum channel
   └─ Sequencer plays the rewritten sequence
```

The "live-only" guarantee is enforced by `setSavedAndCurrent(p)` in
`NotationApp` — `savedPiece` is set to the drum-free input, then
`currentPiece = augmentWithAutoDrum(savedPiece)` is the displayed +
played overlay. JSON / MIDI export reads `savedPiece` and the auto-drum
track never leaves the live session.

## Decisions worth knowing

| Decision | Why |
|---|---|
| **Strategy returns a `Piece`-level `DrumTrack`, not Performance-level `DrumNote`s** | Keeps lane-factory + per-track UI mute/solo working uniformly. Drum bars participate in the abstract DSL like any other. |
| **Drum bars are sequential (no simultaneous kick + hat)** | DSL constraint — a `Bar` carries a flat sequence of `PhraseNode`s. Patterns alternate sounds per subdivision instead of layering. |
| **Density buckets: EMPTY / SPARSE / STANDARD / DENSE** | Coarse-grained enough that strategies don't have to think about exact thresholds; fine-grained enough to differentiate "give it air" from "fill the space". |
| **Humaniser at Sequence level (not Performance)** | Mirrors `SwingSetup`'s slot — same lifecycle, same cost profile. No symbolic-model surgery. |
| **No persistence of strategy / energy / humanise** | Live-only by spec; resets per session. Sticky preference is queued but unbuilt. |

## Quick reference — defaults & behaviour

| Source piece state | Auto Drum picker | Energy / Humanize |
|---|---|---|
| Library piece **with** a `DrumTrack` | disabled (greyed) | disabled |
| Library piece **without** drums | enabled, defaults to None | disabled until non-None |
| Imported MXL / MIDI / JSON | enabled, defaults to None | disabled until non-None |

When **None** is selected, `augmentWithAutoDrum` returns the source
unchanged; the playback path is identical to the no-auto-drum world.

## Cost summary

- **Per-strategy generation**: O(bars × tracks) for `SourceAnalysis` +
  O(bars) for the resolver-driven walk. Sub-millisecond for any
  realistic piece.
- **Humaniser**: O(MIDI events) ≈ a few hundred microseconds for a 5-min
  drum-bearing sequence. Paid once per `start` / `restartAt`. Zero
  per-event cost during playback (Sequencer plays a static event list).
- **Lane factory rebuild on strategy change**: same cost as switching
  pieces. Visible as a brief pause; not a hitch.

## Where to go next

- Adding a new style → [auto-drum-strategies.md](auto-drum-strategies.md)
- Future realism work (velocity model, structural fills, accent matching)
  → [auto-drum-realism-plan.md](auto-drum-realism-plan.md)
