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
import music.notation.structure.DrumTrack;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
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

/**
 * 春晓 / 静夜思 — an 8-bar blues setting of two Tang 五言绝句.
 *
 * <p>Two bars per line, so one poem is exactly one 8-bar chorus, and
 * 起承轉合 lands on I / I7 / IV / I–V7. 春晓 is A major (morning,
 * guitar); 静夜思 is A minor (night, Rhodes). The pivot into the night
 * is written into the text — 春晓 line 3, 夜來風雨聲 — and realised as
 * the IV going minor under that line (form A′, bar 5). The tonic is
 * never sounded under 故鄉: every 静夜思 chorus ends on Em, not E7.</p>
 *
 * <p><b>Phase 1 — structure, harmony, drums.</b> The Voice track is a
 * silent placeholder; vocal lines, guitar/Rhodes solos and the choir
 * are later phases. 88 bars, ♩ = 94, straight eighths, no shuffle.</p>
 *
 * <pre>
 *  #  Section        Form   Bars   Notes
 *  1  Intro          A       1–8   Guitar alone; brushes enter bar 5.
 *  2  春晓 I          A      9–16   Bass in.
 *  3  Guitar solo    A     17–24   (solo blank)
 *  4  春晓 II         A′    25–32   THE DOOR: D→Dm at bar 5.
 *  5  静夜思 I        B     33–40   Rhodes in; guitar out.
 *  6  静夜思 II       B′    41–48   Backbeat begins. C / Cmaj7 under 舉頭.
 *  7  Rhodes solo    B×2   49–64   Ride cymbal.
 *  8  静夜思 III      B     65–72   Stripped: bass and drums out.
 *  9  春晓 III        A     73–80   Guitar back; bar 80 stop-time.
 * 10  Coda           coda  81–88   Guitar + Rhodes together; unresolved.
 * </pre>
 */
public final class BluesChunXiaoJingYeSi implements PieceContentProvider<ChunXiaoJingYeSi> {

    /** The coda's final four bars: a question (E7sus4) or an ache (Fmaj7). */
    public enum CodaEnding { E7SUS4, FMAJ7 }

    static final KeySignature KEY = new KeySignature(A, Mode.MAJOR);
    static final TimeSignature TS  = new TimeSignature(4, 4);
    static final int BPM = 94;
    private static final int BAR_SF = 64;

    private final CodaEnding codaEnding;

    public BluesChunXiaoJingYeSi() { this(CodaEnding.E7SUS4); }

    public BluesChunXiaoJingYeSi(CodaEnding codaEnding) { this.codaEnding = codaEnding; }

    @Override public String subtitle() {
        return "8-bar Blues in A / a" + (codaEnding == CodaEnding.FMAJ7 ? " (Fmaj7 coda)" : "");
    }

    // ════════════════════════════════════════════════════════════════
    //  Form
    // ════════════════════════════════════════════════════════════════

    enum Form { A, A_PRIME, B, B_PRIME, CODA }

    enum Sec {
        INTRO        ("Intro",         Form.A,        8),
        CHUNXIAO_1   ("春晓 I",         Form.A,        8),
        GUITAR_SOLO  ("Guitar solo",   Form.A,        8),
        CHUNXIAO_2   ("春晓 II — door", Form.A_PRIME,  8),
        JINGYESI_1   ("静夜思 I",       Form.B,        8),
        JINGYESI_2   ("静夜思 II — lift",Form.B_PRIME, 8),
        RHODES_SOLO  ("Rhodes solo",   Form.B,       16),
        JINGYESI_3   ("静夜思 III",     Form.B,        8),
        CHUNXIAO_3   ("春晓 III",       Form.A,        8),
        CODA         ("Coda",          Form.CODA,     8);

        final String label; final Form form; final int bars;
        Sec(String label, Form form, int bars) { this.label = label; this.form = form; this.bars = bars; }

        int start() {
            int s = 0;
            for (Sec x : values()) { if (x == this) break; s += x.bars; }
            return s;
        }
    }

    static final int TOTAL = 88;

    /** Absolute 0-based bar → its section. */
    static Sec sectionAt(int bar) {
        for (Sec s : Sec.values()) if (bar < s.start() + s.bars) return s;
        throw new IllegalArgumentException("bar out of range: " + bar);
    }

    /** 0-based bar within its section (0..7 within each 8-bar chorus, even inside the 16-bar solo). */
    static int chorusBar(int bar) {
        Sec s = sectionAt(bar);
        return (bar - s.start()) % 8;
    }

    // ════════════════════════════════════════════════════════════════
    //  Harmony
    // ════════════════════════════════════════════════════════════════

    enum Ch { A_MAJ, A7, D_MAJ, DM, E7, AM, AM7, FMAJ7, EM, C_MAJ, CMAJ7, E7SUS4 }

    private Ch[] chorus(Form f) {
        return switch (f) {
            case A       -> new Ch[]{ Ch.A_MAJ, Ch.A_MAJ, Ch.A7, Ch.A7, Ch.D_MAJ, Ch.D_MAJ, Ch.A_MAJ, Ch.E7 };
            case A_PRIME -> new Ch[]{ Ch.A_MAJ, Ch.A_MAJ, Ch.A7, Ch.A7, Ch.DM,    Ch.DM,    Ch.AM,    Ch.E7 };
            case B       -> new Ch[]{ Ch.AM, Ch.AM, Ch.AM7, Ch.AM7, Ch.DM,    Ch.DM,    Ch.FMAJ7, Ch.EM };
            case B_PRIME -> new Ch[]{ Ch.AM, Ch.AM, Ch.AM7, Ch.AM7, Ch.C_MAJ, Ch.CMAJ7, Ch.FMAJ7, Ch.EM };
            case CODA    -> {
                Ch tail = codaEnding == CodaEnding.FMAJ7 ? Ch.FMAJ7 : Ch.E7SUS4;
                yield new Ch[]{ Ch.A_MAJ, Ch.DM, Ch.FMAJ7, Ch.EM, tail, tail, tail, tail };
            }
        };
    }

    Ch chordAt(int bar) {
        return chorus(sectionAt(bar).form)[chorusBar(bar)];
    }

    /**
     * Close voicings around A3–B4, written against the A-major key
     * signature (so {@code C}, {@code F}, {@code G} default to sharps;
     * the minor-side chords spell their naturals explicitly).
     */
    private static Note[] voicing(Ch c) {
        return switch (c) {
            case A_MAJ  -> new Note[]{ A, C.higher(1), E.higher(1) };
            case A7     -> new Note[]{ A, C.higher(1), E.higher(1), G.n().higher(1) };
            case D_MAJ  -> new Note[]{ D.higher(1), F.higher(1), A.higher(1) };
            case DM     -> new Note[]{ D.higher(1), F.n().higher(1), A.higher(1) };
            case E7     -> new Note[]{ E, G, B, D.higher(1) };
            case AM     -> new Note[]{ A, C.n().higher(1), E.higher(1) };
            case AM7    -> new Note[]{ A, C.n().higher(1), E.higher(1), G.n().higher(1) };
            case FMAJ7  -> new Note[]{ F.n(), A, C.n().higher(1), E.higher(1) };
            case EM     -> new Note[]{ E, G.n(), B };
            case C_MAJ  -> new Note[]{ C.n().higher(1), E.higher(1), G.n().higher(1) };
            case CMAJ7  -> new Note[]{ C.n().higher(1), E.higher(1), G.n().higher(1), B.higher(1) };
            case E7SUS4 -> new Note[]{ E, A, B, D.higher(1) };
        };
    }

    /** Bass root, written for {@code o2(...)}. */
    private static Note root(Ch c) {
        return switch (c) {
            case A_MAJ, A7, AM, AM7 -> A;
            case D_MAJ, DM          -> D.higher(1);
            case E7, EM, E7SUS4     -> E;
            case FMAJ7              -> F.n();
            case C_MAJ, CMAJ7       -> C.n().higher(1);
        };
    }

    /** Bass fifth, written for {@code o2(...)}. */
    private static Note fifth(Ch c) {
        return switch (c) {
            case A_MAJ, A7, AM, AM7 -> E.higher(1);
            case D_MAJ, DM          -> A;
            case E7, EM, E7SUS4     -> B;
            case FMAJ7              -> C.n().higher(1);
            case C_MAJ, CMAJ7       -> G.n();
        };
    }

    // ════════════════════════════════════════════════════════════════
    //  Piece
    // ════════════════════════════════════════════════════════════════

    private StaffPhraseBuilderTyped b() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Override
    public Piece create() {
        var id = new ChunXiaoJingYeSi();

        var voice      = joinMelodicPhrases("Voice",       VOICE_OOHS,           voicePhrases());
        var guitarSolo = joinMelodicPhrases("Guitar Solo", ELECTRIC_GUITAR_JAZZ, guitarSoloPhrases());
        var rhodesSolo = joinMelodicPhrases("Rhodes Solo", ELECTRIC_PIANO_1,     rhodesSoloPhrases());
        var guitar     = joinMelodicPhrases("Guitar",      ELECTRIC_GUITAR_JAZZ, List.of(guitarPhrase()));
        var rhodes     = joinMelodicPhrases("Rhodes",      ELECTRIC_PIANO_1,     List.of(rhodesPhrase()));
        var bass       = joinMelodicPhrases("Bass",        ACOUSTIC_BASS,        List.of(bassPhrase()));
        var drums      = new DrumTrack("Drums", Phrase.of(drumBars()));

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS, new Tempo(BPM, QUARTER),
                List.of(voice, guitarSolo, rhodesSolo, guitar, rhodes, bass),
                List.of(drums));
    }

    // ════════════════════════════════════════════════════════════════
    //  Voice — the two poems. Two bars per line, 2+3 syllable split
    //  across the bar pair. Prosody drives the rhythm: 入聲 (不 覺 落
    //  月) are short and off the beat; 春晓's 上聲 rhymes (曉 鳥 少) are
    //  short and scooped from below with a grace note; 静夜思's 平聲
    //  rhymes (光 霜 鄉) sustain on the fifth, and 鄉 hangs on B over
    //  Em — never the tonic. Lyric attachment is a later infra step;
    //  syllables are noted inline.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> voicePhrases() {
        return List.of(
                silent(Sec.INTRO.bars),
                chunxiao(Third.MAJOR),                 // §2  春晓 I — morning intact
                silent(Sec.GUITAR_SOLO.bars),
                chunxiao(Third.MINOR),                 // §4  春晓 II — the door: F♮ under 夜來風雨聲
                jingyesi(Lift.NONE),                   // §5  静夜思 I
                jingyesi(Lift.MOON),                   // §6  静夜思 II — 舉頭 to G5
                silent(Sec.RHODES_SOLO.bars),
                jingyesiStripped(),                    // §8  静夜思 III — head stays down
                chunxiao(Third.MAJOR),                 // §9  春晓 III — lines 1–3, then stop-time
                coda());                               // §10 花落知多少
    }

    /** Which third the door line takes — the one note that changes between §2/§9 and §4. */
    private enum Third { MAJOR, MINOR }

    /** Whether 舉頭 lifts to the moon (§6, over C) or stays level (§5, over Dm). */
    private enum Lift { NONE, MOON }

    /**
     * 春晓 lines 1–3 over bars 1–6 of form A / A′; bars 7–8 are the
     * guitar's to answer. Line 4 is withheld until the coda.
     * <pre>
     *  1 (A)   春 眠 ·           E4 ♩ · C♯4 𝅗𝅥 · rest
     *  2 (A)   不 覺 曉          off-beat E4 F♯4 ♪ · grace G♯4 → A4 ♩ · rest   (曉 scooped, short)
     *  3 (A7)  處 處 ·           E4 ♩ · G♮4 ♩ · rest 𝅗𝅥
     *  4 (A7)  聞 啼 鳥          A4 ♩ · B4 ♩ · grace B4 → C♮5 ♩ · rest      (鳥 on the ♭3)
     *  5 (D/Dm) 夜 來 ·          D4 ♩ · F♯4 / F♮4 𝅗𝅥 · rest                    (the door)
     *  6 (D/Dm) 風 雨 聲         E4 ♩ · D4 ♪ rest ♪ · A4 𝅗𝅥
     *  7–8     —                rest (guitar answers)
     * </pre>
     */
    private MelodicPhrase chunxiao(Third third) {
        Note comeHome = third == Third.MAJOR ? F : F.n();     // 來 — the one note the door turns
        return b()
                .bar().o4(QUARTER, E).o4(HALF, C).r(QUARTER).done()                        // 春 眠
                .bar().r(EIGHTH).o4(EIGHTH, E).r(EIGHTH).o4(EIGHTH, F)                      // 不 覺
                      .grace(G, 4).main(QUARTER, 4, A).r(QUARTER).done()                    // 曉
                .bar().o4(QUARTER, E).o4(QUARTER, G.n()).r(HALF).done()                     // 處 處
                .bar().o4(QUARTER, A).o4(QUARTER, B)                                        // 聞 啼
                      .grace(B, 4).main(QUARTER, 5, C.n()).r(QUARTER).done()                // 鳥
                .bar().o4(QUARTER, D).o4(HALF, comeHome).r(QUARTER).done()                  // 夜 來
                .bar().o4(QUARTER, E).o4(EIGHTH, D).r(EIGHTH).o4(HALF, A).done()            // 風 雨 聲
                .bar().r(WHOLE).done()
                .bar().r(WHOLE).done()
                .build(attacca());
    }

    /**
     * 静夜思, all four lines over form B / B′. Lines 1–2 sit low
     * (A3–E4); line 3 lifts the octave — literally, as the head comes
     * up. In §6 the lift goes one step further, to G5 over Cmaj7.
     * <pre>
     *  1 (Am)    床 前 ·          A3 ♩ · B3 𝅗𝅥.
     *  2 (Am)    明 月 光         C4 ♩ · D4 ♪ rest ♪ · E4 𝅗𝅥                (光 sustained on 5)
     *  3 (Am7)   疑 是 ·          E4 ♩ · G4 ♪ rest ♪ · rest 𝅗𝅥
     *  4 (Am7)   地 上 霜         rest ♪ A4 ♪ G4 ♪ rest ♪ · E4 𝅗𝅥         (霜 sustained on 5)
     *  5 (Dm/C)  舉 頭 ·          A4 ♩ · D5 𝅗𝅥.   /  A4 ♩ · E5 𝅗𝅥.          (the lift)
     *  6 (Dm/Cmaj7) 望 明 月      E5 ♩ D5 ♩ C5 ♪ rest ♪ rest ♩  /  G5 ♩ E5 ♩ D5 ♪ rest ♪ rest ♩   (breath on 月)
     *  7 (Fmaj7) 低 頭 ·          C5 ♩ · A4 𝅗𝅥.                            (the head goes down)
     *  8 (Em)    思 故 鄉         G4 ♩ · A4 ♪ rest ♪ · B4 𝅗𝅥                (鄉 hangs on B)
     * </pre>
     */
    private MelodicPhrase jingyesi(Lift lift) {
        var bb = b();
        line1(bb); line2(bb);
        if (lift == Lift.MOON) {
            bb.bar().o4(QUARTER, A).o5(HALF.dot(), E).done()                                  // 舉 頭 → E5
              .bar().o5(QUARTER, G.n()).o5(QUARTER, E).o5(EIGHTH, D).r(EIGHTH).r(QUARTER).done(); // 望 明 月
        } else {
            bb.bar().o4(QUARTER, A).o5(HALF.dot(), D).done()                                  // 舉 頭
              .bar().o5(QUARTER, E).o5(QUARTER, D).o5(EIGHTH, C.n()).r(EIGHTH).r(QUARTER).done(); // 望 明 月
        }
        line4(bb);
        return bb.build(attacca());
    }

    /**
     * §8, stripped: the head never lifts again. Lines 1–2 as before,
     * then 低頭思故鄉 twice — over Dm/Dm and again over Fmaj7/Em — in
     * place of 舉頭望明月. Solo voice; bass and drums are out.
     */
    private MelodicPhrase jingyesiStripped() {
        var bb = b();
        line1(bb); line2(bb);
        line4(bb);          // over Dm Dm — 鄉 on B is the 6th, hanging
        line4(bb);          // over Fmaj7 Em — 鄉 on B, the fifth of Em
        return bb.build(attacca());
    }

    private static void line1(StaffPhraseBuilderTyped bb) {
        bb.bar().o3(QUARTER, A).o3(HALF.dot(), B).done()                                      // 床 前
          .bar().o4(QUARTER, C.n()).o4(EIGHTH, D).r(EIGHTH).o4(HALF, E).done();               // 明 月 光
    }

    private static void line2(StaffPhraseBuilderTyped bb) {
        bb.bar().o4(QUARTER, E).o4(EIGHTH, G.n()).r(EIGHTH).r(HALF).done()                    // 疑 是
          .bar().r(EIGHTH).o4(EIGHTH, A).o4(EIGHTH, G.n()).r(EIGHTH).o4(HALF, E).done();      // 地 上 霜
    }

    private static void line4(StaffPhraseBuilderTyped bb) {
        bb.bar().o5(QUARTER, C.n()).o4(HALF.dot(), A).done()                                  // 低 頭
          .bar().o4(QUARTER, G.n()).o4(EIGHTH, A).r(EIGHTH).o4(HALF, B).done();               // 思 故 鄉
    }

    /**
     * Coda: 花落知多少 at last, one phrase across A · Dm · Fmaj7 · Em,
     * then four bars of E7sus4 with the voice silent. 少 is scooped
     * from G♮ to A over Em — the fourth, not a resolution.
     * <pre>
     *  1 (A)     花 ·        C♯5 𝅗𝅥 · rest
     *  2 (Dm)    落 知       rest ♪ A4 ♪ · F♮4 ♩ · rest 𝅗𝅥     (落 入聲, off-beat)
     *  3 (Fmaj7) 多          E4 𝅗𝅥 · rest
     *  4 (Em)    少          grace G♮4 → A4 ♩ · rest 𝅗𝅥.
     *  5–8       —           rest
     * </pre>
     */
    private MelodicPhrase coda() {
        var bb = b()
                .bar().o5(HALF, C).r(HALF).done()                                             // 花
                .bar().r(EIGHTH).o4(EIGHTH, A).o4(QUARTER, F.n()).r(HALF).done()              // 落 知
                .bar().o4(HALF, E).r(HALF).done()                                             // 多
                .bar().grace(G.n(), 4).main(QUARTER, 4, A).r(HALF.dot()).done();              // 少
        for (int i = 0; i < 4; i++) bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Guitar Solo — §3, eight bars over form A. Major, articulate, not
    //  bluesy: sweep arpeggios through the chord tones, two octaves up
    //  and back, a chromatic walk-up into the IV, and a turnaround on
    //  E7 that hands the next chorus its C♯.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> guitarSoloPhrases() {
        return List.of(
                silent(Sec.GUITAR_SOLO.start()),
                guitarSolo(),
                silent(TOTAL - Sec.GUITAR_SOLO.start() - Sec.GUITAR_SOLO.bars));
    }

    private MelodicPhrase guitarSolo() {
        var Gn = G.n();
        var Fn = F.n();
        return b()
                // 1 (A): sweep up two octaves to C♯6 and fall back, land on the root.
                .bar(SIXTEENTH).o4(A).o5(C).o5(E).o5(A).o6(C).o5(A).o5(E).o5(C)
                               .o4(EIGHTH, A).o4(EIGHTH, E).o4(QUARTER, A).done()
                // 2 (A): pedal on E5 against falling chord tones, then a scale run up.
                .bar(SIXTEENTH).o5(E).o5(A).o5(E).o5(C).o5(E).o4(A).o5(E).o5(C)
                               .o4(A).o4(B).o5(C).o5(D).o5(E).o5(F).o5(G).o5(A).done()
                // 3 (A7): dominant arpeggio down through G♮, chromatic walk E–F–F♯–G, push to C♯.
                .bar(SIXTEENTH).o5(A).o5(Gn).o5(E).o5(C).o4(A).o4(Gn).o4(E).o4(C)
                               .o4(E).o4(Fn).o4(F).o4(Gn)
                               .o4(EIGHTH, A).o5(EIGHTH, C).done()
                // 4 (A7): full A7 sweep — up to C♯6, straight back down. Sixteen notes, no rest.
                .bar(SIXTEENTH).o4(C).o4(E).o4(Gn).o4(A).o5(C).o5(E).o5(Gn).o5(A)
                               .o6(C).o5(A).o5(Gn).o5(E).o5(C).o4(A).o4(Gn).o4(E).done()
                // 5 (D): D arpeggio to D6, turn, settle on D4.
                .bar(SIXTEENTH).o4(D).o4(F).o4(A).o5(D).o5(F).o5(A).o6(D).o5(A)
                               .o5(F).o5(D).o4(A).o4(F)
                               .o4(QUARTER, D).done()
                // 6 (D): 9th and 6th colour (E, B) around the chord, then run down to A3.
                .bar(SIXTEENTH).o4(A).o4(B).o5(D).o5(E).o5(F).o5(E).o5(D).o4(B)
                               .o4(A).o4(Gn).o4(F).o4(E).o4(D).o4(C).o3(B).o3(A).done()
                // 7 (A): climb two octaves and top out on E6.
                .bar(SIXTEENTH).o3(A).o4(C).o4(E).o4(A).o5(C).o5(E).o5(A).o6(C)
                               .o6(EIGHTH, E).o6(EIGHTH, C).o5(EIGHTH, A).o5(EIGHTH, E).done()
                // 8 (E7): turnaround — E7 arpeggio over the top, then B–D–G♯–B rising into the next A.
                .bar(SIXTEENTH).o5(E).o5(G).o5(B).o6(D).o5(B).o5(G).o5(E).o5(D)
                               .o4(EIGHTH, B).o5(EIGHTH, D).o5(EIGHTH, G).o5(EIGHTH, B).done()
                .build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Rhodes Solo — §7, sixteen bars over B×2. The centre of gravity;
    //  the night stretches. First chorus opens each bar with a cascade
    //  and lets it hang; second chorus fills every sixteenth, climbs
    //  to A6, and ends suspended on the fifth of Em.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> rhodesSoloPhrases() {
        return List.of(
                silent(Sec.RHODES_SOLO.start()),
                rhodesSolo(),
                silent(TOTAL - Sec.RHODES_SOLO.start() - Sec.RHODES_SOLO.bars));
    }

    private MelodicPhrase rhodesSolo() {
        var Cn = C.n();
        var Fn = F.n();
        var Gn = G.n();
        var Bf = B.f();
        return b()
                // ── Chorus 1 — spacious ──
                // 1 (Am): roll up two octaves, hold the E.
                .bar(SIXTEENTH).o3(A).o4(Cn).o4(E).o4(A).o5(Cn).o5(E).o5(A).o6(Cn)
                               .o5(HALF, E).done()
                // 2 (Am): fall from C6 through the 9th, settle.
                .bar(SIXTEENTH).o6(Cn).o5(B).o5(A).o5(E).o5(Cn).o4(A).o4(E).o4(Cn)
                               .o4(EIGHTH, A).o4(EIGHTH, B).o5(QUARTER, Cn).done()
                // 3 (Am7): pedal on G5.
                .bar(SIXTEENTH).o5(Gn).o5(E).o5(Gn).o5(Cn).o5(Gn).o4(A).o5(Gn).o5(E)
                               .o5(EIGHTH, A).o5(EIGHTH, Gn).o5(EIGHTH, E).o5(EIGHTH, Cn).done()
                // 4 (Am7): full sweep A3 → G6 and turn.
                .bar(SIXTEENTH).o3(A).o4(Cn).o4(E).o4(Gn).o4(A).o5(Cn).o5(E).o5(Gn)
                               .o5(A).o6(Cn).o6(E).o6(Gn).o6(E).o6(Cn).o5(A).o5(Gn).done()
                // 5 (Dm): Dm arpeggio to F6, come down.
                .bar(SIXTEENTH).o4(D).o4(Fn).o4(A).o5(D).o5(Fn).o5(A).o6(D).o6(Fn)
                               .o6(EIGHTH, D).o5(EIGHTH, A).o5(QUARTER, Fn).done()
                // 6 (Dm): B♭ colour, then run down to G3.
                .bar(SIXTEENTH).o5(A).o5(Bf).o5(A).o5(Fn).o5(E).o5(Fn).o5(D).o4(A)
                               .o4(Gn).o4(Fn).o4(E).o4(D).o4(Cn).o3(Bf).o3(A).o3(Gn).done()
                // 7 (Fmaj7): rise through the 7th.
                .bar(SIXTEENTH).o3(Fn).o3(A).o4(Cn).o4(E).o4(Fn).o4(A).o5(Cn).o5(E)
                               .o5(EIGHTH, Fn).o5(EIGHTH, E).o5(QUARTER, Cn).done()
                // 8 (Em): fall to a low held E — weight before the second chorus.
                .bar(SIXTEENTH).o5(B).o5(Gn).o5(E).o4(B).o4(Gn).o4(E).o3(B).o3(Gn)
                               .o3(HALF, E).done()

                // ── Chorus 2 — dense ──
                // 1 (Am): cascade up through the 9th to A6 and straight back.
                .bar(SIXTEENTH).o4(A).o5(Cn).o5(E).o5(A).o5(B).o6(Cn).o6(E).o6(A)
                               .o6(E).o6(Cn).o5(B).o5(A).o5(E).o5(Cn).o4(A).o4(E).done()
                // 2 (Am): broken-chord figure, opening out at the end.
                .bar(SIXTEENTH).o5(Cn).o4(A).o5(E).o4(A).o5(Cn).o4(A).o5(E).o4(A)
                               .o5(Cn).o4(A).o5(E).o4(A).o5(Cn).o5(E).o5(A).o6(Cn).done()
                // 3 (Am7): pedal G5 against falling tones, then a run to G6.
                .bar(SIXTEENTH).o5(Gn).o5(E).o5(Gn).o5(Cn).o5(Gn).o4(A).o5(Gn).o4(E)
                               .o4(A).o5(Cn).o5(E).o5(Gn).o5(A).o6(Cn).o6(E).o6(Gn).done()
                // 4 (Am7): descending sweep from A6 to C4, land on A3.
                .bar(SIXTEENTH).o6(A).o6(Gn).o6(E).o6(Cn).o5(A).o5(Gn).o5(E).o5(Cn)
                               .o4(A).o4(Gn).o4(E).o4(Cn)
                               .o3(QUARTER, A).done()
                // 5 (Dm): Dm with the 9th, up to A6 and back.
                .bar(SIXTEENTH).o4(D).o4(Fn).o4(A).o5(D).o5(E).o5(Fn).o5(A).o6(D)
                               .o6(E).o6(Fn).o6(A).o6(Fn).o6(E).o6(D).o5(A).o5(Fn).done()
                // 6 (Dm): B♭ again, falling, then climbing into the F.
                .bar(SIXTEENTH).o5(D).o5(Fn).o5(Bf).o5(A).o5(Fn).o5(D).o4(A).o4(Fn)
                               .o4(D).o4(Fn).o4(A).o5(D).o5(Fn).o5(A).o6(D).o6(Fn).done()
                // 7 (Fmaj7): down from E6, back up.
                .bar(SIXTEENTH).o6(E).o6(Cn).o5(A).o5(Fn).o5(E).o5(Cn).o4(A).o4(Fn)
                               .o4(E).o4(Fn).o4(A).o5(Cn).o5(E).o5(Fn).o5(A).o6(Cn).done()
                // 8 (Em): Em down, then E–G–B: the line stops on the fifth. No tonic.
                .bar(SIXTEENTH).o5(B).o5(Gn).o5(E).o4(B).o4(Gn).o4(E).o3(B).o3(Gn)
                               .o4(EIGHTH, E).o4(EIGHTH, Gn).o4(QUARTER, B).done()
                .build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Guitar — sparse comping in the major sections; tacet under the
    //  night until the coda
    // ════════════════════════════════════════════════════════════════

    private MelodicPhrase guitarPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = sectionAt(bar);
            int cb = chorusBar(bar);
            Ch ch = chordAt(bar);
            var bar_ = bb.bar();
            switch (s) {
                case INTRO, CHUNXIAO_1, CHUNXIAO_2 -> riff(bar_, ch);
                case GUITAR_SOLO -> rest(bar_);              // soloing — Rhodes takes the harmony
                case RHODES_SOLO -> pick(bar_, ch);          // Rhodes soloing — guitar takes the harmony
                case JINGYESI_1, JINGYESI_2, JINGYESI_3 -> rest(bar_);
                case CHUNXIAO_3 -> {
                    if (cb == 7) stopTime(bar_, ch);    // bar 80: hit and stop
                    else         riff(bar_, ch);
                }
                case CODA -> {
                    if (cb < 4) riff(bar_, ch);         // 花落知多少
                    else        held(bar_, ch);         // hold, do not resolve
                }
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    /**
     * The morning riff — root on 1, a stab on the upper dyad, root and
     * fifth walking under, stab again, and the top note to close.
     * Same shape on every chord, so it transposes with the changes:
     * <pre>  ♩ low | ♪ (mid hi) ♪ hi | ♪ low ♪ fifth | ♪ (mid hi) ♪ top</pre>
     */
    private static void riff(BarBuilderTyped bar, Ch ch) {
        Note[] v = voicing(ch);
        Note low = v[0], mid = v[1], hi = v[2];
        Note top = v.length > 3 ? v[3] : v[0].higher(1);
        Note fifth = hi.lower(1);
        bar.o3(QUARTER, low)
           .o3(EIGHTH, mid, hi).o3(EIGHTH, hi)
           .o3(EIGHTH, low).o3(EIGHTH, fifth)
           .o3(EIGHTH, mid, hi).o3(EIGHTH, top);
    }

    /**
     * The night riff — an open dyad on 1, the upper dyad answering with
     * the top note, the upper dyad held, then root and top to close.
     * Slower and darker than the morning riff; sits well under tremolo.
     * <pre>  ♩ (low hi) | ♪ (mid hi) ♪ top | ♩ (mid hi) | ♪ low ♪ top</pre>
     */
    private static void nightRiff(BarBuilderTyped bar, Ch ch) {
        Note[] v = voicing(ch);
        Note low = v[0], mid = v[1], hi = v[2];
        Note top = v.length > 3 ? v[3] : v[0].higher(1);
        bar.o3(QUARTER, low, hi)
           .o3(EIGHTH, mid, hi).o3(EIGHTH, top)
           .o3(QUARTER, mid, hi)
           .o3(EIGHTH, low).o3(EIGHTH, top);
    }

    /**
     * Fingerstyle broken chord in eighths — low, high, middle, top —
     * twice per bar. Half the solo's speed; keeps motion under it
     * without competing.
     */
    private static void pick(BarBuilderTyped bar, Ch ch) {
        Note[] v = voicing(ch);
        Note low = v[0], mid = v[1], hi = v[2];
        Note top = v.length > 3 ? v[3] : v[0].higher(1);
        bar.o3(EIGHTH, low).o3(EIGHTH, hi).o3(EIGHTH, mid).o3(EIGHTH, top)
           .o3(EIGHTH, low).o3(EIGHTH, hi).o3(EIGHTH, mid).o3(EIGHTH, top);
    }

    /** Rising roll in eighths — low, middle, high, top — twice per bar. */
    private static void roll(BarBuilderTyped bar, Ch ch) {
        Note[] v = voicing(ch);
        Note low = v[0], mid = v[1], hi = v[2];
        Note top = v.length > 3 ? v[3] : v[0].higher(1);
        bar.o3(EIGHTH, low).o3(EIGHTH, mid).o3(EIGHTH, hi).o3(EIGHTH, top)
           .o3(EIGHTH, low).o3(EIGHTH, mid).o3(EIGHTH, hi).o3(EIGHTH, top);
    }

    private static void held(BarBuilderTyped bar, Ch ch) {
        bar.o3(WHOLE, voicing(ch));
    }

    private static void stopTime(BarBuilderTyped bar, Ch ch) {
        bar.o3(QUARTER, voicing(ch)).r(HALF.dot());
    }

    private static void rest(BarBuilderTyped bar) {
        bar.r(WHOLE);
    }

    // ════════════════════════════════════════════════════════════════
    //  Rhodes — whole-note pads; enters with the night, absent from
    //  every major section until the coda
    // ════════════════════════════════════════════════════════════════

    private MelodicPhrase rhodesPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = sectionAt(bar);
            var bar_ = bb.bar();
            switch (s) {
                case JINGYESI_1, JINGYESI_2 -> nightRiff(bar_, chordAt(bar));
                case GUITAR_SOLO -> roll(bar_, chordAt(bar));   // guitar soloing — Rhodes takes the harmony
                case RHODES_SOLO -> rest(bar_);                 // soloing — guitar takes the harmony
                case JINGYESI_3, CODA -> held(bar_, chordAt(bar));   // stripped / held
                default -> rest(bar_);
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Bass — roots and fifths; out for the intro and for the stripped
    //  静夜思 III
    // ════════════════════════════════════════════════════════════════

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
                    if (cb == 7) bar_.o2(QUARTER, root(ch)).r(HALF.dot());   // stop-time
                    else         rootFifth(bar_, ch);
                }
                case CODA -> {
                    if (cb < 4) rootFifth(bar_, ch);
                    else        bar_.o2(WHOLE, root(ch));                     // hold, don't resolve
                }
                default -> rootFifth(bar_, ch);
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    /** Root on 1 (held to 3), fifth on 3, root on 4. */
    private static void rootFifth(BarBuilderTyped bar, Ch ch) {
        bar.o2(HALF, root(ch)).o2(QUARTER, fifth(ch)).o2(QUARTER, root(ch));
    }

    // ════════════════════════════════════════════════════════════════
    //  Drums — brushes throughout (approximated: closed hat, side-stick,
    //  ride). No backbeat until 静夜思 II. Straight eighths.
    // ════════════════════════════════════════════════════════════════

    private List<Bar> drumBars() {
        var bars = new ArrayList<Bar>(TOTAL);
        for (int bar = 0; bar < TOTAL; bar++) {
            Sec s = sectionAt(bar);
            int cb = chorusBar(bar);
            bars.add(switch (s) {
                case INTRO       -> cb < 4 ? silentBar() : hatTime();
                case CHUNXIAO_1, GUITAR_SOLO, CHUNXIAO_2, JINGYESI_1 -> hatTime();
                case JINGYESI_2  -> backbeatHat();
                case RHODES_SOLO -> backbeatRide();
                case JINGYESI_3  -> silentBar();
                case CHUNXIAO_3  -> cb == 7 ? stopTimeBar() : backbeatHat();
                case CODA        -> cb < 4 ? hatQuarters() : (cb == 4 ? rideOnOne() : silentBar());
            });
        }
        return bars;
    }

    /** Pre-backbeat time: kick on 1, closed hat on every other eighth. */
    private static Bar hatTime() {
        var k = perc(BASS_DRUM, EIGHTH);
        var h = perc(CLOSED_HI_HAT, EIGHTH);
        return Bar.of(BAR_SF, k, h, h, h, h, h, h, h);
    }

    /** Backbeat with side-stick (cross-stick) on 2 and 4. */
    private static Bar backbeatHat() {
        var k = perc(BASS_DRUM, EIGHTH);
        var h = perc(CLOSED_HI_HAT, EIGHTH);
        var s = perc(SIDE_STICK, EIGHTH);
        return Bar.of(BAR_SF, k, h, s, h, k, h, s, h);
    }

    /** Same backbeat on the ride for the Rhodes solo. */
    private static Bar backbeatRide() {
        var k = perc(BASS_DRUM, EIGHTH);
        var r = perc(RIDE_CYMBAL, EIGHTH);
        var s = perc(SIDE_STICK, EIGHTH);
        return Bar.of(BAR_SF, k, r, s, r, k, r, s, r);
    }

    /** Stop-time: kick + hat on 1, then nothing. */
    private static Bar stopTimeBar() {
        var k = perc(BASS_DRUM, QUARTER);
        return Bar.of(BAR_SF, k, (PhraseNode) new RestNode(HALF.dot()));
    }

    private static Bar hatQuarters() {
        var h = perc(CLOSED_HI_HAT, QUARTER);
        return Bar.of(BAR_SF, h, h, h, h);
    }

    private static Bar rideOnOne() {
        var r = perc(RIDE_CYMBAL, QUARTER);
        return Bar.of(BAR_SF, r, (PhraseNode) new RestNode(HALF.dot()));
    }

    private static Bar silentBar() {
        return Bar.of(BAR_SF, (PhraseNode) new RestNode(Duration.ofSixtyFourths(BAR_SF)));
    }

    private static PercussionNote perc(PercussionSound sound, music.notation.duration.BaseValue dur) {
        return new PercussionNote(sound, Duration.of(dur));
    }

    // ── shared ──────────────────────────────────────────────────────

    private MelodicPhrase silent(int bars) {
        var bb = b();
        for (int i = 0; i < bars; i++) bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new BluesChunXiaoJingYeSi());
    }
}
