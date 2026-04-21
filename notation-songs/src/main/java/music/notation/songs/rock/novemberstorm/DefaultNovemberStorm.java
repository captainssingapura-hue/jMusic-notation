package music.notation.songs.rock.novemberstorm;

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

        // Five named tracks, instruments declared once at piece level.
        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Lead Guitar",   OVERDRIVEN_GUITAR),
                new TrackDecl.MusicTrackDecl("Rhythm Guitar", DISTORTION_GUITAR),
                new TrackDecl.MusicTrackDecl("Bass",          ELECTRIC_BASS_PICK),
                new TrackDecl.MusicTrackDecl("Organ",         ROCK_ORGAN),
                new TrackDecl.MusicTrackDecl("Drums",         DRUM_KIT)
        );

        // Five sections: pickup (1 bar) → v1/v2/v3 (10 bars) → v4 (11 bars).
        final var leadGuitar   = buildLeadGuitarPerSection();
        final var rhythmGuitar = buildRhythmGuitarPerSection();
        final var bass         = buildBassPerSection();
        final var organ        = buildOrganPerSection();
        final var drums        = buildDrumsPerSection();

        final var sections = List.of(
                sectionFor("Pickup", 1,
                        leadGuitar.get(0), rhythmGuitar.get(0),
                        bass.get(0), organ.get(0), drums.get(0)),
                sectionFor("Verse 1", 10,
                        leadGuitar.get(1), rhythmGuitar.get(1),
                        bass.get(1), organ.get(1), drums.get(1)),
                sectionFor("Verse 2", 10,
                        leadGuitar.get(2), rhythmGuitar.get(2),
                        bass.get(2), organ.get(2), drums.get(2)),
                sectionFor("Verse 3", 10,
                        leadGuitar.get(3), rhythmGuitar.get(3),
                        bass.get(3), organ.get(3), drums.get(3)),
                sectionFor("Verse 4", 11,
                        leadGuitar.get(4), rhythmGuitar.get(4),
                        bass.get(4), organ.get(4), drums.get(4))
        );

        return Piece.ofSections(id.title(), id.composer(),
                KEY, TS,
                new Tempo(132, QUARTER),
                trackDecls,
                sections);
    }

    private static Section sectionFor(String name, int bars,
                                      Phrase lead, Phrase rhythm,
                                      Phrase bass, Phrase organ, Phrase drums) {
        return Section.named(name)
                .duration(Duration.ofSixtyFourths(bars * 64))    // 4/4 bar = 64sf
                .timeSignature(TS)
                .track("Lead Guitar",   lead)
                .track("Rhythm Guitar", rhythm)
                .track("Bass",          bass)
                .track("Organ",         organ)
                .track("Drums",         drums)
                .build();
    }

    // ── Lead Guitar (Overdriven) ───────────────────────────────────────

    /** Fresh single-use builder for this piece's key/TS/default duration. */
    private static StaffPhraseBuilderTyped newBuilder() {
        return StaffPhraseBuilderTyped.in(KEY, TS, EIGHTH);
    }

    private List<Phrase> buildLeadGuitarPerSection() {
        // Pickup (1 bar)
        var pickup = newBuilder()
                .pickup().mf().o4(QUARTER, E).done()
                .build(attacca());

        // Verse 1: standard — o4/o5, mf–f (10 bars)
        var v1 = newBuilder()
                .bar().mf()
                    .o4(QUARTER, A).o4(QUARTER, B).o5(QUARTER.dot(), C).o4(B).done()
                .bar()
                    .o4(QUARTER, A).o4(QUARTER, G).o4(HALF, E).done()
                .bar()
                    .o5(QUARTER, C).o5(QUARTER, D).o5(QUARTER.dot(), E).o5(D).done()
                .bar()
                    .o5(QUARTER, E).o5(QUARTER.dot(),G).o5(EIGHTH,E).o5(QUARTER,C).done()
                .bar()
                    .r(HALF).r(QUARTER).o4(QUARTER, A).done()
                .bar().f()
                    .o5(C).o5(D).o5(E).o5(D).o5(QUARTER, C).o4(QUARTER, B).done()
                .bar()
                    .o4(QUARTER, A).o4(G).o4(A).o4(QUARTER, B).o4(QUARTER, A).done()
                .bar()
                    .o5(QUARTER, E).o5(QUARTER, E).o5(QUARTER.dot(), A).o5(G).done()
                .bar()
                    .o5(QUARTER, E).o5(QUARTER, D).o5(QUARTER, C).o4(QUARTER, B).done()
                .bar()
                    .o4(HALF, A).r(QUARTER).o4(QUARTER, E).done()
                .build(attacca());

        // Verse 2: building up — same melody, f–ff dynamics (10 bars)
        var v2 = newBuilder()
                .bar().f()
                    .o4(QUARTER, A).o4(QUARTER, B).o5(QUARTER.dot(), C).o4(B).done()
                .bar()
                    .o4(QUARTER, A).o4(QUARTER, G).o4(HALF, E).done()
                .bar()
                    .o5(QUARTER, C).o5(QUARTER, D).o5(QUARTER.dot(), E).o5(D).done()
                .bar()
                    .o5(QUARTER, C).o4(QUARTER, B).o4(HALF, A).done()
                .bar()
                    .r(HALF).r(QUARTER).o4(QUARTER, A).done()
                .bar().ff()
                    .o5(C).o5(D).o5(E).o5(D).o5(QUARTER, C).o4(QUARTER, B).done()
                .bar()
                    .o4(QUARTER, A).o4(G).o4(A).o4(QUARTER, B).o4(QUARTER, A).done()
                .bar()
                    .o5(QUARTER, E).o5(QUARTER, E).o5(QUARTER.dot(), A).o5(G).done()
                .bar()
                    .o5(QUARTER, E).o5(QUARTER, D).o5(QUARTER, C).o4(QUARTER, B).done()
                .bar()
                    .o4(HALF, A).r(QUARTER).o5(QUARTER, E).done()
                .build(attacca());

        // Verse 3: climax — ff–fff, lines 1-2 up to o5, lines 3-4 soar to o6 (10 bars)
        var v3 = newBuilder()
                .bar().ff()
                    .o5(QUARTER, A).o5(QUARTER, B).o6(QUARTER.dot(), C).o5(B).done()
                .bar()
                    .o5(QUARTER, A).o5(QUARTER, C.higher(1)).o5(HALF, E.higher(1)).done()
                .bar()
                    .o6(QUARTER, C).o6(QUARTER, D).o6(QUARTER.dot(), E).o6(D).done()
                .bar()
                    .o6(QUARTER, E).o6(QUARTER.dot(), G).o6(EIGHTH,E).o6(QUARTER,C).done()
                .bar()
                    .r(HALF).r(QUARTER).o5(QUARTER, A).done()
                .bar().fff()
                    .o6(C).o6(D).o6(E).o6(D).o6(QUARTER, C).o5(QUARTER, B).done()
                .bar()
                    .o5(QUARTER, A).o5(G).o5(A).o5(QUARTER, B).o5(QUARTER, A).done()
                .bar()
                    .o6(QUARTER, E).o6(QUARTER, E).o6(QUARTER.dot(), A).o6(G).done()
                .bar()
                    .o6(QUARTER, E).o6(QUARTER, D).o6(QUARTER, C).o5(QUARTER, B).done()
                .bar()
                    .o5(HALF, A).r(HALF).done()
                .build(attacca());

        // Verse 4: sharp drop — p→pp, melody sinks to o3, ending in sorrow (11 bars)
        var v4 = newBuilder()
                .bar().p()
                    .o4(QUARTER, A).o4(QUARTER, B).o5(QUARTER.dot(), C).o4(B).done()
                .bar()
                    .o4(QUARTER, A).o4(QUARTER, G).o4(HALF, E).done()
                .bar().pp()
                    .o5(QUARTER, C).o5(QUARTER, D).o5(QUARTER.dot(), E).o5(D).done()
                .bar()
                    .o5(QUARTER, C).o4(QUARTER, B).o4(HALF, A).done()
                .bar()
                    .r(HALF).r(QUARTER).o3(QUARTER, A).done()
                .bar().p()
                    .o4(C).o4(D).o4(E).o4(D).o4(QUARTER, C).o3(QUARTER, B).done()
                .bar()
                    .o3(QUARTER, A).o3(G).o3(A).o3(QUARTER, B).o3(QUARTER, A).done()
                .bar().pp()
                    .o4(QUARTER, E).o4(QUARTER, D).o4(QUARTER.dot(), C).o3(B).done()
                .bar().ppp()
                    .o3(QUARTER, A).o3(QUARTER, G).o3(QUARTER, E).o3(QUARTER, A).done()
                .bar()
                    .o3(WHOLE, A).done()
                // Silence
                .bar()
                    .r(WHOLE).done()
                .build(end());

        return List.of(pickup, v1, v2, v3, v4);
    }

    // ── Rhythm Guitar (Distortion, power chords) ───────────────────────

    private List<Phrase> buildRhythmGuitarPerSection() {
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
                .bar().pp().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().ppp().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().r(WHOLE).done()
                .build(end());

        return List.of(pickup, v1, v2, v3, v4);
    }

    // ── Bass (Electric Pick) ───────────────────────────────────────────

    private List<Phrase> buildBassPerSection() {
        // Pickup (1 bar)
        var pickup = newBuilder()
                .pickup().mf().o2(QUARTER, A).o2(QUARTER, E).done()
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
                    .o2(WHOLE, A).done()
                .bar()
                    .o2(WHOLE, A).done()
                .bar().pp()
                    .o2(WHOLE, C).done()
                .bar()
                    .o2(WHOLE, A).done()
                .bar()
                    .o2(WHOLE, A).done()
                .bar().ppp()
                    .o2(WHOLE, D).done()
                .bar()
                    .o2(WHOLE, A).done()
                .bar()
                    .o2(WHOLE, F).done()
                .bar()
                    .o2(WHOLE, E).done()
                .bar()
                    .o2(WHOLE, A).done()
                .bar()
                    .r(WHOLE).done()
                .build(end());

        return List.of(pickup, v1, v2, v3, v4);
    }

    /** Driving eighth-note bass pattern for one verse (10 bars). */
    private MelodicPhrase buildDrivingBass(Dynamic verse, Dynamic climax) {
        return newBuilder()
                .bar().dyn(verse)
                    .o2(A).o2(A).o2(E).o2(A).o2(A).o2(E).o2(A).o2(A).done()
                .bar()
                    .o2(A).o2(A).o2(E).o2(A).o2(A).o2(E).o2(A).o2(A).done()
                .bar()
                    .o2(C).o2(C).o2(G).o2(C).o2(C).o2(G).o2(C).o2(C).done()
                .bar()
                    .o2(A).o2(A).o2(E).o2(A).o2(A).o2(E).o2(A).o2(A).done()
                .bar()
                    .o2(QUARTER, A).o2(QUARTER, E).o2(QUARTER, D).o2(QUARTER, E).done()
                .bar()
                    .o2(D).o2(D).o2(A).o2(D).o2(D).o2(A).o2(D).o2(D).done()
                .bar()
                    .o2(A).o2(A).o2(E).o2(A).o2(A).o2(E).o2(A).o2(A).done()
                .bar().dyn(climax)
                    .o2(F).o2(F).o2(C).o2(F).o2(F).o2(C).o2(F).o2(F).done()
                .bar()
                    .o2(E).o2(E).o2(B).o2(E).o2(E).o2(B).o2(E).o2(E).done()
                .bar()
                    .o2(HALF, A).o2(QUARTER, E).o2(QUARTER, A).done()
                .build(attacca());
    }

    // ── Organ Pad (Rock Organ) ─────────────────────────────────────────

    private List<Phrase> buildOrganPerSection() {
        // Pickup (1 bar)
        var pickup = newBuilder().bar().mp().o3(WHOLE, A).done().build(attacca());

        // Verse 1: standard (10 bars)
        var v1 = buildOrganVerse(Dynamic.MP, Dynamic.MF);

        // Verse 2: building (10 bars)
        var v2 = buildOrganVerse(Dynamic.MF, Dynamic.F);

        // Verse 3: climax (10 bars)
        var v3 = buildOrganVerse(Dynamic.F, Dynamic.FF);

        // Verse 4: sharp drop, fading to nothing (11 bars)
        var v4 = newBuilder()
                .bar().p().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().pp().o3(WHOLE, C).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().ppp().o3(WHOLE, D).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, F).done()
                .bar().o3(WHOLE, E).done()
                .bar().o3(WHOLE, A).done()
                .bar().r(WHOLE).done()
                .build(end());

        return List.of(pickup, v1, v2, v3, v4);
    }

    /** Organ pad for one verse (10 bars). */
    private MelodicPhrase buildOrganVerse(Dynamic verse, Dynamic climax) {
        return newBuilder()
                .bar().dyn(verse)
                    .o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, C).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, A).done()
                .bar().dyn(climax)
                    .o3(WHOLE, D).done()
                .bar().o3(WHOLE, A).done()
                .bar().o3(WHOLE, F).done()
                .bar().o3(WHOLE, E).done()
                .bar().o3(WHOLE, A).done()
                .build(attacca());
    }

    // ── Drums ──────────────────────────────────────────────────────────

    private List<Phrase> buildDrumsPerSection() {
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

        // ── Assemble per-section DrumPhrases ──

        // Pickup (1 bar)
        var pickupNodes = new ArrayList<PhraseNode>();
        pickupNodes.add(new DynamicNode(Dynamic.MF));
        pickupNodes.addAll(pickupBar);
        var pickup = new DrumPhrase(pickupNodes, attacca());

        // Verse 1: standard rock (10 bars)
        var v1Nodes = new ArrayList<PhraseNode>();
        v1Nodes.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 4; i++) v1Nodes.addAll(rockBar);       // bars 1–4
        v1Nodes.addAll(fillBar);                                    // bar 5
        for (int i = 0; i < 4; i++) v1Nodes.addAll(rockBar);       // bars 6–9
        v1Nodes.addAll(crashEnd);                                   // bar 10
        var v1 = new DrumPhrase(v1Nodes, attacca());

        // Verse 2: building — rock→hard rock (10 bars)
        var v2Nodes = new ArrayList<PhraseNode>();
        v2Nodes.add(new DynamicNode(Dynamic.F));
        for (int i = 0; i < 4; i++) v2Nodes.addAll(rockBar);       // bars 1–4
        v2Nodes.addAll(fillBar);                                    // bar 5
        v2Nodes.add(new DynamicNode(Dynamic.FF));
        for (int i = 0; i < 4; i++) v2Nodes.addAll(hardRockBar);   // bars 6–9
        v2Nodes.addAll(crashEnd);                                   // bar 10
        var v2 = new DrumPhrase(v2Nodes, attacca());

        // Verse 3: strongest climax — crashes, open hats, max intensity (10 bars)
        var v3Nodes = new ArrayList<PhraseNode>();
        v3Nodes.add(new DynamicNode(Dynamic.FFF));
        v3Nodes.addAll(crashEnd);                                   // bar 1  crash intro
        for (int i = 0; i < 3; i++) v3Nodes.addAll(crashRockBar);  // bars 2–4
        v3Nodes.addAll(fillBar);                                    // bar 5
        for (int i = 0; i < 4; i++) v3Nodes.addAll(crashRockBar);  // bars 6–9
        v3Nodes.addAll(crashEnd);                                   // bar 10
        var v3 = new DrumPhrase(v3Nodes, attacca());

        // Verse 4: sharp drop — ride only, fading to silence (11 bars)
        var v4Nodes = new ArrayList<PhraseNode>();
        v4Nodes.add(new DynamicNode(Dynamic.P));
        for (int i = 0; i < 4; i++) v4Nodes.addAll(rideOnly);      // bars 1–4
        v4Nodes.add(new DynamicNode(Dynamic.PP));
        for (int i = 0; i < 3; i++) v4Nodes.addAll(rideOnly);      // bars 5–7
        v4Nodes.add(new DynamicNode(Dynamic.PPP));
        v4Nodes.addAll(rideOnly);                                   // bar 8
        v4Nodes.addAll(rideOnly);                                   // bar 9
        v4Nodes.addAll(silentBar);                                  // bar 10
        v4Nodes.addAll(silentBar);                                  // bar 11 fade-out
        var v4 = new DrumPhrase(v4Nodes, end());

        return List.of(pickup, v1, v2, v3, v4);
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new DefaultNovemberStorm());
    }
}
