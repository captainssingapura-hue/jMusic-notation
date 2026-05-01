package music.notation.experiments.blues;

import music.notation.experiments.blues.minor.BluesMinorDegree;
import music.notation.experiments.blues.minor.BluesMinorNote;
import music.notation.experiments.scale.TimedNote;

import java.util.List;

import static music.notation.experiments.blues.minor.BluesMinorDegree.*;

/**
 * A short 8-bar blues motif authored in abstract {@link BluesMinorNote}
 * terms. Uses all six degrees of the hexatonic scale, so the
 * characteristic ♭V "blue note" appears prominently — and when the motif
 * is re-coloured through {@link music.notation.experiments.blues.major.BluesMajorNote}
 * the same contour reveals a completely different mood (III replaces ♭V
 * at degree index 3; II/III replace ♭III/IV at indices 1–2).
 *
 * <p>Structure (4/4, 120 BPM):</p>
 * <pre>
 *   Bar 1:  I ♭III IV ♭V               — classic blues climb, landing on the blue note
 *   Bar 2:  V ♭VII V ♭V                — high wail + step down to the blue note
 *   Bar 3:  IV ♭III I IV               — descend then accent IV
 *   Bar 4:  ♭III I (h)                 — partial cadence, breath
 *
 *   Bar 5:  I I ♭III IV                — pick up the line again
 *   Bar 6:  V ♭V IV ♭III               — the blue-note wail going the other way
 *   Bar 7:  ♭III IV V ♭VII             — final build-up to the high VII
 *   Bar 8:  V ♭III I (h)               — resolution to tonic
 * </pre>
 */
public final class BluesSong {

    private BluesSong() {}

    private static final int Q = 500;   // quarter (120 BPM)
    private static final int H = 1000;  // half

    private static final int OCTAVE = 4;

    /** 8-bar blues motif, 32 notes, 32 000 ms, all six degrees exercised. */
    public static List<TimedNote<BluesMinorNote>> demo() {
        return List.of(
                // Bar 1: I ♭III IV ♭V
                n(I, Q), n(III_FLAT, Q), n(IV, Q), n(V_FLAT, Q),
                // Bar 2: V ♭VII V ♭V
                n(V, Q), n(VII_FLAT, Q), n(V, Q), n(V_FLAT, Q),
                // Bar 3: IV ♭III I IV
                n(IV, Q), n(III_FLAT, Q), n(I, Q), n(IV, Q),
                // Bar 4: ♭III I--
                n(III_FLAT, H), n(I, H),

                // Bar 5: I I ♭III IV
                n(I, Q), n(I, Q), n(III_FLAT, Q), n(IV, Q),
                // Bar 6: V ♭V IV ♭III
                n(V, Q), n(V_FLAT, Q), n(IV, Q), n(III_FLAT, Q),
                // Bar 7: ♭III IV V ♭VII
                n(III_FLAT, Q), n(IV, Q), n(V, Q), n(VII_FLAT, Q),
                // Bar 8: V ♭III I--
                n(V, Q), n(III_FLAT, Q), n(I, H)
        );
    }

    private static TimedNote<BluesMinorNote> n(BluesMinorDegree d, int durationMs) {
        return TimedNote.of(BluesMinorNote.of(d, OCTAVE), durationMs);
    }
}
