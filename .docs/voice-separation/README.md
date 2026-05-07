# Voice Separation — Tiered Approaches

Voice separation is the problem of taking a single stream of MIDI
notes — onsets, durations, pitches, all interleaved on one track —
and recovering the *N* musically meaningful **voices** that the
composer originally wrote.

A "voice" in this sense is a melodic line: monophonic at any
given instant (no two notes from the same voice sound at once),
continuous in pitch (large leaps are unusual), and intentional
(the composer chose to give it to one logical performer — left hand,
soprano, bass, etc.).

This folder describes a **5-tier ladder** of approaches, ranked by
implementation cost and quality of result:

| Tier | Name                | LOC est. | Quality          | Use case                                     |
|------|---------------------|----------|------------------|----------------------------------------------|
| [0](tier-0-onset-grouping.md)  | Onset grouping      | ~30      | Always-on, free  | Coalesce same-tick notes into chords         |
| [1](tier-1-pitch-band.md)      | Pitch-band split    | ~80      | 70 % for piano   | Quick "RH / LH" hand separation              |
| [2](tier-2-overlap-voice.md)   | Overlap voice split | ~150     | 85 % for piano   | Catches in-band overlap (pad + melody on RH) |
| [3](tier-3-contig-streaming.md)| Contig / Chew-Wu    | ~400     | Published-grade  | Counterpoint, fugues, multi-line composition |
| [4](tier-4-role-classifier.md) | Role classifier     | ~200     | Post-process     | Names voices: melody / bass / pad / chords   |

Tiers compose: **Tier 0 always runs first**; Tier 1 is enough for
keyboard music; adding Tier 2 catches the overlap cases Tier 1
misses; Tier 3 is the principled answer for genuinely contrapuntal
material; Tier 4 sits on top of any of 1–3 to assign musical roles
to the resulting voices.

## How to read these docs

Each tier doc has the same shape:

1. **Problem it solves** — the specific musical situation that motivates the tier.
2. **Algorithm sketch** — the actual procedure, in pseudocode.
3. **Diagram** — a before/after ASCII illustration on a tiny example.
4. **Why it works / where it fails** — honest about the music it can't handle.
5. **Implementation notes** — Java skeleton, dependencies on `notation-performance` primitives.
6. **Test cases** — what to assert.

## What "voice separation" is *not*

- **Not pitch detection.** MIDI already gives us exact pitches.
- **Not onset detection.** MIDI already gives us exact onsets.
- **Not transcription.** We're not turning audio into notes; we're
  taking already-precise note events and grouping them.
- **Not orchestration.** We're identifying *what's there*, not
  deciding what each voice should sound like — that's what the
  arrangement layer does *after* separation.

## Recommended reading order

1. Start with [Tier 0](tier-0-onset-grouping.md) — it's a primitive
   every other tier depends on.
2. Read [Tier 1](tier-1-pitch-band.md) and [Tier 2](tier-2-overlap-voice.md)
   together — they're both pragmatic heuristics, and Tier 2 builds on
   Tier 1's output.
3. [Tier 3](tier-3-contig-streaming.md) is the principled answer; read
   it once you understand why Tier 1+2 are insufficient for some
   inputs.
4. [Tier 4](tier-4-role-classifier.md) is orthogonal to 1–3 — read it
   whenever you need to put musical names on the voices the lower
   tiers produced.

## Pipeline placement

```
MIDI bytes
   │
   ▼
MidiCodec.fromMidi  ────►  Performance (per-track flat note streams)
                                  │
                                  ▼
                          ┌───────────────────┐
                          │  Voice Separation │   ◄── these docs
                          │   Tier 0  always  │
                          │   Tier 1  +       │
                          │   Tier 2  +       │
                          │   Tier 3  altpath │
                          └───────────────────┘
                                  │
                                  ▼
                       N monophonic voice streams
                                  │
                                  ▼
                          Quantizer + TieSplitter
                                  │
                                  ▼
                       List<Bar> per voice
                                  │
                                  ▼
                       MelodicTrack per voice
                                  │
                                  ▼
                  Tier 4 role classifier (optional)
                                  │
                                  ▼
                       Piece (codified base piece)
```
