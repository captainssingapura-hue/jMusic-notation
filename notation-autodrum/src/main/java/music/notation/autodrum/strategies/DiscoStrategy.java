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
import static music.notation.event.PercussionSound.OPEN_HI_HAT;

/**
 * Four-on-the-floor disco beat (4/4 only).
 *
 * <ul>
 *   <li>{@code LOW}    — kick on every quarter, nothing else</li>
 *   <li>{@code MEDIUM} — kick on every quarter, closed-hat on the off-beats</li>
 *   <li>{@code HIGH}   — kick on every quarter, open-hat off-beats with snare on 2 & 4</li>
 * </ul>
 *
 * <p>Sequential bars can't layer simultaneous kick + hat hits, so the
 * MEDIUM/HIGH versions emit hat (or snare) at the off-beats instead of
 * stacking with kick.</p>
 */
public final class DiscoStrategy implements DrumStrategy {

    private static final BarDuration FOUR_FOUR = new BarDuration(4, BaseValue.QUARTER);

    @Override public String id()          { return "disco-four-on-floor"; }
    @Override public String displayName() { return "Disco (Four-on-the-Floor)"; }
    @Override public String description() {
        return "Kick on every quarter — the disco backbone. Energy fills the off-beats "
             + "with closed hi-hat, then open hat + snare on 2 & 4.";
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
        // On dense melody bars, drop the hat and leave just four-on-floor.
        if (features.bucket() == DensityBucket.DENSE) return KICK_QUARTERS;
        return patternFor(bd, energy);
    }

    private PatternSpec patternFor(BarDuration bd, Energy energy) {
        if (!FOUR_FOUR.equals(bd)) return null;
        return switch (energy) {
            case LOW    -> KICK_QUARTERS;
            case MEDIUM -> KICK_HAT_8THS;
            case HIGH   -> KICK_OPEN_HAT_SNARE;
        };
    }

    // ── Velocity profile ────────────────────────────────────────────────
    //
    // Disco's character is the four-on-the-floor pulse — every kick is
    // strong (≈ 110), but beat 1 still leans slightly above the others
    // for downbeat anchoring. In HIGH energy the back-beat snare on 2/4
    // is the loudest hit (≈ 118) to drive the dancefloor; open hats sit
    // brighter than closed (≈ 90 vs 70).

    /** DENSE-bar variant + LOW energy: bare four-on-the-floor kicks, no hat. */
    private static final PatternSpec KICK_QUARTERS = new PatternSpec(BaseValue.QUARTER,
            new PercussionSound[] { BASS_DRUM, BASS_DRUM, BASS_DRUM, BASS_DRUM },
            new int[]              { 115,       108,       110,       108 });

    /** MEDIUM: kick on every quarter + closed hat off-beats. */
    private static final PatternSpec KICK_HAT_8THS = new PatternSpec(BaseValue.EIGHTH,
            new PercussionSound[] {
                    BASS_DRUM, CLOSED_HI_HAT,
                    BASS_DRUM, CLOSED_HI_HAT,
                    BASS_DRUM, CLOSED_HI_HAT,
                    BASS_DRUM, CLOSED_HI_HAT },
            new int[] {
                    115,       70,
                    108,       65,
                    110,       70,
                    108,       65 });

    /** HIGH: kick on every quarter + open hat / snare on the off-beats. */
    private static final PatternSpec KICK_OPEN_HAT_SNARE = new PatternSpec(BaseValue.EIGHTH,
            new PercussionSound[] {
                    BASS_DRUM, OPEN_HI_HAT,
                    BASS_DRUM, ACOUSTIC_SNARE,
                    BASS_DRUM, OPEN_HI_HAT,
                    BASS_DRUM, ACOUSTIC_SNARE },
            new int[] {
                    115,       90,
                    108,       118,
                    110,       90,
                    108,       118 });
}
