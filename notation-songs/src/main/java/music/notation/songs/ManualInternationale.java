package music.notation.songs;

import music.notation.chord.*;
import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.pitch.Accidental;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.Accidental.FLAT;
import static music.notation.pitch.Accidental.SHARP;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * The Internationale — Pierre De Geyter (1888), lyrics Eugène Pottier (1871).
 *
 * <p>Arranged in A major, 4/4 march tempo. Skeleton provider — melody to be
 * filled in manually.</p>
 */
final class ManualInternationale implements PieceContentProvider<Internationale> {

    // ── Chord constants (A major context, octave 3) ────────────────

    private static final Chord A_MAJ  = new MajorTriad(A, 3);
    private static final Chord D_MAJ  = new MajorTriad(D, 3);
    private static final Chord E7     = new DominantSeventh(E, 3);
    private static final Chord B7     = new DominantSeventh(B, 3);
    private static final Chord Fs_MIN = new MinorTriad(F, Accidental.SHARP, 3);
    private static final Chord Cs7    = new DominantSeventh(C, Accidental.SHARP, 3);
    private static final Chord A7     = new DominantSeventh(A, 3);
    private static final Chord Fs7    = new DominantSeventh(F, Accidental.SHARP, 3);
    private static final Chord Bm7    = new MinorSeventh(B, 3);

    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Override
    public Piece create() {
        final var id = new Internationale();

        // A major scale: A B C# D E F# G#

        var P = StaffPhraseBuilder.in(TS, EIGHTH);   // default = QUARTER for march

        // ── Main theme (G minor) ──
        var phrase1 = P
                // Bar 1: D Bb A G (four quarters)
                .bar().f().r(HALF).rd(QUARTER).o4(E)
                .bar()
                    .o4d(A,QUARTER).o4(G,SHARP,EIGHTH).o4(B).o4(A).o4(E).o4(C,SHARP)
                .bar()
                    .o4(F, SHARP, HALF).o4d(D,QUARTER).o4(F,SHARP)
                .bar()
                    .o4d(B, QUARTER).o4(A).o4(G,SHARP).o4(F,SHARP).o4(E).o4(D)
                .bar()
                    .o4d(C,SHARP,HALF).r(EIGHTH).o4(E)
                .bar()
                    .o4d(A,QUARTER).o4(G,SHARP).o4(B).o4(A).o4(E).o4(C,SHARP)
                .bar()
                    .o4(F,SHARP,HALF).o4d(D,QUARTER).o4(A,SIXTEENTH).o4(A,SIXTEENTH)
                .bar()
                    .o4(G,SHARP, QUARTER).o4(G,SHARP).o4(B).o5(D,QUARTER).o4(G,SHARP,QUARTER)
                .bar()
                    .o4d(A,HALF).o5(C,SHARP).o4(B)
                .bar()
                    .o4d(G,SHARP,QUARTER).o4(G,SHARP).o4(F,SHARP).o4(G,SHARP).o4(A).o4(F,SHARP)
                .bar()
                    .o4(G,SHARP,HALF).o4d(E,QUARTER).o4(E,SIXTEENTH).o4(E,SIXTEENTH)
                .bar()
                    .o4(F,SHARP,QUARTER).o4(F,SHARP).o4(F,SHARP).o4(B,QUARTER).o4(A,QUARTER)
                .bar()
                    .o4d(G,SHARP,HALF).r(EIGHTH).o4(B)
                .bar()
                    .o4d(B,QUARTER).o4(G,SHARP).o4(E).o4(E).o4(D,SHARP).o4(E)
                .bar()
                    .o5(C,SHARP,HALF).o4(E).o4(F,SHARP).o4(G,SHARP).o4(A)
                .bar()
                    .o4(G,SHARP,QUARTER).o4(B,QUARTER).o4(A,QUARTER).o4(F,SHARP,QUARTER)
                .bar()
                    .o4(E,HALF).o5d(C,SHARP,QUARTER).o4(B)
                .bar()
                    .o4(A,HALF).o4(E,QUARTER).o4(F,SHARP,QUARTER)
                .bar()
                    .o4(F,SHARP,HALF).o4(D,QUARTER).o4d(B,EIGHTH).o4(A,SIXTEENTH)
                .bar()
                    .o4(G,SHARP,QUARTER).o4(G,SHARP,QUARTER).o4(F,SHARP,QUARTER).o4(E,QUARTER)
                .bar()
                    .o4d(E,HALF).o4(E,QUARTER)
                .bar()
                    .o5d(C,SHARP,QUARTER).o5(C,SHARP).o4(B,QUARTER).o4(E,QUARTER)
                .bar()
                    .o4(A,HALF).o4d(G,SHARP,QUARTER).o4(G,SHARP)
                .bar()
                    .o4d(F,SHARP,QUARTER).o4(E,SHARP).o4(F,SHARP,QUARTER).o4(B,QUARTER)
                .bar()
                    .o4(B,HALF).o5d(C,SHARP,QUARTER).o4(B)
                .bar()
                    .o4(A,HALF).o4(E,QUARTER).o4(F,SHARP,QUARTER)
                .bar()
                    .o4(F,SHARP,HALF).o4(D,QUARTER).o4d(B,EIGHTH).o4(A,SIXTEENTH)
                .bar()
                    .o4(G,SHARP,QUARTER).o4(G,SHARP,QUARTER).o4d(F,SHARP,QUARTER).o4(E)
                .bar()
                    .o5d(C,SHARP,HALF).o5(C,SHARP,QUARTER)
                .bar()
                    .o5d(E,QUARTER).o5(E).o5(D,QUARTER).o5(C,SHARP,QUARTER)
                .bar()
                    .o4(B,QUARTER).o5(C,SHARP,QUARTER).o5d(D,QUARTER).o5(D)
                .bar()
                    .o5(C,SHARP,QUARTER).o5(C,SHARP,QUARTER).o4d(B,QUARTER).o4(A)
                .bar()
                    .o4d(A,HALF).r(QUARTER)
                .build(end());

        var melody = new Track("Melody", FRENCH_HORN, List.of(phrase1));



        return new Piece(id.title(), id.composer(),
                new KeySignature(A, Mode.MAJOR), new TimeSignature(4, 4),
                new Tempo(104, QUARTER), List.of(melody));
    }
}
