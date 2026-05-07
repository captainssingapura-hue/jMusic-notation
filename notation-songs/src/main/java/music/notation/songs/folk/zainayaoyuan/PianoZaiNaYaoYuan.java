package music.notation.songs.folk.zainayaoyuan;

import music.notation.phrase.AuthorPhrase;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.Phrase;
import music.notation.phrase.StaffPhraseBuilderTyped;
import music.notation.play.PlayPiece;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import music.notation.structure.Tempo;
import music.notation.structure.TimeSignature;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * 在那遥远的地方 — solo-piano arrangement, nocturne-style.
 *
 * <p>Two tracks (right hand + left hand, both Acoustic Grand). The
 * melody is stated three times with progressively richer voicing:</p>
 * <ol>
 *   <li>Statement 1 — single-line melody.</li>
 *   <li>Statement 2 — diatonic-thirds harmonisation in the right hand.</li>
 *   <li>Statement 3 — octave doublings on the climactic notes.</li>
 * </ol>
 *
 * <p>The left hand plays a flowing broken-chord pattern throughout
 * (low bass → upper chord-tones → back), Chopin-nocturne style.
 * Tempo 64 BPM, key C major, 4/4. Total ≈ 36 bars / 2:15.</p>
 */
public final class PianoZaiNaYaoYuan implements PieceContentProvider<ZaiNaYaoYuan> {

    static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    static final TimeSignature TS  = new TimeSignature(4, 4);

    private static final int INTRO     = 4;
    private static final int STATEMENT = 10;     // full Wang Luobin verse
    private static final int BRIDGE    = 4;
    private static final int OUTRO     = 4;
    private static final int TOTAL     = INTRO + STATEMENT + STATEMENT
                                       + BRIDGE + STATEMENT + OUTRO;   // 42

    private StaffPhraseBuilderTyped b() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Solo piano (nocturne)"; }

    @Override
    public Piece create() {
        var id = new ZaiNaYaoYuan();

        var rh = joinMelodicPhrases("Right Hand", ACOUSTIC_GRAND_PIANO, rightHandPhrases());
        var lh = joinMelodicPhrases("Left Hand",  ACOUSTIC_GRAND_PIANO, leftHandPhrases());

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS, new Tempo(64, QUARTER),
                List.of(rh, lh),
                List.of());
    }

    // ════════════════════════════════════════════════════════════════
    //  Right Hand — melody, three statements with richer voicing
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> rightHandPhrases() {
        return List.<AuthorPhrase>of(
                silentSection(INTRO),       // intro — LH alone
                statement1(),               // bare-line melody
                statement2(),               // melody + diatonic thirds
                bridgeRH(),                 // brief Am detour
                statement3(),               // melody + octave doublings
                silentSection(OUTRO));      // outro — LH alone fades
    }

    /**
     * Statement 1: faithful 10-bar Wang Luobin melody, four phrases —
     *   <ul>
     *     <li>Bars 1-2 — "在那遥远的地方" (climb to held A)</li>
     *     <li>Bars 3-4 — "有位好姑娘" (climb, descent, settle on E)</li>
     *     <li>Bars 5-6 — "人们走过她的帐房" (climb again, held A)</li>
     *     <li>Bars 7-10 — "都要回头留恋的张望" (descent + held final A in low register)</li>
     *   </ul>
     * The F♯ in bars 1, 5 is the characteristic raised-4 colour.
     */
    private MelodicPhrase statement1() {
        return b()
                // Phrase 1
                .bar().r(QUARTER).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o5(QUARTER, A).o5(EIGHTH, G).o5(EIGHTH, F.s()).done()      // 1
                .bar().o5(EIGHTH, E).o5(EIGHTH, G).o5(HALF.dot(), A).done()       // 2 — held A
                // Phrase 2
                .bar().r(QUARTER).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o5(QUARTER, A).o5(EIGHTH, G).o5(EIGHTH, E).done()          // 3
                .bar().o5(QUARTER, D).o5(EIGHTH, C).o5(EIGHTH, D).o5(HALF, E).done() // 4 — held E
                // Phrase 3
                .bar().r(QUARTER).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o5(QUARTER, A).o5(EIGHTH, G).o5(EIGHTH, F.s()).done()      // 5
                .bar().o5(EIGHTH, E).o5(EIGHTH, G).o5(HALF.dot(), A).done()       // 6 — held A
                // Phrase 4
                .bar().r(QUARTER).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o5(QUARTER, A).o5(EIGHTH, G).o5(EIGHTH, E).done()          // 7
                .bar().o5(QUARTER, D).o5(EIGHTH, C).o5(EIGHTH, D)
                      .o5(QUARTER, E).o5(EIGHTH, D).o5(EIGHTH, C).done()          // 8
                .bar().o4(EIGHTH, B).o4(EIGHTH, A).o4(HALF, A).o4(QUARTER, A).done() // 9
                .bar().o4(WHOLE, A).done()                                        // 10 — final held A
                .build(attacca());
    }

    /** Statement 2: same 10-bar melody, harmonised in diatonic thirds below. */
    private MelodicPhrase statement2() {
        return b()
                // Phrase 1 — third below the melody
                .bar().r(QUARTER).o5(EIGHTH, C, E).o5(EIGHTH, E, G)
                      .o5(QUARTER, F, A).o5(EIGHTH, E, G).o5(EIGHTH, D, F.s()).done() // 1
                .bar().o5(EIGHTH, C, E).o5(EIGHTH, E, G).o5(HALF.dot(), F, A).done()  // 2 — sixth held
                // Phrase 2
                .bar().r(QUARTER).o5(EIGHTH, C, E).o5(EIGHTH, E, G)
                      .o5(QUARTER, F, A).o5(EIGHTH, E, G).o5(EIGHTH, C, E).done()    // 3
                .bar().o5(QUARTER, B.lower(1), D).o5(EIGHTH, A.lower(1), C).o5(EIGHTH, B.lower(1), D)
                      .o5(HALF, C, E).done()                                          // 4
                // Phrase 3
                .bar().r(QUARTER).o5(EIGHTH, C, E).o5(EIGHTH, E, G)
                      .o5(QUARTER, F, A).o5(EIGHTH, E, G).o5(EIGHTH, D, F.s()).done() // 5
                .bar().o5(EIGHTH, C, E).o5(EIGHTH, E, G).o5(HALF.dot(), F, A).done()  // 6
                // Phrase 4
                .bar().r(QUARTER).o5(EIGHTH, C, E).o5(EIGHTH, E, G)
                      .o5(QUARTER, F, A).o5(EIGHTH, E, G).o5(EIGHTH, C, E).done()    // 7
                .bar().o5(QUARTER, B.lower(1), D).o5(EIGHTH, A.lower(1), C).o5(EIGHTH, B.lower(1), D)
                      .o5(QUARTER, C, E).o5(EIGHTH, B.lower(1), D).o5(EIGHTH, A.lower(1), C).done() // 8
                .bar().o4(EIGHTH, G, B).o4(EIGHTH, F, A).o4(HALF, F, A).o4(QUARTER, F, A).done() // 9
                .bar().o4(WHOLE, F, A).done()                                         // 10
                .build(attacca());
    }

    /** Bridge: brief Am detour — slow descent from C6 to A4. */
    private MelodicPhrase bridgeRH() {
        return b()
                .bar().o6(HALF, C).o5(QUARTER, B).o5(QUARTER, A).done()
                .bar().o5(HALF, G).o5(HALF, E).done()
                .bar().o5(QUARTER, F).o5(QUARTER, E).o5(QUARTER, D).o5(QUARTER, C).done()
                .bar().o4(WHOLE, B).done()
                .build(attacca());
    }

    /** Statement 3: full 10-bar melody with octave doublings on climactic notes. */
    private MelodicPhrase statement3() {
        return b()
                // Phrase 1 — climactic A doubled
                .bar().r(QUARTER).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o4(QUARTER, A, A.higher(1)).o5(EIGHTH, G).o5(EIGHTH, F.s()).done()  // 1
                .bar().o5(EIGHTH, E).o5(EIGHTH, G).o4(HALF.dot(), A, A.higher(1)).done()    // 2
                // Phrase 2
                .bar().r(QUARTER).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o4(QUARTER, A, A.higher(1)).o5(EIGHTH, G).o5(EIGHTH, E).done()      // 3
                .bar().o5(QUARTER, D).o5(EIGHTH, C).o5(EIGHTH, D)
                      .o4(HALF, E, E.higher(1)).done()                                      // 4
                // Phrase 3
                .bar().r(QUARTER).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o4(QUARTER, A, A.higher(1)).o5(EIGHTH, G).o5(EIGHTH, F.s()).done()  // 5
                .bar().o5(EIGHTH, E).o5(EIGHTH, G).o4(HALF.dot(), A, A.higher(1)).done()    // 6
                // Phrase 4
                .bar().r(QUARTER).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o4(QUARTER, A, A.higher(1)).o5(EIGHTH, G).o5(EIGHTH, E).done()      // 7
                .bar().o5(QUARTER, D).o5(EIGHTH, C).o5(EIGHTH, D)
                      .o5(QUARTER, E).o5(EIGHTH, D).o5(EIGHTH, C).done()                   // 8
                .bar().o4(EIGHTH, B).o4(EIGHTH, A)
                      .o4(HALF, A, A.higher(1)).o4(QUARTER, A, A.higher(1)).done()         // 9
                .bar().o4(WHOLE, A, A.higher(1)).done()                                    // 10
                .build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Left Hand — Chopin-style broken-chord arpeggios throughout
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> leftHandPhrases() {
        // One unbroken progression spanning all 42 bars:
        //   Intro 4:        C - F - G - C
        //   Statement 1+2:  C F C G C F C Dm G7 Am   (×2 = 20 bars)
        //   Bridge 4:       Am - F - C - G7
        //   Statement 3:    same 10-bar cycle once
        //   Outro 4:        C - F - C - C  (held)
        return List.<AuthorPhrase>of(arpeggios());
    }

    /** Build the entire LH part as one phrase, switching chord per bar. */
    private MelodicPhrase arpeggios() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            switch (chordIndex(bar)) {
                case 0 -> arpC(bb);
                case 1 -> arpF(bb);
                case 2 -> arpG(bb);
                case 3 -> arpDm(bb);
                case 4 -> arpAm(bb);
                case 5 -> arpG7(bb);
            }
        }
        return bb.build(end());
    }

    /** Chord per bar in the order they appear over the 42-bar form. */
    private static int chordIndex(int absBar) {
        // Intro 4: C F G C
        if (absBar < INTRO) {
            return new int[] {0, 1, 2, 0}[absBar];
        }
        int b = absBar - INTRO;
        // Statement 1 + 2 (20 bars): C F C G C F C Dm G7 Am × 2
        if (b < 2 * STATEMENT) {
            return new int[] {0, 1, 0, 2, 0, 1, 0, 3, 5, 4}[b % STATEMENT];
        }
        b -= 2 * STATEMENT;
        // Bridge 4: Am F C G7
        if (b < BRIDGE) {
            return new int[] {4, 1, 0, 5}[b];
        }
        b -= BRIDGE;
        // Statement 3 (10 bars): same cycle once
        if (b < STATEMENT) {
            return new int[] {0, 1, 0, 2, 0, 1, 0, 3, 5, 4}[b];
        }
        b -= STATEMENT;
        // Outro 4: C F C C
        return new int[] {0, 1, 0, 0}[b];
    }

    // ── Chord-by-chord arpeggio helpers ─────────────────────────────
    //  Pattern: bass(EIGHTH) chord-tones up the keyboard then back down.
    //  Each helper places exactly 8 EIGHTH-notes = 64 sf.

    private static void arpC(StaffPhraseBuilderTyped bb) {
        bb.bar(EIGHTH).o2(C).o3(G).o3(E).o3(G).o4(C).o3(G).o3(E).o3(G).done();
    }
    private static void arpF(StaffPhraseBuilderTyped bb) {
        bb.bar(EIGHTH).o2(F).o3(C).o3(F).o3(A).o4(C).o3(A).o3(F).o3(C).done();
    }
    private static void arpG(StaffPhraseBuilderTyped bb) {
        bb.bar(EIGHTH).o2(G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G).o3(D).done();
    }
    private static void arpDm(StaffPhraseBuilderTyped bb) {
        bb.bar(EIGHTH).o2(D).o3(A).o3(D).o3(F).o3(A).o3(F).o3(D).o3(A).done();
    }
    private static void arpAm(StaffPhraseBuilderTyped bb) {
        bb.bar(EIGHTH).o2(A).o3(E).o3(A).o4(C).o3(E).o4(C).o3(A).o3(E).done();
    }
    private static void arpG7(StaffPhraseBuilderTyped bb) {
        bb.bar(EIGHTH).o2(G).o3(D).o3(F).o3(B).o4(D).o3(B).o3(F).o3(D).done();
    }

    // ── shared helper ───────────────────────────────────────────────

    private MelodicPhrase silentSection(int bars) {
        var bb = b();
        for (int i = 0; i < bars; i++) bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    /** Convenience: launch this arrangement directly. */
    public static void main(String[] args) throws Exception {
        PlayPiece.play(new PianoZaiNaYaoYuan());
    }
}
