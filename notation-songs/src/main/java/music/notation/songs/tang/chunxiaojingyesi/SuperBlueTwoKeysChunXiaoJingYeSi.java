package music.notation.songs.tang.chunxiaojingyesi;

import music.notation.duration.Duration;
import music.notation.phrase.AuthorPhrase;
import music.notation.phrase.BarBuilderTyped;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.Phrase;
import music.notation.phrase.StaffPhraseBuilderTyped;
import music.notation.pitch.Note;
import music.notation.play.PlayPiece;
import music.notation.phrase.Bar;
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
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;
import static music.notation.songs.tang.chunxiaojingyesi.BluesChunXiaoJingYeSi.*;

/**
 * 春晓 / 静夜思 — super blue in two keys: B♭ for the morning, E♭ for the night.
 *
 * <p>The {@link SuperBlueChunXiaoJingYeSi super-blue} arrangement with the
 * night moved up a fourth. 春晓 stays in the B♭ blues; 静夜思 is sung in the
 * E♭ minor blues — and the B♭ that is the day's tonic is the night's
 * dominant, so the door section's turnaround chord (B♭7) is the pivot
 * that opens onto E♭m.</p>
 *
 * <p>The form is shorter and song-shaped. 静夜思 is heard once as a verse;
 * its second and third hearings are a <b>chorus</b>: 舉頭望明月 · 低頭思故鄉
 * sung twice, high-energy and climbing, the second pass over the lift
 * chords (G♭ · G♭maj7), the choir shouting the last three syllables back
 * in the answer bars. 鄉 still hangs on the outside note — F, the fifth of
 * the B♭m the chorus refuses to resolve. Then the coda: only
 * 夜來風雨聲 · 花落知多少, soft and falling, back in B♭, on the F7sus4 tail
 * that never lands.</p>
 *
 * <p>Solo 1 (§3) is the B♭ showcase with its IV bars turned to the E♭
 * hexatonic; the Rhodes solo (§7) is the showcase moved into E♭m, with the
 * v bars (B♭m) turned back to the B♭ hexatonic.</p>
 */
public final class SuperBlueTwoKeysChunXiaoJingYeSi implements PieceContentProvider<ChunXiaoJingYeSi> {

    static final KeySignature KEY = SuperBlueChunXiaoJingYeSi.KEY;
    static final TimeSignature TS  = BluesChunXiaoJingYeSi.TS;
    static final int BPM = 92;

    @Override public String subtitle() { return "Super Blue in B♭ / E♭"; }

    private StaffPhraseBuilderTyped b() { return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER); }
    private StaffPhraseBuilderTyped vb(int sh) { return b().octaveShift(sh); }

    // ════════════════════════════════════════════════════════════════
    //  Form — nine sections, 80 bars
    // ════════════════════════════════════════════════════════════════

    enum Form { DAY, DOOR, NIGHT, CHORUS, CODA }

    enum Sec {
        INTRO        ("Intro",             Form.DAY,     8),
        CHUNXIAO_1   ("春晓 I",             Form.DAY,     8),
        SOLO_1       ("Solo 1",            Form.DAY,     8),
        CHUNXIAO_2   ("春晓 II — door",     Form.DOOR,    8),
        JINGYESI     ("静夜思 — verse",      Form.NIGHT,   8),
        CHORUS_1     ("舉頭 · 低頭 — chorus I", Form.CHORUS, 8),
        RHODES_SOLO  ("Rhodes solo",       Form.NIGHT,  16),
        CHORUS_2     ("舉頭 · 低頭 — chorus II", Form.CHORUS, 8),
        CODA         ("Coda",              Form.CODA,    8);

        final String label; final Form form; final int bars;
        Sec(String label, Form form, int bars) { this.label = label; this.form = form; this.bars = bars; }

        int start() {
            int s = 0;
            for (Sec x : values()) { if (x == this) break; s += x.bars; }
            return s;
        }
    }

    static final int TOTAL = 80;

    static Sec sectionAt(int bar) {
        for (Sec s : Sec.values()) if (bar < s.start() + s.bars) return s;
        throw new IllegalArgumentException("bar out of range: " + bar);
    }

    static int chorusBar(int bar) {
        Sec s = sectionAt(bar);
        return (bar - s.start()) % 8;
    }

    /** The caesura — beat 4 dropped in the last bar of every section but the coda. */
    static boolean breathAt(int bar) {
        Sec s = sectionAt(bar);
        return s != Sec.CODA && bar == s.start() + s.bars - 1;
    }

    // ════════════════════════════════════════════════════════════════
    //  Harmony — dominant sevenths for the day, the E♭ minor blues for
    //  the night, B♭ as the hinge between them
    // ════════════════════════════════════════════════════════════════

    enum Ch { BB7, EB7, F7, EBM, EBM7, BBM, BBM7, ABM7, GB, GBMAJ7, CBMAJ7, FM7, F7SUS4 }

    private static Ch[] chorus(Form f) {
        return switch (f) {
            case DAY    -> new Ch[]{ Ch.BB7, Ch.BB7, Ch.BB7, Ch.BB7, Ch.EB7,  Ch.EB7,    Ch.BB7,    Ch.F7  };
            case DOOR   -> new Ch[]{ Ch.BB7, Ch.BB7, Ch.BB7, Ch.BB7, Ch.EBM7, Ch.EBM7,   Ch.BBM7,   Ch.BB7 };   // B♭7 turns into V of E♭m
            case NIGHT  -> new Ch[]{ Ch.EBM, Ch.EBM, Ch.EBM7, Ch.EBM7, Ch.ABM7, Ch.ABM7, Ch.CBMAJ7, Ch.BBM };
            case CHORUS -> new Ch[]{ Ch.EBM, Ch.EBM, Ch.EBM7, Ch.EBM7, Ch.GB,   Ch.GBMAJ7, Ch.CBMAJ7, Ch.BBM };  // the lift under the second pass
            case CODA   -> new Ch[]{ Ch.BB7, Ch.EBM7, Ch.GBMAJ7, Ch.FM7, Ch.F7SUS4, Ch.F7SUS4, Ch.F7SUS4, Ch.F7SUS4 };
        };
    }

    static Ch chordAt(int bar) {
        return chorus(sectionAt(bar).form)[chorusBar(bar)];
    }

    /** Close voicings around A♭3–D♭5, written for {@code o3(...)} against the B♭-major signature. */
    static Note[] voicing(Ch c) {
        return switch (c) {
            case BB7    -> new Note[]{ B, D.higher(1), F.higher(1), A.f().higher(1) };
            case EB7    -> new Note[]{ E.higher(1), G.higher(1), B.higher(1), D.f().higher(2) };
            case F7     -> new Note[]{ F, A, C.higher(1), E.higher(1) };
            case EBM    -> new Note[]{ E.higher(1), G.f().higher(1), B.higher(1) };
            case EBM7   -> new Note[]{ E.higher(1), G.f().higher(1), B.higher(1), D.f().higher(2) };
            case BBM    -> new Note[]{ B, D.f().higher(1), F.higher(1) };
            case BBM7   -> new Note[]{ B, D.f().higher(1), F.higher(1), A.f().higher(1) };
            case ABM7   -> new Note[]{ A.f(), C.f().higher(1), E.higher(1), G.f().higher(1) };
            case GB     -> new Note[]{ G.f(), B, D.f().higher(1) };
            case GBMAJ7 -> new Note[]{ G.f(), B, D.f().higher(1), F.higher(1) };
            case CBMAJ7 -> new Note[]{ C.f().higher(1), E.higher(1), G.f().higher(1), B.higher(1) };
            case FM7    -> new Note[]{ F, A.f(), C.higher(1), E.higher(1) };
            case F7SUS4 -> new Note[]{ F, B, C.higher(1), E.higher(1) };
        };
    }

    /** Bass root, for {@code o2(...)}. */
    static Note root(Ch c) {
        return switch (c) {
            case BB7, BBM, BBM7   -> B;
            case EB7, EBM, EBM7   -> E.higher(1);
            case F7, FM7, F7SUS4  -> F;
            case ABM7             -> A.f();
            case GB, GBMAJ7       -> G.f();
            case CBMAJ7           -> C.f().higher(1);
        };
    }

    /** Bass fifth, for {@code o2(...)}. */
    static Note fifth(Ch c) {
        return switch (c) {
            case BB7, BBM, BBM7   -> F.higher(1);
            case EB7, EBM, EBM7   -> B;
            case F7, FM7, F7SUS4  -> C.higher(1);
            case ABM7             -> E.higher(1);
            case GB, GBMAJ7       -> D.f().higher(1);
            case CBMAJ7           -> G.f();
        };
    }

    // ════════════════════════════════════════════════════════════════
    //  Two blue hexatonics
    // ════════════════════════════════════════════════════════════════

    /** Degrees of a blue hexatonic, plus the one outside note (the 2nd). */
    enum Blue { R, F3, P4, F5, P5, F7, OUT }

    /**
     * A blue hexatonic spelled against the B♭-major signature, with the
     * octave step each degree takes above the root's octave.
     * <pre>
     *   B♭:  B♭ · D♭ · E♭ · E♮ · F  · A♭   (+ C)
     *   E♭:  E♭ · G♭ · A♭ · A♮ · B♭ · D♭   (+ F)
     * </pre>
     */
    enum Hex {
        BB(new Note[]{ B, D.f(), E, E.n(), F, A.f(), C }, new int[]{ 0, 1, 1, 1, 1, 1, 1 }),
        EB(new Note[]{ E, G.f(), A.f(), A, B, D.f(), F }, new int[]{ 0, 0, 0, 0, 0, 1, 1 });

        final Note[] notes; final int[] up;
        Hex(Note[] notes, int[] up) { this.notes = notes; this.up = up; }
    }

    /** Emit one degree of {@code k} at the octave whose root is octave {@code root}. */
    private static BarBuilderTyped n(BarBuilderTyped bar, Duration d, Hex k, int root, Blue deg) {
        int oct = root + k.up[deg.ordinal()];
        Note note = k.notes[deg.ordinal()];
        return switch (oct) {
            case 2 -> bar.o2(d, note);
            case 3 -> bar.o3(d, note);
            case 4 -> bar.o4(d, note);
            case 5 -> bar.o5(d, note);
            case 6 -> bar.o6(d, note);
            default -> throw new IllegalArgumentException("octave " + oct);
        };
    }

    // ════════════════════════════════════════════════════════════════
    //  Piece
    // ════════════════════════════════════════════════════════════════

    @Override
    public Piece create() {
        var id = new ChunXiaoJingYeSi();

        var female     = joinMelodicPhrases("Voice (F)",   VOICE_OOHS,           voicePhrases(0));
        var male       = joinMelodicPhrases("Voice (M)",   SYNTH_VOICE,          voicePhrases(-1));
        var choir      = joinMelodicPhrases("Choir",       CHOIR_AAHS,           choirPhrases());
        var solo1Track = joinMelodicPhrases("Solo 1",      OVERDRIVEN_GUITAR,    solo1Phrases());
        var rhodesSolo = joinMelodicPhrases("Rhodes Solo", ELECTRIC_PIANO_1,     rhodesSoloPhrases());
        var guitar     = joinMelodicPhrases("Guitar",      ELECTRIC_GUITAR_JAZZ, List.of(guitarPhrase()));
        var rhodesRH   = joinMelodicPhrases("Rhodes RH",   ELECTRIC_PIANO_1,     List.of(rhodesPhrase()));
        var rhodesLH   = joinMelodicPhrases("Rhodes LH",   ELECTRIC_PIANO_1,     List.of(rhodesLeftHand()));
        var bass       = joinMelodicPhrases("Bass",        ACOUSTIC_BASS,        List.of(bassPhrase()));
        var drums      = new DrumTrack("Drums", Phrase.of(drumBars()));

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS, new Tempo(BPM, QUARTER),
                List.of(female, male, choir, solo1Track, rhodesSolo, guitar, rhodesRH, rhodesLH, bass),
                List.of(drums));
    }

    // ════════════════════════════════════════════════════════════════
    //  Voice — 春晓 as in the super-blue (B♭, root B♭3); 静夜思 in E♭
    //  (root E♭4): one verse, then the chorus twice; the coda falls.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> voicePhrases(int sh) {
        return List.of(
                silent(Sec.INTRO.bars),
                SuperBlueChunXiaoJingYeSi.chunxiao(vb(sh)),
                silent(Sec.SOLO_1.bars),
                SuperBlueChunXiaoJingYeSi.chunxiao(vb(sh)),   // §4 — the door: E♭m7 under the same D♭
                verse(sh),
                chorus(sh),
                silent(Sec.RHODES_SOLO.bars),
                chorus(sh),
                coda(sh));
    }

    /**
     * 静夜思 verse — the super-blue lines a fourth up, in the E♭ hexatonic.
     * <pre>
     *  1  床 前 明月光     1 ♩ · ♭3 ♩. · 4 ♭5 5 ♪♪♪ ~        E♭4 G♭4 · A♭4 A4 B♭4 — 光 held on 5
     *  3  疑 是 地上霜     5 ♩ · ♭7 ♩. · 5 4 5 ♪♪♪ ~
     *  5  舉 頭 望明月     1′ ♩ · ♭7 ♩. · 5 ♭7 1′ ♪♪♪        looking up from the top — E♭5
     *  7  低 頭 思故鄉     5 ♩ · 4 ♩. · ♭3 1 F ♪♪♪ ~         鄉 on F, the outside note
     * </pre>
     */
    private MelodicPhrase verse(int sh) {
        var bb = vb(sh);
        line(bb, new Blue[]{ Blue.R,  Blue.F3, Blue.P4, Blue.F5, Blue.P5 },  new int[]{ 4, 4, 4, 4, 4 });   // 床前明月光
        bb.bar().o4(HALF, B).r(HALF).done();
        line(bb, new Blue[]{ Blue.P5, Blue.F7, Blue.P5, Blue.P4, Blue.P5 },  new int[]{ 4, 4, 4, 4, 4 });   // 疑是地上霜
        bb.bar().o4(HALF, B).r(HALF).done();
        line(bb, new Blue[]{ Blue.R,  Blue.F7, Blue.P5, Blue.F7, Blue.R },   new int[]{ 5, 4, 4, 4, 5 });   // 舉頭望明月
        bb.bar().o5(QUARTER, E).r(HALF.dot()).done();                                                       // 月 carries a beat
        xiangLineLow(bb);
        bb.bar().o4(QUARTER.dot(), F).r(EIGHTH).r(HALF).done();
        return bb.build(attacca());
    }

    /** 低頭思故鄉 as the verse sings it — falling, 鄉 on F4. */
    private static void xiangLineLow(StaffPhraseBuilderTyped bb) {
        line(bb, new Blue[]{ Blue.P5, Blue.P4, Blue.F3, Blue.R, Blue.OUT }, new int[]{ 4, 4, 4, 4, 3 });
    }

    /**
     * The chorus — 舉頭望明月 · 低頭思故鄉 twice, every line climbing, the
     * second pass a third higher over the lift. The choir echoes the
     * last three syllables in each answer bar (see {@link #choirEcho}).
     * <pre>
     *  1  舉 頭 望明月     1 ♩ · ♭3 ♩. · 4 5 ♭7 ♪♪♪ ~        E♭4 G♭4 · A♭4 B♭4 D♭5
     *  2  ♭7 ♩ · rest                                          (choir: 望明月)
     *  3  低 頭 思故鄉     ♭3 ♩ · 4 ♩. · 5 ♭7 F′ ♪♪♪ ~       G♭4 A♭4 · B♭4 D♭5 F5 — 鄉 outside, above
     *  4  F′ 𝅗𝅥 · rest                                          (choir: 思故鄉)
     *  5  舉 頭 望明月     ♭3 ♩ · 5 ♩. · ♭7 1′ ♭3′ ♪♪♪ ~     G♭4 B♭4 · D♭5 E♭5 G♭5 — the peak
     *  6  ♭3′ ♩ · rest                                         (choir: 望明月)
     *  7  低 頭 思故鄉     5 ♩ · ♭7 ♩. · 1′ ♭3′ F′ ♪♪♪ ~     B♭4 D♭5 · E♭5 G♭5 F5
     *  8  F′ 𝅗𝅥. · rest  (caesura)                              (choir: 思故鄉)
     * </pre>
     */
    private MelodicPhrase chorus(int sh) {
        var bb = vb(sh);
        // pass 1 — E♭m
        line(bb, new Blue[]{ Blue.R,  Blue.F3, Blue.P4, Blue.P5, Blue.F7 },  new int[]{ 4, 4, 4, 4, 4 });
        bb.bar().o5(QUARTER, D.f()).r(HALF.dot()).done();
        line(bb, new Blue[]{ Blue.F3, Blue.P4, Blue.P5, Blue.F7, Blue.OUT }, new int[]{ 4, 4, 4, 4, 4 });
        bb.bar().o5(HALF, F).r(HALF).done();
        // pass 2 — over the lift, a third higher
        line(bb, new Blue[]{ Blue.F3, Blue.P5, Blue.F7, Blue.R,  Blue.F3 },  new int[]{ 4, 4, 4, 5, 5 });
        bb.bar().o5(QUARTER, G.f()).r(HALF.dot()).done();
        line(bb, new Blue[]{ Blue.P5, Blue.F7, Blue.R,  Blue.F3, Blue.OUT }, new int[]{ 4, 4, 5, 5, 4 });
        bb.bar().o5(HALF.dot(), F).r(QUARTER).done();
        return bb.build(attacca());
    }

    /** One 1 · 1½ · 1½ line in the E♭ hexatonic — five degrees at their root octaves; the last note tied over. */
    private static void line(StaffPhraseBuilderTyped bb, Blue[] d, int[] o) {
        var bar = bb.bar();
        n(bar, QUARTER, Hex.EB, o[0], d[0]); n(bar, QUARTER.dot(), Hex.EB, o[1], d[1]);
        n(bar, EIGHTH, Hex.EB, o[2], d[2]);  n(bar, EIGHTH, Hex.EB, o[3], d[3]);
        n(bar, EIGHTH, Hex.EB, o[4], d[4]).tieNext().done();
    }

    /**
     * Coda — 夜來風雨聲 · 花落知多少 only, soft and falling, back in B♭.
     * 聲, which rose to the octave in the verses, sinks to the root here;
     * 少 settles on B♭ — the suspended fourth of F7sus4 — and holds to
     * the end. The choir joins in unison for the second line.
     * <pre>
     *  1  夜 ·             A♭4 𝅗𝅥. · rest                  B♭7
     *  2  來 風雨           F4 𝅗𝅥 · E♭4 ♩ D♭4 ♩             E♭m7
     *  3  聲 ·             B♭3 𝅗𝅥. · rest                  G♭maj7
     *  4  ·                rest                            Fm7
     *  5  花 落            E♭4 𝅗𝅥. · D♭4 ♩                  F7sus4
     *  6  知 多 少          C4 ♩ · D♭4 ♩ · B♭3 𝅗𝅥 ~
     *  7–8 少              B♭3 𝅝 ~ 𝅝
     * </pre>
     */
    private MelodicPhrase coda(int sh) {
        var bb = vb(sh);
        codaLine1(bb);
        bb.bar().r(WHOLE).done();
        codaLine2(bb);
        return bb.build(attacca());
    }

    private static void codaLine1(StaffPhraseBuilderTyped bb) {
        var c1 = bb.bar(); n(c1, HALF.dot(), Hex.BB, 3, Blue.F7); c1.r(QUARTER).done();                       // 夜
        var c2 = bb.bar(); n(c2, HALF, Hex.BB, 3, Blue.P5); n(c2, QUARTER, Hex.BB, 3, Blue.P4); n(c2, QUARTER, Hex.BB, 3, Blue.F3).done(); // 來 風雨
        var c3 = bb.bar(); n(c3, HALF.dot(), Hex.BB, 3, Blue.R); c3.r(QUARTER).done();                        // 聲
    }

    private static void codaLine2(StaffPhraseBuilderTyped bb) {
        var c5 = bb.bar(); n(c5, HALF.dot(), Hex.BB, 3, Blue.P4); n(c5, QUARTER, Hex.BB, 3, Blue.F3).done();  // 花 落
        var c6 = bb.bar(); n(c6, QUARTER, Hex.BB, 3, Blue.OUT); n(c6, QUARTER, Hex.BB, 3, Blue.F3);           // 知 多
        n(c6, HALF, Hex.BB, 3, Blue.R).tieNext().done();                                                       // 少
        var c7 = bb.bar(); n(c7, WHOLE, Hex.BB, 3, Blue.R).tieNext().done();
        var c8 = bb.bar(); n(c8, WHOLE, Hex.BB, 3, Blue.R).done();
    }

    // ════════════════════════════════════════════════════════════════
    //  Choir — the hook line in the verse, echoes in the choruses,
    //  pads under the coda's first line and the words for its second.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> choirPhrases() {
        return List.of(
                silent(Sec.JINGYESI.start()),
                choirVerse(),
                choirEcho(false),
                silent(Sec.RHODES_SOLO.bars),
                choirEcho(true),
                choirCoda());
    }

    /** Verse: silent until 低頭思故鄉, sung in unison — the chorus's hook, foreshadowed. */
    private MelodicPhrase choirVerse() {
        var bb = b();
        for (int i = 0; i < 6; i++) bb.bar().r(WHOLE).done();
        xiangLineLow(bb);
        bb.bar().o4(QUARTER.dot(), F).r(EIGHTH).r(HALF).done();
        return bb.build(attacca());
    }

    /**
     * Chorus: silent in the call bars; in each answer bar the last three
     * syllables shouted back on beats 2–3 (♪ ♪ ♩), at the voice's pitches
     * — doubled an octave below in the second chorus.
     */
    private MelodicPhrase choirEcho(boolean octaves) {
        var bb = b();
        echoBar(bb, octaves, new Note[]{ A.f(), B, D.f().higher(1) }, 4);          // 望明月  A♭4 B♭4 D♭5
        echoBar(bb, octaves, new Note[]{ B, D.f().higher(1), F.higher(1) }, 4);    // 思故鄉  B♭4 D♭5 F5
        echoBar(bb, octaves, new Note[]{ D.f(), E, G.f() }, 5);                      // 望明月  D♭5 E♭5 G♭5
        echoBar(bb, octaves, new Note[]{ E, G.f(), F }, 5);                          // 思故鄉  E♭5 G♭5 F5
        return bb.build(attacca());
    }

    private static void echoBar(StaffPhraseBuilderTyped bb, boolean octaves, Note[] p, int oct) {
        bb.bar().r(WHOLE).done();
        var bar = bb.bar().r(QUARTER);
        for (int i = 0; i < 3; i++) {
            Duration d = i < 2 ? EIGHTH : QUARTER;
            Note x = p[i];
            if (oct == 4) { if (octaves) bar.o4(d, x, x.lower(1)); else bar.o4(d, x); }
            else          { if (octaves) bar.o5(d, x, x.lower(1)); else bar.o5(d, x); }
        }
        bar.r(QUARTER).done();
    }

    /** Coda: whole-note pads under 夜來風雨聲, a bar's rest, then 花落知多少 in unison with the voice. */
    private MelodicPhrase choirCoda() {
        var bb = b();
        int s = Sec.CODA.start();
        for (int i = 0; i < 3; i++) { var bar_ = bb.bar(); held(bar_, voicing(chordAt(s + i))); bar_.done(); }
        bb.bar().r(WHOLE).done();
        codaLine2(bb);
        return bb.build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Solo 1 — §3 over B♭7 ×4 · E♭7 ×2 · B♭7 · F7. The super-blue's
    //  sweep showcase, with the two IV bars re-heard in the E♭
    //  hexatonic: E♭'s blue third (G♭) against E♭7's G, E♭'s ♭5 (A♮),
    //  the D♭ shared by both keys. Written B♭3–B♭5 for guitar or trumpet.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> solo1Phrases() {
        return List.of(
                silent(Sec.SOLO_1.start()),
                solo1(),
                silent(TOTAL - Sec.SOLO_1.start() - Sec.SOLO_1.bars));
    }

    private MelodicPhrase solo1() {
        Note Db = D.f(), Gb = G.f(), Ab = A.f(), En = E.n();
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
                // 4 (B♭7): full sweep to B♭5 and straight back with ♭5 and ♭3 on the way down.
                .bar(SIXTEENTH).o4(D).o4(F).o4(Ab).o4(B).o5(D).o5(F).o5(Ab).o5(B)
                               .o5(Ab).o5(F).o5(En).o5(E).o5(Db).o5(D).o4(B).o4(Ab).done()
                // 5 (E♭7) — in E♭: climb the E♭ hexatonic E♭4 → G♭5, turn on E♭ · D♭, settle on B♭4.
                .bar(SIXTEENTH).o4(E).o4(Gb).o4(Ab).o4(A).o4(B).o5(Db).o5(E).o5(Gb)
                               .o5(EIGHTH, E).o5(EIGHTH, Db).o4(QUARTER, B).done()
                // 6 (E♭7) — in E♭: pedal B♭5 worrying G♭ against G, then the hexatonic down to E♭4.
                .bar(SIXTEENTH).o5(B).o5(Gb).o5(B).o5(G).o5(B).o5(Gb).o5(B).o5(G)
                               .o5(Ab).o5(Gb).o5(E).o5(Db).o4(B).o4(A).o4(Gb).o4(E).done()
                // 7 (B♭7): climb two octaves, top out on B♭5, close on the blue third.
                .bar(SIXTEENTH).o3(B).o4(D).o4(F).o4(Ab).o4(B).o5(D).o5(F).o5(Ab)
                               .o5(EIGHTH, B).o5(EIGHTH, Ab).o5(EIGHTH, F).o5(EIGHTH, Db).done()
                // 8 (F7): turnaround — F7 arpeggio over the top, A♭ (the ♯9) as the last colour, breathe.
                .bar(SIXTEENTH).o4(F).o4(A).o5(C).o5(E).o5(F).o5(E).o5(C).o4(A)
                               .o4(EIGHTH, F).o4(EIGHTH, Ab).r(QUARTER).done()
                .build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Rhodes Solo — §7 over the E♭ minor blues ×2: the super-blue's
    //  shape a fourth up (dropped an octave where it would leave the
    //  keyboard), spacious then dense. The v bars (B♭m, bars 8 and 16)
    //  turn to the B♭ hexatonic — D♭ and E♮ against the minor chord —
    //  and the last sound is F, the night's outside note.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> rhodesSoloPhrases() {
        return List.of(
                silent(Sec.RHODES_SOLO.start()),
                rhodesSolo(),
                silent(TOTAL - Sec.RHODES_SOLO.start() - Sec.RHODES_SOLO.bars));
    }

    private MelodicPhrase rhodesSolo() {
        Note Db = D.f(), Gb = G.f(), Ab = A.f(), Cb = C.f(), En = E.n();
        return b()
                // ── Chorus 1 — spacious ──
                // 1 (E♭m): roll up two octaves, hold the fifth.
                .bar(SIXTEENTH).o4(E).o4(Gb).o4(B).o5(Db).o5(E).o5(Gb).o5(B).o6(Db)
                               .o5(HALF, B).done()
                // 2 (E♭m): fall through the ♭5 (A♮), settle on the blue third.
                .bar(SIXTEENTH).o6(Db).o5(B).o5(A).o5(Ab).o5(Gb).o5(E).o5(Db).o4(B)
                               .o5(EIGHTH, Ab).o5(EIGHTH, B).o4(QUARTER, Gb).done()
                // 3 (E♭m7): pedal on D♭6.
                .bar(SIXTEENTH).o6(Db).o5(B).o6(Db).o5(Gb).o6(Db).o5(E).o6(Db).o5(B)
                               .o6(EIGHTH, E).o6(EIGHTH, Db).o5(EIGHTH, B).o5(EIGHTH, Gb).done()
                // 4 (E♭m7): full sweep E♭3 → D♭6, turn through the ♭5.
                .bar(SIXTEENTH).o3(E).o3(Gb).o3(B).o4(Db).o4(E).o4(Gb).o4(B).o5(Db)
                               .o5(E).o5(Gb).o5(B).o6(Db).o5(B).o5(A).o5(Gb).o5(E).done()
                // 5 (A♭m7): arpeggio to G♭6, come down.
                .bar(SIXTEENTH).o4(Ab).o5(Cb).o5(E).o5(Gb).o5(Ab).o6(Cb).o6(E).o6(Gb)
                               .o6(EIGHTH, Ab).o6(EIGHTH, Gb).o6(QUARTER, E).done()
                // 6 (A♭m7): colour, then run down to A♭3.
                .bar(SIXTEENTH).o6(E).o6(Db).o6(Cb).o5(Ab).o5(Gb).o5(Ab).o5(E).o5(Db)
                               .o5(Cb).o4(Ab).o4(Gb).o4(E).o4(Db).o4(Cb).o3(B).o3(Ab).done()
                // 7 (C♭maj7): rise through the major seventh.
                .bar(SIXTEENTH).o4(Cb).o4(E).o4(Gb).o4(B).o5(Cb).o5(E).o5(Gb).o5(B)
                               .o6(EIGHTH, Cb).o5(EIGHTH, B).o5(QUARTER, Gb).done()
                // 8 (B♭m) — in B♭: down the B♭ hexatonic, D♭ and E♮ against the chord, to a low held B♭.
                .bar(SIXTEENTH).o5(F).o5(E).o5(Db).o4(B).o4(Ab).o4(F).o4(En).o4(E)
                               .o3(HALF, B).done()

                // ── Chorus 2 — dense ──
                // 1 (E♭m): cascade through the 9th to D♭6 and back with the ♭5.
                .bar(SIXTEENTH).o4(E).o4(Gb).o4(B).o5(Db).o5(F).o5(Gb).o5(B).o6(Db)
                               .o5(B).o5(A).o5(Gb).o5(F).o5(Db).o4(B).o4(Gb).o4(E).done()
                // 2 (E♭m): broken-chord figure, opening out.
                .bar(SIXTEENTH).o5(Gb).o5(E).o5(B).o5(E).o5(Gb).o5(E).o5(B).o5(E)
                               .o5(Gb).o5(E).o5(B).o5(E).o5(Gb).o5(B).o6(Db).o6(Gb).done()
                // 3 (E♭m7): pedal D♭5 against falling tones, then a run to D♭6.
                .bar(SIXTEENTH).o5(Db).o4(B).o5(Db).o4(Gb).o5(Db).o4(E).o5(Db).o3(B)
                               .o4(E).o4(Gb).o4(B).o5(Db).o5(E).o5(Gb).o5(B).o6(Db).done()
                // 4 (E♭m7): descending sweep from E♭6 through the ♭5, land on E♭3.
                .bar(SIXTEENTH).o6(E).o6(Db).o5(B).o5(A).o5(Gb).o5(E).o5(Db).o4(B)
                               .o4(A).o4(Gb).o4(E).o4(Db)
                               .o3(QUARTER, E).done()
                // 5 (A♭m7): A♭m with the 9th, up to B♭6 and back.
                .bar(SIXTEENTH).o4(Ab).o5(Cb).o5(E).o5(Gb).o5(Ab).o5(B).o6(Cb).o6(E)
                               .o6(Gb).o6(Ab).o6(B).o6(Ab).o6(Gb).o6(E).o6(Cb).o5(Ab).done()
                // 6 (A♭m7): falling, then climbing into the C♭.
                .bar(SIXTEENTH).o4(Gb).o4(Ab).o5(Cb).o5(E).o5(Db).o5(Cb).o4(Ab).o4(Gb)
                               .o4(E).o4(Gb).o4(Ab).o5(Cb).o5(E).o5(Gb).o5(Ab).o6(Cb).done()
                // 7 (C♭maj7): down from B♭6, back up.
                .bar(SIXTEENTH).o6(B).o6(Gb).o6(E).o6(Cb).o5(B).o5(Gb).o5(E).o5(Cb)
                               .o4(B).o5(Cb).o5(E).o5(Gb).o5(B).o6(Cb).o6(E).o6(Gb).done()
                // 8 (B♭m) — in B♭: down the hexatonic, then B♭ · D♭ · F — the night's outside note last — and breathe.
                .bar(SIXTEENTH).o5(F).o5(E).o5(Db).o4(B).o4(Ab).o4(F).o4(En).o4(E)
                               .o4(Db).o3(B).o4(EIGHTH, F).r(QUARTER).done()
                .build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Band — the base textures; the choruses get the morning riff and
    //  the backbeat, the coda goes to fingerstyle
    // ════════════════════════════════════════════════════════════════

    private MelodicPhrase guitarPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Note[] v = voicing(chordAt(bar));
            var bar_ = bb.bar();
            switch (sectionAt(bar)) {
                case INTRO, CHUNXIAO_1, CHUNXIAO_2, CHORUS_2 -> riff(bar_, v, breathAt(bar));
                case CHORUS_1, RHODES_SOLO, CODA           -> pick(bar_, v, breathAt(bar));
                case SOLO_1, JINGYESI                       -> rest(bar_);
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    private MelodicPhrase rhodesPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Note[] v = voicing(chordAt(bar));
            var bar_ = bb.bar();
            switch (sectionAt(bar)) {
                case JINGYESI, CHORUS_1, CHORUS_2 -> nightRiff(bar_, v, breathAt(bar));
                case SOLO_1                       -> roll(bar_, v, breathAt(bar));
                case CODA                         -> sparse(bar_, v, breathAt(bar));
                default                           -> rest(bar_);
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    private MelodicPhrase rhodesLeftHand() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Ch ch = chordAt(bar);
            var bar_ = bb.bar();
            switch (sectionAt(bar)) {
                case SOLO_1, JINGYESI, CHORUS_1, RHODES_SOLO, CHORUS_2, CODA
                        -> leftHand(bar_, root(ch), fifth(ch), voicing(ch), breathAt(bar));
                default -> rest(bar_);
            }
            bar_.done();
        }
        return bb.build(attacca());
    }

    private MelodicPhrase bassPhrase() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            Ch ch = chordAt(bar);
            var bar_ = bb.bar();
            if (sectionAt(bar) == Sec.INTRO) rest(bar_);
            else rootFifth(bar_, root(ch), fifth(ch), breathAt(bar));
            bar_.done();
        }
        return bb.build(attacca());
    }

    /** Hat time for the day and the verse; backbeat from the first chorus, on the ride from the solo on; the coda thins out. */
    private static List<Bar> drumBars() {
        var bars = new ArrayList<Bar>(TOTAL);
        for (int bar = 0; bar < TOTAL; bar++) {
            int cb = chorusBar(bar);
            boolean br = breathAt(bar);
            bars.add(switch (sectionAt(bar)) {
                case INTRO                                   -> cb < 4 ? silentBar() : hatTime(br);
                case CHUNXIAO_1, SOLO_1, CHUNXIAO_2, JINGYESI -> hatTime(br);
                case CHORUS_1                                -> backbeatHat(br);
                case RHODES_SOLO, CHORUS_2                   -> backbeatRide(br);
                case CODA                                    -> cb < 4 ? hatQuarters() : (cb == 4 ? rideOnOne() : silentBar());
            });
        }
        return bars;
    }

    // ── shared ──────────────────────────────────────────────────────

    private MelodicPhrase silent(int bars) {
        var bb = b();
        for (int i = 0; i < bars; i++) bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new SuperBlueTwoKeysChunXiaoJingYeSi());
    }
}
