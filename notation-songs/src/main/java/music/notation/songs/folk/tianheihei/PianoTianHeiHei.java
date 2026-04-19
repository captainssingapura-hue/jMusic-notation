package music.notation.songs.folk.tianheihei;

import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * 天黑黑 (Tian Hei Hei) — Piano arrangement in G major.
 */
public final class PianoTianHeiHei implements PieceContentProvider<TianHeiHei> {

    static final KeySignature KEY = new KeySignature(G, Mode.MAJOR);
    static final TimeSignature TS = new TimeSignature(4, 4);

    private StaffPhraseBuilder newBuilder(){
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    @Override
    public Piece create() {
        final var id = new TianHeiHei();
        return new Piece(id.title(), id.composer(),
                KEY, TS,
                new Tempo(120, QUARTER),
                List.of(melody(), harmony()));
    }

    private Track melody() {
        final var mainMelody1 = buildMelodyMain1();
        final var tianHei1 = buildMelodyTianHeiHei1();
        final var chorus1 = buildMelodyMain2();
        final var ending = buildEnding();
        var phrases = List.<Phrase>of(
                buildMelodyPre(),
                mainMelody1, tianHei1, mainMelody1, tianHei1,
                chorus1, buildBridge(), chorus1,
                overrideMelodyMain2(),
                ending, tianHei1
        );
        return Track.of("Piano", ACOUSTIC_GRAND_PIANO, phrases);
    }


    MelodicPhrase buildMelodyPre() {
        return newBuilder()
                .bar().o2(G,D.higher(1)).o3(G,A).o2(G,D.higher(1)).o3(G,A)
                .bar().o2(G,D.higher(1)).o3(G,A).o2(HALF,G,D.higher(1))
                .bar().o2(G,D.higher(1)).o3(G,A).o2(G,D.higher(1)).o3(G,A)
                .bar().o2(G,D.higher(1)).o3(HALF, G,A).ending()
                    //.aux().r(HALF).o2(HALF,G,D.higher(1))
                .build(elision());
    }

    MelodicPhrase buildMelodyMain1() {
        // TODO: fill in actual notes
        return newBuilder()
                .pickup(EIGHTH).o4(B).o5(C)
                .bar().o5(D).o5(D).o5(D).o4(EIGHTH,B).o5(EIGHTH,C)
                .bar(EIGHTH).o5(D).o5(E).o5(C).o4(B).o5(QUARTER,C).o4(A).o4(B)
                .bar(EIGHTH).o5(QUARTER,C).o5(QUARTER,C).o5(D).o5(C).o4(B).o4(A)
                .bar().o4(B).o4(QUARTER.dot(),D).r(EIGHTH).o4(EIGHTH,B).o5(EIGHTH,C)
                .bar().o5(D).o5(D).o5(D).o4(EIGHTH,B).o5(EIGHTH,C)
                .bar(EIGHTH).o5(D).o5(E).o5(C).o4(B).o5(QUARTER,C).o4(A).o4(B)
                .bar().o5(C).o4(EIGHTH,B).o5(EIGHTH,C).o5(D).o4(F)
                .bar().o4(A).o4(HALF.dot(),G) // hold G through the transition
                .build(attacca());
    }

    MelodicPhrase buildMelodyTianHeiHei1() {
        return newBuilder()
                .bar().o5(D).o5(C).o5(HALF,D) //天黑黑
                .bar().o5(D).o4(G).o4(HALF,B.f())
                .bar().o4(B.f()).o4(A).o4(HALF,G) // fill: resolve Bb down to G
                .bar().o5(D).o5(C).o5(HALF,D)
                .bar().r().o5(C).o5(HALF,D)
                .bar().o4(G).o4(A).o4(QUARTER.dot(),B).o4(EIGHTH,A).slurStart() // fill: ascending preparation
                .bar().o4(EIGHTH,A).o4(EIGHTH.dot(),G).slurEnd().ending()
                .build(elision());
    }

    MelodicPhrase buildBridge(){
        return newBuilder()
                .pickup(EIGHTH).o4(B).o5(C)
                .bar(EIGHTH).o5(QUARTER.dot(),D).o4(SIXTEENTH,B).o5(SIXTEENTH,C).o5(D).o5(G).o4(D).o4(B)
                .bar(EIGHTH).o5(D).o5(E).o5(SIXTEENTH,C).o5(SIXTEENTH,D).o5(SIXTEENTH,C).o4(SIXTEENTH,B).o5(QUARTER,C).o4(A).o4(B)
                .bar(EIGHTH).o5(QUARTER,C).o4(SIXTEENTH,A).o5(SIXTEENTH,D).o5(SIXTEENTH,F).o5(SIXTEENTH,A).o6(D).o6(C).o5(B).o5(A)
                .bar(EIGHTH).o5(QUARTER.dot(),B).o4(SIXTEENTH,B).o5(SIXTEENTH,C).o4(A).o5(D).o5(D).o5(F)
                .bar(EIGHTH).o4(HALF,B,G.higher(1)).o4(B.lower(1),G).o4(D,B).o4(D,B).o4(G,D.higher(1))
                .bar(EIGHTH).o4(G,D.higher(1)).o4(G,E.higher(1)).o4(E,C.higher(1)).o4(D,B).o4(HALF,E,C.higher(1))
                .bar(EIGHTH).o4(QUARTER,A).o3(EIGHTH,A).o4(A).o4(HALF,C,G)
                .bar(QUARTER).o4(A.lower(1),D,F).o4(G.lower(1),C,E).o3(EIGHTH,F,A,D.higher(1)).ending()
                .build(elision());
    }

    MelodicPhrase buildMelodyMain2(){
        return newBuilder()
                .pickup(EIGHTH).o5(D).o5(A).o5(G)
                .bar(EIGHTH).o5(F).o5(G).o5(A).o5(G).o5(F).o5(D).o4(B).o4(A)
                .bar(EIGHTH).o4(QUARTER,B).o5(QUARTER.dot(),D).o5(D).o5(A).o5(G)
                .bar(EIGHTH).o5(F).o5(G).o5(A).o5(G).o5(F).o5(D).o4(B).o4(A)
                .bar(EIGHTH).o4(QUARTER,A).o5(QUARTER.dot(),G).o5(D).o5(D).o5(E)
                .bar(EIGHTH).o5(QUARTER,G).o5(E).o5(QUARTER.dot(),G).o5(G).o5(E)
                .bar(EIGHTH).o5(QUARTER,G).o5(E).o5(QUARTER,G).o5(D).o5(D).o5(E)
                .bar(EIGHTH).o5(A).o5(G).o5(A).o5(G).o5(A).o5(QUARTER,G).o5(A).slurStart()
                    .aux().o5(HALF,C).o4(QUARTER,G).o5(QUARTER,C)
                .bar(EIGHTH).o5(A).slurEnd().o5(G).o5(B).o5(A).ending()
                .build(elision());
    }

    Phrase overrideMelodyMain2(){
        return OverlayBuilder.over(buildMelodyMain2(), KEY, TS, QUARTER)
                .endingAt(8, b -> b.o5(EIGHTH,A).slurEnd().o5(G).o5(QUARTER.dot(),A))
                .build(elision());
    }

    MelodicPhrase buildEnding(){
        return newBuilder()
                .pickup(EIGHTH).o4(B).o5(C)
                .bar().o5(D).o5(D).o5(D).o4(EIGHTH,B).o5(EIGHTH,C)
                .bar(EIGHTH).o5(D).o5(E).o5(C).o4(B).o5(QUARTER,C).o4(A).o4(B)
                .bar(EIGHTH).o5(QUARTER,C).o5(QUARTER,C).o5(D).o5(C).o4(B).o4(A)
                .bar().o4(B).o4(HALF,D).o4(EIGHTH,B).o5(EIGHTH,C)
                .bar(EIGHTH).o5(D).o5(QUARTER,D).o5(D).o5(G).o5(QUARTER,D).o5(E)
                .bar(EIGHTH).o5(D).o5(C).o5(C).o4(B).o5(QUARTER,C).o4(A).o4(B)
                .bar().o5(G.lower(1),C).o4(EIGHTH,B).o5(EIGHTH,C).o5(D).o4(F)
                .bar().o4(C,A).o4(HALF,G).o4(EIGHTH,A).o4(EIGHTH,B)
                .bar().o5(G.lower(1),C).o4(EIGHTH,B).o5(EIGHTH,C).o5(D).o4(F)
                .bar().o4(A).o4(G).o5(D,G).o4(EIGHTH,A).o4(EIGHTH,B)
                .bar().o5(G.lower(1),C).o4(EIGHTH,B).o5(EIGHTH,C).o5(F.lower(1),D).o4(F)
                .bar().o4(HALF,G).ending()
                .build(end());
    }



    // ══════════════════════════════════════════════════════════════════════════
    //  Harmony (Left Hand) — Chopin nocturne-style: QUARTER bass + 6 EIGHTHS
    //  arpeggio per bar (16 + 48 = 64sf). Absorbs the former ChopinTianHeiHei
    //  arrangement; use the BPM slider for nocturne tempo (~88 BPM).
    // ══════════════════════════════════════════════════════════════════════════

    private Track harmony() {
        final var harmMain1 = buildHarmonyMain1();
        final var harmTianHei = buildHarmonyTianHei();
        final var harmChorus = buildHarmonyChorus();
        final var harmEnding = buildHarmonyEnding();
        var phrases = List.<Phrase>of(
                buildHarmonyPre(),
                harmMain1, harmTianHei, harmMain1, harmTianHei,
                harmChorus, buildHarmonyBridge(), harmChorus,
                overrideHarmonyChorus(),
                harmEnding, harmTianHei
        );
        return Track.of("Harmony", ACOUSTIC_GRAND_PIANO, phrases);
    }

    /** Pre: 4 bars, sparse start easing into the arpeggio shape. Trailing pad = 16. */
    MelodicPhrase buildHarmonyPre() {
        return newBuilder()
                .bar().o2(HALF, G).o3(HALF, G)                                                  // G sustained
                .bar().o2(HALF, G).o3(HALF, D.higher(1))                                        // G with high D
                .bar(EIGHTH).o2(G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G).o3(D)                    // full arpeggio wave
                .bar().o2(QUARTER, G).o3(HALF, B).ending()                                      // G resolve, 48 + pad 16
                .build(elision());
    }

    /** Main1: pickup + 8 bars. G-Am-C-G | G-Am-D-G progression. */
    MelodicPhrase buildHarmonyMain1() {
        return newBuilder()
                .pickup(EIGHTH).o2(G).o3(D)                                                     // 16sf audible → 48 leading
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G)                 // G
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o3(A).o4(C).o4(E).o4(C).o3(A)                 // Am
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E).o4(G).o4(E).o4(C)                 // C
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G)                 // G
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(B).o4(D).o4(G).o4(D).o3(B)                 // G (wider)
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E).o4(C)                 // Am (wider)
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F).o4(D)                 // D major (F# from key sig = the V chord)
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G)                 // G
                .build(attacca());
    }

    /** TianHei: 7 bars. G-Gm-Cm-G-C-D-G(resolve). Trailing pad 44 (big, fits 40+ leading). */
    MelodicPhrase buildHarmonyTianHei() {
        return newBuilder()
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G)                 // G
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B.f()).o4(D).o3(B.f()).o3(G)         // Gm
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E.f()).o4(G).o4(E.f()).o4(C)         // Cm
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G)                 // G
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E).o4(G).o4(E).o4(C)                 // C
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F).o4(D)         // D
                .bar().o2(EIGHTH, G).o3(EIGHTH.dot(), D, B).ending()                            // G resolve, 20 + pad 44
                .build(elision());
    }

    /** Chorus (= melody Main2): pickup (3 eighths, 40sf leading) + 8 bars. D-G-D-G-C-Am-D-G. */
    MelodicPhrase buildHarmonyChorus() {
        return newBuilder()
                .pickup(EIGHTH).o3(D).o3(A).o4(D)                                                // 24sf audible → 40 leading
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F).o4(D)          // D
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G)                  // G
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F).o4(D)          // D
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G)                  // G
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E).o4(G).o5(C).o4(G)                  // C (soaring)
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E).o4(C)                  // Am
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F).o4(D)          // D
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).ending()                           // G resolve, 40 + pad 24
                .build(elision());
    }

    /** Bridge: pickup + 8 bars. G-Am-C→D-G-Em-Am-C-D. Trailing pad 24 (for Chorus 40 leading). */
    MelodicPhrase buildHarmonyBridge() {
        return newBuilder()
                .pickup(EIGHTH).o2(G).o3(D)                                                      // 48 leading
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(B).o4(D).o4(G).o4(B).o4(G)                  // G
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E).o4(C)                  // Am
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(E).o2(QUARTER, D).o3(A).o4(F)           // C→D (naturally 64sf)
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G)                  // G
                .bar(EIGHTH).o2(QUARTER, E).o3(B).o4(E).o4(G).o4(B).o4(G).o4(E)                  // Em
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E).o4(C)                  // Am
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E).o4(G).o4(E).o4(C)                  // C
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).ending()                       // D, 40 + pad 24
                .build(elision());
    }

    /** Override bar 8 of Chorus for the second iteration — alt ending leading into buildEnding. */
    Phrase overrideHarmonyChorus() {
        return OverlayBuilder.over(buildHarmonyChorus(), KEY, TS, QUARTER)
                .endingAt(8, EIGHTH, bar -> bar.o2(G).o3(D).o3(QUARTER.dot(), B))                // 40 + pad 24
                .build(elision());
    }

    /** Ending: pickup + 12 bars, passionate arpeggios winding down to a final G chord. */
    MelodicPhrase buildHarmonyEnding() {
        return newBuilder()
                .pickup(EIGHTH).o2(G).o3(D)                                                      // 48 leading
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(B).o4(D).o4(G).o4(D).o3(B)                  // G
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E).o4(C)                  // Am
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E).o4(G).o4(E).o4(C)                  // C
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(B).o4(D).o4(G).o4(D).o3(B)                  // G
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o4(G).o4(D)                  // G (soaring)
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E).o4(C)                  // Am
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F).o4(D)          // D
                .bar(EIGHTH).o3(C).o3(G).o4(E).o4(G).o2(G).o3(D).o3(B).o4(D)                     // C→G (all eighths)
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F).o4(D)          // D
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G)                  // G (winding down)
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F).o4(D)          // D
                .bar().o2(HALF, G).o3(HALF, D, G, B)                                             // G final
                .build(end());
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new PianoTianHeiHei());
    }
}
