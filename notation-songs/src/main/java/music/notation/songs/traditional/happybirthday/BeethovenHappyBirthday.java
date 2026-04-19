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

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
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
                .pickup(EIGHTH).mf().o4(G).o4(G)
                .bar(QUARTER).o4(A).o4(G).o5(C)
                .bar().o4(HALF, B).ending()
                .build(elision());
    }

    /** Line 2 (f): still single-line but louder — forward momentum. */
    MelodicPhrase line2() {
        return b()
                .pickup(EIGHTH).f().o4(G).o4(G)
                .bar(QUARTER).o4(A).o4(G).o5(D)
                .bar().o5(HALF, C).ending()
                .build(elision());
    }

    /** Line 3 (ff): MELODY DOUBLED IN OCTAVES — dramatic climax for "dear NAME". */
    MelodicPhrase line3() {
        return b()
                .pickup(EIGHTH).ff().o4(G).o4(G)
                // Content bar: melody + lower-octave doubling (poly pitches per beat)
                .bar(QUARTER).o5(G, G.lower(1)).o5(E, E.lower(1)).o5(C, C.lower(1))
                // Sustained "NAME" note held in octaves — the big moment.
                .bar().o4(HALF, B, B.lower(1)).ending()
                .build(elision());
    }

    /** Line 4 (fff): triumphant octaves throughout, final tonic in octaves. */
    MelodicPhrase line4() {
        return b()
                .pickup(EIGHTH).fff().o5(F, F.lower(1)).o5(F, F.lower(1))
                .bar(QUARTER).o5(E, E.lower(1)).o5(C, C.lower(1)).o5(D, D.lower(1))
                .bar().o5(HALF.dot(), C, C.lower(1))       // final tonic C5+C4, full bar
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
        bb.bar(QUARTER).mf().o2(C, C.higher(1)).o3(C, E, G).o3(C, E, G);
        barC(bb); barG7(bb);
        // Line 2 (f): G7 C C
        bb.bar(QUARTER).f().o2(G, G.higher(1)).o3(G, B, F).o3(G, B, F);
        barC(bb); barC(bb);
        // Line 3 (ff): C C F
        bb.bar(QUARTER).ff().o2(C, C.higher(1)).o3(C, E, G).o3(C, E, G);
        barC(bb); barF(bb);
        // Line 4 (fff): F C C(final)
        bb.bar(QUARTER).fff().o2(F, F.higher(1)).o3(F, A, C.higher(1)).o3(F, A, C.higher(1));
        barC(bb); barCFinal(bb);
        return bb.build(end());
    }

    /** Oom-pah-pah C: low octave C bass stab, then two C-major chord stabs. */
    private static void barC(StaffPhraseBuilder bb) {
        bb.bar(QUARTER).o2(C, C.higher(1)).o3(C, E, G).o3(C, E, G);
    }

    /** Oom-pah-pah G7: low octave G bass stab, then two G7 chord stabs (G-B-F, skip the 5th). */
    private static void barG7(StaffPhraseBuilder bb) {
        bb.bar(QUARTER).o2(G, G.higher(1)).o3(G, B, F).o3(G, B, F);
    }

    /** Oom-pah-pah F: low octave F bass stab, then two F-major chord stabs. */
    private static void barF(StaffPhraseBuilder bb) {
        bb.bar(QUARTER).o2(F, F.higher(1)).o3(F, A, C.higher(1)).o3(F, A, C.higher(1));
    }

    /** Final C: held octave bass + full triad — a thundering dotted-half resolution. */
    private static void barCFinal(StaffPhraseBuilder bb) {
        bb.bar().o2(HALF.dot(), C, C.higher(1), E.higher(1), G.higher(1));
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new BeethovenHappyBirthday());
    }
}
