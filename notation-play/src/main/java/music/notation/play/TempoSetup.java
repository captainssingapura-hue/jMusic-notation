package music.notation.play;

import javax.sound.midi.Sequencer;

/**
 * Live tempo control — a multiplicative factor on the sequence's
 * authored tempo curve.
 *
 * <p>Uses {@link Sequencer#setTempoFactor(float)} (not
 * {@code setTempoInBPM}) so the composer-authored tempo *curve* —
 * including ritardando / accelerando — is preserved while playing
 * faster or slower. Absolute-BPM override would flatten expressivity.</p>
 *
 * @param bpmFactor scale factor (1.0 = unchanged, 1.2 = 20% faster).
 */
public record TempoSetup(double bpmFactor) {

    public TempoSetup {
        if (bpmFactor <= 0) {
            throw new IllegalArgumentException("bpmFactor must be positive: " + bpmFactor);
        }
    }

    /** No tempo override — play at the authored tempo curve. */
    public static TempoSetup unity() {
        return new TempoSetup(1.0);
    }

    /** Direct factor. */
    public static TempoSetup factor(double f) {
        return new TempoSetup(f);
    }

    /**
     * Derive a factor from a desired BPM relative to the piece's
     * authored BPM. {@code atBpm(144, 120) ≈ factor(1.2)}.
     */
    public static TempoSetup atBpm(int desiredBpm, int authoredBpm) {
        if (authoredBpm <= 0) {
            throw new IllegalArgumentException("authoredBpm must be positive: " + authoredBpm);
        }
        return new TempoSetup((double) desiredBpm / authoredBpm);
    }

    /** Apply to a running sequencer. Idempotent — call any time. */
    public void apply(Sequencer seq) {
        if (seq == null) return;
        seq.setTempoFactor((float) bpmFactor);
    }
}
