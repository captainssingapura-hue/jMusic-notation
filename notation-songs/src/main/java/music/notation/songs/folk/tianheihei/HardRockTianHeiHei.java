package music.notation.songs.folk.tianheihei;

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
 * 天黑黑 (Tian Hei Hei) — Hard Rock arrangement.
 *
 * <p>Features heavy overdriven rhythm guitar with palm-muted chugging,
 * distortion lead guitar with sustained power chords and melodic fills,
 * aggressive picked bass, and hard-hitting drums with double-kick patterns.</p>
 */
public final class HardRockTianHeiHei implements PieceContentProvider<TianHeiHei> {

    private static final KeySignature KEY = PianoTianHeiHei.KEY;
    private static final TimeSignature TS = PianoTianHeiHei.TS;
    private final PianoTianHeiHei piano = new PianoTianHeiHei();

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Hard Rock"; }

    @Override
    public Piece create() {
        var id = new TianHeiHei();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(126, QUARTER),
                List.of(lead(), pianoHarmony(), rhythmGuitar(), leadGuitar(), bass(), drums()));
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

    // ── Piano: Acoustic Grand ────────────────────────────────────────

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

    // ── Rhythm Guitar: overdriven palm-muted chugging ────────────────

    private Track rhythmGuitar() {
        var m1 = rgMain1();
        var th = rgTianHei();
        var ch = rgChorus();
        return Track.of("Rhythm Guitar", OVERDRIVEN_GUITAR, List.of(
                rgPre(), m1, th, m1, th,
                ch, rgBridge(), ch, ch,
                rgEnding(), th));
    }

    // Pre: building tension — rest then power chord hits
    private MelodicPhrase rgPre() {
        return b()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().o3(QUARTER, G, D.higher(1)).r(QUARTER).o3(QUARTER, G, D.higher(1)).r(QUARTER)
                .bar().o3(QUARTER, G, D.higher(1)).o3(QUARTER, G, D.higher(1)).o3(HALF, G, D.higher(1))
                .build(elision());
    }

    // Main1: eighth-note palm-muted chugs on power chords — G Am C G | G Am D G
    private MelodicPhrase rgMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                              .o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                .bar(EIGHTH).o3(C, G).o3(C, G).o3(C, G).o3(C, G)
                              .o3(C, G).o3(C, G).o3(C, G).o3(C, G)
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                              .o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                .bar(EIGHTH).o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                              .o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .build(attacca());
    }

    // TianHeiHei: sustained power chords — G Gm Cm G C D → G
    private MelodicPhrase rgTianHei() {
        return b()
                .bar().o3(WHOLE, G, D.higher(1))
                .bar().o3(WHOLE, G, D.higher(1))
                .bar().o3(WHOLE, C, G)
                .bar().o3(WHOLE, G, D.higher(1))
                .bar().o3(WHOLE, C, G)
                .bar().o3(WHOLE, D, A)
                .bar().o3(HALF, G, D.higher(1)).ending()
                .build(elision());
    }

    // Chorus: heavy eighth-note chugs — D G D G C Am D G
    private MelodicPhrase rgChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                              .o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                              .o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(C, G).o3(C, G).o3(C, G).o3(C, G)
                              .o3(C, G).o3(C, G).o3(C, G).o3(C, G)
                .bar(EIGHTH).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                              .o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                .bar(EIGHTH).o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                              .o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                .bar().o3(HALF, G, D.higher(1)).ending()
                .build(elision());
    }

    // Bridge: alternating chugs and sustained — G Am C+D G Em Am C D
    private MelodicPhrase rgBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                              .o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                .bar().o3(HALF, C, G).o3(HALF, D, A)
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(E, B).o3(E, B).o3(E, B).o3(E, B)
                              .o3(E, B).o3(E, B).o3(E, B).o3(E, B)
                .bar(EIGHTH).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                              .o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                .bar(EIGHTH).o3(C, G).o3(C, G).o3(C, G).o3(C, G)
                              .o3(C, G).o3(C, G).o3(C, G).o3(C, G)
                .bar().o3(HALF, D, A).o3(D, A).ending()
                .build(elision());
    }

    // Ending: chugging then resolving
    private MelodicPhrase rgEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                              .o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                .bar(EIGHTH).o3(C, G).o3(C, G).o3(C, G).o3(C, G)
                              .o3(C, G).o3(C, G).o3(C, G).o3(C, G)
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                              .o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1)).o3(A, E.higher(1))
                .bar(EIGHTH).o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                              .o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                .bar().o3(HALF, C, G).o3(HALF, G, D.higher(1))
                .bar(EIGHTH).o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                              .o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                .bar(EIGHTH).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                              .o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1)).o3(G, D.higher(1))
                .bar(EIGHTH).o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                              .o3(D, A).o3(D, A).o3(D, A).o3(D, A)
                .bar().o3(HALF, G, D.higher(1)).ending()
                .build(end());
    }

    // ── Lead Guitar: distortion, melodic fills and sustained chords ──

    private Track leadGuitar() {
        var m1 = lgMain1();
        var th = lgTianHei();
        var ch = lgChorus();
        return Track.of("Lead Guitar", DISTORTION_GUITAR, List.of(
                lgPre(), m1, th, m1, th,
                ch, lgBridge(), ch, ch,
                lgEnding(), th));
    }

    // Pre: feedback-style sustained note building
    private MelodicPhrase lgPre() {
        return b()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(HALF).o4(HALF, D)
                .bar().o4(WHOLE, G)
                .build(elision());
    }

    // Main1: sustained power chords with melodic tail — G Am C G | G Am D G
    private MelodicPhrase lgMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, B, D.higher(1))
                .bar().o4(HALF, A, E.higher(1)).o4(HALF, C.higher(1), E.higher(1))
                .bar().o4(HALF, C, G).o4(HALF, E, G)
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, B, D.higher(1))
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, B, D.higher(1))
                .bar().o4(HALF, A, E.higher(1)).o4(HALF, C.higher(1), E.higher(1))
                .bar().o4(HALF, D, A).o4(HALF, F, A)
                .bar().o4(WHOLE, G, D.higher(1))
                .build(attacca());
    }

    // TianHeiHei: singing sustained notes — melodic fills over rhythm
    private MelodicPhrase lgTianHei() {
        return b()
                .bar().o5(WHOLE, D)
                .bar().o5(D).o5(C).o4(HALF, B.f())
                .bar().o4(HALF, G).o4(HALF, E.f())
                .bar().o5(WHOLE, D)
                .bar().o5(C).o4(B).o4(HALF, G)
                .bar().o4(A).o4(B).o4(HALF, D)
                .bar().o4(HALF, G, D.higher(1)).ending()
                .build(elision());
    }

    // Chorus: aggressive power chord hits — D G D G C Am D G
    private MelodicPhrase lgChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(HALF, D, A).o4(HALF, F, A)
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, B, D.higher(1))
                .bar().o4(HALF, D, A).o4(HALF, F, A)
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, B, D.higher(1))
                .bar().o4(HALF, C, G).o4(HALF, E, G)
                .bar().o4(HALF, A, E.higher(1)).o4(HALF, C.higher(1), E.higher(1))
                .bar().o4(HALF, D, A).o4(HALF, F, A)
                .bar().o4(HALF, G, D.higher(1)).ending()
                .build(elision());
    }

    // Bridge: mix of sustained and melodic — G Am C+D G Em Am C D
    private MelodicPhrase lgBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(WHOLE, G, D.higher(1))
                .bar().o4(WHOLE, A, E.higher(1))
                .bar().o4(HALF, C, G).o4(HALF, D, A)
                .bar().o5(D).o5(C).o4(B).o4(G)
                .bar().o4(HALF, E, B).o4(HALF, G, B)
                .bar().o4(WHOLE, A, E.higher(1))
                .bar().o4(HALF, C, G).o4(HALF, E, G)
                .bar().o4(HALF, D, A).o4(D, A).ending()
                .build(elision());
    }

    // Ending: building intensity then final sustained
    private MelodicPhrase lgEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, B, D.higher(1))
                .bar().o4(HALF, A, E.higher(1)).o4(HALF, C.higher(1), E.higher(1))
                .bar().o4(HALF, C, G).o4(HALF, E, G)
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, B, D.higher(1))
                .bar().o4(HALF, G, D.higher(1)).o5(D).o5(G)
                .bar().o5(HALF, A).o5(HALF, E)
                .bar().o5(D).o5(C).o4(B).o4(A)
                .bar().o4(HALF, C, G).o4(HALF, G, D.higher(1))
                .bar().o5(D).o5(C).o4(B).o4(A)
                .bar().o4(WHOLE, G, D.higher(1))
                .bar().o5(D).o5(C).o4(B).o4(A)
                .bar().o4(HALF, G, D.higher(1)).ending()
                .build(end());
    }

    // ── Bass: aggressive picked eighth-note patterns ─────────────────

    private Track bass() {
        var m1 = bassMain1();
        var th = bassTianHei();
        var ch = bassChorus();
        return Track.of("Bass", ELECTRIC_BASS_PICK, List.of(
                bassPre(), m1, th, m1, th,
                ch, bassBridge(), ch, ch,
                bassEnding(), th));
    }

    // Pre: rest then pounding entry
    private MelodicPhrase bassPre() {
        return b()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().o2(QUARTER, G).r(QUARTER).o2(QUARTER, G).r(QUARTER)
                .bar().o2(QUARTER, G).o2(QUARTER, G).o2(HALF, G)
                .build(elision());
    }

    // Main1: aggressive eighth-note roots — G Am C G | G Am D G
    private MelodicPhrase bassMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o2(A).o2(A).o2(A).o3(E).o2(A)
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(C).o3(C).o3(C).o3(G).o3(C)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o2(A).o2(A).o2(A).o3(E).o2(A)
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(D).o3(D).o3(D).o3(A).o3(D)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .build(attacca());
    }

    // TianHeiHei: quarter-note roots with octave jumps
    private MelodicPhrase bassTianHei() {
        return b()
                .bar().o2(G).o3(D).o2(G).o3(D)
                .bar().o2(G).o3(D).o2(G).o3(D)
                .bar().o3(C).o3(G).o3(C).o3(G)
                .bar().o2(G).o3(D).o2(G).o3(D)
                .bar().o3(C).o3(G).o3(C).o3(G)
                .bar().o3(D).o3(A).o3(D).o3(A)
                .bar().o2(HALF, G).ending()
                .build(elision());
    }

    // Chorus: aggressive eighths — D G D G C Am D G
    private MelodicPhrase bassChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(D).o3(D).o3(D).o3(A).o3(D)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(D).o3(D).o3(D).o3(A).o3(D)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(C).o3(C).o3(C).o3(G).o3(C)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o2(A).o2(A).o2(A).o3(E).o2(A)
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(D).o3(D).o3(D).o3(A).o3(D)
                .bar().o2(HALF, G).ending()
                .build(elision());
    }

    // Bridge: mix of eighths and half-notes — G Am C+D G Em Am C D
    private MelodicPhrase bassBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o2(A).o2(A).o2(A).o3(E).o2(A)
                .bar().o3(HALF, C).o3(HALF, D)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o2(E).o2(E).o2(B).o2(E).o2(E).o2(E).o2(B).o2(E)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o2(A).o2(A).o2(A).o3(E).o2(A)
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(C).o3(C).o3(C).o3(G).o3(C)
                .bar().o3(HALF, D).o3(D).ending()
                .build(elision());
    }

    // Ending: aggressive eighths building then resolving
    private MelodicPhrase bassEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o2(A).o2(A).o2(A).o3(E).o2(A)
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(C).o3(C).o3(C).o3(G).o3(C)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o2(A).o2(A).o2(A).o3(E).o2(A)
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(D).o3(D).o3(D).o3(A).o3(D)
                .bar().o3(HALF, C).o2(HALF, G)
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(D).o3(D).o3(D).o3(A).o3(D)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o2(G).o2(G).o2(G).o3(D).o2(G)
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(D).o3(D).o3(D).o3(A).o3(D)
                .bar().o2(HALF, G).ending()
                .build(end());
    }

    // ── Drums: hard-hitting rock ─────────────────────────────────────

    private Track drums() {
        return Track.of("Drums", DRUM_KIT, List.of(
                drumsPre(), drumsMain1(), drumsTianHei(),
                drumsMain1(), drumsTianHei(),
                drumsChorus(), drumsBridge(), drumsChorus(), drumsChorus(),
                drumsEnding(), drumsTianHei()));
    }

    // Pre: building — kick hits matching guitar stabs
    private DrumPhrase drumsPre() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MP));
        // bars 1-2: rest
        for (int i = 0; i < 2; i++) {
            n.add(new RestNode(WHOLE));
        }
        // bar 3: kick hits with guitar
        n.add(d(BASS_DRUM, QUARTER));
        n.add(new RestNode(QUARTER));
        n.add(d(BASS_DRUM, QUARTER));
        n.add(new RestNode(QUARTER));
        // bar 4: building
        n.add(d(BASS_DRUM, QUARTER));
        n.add(d(BASS_DRUM, QUARTER));
        n.add(d(ACOUSTIC_SNARE, EIGHTH));
        n.add(d(ACOUSTIC_SNARE, EIGHTH));
        n.add(d(CRASH_CYMBAL, QUARTER));
        return new DrumPhrase(n, elision());
    }

    // Main1: hard rock beat — double kick, heavy snare (2-eighth pickup + 8 bars)
    private DrumPhrase drumsMain1() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MF));
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        for (int i = 0; i < 7; i++) hardRockBar(n);
        hardFill(n);
        return new DrumPhrase(n, attacca());
    }

    // TianHeiHei: half-time heavy — crash quarters with kick (7 bars)
    private DrumPhrase drumsTianHei() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 6; i++) heavyHalfTime(n);
        // bar 7: resolve with crash
        n.add(d(BASS_DRUM, EIGHTH));
        n.add(d(CRASH_CYMBAL, EIGHTH));
        n.add(d(ACOUSTIC_SNARE, QUARTER));
        n.add(new RestNode(HALF));
        return new DrumPhrase(n, elision());
    }

    // Chorus: full intensity — double kick, crash eighths (3-eighth pickup + 8 bars)
    private DrumPhrase drumsChorus() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.F));
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        n.add(d(CRASH_CYMBAL, EIGHTH));
        for (int i = 0; i < 7; i++) poundingBar(n);
        poundingFill(n);
        return new DrumPhrase(n, elision());
    }

    // Bridge: ride-based hard rock (2-eighth pickup + 8 bars)
    private DrumPhrase drumsBridge() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MF));
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        for (int i = 0; i < 7; i++) hardRockRide(n);
        hardFill(n);
        return new DrumPhrase(n, elision());
    }

    // Ending: build from hard rock to pounding then crash (2-eighth pickup + 12 bars)
    private DrumPhrase drumsEnding() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.F));
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        for (int i = 0; i < 4; i++) hardRockBar(n);           // bars 1–4: standard
        for (int i = 0; i < 4; i++) poundingBar(n);            // bars 5–8: pounding
        for (int i = 0; i < 3; i++) hardRockRide(n);           // bars 9–11: pulling back
        // bar 12: final crash
        n.add(d(CRASH_CYMBAL, EIGHTH));
        n.add(d(BASS_DRUM, EIGHTH));
        n.add(d(ACOUSTIC_SNARE, QUARTER));
        n.add(new RestNode(HALF));
        return new DrumPhrase(n, end());
    }

    // ── Drum patterns ────────────────────────────────────────────────

    /** Hard rock: kick-kick on 1, snare 2&4, hi-hat eighths. */
    private static void hardRockBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
    }

    /** Heavy half-time: crash on 1, snare on 3, open hi-hat. */
    private static void heavyHalfTime(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
    }

    /** Pounding: double kick, snare, crash — maximum intensity. */
    private static void poundingBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    /** Hard rock with ride: kick pattern with ride instead of hi-hat. */
    private static void hardRockRide(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
    }

    /** Hard fill: tom cascade with double kick. */
    private static void hardFill(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(HIGH_TOM, EIGHTH));
        out.add(d(HIGH_MID_TOM, EIGHTH));
        out.add(d(LOW_TOM, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    /** Pounding fill: snare rolls into crash. */
    private static void poundingFill(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new HardRockTianHeiHei());
    }
}
