package music.notation.songs.tang.chunxiaojingyesi;

import music.notation.duration.Duration;
import music.notation.event.PercussionSound;
import music.notation.phrase.AuthorPhrase;
import music.notation.phrase.Bar;
import music.notation.phrase.BarBuilderTyped;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.StaffPhraseBuilderTyped;
import music.notation.pitch.Note;
import music.notation.play.PlayPiece;
import music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.Ch;
import music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.Sec;
import music.notation.structure.DrumTrack;
import music.notation.structure.KeySignature;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import music.notation.structure.Tempo;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;
import static music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.fifth;
import static music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.root;
import static music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.voicing;

/**
 * 春晓 / 静夜思 — U2-style rock arrangement.
 *
 * <p>Same 88-bar form, harmony and dramatic plan as the blues version
 * (the door, the lift, the withheld tonic — see
 * {@link BluesChunXiaoJingYeSi}), re-clothed: The Edge's dotted-quarter
 * delay arpeggios on clean guitar, picked eighth-note bass on root and
 * fifth, rock organ and piano in the night, half-time verses that open
 * into crash-eighth anthem choruses. ♩ = 116.</p>
 *
 * <p>The vocal line is the blues melody straightened for the idiom:
 * no blues scoops, line-ends sustained rather than clipped, 鳥 on the
 * major third instead of the blue third, 少 lifted to E5 over the V so
 * every 春晓 chorus ends on an open, ringing fifth. The night lines are
 * unchanged — they were already anthemic.</p>
 */
public final class U2RockChunXiaoJingYeSi implements PieceContentProvider<ChunXiaoJingYeSi> {

    static final KeySignature KEY = BluesChunXiaoJingYeSi.KEY;
    static final TimeSignature TS  = BluesChunXiaoJingYeSi.TS;
    static final int BPM = 116;
    private static final int BAR_SF = 64;
    private static final PhraseNode REST_Q = new RestNode(Duration.of(QUARTER));
    private static final int TOTAL = BluesChunXiaoJingYeSi.TOTAL;

    /** Form and harmony are the blues arrangement's; only the clothes change. */
    private final BluesChunXiaoJingYeSi form = new BluesChunXiaoJingYeSi();

    @Override public String subtitle() { return "U2 Rock"; }

    private StaffPhraseBuilderTyped b() { return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER); }
    private StaffPhraseBuilderTyped vb(int sh) { return b().octaveShift(sh); }

    private Ch chordAt(int bar) { return form.chordAt(bar); }
    private static boolean breathAt(int bar) { return BluesChunXiaoJingYeSi.breathAt(bar); }

    @Override
    public Piece create() {
        var id = new ChunXiaoJingYeSi();

        var female = joinMelodicPhrases("Voice (F)",   VOICE_OOHS,            voicePhrases(0));
        var male   = joinMelodicPhrases("Voice (M)",   SYNTH_VOICE,           voicePhrases(-1));
        var choir  = joinMelodicPhrases("Choir",       CHOIR_AAHS,            choirPhrases());
        var lead   = joinMelodicPhrases("Lead Guitar", OVERDRIVEN_GUITAR,     leadPhrases());
        var edge   = joinMelodicPhrases("Edge Guitar", ELECTRIC_GUITAR_CLEAN, List.of(edgePhrase()));
        var piano  = joinMelodicPhrases("Piano",       ACOUSTIC_GRAND_PIANO,  List.of(pianoPhrase()));
        var organ  = joinMelodicPhrases("Organ",       ROCK_ORGAN,            List.of(organPhrase()));
        var bass   = joinMelodicPhrases("Bass",        ELECTRIC_BASS_PICK,    List.of(bassPhrase()));
        var drums  = new DrumTrack("Drums", Phrase.of(drumBars()));

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS, new Tempo(BPM, QUARTER),
                List.of(female, male, choir, lead, edge, piano, organ, bass),
                List.of(drums));
    }

    // ════════════════════════════════════════════════════════════════
    //  Voice — the blues line, straightened for the idiom
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> voicePhrases(int sh) {
        return List.of(
                silent(Sec.INTRO.bars),
                chunxiao(sh, Third.MAJOR),
                silent(Sec.GUITAR_SOLO.bars),
                chunxiao(sh, Third.MINOR),
                jingyesi(sh, Lift.NONE),
                jingyesi(sh, Lift.MOON),
                silent(Sec.RHODES_SOLO.bars),
                jingyesiStripped(sh),
                chunxiao(sh, Third.MAJOR),
                coda(sh));
    }

    private enum Third { MAJOR, MINOR }
    private enum Lift { NONE, MOON }

    /**
     * 春晓, rock reading. Same skeleton as the blues line, with the
     * line-ends opened up: 曉 held on A4, 鳥 on C♯5 (major third, no
     * scoop), 聲 held a full bar and a half, 少 up to E5 over the E7.
     * <pre>
     *  1 (A)    春 眠 ·        E4 ♩ · C♯4 𝅗𝅥 · rest ♩
     *  2 (A)    不 覺 曉       rest ♪ E4 ♪ F♯4 ♪ · A4 ♩. ♩              (曉 held)
     *  3 (A7)   處 處 ·        E4 ♩ · G♮4 ♩ · rest 𝅗𝅥
     *  4 (A7)   聞 啼 鳥       A4 ♩ · B4 ♩ · C♯5 / C♮5 𝅗𝅥                (鳥 sustained on the third)
     *  5 (D/Dm) 夜 來 ·        D4 ♩ · F♯4 / F♮4 𝅗𝅥.                     (the door)
     *  6 (D/Dm) 風 雨 聲       E4 ♩ · D4 ♪ rest ♪ · A4 𝅗𝅥
     *  7 (A/Am) 花 落 ·        rest ♩ · C♯5 / C♮5 ♩. · A4 ♪ · rest ♩
     *  8 (E7)   知 多 少       B4 ♩ · D5 ♩ · E5 ♩. · rest ♪                (少 rings on the fifth, then the caesura)
     * </pre>
     */
    private MelodicPhrase chunxiao(int sh, Third third) {
        Note comeHome = third == Third.MAJOR ? F : F.n();
        Note bird     = third == Third.MAJOR ? C : C.n();
        Note flower   = bird;
        return vb(sh)
                .bar().o4(QUARTER, E).o4(HALF, C).r(QUARTER).done()                                 // 春 眠
                .bar().r(EIGHTH).o4(EIGHTH, E).o4(EIGHTH, F).o4(QUARTER.dot(), A).o4(QUARTER, A).done() // 不 覺 曉
                .bar().o4(QUARTER, E).o4(QUARTER, G.n()).r(HALF).done()                              // 處 處
                .bar().o4(QUARTER, A).o4(QUARTER, B).o5(HALF, bird).done()                          // 聞 啼 鳥
                .bar().o4(QUARTER, D).o4(HALF.dot(), comeHome).done()                               // 夜 來
                .bar().o4(QUARTER, E).o4(EIGHTH, D).r(EIGHTH).o4(HALF, A).done()                     // 風 雨 聲
                .bar().r(QUARTER).o5(QUARTER.dot(), flower).o4(EIGHTH, A).r(QUARTER).done()          // 花 落
                .bar().o4(QUARTER, B).o5(QUARTER, D).o5(QUARTER.dot(), E).r(EIGHTH).done()      // 知 多 少 (released before the caesura)
                .build(attacca());
    }

    /** 静夜思 — the blues line as is; it already sings like an anthem. */
    private MelodicPhrase jingyesi(int sh, Lift lift) {
        var bb = vb(sh);
        line1(bb); line2(bb); line3(bb, lift); line4(bb);
        return bb.build(attacca());
    }

    private MelodicPhrase jingyesiStripped(int sh) {
        var bb = vb(sh);
        line1(bb); line2(bb); line4(bb); line4(bb);
        return bb.build(attacca());
    }

    private static void line1(StaffPhraseBuilderTyped bb) {
        bb.bar().r(QUARTER).o4(QUARTER, E).o4(HALF, A).done()                                        // 床 前
          .bar().o4(QUARTER, A).o4(EIGHTH, G.n()).r(EIGHTH).o4(HALF, A).done();                      // 明 月 光
    }

    private static void line2(StaffPhraseBuilderTyped bb) {
        bb.bar().r(QUARTER).o4(QUARTER, E).o4(EIGHTH, G.n()).r(EIGHTH).r(QUARTER).done()             // 疑 是
          .bar().r(EIGHTH).o4(EIGHTH, A).o4(EIGHTH, G.n()).r(EIGHTH).o4(HALF, E).done();             // 地 上 霜
    }

    private static void line3(StaffPhraseBuilderTyped bb, Lift lift) {
        if (lift == Lift.MOON) {
            bb.bar().o4(QUARTER, A).o5(HALF.dot(), E).done()                                         // 舉 頭
              .bar().o5(QUARTER, G.n()).o5(QUARTER, E).o5(EIGHTH, D).r(EIGHTH).r(QUARTER).done();    // 望 明 月
        } else {
            bb.bar().o4(QUARTER, A).o5(HALF.dot(), D).done()
              .bar().o5(QUARTER, E).o5(QUARTER, D).o5(EIGHTH, C.n()).r(EIGHTH).r(QUARTER).done();
        }
    }

    private static void line4(StaffPhraseBuilderTyped bb) {
        bb.bar().o5(QUARTER, C.n()).o4(HALF.dot(), A).done()                                         // 低 頭
          .bar().o4(QUARTER, G.n()).o4(EIGHTH, A).r(EIGHTH).o4(QUARTER.dot(), B).r(EIGHTH).done(); // 思 故 鄉 (released before the caesura)
    }

    /** Coda: as the blues, but 少 is sung straight — no scoop — and held a full half. */
    private MelodicPhrase coda(int sh) {
        var bb = vb(sh)
                .bar().o5(HALF.dot(), C).r(EIGHTH).o4(EIGHTH, A).done()                              // 花 落
                .bar().o4(WHOLE, F.n()).done()                                                       // 知
                .bar().o4(HALF.dot(), E).r(QUARTER).done()                                           // 多
                .bar().o4(HALF, A).r(HALF).done();                                                   // 少
        for (int i = 0; i < 4; i++) bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Choir — oohs on the anthem: half-note chord pads through the
    //  lift and the build, hummed under 春晓 III, then the blues
    //  arrangement's closing lines on the sus4 tail.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> choirPhrases() {
        return List.of(
                silent(Sec.INTRO.bars + Sec.CHUNXIAO_1.bars + Sec.GUITAR_SOLO.bars + Sec.CHUNXIAO_2.bars),
                silent(Sec.JINGYESI_1.bars),
                pads(Sec.JINGYESI_2),
                padsSecondHalf(Sec.RHODES_SOLO),
                silent(Sec.JINGYESI_3.bars),
                pads(Sec.CHUNXIAO_3),
                form.choirCoda());
    }

    private MelodicPhrase pads(Sec s) {
        var bb = b();
        for (int i = 0; i < s.bars; i++) {
            var bar_ = bb.bar();
            if (s == Sec.CHUNXIAO_3 && i == 7) rest(bar_);            // stop-time
            else pad(bar_, chordAt(s.start() + i), breathAt(s.start() + i));
            bar_.done();
        }
        return bb.build(attacca());
    }

    /** Silent for the first eight bars of a 16-bar section, pads for the last eight. */
    private MelodicPhrase padsSecondHalf(Sec s) {
        var bb = b();
        for (int i = 0; i < s.bars; i++) {
            var bar_ = bb.bar();
            if (i < 8) rest(bar_); else pad(bar_, chordAt(s.start() + i), breathAt(s.start() + i));
            bar_.done();
        }
        return bb.build(attacca());
    }

    /** Two half-note chords: the voicing, then its upper inversion. */
    private static void pad(BarBuilderTyped bar, Ch ch, boolean breath) {
        Note[] v = voicing(ch);
        Note low = v[0], mid = v[1], hi = v[2];
        Note top = v.length > 3 ? v[3] : v[0].higher(1);
        bar.o4(HALF, low, mid, hi);
        if (breath) bar.o4(QUARTER, mid, hi, top).r(QUARTER); else bar.o4(HALF, mid, hi, top);
    }

    // ════════════════════════════════════════════════════════════════
    //  Lead Guitar — the two instrumental sections. Edge-style: long
    //  tones, octave leaps, one motif repeated up the chord, not a
    //  sweep. Overdriven.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> leadPhrases() {
        return List.of(
                silent(Sec.GUITAR_SOLO.start()),
                leadMorning(),
                silent(Sec.RHODES_SOLO.start() - Sec.GUITAR_SOLO.start() - Sec.GUITAR_SOLO.bars),
                leadNight(),
                silent(TOTAL - Sec.RHODES_SOLO.start() - Sec.RHODES_SOLO.bars));
    }

    /** §3 over form A: a rising three-note call answered an octave up. */
    private MelodicPhrase leadMorning() {
        var Gn = G.n();
        return b()
                .bar().o5(QUARTER, E).o5(QUARTER, A).o5(HALF, C).done()                        // A
                .bar().o5(QUARTER, E).o5(QUARTER, A).o6(HALF, E).done()                        // A   — octave leap
                .bar().o5(QUARTER, Gn).o5(QUARTER, A).o6(HALF, C).done()                       // A7
                .bar().o6(QUARTER, E).o6(QUARTER, C).o5(QUARTER, A).o5(QUARTER, Gn).done()     // A7  — falling
                .bar().o5(QUARTER, F).o5(QUARTER, A).o6(HALF, D).done()                        // D
                .bar().o6(QUARTER, D).o6(QUARTER, C).o5(QUARTER, A).o5(QUARTER, F).done()      // D
                .bar().o5(QUARTER, E).o5(QUARTER, A).o6(HALF, C).done()                        // A
                .bar().o5(QUARTER, B).o6(QUARTER, D).o6(QUARTER, E).r(QUARTER).done()          // E7  — into the door, then breathe
                .build(attacca());
    }

    /** §7 over B×2: the same call in the minor, the second chorus an octave higher and denser. */
    private MelodicPhrase leadNight() {
        var Cn = C.n(); var Gn = G.n(); var Fn = F.n();
        return b()
                // chorus 1 — the call, low and long
                .bar().o4(QUARTER, E).o4(QUARTER, A).o5(HALF, Cn).done()                       // Am
                .bar().o5(QUARTER, E).o5(QUARTER, Cn).o4(HALF, A).done()                       // Am
                .bar().o4(QUARTER, Gn).o4(QUARTER, A).o5(HALF, Cn).done()                      // Am7
                .bar().o5(QUARTER, E).o5(QUARTER, Gn).o5(HALF, A).done()                       // Am7
                .bar().o4(QUARTER, Fn).o4(QUARTER, A).o5(HALF, D).done()                       // Dm
                .bar().o5(QUARTER, Fn).o5(QUARTER, D).o4(HALF, A).done()                       // Dm
                .bar().o4(QUARTER, A).o5(QUARTER, Cn).o5(HALF, E).done()                       // Fmaj7
                .bar().o5(QUARTER, Gn).o5(QUARTER, E).o4(HALF, B).done()                       // Em
                // chorus 2 — an octave up, eighths
                .bar(EIGHTH).o5(E).o5(A).o6(Cn).o6(E).o6(Cn).o5(A).o5(E).o5(A).done()          // Am
                .bar(EIGHTH).o6(E).o6(Cn).o5(A).o5(E).o5(A).o6(Cn).o6(E).o6(A).done()          // Am
                .bar(EIGHTH).o5(Gn).o5(A).o6(Cn).o6(E).o6(Gn).o6(E).o6(Cn).o5(A).done()        // Am7
                .bar(EIGHTH).o6(E).o6(Gn).o6(A).o6(Gn).o6(E).o6(Cn).o5(A).o5(Gn).done()        // Am7
                .bar(EIGHTH).o5(Fn).o5(A).o6(D).o6(Fn).o6(A).o6(Fn).o6(D).o5(A).done()         // Dm
                .bar(EIGHTH).o6(D).o6(Fn).o6(A).o6(Fn).o6(D).o5(A).o5(Fn).o5(D).done()         // Dm
                .bar().o5(QUARTER, A).o6(QUARTER, Cn).o6(HALF, E).done()                       // Fmaj7 — long again
                .bar().o6(QUARTER, Gn).o6(QUARTER, E).o5(QUARTER, B).r(QUARTER).done()       // Em   — stops on the fifth, then breathe
                .build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Edge Guitar — the delay arpeggio, ♩. ♪ ♩. ♪ on root · fifth ·
    //  third · octave, chiming in the fourth octave. Opens the piece
    //  alone; sustains open fifths in the stripped chorus.
    // ════════════════════════════════════════════════════════════════

    private MelodicPhrase edgePhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = BluesChunXiaoJingYeSi.sectionAt(bar);
            int cb = BluesChunXiaoJingYeSi.chorusBar(bar);
            Ch ch = chordAt(bar);
            var bar_ = bb.bar();
            switch (s) {
                case JINGYESI_3 -> fifths(bar_, ch, breathAt(bar));                       // stripped: open fifths, half notes
                case CHUNXIAO_3 -> { if (cb == 7) stab(bar_, ch); else delay(bar_, ch, breathAt(bar)); }
                case CODA       -> { if (cb < 4) delay(bar_, ch, breathAt(bar)); else fifths(bar_, ch, breathAt(bar)); }
                default         -> delay(bar_, ch, breathAt(bar));
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    /** The Edge figure: ♩. root · ♪ fifth · ♩. third · ♪ octave, an octave above the comp voicing. */
    private static void delay(BarBuilderTyped bar, Ch ch, boolean breath) {
        Note[] v = voicing(ch);
        Note low = v[0], mid = v[1], hi = v[2];
        Note top = v.length > 3 ? v[3] : v[0].higher(1);
        bar.o4(QUARTER.dot(), low).o4(EIGHTH, hi);
        if (breath) bar.o4(QUARTER, mid).r(QUARTER); else bar.o4(QUARTER.dot(), mid).o4(EIGHTH, top);
    }

    /** Open fifth, two half notes. */
    private static void fifths(BarBuilderTyped bar, Ch ch, boolean breath) {
        Note[] v = voicing(ch);
        bar.o4(HALF, v[0], v[2]);
        if (breath) bar.o4(QUARTER, v[0], v[2]).r(QUARTER); else bar.o4(HALF, v[0], v[2]);
    }

    private static void stab(BarBuilderTyped bar, Ch ch) {
        bar.o4(QUARTER, voicing(ch)).r(HALF.dot());
    }

    // ════════════════════════════════════════════════════════════════
    //  Piano — driving eighth-note broken chords through the night;
    //  out in the morning. Rests for the stripped chorus.
    // ════════════════════════════════════════════════════════════════

    private MelodicPhrase pianoPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = BluesChunXiaoJingYeSi.sectionAt(bar);
            var bar_ = bb.bar();
            switch (s) {
                case JINGYESI_1, JINGYESI_2, RHODES_SOLO, CODA -> eighths(bar_, chordAt(bar), breathAt(bar));
                default -> rest(bar_);
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    /** low · hi · mid · top, twice — eighths in the third octave. */
    private static void eighths(BarBuilderTyped bar, Ch ch, boolean breath) {
        Note[] v = voicing(ch);
        Note low = v[0], mid = v[1], hi = v[2];
        Note top = v.length > 3 ? v[3] : v[0].higher(1);
        bar.o3(EIGHTH, low).o3(EIGHTH, hi).o3(EIGHTH, mid).o3(EIGHTH, top)
           .o3(EIGHTH, low).o3(EIGHTH, hi);
        if (breath) bar.r(QUARTER); else bar.o3(EIGHTH, mid).o3(EIGHTH, top);
    }

    // ════════════════════════════════════════════════════════════════
    //  Organ — the wash under the night and the build; half-note
    //  swells so it breathes with the changes.
    // ════════════════════════════════════════════════════════════════

    private MelodicPhrase organPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = BluesChunXiaoJingYeSi.sectionAt(bar);
            int cb = BluesChunXiaoJingYeSi.chorusBar(bar);
            var bar_ = bb.bar();
            switch (s) {
                case CHUNXIAO_2 -> { if (cb >= 4) swell(bar_, chordAt(bar), breathAt(bar)); else rest(bar_); }   // enters at the door
                case JINGYESI_1, JINGYESI_2, RHODES_SOLO, JINGYESI_3, CODA -> swell(bar_, chordAt(bar), breathAt(bar));
                default -> rest(bar_);
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    private static void swell(BarBuilderTyped bar, Ch ch, boolean breath) {
        Note[] v = voicing(ch);
        Note low = v[0], mid = v[1], hi = v[2];
        Note top = v.length > 3 ? v[3] : v[0].higher(1);
        bar.o3(HALF, low, hi);
        if (breath) bar.o3(QUARTER, mid, hi, top).r(QUARTER); else bar.o3(HALF, mid, hi, top);
    }

    // ════════════════════════════════════════════════════════════════
    //  Bass — picked eighths on root and fifth: r r 5 5 r r 5 r.
    //  In from 春晓 I; out for the stripped chorus.
    // ════════════════════════════════════════════════════════════════

    private MelodicPhrase bassPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = BluesChunXiaoJingYeSi.sectionAt(bar);
            int cb = BluesChunXiaoJingYeSi.chorusBar(bar);
            Ch ch = chordAt(bar);
            var bar_ = bb.bar();
            switch (s) {
                case INTRO       -> { if (cb >= 4) drive(bar_, ch, breathAt(bar)); else rest(bar_); }
                case JINGYESI_3  -> rest(bar_);
                case CHUNXIAO_3  -> { if (cb == 7) bar_.o2(QUARTER, root(ch)).r(HALF.dot()); else drive(bar_, ch, breathAt(bar)); }
                default          -> drive(bar_, ch, breathAt(bar));
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    private static void drive(BarBuilderTyped bar, Ch ch, boolean breath) {
        Note r = root(ch), f = fifth(ch);
        bar.o2(EIGHTH, r).o2(EIGHTH, r).o2(EIGHTH, f).o2(EIGHTH, f)
           .o2(EIGHTH, r).o2(EIGHTH, r);
        if (breath) bar.r(QUARTER); else bar.o2(EIGHTH, f).o2(EIGHTH, r);
    }

    // ════════════════════════════════════════════════════════════════
    //  Drums — half-time verses, atmospheric night, anthem for the
    //  lift and the return, ride for the build. Stop-time at bar 80.
    // ════════════════════════════════════════════════════════════════

    private List<Bar> drumBars() {
        var bars = new ArrayList<Bar>(TOTAL);
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = BluesChunXiaoJingYeSi.sectionAt(bar);
            int cb = BluesChunXiaoJingYeSi.chorusBar(bar);
            bars.add(switch (s) {
                case INTRO       -> cb < 4 ? silentBar() : tickBar(breathAt(bar));
                case CHUNXIAO_1  -> cb == 7 ? halfTimeFill() : halfTime(false);
                case GUITAR_SOLO -> cb == 7 ? halfTimeFill() : halfTime(false);
                case CHUNXIAO_2  -> cb < 4 ? halfTime(false) : atmospheric(breathAt(bar));               // the door darkens the kit
                case JINGYESI_1  -> atmospheric(breathAt(bar));
                case JINGYESI_2  -> cb == 7 ? anthemFill() : anthem(false);                 // the lift
                case RHODES_SOLO -> bar - s.start() < 8
                        ? (cb == 7 ? halfTimeFill() : rideHalfTime(false))
                        : (cb == 7 ? anthemFill() : anthem(false));
                case JINGYESI_3  -> tickBar(breathAt(bar));                                          // stripped
                case CHUNXIAO_3  -> cb == 7 ? stopTime() : anthem(false);
                case CODA        -> cb < 4 ? halfTime(false) : (cb == 4 ? finalCrash() : silentBar());
            });
        }
        return bars;
    }

    private static Bar tickBar(boolean breath) {
        var h = perc(CLOSED_HI_HAT, QUARTER);
        return breath ? Bar.of(BAR_SF, h, h, h, REST_Q) : Bar.of(BAR_SF, h, h, h, h);
    }

    /** Kick on 1, snare on 3, open hat eighths. */
    private static Bar halfTime(boolean breath) {
        var k = perc(BASS_DRUM, EIGHTH); var oh = perc(OPEN_HI_HAT, EIGHTH); var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return breath ? Bar.of(BAR_SF, k, oh, oh, oh, s, oh, REST_Q) : Bar.of(BAR_SF, k, oh, oh, oh, s, oh, oh, oh);
    }

    private static Bar halfTimeFill() {
        var k = perc(BASS_DRUM, EIGHTH); var oh = perc(OPEN_HI_HAT, EIGHTH); var s = perc(ACOUSTIC_SNARE, EIGHTH);
        var ht = perc(HIGH_TOM, EIGHTH); var mt = perc(HIGH_MID_TOM, EIGHTH); var lt = perc(LOW_TOM, EIGHTH);
        return Bar.of(BAR_SF, k, oh, oh, oh, s, ht, REST_Q);        // fill, then the caesura
    }

    /** Kick + open hat in quarters — the night hush. */
    private static Bar atmospheric(boolean breath) {
        var k = perc(BASS_DRUM, QUARTER); var oh = perc(OPEN_HI_HAT, QUARTER);
        return breath ? Bar.of(BAR_SF, k, oh, oh, REST_Q) : Bar.of(BAR_SF, k, oh, oh, oh);
    }

    /** Full backbeat with crash eighths. */
    private static Bar anthem(boolean breath) {
        var k = perc(BASS_DRUM, EIGHTH); var c = perc(CRASH_CYMBAL, EIGHTH); var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return breath ? Bar.of(BAR_SF, k, c, s, c, k, c, REST_Q) : Bar.of(BAR_SF, k, c, s, c, k, c, s, c);
    }

    private static Bar anthemFill() {
        var k = perc(BASS_DRUM, EIGHTH); var c = perc(CRASH_CYMBAL, EIGHTH); var s = perc(ACOUSTIC_SNARE, EIGHTH);
        var ht = perc(HIGH_TOM, EIGHTH); var mt = perc(HIGH_MID_TOM, EIGHTH); var lt = perc(LOW_TOM, EIGHTH);
        return Bar.of(BAR_SF, k, c, s, s, ht, mt, REST_Q);         // fill, then the caesura
    }

    private static Bar rideHalfTime(boolean breath) {
        var k = perc(BASS_DRUM, EIGHTH); var r = perc(RIDE_CYMBAL, EIGHTH); var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return breath ? Bar.of(BAR_SF, k, r, r, r, s, r, REST_Q) : Bar.of(BAR_SF, k, r, r, r, s, r, r, r);
    }

    private static Bar stopTime() {
        var k = perc(BASS_DRUM, EIGHTH); var c = perc(CRASH_CYMBAL, EIGHTH);
        return Bar.of(BAR_SF, k, c, (PhraseNode) new RestNode(HALF.dot()));
    }

    private static Bar finalCrash() {
        var c = perc(CRASH_CYMBAL, EIGHTH); var k = perc(BASS_DRUM, EIGHTH); var s = perc(ACOUSTIC_SNARE, QUARTER);
        return Bar.of(BAR_SF, c, k, s, (PhraseNode) new RestNode(Duration.of(HALF)));
    }

    private static Bar silentBar() {
        return Bar.of(BAR_SF, (PhraseNode) new RestNode(Duration.ofSixtyFourths(BAR_SF)));
    }

    private static PercussionNote perc(PercussionSound sound, music.notation.duration.BaseValue dur) {
        return new PercussionNote(sound, Duration.of(dur));
    }

    // ── shared ──────────────────────────────────────────────────────

    private static void rest(BarBuilderTyped bar) { bar.r(WHOLE); }

    private MelodicPhrase silent(int bars) {
        var bb = b();
        for (int i = 0; i < bars; i++) bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new U2RockChunXiaoJingYeSi());
    }
}
