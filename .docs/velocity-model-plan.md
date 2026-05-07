# Velocity Model — Design

> **Status: Foundation shipped. Auto-drum opt-in shipped. Source MXL
> dynamics shipped. Articulation-based accents (`<accent>`,
> `<strong-accent>`) and auto-drum tempo-arrangement awareness still
> queued — see `roadmap.md`.**
>
> Companion to `auto-drum-realism-plan.md` (item #3 in that roadmap)
> and follows the side-channel doctrine established by `Volume`,
> `Articulations`, and `Pedaling`.

## Goal

Add per-note velocity to the `Performance` model — without baking it
into the abstract note records — so that:

- Auto-drum can emit accented kicks (vel ~110), medium snares (vel ~80),
  and ghost-snares (vel ~50) instead of every drum hit at uniform vel
  90.
- Pitched parts can carry composer/engraver dynamics (`<f>`, `<p>`,
  crescendos) when an MXL import declares them.
- Library DSL pieces stay backwards-compatible — every existing
  `PercussionNote` / `PitchedNote` keeps working at the default vel 90.
- The same mechanism extends later to other instrument-specific
  expressivity (pitch bend, modulation, expression CC #11) without
  re-architecting.

## Why side-channel, not a record field

The earlier `auto-drum-realism-plan.md` sketch proposed adding `int
velocity` directly to `PercussionNote` and `DrumNote`. After
revisiting it against the project's existing doctrine, **side-channel
is the better call.**

| Direct field on the note | Side-channel parallel to `Volume` |
|---|---|
| Wide blast radius — every existing test, song fixture, library piece needs constructor updates (mitigated by overloads, but still touches every file) | Zero blast radius — adding a `Velocities` record touches no existing record signature |
| Composer authors "snare hit @ vel 87" — conflates notation with interpretation | Composer authors "snare hit"; performance layer decides vel 87 — matches `Volume`/`Articulations`/`Pedaling` |
| MXL importer must populate velocity at parse time even when source has no dynamics | Importer can leave `Velocities` empty; downstream `AutoVelocity` synthesizes a sensible default |
| Auto-velocity becomes a record-rewriting transform | Auto-velocity is a `Performance → Velocities` helper, parallel to `AutoPedaling` |

The decision matches how every other performance dimension already
works in this codebase: composition stays minimal, performance layers
side-channels on top, codec merges them at MIDI emission.

## Data model

```java
// notation-expressivity (alongside Volume / Articulations / Pedaling)

/** A single velocity set-point on a track, anchored at a millisecond tick. */
public record VelocityChange(long tickMs, int velocity) {
    public VelocityChange {
        if (tickMs < 0)        throw new IllegalArgumentException(...);
        if (velocity < 1 || velocity > 127) throw new IllegalArgumentException(...);
    }
}

/** Per-track sparse velocity timeline — sorted by tick, dedup'd. */
public record VelocityControl(List<VelocityChange> changes) {
    public VelocityControl { /* sort + dedup consecutive same-velocity */ }
    public static VelocityControl empty();
    public static VelocityControl constant(int velocity);
}

/** Side-channel: per-track velocity timelines. */
public record Velocities(Map<TrackId, VelocityControl> byTrack) {
    public Velocities { /* drop empty controls; deep-copy */ }
    public static Velocities empty();
    public static Velocities single(TrackId, VelocityControl);
}
```

`Performance` gains a 7th field:

```java
public record Performance(
        Score score, TempoTrack tempo, Instrumentation instruments,
        Volume volume, Articulations articulations, Pedaling pedaling,
        Velocities velocities) { ... }
```

Plus a 6-arg backwards-compat constructor that defaults `velocities`
to `Velocities.empty()`. Same migration shape that worked for
`Volume` and `Pedaling`.

## Semantics — step-function lookup

A note at `(track, tickMs)` resolves its velocity by looking up the
**most recent** `VelocityChange` on that track at or before `tickMs`:

```
velocity(note) = lookup(note.tickMs, velocities.byTrack[note.track])
              = changes.last(c -> c.tickMs <= note.tickMs).velocity
              ?? DEFAULT_VELOCITY (90)
```

Step-function — no interpolation in V1. A "crescendo from p to f"
imported from MXL gets approximated as a series of dense
`VelocityChange` entries (the importer fans out the span). Drums work
naturally because each hit has its own entry — interpolation isn't
relevant.

Default value: **90** (MIDI mf, the convention everywhere else in the
codebase).

Range: **1–127**. Velocity 0 is illegal in the model — MIDI uses
`NOTE_ON, vel=0` as a synonym for `NOTE_OFF`, and the codec emits
`NOTE_OFF` directly. Clamping to ≥ 1 in the record constructor avoids
surprise rest-events.

## Codec integration

`MidiCodec.toMidi` already walks notes and emits `NOTE_ON` events.
The change:

```java
int vel = velocities.byTrack().getOrDefault(track.id(), VelocityControl.empty())
                              .velocityAt(note.tickMs(), DEFAULT_VELOCITY);
emitNoteOn(channel, note.midi(), vel, tick);
```

Round-trip — the importer side `MidiCodec.fromMidi` reads the velocity
byte from each `NOTE_ON` and back-fills `Velocities`. Not strict
parity with the source (sparse vs dense reconstruction), but the
information survives `toMidi → fromMidi` in a usable form.

**Round-trip parity contract**: a `Performance` whose `Velocities` is
empty round-trips through `toMidi → fromMidi` to a `Performance` with
a constant-90 `Velocities` (every note explicit). The shape isn't
preserved but the *audible result* is identical — same convention
`Pedaling` follows.

## MXL importer integration

Two MusicXML signal sources to read:

1. **`<direction><dynamics>`** — `<p>`, `<mf>`, `<ff>`, etc. Each
   marks the *start* of a dynamic region; the velocity persists until
   the next dynamic. Standard MIDI mappings:

   | Symbol | Velocity |
   |---|---:|
   | ppp | 16 |
   | pp  | 33 |
   | p   | 49 |
   | mp  | 64 |
   | mf  | 80 |
   | f   | 96 |
   | ff  | 112 |
   | fff | 126 |

   Each `<dynamics>` becomes one `VelocityChange` at the parse cursor
   for that part's tracks. Crescendi/decrescendi are spans —
   approximate by interpolating between the start and end dynamics
   into N intermediate `VelocityChange`s (N ≈ one per beat is enough
   for V1).

2. **`<articulations>`** — accent variants get a relative bump on
   top of the prevailing dynamic for that single onset:

   | Articulation | Velocity delta |
   |---|---:|
   | `<accent>` | +15 |
   | `<strong-accent>` (marcato) | +25 |
   | `<staccato>` (no velocity change — articulation, not dynamic) | 0 |

   Implemented as a `VelocityChange` at the note's tickMs, then
   another one immediately after restoring the prevailing dynamic.

The `<ghost-note>` notation (used heavily in jazz/percussion scores)
maps to a *negative* delta (≈ −30) — same mechanism, opposite sign.

## JSON sidecar

A new `velocity.json` joins the existing per-piece folder layout:

```
import-cache/<piece-id>/
    score.json
    pedaling.json
    velocity.json   ← NEW
    pieceinfo.json
```

Format mirrors `pedaling.json`:

```json
{
  "byTrack": {
    "RH": { "changes": [
        { "tickMs": 0,    "velocity": 80 },
        { "tickMs": 4000, "velocity": 96 }
    ]},
    "LH": { "changes": [
        { "tickMs": 0, "velocity": 64 }
    ]}
  }
}
```

`MxlSplitJsonWriter` adds a `writeVelocity(...)` step; reader does
the inverse plus stale-file sweep. Same pattern as pedaling.

## AutoVelocity — sensible default

Mirroring `AutoPedaling`: when an import has no `Velocities`
populated (or the user explicitly bypasses source dynamics), an
`AutoVelocity` helper synthesizes a baseline.

V1 heuristic — three layers:

```java
public final class AutoVelocity {
    public static Velocities generate(Performance perf, TimeSignature ts);
}
```

Per pitched track, walk bar boundaries:

1. **Beat-position accents** — beat 1 of each bar gets +5 over the
   ambient dynamic; beat 3 (in 4/4) gets +3. Mild — enough to add a
   pulse without sounding mechanical.
2. **Phrase-shape inflection** — over a phrase span, raise the
   ambient by a few units mid-phrase, drop slightly at phrase end.
   (Phrase boundaries from `Bar` repeat structure when MXL; segment
   spans for DSL pieces.)
3. **Random ±3 jitter** — already-humanised feel without re-running
   the timing humaniser. Seeded so playback is deterministic.

For drum tracks, see "Auto-drum integration" below — drums are
strategy-driven and don't need AutoVelocity.

The `Performance` ⇒ `Velocities` shape means this can be a static
fallback that fires in the UI when:

- Source `Velocities` is empty AND
- The user has selected an "Auto velocity" mode (when we surface one)

Default UI behaviour: source velocities if present, else AutoVelocity
output, else uniform vel 90. Same fallback chain as the auto-pedal
tri-state.

## Auto-drum integration

Drum strategies currently emit a `DrumTrack` (a `List<DrumNote>`).
With velocity, they additionally produce a `VelocityControl` for that
track:

```java
public interface DrumStrategy {
    Optional<DrumTrack>      generate(Piece source, Energy energy);
    default VelocityControl  velocityFor(DrumTrack generated, Energy energy) {
        return VelocityControl.empty();
    }
}
```

`PatternSpec` gains an optional per-slot `int velocity`:

```java
public record PatternSpec(
        List<PercussionSound> slots,
        List<Integer> slotVelocities,   // null or same length as slots; null = uniform vel 90
        ...) { }
```

Strategies opt in by setting `slotVelocities`; existing strategies
that don't care leave it null and get uniform 90.

The auto-drum bake step then assembles both the `DrumTrack` and the
matching `VelocityControl`, and the host (`NotationApp`) merges that
control into the piece's `Velocities`.

## UI — deferred

V1 has no velocity-specific UI. Behaviour:

- Imports use source velocities when present.
- Library pieces use `AutoVelocity` when enabled (probably tied to an
  existing toggle, not a new one).
- A future per-track velocity curve overlay on the pitch scroll
  could visualise the timeline (parallel to the pedal tint regions),
  but that's V2.

## A wider point — side-channels as the project's expressivity story

Velocity is one of several instrument-specific expressivity layers
that don't belong on the abstract note. The pattern that's emerged:

```
Performance
├─ Score                   (composer-authored content)
├─ TempoTrack              (interpretation: when)
├─ Instrumentation         (interpretation: what voices)
├─ Volume                  (interpretation: per-track CC #7)
├─ Articulations           (interpretation: per-onset attack-shape hints)
├─ Pedaling                (interpretation: piano sustain CC #64)
└─ Velocities  ← new       (interpretation: per-note attack strength)
```

Each side-channel is:

- Per-track keyed (`Map<TrackId, FooControl>`)
- Sparse + canonical (sorted, deduped) at construction
- Optional — `Performance.of(score)` defaults all to empty
- Round-trippable through MIDI (write always; read may approximate)
- Round-trippable through JSON sidecar (split-json reader/writer)
- Composable with other side-channels at codec emission

**Future side-channels likely to follow this shape:**

| Feature | Instrument | MIDI mapping | Likely shape |
|---|---|---|---|
| Sostenuto | Piano | CC #66 | Mirror `Pedaling` — same data, different CC byte |
| Una corda | Piano | CC #67 | Mirror `Pedaling` |
| Pitch bend | Electric guitar, synth, MPE | Pitch wheel (-8192..8191) | `PitchBend` per-track sparse timeline; values continuous, possibly interpolated |
| Modulation | Electric guitar, strings, synth | CC #1 | Mirror `Volume` shape (sparse 0..127 timeline) |
| Expression | Wind, brass, strings | CC #11 | Mirror `Volume` |
| Channel aftertouch | Synth, MPE | Channel pressure | Mirror `Volume` |
| Polyphonic aftertouch | MPE keys | Per-key pressure | Per-(track, midi) sparse timeline |

The architectural rule: when a new performance dimension is needed,
add a side-channel alongside the existing ones. Don't extend the
abstract note records. Composition stays small and stable;
interpretation grows as the tool grows.

This doc therefore also covers "why we won't add `int velocity` to
`PercussionNote`" as a doctrine point. New contributors looking to
add e.g. "tremolo depth on `PitchedNote`" should land at this doc and
follow the side-channel pattern instead.

## Implementation plan

Order of attack — each step independently testable + shippable:

| # | Item | Status |
|---|---|---|
| 1 | Records: `VelocityChange`, `VelocityControl`, `Velocities` | ✅ Shipped (in `notation-expressivity`) |
| 2 | `Performance` 7th field + backwards-compat constructors | ✅ Shipped |
| 3 | `MidiCodec.toMidi` reads `Velocities` per-note | ✅ Shipped — step-function lookup, default 90 |
| 4 | `MidiCodec.fromMidi` back-fills `Velocities` | ✅ Shipped — drops the trivially-default control on read so empty round-trips |
| 5 | JSON sidecar — `velocity.json` writer + reader | ✅ Shipped — `MxlSplitJsonWriter/Reader` |
| 6 | MXL parser — `<dynamics>` → `VelocityChange` | ✅ Shipped — same dynamics events drive both Volume and Velocities |
| 6b | MXL parser — `<accent>` / `<strong-accent>` velocity bumps | ⏳ Queued — `+15` / `+25` over prevailing dynamic at the note's tickMs |
| 7 | `AutoVelocity.generate(...)` + tests | ✅ Shipped — beat-1 +5, mid-bar +3, ±2 jitter; tempo-aware |
| 8 | Auto-drum: `PatternSpec.slotVelocities` + bake | ✅ Shipped — all 6 strategies opted in |
| 8b | Auto-drum: source-aware kick alignment with bass | ✅ Shipped — `+5` boost when kick lines up with source bass onset (±1/16 bar) |
| 8c | Auto-drum: tempo-arrangement-aware bake | ⏳ Queued — currently uses constant tempo |
| 9 | NotationApp wires `Velocities` through play / export | ✅ Shipped |
| 10 | UI overlay for per-track velocity curves | ⏳ Queued (V2) |

Steps 1–5 (foundation), 6–8 (source-or-auto fallback + auto-drum
opt-in) and 9 (UI wiring) all landed in one push after the doc was
drafted. Items 6b, 8c, and 10 remain — see [roadmap.md](roadmap.md)
for current priority.

## Limitations / open questions

1. **Crescendo fidelity.** Step-function approximation of MXL
   crescendi loses the smooth ramp. Acceptable for V1; revisit if a
   piece sounds wrong. Future: optional interpolation flag on
   `VelocityControl`.
2. **Polyphonic differentiation in chords.** Two notes sharing a
   tickMs share a velocity. This matches musical convention (chord
   loudness as a unit) and is what every DAW does for default
   chord-velocity behaviour. Per-voice velocity would need MPE-style
   per-note channelisation — out of scope.
3. **Dynamic range mapping.** The `pp..fff` table above is a
   convention; some sources call ff = 110 and others 120. Bake the
   table into a `DynamicMarking` enum so it's discoverable + easily
   tunable.
4. **Round-trip parity.** As with `Pedaling` and `Volume`, the codec
   doesn't preserve the *shape* of `Velocities` through round-trip —
   only the audible result. Documented contract.
5. **Track-mode interaction.** Bass-aware accent detection (auto-pedal
   V2) and velocity-shaped accents could collaborate — kick velocity
   tracks bass-line dynamics. Phase 2 of source-aware drumming.

## Related docs

- [auto-drum-realism-plan.md](auto-drum-realism-plan.md) — velocity is
  item #3 in that roadmap; this doc is the deep-dive on the data model.
- [auto-pedal.md](auto-pedal.md) — same side-channel pattern (`Pedaling`).
- [pedal-visualisation.md](pedal-visualisation.md) — eventual reference
  for how a velocity overlay would slot into the pitch scroll.
