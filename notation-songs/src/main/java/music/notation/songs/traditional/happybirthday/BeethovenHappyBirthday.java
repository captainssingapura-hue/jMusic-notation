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
 * "Happy Birthday" — Beethoven-style heroic arrangement.
 *
 * <p>A dramatic, building interpretation in the spirit of late-Beethoven
 * heroic writing (think the 5th Symphony's C-major finale). The piece starts
 * at <b>mf</b> and builds phrase-by-phrase to <b>fff</b>: bare melody →
 * stronger → melody doubled in octaves → triumphant octaves. The left hand
 * plays a driving "oom-pah-pah" pattern with low octave bass on beat 1 and
 * block chord stabs on beats 2–3 — no Alberti flow, just rhythmic
 * punctuation and weight.</p>
 */
public final class BeethovenHappyBirthday implements PieceContentProvider<HappyBirthday> {

    private static final KeySignature KEY = DefaultHappyBirthday.KEY;   // C major
    private static final TimeSignature TS = DefaultHappyBirthday.TS;    // 3/4

    private StaffPhraseBuilderTyped b() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Beethoven (Heroic)"; }

    @Override
    public Piece create() {
        final var id = new HappyBirthday();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(120, QUARTER),                // driving but not frantic
                List.of(rightHand(), leftHand()));
    }

    // ── Right Hand: single-line melody that grows into octave doubling ──

    private Track rightHand() {
        return Track.of("Right Hand", ACOUSTIC_GRAND_PIANO, List.of(
                line1(), line2(), line3(), line4()));
    }

    /** Line 1 (mf): plain melody, setting the scene. */
    MelodicPhrase line1() {
        return b()
                .pickup(EIGHTH).mf().o4(G).o4(G).done()
                .bar(QUARTER).o4(A).o4(G).o5(C).done()
                .bar().o4(HALF, B).pad(QUARTER).done()
                .build(elision());
    }

    /** Line 2 (f): still single-line but louder — forward momentum. */
    MelodicPhrase line2() {
        return b()
                .pickup(EIGHTH).f().o4(G).o4(G).done()
                .bar(QUARTER).o4(A).o4(G).o5(D).done()
                .bar().o5(HALF, C).pad(QUARTER).done()
                .build(elision());
    }

    /** Line 3 (ff): MELODY DOUBLED IN OCTAVES — dramatic climax for "dear NAME". */
    MelodicPhrase line3() {
        return b()
                .pickup(EIGHTH).ff().o4(G).o4(G).done()
                // Content bar: melody + lower-octave doubling (poly pitches per beat)
                .bar(QUARTER).o5(G, G.lower(1)).o5(E, E.lower(1)).o5(C, C.lower(1)).done()
                // Sustained "NAME" note held in octaves — the big moment.
                .bar().o4(HALF, B, B.lower(1)).pad(QUARTER).done()
                .build(elision());
    }

    /** Line 4 (fff): triumphant octaves throughout, final tonic in octaves. */
    MelodicPhrase line4() {
        return b()
                .pickup(EIGHTH).fff().o5(F, F.lower(1)).o5(F, F.lower(1)).done()
                .bar(QUARTER).o5(E, E.lower(1)).o5(C, C.lower(1)).o5(D, D.lower(1)).done()
                .bar().o5(HALF.dot(), C, C.lower(1)).done()       // final tonic C5+C4, full bar
                .build(end());
    }

    // ── Left Hand: heroic octave bass + block-chord stabs ──────────────

    private Track leftHand() {
        return Track.of("Left Hand (Heroic)", ACOUSTIC_GRAND_PIANO, List.of(heroicBass()));
    }

    /** 12-bar oom-pah-pah pattern matching RH phrase structure, with dynamics mirroring RH. */
    private MelodicPhrase heroicBass() {
        var bb = b();
        // Line 1 (mf): C C G7 — dynamic inlined into the first bar so it fires before any note.
        bb = bb.bar(QUARTER).mf().o2(C, C.higher(1)).o3(C, E, G).o3(C, E, G).done();
        bb = barC(bb);  bb = barG7(bb);
        // Line 2 (f): G7 C C
        bb = bb.bar(QUARTER).f().o2(G, G.higher(1)).o3(G, B, F).o3(G, B, F).done();
        bb = barC(bb);  bb = barC(bb);
        // Line 3 (ff): C C F
        bb = bb.bar(QUARTER).ff().o2(C, C.higher(1)).o3(C, E, G).o3(C, E, G).done();
        bb = barC(bb);  bb = barF(bb);
        // Line 4 (fff): F C C(final)
        bb = bb.bar(QUARTER).fff().o2(F, F.higher(1)).o3(F, A, C.higher(1)).o3(F, A, C.higher(1)).done();
        bb = barC(bb);  bb = barCFinal(bb);
        return bb.build(end());
    }

    /** Oom-pah-pah C: low octave C bass stab, then two C-major chord stabs. */
    private static StaffPhraseBuilderTyped barC(StaffPhraseBuilderTyped bb) {
        return bb.bar(QUARTER).o2(C, C.higher(1)).o3(C, E, G).o3(C, E, G).done();
    }

    /** Oom-pah-pah G7: low octave G bass stab, then two G7 chord stabs (G-B-F, skip the 5th). */
    private static StaffPhraseBuilderTyped barG7(StaffPhraseBuilderTyped bb) {
        return bb.bar(QUARTER).o2(G, G.higher(1)).o3(G, B, F).o3(G, B, F).done();
    }

    /** Oom-pah-pah F: low octave F bass stab, then two F-major chord stabs. */
    private static StaffPhraseBuilderTyped barF(StaffPhraseBuilderTyped bb) {
        return bb.bar(QUARTER).o2(F, F.higher(1)).o3(F, A, C.higher(1)).o3(F, A, C.higher(1)).done();
    }

    /** Final C: held octave bass + full triad — a thundering dotted-half resolution. */
    private static StaffPhraseBuilderTyped barCFinal(StaffPhraseBuilderTyped bb) {
        return bb.bar().o2(HALF.dot(), C, C.higher(1), E.higher(1), G.higher(1)).done();
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new BeethovenHappyBirthday());
    }
}
