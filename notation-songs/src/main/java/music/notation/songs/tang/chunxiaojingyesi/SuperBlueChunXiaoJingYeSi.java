package music.notation.songs.tang.chunxiaojingyesi;

import music.notation.phrase.AuthorPhrase;
import music.notation.phrase.BarBuilderTyped;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.Phrase;
import music.notation.phrase.StaffPhraseBuilderTyped;
import music.notation.pitch.Note;
import music.notation.play.PlayPiece;
import music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.Ch;
import music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.Sec;
import music.notation.structure.DrumTrack;
import music.notation.structure.KeySignature;
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
import static music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.*;

/**
 * 春晓 / 静夜思 — the super-blue arrangement, in B♭.
 *
 * <p>The base arrangement's form, textures, drums and dramatic plan
 * (see {@link BluesChunXiaoJingYeSi}) moved up a semitone into the
 * blues key, with dominant sevenths on every morning chord. What
 * changes is the singing:</p>
 *
 * <ul>
 *   <li><b>The blue hexatonic is the key.</b> Every sung note is one of
 *       B♭ · D♭ · E♭ · E · F · A♭ (1 ♭3 4 ♭5 5 ♭7) — with one
 *       exception, below. The day/night contrast lives entirely in
 *       the harmony's third; the singer never changes scale.</li>
 *   <li><b>Call and response.</b> Each line is sung in one bar —
 *       1 · 1½ · 1½ beats for the 2+3 syllable split — and the band's
 *       riff answers in the next. Sustained rhymes (光 霜 鄉 聲) tie
 *       across into the answer bar.</li>
 *   <li><b>The door is harmonic.</b> D♭ sung over E♭7 in §2 is a blue
 *       seventh; the same D♭ over E♭m7 in §4 is a chord tone. Same
 *       line, the chord changes what the note means.</li>
 *   <li><b>鄉 is the outside note.</b> Over Fm7 it hangs on C — the
 *       fifth of the v, and the one pitch the blue hexatonic does not
 *       contain. Home is the note the key cannot reach.</li>
 * </ul>
 *
 * <p>Solo 1 (§3) is written B♭3–B♭5 so it plays on guitar or trumpet
 * unchanged — swap the patch at play time. Solo 2 (§7) is the Rhodes.
 * Both keep the base arrangement's sweep-arpeggio showcase shapes, with
 * the blue third, flat five and seventh worked into the lines.</p>
 */
public final class SuperBlueChunXiaoJingYeSi implements PieceContentProvider<ChunXiaoJingYeSi> {

    static final KeySignature KEY = new KeySignature(B, music.notation.pitch.Accidental.FLAT, Mode.MAJOR);
    static final TimeSignature TS  = BluesChunXiaoJingYeSi.TS;
    static final int BPM = 92;

    private final BluesChunXiaoJingYeSi form = new BluesChunXiaoJingYeSi();

    @Override public String subtitle() { return "Super Blue in B♭"; }

    private StaffPhraseBuilderTyped b() { return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER); }
    private StaffPhraseBuilderTyped vb(int sh) { return b().octaveShift(sh); }

    private Ch chordAt(int bar) { return form.chordAt(bar); }

    // ════════════════════════════════════════════════════════════════
    //  The blue hexatonic — written against the B♭-major signature
    //  (B and E default to flats), so D♭ and A♭ are spelled, E♮ is the
    //  ♭5, and C — the outside note — is spelled plainly.
    // ════════════════════════════════════════════════════════════════

    /** Degrees of the blue hexatonic in B♭, plus the one outside note. */
    enum Blue {
        R  (B,       0),   // 1   B♭
        F3 (D.f(),   1),   // ♭3  D♭
        P4 (E,       1),   // 4   E♭
        F5 (E.n(),   1),   // ♭5  E♮
        P5 (F,       1),   // 5   F
        F7 (A.f(),   1),   // ♭7  A♭
        OUT(C,       1);   // 2   C — not in the hexatonic; only 鄉 sings it

        final Note note; final int octaveUp;
        Blue(Note n, int up) { note = n; octaveUp = up; }
    }

    /** Emit one hexatonic degree at the octave whose root is B♭{@code root}. */
    private static BarBuilderTyped n(BarBuilderTyped bar, music.notation.duration.Duration d, int root, Blue deg) {
        int oct = root + deg.octaveUp;
        return switch (oct) {
            case 2 -> bar.o2(d, deg.note);
            case 3 -> bar.o3(d, deg.note);
            case 4 -> bar.o4(d, deg.note);
            case 5 -> bar.o5(d, deg.note);
            case 6 -> bar.o6(d, deg.note);
            default -> throw new IllegalArgumentException("octave " + oct);
        };
    }

    // ════════════════════════════════════════════════════════════════
    //  Harmony in B♭ — the base table's roles, re-voiced
    // ════════════════════════════════════════════════════════════════

    /**
     * Voicings around B♭3–C5. Morning chords are all dominant: the base's
     * I becomes B♭7, IV becomes E♭7. Night chords keep their sevenths and
     * the G♭maj7's clean major seventh.
     */
    static Note[] voicingBb(Ch c) {
        return switch (c) {
            case A_MAJ, A7 -> new Note[]{ B, D.higher(1), F.higher(1), A.f().higher(1) };      // B♭7
            case D_MAJ     -> new Note[]{ E.higher(1), G.higher(1), B.higher(1), D.f().higher(2) }; // E♭7
            case DM        -> new Note[]{ E.higher(1), G.f().higher(1), B.higher(1), D.f().higher(2) }; // E♭m7
            case E7        -> new Note[]{ F, A, C.higher(1), E.higher(1) };                     // F7
            case AM        -> new Note[]{ B, D.f().higher(1), F.higher(1) };                    // B♭m
            case AM7       -> new Note[]{ B, D.f().higher(1), F.higher(1), A.f().higher(1) };   // B♭m7
            case FMAJ7     -> new Note[]{ G.f(), B, D.f().higher(1), F.higher(1) };             // G♭maj7
            case EM        -> new Note[]{ F, A.f(), C.higher(1) };                              // Fm
            case C_MAJ     -> new Note[]{ D.f().higher(1), F.higher(1), A.f().higher(1) };      // D♭
            case CMAJ7     -> new Note[]{ D.f().higher(1), F.higher(1), A.f().higher(1), C.higher(2) }; // D♭maj7
            case E7SUS4    -> new Note[]{ F, B, C.higher(1), E.higher(1) };                     // F7sus4
        };
    }

    static Note rootBb(Ch c) {
        return switch (c) {
            case A_MAJ, A7, AM, AM7 -> B;
            case D_MAJ, DM          -> E.higher(1);
            case E7, EM, E7SUS4     -> F;
            case FMAJ7              -> G.f();
            case C_MAJ, CMAJ7       -> D.f().higher(1);
        };
    }

    static Note fifthBb(Ch c) {
        return switch (c) {
            case A_MAJ, A7, AM, AM7 -> F.higher(1);
            case D_MAJ, DM          -> B;
            case E7, EM, E7SUS4     -> C.higher(1);
            case FMAJ7              -> D.f().higher(1);
            case C_MAJ, CMAJ7       -> A.f();
        };
    }

    // ════════════════════════════════════════════════════════════════
    //  Piece
    // ════════════════════════════════════════════════════════════════

    @Override
    public Piece create() {
        var id = new ChunXiaoJingYeSi();

        var female     = joinMelodicPhrases("Voice (F)",   VOICE_OOHS,       voicePhrases(0));
        var male       = joinMelodicPhrases("Voice (M)",   SYNTH_VOICE,      voicePhrases(-1));
        var choir      = joinMelodicPhrases("Choir",       CHOIR_AAHS,       choirPhrases());
        var solo1Track = joinMelodicPhrases("Solo 1",      OVERDRIVEN_GUITAR, solo1Phrases());
        var rhodesSolo = joinMelodicPhrases("Rhodes Solo", ELECTRIC_PIANO_1, rhodesSoloPhrases());
        var guitar     = joinMelodicPhrases("Guitar",      ELECTRIC_GUITAR_JAZZ, List.of(guitarPhrase()));
        var rhodesRH   = joinMelodicPhrases("Rhodes RH",   ELECTRIC_PIANO_1, List.of(rhodesPhrase()));
        var rhodesLH   = joinMelodicPhrases("Rhodes LH",   ELECTRIC_PIANO_1, List.of(rhodesLeftHand()));
        var bass       = joinMelodicPhrases("Bass",        ACOUSTIC_BASS,    List.of(bassPhrase()));
        var drums      = new DrumTrack("Drums", Phrase.of(BluesChunXiaoJingYeSi.drumBars()));

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS, new Tempo(BPM, QUARTER),
                List.of(female, male, choir, solo1Track, rhodesSolo, guitar, rhodesRH, rhodesLH, bass),
                List.of(drums));
    }

    // ════════════════════════════════════════════════════════════════
    //  Voice — six notes, call and response. Odd bars sing; even bars
    //  belong to the band, except where a level-tone rhyme ties over.
    //  Rhythm per line: 1 · 1½ · 1½ beats for the 2+3 split.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> voicePhrases(int sh) {
        return List.of(
                silent(Sec.INTRO.bars),
                chunxiao(sh),
                silent(Sec.GUITAR_SOLO.bars),
                chunxiao(sh),                      // §4 — the door is in the harmony now
                jingyesi(sh, false),
                jingyesi(sh, true),                // §6 — 望 lifts to ♭3′
                silent(Sec.RHODES_SOLO.bars),
                jingyesiStripped(sh),
                chunxiao(sh),
                coda(sh));
    }

    /**
     * 春晓 — root at B♭3, conversational and low.
     * <pre>
     *  1  春 眠 不覺曉    1 ♩ · ♭3 ♩. · 4 ♭5 5 ♪♪♪ ~      曉 on the fifth, carries a beat
     *  2  5 ♩ · rest
     *  3  處 處 聞啼鳥    5 ♩ · 1′ ♩. · ♭7 ♪ 5 ♪ · ♭5 ♪        鳥 on the ♭5 to the bar line …
     *  4  5 ♩ · rest                                         … sliding onto the fifth
     *  5  夜 來 風雨聲    ♭3 ♩ · 4 ♩. · ♭5 ♪ 5 ♪ · 1′ ♪ ~     風雨 slurred, half the length of 夜; 聲 rises, ties over
     *  6  1′ ♩. · rest
     *  7  花 落 知多少    ♭3′ ♩ · 1′ ♩. · A 1′ 1′ ♪♪♪ ~      知 on A♮ — the lower neighbour
     *  8  1′ ♪ · rest  (caesura)                          少 shoots half a beat over
     * </pre>
     */
    private MelodicPhrase chunxiao(int sh) {
        var bb = vb(sh);
        // 1: 春 眠 不 覺 曉
        var b1 = bb.bar();
        n(b1, QUARTER, 3, Blue.R); n(b1, QUARTER.dot(), 3, Blue.F3);
        n(b1, EIGHTH, 3, Blue.P4); n(b1, EIGHTH, 3, Blue.F5);
        n(b1, EIGHTH, 3, Blue.P5).tieNext().done();
        bb.bar().o4(QUARTER, F).r(HALF.dot()).done();                                   // 曉 carries a beat
        // 3: 處 處 聞 啼 鳥
        var b3 = bb.bar();
        n(b3, QUARTER, 3, Blue.P5); n(b3, QUARTER.dot(), 4, Blue.R);
        n(b3, EIGHTH, 3, Blue.F7); n(b3, EIGHTH, 3, Blue.P5);
        n(b3, EIGHTH, 3, Blue.F5).done();                                                 // 鳥 on E for the rest of the bar …
        bb.bar().o4(QUARTER, F).r(HALF.dot()).done();                                   // … sliding onto F for a beat
        // 5: 夜 來 風雨 聲 — 風雨 slurred sixteenths through the ♭5; 聲 rises to the octave and ties over
        var b5 = bb.bar();
        n(b5, QUARTER, 3, Blue.F3); n(b5, QUARTER.dot(), 3, Blue.P4);
        n(b5, EIGHTH, 3, Blue.F5); n(b5, EIGHTH, 3, Blue.P5);
        n(b5, EIGHTH, 4, Blue.R).tieNext().done();
        bb.bar().o4(QUARTER.dot(), B).r(EIGHTH).r(HALF).done();
        // 7: 花 落 知 多 少 — D♭5 down to the root, 知 on A♮: the lower neighbour worrying the B♭
        var b7 = bb.bar();
        n(b7, QUARTER, 4, Blue.F3); n(b7, QUARTER.dot(), 4, Blue.R);
        b7.o4(EIGHTH, A);
        n(b7, EIGHTH, 4, Blue.R); n(b7, EIGHTH, 4, Blue.R).tieNext().done();
        bb.bar().o4(EIGHTH, B).r(EIGHTH).r(HALF.dot()).done();                          // 少 shoots half a beat over
        return bb.build(attacca());
    }

    /**
     * 静夜思 — root at B♭3 for the first two lines, the octave for the lift.
     * <pre>
     *  1  床 前 明月光     1 ♩ · ♭3 ♩. · 4 ♭5 5 ♪♪♪ ~        光 ties over, held on 5
     *  2  5 𝅗𝅥 · rest
     *  3  疑 是 地上霜     5 ♩ · ♭7 ♩. · 5 4 5 ♪♪♪ ~         霜 ties over
     *  4  5 𝅗𝅥 · rest
     *  5  舉 頭 望明月     1′ ♩ · ♭7 ♩. · 5 ♭7 1′ ♪♪♪       §6: 望 → ♭3′ (D♭5)
     *  6  1′ ♩ · rest  (§6: ♭3′ ♩)                          月 carries a beat into the answer
     *  7  低 頭 思故鄉     5 ♩ · 4 ♩. · ♭3 1 C ♪♪♪ ~         鄉 on the outside note, ties over
     *  8  C 𝅗𝅥 · rest                                          (caesura)
     * </pre>
     */
    private MelodicPhrase jingyesi(int sh, boolean lift) {
        var bb = vb(sh);
        line1(bb); line2(bb); line3(bb, lift); line4(bb);
        return bb.build(attacca());
    }

    /** §8: the head never lifts again — lines 1, 2, 4, 4. */
    private MelodicPhrase jingyesiStripped(int sh) {
        var bb = vb(sh);
        line1(bb); line2(bb); line4(bb); line4(bb);
        return bb.build(attacca());
    }

    private static void line1(StaffPhraseBuilderTyped bb) {
        var b1 = bb.bar();
        n(b1, QUARTER, 3, Blue.R); n(b1, QUARTER.dot(), 3, Blue.F3);
        n(b1, EIGHTH, 3, Blue.P4); n(b1, EIGHTH, 3, Blue.F5);
        n(b1, EIGHTH, 3, Blue.P5).tieNext().done();
        bb.bar().o4(HALF, F).r(HALF).done();
    }

    private static void line2(StaffPhraseBuilderTyped bb) {
        var b3 = bb.bar();
        n(b3, QUARTER, 3, Blue.P5); n(b3, QUARTER.dot(), 3, Blue.F7);
        n(b3, EIGHTH, 3, Blue.P5); n(b3, EIGHTH, 3, Blue.P4);
        n(b3, EIGHTH, 3, Blue.P5).tieNext().done();
        bb.bar().o4(HALF, F).r(HALF).done();
    }

    private static void line3(StaffPhraseBuilderTyped bb, boolean lift) {
        var b5 = bb.bar();
        n(b5, QUARTER, 4, Blue.R); n(b5, QUARTER.dot(), 3, Blue.F7);
        n(b5, EIGHTH, 3, Blue.P5); n(b5, EIGHTH, 3, Blue.F7);
        if (lift) n(b5, EIGHTH, 4, Blue.F3).tieNext(); else n(b5, EIGHTH, 4, Blue.R).tieNext();
        b5.done();
        // 月 lasts a beat into the answer bar before the band takes over.
        if (lift) bb.bar().o5(QUARTER, D.f()).r(HALF.dot()).done();
        else      bb.bar().o4(QUARTER, B).r(HALF.dot()).done();
    }

    private static void line4(StaffPhraseBuilderTyped bb) {
        var b7 = bb.bar();
        n(b7, QUARTER, 3, Blue.P5); n(b7, QUARTER.dot(), 3, Blue.P4);
        n(b7, EIGHTH, 3, Blue.F3); n(b7, EIGHTH, 3, Blue.R);
        n(b7, EIGHTH, 3, Blue.OUT).tieNext().done();
        bb.bar().o4(QUARTER.dot(), C).r(EIGHTH).r(HALF).done();
    }

    /**
     * Coda: 花落知多少 across B♭7 · E♭m7 · G♭maj7 · Fm7, one degree per
     * bar down the scale — ♭7 · 5/4 · ♭3 · 1 — 少 scooped from ♭7 onto
     * the root; then the choir takes the sus4 tail.
     */
    private MelodicPhrase coda(int sh) {
        var bb = vb(sh);
        var c1 = bb.bar(); n(c1, HALF.dot(), 3, Blue.F7); c1.r(EIGHTH); n(c1, EIGHTH, 3, Blue.P5).done();  // 花 落
        var c2 = bb.bar(); n(c2, WHOLE, 3, Blue.P4).done();                                              // 知
        var c3 = bb.bar(); n(c3, HALF.dot(), 3, Blue.F3); c3.r(QUARTER).done();                          // 多
        bb.bar().grace(A.f(), 3).main(HALF, 3, B).r(HALF).done();                                        // 少
        for (int i = 0; i < 4; i++) bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Choir — unison from 疑是地上霜 in §5; half-note pads plus the
    //  低頭 line in §6; hummed under §9; the closing lines on the tail.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> choirPhrases() {
        return List.of(
                silent(Sec.INTRO.bars + Sec.CHUNXIAO_1.bars + Sec.GUITAR_SOLO.bars + Sec.CHUNXIAO_2.bars),
                choirUnison(),
                choirLift(),
                silent(Sec.RHODES_SOLO.bars + Sec.JINGYESI_3.bars),
                choirHum(),
                choirCoda());
    }

    private MelodicPhrase choirUnison() {
        var bb = b();
        bb.bar().r(WHOLE).done().bar().r(WHOLE).done();
        line2(bb); line3(bb, false); line4(bb);
        return bb.build(attacca());
    }

    /** §6: pads under lines 1–3 (high and still under 舉頭), then 低頭思故鄉 in unison, an octave up. */
    private MelodicPhrase choirLift() {
        var bb = b();
        int s = Sec.JINGYESI_2.start();
        for (int i = 0; i < 6; i++) {
            var bar_ = bb.bar();
            Note[] v = voicingBb(chordAt(s + i));
            bar_.o4(HALF, v[0], v[1], v[2]).o4(HALF, v[1], v[2], v.length > 3 ? v[3] : v[0].higher(1));
            bar_.done();
        }
        line4(bb);
        return bb.build(attacca());
    }

    private MelodicPhrase choirHum() {
        var bb = b();
        int s = Sec.CHUNXIAO_3.start();
        for (int i = 0; i < 7; i++) { var bar_ = bb.bar(); held(bar_, voicingBb(chordAt(s + i))); bar_.done(); }
        bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    /**
     * Tail over F7sus4 (F B♭ C E♭): the choir sings the two closing lines
     * as one melodic line — words, not chords — in the chord's tones.
     * 鄉 on C — the fifth, and the outside note again; 少 on B♭ — the
     * suspended fourth — held to the end.
     * <pre>
     *  5  低 頭 ·        E♭5 ♩ · C5 𝅗𝅥.
     *  6  思 故 鄉       B♭4 ♩ · C5 ♪ rest ♪ · C5 𝅗𝅥
     *  7  花 落 ·        E♭5 𝅗𝅥. · rest ♪ · C5 ♪
     *  8  知 多 少       B♭4 ♩ · C5 ♩ · B♭4 𝅗𝅥
     * </pre>
     */
    private MelodicPhrase choirCoda() {
        var bb = b();
        for (int i = 0; i < 4; i++) bb.bar().r(WHOLE).done();
        bb.bar().o5(QUARTER, E).o5(HALF.dot(), C).done()                                   // 低 頭
          .bar().o4(QUARTER, B).o5(EIGHTH, C).r(EIGHTH).o5(HALF, C).done()                   // 思 故 鄉
          .bar().o5(HALF.dot(), E).r(EIGHTH).o5(EIGHTH, C).done()                            // 花 落
          .bar().o4(QUARTER, B).o5(QUARTER, C).o4(HALF, B).done();                           // 知 多 少
        return bb.build(attacca());
    }
    // ════════════════════════════════════════════════════════════════
    //  Solo 1 — §3 over B♭7 · B♭7 · B♭7 · B♭7 · E♭7 · E♭7 · B♭7 · F7.
    //  The base arrangement's sweep-arpeggio showcase, in the blues
    //  key: dominant-seventh arpeggios two octaves up and back, a
    //  chromatic walk-up, a pedal figure worrying the blue third against
    //  the major third, ♭5 and ♭7 as passing and landing tones. Written
    //  B♭3–B♭5 so guitar and trumpet both sit inside it.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> solo1Phrases() {
        return List.of(
                silent(Sec.GUITAR_SOLO.start()),
                solo1(),
                silent(TOTAL - Sec.GUITAR_SOLO.start() - Sec.GUITAR_SOLO.bars));
    }

    private MelodicPhrase solo1() {
        Note Db = D.f(), Ab = A.f(), En = E.n();
        return b()
                // 1 (B♭7): sweep the seventh chord two octaves to B♭5, fall through ♭7 to the fifth.
                .bar(SIXTEENTH).o3(B).o4(D).o4(F).o4(Ab).o4(B).o5(D).o5(F).o5(Ab)
                               .o5(EIGHTH, B).o5(EIGHTH, Ab).o5(QUARTER, F).done()
                // 2 (B♭7): pedal on F5, worrying ♭3 against 3 underneath; then a ♭7 run down.
                .bar(SIXTEENTH).o5(F).o5(Db).o5(F).o5(D).o5(F).o5(Db).o5(F).o5(D)
                               .o5(EIGHTH, Ab).o5(EIGHTH, F).o5(EIGHTH, E).o5(EIGHTH, Db).done()
                // 3 (B♭7): chromatic walk-up B♭–D♭–D–E♭–E–F, then the seventh arpeggio down.
                .bar(SIXTEENTH).o3(B).o4(Db).o4(D).o4(E).o4(En).o4(F).o4(Ab).o4(B)
                               .o5(EIGHTH, Ab).o5(EIGHTH, F).o5(EIGHTH, D).o4(EIGHTH, B).done()
                // 4 (B♭7): full sweep to B♭5 and straight back with ♭5 and ♭3 on the way down. Sixteen notes.
                .bar(SIXTEENTH).o4(D).o4(F).o4(Ab).o4(B).o5(D).o5(F).o5(Ab).o5(B)
                               .o5(Ab).o5(F).o5(En).o5(E).o5(Db).o5(D).o4(B).o4(Ab).done()
                // 5 (E♭7): E♭7 arpeggio to B♭5, turn, settle.
                .bar(SIXTEENTH).o4(E).o4(G).o4(B).o5(Db).o5(E).o5(G).o5(B).o5(G)
                               .o5(EIGHTH, E).o5(EIGHTH, Db).o4(QUARTER, B).done()
                // 6 (E♭7): the key's ♭5 against the IV, then a run down to the root.
                .bar(SIXTEENTH).o4(B).o5(Db).o5(E).o5(En).o5(F).o5(En).o5(E).o5(Db)
                               .o4(B).o4(Ab).o4(G).o4(F).o4(E).o4(Db).o4(D).o3(B).done()
                // 7 (B♭7): climb two octaves, top out on B♭5, close on the blue third.
                .bar(SIXTEENTH).o3(B).o4(D).o4(F).o4(Ab).o4(B).o5(D).o5(F).o5(Ab)
                               .o5(EIGHTH, B).o5(EIGHTH, Ab).o5(EIGHTH, F).o5(EIGHTH, Db).done()
                // 8 (F7): turnaround — F7 arpeggio over the top, A♭ (the ♯9) as the last colour, breathe.
                .bar(SIXTEENTH).o4(F).o4(A).o5(C).o5(E).o5(F).o5(E).o5(C).o4(A)
                               .o4(EIGHTH, F).o4(EIGHTH, Ab).r(QUARTER).done()
                .build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Rhodes Solo — §7 over B♭m7 ×2. The base arrangement's shape —
    //  a spacious first chorus that cascades and lets each bar hang, a
    //  dense second chorus climbing to B♭6 — in the minor blues, with
    //  the ♭5 threaded through the descents and the outside note as the
    //  very last sound.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> rhodesSoloPhrases() {
        return List.of(
                silent(Sec.RHODES_SOLO.start()),
                rhodesSolo(),
                silent(TOTAL - Sec.RHODES_SOLO.start() - Sec.RHODES_SOLO.bars));
    }

    private MelodicPhrase rhodesSolo() {
        Note Db = D.f(), Gb = G.f(), Ab = A.f(), En = E.n();
        return b()
                // ── Chorus 1 — spacious ──
                // 1 (B♭m7): roll up two octaves, hold the fifth.
                .bar(SIXTEENTH).o3(B).o4(Db).o4(F).o4(Ab).o4(B).o5(Db).o5(F).o5(Ab)
                               .o5(HALF, F).done()
                // 2 (B♭m7): fall through the ♭5, settle on the blue third.
                .bar(SIXTEENTH).o5(Ab).o5(F).o5(En).o5(E).o5(Db).o4(B).o4(Ab).o4(F)
                               .o4(EIGHTH, E).o4(EIGHTH, F).o4(QUARTER, Db).done()
                // 3 (B♭m9): pedal on A♭5.
                .bar(SIXTEENTH).o5(Ab).o5(F).o5(Ab).o5(Db).o5(Ab).o4(B).o5(Ab).o5(F)
                               .o5(EIGHTH, B).o5(EIGHTH, Ab).o5(EIGHTH, F).o5(EIGHTH, Db).done()
                // 4 (B♭m9): full sweep B♭3 → A♭6, turn through the ♭5.
                .bar(SIXTEENTH).o3(B).o4(Db).o4(F).o4(Ab).o4(B).o5(Db).o5(F).o5(Ab)
                               .o5(B).o6(Db).o6(F).o6(Ab).o6(F).o6(En).o6(Db).o5(B).done()
                // 5 (E♭m7): arpeggio to D♭6, come down.
                .bar(SIXTEENTH).o4(E).o4(Gb).o4(B).o5(Db).o5(E).o5(Gb).o5(B).o6(Db)
                               .o6(EIGHTH, E).o6(EIGHTH, Db).o5(QUARTER, B).done()
                // 6 (E♭m7): colour, then run down to E♭3.
                .bar(SIXTEENTH).o5(B).o5(Ab).o5(Gb).o5(E).o5(Db).o5(E).o4(B).o4(Ab)
                               .o4(Gb).o4(E).o4(Db).o3(B).o3(Ab).o3(Gb).o3(F).o3(E).done()
                // 7 (G♭maj7): rise through the major seventh.
                .bar(SIXTEENTH).o3(Gb).o3(B).o4(Db).o4(F).o4(Gb).o4(B).o5(Db).o5(F)
                               .o5(EIGHTH, Gb).o5(EIGHTH, F).o5(QUARTER, Db).done()
                // 8 (Fm7): fall to a low held F — weight before the second chorus.
                .bar(SIXTEENTH).o5(C).o4(Ab).o4(F).o4(E).o4(C).o3(Ab).o3(F).o3(E)
                               .o3(HALF, F).done()

                // ── Chorus 2 — dense ──
                // 1 (B♭m7): cascade through the 9th to A♭6 and back with the ♭5.
                .bar(SIXTEENTH).o4(B).o5(Db).o5(F).o5(Ab).o6(C).o6(Db).o6(F).o6(Ab)
                               .o6(F).o6(En).o6(Db).o6(C).o5(Ab).o5(F).o5(Db).o4(B).done()
                // 2 (B♭m7): broken-chord figure, opening out.
                .bar(SIXTEENTH).o5(Db).o4(B).o5(F).o4(B).o5(Db).o4(B).o5(F).o4(B)
                               .o5(Db).o4(B).o5(F).o4(B).o5(Db).o5(F).o5(Ab).o6(Db).done()
                // 3 (B♭m9): pedal A♭5 against falling tones, then a run to A♭6.
                .bar(SIXTEENTH).o5(Ab).o5(F).o5(Ab).o5(Db).o5(Ab).o4(B).o5(Ab).o4(F)
                               .o4(B).o5(Db).o5(F).o5(Ab).o5(B).o6(Db).o6(F).o6(Ab).done()
                // 4 (B♭m9): descending sweep from B♭6 through the ♭5, land on B♭3.
                .bar(SIXTEENTH).o6(B).o6(Ab).o6(F).o6(En).o6(Db).o5(B).o5(Ab).o5(F)
                               .o5(En).o5(Db).o4(B).o4(Ab)
                               .o3(QUARTER, B).done()
                // 5 (E♭m7): E♭m with the 9th, up to F6 and back.
                .bar(SIXTEENTH).o4(E).o4(Gb).o4(B).o5(Db).o5(E).o5(F).o5(Gb).o5(B)
                               .o6(Db).o6(E).o6(F).o6(E).o6(Db).o5(B).o5(Gb).o5(E).done()
                // 6 (E♭m7): falling, then climbing into the G♭.
                .bar(SIXTEENTH).o5(Db).o5(E).o5(Gb).o5(B).o5(Ab).o5(Gb).o5(E).o5(Db)
                               .o4(B).o5(Db).o5(E).o5(Gb).o5(B).o6(Db).o6(E).o6(Gb).done()
                // 7 (G♭maj7): down from F6, back up.
                .bar(SIXTEENTH).o6(F).o6(Db).o5(B).o5(Gb).o5(F).o5(Db).o4(B).o4(Gb)
                               .o4(F).o4(Gb).o4(B).o5(Db).o5(F).o5(Gb).o5(B).o6(Db).done()
                // 8 (Fm7): down through the chord, then F · A♭ · C — the outside note last — and breathe.
                .bar(SIXTEENTH).o5(C).o4(Ab).o4(F).o4(E).o4(C).o3(Ab).o3(F).o3(E)
                               .o3(F).o3(Ab).o4(EIGHTH, C).r(QUARTER).done()
                .build(attacca());
    }


    // ════════════════════════════════════════════════════════════════
    //  Band — the base arrangement's textures with B♭ voicings
    // ════════════════════════════════════════════════════════════════

    private MelodicPhrase guitarPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = sectionAt(bar);
            int cb = chorusBar(bar);
            Note[] v = voicingBb(chordAt(bar));
            var bar_ = bb.bar();
            switch (s) {
                case INTRO, CHUNXIAO_1, CHUNXIAO_2 -> riff(bar_, v, breathAt(bar));
                case GUITAR_SOLO -> rest(bar_);
                case RHODES_SOLO -> pick(bar_, v, breathAt(bar));
                case JINGYESI_1, JINGYESI_2, JINGYESI_3 -> rest(bar_);
                case CHUNXIAO_3 -> { if (cb == 7) stopTime(bar_, v); else riff(bar_, v, breathAt(bar)); }
                case CODA -> { if (cb < 4) riff(bar_, v, breathAt(bar)); else pick(bar_, v, breathAt(bar)); }
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    private MelodicPhrase rhodesPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = sectionAt(bar);
            Note[] v = voicingBb(chordAt(bar));
            var bar_ = bb.bar();
            switch (s) {
                case JINGYESI_1, JINGYESI_2 -> nightRiff(bar_, v, breathAt(bar));
                case GUITAR_SOLO -> roll(bar_, v, breathAt(bar));
                case RHODES_SOLO -> rest(bar_);
                case JINGYESI_3, CODA -> sparse(bar_, v, breathAt(bar));
                default -> rest(bar_);
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    private MelodicPhrase rhodesLeftHand() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = sectionAt(bar);
            Ch ch = chordAt(bar);
            var bar_ = bb.bar();
            switch (s) {
                case GUITAR_SOLO, JINGYESI_1, JINGYESI_2, RHODES_SOLO, JINGYESI_3, CODA
                        -> leftHand(bar_, rootBb(ch), fifthBb(ch), voicingBb(ch), breathAt(bar));
                default -> rest(bar_);
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    private MelodicPhrase bassPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = sectionAt(bar);
            int cb = chorusBar(bar);
            Ch ch = chordAt(bar);
            var bar_ = bb.bar();
            switch (s) {
                case INTRO, JINGYESI_3 -> rest(bar_);
                case CHUNXIAO_3 -> {
                    if (cb == 7) bar_.o2(QUARTER, rootBb(ch)).r(HALF.dot());
                    else rootFifth(bar_, rootBb(ch), fifthBb(ch), breathAt(bar));
                }
                default -> rootFifth(bar_, rootBb(ch), fifthBb(ch), breathAt(bar));
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    // ── shared ──────────────────────────────────────────────────────

    private MelodicPhrase silent(int bars) {
        var bb = b();
        for (int i = 0; i < bars; i++) bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new SuperBlueChunXiaoJingYeSi());
    }
}
