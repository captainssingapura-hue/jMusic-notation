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
 * 天黑黑 (Tian Hei Hei) — Chopin Nocturne-style piano arrangement.
 *
 * <p>Features a slow, expressive tempo with wide-spanning nocturne-style
 * left hand arpeggios: a low bass note followed by a flowing wave of
 * chord tones that rise and fall. The right hand reuses the melody
 * from {@link PianoTianHeiHei}.</p>
 */
public final class ChopinTianHeiHei implements PieceContentProvider<TianHeiHei> {

    private static final KeySignature KEY = PianoTianHeiHei.KEY;
    private static final TimeSignature TS = PianoTianHeiHei.TS;
    private final PianoTianHeiHei piano = new PianoTianHeiHei();

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Chopin Nocturne"; }

    @Override
    public Piece create() {
        var id = new TianHeiHei();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(88, QUARTER),
                List.of(melody(), leftHand()));
    }

    // ── Right Hand: reuses melody from PianoTianHeiHei ───────────────

    private Track melody() {
        var m1 = piano.buildMelodyMain1();
        var th = piano.buildMelodyTianHeiHei1();
        var ch = piano.buildMelodyMain2();
        var ending = piano.buildEnding();
        return Track.of("Right Hand", ACOUSTIC_GRAND_PIANO, List.of(
                piano.buildMelodyPre(), m1, th, m1, th,
                ch, piano.buildBridge(), ch,
                piano.overrideMelodyMain2(),
                ending, th));
    }

    // ── Left Hand: wide nocturne-style arpeggios ─────────────────────
    // Pattern per bar: bass QUARTER + 6 eighths = 16 + 48 = 64 sixty-fourths

    private Track leftHand() {
        var m1 = lhMain1();
        var th = lhTianHei();
        var ch = lhChorus();
        var ending = lhEnding();
        return Track.of("Left Hand", ACOUSTIC_GRAND_PIANO, List.of(
                lhPre(), m1, th, m1, th,
                ch, lhBridge(), ch,
                lhOverrideChorus(),
                ending, th));
    }

    // Pre: sparse pedal tones, gradually introducing the arpeggio shape
    private MelodicPhrase lhPre() {
        return b()
                .bar().o2(HALF, G).o2(HALF, D.higher(1))
                .bar().o2(HALF, G).o3(HALF, G)
                .bar(EIGHTH).o2(G).o3(D).o3(G).o3(B).o4(D).o3(B).o3(G).o3(D)
                .bar(EIGHTH).o2(G).o3(D).o3(G).o3(HALF, B).r(QUARTER)
                .build(elision());
    }

    // Main1: classic nocturne — low bass quarter then flowing eighths up and back
    // Chord progression: G | Am | C | G | G | Am | D | G
    private MelodicPhrase lhMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B)                // G
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o3(A).o4(C).o4(E).o4(C)                // Am
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E).o4(G).o4(E)                // C
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B)                // G
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(B).o4(D).o4(G).o4(D)                // G (variation)
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E)                // Am (wider)
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F)                // D
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B)                // G
                .build(attacca());
    }

    // TianHeiHei: nocturne arpeggios with modal colors
    // G | Gm | Cm | G | C | D | G(ending)
    private MelodicPhrase lhTianHei() {
        return b()
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B)                // G
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B.f()).o4(D).o3(B.f())         // Gm
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E.f()).o4(G).o4(E.f())         // Cm
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B)                // G
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E).o4(G).o4(E)                // C
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F)                // D
                .bar().o2(EIGHTH, G).o3(EIGHTH.dot(), D, B).ending()                       // G resolve
                .build(elision());
    }

    // Chorus: more passionate, wider arpeggios with richer voicings
    // D | G | D | G | C | Am | D | G
    private MelodicPhrase lhChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F)                // D
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(B).o4(D).o4(G).o4(D)                // G
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(D)                // D (variant)
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B)                // G
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E).o4(G).o5(C)                // C (soaring)
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E)                // Am
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F)                // D
                .bar(EIGHTH).o2(G).o3(D).o3(B).o4(D).ending()                              // G
                .build(elision());
    }

    // Bridge: varied patterns — some bars with wider leaps
    // G | Am | C→D | G | Em | Am | C | D
    private MelodicPhrase lhBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(B).o4(D).o4(G).o4(B)                // G (ascending)
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E)                // Am
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(E).o2(QUARTER, D).o3(A).o4(F)       // C→D
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B)                // G
                .bar(EIGHTH).o2(QUARTER, E).o3(B).o4(E).o4(G).o4(B).o4(G)                // Em
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E)                // Am
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E).o4(G).o4(E)                // C
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F.n()).o3(QUARTER, A).ending() // D
                .build(elision());
    }

    // Override chorus bar 8 — slightly different ending
    private Phrase lhOverrideChorus() {
        return OverlayBuilder.over(lhChorus(), KEY, TS, QUARTER)
                .endingAt(8, EIGHTH, bl -> bl.o2(G).o3(D).o3(QUARTER.dot(), B))
                .build(elision());
    }

    // Ending: passionate arpeggios winding down
    // G | Am | C | G | G | Am | D | C→G | D | G | D | G
    private MelodicPhrase lhEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(B).o4(D).o4(G).o4(D)                // G
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E)                // Am
                .bar(EIGHTH).o2(QUARTER, C).o3(G).o4(C).o4(E).o4(G).o4(E)                // C
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(B).o4(D).o4(G).o4(D)                // G
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o4(G)                // G (soaring)
                .bar(EIGHTH).o2(QUARTER, A).o3(E).o4(C).o4(E).o4(A).o4(E)                // Am
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F)                // D
                .bar(EIGHTH).o3(C).o3(G).o4(E).o4(G).o2(G).o3(D).o3(B).o4(D)             // C→G (all eighths)
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F)                // D
                .bar(EIGHTH).o2(QUARTER, G).o3(D).o3(G).o3(B).o4(D).o3(B)                // G (winding down)
                .bar(EIGHTH).o2(QUARTER, D).o3(A).o4(D).o4(F).o4(A).o4(F)                // D
                .bar().o2(HALF, G).o3(HALF, D, G, B)                                       // G final
                .build(end());
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ChopinTianHeiHei());
    }
}
