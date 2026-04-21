package music.notation.songs.folk.katyusha;

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
import static music.notation.songs.folk.katyusha.KatyushaTracks.*;

/**
 * Katyusha (Катюша) — Rock arrangement.
 *
 * <p>Seven verses modulating by fourths with escalating dynamics:
 * D→G→C→F, then D→G→C again one dynamic level stronger.
 * Reuses melody, bass, and chord templates from {@link KatyushaTracks},
 * applying sparse overrides for dynamics and {@link ShiftedPhrase} for
 * key/octave transposition.</p>
 *
 * <p>Sectional structure: one section per verse plus a final Coda section.
 * Each section supplies one phrase per named track; homogeneity is
 * enforced by the piece-level {@link Piece#ofSections} constructor.</p>
 */
public final class RockKatyusha implements PieceContentProvider<Katyusha> {

    // ── Verse specifications ─────────────────────────────────────

    private record VerseSpec(KeySignature key, int octaveShift, Dynamic dyn) {}

    private static final KeySignature DM = KEY; // D minor (source key)

    private static final KeySignature GM  = new KeySignature(G, Mode.MINOR);
    private static final KeySignature CM  = new KeySignature(C, Mode.MINOR);
    private static final KeySignature FM  = new KeySignature(F, Mode.MINOR);
    private static final VerseSpec[] VERSES = {
        // Pass 1: D → G → C → F
        new VerseSpec(DM, 0, Dynamic.F),
        new VerseSpec(GM, 0, Dynamic.FF),
        new VerseSpec(CM, 1, Dynamic.FFF),
        new VerseSpec(FM, 0, Dynamic.FF),
        // Pass 2: D → G → C (one level stronger)
        new VerseSpec(DM, 0, Dynamic.FF),
        new VerseSpec(GM, 0, Dynamic.FFF),
        new VerseSpec(CM, 1, Dynamic.FFF),
    };

    /** Coda: bars 5–8 in C minor octave 5, softer. */
    private static final VerseSpec CODA = new VerseSpec(CM, 1, Dynamic.MF);

    /** 8 bars × 4/4 bar = 512/64 per verse; coda is 4 bars = 256/64. */
    private static final Duration VERSE_DURATION = Duration.ofSixtyFourths(8 * 64);
    private static final Duration CODA_DURATION  = Duration.ofSixtyFourths(4 * 64);

    // ── Piece assembly ───────────────────────────────────────────

    @Override
    public Piece create() {
        final var id = new Katyusha();

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", DISTORTION_GUITAR),
                new TrackDecl.MusicTrackDecl("Chords", DISTORTION_GUITAR),
                new TrackDecl.MusicTrackDecl("Bass",   ELECTRIC_BASS_PICK),
                new TrackDecl.MusicTrackDecl("Drums",  DRUM_KIT)
        );

        // Per-track per-section phrase lists. Each list has exactly 8 entries:
        // 7 verses + 1 coda. Indexing is aligned across tracks.
        final var melody = melodyPerSection();
        final var bass   = bassPerSection();
        final var chords = chordsPerSection();
        final var drums  = drumsPerSection();

        final var sections = new ArrayList<Section>();
        for (int i = 0; i < VERSES.length; i++) {
            sections.add(sectionFor("Verse " + (i + 1), VERSE_DURATION,
                    melody.get(i), chords.get(i), bass.get(i), drums.get(i)));
        }
        int codaIdx = VERSES.length;
        sections.add(sectionFor("Coda", CODA_DURATION,
                melody.get(codaIdx), chords.get(codaIdx),
                bass.get(codaIdx),   drums.get(codaIdx)));

        return Piece.ofSections(id.title(), id.composer(),
                KEY, TS,
                new Tempo(110, QUARTER),
                trackDecls,
                sections);
    }

    private static Section sectionFor(String name, Duration duration,
                                      Phrase melody, Phrase chords,
                                      Phrase bass, Phrase drums) {
        return Section.named(name)
                .duration(duration)
                .timeSignature(TS)
                .track("Melody", melody)
                .track("Chords", chords)
                .track("Bass",   bass)
                .track("Drums",  drums)
                .build();
    }

    // ── Melody (7 verses + coda) ─────────────────────────────────

    private static List<Phrase> melodyPerSection() {
        var template = buildVerse(attacca());
        var phrases = new ArrayList<Phrase>();

        for (VerseSpec v : VERSES) {
            // Override bar 0 to inject dynamic; all verses attacca (coda follows).
            // Voice overlays ride along on the MelodicPhrase — ShiftedPhrase
            // post-shifts all emitted MIDI notes, so voices shift with the main.
            var overridden = OverlayBuilder.over(template, KEY, TS, EIGHTH)
                    .at(0, b -> b.dyn(v.dyn)
                        .o4(QUARTER.dot(), D).o4(EIGHTH, E)
                        .o5(QUARTER.dot(), F).o4(EIGHTH, D))
                    .build(attacca());
            phrases.add(shift(overridden, v));
        }

        // Coda: bars 5–8 repeated softer in C minor (octave 5), rit. to 80 BPM.
        var coda = StaffPhraseBuilderTyped.in(KEY, TS, EIGHTH)
                .bar(QUARTER).dyn(Dynamic.MF).transitionStart()
                    .o5(A).o5(D).o5(C).o5(EIGHTH,D).o5(EIGHTH,C)
                    .aux(HALF, a -> a.o4(F).o4(F.s())).done()
                .bar(QUARTER).o4(B,G).o4(EIGHTH,A).o4(EIGHTH,G).o4(A).o4(D).done()
                .bar(EIGHTH).r(EIGHTH).o4(QUARTER,B).o4(G).o4(QUARTER.dot(),A).o4(F).done()
                .bar(EIGHTH).o4(E).o3(A).o4(F).o4(E).rit(80).o4(QUARTER,D).r(QUARTER).done()
                .build(end());
        phrases.add(shift(coda, CODA));

        return phrases;
    }

    // ── Bass ─────────────────────────────────────────────────────

    private static List<Phrase> bassPerSection() {
        var template = buildBassVerse(attacca());
        var phrases = new ArrayList<Phrase>();

        for (VerseSpec v : VERSES) {
            var overridden = OverlayBuilder.over(template, KEY, TS, QUARTER)
                    .at(0, b -> b.dyn(v.dyn).o3(D).o3(A).o3(D).o3(A))
                    .build(attacca());
            phrases.add(shift(overridden, v));
        }

        // Coda: bars 5–8 (Bb, Gm→Dm, Gm→Dm, A→Dm) softer — fresh builder.
        var coda = StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().dyn(Dynamic.MF).o2(B).o3(F).o2(B).o3(F).done()
                .bar().o3(G).o3(D).o3(D).o3(A).done()
                .bar().o3(G).o3(D).o3(D).o3(A).done()
                .bar().o2(A).o3(E).o2(HALF, D).done()
                .build(end());
        phrases.add(shift(coda, CODA));

        return phrases;
    }

    // ── Chords ───────────────────────────────────────────────────

    private static List<Phrase> chordsPerSection() {
        var template = buildChordVerse(attacca());
        var phrases = new ArrayList<Phrase>();

        for (VerseSpec v : VERSES) {
            var overridden = OverlayBuilder.over(template, KEY, TS, HALF)
                    .at(0, b -> b.dyn(v.dyn).o4(D, F, A).o4(D, F, A))
                    .build(attacca());
            phrases.add(shift(overridden, v));
        }

        // Coda: bars 5–8 (Bb, Gm→Dm, Gm→Dm, A→Dm) softer — fresh builder.
        var coda = StaffPhraseBuilderTyped.in(KEY, TS, HALF)
                .bar().dyn(Dynamic.MF)
                    .o3(B, D.higher(1), F.higher(1)).o3(B, D.higher(1), F.higher(1)).done()
                .bar().o4(G, B, D.higher(1)).o4(D, F, A).done()
                .bar().o4(G, B, D.higher(1)).o4(D, F, A).done()
                .bar().o4(A, C.s().higher(1), E.higher(1)).o4(D, F, A).done()
                .build(end());
        phrases.add(shift(coda, CODA));

        return phrases;
    }

    // ── Key/octave shift helper ──────────────────────────────────

    private static Phrase shift(Phrase phrase, VerseSpec v) {
        if (v.key.equals(DM) && v.octaveShift == 0) return phrase;
        return new ShiftedPhrase(phrase, DM, v.key, v.octaveShift);
    }

    // ── Drums (per-section DrumPhrases; 7 verses + coda) ─────────

    private static List<Phrase> drumsPerSection() {
        var phrases = new ArrayList<Phrase>();

        // ── Pass 1: D → G → C → F ──

        // Verse 1 (D minor, f): standard drive
        phrases.add(buildDrumVerse(Dynamic.F,   DrumPattern.DRIVE,       true));
        // Verse 2 (G minor, ff): open hi-hat drive
        phrases.add(buildDrumVerse(Dynamic.FF,  DrumPattern.OPEN_DRIVE,  false));
        // Verse 3 (C minor, fff): double-time
        phrases.add(buildDrumVerse(Dynamic.FFF, DrumPattern.DOUBLE_TIME, true));
        // Verse 4 (F minor, ff): half-time breather
        phrases.add(buildDrumVerse(Dynamic.FF,  DrumPattern.HALF_TIME,   false));

        // ── Pass 2: D → G → C (one level stronger) ──

        // Verse 5 (D minor, ff): open drive
        phrases.add(buildDrumVerse(Dynamic.FF,  DrumPattern.OPEN_DRIVE,  true));
        // Verse 6 (G minor, fff): double-time
        phrases.add(buildDrumVerse(Dynamic.FFF, DrumPattern.DOUBLE_TIME, false));
        // Verse 7 (C minor, fff): double-time climax → transition fill
        phrases.add(buildDrumVerse(Dynamic.FFF, DrumPattern.DOUBLE_TIME, true));

        // Coda: 4 bars half-time, softer → crash ending.
        var codaNodes = new ArrayList<PhraseNode>();
        codaNodes.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 3; i++) halfTimeBar(codaNodes);
        codaNodes.add(d(CRASH_CYMBAL, EIGHTH));
        codaNodes.add(d(BASS_DRUM, EIGHTH));
        codaNodes.add(d(ACOUSTIC_SNARE, QUARTER));
        codaNodes.add(new RestNode(HALF));
        phrases.add(new DrumPhrase(codaNodes, end()));

        return phrases;
    }

    /** Drum pattern selector for the 7-bar body of a verse. */
    private enum DrumPattern { DRIVE, OPEN_DRIVE, DOUBLE_TIME, HALF_TIME }

    /**
     * Build one verse's DrumPhrase: a starting dynamic, 7 bars of the chosen
     * pattern, and a fill (A or B) for bar 8. Result totals {@value #VERSE_DURATION}/64.
     */
    private static DrumPhrase buildDrumVerse(Dynamic dyn, DrumPattern pattern, boolean useFillA) {
        var nodes = new ArrayList<PhraseNode>();
        nodes.add(new DynamicNode(dyn));
        for (int i = 0; i < 7; i++) {
            switch (pattern) {
                case DRIVE       -> driveBar(nodes);
                case OPEN_DRIVE  -> openDriveBar(nodes);
                case DOUBLE_TIME -> doubleTimeBar(nodes);
                case HALF_TIME   -> halfTimeBar(nodes);
            }
        }
        if (useFillA) fillA(nodes); else fillB(nodes);
        return new DrumPhrase(nodes, attacca());
    }

    // ── Drum patterns ────────────────────────────────────────────

    private static void driveBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(CLOSED_HI_HAT, EIGHTH));
    }

    private static void openDriveBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(OPEN_HI_HAT, EIGHTH));
    }

    private static void halfTimeBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, QUARTER));
        out.add(d(RIDE_CYMBAL, QUARTER));
        out.add(d(ACOUSTIC_SNARE, QUARTER));
        out.add(d(RIDE_CYMBAL, QUARTER));
    }

    private static void doubleTimeBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, SIXTEENTH));     out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH)); out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH)); out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH)); out.add(d(BASS_DRUM, SIXTEENTH));
        out.add(d(BASS_DRUM, SIXTEENTH));     out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH)); out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH)); out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(BASS_DRUM, SIXTEENTH));     out.add(d(CLOSED_HI_HAT, SIXTEENTH));
    }

    private static void fillA(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(HIGH_TOM, EIGHTH));     out.add(d(HIGH_MID_TOM, EIGHTH));
        out.add(d(LOW_TOM, EIGHTH));      out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    private static void fillB(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH)); out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH)); out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(CRASH_CYMBAL, EIGHTH)); out.add(d(BASS_DRUM, EIGHTH));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new RockKatyusha());
    }
}
