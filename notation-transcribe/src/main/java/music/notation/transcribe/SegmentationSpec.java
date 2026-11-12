package music.notation.transcribe;

/**
 * How a pitch curve is cut into notes. Times are in milliseconds and are
 * converted to frames using the track's hop.
 *
 * @param relativeFloorDb frames quieter than the take's loudest voiced
 *                        frame by more than this are treated as silence
 *                        (background noise that happens to have a
 *                        pitch); {@code 0} disables the gate
 * @param medianMs        width of the median filter applied to the pitch
 *                        curve — removes one- or two-frame spikes and
 *                        octave blips
 * @param minNoteMs       shortest note kept; also how long a new pitch
 *                        must persist before it counts as a new note
 * @param splitCents      how far the pitch must move from the current
 *                        note's median to start a new note — set above
 *                        the singer's vibrato depth
 * @param bridgeGapMs     unvoiced gaps up to this long inside a steady
 *                        pitch are dropouts, not note boundaries
 * @param glideRate       a short segment whose pitch moves faster than
 *                        this (semitones per second) is a slide into or
 *                        out of a note and is absorbed by its neighbour
 * @param glideMaxMs      only segments up to this long can be glides
 * @param dipRatio        a note whose loudness dips below this fraction
 *                        of the peaks either side is split there — a
 *                        re-sung syllable on the same pitch; {@code 0}
 *                        disables
 * @param trimFraction    each note's pitch is the median of its middle
 *                        part, ignoring this fraction at either end
 *                        (scoops and fall-offs)
 */
public record SegmentationSpec(double relativeFloorDb, double medianMs, double minNoteMs, double splitCents,
                               double bridgeGapMs, double glideRate, double glideMaxMs,
                               double dipRatio, double trimFraction) {

    public static final SegmentationSpec DEFAULT = new SegmentationSpec(35, 50, 60, 60, 30, 15, 200, 0.35, 0.2);

    public SegmentationSpec {
        if (!(relativeFloorDb >= 0)) throw new IllegalArgumentException("relativeFloorDb must be >= 0");
        if (!(medianMs >= 0)) throw new IllegalArgumentException("medianMs must be >= 0");
        if (!(minNoteMs > 0)) throw new IllegalArgumentException("minNoteMs must be > 0");
        if (!(splitCents > 0)) throw new IllegalArgumentException("splitCents must be > 0");
        if (!(bridgeGapMs >= 0)) throw new IllegalArgumentException("bridgeGapMs must be >= 0");
        if (!(glideRate > 0)) throw new IllegalArgumentException("glideRate must be > 0");
        if (!(glideMaxMs >= 0)) throw new IllegalArgumentException("glideMaxMs must be >= 0");
        if (!(dipRatio >= 0 && dipRatio < 1)) throw new IllegalArgumentException("dipRatio must be in [0,1)");
        if (!(trimFraction >= 0 && trimFraction < 0.5)) throw new IllegalArgumentException("trimFraction must be in [0,0.5)");
    }
}
