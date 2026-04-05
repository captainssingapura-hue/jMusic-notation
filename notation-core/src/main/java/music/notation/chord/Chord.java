package music.notation.chord;

import music.notation.pitch.Pitch;

import java.util.List;

/**
 * A named chord voicing rooted at a specific pitch.
 *
 * <p>Each permitted record encodes a chord <em>quality</em> (major, minor, …)
 * together with its root note and octave, and computes the constituent
 * {@link Pitch}es via interval arithmetic.</p>
 *
 * <p>Usage in song files:
 * <pre>{@code
 *     final var I  = new MajorTriad(C, 3);
 *     final var iv = new MinorTriad(F, 3);
 *     final var V7 = new DominantSeventh(G, 3);
 *     chord(WHOLE, I);   // → ChordEvent with the triad's pitches
 * }</pre>
 */
public sealed interface Chord
        permits MajorTriad, MinorTriad, DiminishedTriad, AugmentedTriad,
                DominantSeventh, MajorSeventh, MinorSeventh {

    /** The pitches of this chord in root position, lowest to highest. */
    List<Pitch> pitches();
}
