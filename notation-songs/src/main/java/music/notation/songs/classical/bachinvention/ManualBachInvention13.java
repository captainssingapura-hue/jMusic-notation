package music.notation.songs.classical.bachinvention;

import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * Invention No. 13 in A Minor (BWV 784) — J.S. Bach.
 *
 * <p>Manual staff-notation transcription using {@link StaffPhraseBuilder}.</p>
 */
public final class ManualBachInvention13 implements PieceContentProvider<BachInvention13> {

    private static final KeySignature KEY = new KeySignature(A, Mode.MINOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Override
    public Piece create() {
        final var id = new BachInvention13();

        var P = StaffPhraseBuilder.in(KEY, TS, SIXTEENTH);

        // ── Right Hand ──
        var rh = P
                // TODO: add bars here
                .bar(SIXTEENTH).r(SIXTEENTH).o4(E).o4(A).o5(C).o4(B).o4(E).o4(B).o5(D).o5(EIGHTH,C).o5(EIGHTH,E).o4(EIGHTH,G.s()).o5(EIGHTH,E)
                .bar(SIXTEENTH).o4(A).o4(E).o4(A).o5(C).o4(B).o4(E).o4(B).o5(D).o5(EIGHTH,C).o4(EIGHTH,A).r(QUARTER)
                .bar(SIXTEENTH).r(SIXTEENTH).o5(E).o5(C).o5(E).o4(A).o5(C).o4(E).o4(G).o4(EIGHTH,F).o4(EIGHTH,A).o5(EIGHTH,D).o5(EIGHTH,F)
                .bar(SIXTEENTH).o5(F).o5(D).o4(B).o5(D).o4(G).o4(B).o4(D).o4(F).o4(EIGHTH,E).o4(EIGHTH,G).o5(EIGHTH,C).o5(EIGHTH,E)
                .bar(SIXTEENTH).o5(E).o5(C).o4(A).o5(C).o4(EIGHTH,F).o5(EIGHTH,D).slurStart().o5(D).slurEnd().o4(B).o4(G).o4(B).o4(EIGHTH,E).o5(EIGHTH,C).slurStart()
                .bar(SIXTEENTH).o5(C).slurEnd().o4(A).o4(F).o4(A).o4(EIGHTH,D).o4(EIGHTH,B).o5(EIGHTH,C).r(EIGHTH).r(QUARTER)
                .build(end());

        // ── Left Hand ──
        var lh = P
                // TODO: add bars here
                .bar(SIXTEENTH).o2(EIGHTH,A).o3(QUARTER,A).o3(EIGHTH,G.s()).o3(A).o3(E).o3(A).o4(C).o3(B).o3(E).o3(B).o4(D)
                .bar(SIXTEENTH).o4(EIGHTH,C).o3(EIGHTH,A).o3(EIGHTH,G.s()).o3(EIGHTH,E).o3(A).o3(E).o3(A).o4(C).o3(B).o3(E).o3(B).o4(D)
                .bar(SIXTEENTH).o4(EIGHTH,C).o3(EIGHTH,A).o4(EIGHTH,C).o3(EIGHTH,A).o4(D).o3(A).o3(F).o3(A).o3(D).o3(F).o2(A).o3(C)
                .bar(SIXTEENTH).o2(EIGHTH,B).o3(EIGHTH,D).o3(EIGHTH,G).o3(EIGHTH,B).slurStart().o3(B).slurEnd().o3(G).o3(E).o3(G).o3(C).o3(E).o2(G).o2(B)
                .bar(SIXTEENTH).o2(EIGHTH,A).o3(EIGHTH,C).o3(D).o3(F).o2(B).o3(D).o2(EIGHTH,G).o2(EIGHTH,B).o3(C).o3(E).o2(A).o3(C)
                .bar(SIXTEENTH).o2(EIGHTH,F).o2(EIGHTH,D).o2(G).o3(G).o3(F).o3(G).o3(C).o3(G).o4(C).o4(E).o4(D).o3(G).o4(D).o4(F)
                .build(end());

        return new Piece(id.title(), id.composer(),
                KEY, TS,
                new Tempo(72, QUARTER),
                List.of(
                        Track.of("Right Hand", ACOUSTIC_GRAND_PIANO, List.of(rh)),
                        Track.of("Left Hand", ACOUSTIC_GRAND_PIANO, List.of(lh))
                )
        );
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ManualBachInvention13());
    }
}
