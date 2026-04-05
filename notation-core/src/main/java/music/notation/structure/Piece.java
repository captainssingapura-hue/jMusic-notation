package music.notation.structure;

import music.notation.pitch.NoteName;

import java.util.List;

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
}
