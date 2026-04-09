package music.notation.songs.anthem.internationale;

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
public final class ManualInternationale implements PieceContentProvider<Internationale> {

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

        final var P1 = StaffPhraseBuilder.in(KEY, TS, EIGHTH);

        var phrase1 = P1
                .pickup()
                    .f().o4(E)
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
                    .o4(QUARTER.dot(), B).o4(G).o4(E).o4(E).o4(D.s()).o4(E)
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
                    .o4(QUARTER.dot(), F).o4(E.s()).o4(QUARTER, F).o4(QUARTER, B)
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
                    .o4(HALF.dot(), A)
                .ending()
                .build(end());

        var melody = Track.of("Melody", FRENCH_HORN, List.of(phrase1));

        final var P2 = StaffPhraseBuilder.in(KEY, TS, EIGHTH);
        // ── Harmony (TODO: fill in) ────────────────────────────────
        var harmonyMain = P2
                .pickup().o4(E) // 1
                .bar().o4(QUARTER.dot(), A).o4(G,E).o4(B).o4(A).o4(G).o4(E)
                    .aux().o4(HALF,C).o4(QUARTER,E).r(QUARTER)
                .bar().o4(QUARTER,F,D).o4(QUARTER,C).o4(QUARTER.dot(), D).o4(F)
                    .aux().o4(QUARTER,D,A.lower(1)).o4(QUARTER,C,A.s().lower(1)).o4(HALF,A.lower(1))
                .bar().o4(QUARTER.dot(), B).o4(A).o4(G,D).o4(F).o4(E).o4(D)
                    .aux().o4(HALF,D).o4(QUARTER,D).r(QUARTER) //5
                .bar().o4(HALF,C,A.lower(1)).o4(QUARTER.dot(),F).o4(E)
                    .aux().o3(HALF,A).o3(QUARTER,A,D.higher(1)).o3(QUARTER,G,D.higher(1))
                .bar().o4(QUARTER.dot(),A).o4(G).o4(B).o4(A).o4(E).o4(C)
                    .aux().o4(HALF,C).o4(QUARTER,E).r(QUARTER)
                .bar().o4(HALF,F).o4(QUARTER.dot(),D).o4(SIXTEENTH,A).o4(SIXTEENTH,A)
                    .aux().o3(QUARTER,A,D.higher(1)).o3(QUARTER,A.s(),C.higher(1)).o3(HALF,A)
                .bar().o4(QUARTER,G).o4(QUARTER,B).o5(QUARTER,D).o4(QUARTER,G)
                    .aux().o4(HALF,D).o4(HALF,D)
                .bar().o4(HALF.dot(),A).o5(C).o4(B)//9
                    .aux().o4(QUARTER,C,E).o3(QUARTER,B,D.higher(1)).o3(HALF,A,C.higher(1))
                .bar().o4(QUARTER.dot(),G).o4(G).o4(E,F).o4(G).o4(D.s(),A).o4(F)
                    .aux().o3(HALF,B,E.higher(1)).o4(QUARTER,E).o3(QUARTER,D.s())
                .bar().o4(QUARTER,B.lower(1),E).o4(QUARTER,A,D.s()).o4(QUARTER.dot(),E).o4(E)
                    .aux().o3(QUARTER,B,E.higher(1)).o3(QUARTER,A,B.s().higher(1)).o3(HALF,G)
                .bar().o4(QUARTER.dot(),F).o4(F).o4(QUARTER,B).o4(QUARTER,A)
                    .aux().o4(HALF,E).o4(HALF,D.s())
                .bar().o4(HALF,B.lower(1),E,G).o4(QUARTER.dot(),A).o4(A) // 10-13
                    .aux().r(HALF).o3(A)
                /*=======================13============================*/
                .bar().o4(QUARTER.dot(),A).o4(G).o4(E).o4(E).o4(D.s()).o4(E)
                    .aux().o3(QUARTER,B,E.higher(1)).o3(QUARTER,B,E.higher(1)).o3(HALF,G,B)
                .bar().o4(HALF,C.higher(1)).o4(A).o4(F).o4(G).o4(A)
                    .aux().o4(QUARTER,E,A).o4(QUARTER,D,G).o4(HALF,C,E)
                .bar().o4(QUARTER,G).o4(QUARTER,B).o4(QUARTER,A).o4(QUARTER,F)
                    .aux().o3(HALF,B,E.higher(1)).o4(QUARTER,E).o4(QUARTER,D.s())
                .bar().o4(HALF,E).o4(QUARTER.dot(), C.higher(1)).o4(B) // 14-17
                    .aux().o3(QUARTER,G,A).o3(QUARTER,A,C.higher(1)).o4(HALF,D.n(),F)
                .bar().o4(QUARTER.dot(),A).o4(E).o4(QUARTER,E).o4(QUARTER,F)
                    .aux(QUARTER).o4(C,E).o3(B,D.higher(1)).o3(HALF,A,C.higher(1))
                .bar().o4(HALF,F).o4(QUARTER,D).o4(EIGHTH.dot(),B).o4(SIXTEENTH,A)
                    .aux(QUARTER).o3(A,D.higher(1)).o3(A.s(),C.higher(1)).o3(B,D.higher(1)).o4(D)
                .bar().o4(QUARTER,G).o4(QUARTER,G).o4(QUARTER,F).o4(QUARTER,E)
                    .aux(HALF).o4(D).o3(G,D.higher(1))
                .bar().o4(HALF.dot(),E).o4(QUARTER,E) // 18-21
                    .aux(QUARTER).o3(HALF,A,C.higher(1)).o3(A).o3(G)
                .bar().o5(QUARTER.dot(),C).o5(C).o4(QUARTER,G,B).o4(QUARTER,E)
                    .aux(HALF).o4(C,A).o4(D)
                .bar().o4(HALF,A).o4(QUARTER.dot(),G).o4(G)
                    .aux(HALF).o3(A,C.higher(1)).o3(G,C.higher(1))
                .bar().o4(QUARTER.dot(),F).o4(E.s()).o4(QUARTER,F).o4(QUARTER,B)
                    .aux(HALF).o3(A,D.higher(1)).o3(A,D.s().higher(1))
                .bar().o4(HALF,B).o5(QUARTER.dot(),C).o4(B) // 22-25
                    .aux(HALF).o4(D,A).o4(D.n(),F)
                .bar().o4(QUARTER.dot(),A).o4(E).o4(QUARTER,E).o4(QUARTER,F)
                    .aux(QUARTER).o4(C,E).o3(B,D.higher(1)).o3(HALF,A,C.higher(1))
                .bar().o4(HALF,F).o4(QUARTER,D).o4(EIGHTH.dot(),B).o4(SIXTEENTH,A)
                    .aux(QUARTER).o3(A,D.higher(1)).o3(A.s(),C.higher(1)).o3(A,D).o4(D)
                .bar().o4(QUARTER,G).o4(QUARTER,G).o4(QUARTER,F).o4(QUARTER,E)
                    .aux(HALF).o4(D).o3(G,D.higher(1))
                .bar().o4(HALF.dot(),C,A,C.higher(1)).o4(QUARTER,C,G.n(),C.higher(1)) // 26-29
                    .aux(WHOLE).o3(E)
                .bar().o5(QUARTER.dot(), E).o5(E).o5(QUARTER,D).o5(QUARTER,C)
                    .aux(QUARTER).o4(HALF,F,A.s()).o4(D,F,A).o4(E,F,A.s())
                .bar().o4(QUARTER,B).o5(QUARTER,C).o5(QUARTER,D).o5(QUARTER,D)
                    .aux(QUARTER).o4(D,F).o4(E,F,A.s()).o4(D,F,A).o4(D,F.n(),A)
                .bar().o5(QUARTER.dot(),C).o5(C).o4(QUARTER.dot(),B).o4(A)
                .aux(HALF).o4(C,E,A).o4(D,E)
                .bar().o4(HALF.dot(),C,E,A) // 30-33
                .ending()
                .build(end());
        var harmonyAux = P2.buildAuxPhrases(end());

        final var P3 = StaffPhraseBuilder.in(KEY, TS, EIGHTH);
        // ── Chords (TODO: fill in) ─────────────────────────────────
        final var chordsMain = P3
                .pickup() // 1 (padding — chords don't play during pickup)
                .bar().o3(HALF,C.higher(1), E, A.lower(1)).o3(HALF, C, A)
                .bar().o3(QUARTER, F.higher(1), C.higher(1),D).o3(QUARTER, C).o3(QUARTER,B.lower(1)).o3(QUARTER,A.lower(1))
                .bar().o3(HALF, E.lower(1), G).o3(HALF,G.lower(1),E)
                .bar().o3(HALF,A.lower(1),E).o3(QUARTER,B.lower(1)).o2(QUARTER,E) // 5
                .bar().o3(HALF,A.lower(1),E).o3(HALF,C,A)
                .bar().o3(QUARTER,D).o3(QUARTER,C).o2(QUARTER,B).o2(QUARTER,A)
                .bar().o3(HALF,G.lower(1),E).o3(HALF,E.lower(1),G)
                .bar().o3(WHOLE, A.lower(1),E) // 9
                .bar().o2(HALF,E).o3(HALF,A)
                    .aux(QUARTER).r(HALF).o2(B).o2(B.lower(1))
                .bar().o2(QUARTER,E).o2(QUARTER,F).o2(HALF,G)
                .bar().o3(HALF, B.lower(1), E.higher(1)).o3(HALF, B.lower(2), D.s().higher(1))
                .bar().o3(QUARTER,E).o3(QUARTER,D.s()).o3(QUARTER,C).o2(QUARTER,B) // 13
                .bar().o3(QUARTER,E.lower(1),G).o3(QUARTER,D.s().lower(1),D.s()).o3(HALF,D.lower(1),D)
                .bar().o3(QUARTER,C.lower(1),C).o2(QUARTER,B.lower(1),B).o2(HALF,A.lower(1),A)
                .bar().o2(HALF,E,B).o3(HALF,B.lower(1),A)
                .bar().o2(HALF,E,B).o3(HALF,E.lower(1),G) // 17
                .bar().o2(QUARTER,A).o2(QUARTER,B).o3(HALF,C)
                .bar().o3(QUARTER,D).o3(QUARTER,C).o2(QUARTER,B).o3(QUARTER,A.lower(1),F)
                .bar().o3(HALF,G.lower(1),E).o3(HALF,E.lower(1),D)
                .bar().o2(QUARTER,A).o2(QUARTER,G).o2(QUARTER,F).o2(QUARTER,E) // 18-21
                .bar().o3(HALF,A.lower(1),E).o3(HALF,G.lower(1),E)
                .bar().o3(HALF,F.lower(1),E).o3(HALF,F.n().lower(1),C)
                .bar().o2(HALF,D,A).o2(HALF,D.s(),B)
                .bar().o3(HALF,E.lower(1),A).o3(HALF,E.lower(1),G) // 22-25
                .bar().o2(QUARTER,A).o2(QUARTER,B).o3(HALF,C)
                .bar().o3(QUARTER,D).o3(QUARTER,C).o2(QUARTER,B).o2(QUARTER,F.higher(1),A)
                .bar().o3(HALF,E,G.lower(1)).o3(HALF,D,E.lower(1))
                .bar().o3(HALF,A.lower(1),A).o3(HALF,G.n().lower(1),G.n()) // 26-29
                .bar().o3(HALF,F.lower(1),F).o2(QUARTER,B.lower(1),B).o3(QUARTER,C.lower(1),C)
                .bar().o3(QUARTER,D.lower(1),D).o3(QUARTER,C.lower(1),C).o2(QUARTER,B.lower(1),B).o2(QUARTER,F.n().lower(1),F.n())
                .bar().o3(QUARTER,E).o2(QUARTER,E).o2(HALF,E)
                    .aux(QUARTER).r(HALF).o3(A).o3(G)
                .bar().o3(HALF.dot(),A.lower(1),E,A).o2(QUARTER,A.lower(1)) // 30-33
                .build(end());
        var chordsAux = P3.buildAuxPhrases(end());

        var harmonyAuxTracks = harmonyAux.stream()
                .map(p -> Track.of("Harmony Aux", FRENCH_HORN, List.of(p)))
                .toList();
        var chordsAuxTracks = chordsAux.stream()
                .map(p -> Track.of("Chords Aux", STRING_ENSEMBLE_1, List.of(p)))
                .toList();

        return new Piece(id.title(), id.composer(),
                KEY, TS,
                new Tempo(104, QUARTER),
                List.of(melody,
                        new Track("Harmony", FRENCH_HORN, List.of(harmonyMain), harmonyAuxTracks),
                        new Track("Chords", STRING_ENSEMBLE_1, List.of(chordsMain), chordsAuxTracks)));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ManualInternationale());
    }
}
