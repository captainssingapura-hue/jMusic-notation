# Tier 4 — Role Classifier

> **Post-process. Takes the *N* voices produced by Tier 1/2/3 and
> assigns each a musical role: melody, bass, pad, accompaniment,
> drums. Enables auto-naming, smart instrument defaults, and
> arrangement template targeting.**

---

## Problem it solves

After the lower tiers have done structural separation, you have:

```
Voice 0:  notes [...]
Voice 1:  notes [...]
Voice 2:  notes [...]
```

Three monophonic streams. Useful, but generic — they have no names,
no semantic identity. Downstream the user wants to:

- See "Melody / Bass / Pad" in the UI, not "Voice 0 / Voice 1 / Voice 2".
- Apply a **style template** like "U2 Rock arrangement" — which needs
  to know *which* voice is the melody so it can swap in a Lead Synth,
  *which* is the bass so it can swap in a Slap Bass, etc.
- Have sensible default instrument suggestions per voice.
- Decide which voice belongs in the right hand vs. left hand for
  sheet-music rendering.

Tier 4 turns the structural output of Tiers 1–3 into something the
arrangement layer can target.

## Algorithm sketch

For each voice, compute a small **feature vector** and apply a
rule-based classifier:

```
features per voice:
    pitch_mean        — average MIDI pitch
    pitch_std         — pitch standard deviation (melodic motion)
    pitch_range       — max - min
    note_density      — notes per bar
    rhythmic_entropy  — Shannon entropy of inter-onset intervals
    chord_ratio       — fraction of events that are chords (>1 pitch)
    leap_ratio        — fraction of intervals > a fifth
    sustain_ratio     — fraction of total time covered by held notes

role assignment (cascading rules):
    if voice is on MIDI channel 9         → DRUMS
    elif pitch_mean < 50 and chord_ratio < 0.3 and pitch_std < 5
                                          → BASS
    elif chord_ratio > 0.6 and note_density < 4
                                          → PAD
    elif chord_ratio > 0.3 and rhythmic_entropy < 1.0
                                          → CHORDS / RHYTHM
    elif pitch_std > 5 and leap_ratio < 0.25 and chord_ratio < 0.2
                                          → MELODY
    elif pitch_mean > 65 and sustain_ratio < 0.4
                                          → MELODY (high register, fast)
    elif pitch_mean between 50 and 65
                                          → INNER VOICE (alto/tenor)
    else                                  → ACCOMPANIMENT (catch-all)
```

The thresholds are pragmatic, not principled; tune them on a
validation corpus. Better long-term: replace the rules with a small
trained classifier (logistic regression or decision tree on a
hand-labelled dataset of ~50 pieces), which is still fast and stays
deterministic.

## Diagram

Three-voice input from Tiers 1–3:

```
midi pitch
  ▲
72│ ●─●─●─●─●─●─●─●─●─●─●─●     Voice 0
  │
60│ ─────────────────────────
  │
48│ ●═══●═══●═══●═══●═══●       Voice 1
  │
36│
  │ ●─●─●─●─●─●─●─●─●─●─●─●─●   Voice 2
  └─────────────────────────────► time
```

Feature extraction per voice:

| Voice | pitch_mean | pitch_std | density | chord_ratio | rhythmic_entropy |
|-------|-----------|-----------|---------|-------------|------------------|
| 0     | 70        | 4.2       | 8/bar   | 0.0         | 0.4              |
| 1     | 48        | 1.1       | 2/bar   | 1.0         | 0.0              |
| 2     | 36        | 2.0       | 8/bar   | 0.0         | 0.2              |

Tier 4 output:

```
Voice 0  →  MELODY        (high mean, moderate std, no chords, even rhythm)
Voice 1  →  PAD           (mid-low mean, low std, all chords, very low entropy)
Voice 2  →  BASS          (low mean, low std, no chords, walking eighths)
```

Names attached; arrangement templates can now target each voice
specifically.

## Why it works

- **The features are compositional fingerprints.** A bass line
  *almost always* has low pitch, low chord ratio, walking
  rhythmic motion. A pad *almost always* has high chord ratio and
  long sustain. Even a crude rule set gets >80 % accuracy on
  conventional Western tonal music.

- **Confidence scoring is natural.** Each rule's threshold can carry
  a margin; the classifier can return `(role, confidence)`. Low-
  confidence assignments surface in the UI as "(?)" so the user can
  override.

- **Roles are extensible.** Adding a "lead guitar" or "string section"
  role is a new rule, not a model retrain.

## Where it fails

| Situation | Failure mode | Notes |
|---|---|---|
| Solo piano (only one voice after separation) | Classified as MELODY by default | Correct for most input; users editing arrangements can re-label |
| Walking bass with chord punctuation (jazz comping) | chord_ratio is moderate; rules may pick BASS or CHORDS | Add a "JAZZ_BASS" role with its own signature |
| Inner voices that are neither melody nor accompaniment (Bach inventions) | Often classified as ACCOMPANIMENT | Acceptable; user can correct |
| Imitative counterpoint where voices share a single role | All voices get MELODY, which is structurally true but unhelpful for arrangement | Fugue mode: detect imitation and prefix the role with "Fugue · S/A/T/B" |

## Implementation notes

Lives in `notation-performance/.../VoiceRoleClassifier.java`:

```java
public final class VoiceRoleClassifier {
    public enum Role {
        MELODY, BASS, PAD, CHORDS, INNER_VOICE,
        ACCOMPANIMENT, DRUMS, UNKNOWN
    }

    public record Classification(Role role, double confidence) {}

    public static Classification classify(List<GroupedEvent> voice, boolean isDrumChannel) {
        var features = computeFeatures(voice);
        return applyRules(features, isDrumChannel);
    }

    public static List<Classification> classifyAll(List<List<GroupedEvent>> voices, BitSet drumChannels) { ... }
}
```

- Pure function, no state.
- The `classifyAll` method gets all voices at once because some
  rules are *relative* — "the highest-mean voice" makes sense only
  in the context of the others.
- Returns a parallel list to the input; same length, same order.

## Test cases

```
1.  All-rests voice (dropped Tier-1/2 artifact) → UNKNOWN with confidence 0
2.  Monophonic high-register line at 8 notes/bar → MELODY
3.  Whole-note chord block at 1 chord/bar → PAD
4.  Quarter-note walking bass below middle C → BASS
5.  Drum channel input → DRUMS regardless of feature values
6.  Two-voice symmetric counterpoint (both voices same features) → both MELODY,
    one tagged HIGH, one tagged LOW
7.  Synthetic mixed corpus (a curated set of 20 hand-labelled tracks) →
    > 80 % role accuracy
```

## Status in this codebase

Not implemented. **Strictly optional for slice 1** — without it, the
user just sees "Voice 0 / 1 / 2" in the UI and labels them manually.
Adding it later is fully additive: nothing in the data model changes;
it just adds metadata.

## Connection to arrangement templates

The whole point of Tier 4. Once each voice has a role, an
arrangement template like *Soul Techno* can declare:

```java
// Pseudocode for a template
if (voice.role() == MELODY)  swap_to(SYNTH_LEAD_SAWTOOTH);
if (voice.role() == BASS)    swap_to(SLAP_BASS);
if (voice.role() == PAD)     swap_to(SYNTH_PAD_WARM);
if (voice.role() == CHORDS)  swap_to(ELECTRIC_PIANO_1);
add_drum_track(STYLE_TECHNO_3_8);
```

…and apply itself to **any** imported piece, not just the
hand-coded `FurElise` it was originally built around. This is the
killer downstream use case.
