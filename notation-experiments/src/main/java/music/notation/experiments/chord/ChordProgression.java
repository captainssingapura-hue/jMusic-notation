package music.notation.experiments.chord;

import music.notation.experiments.chinese.gong.GongDegree;
import music.notation.experiments.chinese.gong.GongNote;

import java.util.List;

import static music.notation.experiments.chinese.gong.GongDegree.*;

/**
 * A simple 4-chord progression in the {@link GongNote Gong} (major
 * pentatonic) scale. Uses pentatonic-native stacked-third chords so the
 * progression stays inside the scale without borrowing chromatic tones.
 *
 * <p>Chords (in C Gong = C D E G A):</p>
 * <pre>
 *   I    [I III V]    = C4 E4 G4      — tonic triad
 *   II   [II V VI]    = D4 G4 A4      — "sus" / ii7 colour
 *   III  [III VI I^5] = E4 A4 C5      — vi / Am colour
 *   I    [I III V]    = C4 E4 G4      — return to tonic
 * </pre>
 *
 * <p>Default duration per chord: 1000 ms (half note at 120 BPM).</p>
 */
public final class ChordProgression {

    private ChordProgression() {}

    private static final int DURATION_PER_CHORD_MS = 1000;

    /** Build the 4-chord progression, all in the given shape. */
    public static List<ScaleChord<GongNote>> demoIn(ChordShape shape) {
        return List.of(
                chord(shape, GongDegree.I,   4, GongDegree.III, 4, GongDegree.V,  4),  // tonic
                chord(shape, GongDegree.II,  4, GongDegree.V,   4, GongDegree.VI, 4),  // "sus"
                chord(shape, GongDegree.III, 4, GongDegree.VI,  4, GongDegree.I,  5),  // vi
                chord(shape, GongDegree.I,   4, GongDegree.III, 4, GongDegree.V,  4)   // tonic return
        );
    }

    private static ScaleChord<GongNote> chord(ChordShape shape,
                                              GongDegree d1, int o1,
                                              GongDegree d2, int o2,
                                              GongDegree d3, int o3) {
        return new ScaleChord<>(
                List.of(GongNote.of(d1, o1), GongNote.of(d2, o2), GongNote.of(d3, o3)),
                DURATION_PER_CHORD_MS,
                shape);
    }
}
