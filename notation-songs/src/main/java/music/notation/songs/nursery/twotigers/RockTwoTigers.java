package music.notation.songs.nursery.twotigers;

import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.pitch.Note;
import music.notation.play.PlayPiece;
import music.notation.songs.anthem.internationale.ManualInternationale;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * Two Tigers (两只老虎) — Rock arrangement with orchestral layers.
 *
 * <p>Four-section key progression with escalating intensity:</p>
 * <ul>
 *   <li>Section 1 — D major: standard rock, establishes the groove</li>
 *   <li>Section 2 — G major: building intensity, strings and horn enter</li>
 *   <li>Section 3 — C major (upper octave): full orchestral climax</li>
 *   <li>Section 4 — E major: soft emotional ending, augmented rhythm</li>
 * </ul>
 *
 * <p>7 tracks: distortion lead, overdriven rhythm, bass, strings,
 * French horn, choir, and drums.</p>
 */
public final class RockTwoTigers implements PieceContentProvider<TwoTigers> {

    private static final KeySignature KEY_DM = new KeySignature(D, Mode.MAJOR);
    private static final KeySignature KEY_GM = new KeySignature(G, Mode.MAJOR);
    private static final KeySignature KEY_CM = new KeySignature(C, Mode.MAJOR);
    private static final KeySignature KEY_EM = new KeySignature(E, Mode.MAJOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Override
    public String subtitle() { return "Rock"; }

    /** Each of the 4 sections is 8 bars in 4/4 = 512/64. */
    private static final Duration SECTION_DURATION = Duration.ofSixtyFourths(8 * 64);

    private static final String[] SECTION_NAMES = {
        "Section 1 (D major)",
        "Section 2 (G major)",
        "Section 3 (C major)",
        "Section 4 (E major)"
    };

    @Override
    public Piece create() {
        final var id = new TwoTigers();

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Lead Guitar",   DISTORTION_GUITAR),
                new TrackDecl.MusicTrackDecl("Rhythm Guitar", OVERDRIVEN_GUITAR),
                new TrackDecl.MusicTrackDecl("Bass",          ELECTRIC_BASS_PICK),
                new TrackDecl.MusicTrackDecl("Strings",       STRING_ENSEMBLE_1),
                new TrackDecl.MusicTrackDecl("French Horn",   FRENCH_HORN),
                new TrackDecl.MusicTrackDecl("Choir",         CHOIR_AAHS),
                new TrackDecl.MusicTrackDecl("Drums",         DRUM_KIT),
                new TrackDecl.MusicTrackDecl("Lyrics",        CHOIR_AAHS)
        );

        // Per-track per-section phrase lists. Each list has exactly 4 entries.
        final var lead    = leadGuitarPerSection();
        final var rhythm  = rhythmGuitarPerSection();
        final var bass    = bassPerSection();
        final var strings = stringsPerSection();
        final var horn    = hornPerSection();
        final var choir   = choirPerSection();
        final var drums   = drumsPerSection();
        final var lyrics  = lyricsPerSection();

        final var sections = new ArrayList<Section>();
        for (int i = 0; i < SECTION_NAMES.length; i++) {
            sections.add(Section.named(SECTION_NAMES[i])
                    .duration(SECTION_DURATION)
                    .timeSignature(TS)
                    .track("Lead Guitar",   lead.get(i))
                    .track("Rhythm Guitar", rhythm.get(i))
                    .track("Bass",          bass.get(i))
                    .track("Strings",       strings.get(i))
                    .track("French Horn",   horn.get(i))
                    .track("Choir",         choir.get(i))
                    .track("Drums",         drums.get(i))
                    .track("Lyrics",        lyrics.get(i))
                    .build());
        }

        return Piece.ofSections(id.title(), id.composer(),
                KEY_DM, TS, new Tempo(138, QUARTER),
                trackDecls,
                sections);
    }

    /** 8-bar rest placeholder (4/4 = 64 sixty-fourths per bar). */
    private static RestPhrase rest8(PhraseMarking m) {
        return new RestPhrase(Duration.ofSixtyFourths(8 * 64), m);
    }

    private static RestPhrase rest8() { return rest8(attacca()); }

    // ── Lead Guitar (Distortion) ──────────────────────────────────────

    private static List<Phrase> leadGuitarPerSection() {
        // Section 1: D major, o4 melody, mf→f
        var Dm = StaffPhraseBuilderTyped.in(KEY_DM, TS, EIGHTH);
        var s1 = Dm
                .bar().mf().o4(QUARTER, D).o4(QUARTER, E).o4(QUARTER, F).o4(QUARTER, D).done()
                .bar()     .o4(QUARTER, D).o4(QUARTER, E).o4(QUARTER, F).o4(QUARTER, D).done()
                .bar()     .o4(QUARTER, F).o4(QUARTER, G).o4(HALF, A).done()
                .bar()     .o4(QUARTER, F).o4(QUARTER, G).o4(HALF, A).done()
                .bar().f() .o4(A).o4(B).o4(A).o4(G).o4(QUARTER, F).o4(QUARTER, D).done()
                .bar()     .o4(A).o4(B).o4(A).o4(G).o4(QUARTER, F).o4(QUARTER, D).done()
                .bar()     .o4(QUARTER, D).o3(QUARTER, A).o4(HALF, D).done()
                .bar()     .o4(QUARTER, D).o3(QUARTER, A).o4(HALF, D).done()
                .build(attacca());

        // Section 2: G minor, o4–o5, f→ff
        var Gm = StaffPhraseBuilderTyped.in(KEY_GM, TS, EIGHTH);
        var s2 = Gm
                .bar().f() .o4(QUARTER, G).o4(QUARTER, A).o4(QUARTER, B).o4(QUARTER, G).done()
                .bar()     .o4(QUARTER, G).o4(QUARTER, A).o4(QUARTER, B).o4(QUARTER, G).done()
                .bar()     .o4(QUARTER, B).o5(QUARTER, C).o5(HALF, D).done()
                .bar()     .o4(QUARTER, B).o5(QUARTER, C).o5(HALF, D).done()
                .bar().ff().o5(D).o5(E).o5(D).o5(C).o4(QUARTER, B).o4(QUARTER, G).done()
                .bar()     .o5(D).o5(E).o5(D).o5(C).o4(QUARTER, B).o4(QUARTER, G).done()
                .bar()     .o4(QUARTER, G).o4(QUARTER, D).o4(HALF, G).done()
                .bar()     .o4(QUARTER, G).o4(QUARTER, D).o4(HALF, G).done()
                .build(attacca());

        // Section 3: C minor, o6 upper octave climax, ff→fff
        var Cm = StaffPhraseBuilderTyped.in(KEY_CM, TS, EIGHTH);
        var s3 = Cm
                .bar().ff() .o6(QUARTER, C).o6(QUARTER, D).o6(QUARTER, E).o6(QUARTER, C).done()
                .bar()      .o6(QUARTER, C).o6(QUARTER, D).o6(QUARTER, E).o6(QUARTER, C).done()
                .bar()      .o6(QUARTER, E).o6(QUARTER, F).o6(HALF, G).done()
                .bar()      .o6(QUARTER, E).o6(QUARTER, F).o6(HALF, G).done()
                .bar().fff().o6(G).o6(A).o6(G).o6(F).o6(QUARTER, E).o6(QUARTER, C).done()
                .bar()      .o6(G).o6(A).o6(G).o6(F).o6(QUARTER, E).o6(QUARTER, C).done()
                .bar()      .o6(QUARTER, C).o5(QUARTER, G).o6(HALF, C).done()
                .bar()      .o6(QUARTER, C).o5(QUARTER, G).o6(HALF, C).done()
                .build(attacca());

        // Section 4: E major, augmented rhythm (each motif → 2 bars), pp→ppp
        var EM = StaffPhraseBuilderTyped.in(KEY_EM, TS, HALF);
        var s4 = EM
                .bar().pp() .o5(E).o5(F).done()                          // A (bar 1)
                .bar()      .o5(G).o5(E).done()                          // A (bar 2)
                .bar()      .o5(G).o5(A).done()                          // B (bar 1)
                .bar()      .o5(WHOLE, B).done()                         // B (bar 2)
                .bar().ppp().o5(QUARTER, B).o6(QUARTER, C)        // C (bar 1)
                            .o5(QUARTER, B).o5(QUARTER, A).done()
                .bar()      .o5(G).o5(E).done()                          // C (bar 2)
                .bar()      .o5(E).o4(B).done()                          // D (bar 1)
                .bar()      .o5(WHOLE, E).done()                         // D (bar 2)
                .build(end());

        return List.of(s1, s2, s3, s4);
    }

    // ── Rhythm Guitar (Power chords) ──────────────────────────────────

    private static List<Phrase> rhythmGuitarPerSection() {
        var pD = chord(WHOLE, p(D, 3), p(A, 3));
        var pG = chord(WHOLE, p(G, 3), p(D, 4));
        var pA = chord(WHOLE, p(A, 3), p(E, 4));
        var pC = chord(WHOLE, p(C, 4), p(G, 4));
        var pF = chord(WHOLE, p(F, 3), p(C, 4));

        // i–i–iv–i–i–i–v–i for each key
        var s1 = new ChordPhrase(List.of(pD, pD, pG, pD, pD, pD, pA, pD), attacca());
        var s2 = new ChordPhrase(List.of(pG, pG, pC, pG, pG, pG, pD, pG), attacca());
        var s3 = new ChordPhrase(List.of(pC, pC, pF, pC, pC, pC, pG, pC), attacca());
        var s4 = rest8(end()); // no distortion in the soft ending

        return List.of(s1, s2, s3, s4);
    }

    // ── Bass ──────────────────────────────────────────────────────────

    private static List<Phrase> bassPerSection() {
        var s1 = drivingBass(KEY_DM, Dynamic.MF, D, G, A);
        var s2 = drivingBass(KEY_GM, Dynamic.F,  G, C, D);
        var s3 = drivingBass(KEY_CM, Dynamic.FF, C, F, G);

        // Section 4: sustained whole-note roots
        var EM = StaffPhraseBuilderTyped.in(KEY_EM, TS);
        var s4 = EM
                .bar().pp() .o2(WHOLE, E).done().bar().o2(WHOLE, E).done()
                .bar()      .o2(WHOLE, A).done().bar().o2(WHOLE, E).done()
                .bar().ppp().o2(WHOLE, E).done().bar().o2(WHOLE, A).done()
                .bar()      .o2(WHOLE, B).done().bar().o2(WHOLE, E).done()
                .build(end());

        return List.of(s1, s2, s3, s4);
    }

    /** Driving eighth-note bass line for one section (8 bars). */
    private static MelodicPhrase drivingBass(KeySignature key, Dynamic dyn,
                                             Note root, Note iv, Note v) {
        var P = StaffPhraseBuilderTyped.in(key, TS, EIGHTH);
        return P
                .bar().dyn(dyn)
                    .o2(root).o2(root).o2(root).o2(root)
                    .o2(root).o2(root).o2(root).o2(root).done()
                .bar()
                    .o2(root).o2(root).o2(root).o2(root)
                    .o2(root).o2(root).o2(root).o2(root).done()
                .bar()
                    .o2(iv).o2(iv).o2(iv).o2(iv)
                    .o2(iv).o2(iv).o2(iv).o2(iv).done()
                .bar()
                    .o2(root).o2(root).o2(root).o2(root)
                    .o2(root).o2(root).o2(root).o2(root).done()
                .bar()
                    .o2(root).o2(root).o2(root).o2(root)
                    .o2(root).o2(root).o2(root).o2(root).done()
                .bar()
                    .o2(root).o2(root).o2(root).o2(root)
                    .o2(root).o2(root).o2(root).o2(root).done()
                .bar()
                    .o2(v).o2(v).o2(v).o2(v)
                    .o2(v).o2(v).o2(v).o2(v).done()
                .bar()
                    .o2(root).o2(root).o2(root).o2(root)
                    .o2(root).o2(root).o2(root).o2(root).done()
                .build(attacca());
    }

    // ── Strings ───────────────────────────────────────────────────────

    private static List<Phrase> stringsPerSection() {
        // Section 1: D minor, soft sustained triads
        var sDm = StaffPhraseBuilderTyped.in(KEY_DM, TS);
        var s1 = sDm
                .bar().mp().o3(WHOLE, D, F, A).done()
                .bar()     .o3(WHOLE, D, F, A).done()
                .bar()     .o3(WHOLE, G, B, D.higher(1)).done()
                .bar()     .o3(WHOLE, D, F, A).done()
                .bar()     .o3(WHOLE, D, F, A).done()
                .bar()     .o3(WHOLE, D, F, A).done()
                .bar()     .o3(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar()     .o3(WHOLE, D, F, A).done()
                .build(attacca());

        // Section 2: G minor, building
        var sGm = StaffPhraseBuilderTyped.in(KEY_GM, TS);
        var s2 = sGm
                .bar().mf().o3(WHOLE, G, B, D.higher(1)).done()
                .bar()     .o3(WHOLE, G, B, D.higher(1)).done()
                .bar()     .o4(WHOLE, C, E, G).done()
                .bar()     .o3(WHOLE, G, B, D.higher(1)).done()
                .bar()     .o3(WHOLE, G, B, D.higher(1)).done()
                .bar()     .o3(WHOLE, G, B, D.higher(1)).done()
                .bar()     .o3(WHOLE, D, F, A).done()
                .bar()     .o3(WHOLE, G, B, D.higher(1)).done()
                .build(attacca());

        // Section 3: C minor, full orchestral
        var sCm = StaffPhraseBuilderTyped.in(KEY_CM, TS);
        var s3 = sCm
                .bar().f() .o4(WHOLE, C, E, G).done()
                .bar()     .o4(WHOLE, C, E, G).done()
                .bar()     .o3(WHOLE, F, A, C.higher(1)).done()
                .bar()     .o4(WHOLE, C, E, G).done()
                .bar().ff().o4(WHOLE, C, E, G).done()
                .bar()     .o4(WHOLE, C, E, G).done()
                .bar()     .o3(WHOLE, G, B, D.higher(1)).done()
                .bar()     .o4(WHOLE, C, E, G).done()
                .build(attacca());

        // Section 4: E major, warm and prominent
        var sEM = StaffPhraseBuilderTyped.in(KEY_EM, TS);
        var s4 = sEM
                .bar().mf().o3(WHOLE, E, G, B).done()
                .bar()     .o3(WHOLE, E, G, B).done()
                .bar()     .o3(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar()     .o3(WHOLE, E, G, B).done()
                .bar().p() .o3(WHOLE, E, G, B).done()
                .bar()     .o3(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar()     .o3(WHOLE, B, D.higher(1), F.higher(1)).done()
                .bar()     .o3(WHOLE, E, G, B).done()
                .build(end());

        return List.of(s1, s2, s3, s4);
    }

    // ── French Horn ───────────────────────────────────────────────────

    private static List<Phrase> hornPerSection() {
        // Section 1: rest — horn enters in section 2

        // Section 2: G minor, sustained roots
        var hGm = StaffPhraseBuilderTyped.in(KEY_GM, TS);
        var s2 = hGm
                .bar().mp().o4(WHOLE, G).done().bar().o4(WHOLE, G).done()
                .bar()     .o4(WHOLE, C).done().bar().o4(WHOLE, G).done()
                .bar().mf().o4(WHOLE, G).done().bar().o4(WHOLE, G).done()
                .bar()     .o4(WHOLE, D).done().bar().o4(WHOLE, G).done()
                .build(attacca());

        // Section 3: C minor, heroic held notes
        var hCm = StaffPhraseBuilderTyped.in(KEY_CM, TS);
        var s3 = hCm
                .bar().f() .o5(WHOLE, C).done().bar().o5(WHOLE, C).done()
                .bar()     .o4(WHOLE, F).done().bar().o5(WHOLE, C).done()
                .bar().ff().o5(WHOLE, C).done().bar().o5(WHOLE, C).done()
                .bar()     .o4(WHOLE, G).done().bar().o5(WHOLE, C).done()
                .build(attacca());

        // Section 4: E major, gentle sustained melody
        var hEM = StaffPhraseBuilderTyped.in(KEY_EM, TS);
        var s4 = hEM
                .bar().p() .o4(WHOLE, E).done().bar().o4(WHOLE, E).done()
                .bar()     .o4(WHOLE, A).done().bar().o4(WHOLE, E).done()
                .bar().pp().o4(WHOLE, E).done().bar().o4(WHOLE, A).done()
                .bar()     .o4(WHOLE, B).done().bar().o4(WHOLE, E).done()
                .build(end());

        return List.of(rest8(), s2, s3, s4);
    }

    // ── Choir ─────────────────────────────────────────────────────────

    private static List<Phrase> choirPerSection() {
        // Sections 1–2: rest — choir enters at the climax

        // Section 3: C minor, sustained "aahs"
        var cCm = StaffPhraseBuilderTyped.in(KEY_CM, TS);
        var s3 = cCm
                .bar().f() .o5(WHOLE, C, E, G).done()
                .bar()     .o5(WHOLE, C, E, G).done()
                .bar()     .o4(WHOLE, F, A, C.higher(1)).done()
                .bar()     .o5(WHOLE, C, E, G).done()
                .bar().ff().o5(WHOLE, C, E, G).done()
                .bar()     .o5(WHOLE, C, E, G).done()
                .bar()     .o4(WHOLE, G, B, D.higher(1)).done()
                .bar()     .o5(WHOLE, C, E, G).done()
                .build(attacca());

        // Section 4: E major, soft ethereal aahs
        var cEM = StaffPhraseBuilderTyped.in(KEY_EM, TS);
        var s4 = cEM
                .bar().pp() .o4(WHOLE, E, G, B).done()
                .bar()      .o4(WHOLE, E, G, B).done()
                .bar()      .o4(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar()      .o4(WHOLE, E, G, B).done()
                .bar().ppp().o4(WHOLE, E, G, B).done()
                .bar()      .o4(WHOLE, A, C.higher(1), E.higher(1)).done()
                .bar()      .o4(WHOLE, B, D.higher(1), F.higher(1)).done()
                .bar()      .o4(WHOLE, E, G, B).done()
                .build(end());

        return List.of(rest8(), rest8(), s3, s4);
    }

    // ── Drums ─────────────────────────────────────────────────────────

    private static List<Phrase> drumsPerSection() {
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

        var crashBar = List.<PhraseNode>of(
                d(CRASH_CYMBAL, EIGHTH),   d(OPEN_HI_HAT, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(OPEN_HI_HAT, EIGHTH),
                d(BASS_DRUM, EIGHTH),      d(CRASH_CYMBAL, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(CRASH_CYMBAL, EIGHTH));

        var fillBar = List.<PhraseNode>of(
                d(ACOUSTIC_SNARE, EIGHTH), d(HIGH_TOM, EIGHTH),
                d(HIGH_MID_TOM, EIGHTH),   d(LOW_MID_TOM, EIGHTH),
                d(LOW_TOM, EIGHTH),        d(LOW_FLOOR_TOM, EIGHTH),
                d(BASS_DRUM, EIGHTH),      d(CRASH_CYMBAL, EIGHTH));

        var rideBar = List.<PhraseNode>of(
                d(RIDE_CYMBAL, QUARTER), d(RIDE_CYMBAL, QUARTER),
                d(RIDE_CYMBAL, QUARTER), d(RIDE_CYMBAL, QUARTER));

        var silentBar = List.<PhraseNode>of(d(CLOSED_HI_HAT, WHOLE));

        // Section 1: standard rock (8 bars)
        var s1Nodes = new ArrayList<PhraseNode>();
        s1Nodes.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 7; i++) s1Nodes.addAll(rockBar);
        s1Nodes.addAll(fillBar);
        var s1 = new DrumPhrase(s1Nodes, attacca());

        // Section 2: building — rock → hard rock (8 bars)
        var s2Nodes = new ArrayList<PhraseNode>();
        s2Nodes.add(new DynamicNode(Dynamic.F));
        for (int i = 0; i < 4; i++) s2Nodes.addAll(rockBar);
        s2Nodes.add(new DynamicNode(Dynamic.FF));
        for (int i = 0; i < 3; i++) s2Nodes.addAll(hardRockBar);
        s2Nodes.addAll(fillBar);
        var s2 = new DrumPhrase(s2Nodes, attacca());

        // Section 3: climax — crashes and cymbals (8 bars)
        var s3Nodes = new ArrayList<PhraseNode>();
        s3Nodes.add(new DynamicNode(Dynamic.FFF));
        for (int i = 0; i < 7; i++) s3Nodes.addAll(crashBar);
        s3Nodes.addAll(fillBar);
        var s3 = new DrumPhrase(s3Nodes, attacca());

        // Section 4: sparse ride, fading (8 bars)
        var s4Nodes = new ArrayList<PhraseNode>();
        s4Nodes.add(new DynamicNode(Dynamic.PP));
        for (int i = 0; i < 6; i++) s4Nodes.addAll(rideBar);
        s4Nodes.add(new DynamicNode(Dynamic.PPP));
        s4Nodes.addAll(rideBar);
        s4Nodes.addAll(silentBar);
        var s4 = new DrumPhrase(s4Nodes, end());

        return List.of(s1, s2, s3, s4);
    }

    // ── Lyrics ─────────────────────────────────────────────────────────

    private static List<Phrase> lyricsPerSection() {
        // Standard verse: A A B B C C D D — each syllable matches a note
        var verse = new LyricPhrase(List.of(
                // A: 两只老虎 (4 quarters)
                ly("两", QUARTER), ly("只", QUARTER), ly("老", QUARTER), ly("虎", QUARTER),
                // A: 两只老虎
                ly("两", QUARTER), ly("只", QUARTER), ly("老", QUARTER), ly("虎", QUARTER),
                // B: 跑得快 (quarter, quarter, half)
                ly("跑", QUARTER), ly("得", QUARTER), ly("快", HALF),
                // B: 跑得快
                ly("跑", QUARTER), ly("得", QUARTER), ly("快", HALF),
                // C: 一只没有眼睛 (6 × eighth + 2 × quarter... 4 eighths + 2 quarters)
                ly("一", EIGHTH), ly("只", EIGHTH), ly("没", EIGHTH), ly("有", EIGHTH),
                ly("眼", QUARTER), ly("睛", QUARTER),
                // C: 一只没有尾巴
                ly("一", EIGHTH), ly("只", EIGHTH), ly("没", EIGHTH), ly("有", EIGHTH),
                ly("尾", QUARTER), ly("巴", QUARTER),
                // D: 真奇怪 (quarter, quarter, half)
                ly("真", QUARTER), ly("奇", QUARTER), ly("怪", HALF),
                // D: 真奇怪
                ly("真", QUARTER), ly("奇", QUARTER), ly("怪", HALF)
        ), attacca());

        // Section 4: augmented rhythm — each motif once, note values doubled
        var ending = new LyricPhrase(List.of(
                // A augmented: 2 bars
                ly("两", HALF), ly("只", HALF), ly("老", HALF), ly("虎", HALF),
                // B augmented: 2 bars
                ly("跑", HALF), ly("得", HALF), ly("快", WHOLE),
                // C augmented: 2 bars
                ly("一", QUARTER), ly("只", QUARTER), ly("没", QUARTER), ly("有", QUARTER),
                ly("眼", HALF), ly("睛", HALF),
                // D augmented: 2 bars
                ly("真", HALF), ly("奇", HALF), ly("怪", WHOLE)
        ), end());

        // Same lyrics for sections 1–3, augmented for section 4
        return List.of(verse, verse, verse, ending);
    }

    private static LyricEvent ly(String syllable, Duration duration) {
        return new LyricEvent(syllable, duration);
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new RockTwoTigers());
    }
}
