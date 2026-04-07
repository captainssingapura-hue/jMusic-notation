package music.notation.play;

import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;

/**
 * Simple utility to play a {@link Piece} from a {@link PieceContentProvider}
 * via MIDI, blocking until playback finishes.
 *
 * <p>Intended for quick testing from a provider's own {@code main} method:
 * <pre>{@code
 *     public static void main(String[] args) throws Exception {
 *         PlayPiece.play(new DefaultTwinkleStar());
 *     }
 * }</pre>
 */
public final class PlayPiece {

    private PlayPiece() {}

    public static void play(final PieceContentProvider<?> provider) throws Exception {
        final Piece piece = provider.create();

        System.out.println("Playing: " + piece.title() + " by " + piece.composer());
        System.out.println("  Key: " + piece.key().tonic() + " " + piece.key().mode()
                + "  |  Time: " + piece.timeSig().beats() + "/" + piece.timeSig().beatValue()
                + "  |  Tempo: " + piece.tempo().bpm() + " BPM");
        System.out.println("  Tracks: " + piece.tracks().stream()
                .map(t -> t.name() + " (" + t.defaultInstrument() + ")")
                .toList());

        final var player = new MidiPlayer();
        player.start(piece);

        while (player.isPlaying()) {
            Thread.sleep(200);
        }
        Thread.sleep(500);
        player.stop();
    }
}
