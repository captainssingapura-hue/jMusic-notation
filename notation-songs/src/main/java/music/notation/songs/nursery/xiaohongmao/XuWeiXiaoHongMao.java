package music.notation.songs.nursery.xiaohongmao;

import music.notation.duration.Duration;
import music.notation.event.PercussionSound;
import music.notation.phrase.AuthorPhrase;
import music.notation.phrase.Bar;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.PitchNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.StaffPhraseBuilderTyped;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
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
 * 小红帽 — 许巍-style rock-ballad arrangement.
 *
 * <p>Keeps the children's-song melody intact and places it inside a
 * full mid-tempo arrangement reminiscent of 许巍's anthemic style:
 * delay-flavoured clean electric guitar arpeggios, steel-string
 * acoustic strumming, picked electric bass, warm synth pad and
 * driving rock drums. The melody is converted from the source 2/4 to
 * 4/4 (each pair of source bars folds into one bar) and tempo lifted
 * from 60 to 88 BPM for the rock feel.</p>
 *
 * <p>Structure (~3:30):
 * Intro 8 · Verse 12 · Chorus 8 · Interlude 4 · Verse 12 ·
 * Chorus 8 · Bridge 8 · Final Chorus 8 · Outro 4 = 72 bars.</p>
 */
public final class XuWeiXiaoHongMao implements PieceContentProvider<XiaoHongMao> {

    static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    static final TimeSignature TS  = new TimeSignature(4, 4);
    private static final int BAR_SF = 64;

    private StaffPhraseBuilderTyped b() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "许巍 rock ballad"; }

    @Override
    public Piece create() {
        var id = new XiaoHongMao();

        var vocal    = joinMelodicPhrases("Vocal",           ACOUSTIC_GRAND_PIANO,
                                          vocalPhrases());
        var lead     = joinMelodicPhrases("Lead Guitar",     OVERDRIVEN_GUITAR,
                                          leadPhrases());
        var edge     = joinMelodicPhrases("Edge Guitar",     ELECTRIC_GUITAR_CLEAN,
                                          edgePhrases());
        var acoustic = joinMelodicPhrases("Acoustic",        ACOUSTIC_GUITAR_STEEL,
                                          acousticPhrases());
        var bass     = joinMelodicPhrases("Bass",            ELECTRIC_BASS_PICK,
                                          bassPhrases());
        var pad      = joinMelodicPhrases("Synth Pad",       SYNTH_PAD_WARM,
                                          padPhrases());
        var drums    = new DrumTrack("Drums", Phrase.of(buildDrumBars()));

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS, new Tempo(88, QUARTER),
                List.of(vocal, lead, edge, acoustic, bass, pad),
                List.of(drums));
    }

    // ── Section bar counts (4/4) ─────────────────────────────────────
    private static final int INTRO    = 8;
    private static final int VERSE    = 12;
    private static final int CHORUS   = 8;
    private static final int INTERLUDE= 4;
    private static final int BRIDGE   = 16;     // 16-bar guitar solo (李延亮 style)
    private static final int OUTRO    = 4;
    private static final int TOTAL    = INTRO + VERSE + CHORUS + INTERLUDE
                                      + VERSE + CHORUS + BRIDGE + CHORUS + OUTRO; // 80

    // ════════════════════════════════════════════════════════════════
    //  Vocal — main melody on piano (vocal stand-in)
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> vocalPhrases() {
        return List.<AuthorPhrase>of(
                silentSection(INTRO),
                verseMelody(),              // verse 1
                chorusMelody(),             // chorus 1
                silentSection(INTERLUDE),   // lead guitar lick fills the gap
                verseMelody(),              // verse 2
                chorusMelody(),             // chorus 2
                silentSection(BRIDGE),      // 16-bar guitar solo owns this
                chorusMelody(),             // final chorus
                vocalOutro());              // sparse arpeggios fading
    }

    /** Outro — 4 bars of slow C-Am-F-C arpeggios, fading. */
    private MelodicPhrase vocalOutro() {
        return b()
                .bar().o5(EIGHTH, C).o5(EIGHTH, E).o5(EIGHTH, G).o6(EIGHTH, C)
                      .o5(HALF, G).done()                                            // C
                .bar().o5(EIGHTH, A).o6(EIGHTH, C).o6(EIGHTH, E).o6(EIGHTH, A)
                      .o6(HALF, E).done()                                            // Am
                .bar().o5(EIGHTH, F).o5(EIGHTH, A).o6(EIGHTH, C).o6(EIGHTH, F)
                      .o6(HALF, C).done()                                            // F
                .bar().o6(WHOLE, C).done()                                           // resolve
                .build(end());
    }

    // ════════════════════════════════════════════════════════════════
    //  Lead Guitar — fills, sustained color, harmonised line, solo
    //  Stays out of the vocal's way; soars when the band needs it.
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> leadPhrases() {
        return List.<AuthorPhrase>of(
                silentSection(INTRO),       // intro — Edge arpeggios only
                verseLeadFills(),           // verse 1 — sparse fills
                chorusLeadSustain(),        // chorus 1 — Pattern B (color)
                interludeLeadLick(),        // interlude — 4-bar melodic phrase
                verseLeadFills(),           // verse 2 — same fill shape
                chorusLeadHarmony(),        // chorus 2 — Pattern C (harmonised)
                bridgeSolo(),               // bridge — 16-bar lead solo
                chorusLeadHarmony(),        // final chorus — harmonised
                silentSection(OUTRO));      // piano carries the fade
    }

    /**
     * Verse fills — silent for bars 1-3, lick on bar 4, repeats for bars
     * 5-8 and 9-12. Fills reference the song's melodic contour.
     */
    private MelodicPhrase verseLeadFills() {
        var bb = b();
        // Bars 1-3: silent
        for (int i = 0; i < 3; i++) bb.bar().r(WHOLE).done();
        // Bar 4 fill — half rest, then ascending pickup over G chord
        bb.bar().r(HALF).o5(EIGHTH, G).o5(EIGHTH, A).o5(QUARTER, B).done();
        // Bars 5-7: silent
        for (int i = 0; i < 3; i++) bb.bar().r(WHOLE).done();
        // Bar 8 fill — C arpeggio answer
        bb.bar().r(HALF).o5(EIGHTH, E).o5(EIGHTH, G).o6(QUARTER, C).done();
        // Bars 9-11: silent
        for (int i = 0; i < 3; i++) bb.bar().r(WHOLE).done();
        // Bar 12 fill — turnaround into chorus
        bb.bar().r(HALF).o5(EIGHTH, G).o6(EIGHTH, C).o6(QUARTER, E).done();
        return bb.build(attacca());
    }

    /**
     * Chorus 1 — Pattern B sustained color: punch a chord-tone quarter,
     * then sustain a higher 7th/9th colour for the rest of the bar.
     */
    private MelodicPhrase chorusLeadSustain() {
        return b()
                // C: G punch, E sustain (third up an octave — bright)
                .bar().o5(QUARTER, G).o6(HALF.dot(), E).done()
                // Am: E punch, A sustain (root, gives weight)
                .bar().o5(QUARTER, E).o5(HALF.dot(), A).done()
                // F: A punch, C sustain (Fmaj add-something)
                .bar().o5(QUARTER, A).o6(HALF.dot(), C).done()
                // G: B punch, D sustain (G major colour)
                .bar().o5(QUARTER, B).o6(HALF.dot(), D).done()
                // C
                .bar().o5(QUARTER, G).o6(HALF.dot(), E).done()
                // Am
                .bar().o5(QUARTER, E).o5(HALF.dot(), A).done()
                // F
                .bar().o5(QUARTER, A).o6(HALF.dot(), C).done()
                // G→C resolve — quarter B, half D6, quarter C6
                .bar().o5(QUARTER, B).o6(HALF, D).o6(QUARTER, C).done()
                .build(attacca());
    }

    /** Interlude — slow C-Am-F-G arpeggios in lead's spotlight. */
    private MelodicPhrase interludeLeadLick() {
        return b()
                .bar().o5(QUARTER, E).o5(QUARTER, G).o6(HALF, C).done()       // C
                .bar().o5(QUARTER, A).o6(QUARTER, C).o6(HALF, E).done()       // Am
                .bar().o5(QUARTER, F).o5(QUARTER, A).o6(HALF, C).done()       // F
                .bar().o5(QUARTER, G).o5(QUARTER, B).o6(HALF, D).done()       // G
                .build(attacca());
    }

    /**
     * Chorus 2 / Final Chorus — Pattern C: parallel diatonic-third
     * harmony above the chorus melody (each note up a third in C major).
     * Same rhythm as the vocal; sounds like an octave-apart vocal harmony.
     */
    private MelodicPhrase chorusLeadHarmony() {
        return b()
                // melody bar 1: C D E F G E C → harmony: E F G A B G E
                .bar().o5(EIGHTH, E).o5(EIGHTH, F).o5(EIGHTH, G).o5(EIGHTH, A)
                      .o5(QUARTER, B).o5(EIGHTH, G).o5(EIGHTH, E).done()
                // melody bar 2: C6 A F G E → harmony: E6 C6 A B G
                .bar().o6(QUARTER, E).o6(EIGHTH, C).o5(EIGHTH, A)
                      .o5(QUARTER, B).o5(QUARTER, G).done()
                // melody bar 3: C D E F G E D C → harmony: E F G A B G F E
                .bar().o5(EIGHTH, E).o5(EIGHTH, F).o5(EIGHTH, G).o5(EIGHTH, A)
                      .o5(EIGHTH, B).o5(EIGHTH, G).o5(EIGHTH, F).o5(EIGHTH, E).done()
                // melody bar 4: D E C C → harmony: F G E E
                .bar().o5(QUARTER, F).o5(QUARTER, G).o5(QUARTER, E).o5(QUARTER, E).done()
                // melody bar 5: C6 A F G G C → harmony: E6 C6 A B B E
                .bar().o6(QUARTER, E).o6(EIGHTH, C).o5(EIGHTH, A)
                      .o5(EIGHTH, B).o5(EIGHTH, B).o5(QUARTER, E).done()
                // melody bar 6: C6 A F G E → harmony: E6 C6 A B G
                .bar().o6(QUARTER, E).o6(EIGHTH, C).o5(EIGHTH, A)
                      .o5(QUARTER, B).o5(QUARTER, G).done()
                // melody bar 7: same as bar 3 → same harmony
                .bar().o5(EIGHTH, E).o5(EIGHTH, F).o5(EIGHTH, G).o5(EIGHTH, A)
                      .o5(EIGHTH, B).o5(EIGHTH, G).o5(EIGHTH, F).o5(EIGHTH, E).done()
                // melody bar 8: D E C(HALF) → harmony: F G E(HALF)
                .bar().o5(QUARTER, F).o5(QUARTER, G).o5(HALF, E).done()
                .build(attacca());
    }

    /** Original 24-bar 2/4 melody folded into 12 bars 4/4. */
    private MelodicPhrase verseMelody() {
        return b()
                .bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(EIGHTH, E).o5(EIGHTH, F)
                      .o5(QUARTER, G).o5(EIGHTH, E).o5(EIGHTH, C).done()        // 1
                .bar().o6(QUARTER, C).o5(EIGHTH, A).o5(EIGHTH, F)
                      .o5(EIGHTH, G).o5(EIGHTH, G).o5(QUARTER, E).done()        // 2
                .bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(EIGHTH, E).o5(EIGHTH, F)
                      .o5(EIGHTH, G).o5(EIGHTH, E).o5(EIGHTH, D).o5(EIGHTH, C).done() // 3
                .bar().o5(QUARTER, D).o5(QUARTER, E)
                      .o5(QUARTER, D).o5(QUARTER, G).done()                     // 4
                .bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(EIGHTH, E).o5(EIGHTH, F)
                      .o5(QUARTER, G).o5(EIGHTH, E).o5(EIGHTH, C).done()        // 5
                .bar().o6(QUARTER, C).o5(EIGHTH, A).o5(EIGHTH, F)
                      .o5(QUARTER, G).o5(QUARTER, E).done()                     // 6
                .bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(EIGHTH, E).o5(EIGHTH, F)
                      .o5(EIGHTH, G).o5(EIGHTH, E).o5(EIGHTH, D).o5(EIGHTH, C).done() // 7
                .bar().o5(QUARTER, D).o5(QUARTER, E)
                      .o5(QUARTER, C).o5(QUARTER, C).done()                     // 8
                .bar().o6(QUARTER, C).o5(EIGHTH, A).o5(EIGHTH, F)
                      .o5(EIGHTH, G).o5(EIGHTH, G).o5(QUARTER, C).done()        // 9
                .bar().o6(QUARTER, C).o5(EIGHTH, A).o5(EIGHTH, F)
                      .o5(QUARTER, G).o5(QUARTER, E).done()                     // 10
                .bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(EIGHTH, E).o5(EIGHTH, F)
                      .o5(EIGHTH, G).o5(EIGHTH, E).o5(EIGHTH, D).o5(EIGHTH, C).done() // 11
                .bar().o5(QUARTER, D).o5(QUARTER, E)
                      .o5(HALF, C).done()                                       // 12 — sustain end
                .build(attacca());
    }

    /** Catchy second-half + ending: source bars 9-16 condensed = 4+4 bars. */
    private MelodicPhrase chorusMelody() {
        // Use bars 5-8 then 9-12 of the verse melody (anthemic part).
        return b()
                .bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(EIGHTH, E).o5(EIGHTH, F)
                      .o5(QUARTER, G).o5(EIGHTH, E).o5(EIGHTH, C).done()
                .bar().o6(QUARTER, C).o5(EIGHTH, A).o5(EIGHTH, F)
                      .o5(QUARTER, G).o5(QUARTER, E).done()
                .bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(EIGHTH, E).o5(EIGHTH, F)
                      .o5(EIGHTH, G).o5(EIGHTH, E).o5(EIGHTH, D).o5(EIGHTH, C).done()
                .bar().o5(QUARTER, D).o5(QUARTER, E)
                      .o5(QUARTER, C).o5(QUARTER, C).done()
                .bar().o6(QUARTER, C).o5(EIGHTH, A).o5(EIGHTH, F)
                      .o5(EIGHTH, G).o5(EIGHTH, G).o5(QUARTER, C).done()
                .bar().o6(QUARTER, C).o5(EIGHTH, A).o5(EIGHTH, F)
                      .o5(QUARTER, G).o5(QUARTER, E).done()
                .bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(EIGHTH, E).o5(EIGHTH, F)
                      .o5(EIGHTH, G).o5(EIGHTH, E).o5(EIGHTH, D).o5(EIGHTH, C).done()
                .bar().o5(QUARTER, D).o5(QUARTER, E)
                      .o5(HALF, C).done()
                .build(attacca());
    }

    /**
     * 16-bar lead-guitar solo in 李延亮's style: melodic statement
     * referencing the song's theme, building through 16th-note runs
     * to a screaming high climax around C7, then resolving back into
     * the final chorus. Sits over the C-Am-F-G arpeggio cycle that
     * the rhythm section keeps spinning underneath.
     */
    private MelodicPhrase bridgeSolo() {
        return b()
                // ── Bars 1-4: melodic call-and-response, derived from the tune ──
                .bar().o5(QUARTER, G).o6(EIGHTH, C).o5(EIGHTH, A)
                      .o5(QUARTER, G).o5(QUARTER, E).done()                       // 1 — over C
                .bar().o5(QUARTER, A).o6(EIGHTH, C).o6(EIGHTH, E)
                      .o6(HALF, D).done()                                         // 2 — over Am
                .bar().o6(QUARTER, C).o5(QUARTER, A).o5(HALF, F).done()           // 3 — over F (sustain)
                .bar().o5(EIGHTH, G).o5(EIGHTH, A).o5(QUARTER, B)
                      .o6(HALF, D).done()                                         // 4 — over G (turn-up)

                // ── Bars 5-8: rhythmic development, faster phrasing ──
                .bar().o6(EIGHTH, E).o6(EIGHTH, D).o6(EIGHTH, C).o5(EIGHTH, A)
                      .o5(QUARTER, G).o5(QUARTER, E).done()                       // 5 — over C
                .bar().o5(SIXTEENTH, A).o6(SIXTEENTH, C).o6(SIXTEENTH, E).o6(SIXTEENTH, G)
                      .o6(QUARTER, A).o6(QUARTER, E).o6(QUARTER, C).done()        // 6 — over Am (16ths)
                .bar().o6(QUARTER, F).o6(HALF.dot(), A).done()                    // 7 — over F (held high A)
                .bar().o6(EIGHTH, G).o6(EIGHTH, A).o6(EIGHTH, B).o7(EIGHTH, C)
                      .o7(HALF, D).done()                                         // 8 — over G (climbing)

                // ── Bars 9-12: scalar climb to climax, screaming high notes ──
                .bar().o6(SIXTEENTH, C).o6(SIXTEENTH, D).o6(SIXTEENTH, E).o6(SIXTEENTH, F)
                      .o6(SIXTEENTH, G).o6(SIXTEENTH, A).o6(SIXTEENTH, B).o7(SIXTEENTH, C)
                      .o7(HALF, C).done()                                         // 9 — over C (full scale)
                .bar().o7(QUARTER, C).o6(EIGHTH, B).o6(EIGHTH, A)
                      .o6(QUARTER, G).o6(QUARTER, E).done()                       // 10 — over Am
                .bar().o6(EIGHTH, F).o6(EIGHTH, G).o6(QUARTER, A)
                      .o6(EIGHTH, G).o6(EIGHTH, A).o7(QUARTER, C).done()          // 11 — over F (peak)
                .bar().o6(SIXTEENTH, D).o6(SIXTEENTH, G).o6(SIXTEENTH, B).o7(SIXTEENTH, D)
                      .o7(HALF, D).o6(QUARTER, B).done()                          // 12 — over G (held high)

                // ── Bars 13-16: come back down, set up final chorus ──
                .bar().o7(QUARTER, C).o6(EIGHTH, B).o7(EIGHTH, C)
                      .o7(HALF, D).done()                                         // 13 — over C (vibrato feel)
                .bar().o7(QUARTER, C).o6(QUARTER, A)
                      .o6(QUARTER, G).o6(QUARTER, E).done()                       // 14 — over Am
                .bar().o6(EIGHTH, F).o6(EIGHTH, E).o6(EIGHTH, D).o6(EIGHTH, C)
                      .o5(QUARTER, A).o5(QUARTER, G).done()                       // 15 — over F (descend)
                .bar().o5(QUARTER, D).o5(QUARTER, E).o5(HALF, G).done()           // 16 — over G (cadence)
                .build(attacca());
    }

    private MelodicPhrase silentSection(int bars) {
        var bb = b();
        for (int i = 0; i < bars; i++) bb.bar().r(WHOLE).done();
        return bb.build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Edge Guitar — clean delay-style arpeggios
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> edgePhrases() {
        return List.<AuthorPhrase>of(arpProgression(TOTAL));
    }

    /** One quarter-note arpeggio per beat. C-Am-F-G cycling — Xu Wei staple. */
    private MelodicPhrase arpProgression(int totalBars) {
        var bb = b();
        for (int bar = 0; bar < totalBars; bar++) {
            switch (bar % 4) {
                case 0 -> bb.bar(EIGHTH).o4(C).o4(E).o4(G).o5(C)
                                        .o5(E).o5(C).o4(G).o4(E).done(); // C
                case 1 -> bb.bar(EIGHTH).o4(A).o5(C).o5(E).o5(A)
                                        .o5(E).o5(C).o4(A).o5(C).done(); // Am
                case 2 -> bb.bar(EIGHTH).o4(F).o4(A).o5(C).o5(F)
                                        .o5(C).o4(A).o5(F).o5(C).done(); // F
                case 3 -> bb.bar(EIGHTH).o4(G).o4(B).o5(D).o5(G)
                                        .o5(D).o4(B).o5(G).o5(D).done(); // G
            }
        }
        return bb.build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Acoustic — sustained chord strums (whole-note voicings)
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> acousticPhrases() {
        return List.<AuthorPhrase>of(acousticChords(TOTAL));
    }

    private MelodicPhrase acousticChords(int totalBars) {
        var bb = b();
        for (int bar = 0; bar < totalBars; bar++) {
            switch (bar % 4) {
                case 0 -> bb.bar().o3(WHOLE, C, G, C.higher(1), E.higher(1)).done(); // C
                case 1 -> bb.bar().o3(WHOLE, A, E.higher(1), A.higher(1), C.higher(2)).done(); // Am
                case 2 -> bb.bar().o3(WHOLE, F, C.higher(1), F.higher(1), A.higher(1)).done(); // F
                case 3 -> bb.bar().o3(WHOLE, G, D.higher(1), G.higher(1), B.higher(1)).done(); // G
            }
        }
        return bb.build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Bass — picked roots & fifths
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> bassPhrases() {
        return List.<AuthorPhrase>of(bassLine(TOTAL));
    }

    /** Quarter-note root + fifth pattern, following the C-Am-F-G cycle. */
    private MelodicPhrase bassLine(int totalBars) {
        var bb = b();
        for (int bar = 0; bar < totalBars; bar++) {
            // Intro (first 8) gets sparser bass: whole-note roots.
            if (bar < INTRO) {
                switch (bar % 4) {
                    case 0 -> bb.bar().o3(WHOLE, C).done();   // C
                    case 1 -> bb.bar().o2(WHOLE, A).done();   // Am
                    case 2 -> bb.bar().o3(WHOLE, F).done();   // F
                    case 3 -> bb.bar().o2(WHOLE, G).done();   // G
                }
            } else {
                switch (bar % 4) {
                    case 0 -> bb.bar(QUARTER).o3(C).o3(G).o3(C).o3(E).done();   // C
                    case 1 -> bb.bar(QUARTER).o2(A).o3(E).o2(A).o3(C).done();   // Am
                    case 2 -> bb.bar(QUARTER).o3(F).o3(C).o3(F).o3(A).done();   // F
                    case 3 -> bb.bar(QUARTER).o2(G).o3(D).o2(G).o3(B).done();   // G
                }
            }
        }
        return bb.build(attacca());
    }

    // ════════════════════════════════════════════════════════════════
    //  Pad — sustained chords through every section
    // ════════════════════════════════════════════════════════════════

    private List<AuthorPhrase> padPhrases() {
        return List.<AuthorPhrase>of(acousticChords(TOTAL));   // same voicings
    }

    // ════════════════════════════════════════════════════════════════
    //  Drums — silent intro, building backbeat, anthemic chorus
    // ════════════════════════════════════════════════════════════════

    private List<Bar> buildDrumBars() {
        var bars = new ArrayList<Bar>();
        // Intro (8) — silent until bar 7 hi-hat ticks, bar 8 fill into verse.
        for (int i = 0; i < 6; i++) bars.add(silentBar());
        bars.add(hatTickBar());
        bars.add(snareFillBar());
        // Verse 1 (12) — half-time backbeat
        for (int i = 0; i < 12; i++) bars.add(halfTimeBar());
        // Chorus 1 (8) — full backbeat with crash on bar 1
        bars.add(crashBackbeatBar());
        for (int i = 0; i < 6; i++) bars.add(backbeatBar());
        bars.add(snareFillBar());
        // Interlude (4) — half-time keep
        for (int i = 0; i < 4; i++) bars.add(halfTimeBar());
        // Verse 2 (12) — half-time
        for (int i = 0; i < 12; i++) bars.add(halfTimeBar());
        // Chorus 2 (8)
        bars.add(crashBackbeatBar());
        for (int i = 0; i < 6; i++) bars.add(backbeatBar());
        bars.add(snareFillBar());
        // Bridge (16) — solo backing: ride builds to backbeat under guitar climax.
        for (int i = 0; i < 8;  i++) bars.add(rideHalfTimeBar());   // bars 1-8 ride/HT
        for (int i = 0; i < 6;  i++) bars.add(backbeatBar());       // bars 9-14 driving
        bars.add(snareFillBar());                                    // bar 15 fill
        bars.add(crashBackbeatBar());                                // bar 16 crash → final chorus
        // Final Chorus (8) — anthemic crash on every downbeat
        for (int i = 0; i < 7; i++) bars.add(crashBackbeatBar());
        bars.add(finalCrashBar());
        // Outro (4) — fading hi-hat
        for (int i = 0; i < 3; i++) bars.add(hatTickBar());
        bars.add(silentBar());
        return bars;
    }

    // ── Drum bar primitives ─────────────────────────────────────────

    private static Bar halfTimeBar() {
        var k = perc(BASS_DRUM, EIGHTH);
        var h = perc(CLOSED_HI_HAT, EIGHTH);
        var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return Bar.of(BAR_SF, k, h, h, h, s, h, h, h);
    }

    private static Bar backbeatBar() {
        var k = perc(BASS_DRUM, EIGHTH);
        var h = perc(CLOSED_HI_HAT, EIGHTH);
        var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return Bar.of(BAR_SF, k, h, s, h, k, h, s, h);
    }

    private static Bar crashBackbeatBar() {
        var c = perc(CRASH_CYMBAL, EIGHTH);
        var k = perc(BASS_DRUM, EIGHTH);
        var h = perc(CLOSED_HI_HAT, EIGHTH);
        var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return Bar.of(BAR_SF, c, h, s, h, k, h, s, h);
    }

    private static Bar rideHalfTimeBar() {
        var k = perc(BASS_DRUM, EIGHTH);
        var r = perc(RIDE_CYMBAL, EIGHTH);
        var s = perc(ACOUSTIC_SNARE, EIGHTH);
        return Bar.of(BAR_SF, k, r, r, r, s, r, r, r);
    }

    private static Bar hatTickBar() {
        var h = perc(CLOSED_HI_HAT, QUARTER);
        return Bar.of(BAR_SF, h, h, h, h);
    }

    private static Bar snareFillBar() {
        var k  = perc(BASS_DRUM, EIGHTH);
        var s  = perc(ACOUSTIC_SNARE, EIGHTH);
        var ht = perc(HIGH_TOM, EIGHTH);
        var mt = perc(HIGH_MID_TOM, EIGHTH);
        var lt = perc(LOW_TOM, EIGHTH);
        var c  = perc(CRASH_CYMBAL, EIGHTH);
        return Bar.of(BAR_SF, k, s, s, ht, ht, mt, lt, c);
    }

    private static Bar finalCrashBar() {
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

    /** Convenience: launch this arrangement directly. */
    public static void main(String[] args) throws Exception {
        PlayPiece.play(new XuWeiXiaoHongMao());
    }
}
