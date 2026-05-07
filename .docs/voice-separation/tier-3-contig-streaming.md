# Tier 3 — Contig (Chew–Wu) Voice Streaming

> **The principled answer. A globally-optimised voice partition based
> on Elaine Chew's Contig algorithm. Handles voice-crossing,
> counterpoint, and arbitrary polyphony — at significant cost.**

---

## Problem it solves

Tier 2 is greedy: it commits to a voice assignment for note *t* without
looking ahead to note *t+1*. That's fine for keyboard music where
voices stay in their lanes, but it breaks down for:

- **Counterpoint** (Bach inventions, fugues) — voices routinely cross
  each other.
- **Voice imitation** — soprano hands a motif down to alto, then alto
  to tenor; greedy fails because each handoff looks like a "new
  voice" attack.
- **Wide-range single voice** with a sudden register leap — greedy
  starts a new voice instead of recognising it as one phrase.

These cases need an algorithm that can *reason about the whole
sequence at once*, weigh alternative partitions, and pick the one
with minimum cumulative cost.

## Algorithm sketch (after Chew & Wu, 2004)

The Chew–Wu **Contig algorithm** has three phases:

### Phase 1 — segment into "contigs"

A *contig* is a maximal interval of time in which the **number of
simultaneously sounding notes is constant**. Boundaries occur at any
onset or offset that changes the polyphony count.

```
notes:    [─────A─────][─────B─────]
                  [────C────]
                       [────D────]
contigs:  | 1 |  2  | 3 | 2 | 2 | 1 |
```

Inside any one contig, every voice is unambiguously one of the *N*
sounding notes — there's no ambiguity, only a choice of which note
goes to which voice slot.

### Phase 2 — within each contig, sort notes by pitch

Within a contig of *N* notes, sort them ascending by pitch. This
gives a canonical "voice 0 = lowest, voice N-1 = highest" labeling
local to the contig.

### Phase 3 — connect contigs with minimum-cost matching

Adjacent contigs may have different voice counts (e.g., a 3-voice
contig followed by a 2-voice contig). The transition needs to decide
which voice "ends" or "starts." Build a bipartite graph between the
voice-slots of contig *k* and contig *k+1*; edge weights are the
pitch distance plus a small penalty for ending or starting a voice.
Find the **minimum-cost matching** (Hungarian algorithm or simple
DP for small N).

The result: a global voice-stream assignment that minimises total
pitch motion across the entire piece, while respecting the local
constraint that voices inside any contig don't cross each other.

## Diagram

Input — Bach two-part invention with voice-crossing:

```
midi pitch
  ▲
72│ ●         ●─●               ●
  │   ●         ●─●─●         ●
68│     ●─●─●         ●─●─●
  │  (V1)            (V2)
64│                              ●─●
  │   ●                ●           ●
60│ ●─●─●         ●─●─●               ●
  │     ●─●         ●               ●─●
  │  (V2)            (V1)
  └──────────────────────────────────► time
```

Voices V1 and V2 cross at the marked times.

Tier 2 (greedy) fails: it would assign the upper note of every
contig to "voice 0" and the lower to "voice 1", which contradicts
the actual line continuity.

Tier 3 finds the assignment that minimises *total pitch motion across
the whole piece*. It correctly identifies that:

```
Voice 1: descends from 72 down through the crossing region back up
Voice 2: ascends from 60 up through the crossing region back down
```

Even though at the crossing instant V2 is *above* V1 in absolute
pitch — Tier 3 follows the line, not the register.

## Why it works

- **Local + global optimization.** The contig segmentation makes
  the voice count *locally* unambiguous; the matching step makes
  the voice *identity* globally optimal.

- **Linear in number of contigs.** Each contig has at most *N*
  notes (the maximum polyphony); each transition costs O(N²) for
  the bipartite match (or O(N³) for Hungarian, but N is small).
  Total: O(C × N³) where C is the number of contigs.

- **Published, tested, and used in real software.** Chew & Wu's
  algorithm is the basis of voice separation in `music21`,
  `Humdrum`, MIRtoolbox, and several commercial transcription
  products. It's a known good answer.

## Where it still struggles

| Situation | Limitation | Honest assessment |
|---|---|---|
| Hidden voice (a voice that pauses for several bars then re-enters) | Algorithm can't tell whether re-entry is the same voice or new | All voice-separation algorithms suffer this; it's musical interpretation |
| Voice with a very large leap (octave) | Pitch-distance cost may prefer to swap voices instead | Tunable: increase the "voice ending/starting" penalty |
| Drum tracks | Algorithm not meaningful (no melodic continuity) | Skip Tier 3 for drums; pass through unchanged |
| Polyphony > ~8 voices | Hungarian becomes the bottleneck | Cap or fall back to Tier 2 |

## Implementation notes

Lives in `notation-performance/.../ContigVoiceStreamer.java`. Substantially
larger than Tiers 0–2 — at least three internal records:

```java
public final class ContigVoiceStreamer {
    record Contig(long startTick, long endTick, List<GroupedEvent> notes) {}
    record VoiceSlot(int contigIdx, int voiceIdxInContig) {}
    record StreamingResult(List<List<GroupedEvent>> voices) {}

    // Phase 1: build contigs by sweeping note on/off events
    private static List<Contig> buildContigs(List<GroupedEvent> events) { ... }

    // Phase 2: sort each contig by pitch
    private static void labelByPitch(List<Contig> contigs) { ... }

    // Phase 3: cross-contig minimum-cost matching
    private static int[][] matchAdjacent(Contig prev, Contig next) { ... }

    public static StreamingResult stream(List<GroupedEvent> events) { ... }
}
```

- Hungarian algorithm: ~80 LOC if written from scratch; consider
  pulling in a small public-domain implementation. (We don't add
  major library dependencies.)
- For pieces with N ≤ 4 voices, brute-force matching (try all N!
  permutations) is faster than Hungarian and trivially obviously
  correct.

Total estimate: **400–500 LOC** across `ContigVoiceStreamer.java`
plus a separate `Hungarian.java`.

## Test cases

```
1.  Monophonic input → 1 voice
2.  Two non-overlapping notes → 1 voice (no contig with > 1 note)
3.  Two simultaneous monophonic lines → 2 voices
4.  Voice crossing test (constructed from Bach Invention 13 fragment) →
    voices follow line continuity, not pitch ordering
5.  Three voices with one voice silent for 4 beats → algorithm continues
    voice across the gap as long as cost is favourable
6.  Polyphony-count change at every contig boundary → voices smoothly
    appear/disappear; no "voice swap" jitter
7.  Drum track input → noop / passthrough behaviour
```

## Status in this codebase

Not implemented. **Optional for slice 1** — the user's stated case
(piano arrangements that are "messed up into one track") is well
served by Tier 1 + Tier 2. Tier 3 is the right answer if/when we
encounter genuine counterpoint that the lower tiers mishandle.

## References

- Chew, E. and Wu, X. (2004). "Separating Voices in Polyphonic
  Music: A Contig Mapping Approach." In *Computer Music Modeling
  and Retrieval*, LNCS 3310, pp. 1–20.
- Cambouropoulos, E. (2008). "Voice and Stream: Perceptual and
  Computational Modeling of Voice Separation." *Music Perception*
  26 (1), pp. 75–94.
- `music21.voiceLeading` and `music21.stream.makeVoices` —
  reference implementations.
