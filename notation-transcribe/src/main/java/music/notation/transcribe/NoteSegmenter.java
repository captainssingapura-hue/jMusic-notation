package music.notation.transcribe;

import music.notation.input.PitchEstimate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Cuts a whole {@link PitchTrack} into {@link TranscribedNote}s. Unlike
 * the live {@code PitchedNoteSegmenter} it sees the entire take, so it
 * can look ahead before deciding a note has changed.
 *
 * <ol>
 *   <li><b>Gate</b> — frames far quieter than the take's peak become
 *       silence.</li>
 *   <li><b>Bridge</b> — short unvoiced gaps inside a steady pitch are
 *       filled in.</li>
 *   <li><b>Smooth</b> — a median filter removes spikes and octave
 *       blips.</li>
 *   <li><b>Split</b> — inside each voiced run a new note starts where the
 *       pitch leaves the current note's median by more than
 *       {@code splitCents} <em>and stays away</em> for
 *       {@code minNoteMs}, and only once the current note is itself
 *       {@code minNoteMs} long (so a scoop cannot set the reference).
 *       Vibrato inside the band never splits.</li>
 *   <li><b>Glides</b> — short, steep segments (slides into or out of a
 *       note) are absorbed by the note they lead into, or out of.</li>
 *   <li><b>Short pieces</b> — fragments below {@code minNoteMs} join the
 *       touching neighbour closest in pitch, or are dropped if
 *       isolated.</li>
 *   <li><b>Same pitch</b> — touching segments that round to the same
 *       semitone are rejoined.</li>
 *   <li><b>Re-articulation</b> — a note whose loudness dips deeply in
 *       the middle is split there (a repeated syllable on one
 *       pitch).</li>
 * </ol>
 *
 * Each note's pitch is the median of its middle part, so a scoop or a
 * fall-off does not pull it.
 */
public final class NoteSegmenter {

    private final SegmentationSpec spec;

    public NoteSegmenter(SegmentationSpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    private static final class Seg {
        int s, e;
        Seg(int s, int e) { this.s = s; this.e = e; }
        int len() { return e - s + 1; }
    }

    public List<TranscribedNote> segment(PitchTrack track) {
        int n = track.size();
        if (n == 0) return List.of();
        double hop = track.hopMs();
        double[] p = new double[n], rms = new double[n], conf = new double[n];
        for (int i = 0; i < n; i++) {
            PitchEstimate f = track.get(i);
            p[i] = f.hasPitch() ? f.midiFloat() : Double.NaN;
            rms[i] = f.rms();
            conf[i] = f.confidence();
        }
        double splitSt = spec.splitCents() / 100.0;
        gate(p, rms);
        bridge(p, (int) Math.floor(spec.bridgeGapMs() / hop + 1e-9), splitSt);
        double[] sm = medianFilter(p, (int) Math.round(spec.medianMs() / hop / 2));
        int minF = Math.max(1, (int) Math.ceil(spec.minNoteMs() / hop - 1e-9));

        var segs = new ArrayList<Seg>();
        for (int i = 0; i < n; ) {
            if (Double.isNaN(sm[i])) { i++; continue; }
            int j = i;
            while (j + 1 < n && !Double.isNaN(sm[j + 1])) j++;
            segs.addAll(split(sm, i, j, minF, splitSt));
            i = j + 1;
        }
        mergeGlides(segs, sm, hop);
        mergeShort(segs, sm, minF);
        mergeSamePitch(segs, sm);
        var finalSegs = new ArrayList<Seg>();
        for (Seg g : segs) splitOnDip(g, rms, minF, finalSegs);

        var notes = new ArrayList<TranscribedNote>();
        for (Seg g : finalSegs) {
            if (g.len() < minF) continue;
            double mf = trimmedMedian(sm, g);
            int midi = (int) Math.round(mf);
            if (midi < 0 || midi > 127) continue;
            double c = 0, peak = 0; int cn = 0;
            for (int i = g.s; i <= g.e; i++) {
                if (!Double.isNaN(p[i]) && conf[i] > 0) { c += conf[i]; cn++; }
                peak = Math.max(peak, rms[i]);
            }
            double start = Math.max(0, track.get(g.s).tickMs() - hop / 2);
            double end = track.get(g.e).tickMs() + hop / 2;
            notes.add(new TranscribedNote(start, end, midi, (mf - midi) * 100.0, cn == 0 ? 0 : c / cn, peak));
        }
        return notes;
    }

    // ── 1–3: clean-up of the curve ─────────────────────────────────────

    private void gate(double[] p, double[] rms) {
        if (spec.relativeFloorDb() <= 0) return;
        double peak = 0;
        for (int i = 0; i < p.length; i++) if (!Double.isNaN(p[i])) peak = Math.max(peak, rms[i]);
        double floor = peak * Math.pow(10, -spec.relativeFloorDb() / 20.0);
        for (int i = 0; i < p.length; i++) if (rms[i] < floor) p[i] = Double.NaN;
    }

    private static void bridge(double[] p, int maxGap, double tolerance) {
        if (maxGap <= 0) return;
        int n = p.length;
        for (int i = 1; i < n; i++) {
            if (!Double.isNaN(p[i]) || Double.isNaN(p[i - 1])) continue;
            int j = i;
            while (j < n && Double.isNaN(p[j])) j++;
            int gap = j - i;
            if (j < n && gap <= maxGap && Math.abs(p[j] - p[i - 1]) <= tolerance) {
                for (int k = i; k < j; k++) p[k] = p[i - 1] + (p[j] - p[i - 1]) * (k - i + 1) / (gap + 1);
            }
            i = j;
        }
    }

    static double[] medianFilter(double[] p, int half) {
        double[] out = new double[p.length];
        double[] buf = new double[2 * half + 1];
        for (int i = 0; i < p.length; i++) {
            if (Double.isNaN(p[i])) { out[i] = Double.NaN; continue; }
            int c = 0;
            for (int k = Math.max(0, i - half); k <= Math.min(p.length - 1, i + half); k++)
                if (!Double.isNaN(p[k])) buf[c++] = p[k];
            out[i] = median(buf, 0, c);
        }
        return out;
    }

    // ── 4: split a voiced run where the pitch moves and stays moved ────

    private static List<Seg> split(double[] sm, int a, int b, int minF, double splitSt) {
        var out = new ArrayList<Seg>();
        var vals = new ArrayList<Double>();            // kept sorted
        int start = a, j = a;
        while (j <= b) {
            if (vals.size() >= minF) {                 // a note must exist before it can end
                double ref = sortedMedian(vals);
                double d = sm[j] - ref;
                if (Math.abs(d) > splitSt) {
                    int end = Math.min(b, j + minF - 1);
                    if (end - j + 1 >= minF) {
                        double m = median(sm, j, end + 1);
                        if (Math.signum(m - ref) == Math.signum(d) && Math.abs(m - ref) > splitSt) {
                            out.add(new Seg(start, j - 1));
                            start = j;
                            vals.clear();
                            continue;
                        }
                    }
                }
            }
            int at = java.util.Collections.binarySearch(vals, sm[j]);
            vals.add(at < 0 ? -at - 1 : at, sm[j]);
            j++;
        }
        out.add(new Seg(start, b));
        return out;
    }

    // ── 5–7: tidy the segment list ─────────────────────────────────────

    private void mergeGlides(List<Seg> segs, double[] sm, double hop) {
        int i = 0;
        while (i < segs.size() && segs.size() > 1) {
            Seg g = segs.get(i);
            if (g.len() * hop <= spec.glideMaxMs() && Math.abs(slope(sm, g, hop)) > spec.glideRate()) {
                if (i + 1 < segs.size() && touching(g, segs.get(i + 1))) {
                    segs.get(i + 1).s = g.s; segs.remove(i); continue;
                }
                if (i > 0 && touching(segs.get(i - 1), g)) {
                    segs.get(i - 1).e = g.e; segs.remove(i); continue;
                }
            }
            i++;
        }
    }

    private void mergeShort(List<Seg> segs, double[] sm, int minF) {
        while (true) {
            int worst = -1;
            for (int i = 0; i < segs.size(); i++)
                if (segs.get(i).len() < minF && (worst < 0 || segs.get(i).len() < segs.get(worst).len())) worst = i;
            if (worst < 0) return;
            Seg g = segs.get(worst);
            Seg prev = worst > 0 && touching(segs.get(worst - 1), g) ? segs.get(worst - 1) : null;
            Seg next = worst + 1 < segs.size() && touching(g, segs.get(worst + 1)) ? segs.get(worst + 1) : null;
            double pg = trimmedMedian(sm, g);
            if (prev != null && (next == null
                    || Math.abs(trimmedMedian(sm, prev) - pg) <= Math.abs(trimmedMedian(sm, next) - pg))) {
                prev.e = g.e;
            } else if (next != null) {
                next.s = g.s;
            }
            segs.remove(worst);
        }
    }

    private void mergeSamePitch(List<Seg> segs, double[] sm) {
        int i = 0;
        while (i + 1 < segs.size()) {
            Seg a = segs.get(i), b = segs.get(i + 1);
            if (touching(a, b) && Math.round(trimmedMedian(sm, a)) == Math.round(trimmedMedian(sm, b))) {
                a.e = b.e; segs.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    private void splitOnDip(Seg g, double[] rms, int minF, List<Seg> out) {
        if (spec.dipRatio() > 0 && g.len() >= 2 * minF + 1) {
            int best = -1;
            for (int i = g.s + minF; i <= g.e - minF; i++) if (best < 0 || rms[i] < rms[best]) best = i;
            if (best >= 0) {
                double left = 0, right = 0;
                for (int i = g.s; i < best; i++) left = Math.max(left, rms[i]);
                for (int i = best + 1; i <= g.e; i++) right = Math.max(right, rms[i]);
                if (rms[best] < spec.dipRatio() * Math.min(left, right)) {
                    splitOnDip(new Seg(g.s, best - 1), rms, minF, out);
                    splitOnDip(new Seg(best + 1, g.e), rms, minF, out);
                    return;
                }
            }
        }
        out.add(g);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static boolean touching(Seg a, Seg b) { return a.e + 1 == b.s; }

    private double trimmedMedian(double[] sm, Seg g) {
        int t = (int) Math.floor(g.len() * spec.trimFraction());
        if (g.len() - 2 * t < 1) t = 0;
        return median(sm, g.s + t, g.e - t + 1);
    }

    /** Least-squares slope of the pitch over the segment, semitones per second. */
    private static double slope(double[] sm, Seg g, double hopMs) {
        int L = g.len();
        if (L < 2) return 0;
        double mx = (L - 1) / 2.0, my = 0;
        for (int i = g.s; i <= g.e; i++) my += sm[i];
        my /= L;
        double sxy = 0, sxx = 0;
        for (int k = 0; k < L; k++) {
            double dx = k - mx;
            sxy += dx * (sm[g.s + k] - my);
            sxx += dx * dx;
        }
        return sxy / sxx / (hopMs / 1000.0);
    }

    private static double median(double[] v, int from, int to) {
        double[] c = Arrays.copyOfRange(v, from, to);
        Arrays.sort(c);
        int L = c.length;
        return L % 2 == 1 ? c[L / 2] : (c[L / 2 - 1] + c[L / 2]) / 2.0;
    }

    private static double sortedMedian(List<Double> v) {
        int L = v.size();
        return L % 2 == 1 ? v.get(L / 2) : (v.get(L / 2 - 1) + v.get(L / 2)) / 2.0;
    }
}
