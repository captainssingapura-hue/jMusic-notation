package music.notation.songs.folk.tianheihei;

import music.notation.duration.Duration;
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
 *
 * <p>Migrated from the old {@code Section} / {@code TrackDecl} /
 * {@code DrumPhrase} API to the canonical
 * {@link music.notation.songs.PieceHelper#joinMelodicPhrases} +
 * {@link Piece#ofTrackKinds} pattern. Drums are emitted as a single
 * flat bar list (no {@code DrumPhrase} in the new ADT).</p>
 */
public final class U2RockTianHeiHei implements PieceContentProvider<TianHeiHei> {

    private static final KeySignature KEY = PianoTianHeiHei.KEY;
    private static final TimeSignature TS  = PianoTianHeiHei.TS;
    private static final int BAR_SF = 64;   // 4/4
    private final PianoTianHeiHei piano = new PianoTianHeiHei();

    private StaffPhraseBuilderTyped b() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "U2 Rock"; }

    @Override
    public Piece create() {
        var id = new TianHeiHei();

        var lead  = joinMelodicPhrases("Lead",        ELECTRIC_PIANO_1,      leadPhrases());
        var pno   = joinMelodicPhrases("Piano",       ACOUSTIC_GRAND_PIANO,  pianoHarmonyPhrases());
        var edge  = joinMelodicPhrases("Edge Guitar", ELECTRIC_GUITAR_CLEAN, edgeGuitarPhrases());
        var bass  = joinMelodicPhrases("Bass",        ELECTRIC_BASS_PICK,    bassPhrases());
        var organ = joinMelodicPhrases("Organ",       ROCK_ORGAN,            organPhrases());
        var drums = new DrumTrack("Drums", Phrase.of(buildAllDrumBars()));

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS, new Tempo(116, QUARTER),
                List.of(lead, pno, edge, bass, organ),
                List.of(drums));
    }

    // ── Lead: Electric Piano 1 (reuses piano melody phrases) ─────────

    private List<AuthorPhrase> leadPhrases() {
        var m1 = piano.buildMelodyMain1();
        var th = piano.buildMelodyTianHeiHei1();
        var ch = piano.buildMelodyMain2();
        var ending = piano.buildEnding();
        return List.<AuthorPhrase>of(
                piano.buildMelodyPre(), m1, th, m1, th,
                ch, piano.buildBridge(), ch,
                piano.overrideMelodyMain2(),
                ending, th);
    }

    // ── Piano: Acoustic Grand — Chopin nocturne harmony ──────────────

    private List<AuthorPhrase> pianoHarmonyPhrases() {
        var m1 = piano.buildHarmonyMain1();
        var th = piano.buildHarmonyTianHei();
        var ch = piano.buildHarmonyChorus();
        var ending = piano.buildHarmonyEnding();
        return List.<AuthorPhrase>of(
                piano.buildHarmonyPre(), m1, th, m1, th,
                ch, piano.buildHarmonyBridge(), ch,
                piano.overrideHarmonyChorus(),
                ending, th);
    }

    // ── Edge Guitar: dotted-quarter delay arpeggios ──────────────────

    private List<AuthorPhrase> edgeGuitarPhrases() {
        var m1 = edgeMain1();
        var th = edgeTianHei();
        var ch = edgeChorus();
        return List.<AuthorPhrase>of(
                edgePre(), m1, th, m1, th,
                ch, edgeBridge(), ch, ch,
                edgeEnding(), th);
    }

    private MelodicPhrase edgePre() {
        return b()
                .bar().r(WHOLE).done()
                .bar().r(WHOLE).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER, B).pad(QUARTER).done()
                .build(elision());
    }

    private MelodicPhrase edgeMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E).done()
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), G).o5(EIGHTH, C).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .build(attacca());
    }

    private MelodicPhrase edgeTianHei() {
        return b()
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, G, D.higher(1)).done()
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, G, D.higher(1)).done()
                .bar().o4(HALF, C, G).o4(HALF, C, G).done()
                .bar().o4(HALF, G, D.higher(1)).o4(HALF, G, D.higher(1)).done()
                .bar().o4(HALF, C, G).o4(HALF, C, G).done()
                .bar().o4(HALF, D, A).o4(HALF, D, A).done()
                .bar().o4(HALF, G, D.higher(1)).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase edgeChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), G).o5(EIGHTH, C).done()
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D).done()
                .bar().o4(HALF, G, D.higher(1)).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase edgeBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E).done()
                .bar().o4(HALF, C, G).o4(HALF, D, A).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, E).done()
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E).done()
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), G).o5(EIGHTH, C).done()
                .bar().o4(HALF, D, A).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase edgeEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E).done()
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), G).o5(EIGHTH, C).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o5(QUARTER.dot(), C).o5(EIGHTH, E).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D).done()
                .bar().o4(HALF, C, G).o4(HALF, G, D.higher(1)).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, D).done()
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), A).o5(EIGHTH, D).done()
                .bar().o4(HALF, G, D.higher(1)).pad(HALF).done()
                .build(end());
    }

    // ── Bass: driving picked eighth-note patterns ────────────────────

    private List<AuthorPhrase> bassPhrases() {
        var m1 = bassMain1();
        var th = bassTianHei();
        var ch = bassChorus();
        return List.<AuthorPhrase>of(
                bassPre(), m1, th, m1, th,
                ch, bassBridge(), ch, ch,
                bassEnding(), th);
    }

    private MelodicPhrase bassPre() {
        return b()
                .bar().r(WHOLE).done()
                .bar().r(WHOLE).done()
                .bar().r(WHOLE).done()
                .bar().o2(HALF, G).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase bassMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A).done()
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o3(C).o3(C).o3(G).o3(C).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A).done()
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .build(attacca());
    }

    private MelodicPhrase bassTianHei() {
        return b()
                .bar().o2(HALF, G).o3(HALF, D).done()
                .bar().o2(HALF, G).o3(HALF, D).done()
                .bar().o3(HALF, C).o3(HALF, G).done()
                .bar().o2(HALF, G).o3(HALF, D).done()
                .bar().o3(HALF, C).o3(HALF, G).done()
                .bar().o3(HALF, D).o3(HALF, A).done()
                .bar().o2(HALF, G).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase bassChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o3(C).o3(C).o3(G).o3(C).done()
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A).done()
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D).done()
                .bar().o2(HALF, G).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase bassBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A).done()
                .bar().o3(HALF, C).o3(HALF, D).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o2(E).o2(E).o2(B).o2(B).o2(E).o2(E).o2(B).o2(E).done()
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A).done()
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o3(C).o3(C).o3(G).o3(C).done()
                .bar().o3(HALF, D).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase bassEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A).done()
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o3(C).o3(C).o3(G).o3(C).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o2(A).done()
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D).done()
                .bar().o3(HALF, C).o2(HALF, G).done()
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D).done()
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(G).o2(G).o3(D).o2(G).done()
                .bar(EIGHTH).o3(D).o3(D).o3(A).o3(A).o3(D).o3(D).o3(A).o3(D).done()
                .bar().o2(HALF, G).pad(HALF).done()
                .build(end());
    }

    // ── Rock Organ: atmospheric sustained chords ─────────────────────

    private List<AuthorPhrase> organPhrases() {
        var m1 = organMain1();
        var th = organTianHei();
        var ch = organChorus();
        return List.<AuthorPhrase>of(
                organPre(), m1, th, m1, th,
                ch, organBridge(), ch, ch,
                organEnding(), th);
    }

    private MelodicPhrase organPre() {
        return b()
                .bar().r(WHOLE).done().bar().r(WHOLE).done().bar().r(WHOLE).done()
                .bar().r(HALF).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase organMain1() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar().o3(WHOLE, C, E, G).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar().o3(WHOLE, D, F, A).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .build(attacca());
    }

    private MelodicPhrase organTianHei() {
        return b()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, G, B.f(), D.higher(1)).done()
                .bar().o3(WHOLE, C, E.f(), G).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, C, E, G).done()
                .bar().o3(WHOLE, D, F, A).done()
                .bar().o3(HALF, G, B, D.higher(1)).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase organChorus() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar().o3(WHOLE, D, F, A).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, D, F, A).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, C, E, G).done()
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar().o3(WHOLE, D, F, A).done()
                .bar().o3(HALF, G, B, D.higher(1)).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase organBridge() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar().o3(HALF, C, E, G).o3(HALF, D, F, A).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, E, G, B).done()
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar().o3(WHOLE, C, E, G).done()
                .bar().o3(HALF, D, F, A).pad(HALF).done()
                .build(elision());
    }

    private MelodicPhrase organEnding() {
        return b()
                .pickup(EIGHTH).r(EIGHTH).r(EIGHTH).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar().o3(WHOLE, C, E, G).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar().o3(WHOLE, D, F, A).done()
                .bar().o3(HALF, C, E, G).o3(HALF, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, D, F, A).done()
                .bar().o3(WHOLE, G, B, D.higher(1)).done()
                .bar().o3(WHOLE, D, F, A).done()
                .bar().o3(HALF, G, B, D.higher(1)).pad(HALF).done()
                .build(end());
    }

    // ── Drums ────────────────────────────────────────────────────────
    //
    // The old DrumPhrase API is gone — drums are now a flat bar list.
    // Pickup/elision handling for drums is approximated as full silent
    // bars (pickups become the previous section's resolve, then a fresh
    // bar starts). Bar counts per section are preserved.

    private List<Bar> buildAllDrumBars() {
        var bars = new ArrayList<Bar>();

        // Pre — 4 bars of soft hi-hat ticks
        for (int i = 0; i < 4; i++) bars.add(tickBar());

        // Main1 — 1 silent pickup + 7 half-time + 1 fill = 9 bars
        bars.add(silentBar());
        for (int i = 0; i < 7; i++) bars.add(halfTimeBar());
        bars.add(halfTimeFillBar());

        // TianHei — 6 atmospheric + 1 resolve = 7 bars
        for (int i = 0; i < 6; i++) bars.add(atmosphericBar());
        bars.add(atmosphericResolveBar());

        // Main1 again
        bars.add(silentBar());
        for (int i = 0; i < 7; i++) bars.add(halfTimeBar());
        bars.add(halfTimeFillBar());

        // TianHei again
        for (int i = 0; i < 6; i++) bars.add(atmosphericBar());
        bars.add(atmosphericResolveBar());

        // Chorus — pickup + 7 anthem + 1 fill = 9 bars
        bars.add(crashPickupBar());
        for (int i = 0; i < 7; i++) bars.add(anthemBar());
        bars.add(anthemFillBar());

        // Bridge — pickup + 7 ride half-time + 1 fill = 9 bars
        bars.add(silentBar());
        for (int i = 0; i < 7; i++) bars.add(rideHalfTimeBar());
        bars.add(halfTimeFillBar());

        // Chorus
        bars.add(crashPickupBar());
        for (int i = 0; i < 7; i++) bars.add(anthemBar());
        bars.add(anthemFillBar());

        // Chorus
        bars.add(crashPickupBar());
        for (int i = 0; i < 7; i++) bars.add(anthemBar());
        bars.add(anthemFillBar());

        // Ending — pickup + 4 half-time + 4 anthem + 3 ride + 1 final = 13 bars
        bars.add(silentBar());
        for (int i = 0; i < 4; i++) bars.add(halfTimeBar());
        for (int i = 0; i < 4; i++) bars.add(anthemBar());
        for (int i = 0; i < 3; i++) bars.add(rideHalfTimeBar());
        bars.add(finalCrashBar());

        // TianHei tail
        for (int i = 0; i < 6; i++) bars.add(atmosphericBar());
        bars.add(atmosphericResolveBar());

        return bars;
    }

    // ── Drum bar primitives ──────────────────────────────────────────

    /** Just hi-hat quarters — sparse intro. */
    private static Bar tickBar() {
        var h = new PercussionNote(CLOSED_HI_HAT, Duration.of(QUARTER));
        return Bar.of(BAR_SF, h, h, h, h);
    }

    /** Half-time: kick on 1, snare on 3, open hi-hat eighths. */
    private static Bar halfTimeBar() {
        var k  = new PercussionNote(BASS_DRUM,       Duration.of(EIGHTH));
        var oh = new PercussionNote(OPEN_HI_HAT,     Duration.of(EIGHTH));
        var s  = new PercussionNote(ACOUSTIC_SNARE,  Duration.of(EIGHTH));
        return Bar.of(BAR_SF, k, oh, oh, oh, s, oh, oh, oh);
    }

    /** Atmospheric: kick on 1, open hi-hat quarters. */
    private static Bar atmosphericBar() {
        var k  = new PercussionNote(BASS_DRUM,    Duration.of(QUARTER));
        var oh = new PercussionNote(OPEN_HI_HAT,  Duration.of(QUARTER));
        return Bar.of(BAR_SF, k, oh, oh, oh);
    }

    /** Atmospheric resolve: kick + open hi-hat then half rest. */
    private static Bar atmosphericResolveBar() {
        var k  = new PercussionNote(BASS_DRUM,   Duration.of(QUARTER));
        var oh = new PercussionNote(OPEN_HI_HAT, Duration.of(QUARTER));
        return Bar.of(BAR_SF, k, oh,
                (PhraseNode) new RestNode(Duration.of(HALF)));
    }

    /** Anthemic: kick 1&3, snare 2&4, crash eighths. */
    private static Bar anthemBar() {
        var k  = new PercussionNote(BASS_DRUM,      Duration.of(EIGHTH));
        var c  = new PercussionNote(CRASH_CYMBAL,   Duration.of(EIGHTH));
        var s  = new PercussionNote(ACOUSTIC_SNARE, Duration.of(EIGHTH));
        return Bar.of(BAR_SF, k, c, s, c, k, c, s, c);
    }

    /** Ride half-time: kick on 1, snare on 3, ride quarters. */
    private static Bar rideHalfTimeBar() {
        var k  = new PercussionNote(BASS_DRUM,      Duration.of(EIGHTH));
        var r  = new PercussionNote(RIDE_CYMBAL,    Duration.of(EIGHTH));
        var s  = new PercussionNote(ACOUSTIC_SNARE, Duration.of(EIGHTH));
        return Bar.of(BAR_SF, k, r, r, r, s, r, r, r);
    }

    /** Half-time fill: snare buildup into crash. */
    private static Bar halfTimeFillBar() {
        var k  = new PercussionNote(BASS_DRUM,      Duration.of(EIGHTH));
        var oh = new PercussionNote(OPEN_HI_HAT,    Duration.of(EIGHTH));
        var s  = new PercussionNote(ACOUSTIC_SNARE, Duration.of(EIGHTH));
        var ht = new PercussionNote(HIGH_TOM,       Duration.of(EIGHTH));
        var mt = new PercussionNote(HIGH_MID_TOM,   Duration.of(EIGHTH));
        var lt = new PercussionNote(LOW_TOM,        Duration.of(EIGHTH));
        var c  = new PercussionNote(CRASH_CYMBAL,   Duration.of(EIGHTH));
        return Bar.of(BAR_SF, k, oh, s, s, ht, mt, lt, c);
    }

    /** Anthemic fill: tom cascade into crash. */
    private static Bar anthemFillBar() {
        var k  = new PercussionNote(BASS_DRUM,      Duration.of(EIGHTH));
        var c  = new PercussionNote(CRASH_CYMBAL,   Duration.of(EIGHTH));
        var s  = new PercussionNote(ACOUSTIC_SNARE, Duration.of(EIGHTH));
        var ht = new PercussionNote(HIGH_TOM,       Duration.of(EIGHTH));
        var mt = new PercussionNote(HIGH_MID_TOM,   Duration.of(EIGHTH));
        var lt = new PercussionNote(LOW_TOM,        Duration.of(EIGHTH));
        return Bar.of(BAR_SF, k, c, s, s, ht, mt, lt, c);
    }

    /** Chorus pickup bar: half rest + eighth rest + eighth rest + crash on "and of 4". */
    private static Bar crashPickupBar() {
        var c = new PercussionNote(CRASH_CYMBAL, Duration.of(EIGHTH));
        return Bar.of(BAR_SF,
                (PhraseNode) new RestNode(Duration.of(HALF)),
                new RestNode(Duration.of(EIGHTH)),
                new RestNode(Duration.of(EIGHTH)),
                new RestNode(Duration.of(EIGHTH)),
                c);
    }

    /** Final crash: crash + kick + snare half + half rest. */
    private static Bar finalCrashBar() {
        var c = new PercussionNote(CRASH_CYMBAL,   Duration.of(EIGHTH));
        var k = new PercussionNote(BASS_DRUM,      Duration.of(EIGHTH));
        var s = new PercussionNote(ACOUSTIC_SNARE, Duration.of(QUARTER));
        return Bar.of(BAR_SF, c, k, s,
                (PhraseNode) new RestNode(Duration.of(HALF)));
    }

    private static Bar silentBar() {
        return Bar.of(BAR_SF,
                (PhraseNode) new RestNode(Duration.ofSixtyFourths(BAR_SF)));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new U2RockTianHeiHei());
    }
}
