package music.notation.transcribe;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Which frame-by-frame pitch detector to run, with its parameters.
 * Every detector works on mono audio at {@link Transcriber#ANALYSIS_RATE}
 * and emits one frame per hop.
 *
 * <pre>{@code  { "type": "yin", "windowSize": 1024, "hopMs": 10, ... } }</pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = PitchDetectorSpec.Yin.class, name = "yin") })
public sealed interface PitchDetectorSpec {

    /** Frame spacing in milliseconds. */
    double hopMs();

    /** Short human description for reports. */
    String describe();

    /**
     * Classical YIN autocorrelation tracker. Good on a clean single voice
     * (a mic take); unreliable on a full mix.
     *
     * @param windowSize analysis window in samples at the analysis rate
     *                   (1024 = 64 ms; the lowest detectable pitch is
     *                   about {@code 2 × rate / windowSize})
     * @param hopMs      frame spacing
     * @param threshold  YIN dip threshold (lower = stricter, fewer frames voiced)
     * @param minHz      lowest pitch accepted
     * @param maxHz      highest pitch accepted
     * @param minRms     frames quieter than this (0–1 full scale) are unvoiced
     */
    record Yin(int windowSize, double hopMs, double threshold, double minHz, double maxHz, double minRms)
            implements PitchDetectorSpec {

        public static final Yin DEFAULT = new Yin(1024, 10, 0.15, 65, 1100, 0.003);

        public Yin {
            if (windowSize < 128 || (windowSize & 1) != 0)
                throw new IllegalArgumentException("windowSize must be even and >= 128: " + windowSize);
            if (!(hopMs > 0)) throw new IllegalArgumentException("hopMs must be > 0");
            if (!(threshold > 0 && threshold < 1)) throw new IllegalArgumentException("threshold must be in (0,1)");
            if (!(minHz > 0 && maxHz > minHz)) throw new IllegalArgumentException("require 0 < minHz < maxHz");
            if (minHz < 2.0 * Transcriber.ANALYSIS_RATE / windowSize)
                throw new IllegalArgumentException("minHz " + minHz + " is below what a window of "
                        + windowSize + " can resolve (" + 2.0 * Transcriber.ANALYSIS_RATE / windowSize + " Hz)");
            if (maxHz >= Transcriber.ANALYSIS_RATE / 2.0) throw new IllegalArgumentException("maxHz must be below Nyquist");
            if (!(minRms >= 0)) throw new IllegalArgumentException("minRms must be >= 0");
        }

        @Override public String describe() {
            return String.format("YIN (window %d, hop %s ms, threshold %s, %s–%s Hz)",
                    windowSize, fmt(hopMs), fmt(threshold), fmt(minHz), fmt(maxHz));
        }
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
