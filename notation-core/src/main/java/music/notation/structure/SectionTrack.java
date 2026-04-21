package music.notation.structure;

import music.notation.phrase.Phrase;

import java.util.List;

/**
 * A section's contribution to one named track — the phrases (and nested
 * aux voices) that belong to this track <em>within</em> a single
 * {@link Section}.
 *
 * <p>Carries no name (that's the key in the {@link Section} map) and no
 * instrument (that's declared piece-wide via {@link TrackDecl}). The
 * phrases' total duration is validated by {@link Section} at construction
 * time against the section's declared duration.</p>
 *
 * <p>{@code auxTracks} mirror {@link Track#auxTracks()} — parallel voice
 * overlays within the same named track. Each aux must also sum to the
 * section's declared duration.</p>
 */
public record SectionTrack(List<Phrase> phrases, List<SectionTrack> auxTracks) {
    public SectionTrack {
        phrases = List.copyOf(phrases);
        auxTracks = List.copyOf(auxTracks);
    }

    /** Convenience factory: a section-track with no aux voices. */
    public static SectionTrack of(List<Phrase> phrases) {
        return new SectionTrack(phrases, List.of());
    }

    /** Convenience factory: a section-track with a single phrase. */
    public static SectionTrack of(Phrase phrase) {
        return new SectionTrack(List.of(phrase), List.of());
    }
}
