package music.notation.transcribe;

/**
 * Per-frame loudness, measured over a short window independent of the
 * pitch detector's own analysis window. A pitch window has to span
 * several periods (64 ms for YIN), which smears a 40 ms break between
 * two sung notes into a shallow dip; 20 ms keeps it deep enough for the
 * segmenter to see. Every detector reports this value as
 * {@code PitchEstimate.rms}, so segmentation behaves the same whichever
 * detector produced the pitch.
 */
final class FrameEnergy {

    static final double WINDOW_MS = 20;

    private FrameEnergy() {}

    /** RMS of {@code WINDOW_MS} of audio centred on sample {@code centre}; zero outside the clip. */
    static double rms(float[] x, int centre, int rate) {
        int len = Math.max(1, (int) Math.round(WINDOW_MS * rate / 1000.0));
        int start = centre - len / 2;
        double s = 0;
        for (int k = Math.max(0, start); k < Math.min(x.length, start + len); k++) s += (double) x[k] * x[k];
        return Math.sqrt(s / len);
    }
}
