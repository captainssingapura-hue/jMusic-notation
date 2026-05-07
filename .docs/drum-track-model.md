# Drum Track Model

## Summary

| Layer       | Drum-track count                     | Source                                         |
|-------------|--------------------------------------|------------------------------------------------|
| `Piece`     | 0 or more `DrumTrack`s               | One per source — typically one per imported MIDI drum source, or however many an authored song chooses to declare. |
| `Score` (in `Performance`, audio output) | At most 1 (merged if needed) | MIDI channel 9 hosts a single drum kit. |

In practice the Piece almost always carries **one** `DrumTrack`. The asymmetry exists only for the rare case where a `Piece` legitimately has more than one (e.g. a hand-authored song wanting two named drum lanes). `PieceConcretizer` collapses any such multi-`DrumTrack` `Piece` to a single channel-9 stream when it produces the `Score`.

## Why it's *one* lane per source — and not one per percussion piece

The first cut split each percussion piece (kick / snare / hi-hat / …) into its own `DrumTrack` so simultaneous strikes (kick + crash on beat 1) stayed on monophonic-by-construction timelines. That's musically correct but visually awful — a typical kit explodes into 8–16 lanes in the UI.

We chose instead to keep one drum lane per source and **micro-stagger same-quantum collisions by 1 sixty-fourth**. At 120 BPM, 1 sf is ≈ 4 ms — well below human perceptual threshold for rhythm. Every hit is preserved, no notes are lost, and the UI stays compact.

## How simultaneous strikes are handled

`DrumBarBuilder.build(List<Hit> hits, Config cfg)` takes a mixed-percussion hit stream and:

1. Sorts hits by onset.
2. Quantizes each onset to the nearest sixty-fourth.
3. When two hits land on the same quantized onset, the second is shifted to `cursor + 1 sf` and so on for further collisions in the same quantum.
4. Each hit's duration is clipped to fill the gap before the next event.
5. Decomposes into legal `Quantizer` durations; emits `PercussionNote(sound, duration)` for the head chunk and `RestNode`s for any decomposition tail.

Because the stagger is sub-perceptual, the resulting pattern *sounds* like simultaneous strikes — but every hit is structurally representable in the sequential `Bar` node list.

## Why an asymmetry still exists at all

`Score` enforces "at most one DRUM track" (see `Score.java`) — MIDI channel 9 can host only one kit. A `Piece` that nevertheless has multiple `DrumTrack`s (an authored song with two named drum parts, say) needs them collapsed at concretize time. `PieceConcretizer.concretize(piece)` does this defensively even though the importer never triggers it now.

## Where this matters in code

- `notation-performance/.../DrumBarBuilder.java` — micro-stagger logic.
- `notation-performance/.../PerformanceImporter.java` — emits one `DrumTrack` per source drum.
- `notation-play/.../PieceConcretizer.java` — defensive `Score`-side merge for multi-`DrumTrack` Pieces.
- `notation-performance/.../Score.java` — the "at most one DRUM track" invariant.

## Future direction

If per-percussion-piece UI controls (mute, volume, swap kit) ever become useful, the natural extension is **inside one `DrumTrack`**, e.g. a UI filter that mutes specific `PercussionSound`s during playback. The structural model wouldn't change.

The other long-term option — a `PolyPercussionNode` `PhraseNode` variant — would let one `Bar` position carry multiple simultaneous sounds with no staggering at all. Worth doing if we ever want bit-exact roundtrip of human-played drum performance, but not needed now.
