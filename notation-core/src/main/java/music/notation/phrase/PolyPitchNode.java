package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.pitch.Pitch;

import java.util.List;

/**
 * A chord note event — two or more pitches sounded together for a single
 * duration. No ornament slot (ornaments only apply to single pitches).
 */
public record PolyPitchNode(
        List<Pitch> pitches,
        Duration duration,
        List<GraceNote> graceNotes,
        boolean equalDivision,
        boolean tiedToNext
) implements PitchNode {

    public PolyPitchNode {
        if (pitches.size() < 2) {
            throw new IllegalArgumentException(
                    "PolyPitchNode requires at least 2 pitches (got " + pitches.size()
                            + "); use SimplePitchNode for single-pitch notes.");
        }
        pitches = List.copyOf(pitches);
        graceNotes = List.copyOf(graceNotes);
    }

    @Override
    public PolyPitchNode withTiedToNext() {
        return new PolyPitchNode(pitches, duration, graceNotes, equalDivision, true);
    }
}
