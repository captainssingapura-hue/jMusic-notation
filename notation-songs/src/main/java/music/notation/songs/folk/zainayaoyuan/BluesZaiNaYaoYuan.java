package music.notation.songs.folk.zainayaoyuan;

import music.notation.duration.Duration;
import music.notation.event.PercussionSound;
import music.notation.phrase.AuthorPhrase;
import music.notation.phrase.Bar;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.StaffPhraseBuilderTyped;
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
 * 在那遥远的地方 — slow electric Blues arrangement in C.
 *
 * <p>Twelve-bar blues form (C7-F7-C7-C7 / F7-F7-C7-C7 / G7-F7-C7-G7),
 * 65 BPM, with the original Wang Luobin melody fitted into the first
 * 8 bars of each verse and a 4-bar instrumental tag closing the form.
 * A full 12-bar lead-guitar solo using the C blues scale sits between
 * verses 2 and 3.</p>
 *
 * <p>Structure (~3:30): Intro 4 · Verse 1 (12) · Verse 2 (12) ·
 * Solo (12) · Verse 3 (12) · Outro 4 = 56 bars.</p>
 */
public final class BluesZaiNaYaoYuan implements PieceContentProvider<ZaiNaYaoYuan> {

    static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    static final TimeSignature TS  = new TimeSignature(4, 4);
    private static final int BAR_SF = 64;

    private static final int INTRO  = 4;
    private static final int VERSE  = 12;     // one 12-bar blues form
    private static final int SOLO   = 12;
    private static final int OUTRO  = 4;
    private static final int TOTAL  = INTRO + VERSE + VERSE + SOLO + VERSE + OUTRO; // 56

    private StaffPhraseBuilderTyped b() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Slow Blues in C"; }

    @Override
    public Piece create() {
        var id = new ZaiNaYaoYuan();

        var vocal    = joinMelodicPhrases("Vocal",       ACOUSTIC_GRAND_PIANO,    vocalPhrases());
        var lead     = joinMelodicPhrases("Lead Guitar", OVERDRIVEN_GUITAR,        leadPhrases());
        var organ    = joinMelodicPhrases("Organ",       ROCK_ORGAN,               organPhrases());
        var comping  = joinMelodicPhrases("Comp Guitar", ACOUSTIC_GUITAR_STEEL,    compingPhrases());
        var bass     = joinMelodicPhrases("Bass",        ELECTRIC_BASS_FINGER,     bassPhrases());
        var drums    = new DrumTrack("Drums", Phrase.of(buildDrumBars()));

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS, new Tempo(65, QUARTER),
                List.of(vocal, lead, organ, comping, bass),
                List.of(drums));
    }

    // ════════════════════════════════════════════════════════════════
    //  Vocal — melody on piano, present in the 3 verses only
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> vocalPhrases() {
        return List.<AuthorPhrase>of(
                silentSection(INTRO),
                verseMelody(),               // verse 1
                verseMelody(),               // verse 2
                silentSection(SOLO),         // solo — vocal silent
                verseMelody(),               // verse 3
                silentSection(OUTRO));
    }

    /** 12 bars: 8-bar melody (over bars 1-8 of the blues) + 4-bar rest tag. */
    private MelodicPhrase verseMelody() {
        return b()
                // ── Phrase A (over C7-F7-C7-C7) ──
                .bar().r(QUARTER).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o5(QUARTER, A).o5(EIGHTH, G).o5(EIGHTH, F.s()).done()  // 1: pickup + climb
                .bar().o5(EIGHTH, E).o5(EIGHTH, G).o5(HALF, A).o5(QUARTER, A).done() // 2: held A
                .bar().r(EIGHTH).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o5(QUARTER, A).o5(EIGHTH, G).o5(EIGHTH, E).o5(EIGHTH, D).done() // 3
                .bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(QUARTER, E)
                      .o5(EIGHTH, D).o5(EIGHTH, C).o5(QUARTER, C).done()      // 4
                // ── Phrase B (over F7-F7-C7-C7) ──
                .bar().o5(EIGHTH, E).o5(EIGHTH, G).o5(QUARTER, A)
                      .o5(EIGHTH, G).o5(EIGHTH, E).o5(QUARTER, D).done()      // 5
                .bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o5(QUARTER, A).o5(EIGHTH, G).o5(EIGHTH, F).done()      // 6
                .bar().o5(QUARTER, E).o5(EIGHTH, D).o5(EIGHTH, C)
                      .o4(QUARTER, B).o4(QUARTER, A).done()                   // 7: descend
                .bar().o4(WHOLE, A).done()                                    // 8: held resolve
                // ── Tag (bars 9-12: G7-F7-C7-G7) — vocal silent ──
                .bar().r(WHOLE).done().bar().r(WHOLE).done()
                .bar().r(WHOLE).done().bar().r(WHOLE).done()
                .build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Lead Guitar — sparse fills in verses, 12-bar blues solo
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> leadPhrases() {
        return List.<AuthorPhrase>of(
                silentSection(INTRO),
                verseTagFill(),              // verse 1 — fill the 4-bar tag only
                verseTagFill(),              // verse 2 — same
                bluesSolo(),                 // solo — full 12-bar
                verseTagFill(),              // verse 3 — same
                silentSection(OUTRO));
    }

    /**
     * Lead guitar during verses: silent for the 8-bar vocal melody,
     * then 4 bars of bluesy fill over the G7-F7-C7-G7 turnaround.
     */
    private MelodicPhrase verseTagFill() {
        var bb = b();
        for (int i = 0; i < 8; i++) bb.bar().r(WHOLE).done();   // silent under vocal
        // Bar 9 (G7): G blues lick
        bb.bar().o5(EIGHTH, G).o5(EIGHTH, B).o5(QUARTER, D.higher(1))
                .o5(EIGHTH, B).o5(EIGHTH, G).o5(QUARTER, F).done();
        // Bar 10 (F7): F lick
        bb.bar().o5(EIGHTH, F).o5(EIGHTH, A).o5(QUARTER, C.higher(1))
                .o5(EIGHTH, B.f()).o5(EIGHTH, A).o5(QUARTER, F).done();
        // Bar 11 (C7): turnaround set-up
        bb.bar().o5(EIGHTH, E).o5(EIGHTH, G).o5(QUARTER, C.higher(1))
                .o5(EIGHTH, B.f()).o5(EIGHTH, G).o5(QUARTER, E).done();
        // Bar 12 (G7): land on B → D, sets up next verse
        bb.bar().o5(QUARTER, G).o5(QUARTER, B).o5(HALF, D.higher(1)).done();
        return bb.build(attacca());
    }

    /**
     * 12-bar blues solo using the C blues scale (C E♭ F F♯ G B♭) plus
     * the underlying 7th chord-tones. Climbs to high C6 in bar 4 and
     * comes back down.
     */
    private MelodicPhrase bluesSolo() {
        return b()
                // Bar 1 (C7): classic blues lick
                .bar().o5(EIGHTH, C).o5(EIGHTH, E.f()).o5(EIGHTH, F).o5(EIGHTH, G)
                      .o5(QUARTER, B.f()).o5(QUARTER, G).done()
                // Bar 2 (F7)
                .bar().o5(EIGHTH, F).o5(EIGHTH, A).o5(EIGHTH, G).o5(EIGHTH, F)
                      .o5(QUARTER, E.f()).o5(QUARTER, C).done()
                // Bar 3 (C7) — chromatic E♭→E pull
                .bar().o5(EIGHTH, C).o5(EIGHTH, E.f()).o5(EIGHTH, E).o5(EIGHTH, G)
                      .o5(QUARTER, G).o5(QUARTER, E).done()
                // Bar 4 (C7) — climb to high C
                .bar().r(QUARTER).o5(EIGHTH, G).o5(EIGHTH, B.f())
                      .o6(QUARTER, C).o5(QUARTER, B.f()).done()
                // Bar 5 (F7)
                .bar().o6(QUARTER, C).o5(EIGHTH, B.f()).o5(EIGHTH, A)
                      .o5(QUARTER, F).o5(QUARTER, A).done()
                // Bar 6 (F7)
                .bar().o5(EIGHTH, F).o5(EIGHTH, A).o6(QUARTER, C)
                      .o5(EIGHTH, B.f()).o5(EIGHTH, G).o5(QUARTER, F).done()
                // Bar 7 (C7)
                .bar().o5(EIGHTH, E.f()).o5(EIGHTH, E).o5(QUARTER, G)
                      .o6(EIGHTH, C).o5(EIGHTH, B.f()).o5(QUARTER, G).done()
                // Bar 8 (C7) — breath
                .bar().o5(QUARTER, G).o5(EIGHTH, E.f()).o5(EIGHTH, C)
                      .o5(QUARTER, G).r(QUARTER).done()
                // Bar 9 (G7)
                .bar().o5(EIGHTH, G).o5(EIGHTH, B).o5(QUARTER, D.higher(1))
                      .o5(EIGHTH, B).o5(EIGHTH, G).o5(QUARTER, F).done()
                // Bar 10 (F7)
                .bar().o5(EIGHTH, F).o5(EIGHTH, A).o6(QUARTER, C)
                      .o5(EIGHTH, B.f()).o5(EIGHTH, A).o5(QUARTER, F).done()
                // Bar 11 (C7) — tag set-up
                .bar().o5(EIGHTH, G).o5(EIGHTH, E).o5(EIGHTH, D).o5(EIGHTH, C)
                      .o5(QUARTER, G).o5(QUARTER, C).done()
                // Bar 12 (G7) — turnaround
                .bar().o5(QUARTER, G).o5(QUARTER, F).o5(QUARTER, D).o5(QUARTER, G).done()
                .build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Organ — sustained 7th chord washes per blues bar
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> organPhrases() {
        return List.<AuthorPhrase>of(twelveBarBlock(TOTAL));
    }

    /** Whole-note chord per bar following 12-bar form (after the 4-bar intro vamp on C7). */
    private MelodicPhrase twelveBarBlock(int totalBars) {
        var bb = b();
        for (int bar = 0; bar < totalBars; bar++) {
            switch (chordIndex(bar)) {
                case 0 -> bb.bar().o3(WHOLE, C, E, G, B.f()).done();   // C7
                case 1 -> bb.bar().o3(WHOLE, F, A, C.higher(1), E.f().higher(1)).done(); // F7
                case 2 -> bb.bar().o3(WHOLE, G, B, D.higher(1), F.higher(1)).done();     // G7
            }
        }
        return bb.build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Comp Guitar — quarter-note chord punches (every beat)
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> compingPhrases() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            switch (chordIndex(bar)) {
                case 0 -> bb.bar()
                        .o3(QUARTER, C, E, G, B.f())
                        .o3(QUARTER, C, E, G, B.f())
                        .o3(QUARTER, C, E, G, B.f())
                        .o3(QUARTER, C, E, G, B.f()).done();
                case 1 -> bb.bar()
                        .o3(QUARTER, F, A, C.higher(1), E.f().higher(1))
                        .o3(QUARTER, F, A, C.higher(1), E.f().higher(1))
                        .o3(QUARTER, F, A, C.higher(1), E.f().higher(1))
                        .o3(QUARTER, F, A, C.higher(1), E.f().higher(1)).done();
                case 2 -> bb.bar()
                        .o3(QUARTER, G, B, D.higher(1), F.higher(1))
                        .o3(QUARTER, G, B, D.higher(1), F.higher(1))
                        .o3(QUARTER, G, B, D.higher(1), F.higher(1))
                        .o3(QUARTER, G, B, D.higher(1), F.higher(1)).done();
            }
        }
        return List.<AuthorPhrase>of(bb.build(attacca()));
    }

    // ════════════════════════════════════════════════════════════════
    //  Bass — walking pattern (root-3rd-5th-6th over each chord)
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> bassPhrases() {
        var bb = b();
        for (int bar = 0; bar < TOTAL; bar++) {
            switch (chordIndex(bar)) {
                case 0 -> bb.bar(QUARTER).o3(C).o3(E).o3(G).o3(A).done();   // C7 walking
                case 1 -> bb.bar(QUARTER).o3(F).o3(A).o4(C).o4(D).done();   // F7 walking
                case 2 -> bb.bar(QUARTER).o3(G).o3(B).o4(D).o4(E).done();   // G7 walking
            }
        }
        return List.<AuthorPhrase>of(bb.build(attacca()));
    }

    // ── Chord index for each bar in the song ────────────────────────

    /** Returns 0=C7, 1=F7, 2=G7 for the given absolute bar index. */
    private static int chordIndex(int absBar) {
        if (absBar < INTRO) return 0;                     // intro vamp on C7
        int bar = (absBar - INTRO) % VERSE;               // 0..11 within form
        return switch (bar) {
            case 0, 2, 3, 6, 7, 10 -> 0;                  // C7
            case 1, 4, 5, 9        -> 1;                  // F7
            case 8, 11             -> 2;                  // G7
            default                -> 0;
        };
    }

    // ════════════════════════════════════════════════════════════════
    //  Drums — slow blues backbeat with cross-stick feel
    // ════════════════════════════════════════════════════════════════

    private List<Bar> buildDrumBars() {
        var bars = new ArrayList<Bar>();
        // Intro (4) — sparse: hi-hat ticks bar 1, snare pickup bar 4
        bars.add(silentBar());
        bars.add(hatTickBar());
        bars.add(hatTickBar());
        bars.add(snarePickupBar());
        // Verse 1 (12) — gentle backbeat with hi-hat
        for (int i = 0; i < 11; i++) bars.add(slowBluesBar());
        bars.add(snarePickupBar());
        // Verse 2 (12) — same with crash on bar 1
        bars.add(crashBackbeatBar());
        for (int i = 0; i < 10; i++) bars.add(slowBluesBar());
        bars.add(snareFillBar());
        // Solo (12) — switch to ride cymbal
        bars.add(crashBackbeatBar());
        for (int i = 0; i < 10; i++) bars.add(rideBluesBar());
        bars.add(snareFillBar());
        // Verse 3 (12) — biggest energy
        bars.add(crashBackbeatBar());
        for (int i = 0; i < 10; i++) bars.add(slowBluesBar());
        bars.add(snareFillBar());
        // Outro (4)
        for (int i = 0; i < 3; i++) bars.add(slowBluesBar());
        bars.add(finalBluesBar());
        return bars;
    }

    // ── Drum bar primitives ─────────────────────────────────────────

    /** Slow-blues backbeat: K H S H K H S H. */
    private static Bar slowBluesBar() {
        var k = perc(BASS_DRUM, EIGHTH);
        var h = perc(CLOSED_HI_HAT, EIGHTH);
        var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return Bar.of(BAR_SF, k, h, s, h, k, h, s, h);
    }

    /** Solo backing — same backbeat but ride cymbal instead of hi-hat. */
    private static Bar rideBluesBar() {
        var k = perc(BASS_DRUM, EIGHTH);
        var r = perc(RIDE_CYMBAL, EIGHTH);
        var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return Bar.of(BAR_SF, k, r, s, r, k, r, s, r);
    }

    /** Verse opener: replace the bar-1 hi-hat with a crash. */
    private static Bar crashBackbeatBar() {
        var c = perc(CRASH_CYMBAL, EIGHTH);
        var k = perc(BASS_DRUM, EIGHTH);
        var h = perc(CLOSED_HI_HAT, EIGHTH);
        var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return Bar.of(BAR_SF, c, h, s, h, k, h, s, h);
    }

    /** Just hi-hat quarters — sparse intro. */
    private static Bar hatTickBar() {
        var h = perc(CLOSED_HI_HAT, QUARTER);
        return Bar.of(BAR_SF, h, h, h, h);
    }

    /** Single snare pickup at end of bar 4 — count-in feel. */
    private static Bar snarePickupBar() {
        var h = perc(CLOSED_HI_HAT, QUARTER);
        var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return Bar.of(BAR_SF, h, h, h, s, s);   // 16+16+16+8+8 = 64
    }

    /** Snare fill into next section. */
    private static Bar snareFillBar() {
        var k  = perc(BASS_DRUM, EIGHTH);
        var s  = perc(ACOUSTIC_SNARE, EIGHTH);
        var ht = perc(HIGH_TOM, EIGHTH);
        var mt = perc(HIGH_MID_TOM, EIGHTH);
        var lt = perc(LOW_TOM, EIGHTH);
        var c  = perc(CRASH_CYMBAL, EIGHTH);
        return Bar.of(BAR_SF, k, s, s, ht, mt, lt, lt, c);
    }

    /** Final crash + held resolve. */
    private static Bar finalBluesBar() {
        var c = perc(CRASH_CYMBAL, EIGHTH);
        var k = perc(BASS_DRUM, EIGHTH);
        var s = perc(ACOUSTIC_SNARE, QUARTER);
        return Bar.of(BAR_SF, c, k, s,
                (PhraseNode) new RestNode(Duration.of(HALF)));
    }

    private static Bar silentBar() {
        return Bar.of(BAR_SF,
                (PhraseNode) new RestNode(Duration.ofSixtyFourths(BAR_SF)));
    }

    private static PercussionNote perc(PercussionSound sound,
                                       music.notation.duration.BaseValue dur) {
        return new PercussionNote(sound, Duration.of(dur));
    }

    // ── shared helper ───────────────────────────────────────────────

    private MelodicPhrase silentSection(int bars) {
        var bb = b();
        for (int i = 0; i < bars; i++) bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    /** Convenience: launch this arrangement directly. */
    public static void main(String[] args) throws Exception {
        PlayPiece.play(new BluesZaiNaYaoYuan());
    }
}
