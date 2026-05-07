package music.notation.autodrum.strategies;

import music.notation.autodrum.DrumStrategy;
import music.notation.autodrum.Energy;
import music.notation.autodrum.GeneratedDrums;
import music.notation.autodrum.PatternSpec;
import music.notation.autodrum.Patterns;
import music.notation.duration.BarDuration;
import music.notation.duration.BaseValue;
import music.notation.event.PercussionSound;
import music.notation.expressivity.VelocityChange;
import music.notation.expressivity.VelocityControl;
import music.notation.phrase.Bar;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.RestNode;
import music.notation.structure.DrumTrack;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static music.notation.event.PercussionSound.BASS_DRUM;
import static music.notation.event.PercussionSound.SIDE_STICK;

/**
 * Gentle, classical-friendly accompaniment: soft kick on strong beats,
 * subtle side-stick on the off-beats, and a single soft kick on bars
 * where the source is silent.
 *
 * <p>Energy is intentionally ignored — the aesthetic is "hold the pulse
 * without taking attention", so all energy levels render the same
 * pattern. Loud variants belong in other strategies.</p>
 *
 * <h3>Time-signature support</h3>
 * <ul>
 *   <li>{@code 4/4} — kick · stick · kick · stick</li>
 *   <li>{@code 3/4} — kick · stick · stick (waltz)</li>
 *   <li>{@code 2/4} — kick · stick</li>
 *   <li>{@code 3/8} — three eighths: kick · stick · stick</li>
 *   <li>{@code 6/8} — two dotted-quarter beats (kick · stick)</li>
 *   <li>{@code 12/8} — four dotted-quarter beats</li>
 * </ul>
 */
public final class GentleClassicalStrategy implements DrumStrategy {

    @Override public String id()          { return "gentle-classical"; }
    @Override public String displayName() { return "Gentle Classical"; }
    @Override public String description() {
        return "Soft kick on strong beats, subtle side-stick off-beats — "
             + "tuned to support classical/piano material without taking attention.";
    }

    @Override
    public Optional<DrumTrack> generate(Piece source, Energy energy) {
        return generateWithVelocities(source, energy).map(GeneratedDrums::track);
    }

    @Override
    public Optional<GeneratedDrums> generateWithVelocities(Piece source, Energy energy) {
        if (!appliesTo(source)) return Optional.empty();
        if (source.tracks().isEmpty()) return Optional.empty();

        Track template = Patterns.firstNonEmptyTrack(source);
        if (template == null) return Optional.empty();
        int barCount = template.bars().size();
        if (barCount == 0) return Optional.empty();

        double msPerSixtyFourth = 60_000.0 / source.tempo().bpm() / 16.0;

        List<Bar> drumBars = new ArrayList<>(barCount);
        List<VelocityChange> velocityChanges = new ArrayList<>();
        long cumulativeSf = 0;

        for (int i = 0; i < barCount; i++) {
            BarDuration bd = template.bars().get(i).expectedDuration();
            PatternSpec spec = patternFor(bd);
            if (spec == null) {
                // Non-standard bar (pickup, mid-piece meter change, odd
                // time) — emit graceful fallback so the user still hears
                // a pulse instead of silence.
                drumBars.add(Patterns.fallbackBar(bd));
                emitFallbackVelocity(velocityChanges, cumulativeSf, msPerSixtyFourth);
            } else if (isSourceBarSilent(source, i)) {
                drumBars.add(quietBarFor(bd));
                // Single soft kick at bar start.
                velocityChanges.add(new VelocityChange(
                        Math.round(cumulativeSf * msPerSixtyFourth), VEL_QUIET_KICK));
            } else if (bd.unit() == BaseValue.EIGHTH
                    && (bd.unitCount() == 6 || bd.unitCount() == 12)) {
                // Compound time uses dotted-quarter sub-beats; the
                // PatternSpec helper expects a uniform subdivision, so
                // build those bars by hand.
                drumBars.add(buildCompoundBar(bd));
                emitCompoundVelocities(velocityChanges, bd, cumulativeSf, msPerSixtyFourth);
            } else {
                drumBars.add(Patterns.buildBar(bd, spec));
                emitPatternVelocities(velocityChanges, spec, cumulativeSf, msPerSixtyFourth);
            }
            cumulativeSf += bd.sixtyFourths();
        }

        DrumTrack track = new DrumTrack("Auto Drum", Phrase.of(drumBars));
        VelocityControl vels = velocityChanges.isEmpty()
                ? VelocityControl.empty()
                : new VelocityControl(velocityChanges);
        return Optional.of(new GeneratedDrums(track, vels));
    }

    // ── Velocity profile ────────────────────────────────────────────────
    //
    // Gentle Classical is intentionally the quietest strategy — supports
    // the source without taking attention. Soft kick (≈ 65); subtle
    // side-stick on off-beats (≈ 55); near-silent kick for gap-filling
    // bars (≈ 50). All well below mf so the piano stays in front.
    private static final int VEL_KICK       = 65;
    private static final int VEL_SIDESTICK  = 55;
    private static final int VEL_QUIET_KICK = 50;

    /** Velocity for a fallback bar — single kick on slot 0. */
    private static void emitFallbackVelocity(List<VelocityChange> out,
                                             long cumulativeSf, double msPerSf) {
        out.add(new VelocityChange(Math.round(cumulativeSf * msPerSf), VEL_KICK));
    }

    /** Velocities for the 6/8 or 12/8 dotted-quarter compound bar. */
    private static void emitCompoundVelocities(List<VelocityChange> out, BarDuration bd,
                                                long cumulativeSf, double msPerSf) {
        int dottedQuarters = bd.unitCount() / 3;
        long dottedSf = BaseValue.EIGHTH.sixtyFourths() * 3L;
        for (int i = 0; i < dottedQuarters; i++) {
            long slotSf = cumulativeSf + i * dottedSf;
            int vel = (i % 2 == 0) ? VEL_KICK : VEL_SIDESTICK;
            out.add(new VelocityChange(Math.round(slotSf * msPerSf), vel));
        }
    }

    /** Velocities for the standard PatternSpec walk (one entry per non-rest slot). */
    private static void emitPatternVelocities(List<VelocityChange> out, PatternSpec spec,
                                              long cumulativeSf, double msPerSf) {
        long unitSf = spec.unit().sixtyFourths();
        PercussionSound[] seq = spec.sequence();
        for (int s = 0; s < seq.length; s++) {
            if (seq[s] == null) continue;
            int vel = (seq[s] == BASS_DRUM) ? VEL_KICK : VEL_SIDESTICK;
            long slotSf = cumulativeSf + (long) s * unitSf;
            out.add(new VelocityChange(Math.round(slotSf * msPerSf), vel));
        }
    }

    /** Pattern at full strength (non-silent source bar). Returns {@code null} for unsupported meters. */
    private static PatternSpec patternFor(BarDuration bd) {
        if (bd.unit() == BaseValue.QUARTER) {
            return switch (bd.unitCount()) {
                case 2 -> new PatternSpec(BaseValue.QUARTER,
                        new PercussionSound[] { BASS_DRUM, SIDE_STICK });
                case 3 -> new PatternSpec(BaseValue.QUARTER,
                        new PercussionSound[] { BASS_DRUM, SIDE_STICK, SIDE_STICK });
                case 4 -> new PatternSpec(BaseValue.QUARTER,
                        new PercussionSound[] { BASS_DRUM, SIDE_STICK, BASS_DRUM, SIDE_STICK });
                default -> null;
            };
        }
        if (bd.unit() == BaseValue.EIGHTH) {
            return switch (bd.unitCount()) {
                case 3 -> new PatternSpec(BaseValue.EIGHTH,
                        new PercussionSound[] { BASS_DRUM, SIDE_STICK, SIDE_STICK });
                // 6/8 and 12/8 build via buildCompoundBar (dotted-quarter beats).
                case 6, 12 -> new PatternSpec(BaseValue.EIGHTH,
                        new PercussionSound[bd.unitCount()]);   // sentinel — actual bar built specially
                default -> null;
            };
        }
        return null;
    }

    /** Compound 6/8 or 12/8 bar — kick / stick alternating on dotted-quarter beats. */
    private static Bar buildCompoundBar(BarDuration bd) {
        int dottedQuarters = bd.unitCount() / 3;
        PhraseNode[] nodes = new PhraseNode[dottedQuarters];
        for (int i = 0; i < dottedQuarters; i++) {
            PercussionSound s = (i % 2 == 0) ? BASS_DRUM : SIDE_STICK;
            nodes[i] = new PercussionNote(s, BaseValue.QUARTER.dot());
        }
        return Bar.of(bd, nodes);
    }

    /** Silent source bar → single soft kick on beat 1, rest fills the remainder. */
    private static Bar quietBarFor(BarDuration bd) {
        List<PhraseNode> nodes = new ArrayList<>();
        nodes.add(new PercussionNote(BASS_DRUM,
                music.notation.duration.Duration.of(bd.unit())));
        for (int i = 1; i < bd.unitCount(); i++) {
            nodes.add(new RestNode(music.notation.duration.Duration.of(bd.unit())));
        }
        return Bar.of(bd, nodes.toArray(new PhraseNode[0]));
    }

    /** True when every track's bar at {@code idx} contains only rest nodes. */
    private static boolean isSourceBarSilent(Piece source, int idx) {
        for (Track t : source.tracks()) {
            List<Bar> bars = t.bars();
            if (idx >= bars.size()) continue;
            for (PhraseNode n : bars.get(idx).nodes()) {
                if (!(n instanceof RestNode)) return false;
            }
        }
        return true;
    }
}
