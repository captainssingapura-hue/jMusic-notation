# Tier 1 — Pitch-Band Split

> **Cheap. Splits a single piano-blob track into "right hand" and
> "left hand" by a fixed pitch cutoff. Gets you 70 % of the way for
> keyboard music in 80 lines of code.**

---

## Problem it solves

A typical piano MIDI is a single track with notes spanning four to
six octaves. Musically, the player's two hands inhabit two
*registers*:

- **Right hand**: melody, treble harmony, sometimes inner voices.
  Roughly C4 and above.
- **Left hand**: bass, bass-line motion, occasional accompaniment.
  Roughly B3 and below.

If we treat the whole stream as one voice, we can't:
- Assign different instruments per hand (slap bass on LH, lead on RH).
- Render two staves of sheet music.
- Edit one hand without disturbing the other.

Tier 1 makes the cut at a single pitch threshold and produces two
separate output streams.

## Algorithm sketch

```
input:  events = list of PitchEvent (single or chord), each with pitches[]
        cutoff = MIDI note number (default 60 = middle C)
output: rh_events, lh_events

for each event in events:
    high  = max(event.pitches)
    low   = min(event.pitches)

    if low >= cutoff:
        rh_events.append(event)
    elif high < cutoff:
        lh_events.append(event)
    else:
        # event straddles the cutoff: split the chord
        rh_pitches = [p for p in event.pitches if p >= cutoff]
        lh_pitches = [p for p in event.pitches if p <  cutoff]
        rh_events.append(event.with(pitches=rh_pitches))
        lh_events.append(event.with(pitches=lh_pitches))
```

The "straddles" branch matters for any voicing where the pianist
plays an octave or wide chord (e.g., C3 + G3 + C4 + E4). Without
splitting, the whole chord goes to one hand and ruins the
separation.

## Diagram

Input — one track, mixed register:

```
midi pitch
  ▲
72│           ●            ●            ●
  │     ●           ●            ●
60│  ────────────────────────────────────────  cutoff (middle C)
  │                       ●
48│  ●            ●               ●
  │        ●           ●     ●         ●
36│  ●
  └──────────────────────────────────────────► time
```

After Tier 1 split:

```
RIGHT HAND                              LEFT HAND
midi pitch                              midi pitch
  ▲                                       ▲
72│      ●         ●        ●           60│ ────────────────  cutoff
  │ ●         ●         ●                 │     ●
60│ ──────────────────────────             │ ●        ●     ●
  └──────────────────────► time          48│   ●         ●
                                          │ ●     ●
                                        36│   ●
                                          └──────────────► time
```

Same time axis, two register-bound streams.

## Why it works

- **Composers think in hands.** Keyboard repertoire from Bach
  inventions to Chopin nocturnes to pop ballads is *written* in
  two staves. The MIDI export flattened that; Tier 1 reconstructs
  a sensible approximation.

- **Chopping by pitch is correct most of the time.** Voice-leading
  tradition keeps a soprano line above the alto, alto above tenor,
  tenor above bass. For two-hand keyboard music, any single pitch
  cutoff between B3 and D4 separates the hands of >90 % of the
  repertoire.

- **The threshold is per-piece, not universal.** Bach inventions in
  high registers want a higher cutoff; Rachmaninoff bass thunder
  wants a lower one. Make the cutoff a parameter; expose it in the
  UI ("hand-split at: __").

## Where it fails

| Situation | Failure mode | Mitigation |
|---|---|---|
| Hand-crossing (LH plays above RH momentarily) | Crossed notes go to the wrong hand | Tier 3 handles this; Tier 1 doesn't |
| Inner voice that lives near the cutoff (alto C4 ↔ B3 oscillation) | Voice flickers between hands bar to bar | Adaptive cutoff per phrase, or use Tier 2 |
| Single-staff music (cello, vocal line) | Cuts a monophonic line in half if it crosses the cutoff | Detect single-line input and skip Tier 1 |
| Bass + chord on LH (typical pop ballad) | Both go to LH bucket — still one voice in the LH | Run Tier 2 on the LH output |

## Implementation notes

Lives in `notation-performance/.../PitchBandSplitter.java`:

```java
public final class PitchBandSplitter {
    public record SplitResult(
            List<GroupedEvent> high,
            List<GroupedEvent> low) {}

    /** Default cutoff = 60 (middle C). */
    public static SplitResult split(List<GroupedEvent> events) {
        return split(events, 60);
    }

    public static SplitResult split(List<GroupedEvent> events, int cutoffMidi) { ... }
}
```

- Input is a Tier-0-grouped event list (`List<GroupedEvent>`).
- Output preserves the input's relative order in each band.
- A straddling chord becomes one event in each band, **same onset
  and duration**, with disjoint pitch sets.
- Empty pitch sets are dropped — no zero-pitch chords leak through.

## Test cases

```
1.  All notes ≥ cutoff → all in 'high', empty 'low'
2.  All notes < cutoff → all in 'low', empty 'high'
3.  Mixed-register chord straddling cutoff → split into two events
4.  Cutoff exactly at a pitch → that pitch belongs to 'high' (≥ rule)
5.  Empty input → both lists empty
6.  Single chord that's all on one side → goes to that side, not split
7.  Hand-crossing example: LH playing C5 while RH plays G3 → goes to wrong hand
    (documented limitation; assert that it's broken so the test catches a future fix)
```

## Status in this codebase

Not implemented. New class proposed for step 2.2a.
