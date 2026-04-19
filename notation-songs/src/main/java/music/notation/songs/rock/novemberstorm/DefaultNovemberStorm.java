package music.notation.songs.rock.novemberstorm;

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
 * 十一月四日风雨大作（其二）— 陆游 (1192)
 *
 * <p>Rock arrangement in A minor, 4/4, 132 BPM.
 * Four-verse structure with emotional arc:</p>
 * <ul>
 *   <li>Verse 1 — standard rock, sets the tone</li>
 *   <li>Verse 2 — building up, growing intensity</li>
 *   <li>Verse 3 — strongest climax, melody soars to highest octave</li>
 *   <li>Verse 4 — sharp turndown, deep sorrow, fading to silence</li>
 * </ul>
 */
public final class DefaultNovemberStorm implements PieceContentProvider<NovemberStorm> {

    private static final KeySignature KEY = new KeySignature(A, Mode.MINOR);
    private static final TimeSignature TS  = new TimeSignature(4, 4);

    @Override
    public Piece create() {
        final var id = new NovemberStorm();

        return new Piece(id.title(), id.composer(),
                KEY, TS,
                new Tempo(132, QUARTER),
                List.of(
                        buildLeadGuitar(),
                        buildRhythmGuitar(),
                        buildBass(),
                        buildOrgan(),
                        buildDrums()));
    }

    // ── Lead Guitar (Overdriven) ───────────────────────────────────────

    /** Fresh single-use builder for this piece's key/TS/default duration. */
    private static StaffPhraseBuilder newBuilder() {
        return StaffPhraseBuilder.in(KEY, TS, EIGHTH);
    }

    private Track buildLeadGuitar() {
        // Pickup (1 bar)
        var pickup = newBuilder()
                .pickup().mf().o4(QUARTER, E)
                .build(attacca());

        // Verse 1: standard — o4/o5, mf–f (10 bars)
        var v1 = newBuilder()
                .bar().mf()
                    .o4(QUARTER, A).o4(QUARTER, B).o5(QUARTER.dot(), C).o4(B)
                .bar()
                    .o4(QUARTER, A).o4(QUARTER, G).o4(HALF, E)
                .bar()
                    .o5(QUARTER, C).o5(QUARTER, D).o5(QUARTER.dot(), E).o5(D)
                .bar()
                    .o5(QUARTER, E).o5(QUARTER.dot(),G).o5(EIGHTH,E).o5(QUARTER,C)
                .bar()
                    .r(HALF).r(QUARTER).o4(QUARTER, A)
                .bar().f()
                    .o5(C).o5(D).o5(E).o5(D).o5(QUARTER, C).o4(QUARTER, B)
                .bar()
                    .o4(QUARTER, A).o4(G).o4(A).o4(QUARTER, B).o4(QUARTER, A)
                .bar()
                    .o5(QUARTER, E).o5(QUARTER, E).o5(QUARTER.dot(), A).o5(G)
                .bar()
                    .o5(QUARTER, E).o5(QUARTER, D).o5(QUARTER, C).o4(QUARTER, B)
                .bar()
                    .o4(HALF, A).r(QUARTER).o4(QUARTER, E)
                .build(attacca());

        // Verse 2: building up — same melody, f–ff dynamics (10 bars)
        var v2 = newBuilder()
                .bar().f()
                    .o4(QUARTER, A).o4(QUARTER, B).o5(QUARTER.dot(), C).o4(B)
                .bar()
                    .o4(QUARTER, A).o4(QUARTER, G).o4(HALF, E)
                .bar()
                    .o5(QUARTER, C).o5(QUARTER, D).o5(QUARTER.dot(), E).o5(D)
                .bar()
                    .o5(QUARTER, C).o4(QUARTER, B).o4(HALF, A)
                .bar()
                    .r(HALF).r(QUARTER).o4(QUARTER, A)
                .bar().ff()
                    .o5(C).o5(D).o5(E).o5(D).o5(QUARTER, C).o4(QUARTER, B)
                .bar()
                    .o4(QUARTER, A).o4(G).o4(A).o4(QUARTER, B).o4(QUARTER, A)
                .bar()
                    .o5(QUARTER, E).o5(QUARTER, E).o5(QUARTER.dot(), A).o5(G)
                .bar()
                    .o5(QUARTER, E).o5(QUARTER, D).o5(QUARTER, C).o4(QUARTER, B)
                .bar()
                    .o4(HALF, A).r(QUARTER).o5(QUARTER, E)
                .build(attacca());

        // Verse 3: climax — ff–fff, lines 1-2 up to o5, lines 3-4 soar to o6 (10 bars)
        var v3 = newBuilder()
                .bar().ff()
                    .o5(QUARTER, A).o5(QUARTER, B).o6(QUARTER.dot(), C).o5(B)
                .bar()
                    .o5(QUARTER, A).o5(QUARTER, C.higher(1)).o5(HALF, E.higher(1))
                .bar()
                    .o6(QUARTER, C).o6(QUARTER, D).o6(QUARTER.dot(), E).o6(D)
                .bar()
                    .o6(QUARTER, E).o6(QUARTER.dot(), G).o6(EIGHTH,E).o6(QUARTER,C)
                .bar()
                    .r(HALF).r(QUARTER).o5(QUARTER, A)
                .bar().fff()
                    .o6(C).o6(D).o6(E).o6(D).o6(QUARTER, C).o5(QUARTER, B)
                .bar()
                    .o5(QUARTER, A).o5(G).o5(A).o5(QUARTER, B).o5(QUARTER, A)
                .bar()
                    .o6(QUARTER, E).o6(QUARTER, E).o6(QUARTER.dot(), A).o6(G)
                .bar()
                    .o6(QUARTER, E).o6(QUARTER, D).o6(QUARTER, C).o5(QUARTER, B)
                .bar()
                    .o5(HALF, A).r(HALF)
                .build(attacca());

        // Verse 4: sharp drop — p→pp, melody sinks to o3, ending in sorrow (11 bars)
        var v4 = newBuilder()
                .bar().p()
                    .o4(QUARTER, A).o4(QUARTER, B).o5(QUARTER.dot(), C).o4(B)
                .bar()
                    .o4(QUARTER, A).o4(QUARTER, G).o4(HALF, E)
                .bar().pp()
                    .o5(QUARTER, C).o5(QUARTER, D).o5(QUARTER.dot(), E).o5(D)
                .bar()
                    .o5(QUARTER, C).o4(QUARTER, B).o4(HALF, A)
                .bar()
                    .r(HALF).r(QUARTER).o3(QUARTER, A)
                .bar().p()
                    .o4(C).o4(D).o4(E).o4(D).o4(QUARTER, C).o3(QUARTER, B)
                .bar()
                    .o3(QUARTER, A).o3(G).o3(A).o3(QUARTER, B).o3(QUARTER, A)
                .bar().pp()
                    .o4(QUARTER, E).o4(QUARTER, D).o4(QUARTER.dot(), C).o3(B)
                .bar().ppp()
                    .o3(QUARTER, A).o3(QUARTER, G).o3(QUARTER, E).o3(QUARTER, A)
                .bar()
                    .o3(WHOLE, A)
                // Silence
                .bar()
                    .r(WHOLE)
                .build(end());

        return Track.of("Lead Guitar", OVERDRIVEN_GUITAR,
                List.of(pickup, v1, v2, v3, v4));
    }

    // ── Rhythm Guitar (Distortion, power chords) ───────────────────────

    private Track buildRhythmGuitar() {
        // Power-chord voicings: root + fifth
        final var pAm = chord(HALF, p(A, 3), p(E, 4));
        final var pC  = chord(HALF, p(C, 4), p(G, 4));
        final var pDm = chord(HALF, p(D, 3), p(A, 3));
        final var pF  = chord(HALF, p(F, 3), p(C, 4));
        final var pEm = chord(HALF, p(E, 3), p(B, 3));
        final var pAmW = chord(WHOLE, p(A, 3), p(E, 4));

        // Verse chord pattern (19 half-note chords + 1 whole = 10 bars)
        var fullVerse = List.of(
                pAm, pAm,       // bar 1
                pAm, pAm,       // bar 2
                pC,  pC,        // bar 3
                pAm, pAm,       // bar 4
                pAm, pAm,       // bar 5  interlude
                pDm, pDm,       // bar 6
                pAm, pAm,       // bar 7
                pF,  pF,        // bar 8
                pEm, pEm,       // bar 9
                pAmW);           // bar 10

        // Pickup (1 bar)
        var pickup = new ChordPhrase(List.of(pAmW), attacca());

        // Verses 1–3: full power chords (10 bars each)
        var v1 = new ChordPhrase(fullVerse, attacca());
        var v2 = new ChordPhrase(fullVerse, attacca());
        var v3 = new ChordPhrase(fullVerse, attacca());

        // Verse 4: thin sustained root, sharp drop (11 bars)
        var v4 = newBuilder()
                .bar().pp().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().ppp().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().r(WHOLE)
                .build(end());

        return Track.of("Rhythm Guitar", DISTORTION_GUITAR,
                List.of(pickup, v1, v2, v3, v4));
    }

    // ── Bass (Electric Pick) ───────────────────────────────────────────

    private Track buildBass() {
        // Pickup (1 bar)
        var pickup = newBuilder()
                .pickup().mf().o2(QUARTER, A).o2(QUARTER, E)
                .build(attacca());

        // Verse 1: standard driving eighths (10 bars)
        var v1 = buildDrivingBass(Dynamic.MF, Dynamic.F);

        // Verse 2: building up (10 bars)
        var v2 = buildDrivingBass(Dynamic.F, Dynamic.FF);

        // Verse 3: climax, hardest driving (10 bars)
        var v3 = buildDrivingBass(Dynamic.FF, Dynamic.FFF);

        // Verse 4: whole notes, sparse, sharp drop (11 bars)
        var v4 = newBuilder()
                .bar().p()
                    .o2(WHOLE, A)
                .bar()
                    .o2(WHOLE, A)
                .bar().pp()
                    .o2(WHOLE, C)
                .bar()
                    .o2(WHOLE, A)
                .bar()
                    .o2(WHOLE, A)
                .bar().ppp()
                    .o2(WHOLE, D)
                .bar()
                    .o2(WHOLE, A)
                .bar()
                    .o2(WHOLE, F)
                .bar()
                    .o2(WHOLE, E)
                .bar()
                    .o2(WHOLE, A)
                .bar()
                    .r(WHOLE)
                .build(end());

        return Track.of("Bass", ELECTRIC_BASS_PICK,
                List.of(pickup, v1, v2, v3, v4));
    }

    /** Driving eighth-note bass pattern for one verse (10 bars). */
    private MelodicPhrase buildDrivingBass(Dynamic verse, Dynamic climax) {
        return newBuilder()
                .bar().dyn(verse)
                    .o2(A).o2(A).o2(E).o2(A).o2(A).o2(E).o2(A).o2(A)
                .bar()
                    .o2(A).o2(A).o2(E).o2(A).o2(A).o2(E).o2(A).o2(A)
                .bar()
                    .o2(C).o2(C).o2(G).o2(C).o2(C).o2(G).o2(C).o2(C)
                .bar()
                    .o2(A).o2(A).o2(E).o2(A).o2(A).o2(E).o2(A).o2(A)
                .bar()
                    .o2(QUARTER, A).o2(QUARTER, E).o2(QUARTER, D).o2(QUARTER, E)
                .bar()
                    .o2(D).o2(D).o2(A).o2(D).o2(D).o2(A).o2(D).o2(D)
                .bar()
                    .o2(A).o2(A).o2(E).o2(A).o2(A).o2(E).o2(A).o2(A)
                .bar().dyn(climax)
                    .o2(F).o2(F).o2(C).o2(F).o2(F).o2(C).o2(F).o2(F)
                .bar()
                    .o2(E).o2(E).o2(B).o2(E).o2(E).o2(B).o2(E).o2(E)
                .bar()
                    .o2(HALF, A).o2(QUARTER, E).o2(QUARTER, A)
                .build(attacca());
    }

    // ── Organ Pad (Rock Organ) ─────────────────────────────────────────

    private Track buildOrgan() {
        // Pickup (1 bar)
        var pickup = newBuilder().bar().mp().o3(WHOLE, A).build(attacca());

        // Verse 1: standard (10 bars)
        var v1 = buildOrganVerse(Dynamic.MP, Dynamic.MF);

        // Verse 2: building (10 bars)
        var v2 = buildOrganVerse(Dynamic.MF, Dynamic.F);

        // Verse 3: climax (10 bars)
        var v3 = buildOrganVerse(Dynamic.F, Dynamic.FF);

        // Verse 4: sharp drop, fading to nothing (11 bars)
        var v4 = newBuilder()
                .bar().p().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().pp().o3(WHOLE, C)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().ppp().o3(WHOLE, D)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, F)
                .bar().o3(WHOLE, E)
                .bar().o3(WHOLE, A)
                .bar().r(WHOLE)
                .build(end());

        return Track.of("Organ", ROCK_ORGAN,
                List.of(pickup, v1, v2, v3, v4));
    }

    /** Organ pad for one verse (10 bars). */
    private MelodicPhrase buildOrganVerse(Dynamic verse, Dynamic climax) {
        return newBuilder()
                .bar().dyn(verse)
                    .o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, C)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, A)
                .bar().dyn(climax)
                    .o3(WHOLE, D)
                .bar().o3(WHOLE, A)
                .bar().o3(WHOLE, F)
                .bar().o3(WHOLE, E)
                .bar().o3(WHOLE, A)
                .build(attacca());
    }

    // ── Drums ──────────────────────────────────────────────────────────

    private Track buildDrums() {
        // ── Patterns ──

        var rockBar = List.<PhraseNode>of(
                d(BASS_DRUM, EIGHTH),      d(CLOSED_HI_HAT, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(CLOSED_HI_HAT, EIGHTH),
                d(BASS_DRUM, EIGHTH),      d(CLOSED_HI_HAT, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(CLOSED_HI_HAT, EIGHTH));

        var hardRockBar = List.<PhraseNode>of(
                d(BASS_DRUM, EIGHTH),      d(OPEN_HI_HAT, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(OPEN_HI_HAT, EIGHTH),
                d(BASS_DRUM, EIGHTH),      d(OPEN_HI_HAT, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(CRASH_CYMBAL, EIGHTH));

        var crashRockBar = List.<PhraseNode>of(
                d(CRASH_CYMBAL, EIGHTH),   d(OPEN_HI_HAT, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(OPEN_HI_HAT, EIGHTH),
                d(BASS_DRUM, EIGHTH),      d(CRASH_CYMBAL, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(CRASH_CYMBAL, EIGHTH));

        var rideOnly = List.<PhraseNode>of(
                d(RIDE_CYMBAL, QUARTER), d(RIDE_CYMBAL, QUARTER),
                d(RIDE_CYMBAL, QUARTER), d(RIDE_CYMBAL, QUARTER));

        var fillBar = List.<PhraseNode>of(
                d(ACOUSTIC_SNARE, EIGHTH), d(HIGH_TOM, EIGHTH),
                d(HIGH_MID_TOM, EIGHTH),   d(LOW_MID_TOM, EIGHTH),
                d(LOW_TOM, EIGHTH),        d(LOW_FLOOR_TOM, EIGHTH),
                d(BASS_DRUM, EIGHTH),      d(CRASH_CYMBAL, EIGHTH));

        var pickupBar = List.<PhraseNode>of(
                d(CLOSED_HI_HAT, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(SIDE_STICK, QUARTER),    d(SIDE_STICK, QUARTER));

        var crashEnd = List.<PhraseNode>of(
                d(CRASH_CYMBAL, QUARTER), d(BASS_DRUM, QUARTER),
                d(BASS_DRUM, HALF));

        var silentBar = List.<PhraseNode>of(
                d(CLOSED_HI_HAT, WHOLE));

        // ── Assemble ──

        var nodes = new ArrayList<PhraseNode>();

        // ── Pickup (1 bar)
        nodes.add(new DynamicNode(Dynamic.MF));
        nodes.addAll(pickupBar);

        // ── Verse 1: standard rock (10 bars)
        nodes.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 4; i++) nodes.addAll(rockBar);       // bars 1–4
        nodes.addAll(fillBar);                                     // bar 5
        for (int i = 0; i < 4; i++) nodes.addAll(rockBar);       // bars 6–9
        nodes.addAll(crashEnd);                                    // bar 10

        // ── Verse 2: building — rock→hard rock (10 bars)
        nodes.add(new DynamicNode(Dynamic.F));
        for (int i = 0; i < 4; i++) nodes.addAll(rockBar);       // bars 1–4
        nodes.addAll(fillBar);                                     // bar 5
        nodes.add(new DynamicNode(Dynamic.FF));
        for (int i = 0; i < 4; i++) nodes.addAll(hardRockBar);   // bars 6–9
        nodes.addAll(crashEnd);                                    // bar 10

        // ── Verse 3: strongest climax — crashes, open hats, max intensity (10 bars)
        nodes.add(new DynamicNode(Dynamic.FFF));
        nodes.addAll(crashEnd);                                    // bar 1  crash intro
        for (int i = 0; i < 3; i++) nodes.addAll(crashRockBar);  // bars 2–4
        nodes.addAll(fillBar);                                     // bar 5
        for (int i = 0; i < 4; i++) nodes.addAll(crashRockBar);  // bars 6–9
        nodes.addAll(crashEnd);                                    // bar 10

        // ── Verse 4: sharp drop — ride only, fading to silence (11 bars)
        nodes.add(new DynamicNode(Dynamic.P));
        for (int i = 0; i < 4; i++) nodes.addAll(rideOnly);      // bars 1–4
        nodes.add(new DynamicNode(Dynamic.PP));
        for (int i = 0; i < 3; i++) nodes.addAll(rideOnly);      // bars 5–7
        nodes.add(new DynamicNode(Dynamic.PPP));
        nodes.addAll(rideOnly);                                    // bar 8
        nodes.addAll(rideOnly);                                    // bar 9
        nodes.addAll(silentBar);                                   // bar 10
        nodes.addAll(silentBar);                                   // bar 11 fade-out

        return Track.of("Drums", DRUM_KIT, List.of(
                new DrumPhrase(nodes, end())));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new DefaultNovemberStorm());
    }
}
