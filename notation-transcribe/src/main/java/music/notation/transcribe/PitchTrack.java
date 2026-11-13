package music.notation.transcribe;

import music.notation.input.PitchEstimate;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The detector's output: one {@link PitchEstimate} per hop, in time
 * order. Frame {@code i} is centred at {@code tickMs} (file time).
 * Unvoiced frames carry {@code NaN} pitch.
 */
public record PitchTrack(double hopMs, List<PitchEstimate> frames) {

    public PitchTrack {
        if (!(hopMs > 0)) throw new IllegalArgumentException("hopMs must be > 0");
        frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
    }

    public int size() { return frames.size(); }

    public PitchEstimate get(int i) { return frames.get(i); }

    public double voicedFraction() {
        if (frames.isEmpty()) return 0;
        return (double) frames.stream().filter(PitchEstimate::hasPitch).count() / frames.size();
    }

    /** CSV with a header: time, MIDI, Hz, confidence, RMS. Unvoiced frames have empty pitch cells. */
    public String toCsv() {
        var sb = new StringBuilder("time_ms,midi,hz,confidence,rms\n");
        for (PitchEstimate f : frames) {
            sb.append(f.tickMs()).append(',');
            if (f.hasPitch()) {
                double hz = 440.0 * Math.pow(2, (f.midiFloat() - 69) / 12.0);
                sb.append(String.format(Locale.ROOT, "%.3f,%.2f,%.3f", f.midiFloat(), hz, f.confidence()));
            } else {
                sb.append(",,0");
            }
            sb.append(String.format(Locale.ROOT, ",%.5f\n", f.rms()));
        }
        return sb.toString();
    }
}
