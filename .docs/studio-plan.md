# Notation Studios — Plan

> **Status**: Plan only, no code yet. Reframed twice:
>
> 1. After recognising that the IDE + Git already own the
>    "library" — pieces are hand-authored Java in dedicated
>    collection projects (see [`collections-architecture.md`](collections-architecture.md)).
> 2. After recognising that **MIDI file import** and **live MIDI
>    capture** are different enough problems to deserve separate
>    mini-apps rather than one Christmas-tree studio.

---

## Two mini-studios, one shared core

Both apps end at the same place — Java code pasted into a
collection module — but the road in is very different.

| | **Import Studio** | **Capture Studio** |
|---|---|---|
| Source | A `.mid` file | A connected MIDI keyboard |
| Input shape | Already structured: tracks, tempo map, time signatures, meta events | One unstructured event stream |
| Typical size | Whole movements (Mozart sonata, Real Book chart) | Short phrases (4–32 bars) |
| Hard problems | Voice-splitting, per-track profile choice, pickup beats, messy authored timing, tempo / meter changes mid-piece | Tempo detection or click-track, count-in, latency, retake / punch-in, no track structure to start from |
| Iteration unit | "Try this profile / split mode on the whole piece" | "Try this tempo / quantize on this take" |
| Persistence appetite | Re-open the same `.mid` later — fine, it's already on disk | Want to keep the take itself; raw MIDI is the only record |

Trying to support both in one window meant either a parameter
panel cluttered with capture-only controls (device, count-in) when
importing, or vice versa. Two focused apps each fit on a small
screen and explain themselves at a glance.

---

## Shared core: `notation-studio-core`

A small library both apps depend on. Holds:

- **`JavaEmitter`** — walks a `Piece` and produces `MusicalPiece`
  identity record + `PieceContentProvider` source mirroring the
  conventions in `arrangement-cookbook.md`. Output is plain Java
  that compiles against `notation-core`. The single most valuable
  piece of code in either studio.
- **`PieceAuditionPanel`** — a reusable JavaFX component: a
  read-only piano-roll view of a `Piece`, a play/stop pair, and a
  status strip (bar count, BPM, meter, track count). Both apps
  embed it.
- **`ParameterPanel`** primitives — profile dropdown, split-mode
  dropdown, BPM-override field — composable bits each app picks
  from.

No app-specific UX in `notation-studio-core`. Just the
"once you have a `Piece`, do these reusable things" toolkit.

```
notation-core
  ↓
notation-performance ← BarBuilder, Quantizer, voice splitter
  ↓
notation-play ← PieceConcretizer, MidiPlayer, LoadedPiece
  ↓                              ↓
notation-studio-core ← JavaEmitter + audition / parameter components
  ↓                              ↓
notation-import-studio    notation-capture-studio    (and notation-ui / NotationApp)
```

---

## App 1 — Import Studio (`notation-import-studio`)

### What it does

Open a `.mid`, run it through `PerformanceImporter` with a chosen
profile + split mode, audition, iterate parameters until the
result sounds right, emit Java.

### Why it's its own thing

The import problem is *interpretive*. The MIDI is already
structured — it has tracks, a tempo map, time signatures. The
job is choosing how to read those structures: triplet-aware or
not, split a polyphonic track or leave it, override an obviously
wrong tempo. The full screen real estate goes to per-track view +
per-track parameters.

### Phasing

- **A1 — Shell** (~300 LOC). Open file dialog, embedded
  `PieceAuditionPanel`, `ParameterPanel` (profile + split mode +
  BPM override). Re-derives on every parameter change.
- **B1 — Per-track parameter overrides** (~150 LOC). Many real
  imports want different settings per track (drum track gets
  STANDARD, melody gets WITH_TRIPLETS). Add per-row dropdowns in
  the audition panel.
- **C1 — Copy / Save as Java** (~200 LOC). Wire `JavaEmitter` to
  a toolbar button; clipboard or directory save.
- **D1 — Polish**: pickup-beat detection nudge, tempo override per
  bar range, profile A/B compare. Picked up when needed.

Practical v1 = A1 + C1 (~500 LOC).

---

## App 2 — Capture Studio (`notation-capture-studio`)

### What it does

Listen on a MIDI input port, record a phrase, run it through
`PerformanceImporter` with `IMPROV` defaults, audition, tweak
tempo / quantize, emit Java.

### Why it's its own thing

The capture problem is *synthetic*. There's no track structure
yet, no tempo map, just a stream of events. Half the UI is about
the *act of recording*: device picker, click track, count-in,
take list, retake. The interpretation half is much smaller than
in import — usually one track, IMPROV profile, the tempo
established by the click.

### Phasing

- **A2 — Shell** (~250 LOC). Device picker, tempo + count-in,
  record / stop transport, embedded `PieceAuditionPanel`.
- **B2 — Take list** (~150 LOC). Each recording becomes a take;
  switch between takes in a sidebar. Discard / rename / save raw
  `.mid` per take. Solves "I almost had it on take 3" without
  forcing the user to pick perfectly the first time.
- **C2 — Copy / Save as Java** (~150 LOC). Same `JavaEmitter`
  binding as Import Studio.
- **D2 — Polish**: tempo *detection* (versus the imposed click)
  for free-time captures, punch-in over an existing take, layered
  multi-track capture. Each is small and additive.

Practical v1 = A2 + C2 (~400 LOC).

---

## What's *not* in either studio

Both apps share the same hard limits, inherited from the previous
reframe:

- No piece library, no `.piece.json`. Pieces are `.java` files in
  collection modules; persistence is the IDE + Git's job.
- No per-note GUI edits. If a note is wrong, edit the emitted
  Java in the IDE.
- No undo/redo of model edits. Re-deriving on parameter change
  *is* the undo: change parameters, get a fresh `Piece`. Git owns
  source-code history.
- No score rendering, no arrangement-template engine, no
  multi-piece sessions.

The only file either studio writes is:

- The Java code it emits (when the user explicitly chooses
  "Save to folder…").
- For the Capture Studio: optional raw `.mid` of a take that the
  user wants to keep around for re-analysis.

Everything else is transient.

---

## Plans absorbed (and what changed)

| Plan | Where it goes now |
|---|---|
| `duration-rational-plan.md` Phase 7 | `notation-core`; both studios consume |
| `tempo-arrangement-plan.md` | `notation-performance` + cookbook; Import Studio's per-bar tempo override exposes it |
| `mixed-meter-plan.md` | `notation-core` model; Import Studio exposes a per-bar meter editor in polish phase |
| `voice-separation/tier-4-role-classifier.md` | `notation-performance`; both studios surface inferred roles |
| `arrangement-cookbook.md` "apply template" | Stays as authoring code — the IDE applies templates to emitted Java |
| Save-to-library | Removed |
| Per-bar GUI edit | Removed |
| Live MIDI keyboard capture | **App 2 (Capture Studio)** |

---

## Effort estimate (very rough)

| Module | Phase | LOC | Cumulative |
|---|---|---:|---:|
| `notation-studio-core` | JavaEmitter + audition panel + param primitives | 400 | 400 |
| `notation-import-studio` | A1 shell | 300 | 700 |
| `notation-import-studio` | B1 per-track overrides | 150 | 850 |
| `notation-import-studio` | C1 emit | 200 | 1050 |
| `notation-capture-studio` | A2 shell | 250 | 1300 |
| `notation-capture-studio` | B2 take list | 150 | 1450 |
| `notation-capture-studio` | C2 emit | 150 | 1600 |

Practical v1 = `studio-core` + Import A1/C1 + Capture A2/C2 ≈
1300 LOC, both studios usable.

The split is *more* total LOC than one combined studio would be —
shared chrome is small and the per-app shells aren't free — but
each app is much smaller and more legible than the combined one
would have to be. And either one can ship without the other.

---

## Recommended starting order

1. **`notation-studio-core`** — the `JavaEmitter` is the highest-
   risk, highest-value piece. Build it first against an existing
   in-memory `Piece` (e.g. `XuWeiXiaoHongMao`) and verify the
   round-trip: Java → `Piece` → emitted Java compiles back to the
   same `Piece`. The audition panel and parameter primitives can
   land alongside.
2. **Import Studio (A1 + C1)**. Bigger payoff — turns every `.mid`
   the user already has into starter Java.
3. **Capture Studio (A2 + C2)**. Adds the live-keyboard on-ramp.

---

## Status

- Plan only. No code yet.
- Pairs with `studio-design.md`, which sketches both windows.
