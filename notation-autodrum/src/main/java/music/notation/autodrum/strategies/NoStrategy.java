package music.notation.autodrum.strategies;

import music.notation.autodrum.DrumStrategy;
import music.notation.autodrum.Energy;
import music.notation.structure.DrumTrack;
import music.notation.structure.Piece;

import java.util.Optional;

/**
 * Sentinel "off" strategy. Always returns {@link Optional#empty()} so a
 * picker can use it as the no-drum default while keeping the picker model
 * homogeneous (every entry is a {@code DrumStrategy}).
 */
public final class NoStrategy implements DrumStrategy {

    @Override public String id()          { return "none"; }
    @Override public String displayName() { return "None"; }
    @Override public String description() { return "No auto-drum accompaniment."; }

    @Override public boolean appliesTo(Piece source) { return false; }

    @Override public Optional<DrumTrack> generate(Piece source, Energy energy) {
        return Optional.empty();
    }
}
