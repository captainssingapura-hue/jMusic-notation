package music.notation.songs.traditional.happybirthday;

import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * "Happy Birthday" — Chopin-style romantic nocturne arrangement.
 *
 * <p>Slow, expressive (76 BPM, lento-ish). The right-hand melody sings above
 * a wide-spanning left-hand arpeggio pattern reminiscent of Chopin nocturnes
 * (bass quarter + 4 flowing eighths per bar). Grace notes decorate the
 * melodic peaks. Tempo and texture invite rubato in live performance.</p>
 */
public final class ChopinHappyBirthday implements PieceContentProvider<HappyBirthday> {

    private static final KeySignature KEY = DefaultHappyBirthday.KEY;   // C major
    private static final TimeSignature TS = DefaultHappyBirthday.TS;    // 3/4

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Chopin (Nocturne)"; }

    @Override
    public Piece create() {
        final var id = new HappyBirthday();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(76, QUARTER),
                List.of(rightHand(), leftHand()));
    }

    // ── Right Hand: singing melody with Chopinesque grace-note decorations ─

    private Track rightHand() {
        return Track.of("Right Hand", ACOUSTIC_GRAND_PIANO, List.of(
                line1(), line2(), line3(), line4()));
    }

    /** Line 1: "Happy birthday to you" — soft lyrical entry, grace into "you". */
    MelodicPhrase line1() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G)
                .bar(QUARTER).o4(A).o4(G).o5(C)
                // Grace note C5 leaning into the sustained B4 — Chopin's "sigh" gesture.
                .bar().grace(C, 5).main(HALF, 4, B).ending()
                .build(elision());
    }

    /** Line 2: "Happy birthday to you" — small appoggiatura on the higher C5. */
    MelodicPhrase line2() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G)
                .bar(QUARTER).o4(A).o4(G).o5(D)
                // Appoggiatura-style grace leaning into C5.
                .bar().grace(D, 5).main(HALF, 5, C).ending()
                .build(elision());
    }

    /** Line 3: "Happy birthday dear NAME" — tender double-grace into NAME. */
    MelodicPhrase line3() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G)
                .bar(QUARTER).o5(G).o5(E).o5(C)
                // Two grace notes (D-C) caressing into the held B4.
                .bar().grace(D, 5).grace(C, 5).main(HALF, 4, B).ending()
                .build(elision());
    }

    /** Line 4: "Happy birthday to you" — unadorned resolution, tenuto held C5. */
    MelodicPhrase line4() {
        return b()
                .pickup(EIGHTH).o5(F).o5(F)
                .bar(QUARTER).o5(E).o5(C).o5(D)
                .bar().o5(HALF.dot(), C)
                .build(end());
    }

    // ── Left Hand: nocturne arpeggios (bass quarter + 4 flowing eighths) ──

    private Track leftHand() {
        return Track.of("Left Hand (Nocturne)", ACOUSTIC_GRAND_PIANO, List.of(nocturne()));
    }

    /** 12-bar nocturne LH arpeggios matching the RH phrase structure. */
    private MelodicPhrase nocturne() {
        var bb = b();
        //   1: C   2: C   3: G7    (line 1)
        //   4: G7  5: C   6: C     (line 2)
        //   7: C   8: C   9: F     (line 3, subdominant on "dear NAME")
        //  10: F  11: C  12: C     (line 4, final resolution)
        barC(bb); barC(bb); barG7(bb);
        barG7(bb); barC(bb); barC(bb);
        barC(bb); barC(bb); barF(bb);
        barF(bb); barC(bb); barCFinal(bb);
        return bb.build(end());
    }

    /** C-major nocturne bar: low C bass, then E-G-C-G arpeggio (fills upper register). */
    private static void barC(StaffPhraseBuilder bb) {
        bb.bar(EIGHTH).o2(QUARTER, C).o3(E).o3(G).o4(C).o3(G);
    }

    /** G7 nocturne bar: low G bass, then B-D-F-D arpeggio (brings in the ♭7 colour). */
    private static void barG7(StaffPhraseBuilder bb) {
        bb.bar(EIGHTH).o2(QUARTER, G).o2(B).o3(D).o3(F).o3(D);
    }

    /** F-major nocturne bar: low F bass, A-C-F-C arpeggio (warm subdominant colour). */
    private static void barF(StaffPhraseBuilder bb) {
        bb.bar(EIGHTH).o2(QUARTER, F).o2(A).o3(C).o3(F).o3(C);
    }

    /** Final C: spacious dotted-half bass + mid-register triad, letting the sonority ring. */
    private static void barCFinal(StaffPhraseBuilder bb) {
        bb.bar().o2(HALF.dot(), C, E, G);   // wide-voiced C major chord, full-bar hold
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new ChopinHappyBirthday());
    }
}
