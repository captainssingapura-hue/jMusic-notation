package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.event.Ornament;
import music.notation.pitch.Pitch;

import java.util.List;
import java.util.Optional;

/**
 * A single-pitch note event. May carry an optional {@link Ornament}
 * (chord notes cannot — see {@link PolyPitchNode}).
 *
 * <p>The {@code ornament} field is preserved on the model but is ignored
 * by {@code PhraseInterpreter} in the current pipeline (Phase 1 retires
 * ornament rendering — see {@code .docs/microtiming.md}). A future opt-in
 * Performance transformer will restore expressive rendering.</p>
 */
public record SimplePitchNode(
        Pitch pitch,
        Duration duration,
        Optional<Ornament> ornament,
        List<GraceNote> graceNotes,
        boolean equalDivision,
        boolean tiedToNext
) implements PitchNode {

    public SimplePitchNode {
        graceNotes = List.copyOf(graceNotes);
    }

    @Override
    public List<Pitch> pitches() { return List.of(pitch); }

    @Override
    public SimplePitchNode withTiedToNext() {
        return new SimplePitchNode(pitch, duration, ornament, graceNotes, equalDivision, true);
    }
}
