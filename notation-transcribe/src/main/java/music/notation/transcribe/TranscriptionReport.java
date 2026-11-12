package music.notation.transcribe;

import music.notation.duration.Duration;

import java.util.Locale;

/** Plain-text report of a {@link Transcription}: header, tuning summary, one line per note. */
public final class TranscriptionReport {

    private TranscriptionReport() {}

    public static String render(Transcription t) {
        var r = t.request();
        var timing = r.timing();
        var sb = new StringBuilder();
        line(sb, "Transcription: %s", t.title());
        line(sb, "Audio:     %s  (analysed %.2f–%.2f s)", r.audio(), t.fromSec(), t.toSec());
        line(sb, "Detector:  %s", r.detector().describe());
        line(sb, "Timing:    ♩ = %d, %d/%d, grid %s, downbeat at %.3f s", timing.bpm(),
                timing.timeSignature().beats(), timing.timeSignature().beatValue(), fraction(timing.grid()), t.downbeatSec());
        line(sb, "Key:       %s%s", Keys.name(t.key()), t.keyEstimated() ? " (estimated)" : "");
        line(sb, "Result:    %d notes · voiced %.0f%% of frames · pickup %d bar%s · %d dropped on the grid",
                t.quantized().size(), 100 * t.voicedFraction(), t.pickupBars(), t.pickupBars() == 1 ? "" : "s", t.dropped());
        if (!t.quantized().isEmpty()) {
            QuantizedNote flat = null, sharp = null;
            double[] abs = new double[t.quantized().size()];
            for (int i = 0; i < abs.length; i++) {
                QuantizedNote q = t.quantized().get(i);
                abs[i] = Math.abs(q.cents());
                if (flat == null || q.cents() < flat.cents()) flat = q;
                if (sharp == null || q.cents() > sharp.cents()) sharp = q;
            }
            java.util.Arrays.sort(abs);
            line(sb, "Tuning:    median off-pitch %.0f¢ · flattest %+.0f¢ (bar %d) · sharpest %+.0f¢ (bar %d)",
                    abs[abs.length / 2], flat.cents(), flat.bar(), sharp.cents(), sharp.bar());
        }
        sb.append('\n');
        line(sb, "%4s %4s %6s  %-5s %-7s %6s   %-17s %4s", "#", "bar", "beat", "note", "length", "cents", "time (s)", "vel");
        int i = 1;
        for (QuantizedNote q : t.quantized()) {
            line(sb, "%4d %4d %6s  %-5s %-7s %+6.0f   %7.3f – %7.3f %4d", i++, q.bar(), beat(q.beat()),
                    Keys.noteName(q.midi(), t.key()), fraction(q.duration()), q.cents(),
                    q.source().startMs() / 1000, q.source().endMs() / 1000, q.velocity());
        }
        return sb.toString();
    }

    static String fraction(Duration d) {
        long n = d.numerator(), den = d.denominator(), g = gcd(Math.abs(n), Math.abs(den));
        return (n / g) + "/" + (den / g);
    }

    private static String beat(double b) {
        String s = String.format(Locale.ROOT, "%.2f", b);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static long gcd(long a, long b) { return b == 0 ? Math.max(a, 1) : gcd(b, a % b); }

    private static void line(StringBuilder sb, String fmt, Object... args) {
        sb.append(String.format(Locale.ROOT, fmt, args)).append('\n');
    }
}
