package music.notation.play;

import music.notation.event.Instrument;
import music.notation.structure.DrumTrack;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import music.notation.structure.Track;

/**
 * Simple utility to play a {@link Piece} from a {@link PieceContentProvider}
 * via MIDI, blocking until playback finishes.
 *
 * <p>Phase 4d: lyric phrases are gone with the legacy phrase family —
 * the karaoke display path was removed. Restore via a typed lyric
 * track later if needed.</p>
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
                .map(t -> t.name() + " (" + defaultInstrumentOf(t) + ")")
                .toList());
        System.out.println();

        final var player = new MidiPlayer();
        player.start(piece);

        while (player.isPlaying()) {
            Thread.sleep(50);
        }

        Thread.sleep(500);
        player.stop();
    }

    private static Instrument defaultInstrumentOf(Track track) {
        return switch (track) {
            case MelodicTrack mt -> mt.defaultInstrument();
            case DrumTrack dt -> Instrument.DRUM_KIT;
        };
    }
}
