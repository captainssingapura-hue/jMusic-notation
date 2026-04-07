package music.notation.songs;

import music.notation.chord.*;
import music.notation.phrase.*;
import music.notation.pitch.Accidental;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.Accidental.SHARP;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * The Internationale — Pierre De Geyter (1888), lyrics Eugène Pottier (1871).
 *
 * <p>Arranged in A major, 4/4 march tempo.</p>
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

    private static final KeySignature KEY = new KeySignature(A, Mode.MAJOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Override
    public Piece create() {
        final var id = new Internationale();

        // A major: F#, C#, G# are implicit — no need to annotate them

        var P = StaffPhraseBuilder.in(KEY, TS, EIGHTH);

        var phrase1 = P
                .bar()
                    .f().r(HALF).r(QUARTER.dot()).o4(E)
                .bar()
                    .o4(QUARTER.dot(), A).o4(G).o4(B).o4(A).o4(E).o4(C)
                .bar()
                    .o4(HALF, F).o4(QUARTER.dot(), D).o4(F)
                .bar()
                    .o4(QUARTER.dot(), B).o4(A).o4(G).o4(F).o4(E).o4(D)
                .bar()
                    .o4(HALF.dot(), C).r(EIGHTH).o4(E)
                .bar()
                    .o4(QUARTER.dot(), A).o4(G).o4(B).o4(A).o4(E).o4(C)
                .bar()
                    .o4(HALF, F).o4(QUARTER.dot(), D).o4(SIXTEENTH, A).o4(SIXTEENTH, A)
                .bar()
                    .o4(QUARTER, G).o4(G).o4(B).o5(QUARTER, D).o4(QUARTER, G)
                .bar()
                    .o4(HALF.dot(), A).o5(C).o4(B)
                .bar()
                    .o4(QUARTER.dot(), G).o4(G).o4(F).o4(G).o4(A).o4(F)
                .bar()
                    .o4(HALF, G).o4(QUARTER.dot(), E).o4(SIXTEENTH, E).o4(SIXTEENTH, E)
                .bar()
                    .o4(QUARTER, F).o4(F).o4(F).o4(QUARTER, B).o4(QUARTER, A)
                .bar()
                    .o4(HALF.dot(), G).r(EIGHTH).o4(B)
                .bar()
                    .o4(QUARTER.dot(), B).o4(G).o4(E).o4(E).o4(D, SHARP).o4(E)
                .bar()
                    .o5(HALF, C).o4(E).o4(F).o4(G).o4(A)
                .bar()
                    .o4(QUARTER, G).o4(QUARTER, B).o4(QUARTER, A).o4(QUARTER, F)
                .bar()
                    .o4(HALF, E).o5(QUARTER.dot(), C).o4(B)
                .bar()
                    .o4(HALF, A).o4(QUARTER, E).o4(QUARTER, F)
                .bar()
                    .o4(HALF, F).o4(QUARTER, D).o4(EIGHTH.dot(), B).o4(SIXTEENTH, A)
                .bar()
                    .o4(QUARTER, G).o4(QUARTER, G).o4(QUARTER, F).o4(QUARTER, E)
                .bar()
                    .o4(HALF.dot(), E).o4(QUARTER, E)
                .bar()
                    .o5(QUARTER.dot(), C).o5(C).o4(QUARTER, B).o4(QUARTER, E)
                .bar()
                    .o4(HALF, A).o4(QUARTER.dot(), G).o4(G)
                .bar()
                    .o4(QUARTER.dot(), F).o4(E, SHARP).o4(QUARTER, F).o4(QUARTER, B)
                .bar()
                    .o4(HALF, B).o5(QUARTER.dot(), C).o4(B)
                .bar()
                    .o4(HALF, A).o4(QUARTER, E).o4(QUARTER, F)
                .bar()
                    .o4(HALF, F).o4(QUARTER, D).o4(EIGHTH.dot(), B).o4(SIXTEENTH, A)
                .bar()
                    .o4(QUARTER, G).o4(QUARTER, G).o4(QUARTER.dot(), F).o4(E)
                .bar()
                    .o5(HALF.dot(), C).o5(QUARTER, C)
                .bar()
                    .o5(QUARTER.dot(), E).o5(E).o5(QUARTER, D).o5(QUARTER, C)
                .bar()
                    .o4(QUARTER, B).o5(QUARTER, C).o5(QUARTER.dot(), D).o5(D)
                .bar()
                    .o5(QUARTER, C).o5(QUARTER, C).o4(QUARTER.dot(), B).o4(A)
                .bar()
                    .o4(HALF.dot(), A).r(QUARTER)
                .build(end());

        var melody = new Track("Melody", FRENCH_HORN, List.of(phrase1));

        // ── Harmony (TODO: fill in) ────────────────────────────────
        var harmony = P
                .bar().r(HALF).r(QUARTER.dot()).o4(E) // 1
                .bar().o4(QUARTER.dot(), A).o4(G,E).o4(B).o4(A).o4(G).o4(E)
                .bar().o4(QUARTER,F,D).o4(QUARTER,C).o4(QUARTER.dot(), D).o4(F)
                .bar().o4(QUARTER.dot(), B).o4(A).o4(G,D).o4(F).o4(E).o4(D)
                .bar().r(WHOLE) // 2-5
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE) // 6-9
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 10-13
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 14-17
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 18-21
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 22-25
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 26-29
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 30-33
                .build(end());

        // ── Chords (TODO: fill in) ─────────────────────────────────
        var chords = P
                .bar().r(WHOLE) // 1
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 2-5
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 6-9
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 10-13
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 14-17
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 18-21
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 22-25
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 26-29
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE) // 30-33
                .build(end());

        return new Piece(id.title(), id.composer(),
                KEY, TS,
                new Tempo(104, QUARTER),
                List.of(melody,
                        new Track("Harmony", FRENCH_HORN, List.of(harmony)),
                        new Track("Chords", STRING_ENSEMBLE_1, List.of(chords))));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ManualInternationale());
    }
}
