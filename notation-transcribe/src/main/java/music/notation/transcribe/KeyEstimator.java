package music.notation.transcribe;

import music.notation.structure.KeySignature;
import music.notation.structure.Mode;

import java.util.List;

/**
 * Krumhansl–Schmuckler key finding: correlate the melody's
 * duration-weighted pitch-class histogram with the Krumhansl–Kessler
 * major and minor profiles in all twelve rotations, and take the best.
 * Melody-only input confuses relative keys more than a full texture
 * would, but the tonic weighting of a sung line usually settles it.
 */
public final class KeyEstimator {

    private KeyEstimator() {}

    static final double[] MAJOR = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
    static final double[] MINOR = {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};

    /** Best key, or C major when there are no notes. */
    public static KeySignature estimate(List<TranscribedNote> notes) {
        double[] hist = new double[12];
        for (TranscribedNote n : notes) hist[Math.floorMod(n.midi(), 12)] += n.durationMs();
        double best = Double.NEGATIVE_INFINITY;
        int bestPc = 0; Mode bestMode = Mode.MAJOR;
        for (int pc = 0; pc < 12; pc++) {
            double maj = correlation(hist, MAJOR, pc), min = correlation(hist, MINOR, pc);
            if (maj > best) { best = maj; bestPc = pc; bestMode = Mode.MAJOR; }
            if (min > best) { best = min; bestPc = pc; bestMode = Mode.MINOR; }
        }
        return Keys.of(bestPc, bestMode);
    }

    private static double correlation(double[] hist, double[] profile, int tonic) {
        double mh = 0, mp = 0;
        for (int i = 0; i < 12; i++) { mh += hist[i]; mp += profile[i]; }
        mh /= 12; mp /= 12;
        double num = 0, dh = 0, dp = 0;
        for (int i = 0; i < 12; i++) {
            double h = hist[(i + tonic) % 12] - mh, p = profile[i] - mp;
            num += h * p; dh += h * h; dp += p * p;
        }
        return dh == 0 ? Double.NEGATIVE_INFINITY : num / Math.sqrt(dh * dp);
    }
}
