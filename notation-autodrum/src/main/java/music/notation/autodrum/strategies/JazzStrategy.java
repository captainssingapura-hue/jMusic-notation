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
import static music.notation.event.PercussionSound.CLOSED_HI_HAT;
import static music.notation.event.PercussionSound.RIDE_CYMBAL;

/**
 * Jazz comp — ride-cymbal-driven, hi-hat on 2 & 4 (the "chick"), kick
 * very sparse (feathered). True swing feel needs triplet eighths;
 * sequential approximation here uses straight eighths with the ride/hat
 * placement that captures the structural feel.
 *
 * <ul>
 *   <li>{@code LOW}    — quarter ride on every beat, no kick/snare</li>
 *   <li>{@code MEDIUM} — straight 8ths: ride · ride · ride+hat · ride · ride · ride · ride+hat · ride</li>
 *   <li>{@code HIGH}   — same MEDIUM groove but sparse kick on 1 (feathered)</li>
 * </ul>
 *
 * <p>Active for 4/4 only.</p>
 */
public final class JazzStrategy implements DrumStrategy {

    private static final BarDuration FOUR_FOUR = new BarDuration(4, BaseValue.QUARTER);

    @Override public String id()          { return "jazz"; }
    @Override public String displayName() { return "Jazz (Swing Comp)"; }
    @Override public String description() {
        return "Ride cymbal on every beat with hi-hat \"chick\" on 2 & 4. Sparse kick "
             + "(\"feather\") at higher energy. Sequential approximation of swing feel.";
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
        // Dense melody → drop hi-hat chick, keep just ride pulse.
        if (features.bucket() == DensityBucket.DENSE) return RIDE_QUARTERS;
        return patternFor(bd, energy);
    }

    private PatternSpec patternFor(BarDuration bd, Energy energy) {
        if (!FOUR_FOUR.equals(bd)) return null;
        return switch (energy) {
            case LOW    -> RIDE_QUARTERS;
            case MEDIUM -> RIDE_HAT_CHICK;
            case HIGH   -> RIDE_FEATHER_KICK;
        };
    }

    // ── Velocity profile ────────────────────────────────────────────────
    //
    // Jazz comp is the quietest of the strategies — overall lower than
    // rock/funk. The ride is steady (≈ 80–85), the hat "chick" on 2 & 4
    // is a touch louder (≈ 90) — that's the back-beat in jazz. "Feathered"
    // kick at HIGH energy is intentionally barely-there (≈ 50) — the
    // jazz tradition is for the kick to be felt, not heard.

    /** LOW + DENSE-bar: just ride cymbal on each quarter. */
    private static final PatternSpec RIDE_QUARTERS = new PatternSpec(BaseValue.QUARTER,
            new PercussionSound[] { RIDE_CYMBAL, RIDE_CYMBAL, RIDE_CYMBAL, RIDE_CYMBAL },
            new int[]              { 90,          80,          85,          80 });

    /** MEDIUM: ride on 1 & 3, hi-hat "chick" on 2 & 4. */
    private static final PatternSpec RIDE_HAT_CHICK = new PatternSpec(BaseValue.EIGHTH,
            new PercussionSound[] {
                    RIDE_CYMBAL,   null,
                    CLOSED_HI_HAT, null,
                    RIDE_CYMBAL,   null,
                    CLOSED_HI_HAT, null },
            new int[] {
                    90,            64,
                    90,            64,    // chick on 2
                    85,            64,
                    90,            64 });  // chick on 4

    /** HIGH: feathered kick + ride pulse + snare on the "and of 4". */
    private static final PatternSpec RIDE_FEATHER_KICK = new PatternSpec(BaseValue.EIGHTH,
            new PercussionSound[] {
                    BASS_DRUM,     RIDE_CYMBAL,
                    CLOSED_HI_HAT, RIDE_CYMBAL,
                    BASS_DRUM,     RIDE_CYMBAL,
                    CLOSED_HI_HAT, ACOUSTIC_SNARE },
            new int[] {
                    50,            85,    // feathered kick on 1
                    90,            80,    // chick on 2
                    50,            80,    // feathered kick on 3
                    90,            65 }); // chick on 4 + ghost snare on "and of 4"
}
