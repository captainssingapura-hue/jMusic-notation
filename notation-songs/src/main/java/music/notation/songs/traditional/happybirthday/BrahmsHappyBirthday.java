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
 * "Happy Birthday" — Brahms-style romantic arrangement.
 *
 * <p>Warm, autumnal 88 BPM (moderato). Two Brahms trademarks drive the
 * texture:
 * <ul>
 *   <li><b>Cross-rhythm / hemiola in the left hand</b> — instead of three
 *       quarter-note chord stabs per 3/4 bar, the LH plays <i>two</i>
 *       dotted-quarter stabs, giving the characteristic "2-against-3"
 *       Brahms lilt.</li>
 *   <li><b>Thirds / sixths doubling in the right hand</b> at the climax —
 *       line 3 ("birthday dear NAME") gets the full Brahms warmth with the
 *       melody doubled in diatonic thirds below.</li>
 * </ul>
 * The subdominant colour on "dear NAME" is amplified with a IV chord
 * voicing that includes the 9th for that distinctive Brahms mellowness.</p>
 */
public final class BrahmsHappyBirthday implements PieceContentProvider<HappyBirthday> {

    private static final KeySignature KEY = DefaultHappyBirthday.KEY;   // C major
    private static final TimeSignature TS = DefaultHappyBirthday.TS;    // 3/4

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Brahms (Hemiola)"; }

    @Override
    public Piece create() {
        final var id = new HappyBirthday();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(88, QUARTER),
                List.of(rightHand(), leftHand()));
    }

    // ── Right Hand: melody, with thirds-doubling at the climax ────────

    private Track rightHand() {
        return Track.of("Right Hand", ACOUSTIC_GRAND_PIANO, List.of(
                line1(), line2(), line3(), line4()));
    }

    /** Line 1: plain melody, warm mf. */
    MelodicPhrase line1() {
        return b()
                .pickup(EIGHTH).mf().o4(G).o4(G)
                .bar(QUARTER).o4(A).o4(G).o5(C)
                .bar().o4(HALF, B).ending()
                .build(elision());
    }

    /** Line 2: plain melody, same intensity. */
    MelodicPhrase line2() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G)
                .bar(QUARTER).o4(A).o4(G).o5(D)
                .bar().o5(HALF, C).ending()
                .build(elision());
    }

    /**
     * Line 3: "Happy birthday dear NAME" — the climax.
     * Melody doubled in diatonic thirds below (Brahms-style richness).
     *   G5 → E5 (m3 below);  E5 → C5 (m3);  C5 → A4 (m3);  B4 → G4 (M3)
     */
    MelodicPhrase line3() {
        return b()
                .pickup(EIGHTH).f().o4(G).o4(G)
                // Poly: top voice + third below, all at octave-relative pitches.
                .bar(QUARTER).o5(G, E).o5(E, C).o5(C, A.lower(1))
                // Held "NAME" note doubled in thirds: B4 + G4.
                .bar().o4(HALF, B, G).ending()
                .build(elision());
    }

    /** Line 4: final resolution, settling back to mf; final note is a full C major triad. */
    MelodicPhrase line4() {
        return b()
                .pickup(EIGHTH).mf().o5(F).o5(F)
                .bar(QUARTER).o5(E).o5(C).o5(D)
                // Final chord: C5 + E5 + G5 — a warm full-triad dotted half.
                .bar().o5(HALF.dot(), C, E, G)
                .build(end());
    }

    // ── Left Hand: hemiola — two dotted-quarter chord stabs per 3/4 bar ──

    private Track leftHand() {
        return Track.of("Left Hand (Hemiola)", ACOUSTIC_GRAND_PIANO, List.of(hemiola()));
    }

    /** 12-bar hemiola LH. Each bar = two dotted-quarter chord hits (24+24=48sf). */
    private MelodicPhrase hemiola() {
        var bb = b();
        // Line 1 (mf): C C G7 — dynamic inlined into the first bar.
        bb.bar().mf().o3(QUARTER.dot(), C, E, G).o3(QUARTER.dot(), C, E, G);
        barC(bb); barG7(bb);
        // Line 2: G7 C C
        barG7(bb); barC(bb); barC(bb);
        // Line 3 (f): C C F-with-9th — the subdominant "dear NAME" moment gets extra colour.
        bb.bar().f().o3(QUARTER.dot(), C, E, G).o3(QUARTER.dot(), C, E, G);
        barC(bb); barF9(bb);
        // Line 4 (mf): F C C(final)
        bb.bar().mf().o2(QUARTER.dot(), F).o3(QUARTER.dot(), F, A, C.higher(1));
        barC(bb); barCFinal(bb);
        return bb.build(end());
    }

    /** C-major hemiola bar: two dotted-quarter C-E-G stabs. */
    private static void barC(StaffPhraseBuilder bb) {
        bb.bar().o3(QUARTER.dot(), C, E, G).o3(QUARTER.dot(), C, E, G);
    }

    /** G7 hemiola bar: two dotted-quarter G-B-F stabs (drop the 5th for punch). */
    private static void barG7(StaffPhraseBuilder bb) {
        bb.bar().o3(QUARTER.dot(), G, B, F).o3(QUARTER.dot(), G, B, F);
    }

    /**
     * F with added 9th hemiola bar: F-A-C-G voicing. The 9th (G) adds Brahmsian
     * warmth/ambiguity — a trademark of his subdominant colourings.
     */
    private static void barF9(StaffPhraseBuilder bb) {
        bb.bar().o3(QUARTER.dot(), F, A, C.higher(1), G).o3(QUARTER.dot(), F, A, C.higher(1), G);
    }

    /** Final C: full-bar dotted half chord — wide-voiced C-E-G-C for richness. */
    private static void barCFinal(StaffPhraseBuilder bb) {
        bb.bar().o2(HALF.dot(), C, G, C.higher(1), E.higher(1));
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new BrahmsHappyBirthday());
    }
}
