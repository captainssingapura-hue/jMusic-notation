# Auto-Drum Realism & Adaptiveness — Roadmap

> **Status:**
> - Density-aware variants ✅ shipped
> - Humaniser (microtiming) ✅ shipped
> - Velocity model + per-strategy slot velocities ✅ shipped
> - Source-aware kick-on-bass-onset alignment ✅ shipped
> - Ghost-snares between melody onsets ⏳ queued
> - Structural fills (volta / repeat / phrase boundaries) ⏳ queued
>
> See also [auto-drum-overview.md](auto-drum-overview.md) and
> [auto-drum-strategies.md](auto-drum-strategies.md) for the
> implemented surface; [velocity-model-plan.md](velocity-model-plan.md)
> for the velocity layer's design; [roadmap.md](roadmap.md) for the
> consolidated backlog.

The goal is to make `notation-autodrum` output sound less mechanical and
adapt to the source melody, without forcing every strategy author to
re-implement humanisation from scratch.

---

## Two distinct goals

### 1. "Less mechanical" — humanisation

A real drummer varies along three dimensions our current data doesn't
capture:

| Layer | What it means | Model impact |
|---|---|---|
| **Velocity dynamics** | Accents on 1 & 3, ghost-snare around 60-80 velocity, randomised ±5-10 within style | `PercussionNote` has no velocity field. Either add one (records change — wide blast radius) or add a **per-note dynamics side-channel** parallel to `Volume`/`Articulations`. |
| **Microtiming** | Snare slightly behind the click (~10-20 ms), hi-hat slightly ahead, small jitter on every hit | A **Performance → Performance transformer** in the spirit of `Swing`. Operates on `DrumNote.tickMs` post-bake. No model change. |
| **Sound variation** | Closed → half-open hat occasionally, rim-shot vs centre snare, splash on phrase ends | Pure strategy logic — pick from a pool of `PercussionSound` aliases per slot. No model change. |

The biggest perceptual win is **velocity**. Even perfect timing sounds
robotic without it. Microtiming alone only takes you so far.

### 2. "Adapts to melody"

A real drummer listens. We can mine the source `Performance` (or `Piece`)
for the same signals:

| Signal | Where to read it | What the strategy does |
|---|---|---|
| **Density per bar** | Note count + active-duration ratio in melody tracks | Sparse bar → fill more (16th hats, fills); dense bar → simplify (kick + snare only) |
| **Accent positions** | Onset positions across all melodic tracks; lowest-pitch onsets ≈ bass | Ghost-snares between melodic onsets; kick aligned to bass-line onsets |
| **Held notes / pauses** | Bars whose melody is whole-note holds or all rests | Activate fills (already partially in Gentle Classical's gap-aware path) |
| **Dynamic curve** | Existing `Volume` side-channel (from MXL imports) | Match drum velocity to melody loudness (when velocity model lands) |
| **Section boundaries** | `RepeatStructure` (MXL) + phrase tree (DSL) | Fill on the bar before a repeat/volta; crash on the bar after |

---

## Architectural constraints

1. **No velocity in the symbolic model today.** Both
   `notation-core.PercussionNote` and `notation-performance.DrumNote`
   are velocity-free. That blocks the most impactful humanisation
   dimension until we resolve it.
2. **Drums are monophonic per bar.** A `Bar` carries a sequential list
   of `PhraseNode`s — no simultaneous kick + hat in a single drum
   track. Even with velocity, layered grooves remain sequential
   approximations.
3. **Library vs imported pieces.** Library pieces are DSL-native (Phrase
   trees), no `Performance`. Source-aware analysis must work from
   `Piece.tracks().bars()` so it covers both paths uniformly.

---

## Proposed architecture

Two reusable utilities that compose cleanly with the existing
`DrumStrategy` interface:

```
Piece                               Piece
   │                                   │
   ▼                                   ▼
DrumStrategy.generate(...)          SourceAnalysis.scan(piece) ──┐
   │                                                              │
   ▼                                                              ▼
DrumTrack ─────► Humanizer.apply(track, energy) ─────► humanised DrumTrack
   ▲                                                              │
   └───────────── strategy may also consult analysis ◄────────────┘
```

- **`SourceAnalysis`** — pre-scans the source once and exposes per-bar
  feature vectors (density, accent positions, "is bar silent", section
  flags). Strategies consult this when choosing bar-level variants.
- **`Humanizer`** — Performance/track-level transformer that applies
  velocity shaping, microtiming jitter, and sound-variation. Composes
  after any strategy.

This keeps the `DrumStrategy` contract stable (still `(Piece, Energy) →
Optional<DrumTrack>`); both helpers are opt-in.

---

## Order of attack — current status

| # | Item | Status |
|---|---|---|
| 1 | **Density-aware variants** — strategies bucket source bars (EMPTY / SPARSE / STANDARD / DENSE) and pick per-bucket `PatternSpec` | ✅ Shipped — `BarFeatures`, `SourceAnalysis`, `PatternResolver` in `notation-autodrum`; details inlined below |
| 2 | **Humaniser transformer for microtiming** — Gaussian jitter on drum-channel `NOTE_ON`/`NOTE_OFF` ticks, paired with `SwingSetup`'s machinery | ✅ Shipped — `HumanizerSetup` in `notation-play`, UI combo in `ControlsPanel`. Covered by `HumanizerSetupTest`. |
| 3 | **Velocity model** — `Velocities` side-channel chosen over per-record field (matches `Volume` / `Pedaling` doctrine) | ✅ Shipped — `VelocityChange/Control/ies` in `notation-expressivity`, `MidiCodec.toMidi/fromMidi` round-trip, `velocity.json` sidecar, MXL `<dynamics>` parser, `AutoVelocity` for pitched. See [velocity-model-plan.md](velocity-model-plan.md). |
| 3a | **Auto-drum: `PatternSpec.slotVelocities` + per-strategy opt-in** | ✅ Shipped — all 6 strategies declare slot velocities (Rock / Disco / Funk / Jazz / Metal / Shuffle / GentleClassical). |
| 4a | **Source-aware kick alignment** — boost kick velocity when it lines up with a source bass-line onset | ✅ Shipped — `BarFeatures.bassOnsetFractions` + `Patterns.applyBassAlignmentBoost` (+5, ±1/16 bar tolerance). |
| 4b | **Ghost-snares between melody onsets** — soft snare fills in the gaps between melody attacks for a "drummer in the cracks" feel | ⏳ Queued — needs all-melody-onset detection extension to `BarFeatures`. |
| 5 | **Structural fills** — fill bars before voltas / repeats / phrase boundaries; crash on first bar after a section change | ⏳ Queued — reads `RepeatStructure` (MXL imports) + phrase tree (DSL pieces). |
| 6 | **Auto-drum tempo-arrangement awareness** — currently uses `Piece.tempo().bpm()` constant for slot-position ms | ⏳ Queued — thread `TempoConversion` through `Patterns.generateTrackWithVelocities`. |

---

## Density-aware variants — design (now implemented)

Captured here for reference. Implementation matches this design;
[auto-drum-strategies.md](auto-drum-strategies.md) is the live API doc.

### What "density" means

For a given source bar `b`:

```
density(b) = Σ over melody tracks: |notes in b that aren't rests|
            -----------------------------------------------------
                              expected unit count of b
```

So a 4/4 bar with 16 source notes has density 4.0; a bar with one
whole-note has density 0.25; a bar with all rests has density 0.

We may also separately track:

- **active-time ratio** — fraction of the bar covered by note durations
  (1.0 = note fills the whole bar, 0.0 = bar is all rests)
- **accent slots** — list of (subdivision-index, accent-strength) pairs
  used by future strategies that align drum hits to melody onsets

### Per-bar feature record

```java
record BarFeatures(
    double density,        // notes per beat
    double activeRatio,    // 0.0 (all rests) … 1.0 (fully held)
    boolean silent         // density == 0 && activeRatio == 0
) {}

record SourceAnalysis(List<BarFeatures> perBar) {
    static SourceAnalysis scan(Piece source) { ... }
}
```

### Variant selection

A strategy declares **multiple** `PatternSpec`s for the same
(BarDuration, Energy) — one per density bucket:

| Bucket | Range | Drum response |
|---|---|---|
| `EMPTY` | bar is silent | quiet kick on 1, fills the gap |
| `SPARSE` | density ≤ 1.0 | full strategy pattern (no need to thin out) |
| `STANDARD` | 1.0 < density ≤ 3.0 | strategy's medium pattern |
| `DENSE` | density > 3.0 | thinned variant — drop hi-hat, just kick/snare |

`Patterns.generateTrack` is extended to take a feature-aware resolver:

```java
@FunctionalInterface
public interface PatternResolver {
    PatternSpec resolve(BarDuration bd, Energy energy, BarFeatures features);
}
```

Existing strategies opt in by returning different specs for different
buckets; strategies that don't care (e.g. NoStrategy) ignore the
features parameter.

### Concrete example — Bach Air

The Air's first measure has a pickup of one quarter-note on the cello
melody (very sparse, density ≈ 1.0). The body bars have steady-eighth
melodic activity (density ≈ 4.0).

With density-aware Rock 8ths:

- **Pickup bar**: sparse → full pattern (kick · hat · snare · hat ·
  kick · hat · snare · hat) — but the bar is short, so `fallbackBar`
  catches it anyway.
- **Body bars (dense)**: switch to the *thinned* variant: kick · - ·
  snare · - · kick · - · snare · - (just quarter kick/snare). Drums
  hold the pulse without colliding with the melody's eighth-note
  rhythm.
- **Held-note bars** (e.g. the long sustained final note): switch to
  the *fill* variant: 16th hi-hat with mid-bar tom roll. Drums activate
  to fill the gap.

That's the perceptual lift "the drums are responding to the music"
without any velocity model.

### Where the work lands

- `SourceAnalysis` + `BarFeatures` records → `notation-autodrum`
- `PatternResolver` interface added to `notation-autodrum`
- `Patterns.generateTrack` overload that accepts a `PatternResolver`
  (existing `BiFunction` overload kept for back-compat)
- Each strategy gradually opts in; `NoStrategy` ignores it; strategies
  that want only one pattern keep returning the same spec for every
  bucket
- Tests: synthetic dense / sparse / silent fixtures verify the right
  variant is chosen

### Estimated scope

~150-200 lines of new code in `notation-autodrum`, no UI changes, no
model changes. The strategy authors decide bucket → spec mappings; the
helper handles the rest.

---

## Open questions answered during implementation

1. **`SourceAnalysis` caching** — re-scan per `generate(Piece, Energy)`
   call. The cost is negligible (~ µs) for realistic pieces; not worth
   memoisation complexity. Strategies stay stateless.
2. **Density bucket thresholds** — shipped with the table above.
   Adjust by ear if specific corpora need different cuts.
3. **Track-kind weighting** — not implemented; all melodic tracks
   contribute equally. Bass-track-aware density would require
   distinguishing bass via low-pitch heuristic or instrument metadata
   — defer until we have a concrete need.
4. **DSL piece walking** — `SourceAnalysis.scan(Piece)` resolves bars
   and counts `PhraseNode`s directly (the simpler choice).

## Velocity story — what shipped

The velocity model went **side-channel** instead of per-record field —
matches the doctrine of `Volume`, `Articulations`, `Pedaling`. Records
unchanged, zero blast radius, performance dimensions live where they
belong (in [`notation-expressivity`](../notation-expressivity)). Full
design and rationale: [velocity-model-plan.md](velocity-model-plan.md).

What changed for auto-drum:

- **`PatternSpec.slotVelocities`** — optional `int[]` parallel to
  `sequence`. Strategies opt in by attaching velocities to their
  cached pattern singletons. All 6 strategies have done so.
- **`Patterns.generateTrackWithVelocities(...)`** — bake walks bars,
  emits a per-onset `VelocityChange` for each non-rest slot, applies
  the bass-alignment boost (item 4a above) when applicable. Returns
  a `GeneratedDrums(track, velocities)` bundle.
- **`DrumStrategy.generateWithVelocities(...)`** — interface default
  wraps `generate(...)` with empty velocities; opting in is one
  override line per strategy.
- **NotationApp wiring** — `currentDrumVelocities` field captured in
  `augmentWithAutoDrum`; `refreshVelocityState` merges pitched-auto
  + drum-strategy velocities into a single `Velocities` for the
  player. Live-restages on strategy / energy change.

Velocity profiles per strategy live in source comments next to the
cached patterns (search for "Velocity profile" in each strategy).
Future tuning is a one-array edit per pattern.

What remains:

- **Ghost-snares between melody onsets** (item 4b) — adds the "fill
  the cracks" texture that distinguishes a real drummer from a
  pattern player. Needs all-melody-onset detection in `BarFeatures`,
  paralleling the existing bass-onset detection.
- **Tempo-arrangement-aware velocity bake** (item 6) — currently
  `Patterns.generateTrackWithVelocities` uses `piece.tempo().bpm()`
  as a constant for slot-position ms. Same `TempoConversion`
  propagation `AutoPedaling` and `AutoVelocity` will eventually
  share — when that lands project-wide, all three auto-X helpers
  benefit together.
