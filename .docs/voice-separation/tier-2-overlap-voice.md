# Tier 2 — Overlap-Based Voice Split

> **Within a single pitch band, a sustained pad and a rhythmic line
> coexist. Tier 2 detects that they're separate voices by looking at
> *which notes are still sounding when a new one starts*.**

---

## Problem it solves

Tier 1 hands you "right hand" and "left hand". But the right hand
of a typical pop arrangement contains *both*:

- A **melody** in eighths.
- A **sustained chord pad** held under it.

Both live in the same register, so Tier 1 leaves them mixed in one
output stream. Tier 2 takes that stream and splits it again — this
time by *time overlap* rather than register.

The principle: **a single voice plays only one note (or chord) at
a time**. If a new note starts while an existing note is still
sounding, the new note must belong to a *different voice*.

## Algorithm sketch

```
input:  events  = list of GroupedEvent (Tier 0 output, possibly Tier 1-filtered)
        max_voices = upper bound on output streams (default 4)
output: voices = list of voice streams; each voice is monophonic in time

voices = []   # list of {last_end_tick, last_pitch, events: []}

for each event in events sorted by onset_tick:
    candidates = [v for v in voices if v.last_end_tick <= event.onset_tick]

    if candidates:
        # pick the candidate whose last note ended most recently AND
        # whose last pitch is closest to this event's centroid
        best = argmin over candidates of
                 ( (event.onset_tick - v.last_end_tick) * TIME_WEIGHT
                 + abs(centroid(event) - v.last_pitch)  * PITCH_WEIGHT )
        best.events.append(event)
        best.last_end_tick = event.onset_tick + event.duration_ticks
        best.last_pitch    = centroid(event)
    elif len(voices) < max_voices:
        # start a new voice
        voices.append(new voice initialized with event)
    else:
        # forced overlap — assign to the voice whose pitch is closest
        # (the new note will overwrite/cut the existing one;
        #  acceptable for the slice-1 simple case)
        nearest = argmin over voices of abs(centroid(event) - v.last_pitch)
        nearest.events.append(event)
        # update; some musical information is lost here
```

The two weights `TIME_WEIGHT` and `PITCH_WEIGHT` are the
algorithm's only tunable knobs. Reasonable defaults:

- `TIME_WEIGHT = 1` (per tick)
- `PITCH_WEIGHT = 16` (one semitone is worth ~16 ticks of
  inactivity; encourages voices to keep similar pitch contours)

`centroid(event)` is the mean pitch when the event is a chord
(Tier 0 output may include chords — Tier 2 keeps chords as atomic
units belonging to one voice).

## Diagram

Input — Tier 1's "right hand" output, two musical lines mixed:

```
midi pitch
  ▲
72│ ●─────●─────●─────●─────●─────●─────  (melody — eighth notes)
  │
68│
  │
64│ ●═══════════════════════════════════  (pad — whole note)
  │ │
  └─┼──┬──┬──┬──┬──┬──┬──┬──► time
   t0 t1 t2 t3 t4 t5 t6 t7
```

The pad attacks at t0 and is still sounding when the melody's
notes at t1, t2, t3 … attack. Tier 2 sees the overlap and assigns
them to separate voices.

After Tier 2 split:

```
VOICE A (melody)                   VOICE B (pad)
midi pitch                          midi pitch
  ▲                                   ▲
72│ ●  ●  ●  ●  ●  ●  ●  ●          68│
  │                                   │
  │                                  64│ ●═══════════════════
  └─────────────────► time             │
   t0 t1 t2 t3 …                       └────────────────► time
                                        t0
```

Each voice is now monophonic — at most one note (or chord) sounding
at any instant.

## Why it works

- **Polyphony is the signature of multiple voices.** If the input
  is genuinely monophonic (single melody line), Tier 2 produces one
  output voice containing every input note — correctly identifying
  that there's only one voice present. **No false positives on
  monophonic input.**

- **The greedy "extend the most recently freed voice" heuristic
  matches how composers and arrangers think.** A new note in the
  alto register is more likely a continuation of the alto line than
  a sudden new alto entrance.

- **Pitch-distance weighting prevents voice-crossing.** Without the
  pitch term, the algorithm could swap voices whenever both happen
  to be free at the same moment. With it, voices prefer to stay in
  their pitch register.

## Where it fails

| Situation | Failure mode | Notes |
|---|---|---|
| Genuine voice-crossing (a fugue subject crossing the answer) | Greedy assignment commits early; can't backtrack | Tier 3 needed |
| Pad chord changes mid-melody (e.g. Cmaj → Fmaj transition under a held melody) | Pad's first chord ends, second chord starts; greedy may move melody to pad voice if timing is unlucky | Reduce TIME_WEIGHT or pre-cluster chords by registral identity |
| 5+ voices densely overlapping (Bach four-part chorale + bass pedal) | `max_voices` cap forces collisions | Raise cap; or use Tier 3 |
| Fast trill above sustained note | Trill notes correctly assigned to one voice; sustained note correctly assigned to another | Works as intended |

The first row is the genuine theoretical limit of any greedy
algorithm. Tier 3 (Contig) addresses it with global optimization.

## Implementation notes

Lives in `notation-performance/.../OverlapVoiceSplitter.java`:

```java
public final class OverlapVoiceSplitter {
    public record SplitResult(List<List<GroupedEvent>> voices) {}

    public record Config(int maxVoices, double timeWeight, double pitchWeight) {
        public static Config defaults() { return new Config(4, 1.0, 16.0); }
    }

    public static SplitResult split(List<GroupedEvent> events) {
        return split(events, Config.defaults());
    }

    public static SplitResult split(List<GroupedEvent> events, Config cfg) { ... }
}
```

- Output `voices` is sorted: voice 0 has the highest mean pitch
  (the "soprano"); voice N-1 has the lowest. This stable order
  makes downstream naming consistent.
- Empty voices are dropped from the result.
- Each output voice's events stay in onset order.

## Test cases

```
1.  Monophonic input → 1 voice with all input events
2.  Two non-overlapping notes → 1 voice (no overlap, no split)
3.  Two simultaneous notes of identical duration → still 1 voice (Tier 0
    should have made them a chord; if not, they're treated as 2 voices)
4.  Held whole-note + 8 eighth-notes above → 2 voices (pad + melody)
5.  Three-voice chorale → 3 voices (S/A/B)
6.  Voice-crossing pair → assert specific (failing) behaviour to document the limit
7.  6 simultaneous voices with maxVoices=4 → 4 voices, 2 voices' notes get
    folded into the nearest pitch; assert no notes are dropped
8.  Output voice ordering: highest-pitch-mean voice is index 0
```

## Status in this codebase

Not implemented. New class proposed for step 2.2b. Depends on
Tier 0 (`OnsetGrouper`) for input shape.
