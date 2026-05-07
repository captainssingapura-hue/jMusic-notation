# Roadmap

> Single canonical backlog. One line per item; the deep design lives
> in the per-feature plan docs linked from each row.
>
> **Maintenance rule**: when a feature ships, move its row from
> "Queued" to "Shipped" and update the linked plan doc's status
> header in the same change.

---

## Shipped

Recent (since module-split refactor):

| Feature | Notes | Doc |
|---|---|---|
| **Tempo-aware sustain pedal** | `AutoPedaling` + `PedalInjector` walk via `TempoConversion`; rubato no longer drifts | [auto-pedal.md](auto-pedal.md) |
| **Bass-aware auto-pedal** | Mid-bar CHANGEs at bass-note movements (MIDI &lt; 60), 200 ms debounce | [auto-pedal.md](auto-pedal.md) |
| **Tri-state pedal toggle** (Source · Auto · Off) | Sticky preference; auto-falls back when current selection unavailable | [auto-pedal.md](auto-pedal.md) |
| **Auto-pedal for library DSL pieces** | Concretizes `Piece → Performance` on demand | [auto-pedal.md](auto-pedal.md) |
| **`notation-expressivity` module** | Side-channel home (TrackId, Volume, Articulations, Pedaling, Velocities) | `notation-expressivity/.../package-info.java` |
| **Velocity side-channel + codec round-trip** | `Velocities` records, `MidiCodec.toMidi/fromMidi` per-note vel byte | [velocity-model-plan.md](velocity-model-plan.md) |
| **`AutoVelocity` for pitched** | Beat-1 +5, mid-bar +3, ±2 jitter; tempo-aware | [velocity-model-plan.md](velocity-model-plan.md) |
| **Per-strategy auto-drum velocities** | All 6 strategies (Rock / Disco / Funk / Jazz / Metal / Shuffle / GentleClassical) declare slot velocities | [auto-drum-realism-plan.md](auto-drum-realism-plan.md) |
| **Source-aware drum kick alignment** | +5 boost when kick lines up with source bass onset (±1/16 bar) | [auto-drum-realism-plan.md](auto-drum-realism-plan.md) |
| **MXL `<dynamics>` → Velocities** | Same dynamics events drive both Volume (CC #7) and Velocities | [velocity-model-plan.md](velocity-model-plan.md) |
| **JSON sidecar `velocity.json`** | Round-trips through `MxlSplitJsonReader/Writer` | [velocity-model-plan.md](velocity-model-plan.md) |
| **Sticky UI preferences** | Drum strategy / energy / pedal mode / swing / humanizer persist via `Preferences` | (no dedicated doc — small) |

Pre-existing:

| Feature | Notes | Doc |
|---|---|---|
| **Density-aware drum strategies** | Bar-density buckets drive per-bar pattern selection | [auto-drum-realism-plan.md](auto-drum-realism-plan.md) |
| **Microtiming humaniser** | Gaussian jitter on drum NOTE_ON/OFF ticks | [auto-drum-realism-plan.md](auto-drum-realism-plan.md) |
| **Per-lane pedal tint visualisation** | Cycling palette per region | [pedal-visualisation.md](pedal-visualisation.md) |
| **Sustain pedal end-to-end** | MXL parse + side-channel + codec + post-inject + UI tint | [auto-pedal.md](auto-pedal.md), [pedal-visualisation.md](pedal-visualisation.md) |

---

## Queued — small, well-scoped

| Item | Effort | Notes | Linked plan |
|---|---|---|---|
| **Tempo-aware drum velocity bake** | 30 min | Currently uses constant tempo; thread `TempoConversion` through `Patterns.generateTrackWithVelocities` | [velocity-model-plan.md](velocity-model-plan.md) |
| **Ghost-snare fills between melody onsets** | 1–2 hr | Auto-drum item #4 part B; uses `BarFeatures.bassOnsetFractions` shape extended to all-melody-onsets | [auto-drum-realism-plan.md](auto-drum-realism-plan.md) |
| **MXL `<accent>` / `<strong-accent>` velocity bumps** | 1 hr | `+15` / `+25` over prevailing dynamic at the note's tickMs | [velocity-model-plan.md](velocity-model-plan.md) |
| **Pitch-class set detection (auto-pedal V3)** | 2–3 hr | Catches harmonic changes when bass holds (pedal point + upper voices modulate) | [auto-pedal.md](auto-pedal.md) |
| **Hungarian Dance regression test** | 30 min | Capture as fixture; guard against import regressions | (none — convention) |
| **MxlBatch recursive scan** | 30 min | Find MXL files in subdirectories | (none — convention) |
| **Auto Drum + Energy on same UI row** | 30 min | Visual cleanup | (none) |
| **MxlPlay flags** | 1 hr | CLI player improvements | (none) |
| **NotationApp import tidying** | 1 hr | Refactor heavy class | (none) |

---

## Queued — medium

| Item | Effort | Notes | Linked plan |
|---|---|---|---|
| **Structural fills** | half-day | Fill bars before voltas / repeats / phrase boundaries; crash on first bar after | [auto-drum-realism-plan.md](auto-drum-realism-plan.md) |
| **Sostenuto + Una corda** (CC #66 / #67) | half-day | Mirror `Pedaling` shape; speculative since no current import declares them | [velocity-model-plan.md](velocity-model-plan.md) section "Future side-channels" |
| **Pitch bend** | 1 day | Continuous controller (-8192..8191), possibly interpolated. New shape — existing side-channels are step-function | [velocity-model-plan.md](velocity-model-plan.md) |
| **Modulation / Expression** (CC #1 / #11) | half-day each | Mirror `Volume` shape | [velocity-model-plan.md](velocity-model-plan.md) |
| **Per-track velocity-curve overlay on PitchScroll** | 1 day | V2 of velocity model — visualise the per-track timeline | [velocity-model-plan.md](velocity-model-plan.md) |

---

## Queued — bigger

| Item | Effort | Notes | Linked plan |
|---|---|---|---|
| **Mid-piece time/key changes** | multi-day | Currently piece-wide `TimeSignature`. Touches MXL parser, Performance, AutoPedaling/AutoVelocity/auto-drum bake (their bar boundaries need active-TS-at-segment) | [mixed-meter-plan.md](mixed-meter-plan.md) |
| **Per-track velocity merge in NotationApp** | 1–2 hr | When MXL has dynamics on only ONE track, fall back to AutoVelocity for the others (currently all-or-nothing) | (none) |
| **Channel aftertouch / Polyphonic aftertouch** | 1 day each | MPE-style expressivity. Per-(track, midi) for poly. | [velocity-model-plan.md](velocity-model-plan.md) |
| **Auto-drum velocity tempo-arrangement awareness** | 2–3 hr | When tempo arrangement lands project-wide, propagate `TempoConversion` through all auto-X helpers | (none — joint with velocity above) |

---

## Speculative

| Item | Why not yet |
|---|---|
| **Tri-state Source / Auto / Off** for velocity (parallel to pedal) | Source velocities haven't been observed often enough to warrant the UI cost yet |
| **Strategy-specific accent profiles** for AutoVelocity (pitched) | Current heuristic is generic; per-strategy contour curves would need a corpus to tune against |
| **Auto-sostenuto / auto-una-corda heuristics** | No corpus to validate against |
| **Articulation → velocity bridge** | Articulations are currently a separate side-channel; bridging them into Velocities (e.g. `<staccato>` shortens AND softens) needs design |
| **Source-aware accents reading `Volume` curve** | Need a piece corpus where `<crescendo>` shapes drum dynamics meaningfully |

---

## Architectural patterns to remember

1. **Side-channels for performance dimensions.** Don't extend abstract note records. See [`notation-expressivity/.../package-info.java`](../notation-expressivity/src/main/java/music/notation/expressivity/package-info.java) for the doctrine.
2. **Auto-X helpers as fallbacks.** When source data is empty, an `Auto*.generate(...)` static helper synthesises a sensible default — never embedded in the UI logic.
3. **Tempo-aware time math.** All bar-boundary / slot-position calculations should walk `TempoConversion` (in `notation-performance`). Avoid raw `bpm` constants.
4. **Sticky preferences via `java.util.prefs`.** New UI choices that the user might want to retain across sessions should follow the `PREF_*` constant + load-on-startup + persist-on-change pattern in `NotationApp`.
