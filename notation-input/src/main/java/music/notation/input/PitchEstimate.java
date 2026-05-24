package music.notation.input;

/**
 * One frame's worth of pitch information emitted by a real-time pitch
 * detector (e.g. {@link YinPitchDetector} running over a sliding window
 * of mic audio).
 *
 * @param tickMs      monotonic time of the window's centre, in ms from
 *                    the input source's session start. Comparable across
 *                    estimates from the same source.
 * @param midiFloat   the detected pitch expressed in fractional MIDI
 *                    semitones (e.g. 60.0 = middle C, 60.5 = a quarter
 *                    tone above). {@link Double#NaN} when the frame is
 *                    too quiet to detect a pitch, or when the detector
 *                    has no confident reading.
 * @param confidence  the detector's confidence in {@code midiFloat},
 *                    typically 0.0 (no confidence, suppressed) through
 *                    1.0 (high confidence). Useful for filtering noisy
 *                    frames downstream.
 * @param rms         root-mean-square amplitude of the analysed window,
 *                    in normalised {@code [0, 1]} units. Used by the
 *                    segmenter for energy gating and by downstream
 *                    consumers for velocity mapping.
 */
public record PitchEstimate(
        long tickMs,
        double midiFloat,
        double confidence,
        double rms) {

    /** True when the detector returned a usable pitch reading. */
    public boolean hasPitch() {
        return !Double.isNaN(midiFloat);
    }

    /** A {@link PitchEstimate} carrying no pitch — used when the window is silent. */
    public static PitchEstimate silent(long tickMs, double rms) {
        return new PitchEstimate(tickMs, Double.NaN, 0.0, rms);
    }
}
