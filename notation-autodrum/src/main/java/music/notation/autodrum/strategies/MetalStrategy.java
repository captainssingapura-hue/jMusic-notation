package music.notation.autodrum.strategies;

import music.notation.autodrum.BarFeatures;
import music.notation.autodrum.DensityBucket;
import music.notation.autodrum.DrumStrategy;
import music.notation.autodrum.Energy;
import music.notation.autodrum.GeneratedDrums;
import music.notation.autodrum.PatternResolver;
import music.notation.autodrum.PatternSpec;
import music.notation.autodrum.Patterns;
import music.notation.duration.BarDuration;
import music.notation.duration.BaseValue;
import music.notation.event.PercussionSound;
import music.notation.structure.DrumTrack;
import music.notation.structure.Piece;

import java.util.Optional;

import static music.notation.event.PercussionSound.ACOUSTIC_SNARE;
import static music.notation.event.PercussionSound.BASS_DRUM;
import static music.notation.event.PercussionSound.CRASH_CYMBAL;
import static music.notation.event.PercussionSound.RIDE_CYMBAL;

/**
 * Metal — heavy kick presence, snare on 2 & 4, ride/crash for cymbal feel.
 *
 * <ul>
 *   <li>{@code LOW}    — quarter kick/snare basic — feels like steady rock</li>
 *   <li>{@code MEDIUM} — 8th kicks throughout, snare on 2 & 4</li>
 *   <li>{@code HIGH}   — 16th double-bass kicks with snare on 2 & 4 and crash on 1</li>
 * </ul>
 *
 * <p>Active for 4/4 only.</p>
 */
public final class MetalStrategy implements DrumStrategy {

    private static final BarDuration FOUR_FOUR = new BarDuration(4, BaseValue.QUARTER);

    @Override public String id()          { return "metal"; }
    @Override public String displayName() { return "Metal"; }
    @Override public String description() {
        return "Heavy kick + snare on 2 & 4. Energy escalates from quarters to "
             + "8th kicks to 16th double-bass with crash on the down-beat.";
    }

    @Override
    public Optional<DrumTrack> generate(Piece source, Energy energy) {
        if (!appliesTo(source)) return Optional.empty();
        return Patterns.generateTrack("Auto Drum", source, energy,
                (PatternResolver) this::resolvePattern);
    }

    @Override
    public Optional<GeneratedDrums> generateWithVelocities(Piece source, Energy energy) {
        if (!appliesTo(source)) return Optional.empty();
        return Patterns.generateTrackWithVelocities("Auto Drum", source, energy,
                (PatternResolver) this::resolvePattern);
    }

    private PatternSpec resolvePattern(BarDuration bd, Energy energy,
                                        BarFeatures features, int barIndex) {
        if (!FOUR_FOUR.equals(bd)) return null;
        // Dense melody → fall back to rock-like quarter K/S so the runs come through.
        if (features.bucket() == DensityBucket.DENSE) return THINNED;
        return patternFor(bd, energy);
    }

    private PatternSpec patternFor(BarDuration bd, Energy energy) {
        if (!FOUR_FOUR.equals(bd)) return null;
        return switch (energy) {
            case LOW    -> LOW_QUARTERS;
            case MEDIUM -> EIGHTH_KICKS;
            case HIGH   -> DOUBLE_BASS_16THS;
        };
    }

    // ── Velocity profile ────────────────────────────────────────────────
    //
    // Metal is loud — every hit sits in the 100–127 range. Crash on the
    // downbeat is at the absolute ceiling (≈ 127); snare back-beats are
    // wall-shaking (≈ 120); double-bass 16ths are uniformly aggressive
    // (≈ 105). Almost no dynamic contrast — that's the point.

    /** LOW: quarter K/S basic. */
    private static final PatternSpec LOW_QUARTERS = new PatternSpec(BaseValue.QUARTER,
            new PercussionSound[] { BASS_DRUM, ACOUSTIC_SNARE, BASS_DRUM, ACOUSTIC_SNARE },
            new int[]              { 115,       120,            110,       120 });

    /** MEDIUM: 8th kicks throughout, snare on 2 & 4. */
    private static final PatternSpec EIGHTH_KICKS = new PatternSpec(BaseValue.EIGHTH,
            new PercussionSound[] {
                    BASS_DRUM,      BASS_DRUM,
                    ACOUSTIC_SNARE, BASS_DRUM,
                    BASS_DRUM,      BASS_DRUM,
                    ACOUSTIC_SNARE, BASS_DRUM },
            new int[] {
                    115,            105,
                    120,            105,
                    110,            105,
                    120,            105 });

    /** HIGH: 16th double-bass kicks + crash on 1, snare on 2 & 4, ride on 3. */
    private static final PatternSpec DOUBLE_BASS_16THS = new PatternSpec(BaseValue.SIXTEENTH,
            new PercussionSound[] {
                    CRASH_CYMBAL,   BASS_DRUM, BASS_DRUM, BASS_DRUM,
                    ACOUSTIC_SNARE, BASS_DRUM, BASS_DRUM, BASS_DRUM,
                    RIDE_CYMBAL,    BASS_DRUM, BASS_DRUM, BASS_DRUM,
                    ACOUSTIC_SNARE, BASS_DRUM, BASS_DRUM, BASS_DRUM },
            new int[] {
                    127,            108, 100, 108,    // crash + double-kick
                    120,            108, 100, 108,    // back-beat + double-kick
                    100,            108, 100, 108,    // ride + double-kick
                    120,            108, 100, 108 }); // back-beat + double-kick

    /** DENSE-bar variant: bare quarter K/S — keeps the pulse, lets the riff lead. */
    private static final PatternSpec THINNED = new PatternSpec(BaseValue.QUARTER,
            new PercussionSound[] { BASS_DRUM, ACOUSTIC_SNARE, BASS_DRUM, ACOUSTIC_SNARE },
            new int[]              { 115,       120,            110,       120 });
}
