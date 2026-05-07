package music.notation.autodrum.strategies;

import music.notation.autodrum.BarFeatures;
import music.notation.autodrum.DensityBucket;
import music.notation.autodrum.DrumStrategy;
import music.notation.autodrum.Energy;
import music.notation.autodrum.GeneratedDrums;
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
import static music.notation.event.PercussionSound.CRASH_CYMBAL;

/**
 * Foundational 8th-note rock beat (4/4 only), now <strong>density-aware</strong>.
 *
 * <p>The pattern shapes itself to what the melody is doing each bar:</p>
 *
 * <table>
 *   <caption>Per-bar pattern selection</caption>
 *   <tr><th>Bucket</th><th>Source feel</th><th>Drum response</th></tr>
 *   <tr><td>{@code EMPTY}</td><td>bar is silent</td><td>kick on 1, rest fill (graceful pulse)</td></tr>
 *   <tr><td>{@code SPARSE}</td><td>half-notes / pickups</td><td>standard 8ths; every 4th sparse bar adds 16th-hat fill</td></tr>
 *   <tr><td>{@code STANDARD}</td><td>8th-note territory</td><td>standard 8ths (current MEDIUM groove)</td></tr>
 *   <tr><td>{@code DENSE}</td><td>16th runs</td><td>thinned: kick · - · snare · - · kick · - · snare · - (gives the melody air)</td></tr>
 * </table>
 *
 * <p>Energy raises overall intensity orthogonally — at HIGH the down-beat
 * kick becomes a crash for the first STANDARD bar of each section
 * (visible only structurally; without velocity rendering, energy effects
 * are limited to pattern density / sound choice).</p>
 */
public final class RockBeatStrategy implements DrumStrategy {

    private static final BarDuration FOUR_FOUR = new BarDuration(4, BaseValue.QUARTER);

    /** Periodicity for the 16th-hat fill: every Nth SPARSE bar gets the busier pattern. */
    private static final int FILL_PERIOD = 4;

    @Override public String id()          { return "rock-8th"; }
    @Override public String displayName() { return "Rock (8ths)"; }
    @Override public String description() {
        return "8th-note rock beat — kick on 1 & 3, snare on 2 & 4, hat 8ths. "
             + "Adapts: thins out on dense melody bars, adds 16th-hat fills on sparse bars.";
    }

    @Override
    public Optional<DrumTrack> generate(Piece source, Energy energy) {
        if (!appliesTo(source)) return Optional.empty();
        return Patterns.generateTrack("Auto Drum", source, energy, this::patternFor);
    }

    @Override
    public Optional<GeneratedDrums> generateWithVelocities(Piece source, Energy energy) {
        if (!appliesTo(source)) return Optional.empty();
        return Patterns.generateTrackWithVelocities("Auto Drum", source, energy, this::patternFor);
    }

    private PatternSpec patternFor(BarDuration bd, Energy energy,
                                    BarFeatures features, int barIndex) {
        if (!FOUR_FOUR.equals(bd)) return null;
        DensityBucket bucket = features.bucket();

        return switch (bucket) {
            case EMPTY    -> null;                       // helper drops in fallbackBar
            case STANDARD -> standardFor(energy);
            case DENSE    -> THINNED;                    // kick · - · snare · - · kick · - · snare · -
            case SPARSE   -> (barIndex % FILL_PERIOD == FILL_PERIOD - 1)
                    ? SIXTEENTH_HAT_FILL
                    : standardFor(energy);
        };
    }

    private static PatternSpec standardFor(Energy energy) {
        return switch (energy) {
            case LOW -> LOW_QUARTERS;
            case MEDIUM -> MEDIUM_8THS;
            case HIGH -> HIGH_8THS_CRASH;
        };
    }

    // ── Pattern catalogue (cached singletons) ────────────────────────────

    // ── Velocity profile ────────────────────────────────────────────────
    //
    // The numbers below encode the rock-feel accent map:
    //   beat-1 kick   ≈ 105   (downbeat anchor)
    //   beat-3 kick   ≈ 100   (weaker downbeat)
    //   back-beat snare ≈ 115 (loudest hit; defines rock)
    //   off-beat hi-hat ≈ 70  (supporting tick — well below the snare)
    //   crash cymbal  ≈ 120   (the loudest moment we can summon)
    //
    // Pure declarations — the strategy doesn't compute velocity at runtime.

    private static final PatternSpec LOW_QUARTERS = new PatternSpec(BaseValue.QUARTER,
            new PercussionSound[] {
                    BASS_DRUM, ACOUSTIC_SNARE, BASS_DRUM, ACOUSTIC_SNARE },
            new int[] {
                    100,       110,            95,        110 });

    private static final PatternSpec MEDIUM_8THS = new PatternSpec(BaseValue.EIGHTH,
            new PercussionSound[] {
                    BASS_DRUM,      CLOSED_HI_HAT,
                    ACOUSTIC_SNARE, CLOSED_HI_HAT,
                    BASS_DRUM,      CLOSED_HI_HAT,
                    ACOUSTIC_SNARE, CLOSED_HI_HAT },
            new int[] {
                    105,            70,
                    115,            65,
                    100,            70,
                    115,            65 });

    private static final PatternSpec HIGH_8THS_CRASH = new PatternSpec(BaseValue.EIGHTH,
            new PercussionSound[] {
                    CRASH_CYMBAL,   CLOSED_HI_HAT,
                    ACOUSTIC_SNARE, CLOSED_HI_HAT,
                    BASS_DRUM,      CLOSED_HI_HAT,
                    ACOUSTIC_SNARE, CLOSED_HI_HAT },
            new int[] {
                    120,            75,
                    115,            70,
                    105,            75,
                    115,            70 });

    /** DENSE — drop hi-hat, leave just kick & snare on quarters so the melody breathes. */
    private static final PatternSpec THINNED = new PatternSpec(BaseValue.QUARTER,
            new PercussionSound[] {
                    BASS_DRUM, ACOUSTIC_SNARE, BASS_DRUM, ACOUSTIC_SNARE },
            new int[] {
                    100,       110,            95,        110 });

    /** SPARSE-fill — 16-slot bar, kick/snare on quarters with hi-hat 16ths between. */
    private static final PatternSpec SIXTEENTH_HAT_FILL = new PatternSpec(BaseValue.SIXTEENTH,
            new PercussionSound[] {
                    BASS_DRUM,      CLOSED_HI_HAT, CLOSED_HI_HAT, CLOSED_HI_HAT,
                    ACOUSTIC_SNARE, CLOSED_HI_HAT, CLOSED_HI_HAT, CLOSED_HI_HAT,
                    BASS_DRUM,      CLOSED_HI_HAT, CLOSED_HI_HAT, CLOSED_HI_HAT,
                    ACOUSTIC_SNARE, CLOSED_HI_HAT, CLOSED_HI_HAT, CLOSED_HI_HAT },
            new int[] {
                    105,            65,            55,            65,    // accent kick + ghost hat fill
                    115,            65,            55,            65,    // back-beat snare + ghost hat fill
                    100,            65,            55,            65,
                    115,            65,            55,            65 });
}
