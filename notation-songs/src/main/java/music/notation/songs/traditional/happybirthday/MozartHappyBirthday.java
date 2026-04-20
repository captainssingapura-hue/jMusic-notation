package music.notation.songs.traditional.happybirthday;

import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.Ornament.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * "Happy Birthday" — Mozart-style classical arrangement.
 *
 * <p>Right-hand melody keeps the classic tune, with tasteful ornaments on the
 * sustained notes (turn, trill, mordent) — hallmarks of Mozart's keyboard
 * style. Left hand plays a running <b>Alberti bass</b>: broken-chord eighths
 * in a root-third-fifth-third-fifth-third pattern, giving the transparent,
 * forward-moving texture of Mozart's sonatas.  Tempo bumped to a bright 132
 * BPM (allegretto).</p>
 */
public final class MozartHappyBirthday implements PieceContentProvider<HappyBirthday> {

    private static final KeySignature KEY = DefaultHappyBirthday.KEY;   // C major
    private static final TimeSignature TS = DefaultHappyBirthday.TS;    // 3/4

    private StaffPhraseBuilderTyped b() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Mozart (Alberti Bass)"; }

    @Override
    public Piece create() {
        final var id = new HappyBirthday();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(132, QUARTER),
                List.of(rightHand(), leftHand()));
    }

    // ── Right Hand: ornamented melody ─────────────────────────────────

    private Track rightHand() {
        return Track.of("Right Hand", ACOUSTIC_GRAND_PIANO, List.of(
                line1(), line2(), line3(), line4()));
    }

    /** Line 1: "Happy birthday to you" — MORDENT on the sustained B4. */
    MelodicPhrase line1() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G).done()
                .bar(QUARTER).o4(A).o4(G).o5(C).done()
                .bar().o4(B, HALF, MORDENT).pad(QUARTER).done()     // MORDENT on "you"
                .build(elision());
    }

    /** Line 2: "Happy birthday to you" — TURN on the sustained C5. */
    MelodicPhrase line2() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G).done()
                .bar(QUARTER).o4(A).o4(G).o5(D).done()
                .bar().o5(C, HALF, TURN).pad(QUARTER).done()        // TURN on "you"
                .build(elision());
    }

    /** Line 3: "Happy birthday dear NAME" — TRILL on the sustained B4 (classical flourish). */
    MelodicPhrase line3() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G).done()
                .bar(QUARTER).o5(G).o5(E).o5(C).done()
                .bar().o4(B, HALF, TRILL).pad(QUARTER).done()        // TRILL on the "NAME"
                .build(elision());
    }

    /** Line 4: "Happy birthday to you" — final resolution, no ornament on the last note. */
    MelodicPhrase line4() {
        return b()
                .pickup(EIGHTH).o5(F).o5(F).done()
                .bar(QUARTER).o5(E).o5(C).o5(D).done()
                .bar().o5(HALF.dot(), C).done()                  // final tonic, held
                .build(end());
    }

    // ── Left Hand: Alberti bass (root-third-fifth-third-fifth-third) ──

    private Track leftHand() {
        return Track.of("Left Hand (Alberti)", ACOUSTIC_GRAND_PIANO, List.of(alberti()));
    }

    /** 12-bar Alberti bass matching the RH phrase structure. */
    private MelodicPhrase alberti() {
        var bb = b();
        // Progression (one chord per dotted-half bar):
        //   1: C   2: C   3: G7   (line 1 pickup + content + "you")
        //   4: G7  5: C   6: C    (line 2)
        //   7: C   8: C   9: F    (line 3, F for "dear NAME")
        //  10: F  11: C  12: C    (line 4, resolution)
        bb = barC(bb);  bb = barC(bb);  bb = barG7(bb);
        bb = barG7(bb); bb = barC(bb);  bb = barC(bb);
        bb = barC(bb);  bb = barC(bb);  bb = barF(bb);
        bb = barF(bb);  bb = barC(bb);  bb = barC(bb);
        return bb.build(end());
    }

    /** C-major Alberti bar: C3 E3 G3 E3 G3 E3 (6 eighths). */
    private static StaffPhraseBuilderTyped barC(StaffPhraseBuilderTyped bb) {
        return bb.bar(EIGHTH).o3(C).o3(E).o3(G).o3(E).o3(G).o3(E).done();
    }

    /** G7 Alberti bar: G2 B2 D3 B2 F3 B2 (root-3-5-3-♭7-3, 6 eighths).  F is natural in C major. */
    private static StaffPhraseBuilderTyped barG7(StaffPhraseBuilderTyped bb) {
        return bb.bar(EIGHTH).o2(G).o2(B).o3(D).o2(B).o3(F).o2(B).done();
    }

    /** F-major Alberti bar: F2 A2 C3 A2 C3 A2 (root-3-5-3-5-3, 6 eighths). */
    private static StaffPhraseBuilderTyped barF(StaffPhraseBuilderTyped bb) {
        return bb.bar(EIGHTH).o2(F).o2(A).o3(C).o2(A).o3(C).o2(A).done();
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new MozartHappyBirthday());
    }
}
