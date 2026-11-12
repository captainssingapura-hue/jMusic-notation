package music.notation.transcribe;

import java.util.Arrays;
import java.util.Objects;

/**
 * Mono audio as floats in {@code [-1, 1]}, with the time of its first
 * sample in the source file — so every time the pipeline reports is a
 * file time, even after a {@link TimeWindow} cut.
 */
public final class AudioClip {

    private final int sampleRate;
    private final float[] samples;
    private final double offsetSec;

    public AudioClip(int sampleRate, float[] samples, double offsetSec) {
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be > 0");
        if (offsetSec < 0) throw new IllegalArgumentException("offsetSec must be >= 0");
        this.sampleRate = sampleRate;
        this.samples = Objects.requireNonNull(samples, "samples");
        this.offsetSec = offsetSec;
    }

    public AudioClip(int sampleRate, float[] samples) { this(sampleRate, samples, 0); }

    public int sampleRate()   { return sampleRate; }
    public int length()       { return samples.length; }
    public double offsetSec() { return offsetSec; }
    public double durationSec() { return (double) samples.length / sampleRate; }

    /** The samples themselves — not copied; treat as read-only. */
    public float[] samples() { return samples; }

    /** The part of this clip inside {@code w} (file times), clamped to what exists. */
    public AudioClip slice(TimeWindow w) {
        int from = clamp((int) Math.round((w.startSec() - offsetSec) * sampleRate));
        int to = w.toEnd() ? samples.length : clamp((int) Math.round((w.endSec() - offsetSec) * sampleRate));
        if (to < from) to = from;
        return new AudioClip(sampleRate, Arrays.copyOfRange(samples, from, to), offsetSec + (double) from / sampleRate);
    }

    /** Root-mean-square level of the whole clip. */
    public double rms() {
        double s = 0;
        for (float v : samples) s += (double) v * v;
        return samples.length == 0 ? 0 : Math.sqrt(s / samples.length);
    }

    private int clamp(int i) { return Math.max(0, Math.min(samples.length, i)); }

    @Override public String toString() {
        return String.format("AudioClip[%d Hz, %.3f s from %.3f s]", sampleRate, durationSec(), offsetSec);
    }
}
