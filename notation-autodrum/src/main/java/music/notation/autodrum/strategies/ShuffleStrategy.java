package music.notation.autodrum.strategies;

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
import static music.notation.event.PercussionSound.RIDE_CYMBAL;

/**
 * Shuffle / blues-shuffle groove. Built for compound-time meters where
 * the triplet feel is naturally encoded in the time signature itself
 * (6/8, 9/8, 12/8). 4/4 swing-shuffle would need triplet tuplets and is
 * deferred.
 *
 * <ul>
 *   <li>{@code LOW}    — kick on each dotted-quarter group's beat 1, snare on backbeats</li>
 *   <li>{@code MEDIUM} — adds closed hi-hat on the in-between 8ths</li>
 *   <li>{@code HIGH}   — same shape but with ride cymbal on the in-between 8ths</li>
 * </ul>
 */
public final class ShuffleStrategy implements DrumStrategy {

    @Override public String id()          { return "shuffle"; }
    @Override public String displayName() { return "Shuffle"; }
    @Override public String description() {
        return "Compound-time triplet feel — kick on the down-beat group, snare on "
             + "backbeat group, hat or ride filling the off-beats. Fits 6/8 and 12/8.";
    }

    @Override
    public Optional<DrumTrack> generate(Piece source, Energy energy) {
        if (!appliesTo(source)) return Optional.empty();
        return Patterns.generateTrack("Auto Drum", source, energy, this::patternFor);
    }

    @Override
    public Optional<GeneratedDrums> generateWithVelocities(Piece source, Energy energy) {
        if (!appliesTo(source)) return Optional.empty();
        return Patterns.generateTrackWithVelocities("Auto Drum", source, energy,
                (bd, e, f, i) -> patternFor(bd, e));
    }

    // ── Velocity profile ────────────────────────────────────────────────
    //
    // Shuffle has the "long-short" triplet bounce of blues drumming.
    // The kick on group 0 anchors the bar (≈ 105); the back-beat snare
    // on odd groups is the loudest hit (≈ 115). Hat/ride filler on the
    // off-beat 8th sits well below (≈ 70) — the bouncy lift of the feel.
    private static final int VEL_KICK_DOWNBEAT = 110;
    private static final int VEL_KICK = 100;
    private static final int VEL_BACKBEAT  = 115;
    private static final int VEL_FILLER = 70;

    private PatternSpec patternFor(BarDuration bd, Energy energy) {
        if (bd.unit() != BaseValue.EIGHTH) return null;
        // Build for any compound bar with a 3-eighth grouping (6/8, 9/8, 12/8).
        if (bd.unitCount() % 3 != 0) return null;
        int groups = bd.unitCount() / 3;

        PercussionSound[] seq = new PercussionSound[bd.unitCount()];
        int[] vels = new int[bd.unitCount()];
        for (int g = 0; g < groups; g++) {
            int base = g * 3;
            // Group's down-beat: kick on even groups, snare on odd (backbeat).
            boolean isBackbeat = g % 2 == 1;
            seq[base] = isBackbeat ? ACOUSTIC_SNARE : BASS_DRUM;
            vels[base] = isBackbeat
                    ? VEL_BACKBEAT
                    : (g == 0 ? VEL_KICK_DOWNBEAT : VEL_KICK);
            // Filler unused slots default to a token velocity (rest slots
            // ignore it but the array length must match).
            vels[base + 1] = 64;
            vels[base + 2] = 64;
            // Slot 2 of group is silent in LOW; hat/ride otherwise (slot 1
            // stays silent for the long-short shuffle feel).
            if (energy == Energy.MEDIUM) {
                seq[base + 2] = CLOSED_HI_HAT;
                vels[base + 2] = VEL_FILLER;
            } else if (energy == Energy.HIGH) {
                seq[base + 2] = RIDE_CYMBAL;
                vels[base + 2] = VEL_FILLER + 10;   // ride brighter than hat
            }
        }
        return new PatternSpec(BaseValue.EIGHTH, seq, vels);
    }
}
