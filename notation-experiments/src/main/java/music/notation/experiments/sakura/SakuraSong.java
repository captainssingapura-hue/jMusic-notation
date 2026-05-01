package music.notation.experiments.sakura;

import music.notation.experiments.hirajoshi.HirajoshiDegree;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.scale.TimedNote;

import java.util.List;

import static music.notation.experiments.hirajoshi.HirajoshiDegree.*;

/**
 * さくら さくら (Sakura Sakura) — the beloved traditional Japanese folk
 * song about cherry blossoms, written here in pure Hirajoshi scale-degree
 * terms at octave 4.
 *
 * <p>Uses only the five Hirajoshi degrees — {@code I II III V VI} — which
 * in A-tonic Hirajoshi materialise as A, B, C, E, F: the exact pitch
 * palette of the traditional koto arrangement. Because the representation
 * is tonic-free, a single {@code ScaleTranspose} can re-colour the whole
 * song into Yo, Gong, Ryukyu, or any other {@code ScaleNote} family
 * without touching the melody definition.</p>
 *
 * <p>Structure (16 bars, 4/4, 120 BPM):</p>
 * <pre>
 *   Bars  1–2:  "Sakura, sakura"           — opening couplet
 *   Bars  3–4:  "Yayoi no sora wa"          — "the March sky"
 *   Bars  5–6:  "Miwatasu kagiri"           — "as far as the eye sees"
 *   Bars  7–8:  "Kasumi ka kumo ka"         — climax on V
 *   Bars  9–10: "Nioi zo izuru"             — climax repeated
 *   Bars 11–14: "Izaya, izaya"              — call to come see
 *   Bars 15–16: "Mini yukan"                — resolution to tonic
 * </pre>
 */
public final class  SakuraSong {

    private SakuraSong() {}

    /** Milliseconds per note value at 120 BPM. */
    private static final int Q  = 500;   // quarter
    private static final int H  = 1000;  // half
    private static final int DH = 1500;  // dotted half
    private static final int W  = 2000;  // whole

    /** Default octave for the entire melody — comfortable koto / alto range. */
    private static final int OCTAVE = 4;

    /** The Sakura melody as a list of Hirajoshi abstract notes with timing. */
    public static List<TimedNote<HirajoshiNote>> melody() {
        return List.of(
                // Bar 1–2: "Sa-ku-ra   sa-ku-ra"
                n(I, Q),   n(I, Q),   n(II, H),
                n(I, Q),   n(I, Q),   n(II, H),

                // Bar 3–4: "Ya-yo-i   no so-ra wa"  (A B C | B A---)
                n(I, Q),   n(II, Q),  n(III, H),
                n(II, Q),  n(I, DH),

                // Bar 5–6: "Mi-wa-ta-su   ka-gi-ri"
                n(I, Q),   n(II, Q),  n(III, H),
                n(II, Q),  n(I, DH),

                // Bar 7–8: "Ka-su-mi ka   ku-mo-ka"  (C C B- | A A E-)  — climb to V
                n(III, Q), n(III, Q), n(II, H),
                n(I, Q),   n(I, Q),   n(V, H),

                // Bar 9–10: "Ni-oi zo   i-zu-ru"  (same climb)
                n(III, Q), n(III, Q), n(II, H),
                n(I, Q),   n(I, Q),   n(V, H),

                // Bar 11–14: "I-za-ya,   i-za-ya,   mi-ni   yu-kan"
                //            (descending "call" motif, twice)
                n(I, Q),   n(II, Q),  n(III, Q), n(II, Q),
                n(I, Q),   n(II, Q),  n(I, Q),   n(VI, Q),
                n(I, Q),   n(II, Q),  n(III, Q), n(II, Q),
                n(I, Q),   n(II, Q),  n(I, Q),   n(VI, Q),

                // Bar 15–16: gentle resolution — "mi-ni" falling, "yu-kan" held
                n(III, Q), n(II, Q),  n(I, H),
                n(I, W)
        );
    }

    /** Compact constructor for a timed Hirajoshi note at the default octave. */
    private static TimedNote<HirajoshiNote> n(HirajoshiDegree degree, int durationMs) {
        return TimedNote.of(HirajoshiNote.of(degree, OCTAVE), durationMs);
    }
}
