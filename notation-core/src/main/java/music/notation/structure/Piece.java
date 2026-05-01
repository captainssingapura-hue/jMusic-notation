package music.notation.structure;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete musical piece — metadata plus a flat list of tracks.
 *
 * <p>Phase 4d cutover: tracks are sealed {@link Track} instances
 * ({@link MelodicTrack} or {@link DrumTrack}) carrying
 * {@link music.notation.phrase.Phrase} trees that resolve to bar
 * lists on demand. The previous {@link music.notation.phrase.AuthorPhrase}
 * sequence and section/control-track machinery have been removed.</p>
 */
public record Piece(
        String title,
        String composer,
        KeySignature key,
        TimeSignature timeSig,
        Tempo tempo,
        List<Track> tracks
) {
    public Piece {
        tracks = List.copyOf(tracks);
    }

    /**
     * Build a {@link Piece} directly from kinded tracks. Melodic
     * tracks come first in declaration order, followed by drum tracks
     * — matching the convention used by all songs in the corpus.
     */
    public static Piece ofTrackKinds(String title, String composer,
                                     KeySignature key, TimeSignature timeSig, Tempo tempo,
                                     List<MelodicTrack> melodicTracks,
                                     List<DrumTrack> drumTracks) {
        var tracks = new ArrayList<Track>(melodicTracks.size() + drumTracks.size());
        tracks.addAll(melodicTracks);
        tracks.addAll(drumTracks);
        return new Piece(title, composer, key, timeSig, tempo, tracks);
    }

    // ── Compatibility helpers (Phase 4d transitional) ────────────────

    /**
     * Always {@code false} after Phase 4d: control tracks were carried
     * by the section machinery which has been removed. Kept as a
     * no-op so the few remaining callers compile during the cutover;
     * they are slated for cleanup.
     */
    public boolean isControlTrack(String trackName) {
        return false;
    }
}
