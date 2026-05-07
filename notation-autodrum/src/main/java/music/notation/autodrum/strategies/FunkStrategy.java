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
 * Funk groove — syncopated kick, snare on 2 & 4. Ghost notes (soft snare
 * fill-ins between backbeats) would normally be velocity-shaped; without
 * velocity rendering yet, MEDIUM/HIGH render them as full-volume snares,
 * preserving the structural placement.
 *
 * <ul>
 *   <li>{@code LOW}    — 8ths: kick · - · snare · - · - · kick · snare · -</li>
 *   <li>{@code MEDIUM} — 8ths with hi-hat between hits + syncopated kick</li>
 *   <li>{@code HIGH}   — 16ths with ghost-snare fills</li>
 * </ul>
 *
 * <p>Active for 4/4 only.</p>
 */
public final class FunkStrategy implements DrumStrategy {

    private static final BarDuration FOUR_FOUR = new BarDuration(4, BaseValue.QUARTER);

    @Override public String id()          { return "funk"; }
    @Override public String displayName() { return "Funk"; }
    @Override public String description() {
        return "Syncopated kick with snare on 2 & 4 — funk backbone. Higher energy "
             + "adds hi-hat fill and ghost-snare 16ths between the backbeats.";
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
        if (features.bucket() == DensityBucket.DENSE) return THINNED;
        return patternFor(bd, energy);
    }

    private PatternSpec patternFor(BarDuration bd, Energy energy) {
        if (!FOUR_FOUR.equals(bd)) return null;
        return switch (energy) {
            case LOW    -> LOW_8THS;
            case MEDIUM -> MEDIUM_8THS;
            case HIGH   -> HIGH_16THS_GHOST;
        };
    }

    // ── Velocity profile ────────────────────────────────────────────────
    //
    // Funk's defining feature is the back-beat-vs-ghost-snare contrast.
    // Main back-beats (snare on 2 & 4) at ≈ 115; ghost-snares between
    // them at ≈ 50–55 — barely audible on their own, sit in the cracks.
    // The kick on 1 anchors at ≈ 105, syncopated kick on the "and of 3"
    // is slightly weaker (≈ 100). Open hat on the "and of 2" gets a
    // small bump (≈ 95) — a typical funk accent.

    /** LOW: kick · - · snare · - · - · kick · snare · - (8ths). */
    private static final PatternSpec LOW_8THS = new PatternSpec(BaseValue.EIGHTH,
            new PercussionSound[] {
                    BASS_DRUM, null,
                    ACOUSTIC_SNARE, null,
                    null, BASS_DRUM,
                    ACOUSTIC_SNARE, null },
            new int[] {
                    105, 64,
                    115, 64,
                    64,  100,
                    115, 64 });

    /** MEDIUM: kick + snare + hi-hat with funk accent on the "and of 2". */
    private static final PatternSpec MEDIUM_8THS = new PatternSpec(BaseValue.EIGHTH,
            new PercussionSound[] {
                    BASS_DRUM, CLOSED_HI_HAT,
                    ACOUSTIC_SNARE, CLOSED_HI_HAT,
                    OPEN_HI_HAT, BASS_DRUM,
                    ACOUSTIC_SNARE, CLOSED_HI_HAT },
            new int[] {
                    105, 70,
                    115, 65,
                    95,  100,    // open-hat funk accent + syncopated kick
                    115, 65 });

    /** HIGH: 16ths with ghost-snare fills between the back-beats. */
    private static final PatternSpec HIGH_16THS_GHOST = new PatternSpec(BaseValue.SIXTEENTH,
            new PercussionSound[] {
                    BASS_DRUM,      CLOSED_HI_HAT, ACOUSTIC_SNARE, CLOSED_HI_HAT,
                    ACOUSTIC_SNARE, CLOSED_HI_HAT, ACOUSTIC_SNARE, CLOSED_HI_HAT,
                    BASS_DRUM,      CLOSED_HI_HAT, BASS_DRUM,      CLOSED_HI_HAT,
                    ACOUSTIC_SNARE, CLOSED_HI_HAT, ACOUSTIC_SNARE, CLOSED_HI_HAT },
            new int[] {
                    105,            70,            55,             65,    // beat 1 + ghost snare
                    115,            65,            55,             65,    // beat 2 backbeat + ghost
                    100,            70,            100,            65,    // beat 3 + sync kick on "and"
                    115,            65,            55,             65 }); // beat 4 backbeat + ghost

    /** DENSE-bar variant: bare quarter kick/snare so the melody runs breathe. */
    private static final PatternSpec THINNED = new PatternSpec(BaseValue.QUARTER,
            new PercussionSound[] { BASS_DRUM, ACOUSTIC_SNARE, BASS_DRUM, ACOUSTIC_SNARE },
            new int[]              { 105,       115,            100,       115 });
}
