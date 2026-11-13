package music.notation.transcribe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonSubTypes({
        @JsonSubTypes.Type(value = PitchDetectorSpec.Yin.class, name = "yin"),
        @JsonSubTypes.Type(value = PitchDetectorSpec.Rmvpe.class, name = "rmvpe")
})
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

    /**
     * RMVPE neural pitch tracker, run with ONNX Runtime. Robust to
     * accompaniment and noise; needs the model weights (see
     * {@link RmvpeModel}). Fixed 10 ms frames, 16 kHz input.
     *
     * @param model     path to {@code rmvpe.onnx}; {@code null} = resolve
     *                  via {@link RmvpeModel#locate}
     * @param threshold peak salience a frame needs to count as voiced
     *                  (RVC uses 0.03; raise it to drop breathy or doubtful frames)
     * @param minHz     lowest pitch accepted
     * @param maxHz     highest pitch accepted
     * @param minRms    frames quieter than this (0–1 full scale) are unvoiced;
     *                  0 leaves voicing to the model
     */
    record Rmvpe(String model, double threshold, double minHz, double maxHz, double minRms)
            implements PitchDetectorSpec {

        public static final Rmvpe DEFAULT = new Rmvpe(null, 0.03, 50, 1400, 0);

        /** JSON form: any field left out takes its {@link #DEFAULT} value. */
        @JsonCreator
        static Rmvpe fromJson(@JsonProperty("model") String model, @JsonProperty("threshold") Double threshold,
                              @JsonProperty("minHz") Double minHz, @JsonProperty("maxHz") Double maxHz,
                              @JsonProperty("minRms") Double minRms) {
            return new Rmvpe(model,
                    threshold != null ? threshold : DEFAULT.threshold(),
                    minHz != null ? minHz : DEFAULT.minHz(),
                    maxHz != null ? maxHz : DEFAULT.maxHz(),
                    minRms != null ? minRms : DEFAULT.minRms());
        }

        public Rmvpe {
            if (!(threshold > 0 && threshold < 1)) throw new IllegalArgumentException("threshold must be in (0,1)");
            if (!(minHz > 0 && maxHz > minHz)) throw new IllegalArgumentException("require 0 < minHz < maxHz");
            if (!(minRms >= 0)) throw new IllegalArgumentException("minRms must be >= 0");
        }

        @Override public double hopMs() { return LogMelSpec.RMVPE.hopMs(); }

        @Override public String describe() {
            String m = model == null ? "" : ", model " + java.nio.file.Path.of(model).getFileName();
            return String.format("RMVPE (threshold %s, %s–%s Hz%s)", fmt(threshold), fmt(minHz), fmt(maxHz), m);
        }
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
