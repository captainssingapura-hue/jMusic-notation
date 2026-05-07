package music.notation.autodrum;

import music.notation.duration.BarDuration;
import music.notation.phrase.Bar;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.PitchNode;
import music.notation.phrase.PolyPitchNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.SimplePitchNode;
import music.notation.pitch.Pitch;
import music.notation.pitch.StaffPitch;
import music.notation.structure.DrumTrack;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Pre-scan of a {@link Piece}: per-bar melodic features that downstream
 * {@link DrumStrategy} implementations consult to vary their pattern.
 *
 * <p>"Melody" here means every {@link MelodicTrack} (drum tracks are
 * excluded — they are not what the drum strategy is "listening to").
 * Features are averaged across the melody tracks present at each bar.</p>
 *
 * <p>Stateless — produced once via {@link #scan(Piece)} and consulted
 * many times during track generation.</p>
 */
public final class SourceAnalysis {

    private final List<BarFeatures> perBar;

    private SourceAnalysis(List<BarFeatures> perBar) {
        this.perBar = List.copyOf(perBar);
    }

    public List<BarFeatures> perBar() { return perBar; }
    public int barCount()             { return perBar.size(); }

    /** Features for {@code barIndex} (0-based). Out-of-range returns an EMPTY-equivalent. */
    public BarFeatures at(int barIndex) {
        if (barIndex < 0 || barIndex >= perBar.size()) {
            return new BarFeatures(0.0, 0.0, true);
        }
        return perBar.get(barIndex);
    }

    /**
     * Walk every {@link MelodicTrack} in {@code source} and produce a
     * {@link BarFeatures} entry per bar, indexed by the bar position
     * the longest track covers. Drum tracks are deliberately excluded.
     */
    public static SourceAnalysis scan(Piece source) {
        if (source == null) return new SourceAnalysis(List.of());
        int barCount = 0;
        for (Track t : source.tracks()) {
            if (t instanceof MelodicTrack) {
                barCount = Math.max(barCount, t.bars().size());
            }
        }
        List<BarFeatures> out = new ArrayList<>(barCount);
        for (int i = 0; i < barCount; i++) {
            out.add(featuresAt(source, i));
        }
        return new SourceAnalysis(out);
    }

    /**
     * Octave threshold for "bass" detection — pitches at or below this
     * staff octave count toward {@link BarFeatures#bassOnsetFractions}.
     * Octave 3 = the octave immediately below middle C.
     */
    private static final int BASS_OCTAVE_THRESHOLD = 3;

    /** Feature vector for a single bar across every melody track. */
    private static BarFeatures featuresAt(Piece source, int barIndex) {
        long noteCount = 0;
        long activeSf = 0;
        long totalSf = 0;
        long barSf = 0;
        boolean anySource = false;
        boolean anyNonRest = false;
        TreeSet<Double> bassOnsets = new TreeSet<>();

        for (Track t : source.tracks()) {
            if (!(t instanceof MelodicTrack)) continue;
            List<Bar> bars = t.bars();
            if (barIndex >= bars.size()) continue;
            Bar bar = bars.get(barIndex);
            anySource = true;

            BarDuration bd = bar.expectedDuration();
            barSf = bd.sixtyFourths();   // assume all melody tracks share the bar shape
            totalSf += barSf;

            long cumulativeSf = 0;
            for (PhraseNode n : bar.nodes()) {
                long durSf = nodeSixtyFourths(n);
                if (n instanceof RestNode) {
                    // counts toward total but not toward activity
                } else {
                    anyNonRest = true;
                    if (n instanceof PitchNode || n instanceof PercussionNote) {
                        noteCount++;
                    }
                    if (isBassOnset(n) && barSf > 0) {
                        bassOnsets.add((double) cumulativeSf / barSf);
                    }
                    activeSf += durSf;
                }
                cumulativeSf += durSf;
            }
        }

        if (!anySource) return new BarFeatures(0.0, 0.0, true, List.of());

        // Density = notes per beat (using the bar's "unit" beat as the
        // denominator — normalises across 4/4, 3/4, 6/8, etc.).
        double beatCount = beatCountAt(source, barIndex);
        double density = beatCount > 0 ? (double) noteCount / beatCount : 0.0;
        double activeRatio = totalSf > 0 ? (double) activeSf / totalSf : 0.0;
        return new BarFeatures(density, Math.min(activeRatio, 1.0), !anyNonRest,
                new ArrayList<>(bassOnsets));
    }

    /**
     * True when the node's lowest pitch sits at or below
     * {@link #BASS_OCTAVE_THRESHOLD} — what the velocity bake calls
     * "bass" for kick-alignment purposes.
     */
    private static boolean isBassOnset(PhraseNode n) {
        return switch (n) {
            case SimplePitchNode sp -> isBassPitch(sp.pitch());
            case PolyPitchNode pp -> {
                for (Pitch p : pp.pitches()) if (isBassPitch(p)) yield true;
                yield false;
            }
            default -> false;
        };
    }

    private static boolean isBassPitch(Pitch p) {
        return p instanceof StaffPitch sp && sp.octave().value() <= BASS_OCTAVE_THRESHOLD;
    }

    /** Beat count for a bar — uses the first melody track's expectedDuration. */
    private static double beatCountAt(Piece source, int barIndex) {
        for (Track t : source.tracks()) {
            if (!(t instanceof MelodicTrack)) continue;
            List<Bar> bars = t.bars();
            if (barIndex < bars.size()) {
                return bars.get(barIndex).expectedDuration().unitCount();
            }
        }
        return 1.0;
    }

    /** Sixty-fourth count for nodes that expose a flat duration. */
    private static long nodeSixtyFourths(PhraseNode n) {
        if (n instanceof RestNode r)         return r.duration().sixtyFourths();
        if (n instanceof PitchNode p)        return p.duration().sixtyFourths();
        if (n instanceof PercussionNote pn)  return pn.duration().sixtyFourths();
        // SubPhrase, DynamicNode, TempoChangeNode, PaddingNode — out of
        // scope for density signals; treated as zero contribution.
        return 0L;
    }

    /** True iff the piece carries at least one drum track already. */
    public static boolean hasDrumTrack(Piece source) {
        for (Track t : source.tracks()) if (t instanceof DrumTrack) return true;
        return false;
    }
}
