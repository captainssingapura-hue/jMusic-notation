package music.notation.songs.folk.tianheihei;

import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * 天黑黑 (Tian Hei Hei) — U2-style rock arrangement.
 *
 * <p>Features The Edge's signature dotted-quarter delay arpeggios,
 * driving picked bass, atmospheric rock organ, and anthemic drums
 * with a half-time verse feel building to full backbeat choruses.</p>
 */
public final class U2RockTianHeiHei implements PieceContentProvider<TianHeiHei> {

    private static final KeySignature KEY = PianoTianHeiHei.KEY;
    private static final TimeSignature TS = PianoTianHeiHei.TS;
    private final PianoTianHeiHei piano = new PianoTianHeiHei();

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "U2 Rock"; }

    @Override
    public Piece create() {
        var id = new TianHeiHei();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(116, QUARTER),
                List.of(lead(), pianoHarmony(), edgeGuitar(), bass(), organ(), drums()));
    }

    // ── Lead: Electric Piano 1 ───────────────────────────────────────

    private Track lead() {
        var m1 = piano.buildMelodyMain1();
        var th = piano.buildMelodyTianHeiHei1();
        var ch = piano.buildMelodyMain2();
        var ending = piano.buildEnding();
        return Track.of("Lead", ELECTRIC_PIANO_1, List.of(
                piano.buildMelodyPre(), m1, th, m1, th,
                ch, piano.buildBridge(), ch,
                piano.overrideMelodyMain2(),
                ending, th));
    }

    // ── Piano: Acoustic Grand — Chopin nocturne harmony ──────────────

    private Track pianoHarmony() {
        var m1 = piano.buildHarmonyMain1();
        var th = piano.buildHarmonyTianHei();
        var ch = piano.buildHarmonyChorus();
        var ending = piano.buildHarmonyEnding();
        return Track.of("Piano", ACOUSTIC_GRAND_PIANO, List.of(
                piano.buildHarmonyPre(), m1, th, m1, th,
                ch, piano.buildHarmonyBridge(), ch,
                piano.overrideHarmonyChorus(),
                ending, th));
    }

    // ── Edge Guitar: dotted-quarter delay arpeggios ──────────────────

    private Track edgeGuitar() {
        var m1 = edgeMain1();
        var th = edgeTianHei();
        var ch = edgeChorus();
        return Track.of("Edge Guitar", ELECTRIC_GUITAR_CLEAN, List.of(
                edgePre(), m1, th, m1, th,
                ch, edgeBridge(), ch, ch,
                edgeEnding(), th));
    }

    // Pre: sparse — solo Edge arpeggios emerging from silence
    private MelodicPhrase edgePre() {
        return b()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                // Edge enters bar 3: G arpeggio
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)
                // Last bar: shorten so it ends with trailing pad ≥ 16sf (needed for
                // elision into edgeMain1's 48sf leading-padding pickup).
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER, B).ending()
                .build(elision());
    }

    // Main1: signature dotted-quarter arpeggios — G Am C G | G Am D G
    private MelodicPhrase edgeMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E)   // Am
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), G).o5(EIGHTH, C)   // C
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E)   // Am
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D)   // D
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .build(attacca());
    }

    // TianHeiHei: sustained half-note power chords for contrast
    private MelodicPhrase edgeTianHei() {
        return b()
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, G, D.higher(1))                       // G
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, G, D.higher(1))                       // Gm (power chord same)
                .bar().o4(HALF, C, G).o4(HALF, C, G)                                           // Cm
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, G, D.higher(1))                       // G
                .bar().o4(HALF, C, G).o4(HALF, C, G)                                           // C
                .bar().o4(HALF, D, A).o4(HALF, D, A)                                           // D
                .bar().o4(HALF, G, D.higher(1)).ending()                                        // G resolve
                .build(elision());
    }

    // Chorus: energetic arpeggios — D G D G C Am D G
    private MelodicPhrase edgeChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D)   // D
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D)   // D
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), G).o5(EIGHTH, C)   // C
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E)   // Am
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D)   // D
                .bar().o4(HALF, G, D.higher(1)).ending()                                        // G sustain
                .build(elision());
    }

    // Bridge: alternating arpeggios and sustained — G Am C+D G Em Am C D
    private MelodicPhrase edgeBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E)   // Am
                .bar().o4(HALF, C, G).o4(HALF, D, A)                                           // C → D sustained
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, E)   // Em
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E)   // Am
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), G).o5(EIGHTH, C)   // C
                // D: shorten so trailing pad ≥ 24 (for elision into chorus's 40sf leading pad)
                .bar().o4(HALF, D, A).ending()                                                  // D
                .build(elision());
    }

    // Ending: arpeggios intensifying then resolving
    private MelodicPhrase edgeEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E)   // Am
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), G).o5(EIGHTH, C)   // C
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E)   // Am
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D)   // D
                .bar().o4(HALF, C, G).o4(HALF, G, D.higher(1))                                 // C → G
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D)   // D
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D)   // G
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D)   // D
                .bar().o4(HALF, G, D.higher(1)).ending()                                        // G resolve
                .build(end());
    }

    // ── Bass: driving picked eighth-note patterns ────────────────────

    private Track bass() {
        var m1 = bassMain1();
        var th = bassTianHei();
        var ch = bassChorus();
        return Track.of("Bass", ELECTRIC_BASS_PICK, List.of(
                bassPre(), m1, th, m1, th,
                ch, bassBridge(), ch, ch,
                bassEnding(), th));
    }

    // Pre: rest then a simple entry
    private MelodicPhrase bassPre() {
        return b()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                // Last bar: G half + ending() pad 32sf (for elision into bassMain1's 48sf lead)
                .bar().o2(HALF, G).ending()
                .build(elision());
    }

    // Main1: driving eighth-note root patterns — G Am C G | G Am D G
    private MelodicPhrase bassMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A)                 // Am
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o3(C).o3(C).o3(G).o3(C)                 // C
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A)                 // Am
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D)                 // D
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .build(attacca());
    }

    // TianHeiHei: half-note roots, more space
    private MelodicPhrase bassTianHei() {
        return b()
                .bar().o2(HALF, G).o3(HALF, D)                                                 // G
                .bar().o2(HALF, G).o3(HALF, D)                                                 // Gm
                .bar().o3(HALF, C).o3(HALF, G)                                                 // Cm
                .bar().o2(HALF, G).o3(HALF, D)                                                 // G
                .bar().o3(HALF, C).o3(HALF, G)                                                 // C
                .bar().o3(HALF, D).o3(HALF, A)                                                 // D
                .bar().o2(HALF, G).ending()                                                     // G resolve
                .build(elision());
    }

    // Chorus: driving eighths — D G D G C Am D G
    private MelodicPhrase bassChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D)                 // D
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D)                 // D
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o3(C).o3(C).o3(G).o3(C)                 // C
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A)                 // Am
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D)                 // D
                .bar().o2(HALF, G).ending()                                                     // G
                .build(elision());
    }

    // Bridge: mix of eighths and halves — G Am C+D G Em Am C D
    private MelodicPhrase bassBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A)                 // Am
                .bar().o3(HALF, C).o3(HALF, D)                                                 // C → D
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o2(E).o2(E).o2(B).o2(B).o2(E).o2(E).o2(B).o2(E)                 // Em
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A)                 // Am
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o3(C).o3(C).o3(G).o3(C)                 // C
                // D: shorten so trailing pad ≥ 24 (for elision into chorus's 40sf leading pad)
                .bar().o3(HALF, D).ending()                                                     // D
                .build(elision());
    }

    // Ending: eighths building then resolving
    private MelodicPhrase bassEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A)                 // Am
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o3(C).o3(C).o3(G).o3(C)                 // C
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A)                 // Am
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D)                 // D
                .bar().o3(HALF, C).o2(HALF, G)                                                 // C → G
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D)                 // D
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G)                 // G
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D)                 // D
                .bar().o2(HALF, G).ending()                                                     // G
                .build(end());
    }

    // ── Rock Organ: atmospheric sustained chords ─────────────────────

    private Track organ() {
        var m1 = organMain1();
        var th = organTianHei();
        var ch = organChorus();
        return Track.of("Organ", ROCK_ORGAN, List.of(
                organPre(), m1, th, m1, th,
                ch, organBridge(), ch, ch,
                organEnding(), th));
    }

    // Pre: silent — organ enters with the band
    private MelodicPhrase organPre() {
        return b()
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE)
                // Last bar: HALF rest + ending pad 32 (for elision into organMain1's 48sf lead)
                .bar().r(HALF).ending()
                .build(elision());
    }

    // Main1: whole-note sustained pads — G Am C G | G Am D G
    private MelodicPhrase organMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o3(WHOLE, C, E, G)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o3(WHOLE, D, F, A)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .build(attacca());
    }

    // TianHeiHei: whole-note pads with modal colors
    private MelodicPhrase organTianHei() {
        return b()
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, G, B.f(), D.higher(1))
                .bar().o3(WHOLE, C, E.f(), G)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, C, E, G)
                .bar().o3(WHOLE, D, F, A)
                .bar().o3(HALF, G, B, D.higher(1)).ending()
                .build(elision());
    }

    // Chorus: whole-note pads — D G D G C Am D G
    private MelodicPhrase organChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o3(WHOLE, D, F, A)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, D, F, A)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, C, E, G)
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o3(WHOLE, D, F, A)
                // Last bar: HALF so trailing pad = 32 (for elision into next 40/48sf pickup)
                .bar().o3(HALF, G, B, D.higher(1)).ending()
                .build(elision());
    }

    // Bridge: whole-note pads — G Am C+D G Em Am C D
    private MelodicPhrase organBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, C, E, G).o3(HALF, D, F, A)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, E, G, B)
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o3(WHOLE, C, E, G)
                // Last bar: HALF so trailing pad = 32 (for elision into chorus's 40sf pickup)
                .bar().o3(HALF, D, F, A).ending()
                .build(elision());
    }

    // Ending: whole-note pads
    private MelodicPhrase organEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o3(WHOLE, C, E, G)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o3(WHOLE, D, F, A)
                .bar().o3(HALF, C, E, G).o3(HALF, G, B, D.higher(1))
                .bar().o3(WHOLE, D, F, A)
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, D, F, A)
                .bar().o3(HALF, G, B, D.higher(1)).ending()
                .build(end());
    }

    // ── Drums: anthemic U2 patterns ──────────────────────────────────

    private Track drums() {
        return Track.of("Drums", DRUM_KIT, List.of(
                drumsPre(), drumsMain1(), drumsTianHei(),
                drumsMain1(), drumsTianHei(),
                drumsChorus(), drumsBridge(), drumsChorus(), drumsChorus(),
                drumsEnding(), drumsTianHei()));
    }

    // Pre: minimal — just hi-hat ticking
    private DrumPhrase drumsPre() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.P));
        for (int i = 0; i < 4; i++) tickBar(n);
        return new DrumPhrase(n, elision());
    }

    // Main1: half-time feel — kick on 1, snare on 3, open hi-hat eighths
    //        Pickup bar: 48sf leading padding + 16sf of rests = 64sf, matching
    //        the melodic Main1's pickup shape. Then 8 bars of content = 9 bars total.
    private DrumPhrase drumsMain1() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MP));
        n.add(new PaddingNode(HALF.dot()));   // 48sf leading silence (pickup bar)
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        for (int i = 0; i < 7; i++) halfTimeBar(n);
        halfTimeFill(n);
        return new DrumPhrase(n, attacca());
    }

    // TianHeiHei: atmospheric open hi-hat quarters with kick (7 bars)
    private DrumPhrase drumsTianHei() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MP));
        for (int i = 0; i < 6; i++) atmosphericBar(n);
        // bar 7: resolve
        n.add(d(BASS_DRUM, QUARTER));
        n.add(d(OPEN_HI_HAT, QUARTER));
        n.add(new RestNode(HALF));
        return new DrumPhrase(n, elision());
    }

    // Chorus: full anthemic backbeat — kick on 1&3, snare on 2&4, crash
    //        Pickup bar: 40sf leading padding + 3×EIGHTH (2 rests + crash) = 64sf.
    //        Then 8 bars = 9 bars total, matching melodic Chorus.
    private DrumPhrase drumsChorus() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.F));
        n.add(new PaddingNode(Duration.ofSixtyFourths(40)));  // 40sf leading (HALF + EIGHTH)
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        n.add(d(CRASH_CYMBAL, EIGHTH));
        for (int i = 0; i < 7; i++) anthemBar(n);
        anthemFill(n);
        return new DrumPhrase(n, elision());
    }

    // Bridge: ride-based half-time. Pickup bar = 48sf padding + 16sf rests = 64sf.
    //        Then 8 bars = 9 bars total, matching melodic Bridge.
    private DrumPhrase drumsBridge() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MF));
        n.add(new PaddingNode(HALF.dot()));   // 48sf leading silence (pickup bar)
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        for (int i = 0; i < 7; i++) rideHalfTime(n);
        halfTimeFill(n);
        return new DrumPhrase(n, elision());
    }

    // Ending: build from half-time to anthemic then resolve.
    //        Pickup bar = 48sf padding + 16sf rests = 64sf. Then 12 bars = 13 bars total.
    private DrumPhrase drumsEnding() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MF));
        n.add(new PaddingNode(HALF.dot()));   // 48sf leading silence (pickup bar)
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        for (int i = 0; i < 4; i++) halfTimeBar(n);          // bars 1–4: half-time
        for (int i = 0; i < 4; i++) anthemBar(n);             // bars 5–8: anthemic
        for (int i = 0; i < 3; i++) rideHalfTime(n);          // bars 9–11: pulling back
        // bar 12: final crash
        n.add(d(CRASH_CYMBAL, EIGHTH));
        n.add(d(BASS_DRUM, EIGHTH));
        n.add(d(ACOUSTIC_SNARE, QUARTER));
        n.add(new RestNode(HALF));
        return new DrumPhrase(n, end());
    }

    // ── Drum patterns ────────────────────────────────────────────────

    /** Tick: just hi-hat quarters — sparse intro. */
    private static void tickBar(List<PhraseNode> out) {
        out.add(d(CLOSED_HI_HAT, QUARTER));
        out.add(d(CLOSED_HI_HAT, QUARTER));
        out.add(d(CLOSED_HI_HAT, QUARTER));
        out.add(d(CLOSED_HI_HAT, QUARTER));
    }

    /** Half-time: kick on 1, snare on 3, open hi-hat eighths — U2 verse feel. */
    private static void halfTimeBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
    }

    /** Atmospheric: kick on 1, open hi-hat quarters — TianHeiHei sections. */
    private static void atmosphericBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, QUARTER));
        out.add(d(OPEN_HI_HAT, QUARTER));
        out.add(d(OPEN_HI_HAT, QUARTER));
        out.add(d(OPEN_HI_HAT, QUARTER));
    }

    /** Anthemic: kick 1&3, snare 2&4, crash eighths — U2 chorus explosion. */
    private static void anthemBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    /** Ride half-time: kick on 1, snare on 3, ride quarters. */
    private static void rideHalfTime(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
    }

    /** Half-time fill: snare buildup into crash. */
    private static void halfTimeFill(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(HIGH_TOM, EIGHTH));
        out.add(d(HIGH_MID_TOM, EIGHTH));
        out.add(d(LOW_TOM, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    /** Anthemic fill: tom cascade into crash. */
    private static void anthemFill(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(HIGH_TOM, EIGHTH));
        out.add(d(HIGH_MID_TOM, EIGHTH));
        out.add(d(LOW_TOM, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new U2RockTianHeiHei());
    }
}
