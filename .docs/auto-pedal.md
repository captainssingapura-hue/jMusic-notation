# Auto-Pedal — How It Works

> **Status: Shipped.** Companion to the source-driven sustain feature
> ([pedal-visualisation.md](pedal-visualisation.md)).

When an imported piece has no `<pedal>` markings of its own, auto-pedal
synthesises a per-bar damper timeline so the playback still gets the
pedal-rich resonance pianists actually use. Lives in
[`AutoPedaling`](../notation-performance/src/main/java/music/notation/performance/AutoPedaling.java)
and is consulted by `NotationApp` whenever the source pedaling is empty.

---

## The decision tree

```
                       Piece loaded
                            │
                            ▼
              currentImport.pedaling.byTrack
                            │
                ┌───────────┴───────────┐
              empty                  non-empty
                │                       │
                ▼                       ▼
   ┌────────────────────────┐    ┌──────────────────┐
   │ AutoPedaling.generate  │    │ source pedaling  │
   │  (per-bar pattern)     │    │   (engraver's    │
   │                        │    │    markings)     │
   └────────────┬───────────┘    └────────┬─────────┘
                │                          │
                └──────────┬───────────────┘
                           ▼
                  availablePedaling()
                           │
                           ▼
            ┌─── checkbox toggle ───┐
            │                       │
           OFF                     ON
            │                       │
            ▼                       ▼
        empty (dry)         player.setPedaling
                            pitchScroll tint regions
                            export injects CC #64
```

Source markings always win over auto-pedal — auto-pedal is a *fallback*.
The checkbox toggle is honoured at the very end so the user can compare
dry vs. pedaled at any time.

---

## The pattern

Pattern in one sentence: **press at bar 1, release-and-re-press at every
bar boundary AND at every mid-bar bass-note movement, release at the
last note's tail.**

Bar boundaries are the bedrock — they're always emitted. Bass-note
movements layer on top, debounced so they don't crowd a boundary or
each other (see [bass-aware heuristic](#bass-aware-heuristic)).

Translated to `PedalChange` events for a 4-bar piece in 4/4 at 120 bpm:

```
ms:        0      2000      4000      6000      8000  (end of music)
           │       │         │         │          │
Pedal:    DOWN   CHANGE    CHANGE    CHANGE      UP
           ▼       ▼         ▼         ▼          ▼
Bar:    │── 1 ───│── 2 ────│── 3 ────│── 4 ────│
Notes:    ♩ ♩ ♪ ♩  ♪ ♩ ♩ ♪    ♩ ♪ ♩ ♩    ♩ ♩ ♩ ─.
           ────────╲─────────╲─────────╲─────────╲
                    rings     rings     rings     decays
```

Each `CHANGE` is the pianist's "release-and-instantly-re-press" technique
— it clears the held resonance from the previous bar before the new
bar's harmony rings. On the MIDI wire this is two CC #64 events 1 ms
apart (value 0 then 127):

```
MIDI events emitted (CC #64 on every non-drum channel):

tick(0):       CC#64 = 127      ← pedal down at piece start
tick(2000ms):  CC#64 = 0        ← release for bar 2
tick(2001ms):  CC#64 = 127      ← re-engage immediately
tick(4000ms):  CC#64 = 0
tick(4001ms):  CC#64 = 127
tick(6000ms):  CC#64 = 0
tick(6001ms):  CC#64 = 127
tick(8000ms):  CC#64 = 0        ← final release at end of music
```

The split is intentional: a continuous `127` would let the previous
bar's harmony bleed into the next one, muddying chord changes. Real
pianists do the same release-and-re-press.

---

## Bass-aware heuristic

Bar boundaries alone leave one big gap: when the harmony changes
**inside** a bar (think Romantic-era LH walking through I → V → IV
mid-measure), the V1 pattern would let the I chord bleed into the V.
V2 closes that gap by adding mid-bar `CHANGE` events at every
**bass-note movement** — pianists' rule of thumb for "new harmony =
new pedal."

```
Bar 1 (4/4 @ 120bpm = 2000ms)         Bar 2 (2000ms)
│                                     │                              │
ms:  0          1000        2000          3000              4000
     │            │           │             │                 │
LH:  C2 ────────► G2 ───────► C2 ─────────► F2 ──────────────►
RH:  C5 ────────────────────────────────────────────────────────►
     │            │           │             │                 │
Ped: DOWN       CHANGE      CHANGE        CHANGE             UP
                ▲                          ▲
                bass C→G                   bass C→F
                (mid-bar 1)                (mid-bar 2)
```

Three filters keep it from over-firing:

1. **Pitch threshold** — only notes below MIDI 60 (middle C) count as
   "bass." A solo treble melody never triggers mid-bar `CHANGE`s; it
   falls back to plain bar-only auto-pedal.
2. **Bass-pitch movement** — only emit when the lowest sounding pitch
   *changes* from one onset-group to the next. Repeated bass notes
   (boom-boom-boom-boom) and bass pedal points add zero `CHANGE`s.
3. **Min gap (200 ms)** — bass changes within 200 ms of a bar boundary
   or another emitted `CHANGE` are suppressed. Catches: chromatic
   bass walks (overpedal hazard), grace-note pickups before a bar
   boundary, and double-stops where LH and RH attacks land slightly
   apart.

Onsets within ±25 ms are grouped into the same chord — covers MXL
imports where LH/RH attacks aren't perfectly co-incident, and notated
chords spread across a tiny humanisation interval.

| Source pattern | Mid-bar CHANGEs added | Why |
|---|---|---|
| Boom-chick LH (C-G-C-G repeated) | 1 (C→G mid-bar) | Bass moves at the second beat |
| Alberti bass (C-E-G-E pattern, all in bass register) | 0 | Lowest pitch (C) never moves |
| Walking bass (C-D-E-F quarter notes) | 1-2 | Min-gap drops some; first move kept |
| Solo treble melody (all notes ≥ MIDI 60) | 0 | No notes pass the bass filter |
| Bass pedal point + treble melody | 0 | Bass never changes |

---

## Track scope

Pedal applies to every **PITCHED** track in the source's Score, never to
DRUM tracks. The damper is a piano-instrument concept; emitting CC #64
on the drum channel would do nothing useful and clutter the MIDI bytes.

Concretely for a typical piano piece (right-hand + left-hand staves
become two pitched tracks):

```
Track 1 (RH treble) ─── PedalControl A ─┐
Track 2 (LH bass)   ─── PedalControl A ─┤  ← same control reference
Track 3 (Drums)     ─── (no pedal)      ─┘     (auto-drum overlay)
```

Both pitched tracks reference the **same** `PedalControl` instance —
the visualisation's per-lane background tinting therefore aligns
vertically into a single visual band across the lanes (the typical
piano case). When a multi-instrument MXL has *different* pedaling per
part the tints diverge, which is why the visualisation cycles through
a palette per lane (see [pedal-visualisation.md](pedal-visualisation.md)).

---

## Tempo handling

Bar boundaries are walked in **quarter-note space** (exact) and mapped
to ms through `TempoConversion` against the performance's own
`TempoTrack`. The math:

```
quartersPerBar = ts.beats × 4 ÷ ts.beatValue
boundary[N]    = TempoConversion.quartersToMs(performance.tempo(),
                                                N × quartersPerBar)
```

`TempoConversion` walks the piecewise-constant bpm timeline and
accumulates ms-per-segment. Pieces with **rubato, accelerando or
ritardando** get tempo-aware boundaries — auto-pedal events land on
the actual notated bars, not on a constant-bpm grid that drifts. Same
helper backs `PedalInjector`, so audio + visualization agree.

Empty `TempoTrack` falls back to a constant 120 bpm (matching the
runtime default elsewhere in the codec).

Worked example for a few common meters at constant 120 bpm:

| Time sig | Quarters per bar | Bar duration @ 120 bpm |
|---|---:|---:|
| 4/4 | 4.0  | 2000 ms |
| 3/4 | 3.0  | 1500 ms |
| 2/4 | 2.0  | 1000 ms |
| 6/8 | 3.0  | 1500 ms |
| 12/8 | 6.0 | 3000 ms |
| 5/4 | 5.0  | 2500 ms |

---

## Edge cases

| Case | Behaviour |
|---|---|
| **No `currentImport`** (library piece, no MXL source) | Auto-pedal not applied. Library pieces are DSL-authored — pedal would need to be declared in the DSL itself (deferred). |
| **Score with only drum tracks** | `Pedaling.byTrack` map is empty; checkbox stays disabled. |
| **Pickup measure** (short partial bar before bar 1) | `DOWN` is emitted at ms 0 of the pickup, then the next `CHANGE` lands at the first FULL bar boundary. The pickup gets the pedal's start tail; bar 1 gets a fresh re-press. |
| **Trailing notes past the last bar boundary** | The trailing `UP` is clamped to the actual last-note `tickMs + durationMs`, not the next bar past it. Avoids a phantom pedal-down extending into silence. |
| **Very short piece** (< 1 bar of music) | Just `DOWN` at 0 + `UP` at end-of-music. No `CHANGE` events. |
| **Source declared an empty `<pedal>` block but with no actual events** | Same as no source markings — auto-pedal kicks in. |
| **User toggles "Honor sustain pedal" off mid-playback** | `applyPedalEnabled(false)` rebuilds the Sequence with no CC #64 events; tints disappear. Re-enabling restores both. |

---

## Visualisation interaction

Auto-pedal feeds the same `Pedaling` data path as source pedaling, so
the per-lane background tint visualisation works identically:

```
Bar 1                Bar 2                Bar 3                Bar 4
░░░░░░░░░░░░░░░░░░░░ ░░░░░░░░░░░░░░░░░░░░ ░░░░░░░░░░░░░░░░░░░░ ░░░░░░░░░░░░░░░░░░░░
amber                rose                 sage                 lavender
                     ▲                    ▲                    ▲
                     CHANGE — colour      CHANGE — colour      CHANGE — colour
                     flips here           flips here           flips here
```

Adjacent regions always differ in colour because the per-lane palette
counter increments on every region close (UP or CHANGE). With a CHANGE
event the audio gap is just 1 ms but the colour flip is fully visible.

---

## Comparison: source pedaling vs auto-pedal

| Aspect | Source `<pedal>` | Auto-pedal |
|---|---|---|
| **Emit rate** | Whatever the engraver wrote — could be every quarter, every chord, every phrase | Exactly one event per bar boundary |
| **Tempo accuracy** | Each event already has its own ms anchor → tempo-exact at that instant | Constant-bpm approximation; drifts under rubato |
| **CHANGE granularity** | Often used at chord changes within a bar | Always at bar boundaries only |
| **Across tracks** | Independent per track (model supports it) | Shared timeline across all PITCHED tracks |
| **Triggers when** | `currentImport.performance().pedaling()` is non-empty | Source is empty AND import is loaded |
| **Lossless round-trip** | Yes (sidecar `pedaling.json`) | Re-derived per session — not persisted |

The user can't currently distinguish at the UI which mode is active
(both render as tint + audible sustain). A future "Source · Auto · Off"
tri-state control could expose the choice; for V1 the simpler "On / Off"
honour-toggle is enough.

---

## Limitations and follow-ups

1. **Pitch-class set detection.** The current bass-aware heuristic
   misses harmonic changes that happen *above* the bass — e.g., when
   the bass holds a pedal point but the upper voices modulate. A
   pitch-class-set comparison between consecutive chord onsets would
   catch those. Trade-off: more sophisticated, easier to misfire on
   passing-tone clouds.
2. **Other pedals.** Sostenuto (CC #66) and una corda (CC #67) follow
   the same data shape; an auto-sostenuto could engage on long-held
   bass notes. Speculative — none of our import corpus uses those
   pedals so there's nothing to compare against.

---

## Related docs

- [pedal-visualisation.md](pedal-visualisation.md) — per-lane tint with cycling palette
- [auto-drum-realism-plan.md](auto-drum-realism-plan.md) — adjacent
  "auto-X" for drum density / accents (parallel design space)
