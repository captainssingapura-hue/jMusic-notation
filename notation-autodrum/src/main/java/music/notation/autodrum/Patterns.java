package music.notation.autodrum;

import music.notation.duration.BarDuration;
import music.notation.duration.Duration;
import music.notation.event.PercussionSound;
import music.notation.expressivity.VelocityChange;
import music.notation.expressivity.VelocityControl;
import music.notation.phrase.Bar;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.Phrase;
import music.notation.phrase.RestNode;
import music.notation.structure.DrumTrack;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Shared helpers for translating {@link PatternSpec}s into bars and
 * walking a source {@link Piece} bar-by-bar with a strategy-supplied
 * pattern resolver.
 *
 * <p>Strategies typically only need to declare per-(BarDuration, Energy)
 * patterns and call {@link #generateTrack(String, Piece, Energy, BiFunction)}.
 * Bars whose duration has no matching pattern are filled with rests; the
 * resulting {@link DrumTrack} is returned only when at least one bar
 * actually receives a pattern.</p>
 */
public final class Patterns {

    private Patterns() {}

    /**
     * Build a {@link Bar} from a pattern spec. The spec's total
     * subdivision count must equal the bar's expected size in sixty-
     * fourths; otherwise an {@link IllegalArgumentException} is thrown
     * (mirrors {@link Bar}'s own duration check, but with a clearer
     * message for strategy authors).
     */
    public static Bar buildBar(BarDuration bd, PatternSpec spec) {
        long unitSf = spec.unit().sixtyFourths();
        long expectedTotal = bd.sixtyFourths();
        long actualTotal = (long) spec.sequence().length * unitSf;
        if (actualTotal != expectedTotal) {
            throw new IllegalArgumentException(
                    "PatternSpec total " + actualTotal + " sf does not fit "
                            + bd + " (" + expectedTotal + " sf): "
                            + spec.sequence().length + " × " + spec.unit());
        }
        PhraseNode[] nodes = new PhraseNode[spec.sequence().length];
        for (int i = 0; i < nodes.length; i++) {
            PercussionSound s = spec.sequence()[i];
            nodes[i] = (s == null)
                    ? new RestNode(Duration.of(spec.unit()))
                    : new PercussionNote(s, Duration.of(spec.unit()));
        }
        return Bar.of(bd, nodes);
    }

    /**
     * Walk the source piece bar-by-bar, asking {@code resolver} for a
     * pattern given (bar-duration, energy). Bars whose duration has no
     * matching pattern get a graceful {@link #fallbackBar fallback bar}
     * (a single kick on beat 1, rest for the remainder) so the strategy
     * still emits an audible pulse for non-standard meters or pickup
     * measures. Returns {@link Optional#empty()} only when the source
     * has no bars at all.
     */
    public static Optional<DrumTrack> generateTrack(
            String name, Piece source, Energy energy,
            BiFunction<BarDuration, Energy, PatternSpec> resolver) {
        return generateTrack(name, source, energy,
                (bd, e, features, idx) -> resolver.apply(bd, e));
    }

    /**
     * Feature-aware overload — same as the {@link BiFunction} version but
     * the resolver also receives {@link BarFeatures} from a one-shot
     * {@link SourceAnalysis} pre-scan plus the bar index. Strategies that
     * want melody-following behaviour use this; the simpler overload is
     * retained for back-compat.
     */
    public static Optional<DrumTrack> generateTrack(
            String name, Piece source, Energy energy,
            PatternResolver resolver) {
        return generateTrackWithVelocities(name, source, energy, resolver)
                .map(GeneratedDrums::track);
    }

    /**
     * Velocity-aware bake — returns the {@link DrumTrack} plus a
     * {@link VelocityControl} carrying per-onset velocity entries for
     * any {@link PatternSpec} whose {@link PatternSpec#slotVelocities}
     * is non-null. The control is empty when no spec opted in.
     *
     * <p>Slot tickMs are computed using the source piece's
     * {@link Piece#tempo() initial tempo} treated as constant — for
     * rubato-heavy pieces with tempo arrangements, the velocity
     * timeline drifts in the same direction the rest of the auto-X
     * helpers do (acceptable until a shared
     * {@code TempoConversion} pass is propagated through).</p>
     */
    public static Optional<GeneratedDrums> generateTrackWithVelocities(
            String name, Piece source, Energy energy,
            PatternResolver resolver) {

        Track template = firstNonEmptyTrack(source);
        if (template == null) return Optional.empty();
        int barCount = template.bars().size();
        if (barCount == 0) return Optional.empty();

        SourceAnalysis analysis = SourceAnalysis.scan(source);
        double msPerSixtyFourth = 60_000.0 / source.tempo().bpm() / 16.0;

        List<Bar> drumBars = new ArrayList<>(barCount);
        List<VelocityChange> velocityChanges = new ArrayList<>();
        long cumulativeSf = 0;

        for (int i = 0; i < barCount; i++) {
            BarDuration bd = template.bars().get(i).expectedDuration();
            BarFeatures features = analysis.at(i);
            PatternSpec spec = resolver.resolve(bd, energy, features, i);

            if (spec == null) {
                drumBars.add(fallbackBar(bd));
            } else {
                drumBars.add(buildBar(bd, spec));
                if (spec.hasVelocities()) {
                    long unitSf = spec.unit().sixtyFourths();
                    long barSf  = bd.sixtyFourths();
                    PercussionSound[] seq = spec.sequence();
                    int[] vels = spec.slotVelocities();
                    for (int s = 0; s < seq.length; s++) {
                        if (seq[s] == null) continue;   // rest slot — no NOTE_ON to velocity-tag
                        long slotSf = cumulativeSf + (long) s * unitSf;
                        long slotMs = Math.round(slotSf * msPerSixtyFourth);
                        int velocity = applyBassAlignmentBoost(
                                vels[s], seq[s], features,
                                barSf > 0 ? (double) (s * unitSf) / barSf : 0.0);
                        velocityChanges.add(new VelocityChange(slotMs, velocity));
                    }
                }
            }
            cumulativeSf += bd.sixtyFourths();
        }

        DrumTrack track = new DrumTrack(name, Phrase.of(drumBars));
        VelocityControl velocities = velocityChanges.isEmpty()
                ? VelocityControl.empty()
                : new VelocityControl(velocityChanges);
        return Optional.of(new GeneratedDrums(track, velocities));
    }

    /**
     * Bump kick velocity by {@value #BASS_ALIGN_BOOST} when the slot
     * lines up (within {@value #BASS_ALIGN_TOLERANCE} of a fractional
     * bar position) with a source bass onset. Gives the kick a small
     * "anchor" effect when it locks with the music's actual bass line —
     * the listener feels the drummer responding to the part instead
     * of metronome-following.
     *
     * <p>Only applies to {@link PercussionSound#BASS_DRUM} slots; other
     * sounds pass through unchanged. Output is clamped to [1,127].</p>
     */
    private static int applyBassAlignmentBoost(int slotVelocity,
                                               PercussionSound sound,
                                               BarFeatures features,
                                               double slotFraction) {
        if (sound != PercussionSound.BASS_DRUM) return slotVelocity;
        if (features == null || features.bassOnsetFractions().isEmpty()) return slotVelocity;
        if (!features.bassOnsetNear(slotFraction, BASS_ALIGN_TOLERANCE)) return slotVelocity;
        int boosted = slotVelocity + BASS_ALIGN_BOOST;
        return Math.max(1, Math.min(127, boosted));
    }

    /** Velocity bump for kick slots that align with a source bass onset. */
    static final int BASS_ALIGN_BOOST = 5;

    /** Fractional bar window for "kick aligned with source bass" detection. */
    static final double BASS_ALIGN_TOLERANCE = 0.0625;   // ±1/16 of a bar

    /** A bar whose only content is a single rest filling the entire bar. */
    public static Bar silentBar(BarDuration bd) {
        return Bar.of(bd, new RestNode(bd.totalDuration()));
    }

    /**
     * Graceful fallback bar — soft kick on the down-beat, rest for the
     * remainder. Used when a strategy has no specific pattern for the
     * bar's duration (non-standard meter, pickup measure, etc.) so the
     * drum track still keeps the pulse instead of going silent.
     */
    public static Bar fallbackBar(BarDuration bd) {
        if (bd.unitCount() == 1) {
            return Bar.of(bd,
                    new music.notation.phrase.PercussionNote(
                            music.notation.event.PercussionSound.BASS_DRUM,
                            Duration.of(bd.unit())));
        }
        PhraseNode[] nodes = new PhraseNode[bd.unitCount()];
        nodes[0] = new music.notation.phrase.PercussionNote(
                music.notation.event.PercussionSound.BASS_DRUM,
                Duration.of(bd.unit()));
        for (int i = 1; i < nodes.length; i++) {
            nodes[i] = new RestNode(Duration.of(bd.unit()));
        }
        return Bar.of(bd, nodes);
    }

    /** First track in {@code source} that has at least one bar. */
    public static Track firstNonEmptyTrack(Piece source) {
        for (Track t : source.tracks()) {
            if (!t.bars().isEmpty()) return t;
        }
        return null;
    }
}
