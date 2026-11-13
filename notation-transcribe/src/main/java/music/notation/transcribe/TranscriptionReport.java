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
        line(sb, "Smoothing: %s", smoothing(r.smoothing(), t.smoothing()));
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
        if (!t.smoothing().isEmpty()) {
            sb.append('\n');
            line(sb, "Smoothing edits:");
            for (SmoothingEdit e : t.smoothing()) {
                line(sb, "  %7.3f – %7.3f  %-6s %s → %s", e.startMs() / 1000, e.endMs() / 1000,
                        e.kind().name().toLowerCase(Locale.ROOT),
                        Keys.noteName(e.fromMidi(), t.key()), Keys.noteName(e.toMidi(), t.key()));
            }
        }
        return sb.toString();
    }

    private static String smoothing(SmoothingSpec s, java.util.List<SmoothingEdit> edits) {
        var on = new java.util.ArrayList<String>();
        if (s.snapCents() > 0) on.add(String.format(Locale.ROOT, "snap ≥ %.0f¢", s.snapCents()));
        if (s.octaveMaxMs() > 0) on.add(String.format(Locale.ROOT, "octave ≤ %.0f ms", s.octaveMaxMs()));
        if (s.blipMaxMs() > 0) on.add(String.format(Locale.ROOT, "blips ≤ %.0f ms", s.blipMaxMs()));
        if (on.isEmpty()) return "off";
        var counts = new java.util.EnumMap<SmoothingEdit.Kind, Integer>(SmoothingEdit.Kind.class);
        for (SmoothingEdit e : edits) counts.merge(e.kind(), 1, Integer::sum);
        var parts = new java.util.ArrayList<String>();
        counts.forEach((k, n) -> parts.add(n + " " + k.name().toLowerCase(Locale.ROOT)));
        return String.join(", ", on) + " · " + (edits.isEmpty() ? "no changes" : String.join(", ", parts));
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
