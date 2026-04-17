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
 * 天黑黑 (Tian Hei Hei) — Soft Rock arrangement.
 *
 * <p>Reuses the piano melody and harmony from {@link PianoTianHeiHei},
 * adding acoustic guitar, electric bass, string pad, and drums.</p>
 */
public final class SoftRockTianHeiHei implements PieceContentProvider<TianHeiHei> {

    private static final KeySignature KEY = PianoTianHeiHei.KEY;
    private static final TimeSignature TS = PianoTianHeiHei.TS;
    private final PianoTianHeiHei piano = new PianoTianHeiHei();

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Soft Rock"; }

    @Override
    public Piece create() {
        var id = new TianHeiHei();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(108, QUARTER),
                List.of(lead(), pianoHarmony(), guitar(), bass(), strings(), drums()));
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

    // ── Acoustic Guitar: chord strumming ─────────────────────────────

    private Track guitar() {
        var m1 = guitarMain1();
        var th = guitarTianHei();
        var ch = guitarChorus();
        return Track.of("Guitar", ACOUSTIC_GUITAR_STEEL, List.of(
                guitarPre(), m1, th, m1, th,
                ch, guitarBridge(), ch, ch,
                guitarEnding(), th));
    }

    // Pre: gentle whole-note G chords
    private MelodicPhrase guitarPre() {
        return b()
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(HALF, G, B, D.higher(1)).r(HALF)
                .build(elision());
    }

    // Main1: half-note chord strums — G Am C G | G Am D G
    private MelodicPhrase guitarMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, C, E, G).o3(HALF, C, E, G)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, D, F, A).o3(HALF, D, F, A)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, G, B, D.higher(1))
                .build(attacca());
    }

    // TianHeiHei: whole-note sustained chords — G Gm Cm G C D → G
    private MelodicPhrase guitarTianHei() {
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

    // Chorus: quarter-note driving strums — D G D G C Am D G
    private MelodicPhrase guitarChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o3(D, F, A).o3(D, F, A).o3(D, F, A).o3(D, F, A)
                .bar().o3(G, B, D.higher(1)).o3(G, B, D.higher(1)).o3(G, B, D.higher(1)).o3(G, B, D.higher(1))
                .bar().o3(D, F, A).o3(D, F, A).o3(D, F, A).o3(D, F, A)
                .bar().o3(G, B, D.higher(1)).o3(G, B, D.higher(1)).o3(G, B, D.higher(1)).o3(G, B, D.higher(1))
                .bar().o3(C, E, G).o3(C, E, G).o3(C, E, G).o3(C, E, G)
                .bar().o3(A, C.higher(1), E.higher(1)).o3(A, C.higher(1), E.higher(1)).o3(A, C.higher(1), E.higher(1)).o3(A, C.higher(1), E.higher(1))
                .bar().o3(D, F, A).o3(D, F, A).o3(D, F, A).o3(D, F, A)
                .bar().o3(HALF, G, B, D.higher(1)).ending()
                .build(elision());
    }

    // Bridge: half-note chords — G Am C+D G Em Am C D
    private MelodicPhrase guitarBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, C, E, G).o3(HALF, D, F, A)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, E, G, B).o3(HALF, E, G, B)
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, C, E, G).o3(HALF, C, E, G)
                .bar().o3(HALF, D, F, A).o3(D, F, A).ending()
                .build(elision());
    }

    // Ending: half-note chords — G Am C G | G Am D C+G | D G D G
    private MelodicPhrase guitarEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, C, E, G).o3(HALF, C, E, G)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, D, F, A).o3(HALF, D, F, A)
                .bar().o3(HALF, C, E, G).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, D, F, A).o3(HALF, D, F, A)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, D, F, A).o3(HALF, D, F, A)
                .bar().o3(HALF, G, B, D.higher(1)).ending()
                .build(end());
    }

    // ── Electric Bass: root–fifth patterns ───────────────────────────

    private Track bass() {
        var m1 = bassMain1();
        var th = bassTianHei();
        var ch = bassChorus();
        return Track.of("Bass", ELECTRIC_BASS_FINGER, List.of(
                bassPre(), m1, th, m1, th,
                ch, bassBridge(), ch, ch,
                bassEnding(), th));
    }

    // Pre: rest, then gentle entry bars 3–4
    private MelodicPhrase bassPre() {
        return b()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o2(HALF, G).r(HALF)
                .build(elision());
    }

    // Main1: half-note root–fifth — G Am C G | G Am D G
    private MelodicPhrase bassMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o2(HALF, A).o3(HALF, E)
                .bar().o3(HALF, C).o3(HALF, G)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o2(HALF, A).o3(HALF, E)
                .bar().o3(HALF, D).o3(HALF, A)
                .bar().o2(HALF, G).o3(HALF, D)
                .build(attacca());
    }

    // TianHeiHei: half-note roots — G Gm Cm G C D → G
    private MelodicPhrase bassTianHei() {
        return b()
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o3(HALF, C).o3(HALF, G)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o3(HALF, C).o3(HALF, G)
                .bar().o3(HALF, D).o3(HALF, A)
                .bar().o2(HALF, G).ending()
                .build(elision());
    }

    // Chorus: quarter-note walking — D G D G C Am D G
    private MelodicPhrase bassChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o3(D).o3(A).o3(D).o3(A)
                .bar().o2(G).o3(D).o2(G).o3(D)
                .bar().o3(D).o3(A).o3(D).o3(A)
                .bar().o2(G).o3(D).o2(G).o3(D)
                .bar().o3(C).o3(G).o3(C).o3(G)
                .bar().o2(A).o3(E).o2(A).o3(E)
                .bar().o3(D).o3(A).o3(D).o3(A)
                .bar().o2(HALF, G).ending()
                .build(elision());
    }

    // Bridge: half-note root–fifth — G Am C+D G Em Am C D
    private MelodicPhrase bassBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o2(HALF, A).o3(HALF, E)
                .bar().o3(HALF, C).o3(HALF, D)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o2(HALF, E).o2(HALF, B)
                .bar().o2(HALF, A).o3(HALF, E)
                .bar().o3(HALF, C).o3(HALF, G)
                .bar().o3(HALF, D).o3(D).ending()
                .build(elision());
    }

    // Ending: half-note patterns — G Am C G | G Am D C+G | D G D G
    private MelodicPhrase bassEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o2(HALF, A).o3(HALF, E)
                .bar().o3(HALF, C).o3(HALF, G)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o2(HALF, A).o3(HALF, E)
                .bar().o3(HALF, D).o3(HALF, A)
                .bar().o3(HALF, C).o2(HALF, G)
                .bar().o3(HALF, D).o3(HALF, A)
                .bar().o2(HALF, G).o3(HALF, D)
                .bar().o3(HALF, D).o3(HALF, A)
                .bar().o2(HALF, G).ending()
                .build(end());
    }

    // ── Strings: sustained pad ───────────────────────────────────────

    private Track strings() {
        var m1 = stringsMain1();
        var th = stringsTianHei();
        var ch = stringsChorus();
        return Track.of("Strings", STRING_ENSEMBLE_1, List.of(
                stringsPre(), m1, th, m1, th,
                ch, stringsBridge(), ch, ch,
                stringsEnding(), th));
    }

    // Pre: rest — strings enter later
    private MelodicPhrase stringsPre() {
        return b()
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE)
                .build(elision());
    }

    // Main1: whole-note chords — G Am C G | G Am D G
    private MelodicPhrase stringsMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o4(WHOLE, C, E, G)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o4(WHOLE, D, F, A)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .build(attacca());
    }

    // TianHeiHei: whole-note chords — G Gm Cm G C D → G
    private MelodicPhrase stringsTianHei() {
        return b()
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, G, B.f(), D.higher(1))
                .bar().o4(WHOLE, C, E.f(), G)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, C, E, G)
                .bar().o4(WHOLE, D, F, A)
                .bar().o4(HALF, G, B, D.higher(1)).ending()
                .build(elision());
    }

    // Chorus: whole-note chords — D G D G C Am D G
    private MelodicPhrase stringsChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(WHOLE, D, F, A)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, D, F, A)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, C, E, G)
                .bar().o4(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o4(WHOLE, D, F, A)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .build(elision());
    }

    // Bridge: whole-note chords — G Am C+D G Em Am C D
    private MelodicPhrase stringsBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o4(HALF, C, E, G).o4(HALF, D, F, A)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, E, G, B)
                .bar().o4(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o4(WHOLE, C, E, G)
                .bar().o4(WHOLE, D, F, A)
                .build(elision());
    }

    // Ending: whole-note chords — G Am C G | G Am D C+G | D G D G
    private MelodicPhrase stringsEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o4(WHOLE, C, E, G)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o4(WHOLE, D, F, A)
                .bar().o4(HALF, C, E, G).o4(HALF, G, B, D.higher(1))
                .bar().o4(WHOLE, D, F, A)
                .bar().o4(WHOLE, G, B, D.higher(1))
                .bar().o4(WHOLE, D, F, A)
                .bar().o4(HALF, G, B, D.higher(1)).ending()
                .build(end());
    }

    // ── Drums: soft rock ─────────────────────────────────────────────

    private Track drums() {
        return Track.of("Drums", DRUM_KIT, List.of(
                drumsPre(), drumsMain1(), drumsTianHei(),
                drumsMain1(), drumsTianHei(),
                drumsChorus(), drumsBridge(), drumsChorus(), drumsChorus(),
                drumsEnding(), drumsTianHei()));
    }

    // Pre: rest — drums enter in verse
    private MelodicPhrase drumsPre() {
        return b()
                .bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE)
                .build(elision());
    }

    // Main1: soft ride pattern (2-eighth pickup + 8 bars)
    private DrumPhrase drumsMain1() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MP));
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        for (int i = 0; i < 7; i++) softBar(n);
        softFill(n);
        return new DrumPhrase(n, attacca());
    }

    // TianHeiHei: gentle ride quarters (7 bars)
    private DrumPhrase drumsTianHei() {
        var n = new ArrayList<PhraseNode>();
        for (int i = 0; i < 6; i++) rideOnly(n);
        // bar 7: resolve with crash
        n.add(d(RIDE_CYMBAL, QUARTER));
        n.add(d(RIDE_CYMBAL, QUARTER));
        n.add(new RestNode(HALF));
        return new DrumPhrase(n, elision());
    }

    // Chorus: full kick-snare-hihat (3-eighth pickup + 8 bars)
    private DrumPhrase drumsChorus() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MF));
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        n.add(d(CRASH_CYMBAL, EIGHTH));
        for (int i = 0; i < 7; i++) driveBar(n);
        tomFill(n);
        return new DrumPhrase(n, elision());
    }

    // Bridge: ride-based drive (2-eighth pickup + 8 bars)
    private DrumPhrase drumsBridge() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MP));
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        for (int i = 0; i < 7; i++) rideBar(n);
        softFill(n);
        return new DrumPhrase(n, elision());
    }

    // Ending: drive building then fading (2-eighth pickup + 12 bars)
    private DrumPhrase drumsEnding() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MF));
        n.add(new RestNode(EIGHTH));
        n.add(new RestNode(EIGHTH));
        for (int i = 0; i < 4; i++) softBar(n);          // bars 1–4: soft
        for (int i = 0; i < 4; i++) driveBar(n);          // bars 5–8: driving
        for (int i = 0; i < 3; i++) rideBar(n);           // bars 9–11: ride fade
        // bar 12: crash and sustain
        n.add(d(CRASH_CYMBAL, EIGHTH));
        n.add(d(BASS_DRUM, EIGHTH));
        n.add(d(ACOUSTIC_SNARE, QUARTER));
        n.add(new RestNode(HALF));
        return new DrumPhrase(n, end());
    }

    // ── Drum patterns ────────────────────────────────────────────────

    /** Soft verse: side stick on 2&4, ride on 1&3. */
    private static void softBar(List<PhraseNode> out) {
        out.add(d(RIDE_CYMBAL, QUARTER));
        out.add(d(SIDE_STICK, QUARTER));
        out.add(d(RIDE_CYMBAL, QUARTER));
        out.add(d(SIDE_STICK, QUARTER));
    }

    /** Ride quarter notes only — minimal. */
    private static void rideOnly(List<PhraseNode> out) {
        out.add(d(RIDE_CYMBAL, QUARTER));
        out.add(d(RIDE_CYMBAL, QUARTER));
        out.add(d(RIDE_CYMBAL, QUARTER));
        out.add(d(RIDE_CYMBAL, QUARTER));
    }

    /** Standard drive: kick 1&3, snare 2&4, hi-hat eighths. */
    private static void driveBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
    }

    /** Ride drive: kick 1&3, snare 2&4, ride eighths. */
    private static void rideBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));
    }

    /** Gentle fill into next section. */
    private static void softFill(List<PhraseNode> out) {
        out.add(d(RIDE_CYMBAL, QUARTER));
        out.add(d(SIDE_STICK, QUARTER));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CRASH_CYMBAL, QUARTER));
    }

    /** Tom cascade fill for transitions. */
    private static void tomFill(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(HIGH_TOM, EIGHTH));
        out.add(d(HIGH_MID_TOM, EIGHTH));
        out.add(d(LOW_TOM, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new SoftRockTianHeiHei());
    }
}
