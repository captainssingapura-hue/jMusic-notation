package music.notation.songs.nursery.antsmarching;

import music.notation.chord.MajorTriad;
import music.notation.chord.MinorTriad;
import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.Ornament.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

public final class DefaultAntsGoMarching implements PieceContentProvider<AntsGoMarching> {

    @Override
    public Piece create() {
        final var id = new AntsGoMarching();

        // "The Ants Go Marching" in A minor, 6/8 time — 4 tracks: lead, chords, bass, drums

        // --- Lead melody ---
        var verse1 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.MF),
                        n(A,4,EIGHTH), n(A,4,EIGHTH), n(A,4,EIGHTH), n(A,4,EIGHTH), n(C,5,EIGHTH), n(E,5,EIGHTH),
                        nd(E,5,QUARTER), n(D,5,EIGHTH), n(C,5,EIGHTH), n(D,5,EIGHTH),
                        n(A,4,EIGHTH), n(A,4,EIGHTH), n(A,4,EIGHTH), n(A,4,EIGHTH), n(C,5,EIGHTH), n(E,5,EIGHTH),
                        n(E,5,EIGHTH), n(D,5,EIGHTH), n(C,5,EIGHTH), nd(B,4,QUARTER)),
                breath());

        var verse2 = new MelodicPhrase(
                List.of(n(A,4,EIGHTH), n(A,4,EIGHTH), n(A,4,EIGHTH), n(A,4,EIGHTH), n(C,5,EIGHTH), n(E,5,EIGHTH),
                        n(E,5,EIGHTH), n(F,5,EIGHTH), n(E,5,EIGHTH), n(D,5,EIGHTH), n(C,5,EIGHTH), n(D,5,EIGHTH),
                        orn(E,5,EIGHTH, MORDENT), n(C,5,EIGHTH), n(A,4,EIGHTH), n(C,5,EIGHTH), n(B,4,EIGHTH), n(A,4,EIGHTH),
                        nd(A,4,QUARTER), new RestNode(Duration.dotted(QUARTER))),
                breath());

        var ending = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.F),
                        nd(A,4,QUARTER), nd(C,5,QUARTER),
                        n(E,5,EIGHTH), n(D,5,EIGHTH), n(C,5,EIGHTH), n(D,5,EIGHTH), n(C,5,EIGHTH), n(B,4,EIGHTH),
                        n(A,4,EIGHTH), n(C,5,EIGHTH), n(E,5,EIGHTH), orn(D,5,EIGHTH, TURN), n(C,5,EIGHTH), n(B,4,EIGHTH),
                        nd(A,4,HALF)),
                end());

        // --- Chords (dotted-half per measure in 6/8) ---
        final var aMin = new MinorTriad(A, 3);
        final var dMin = new MinorTriad(D, 3);
        final var eMin = new MinorTriad(E, 3);
        final var cMaj = new MajorTriad(C, 3);
        var Am = dchord(HALF, aMin);
        var Dm = dchord(HALF, dMin);
        var Em = dchord(HALF, eMin);
        var CM = dchord(HALF, cMaj);
        var cm = new PhraseMarking(PhraseConnection.ATTACCA, false);
        var ce = new PhraseMarking(PhraseConnection.CAESURA, false);

        var chordsV1 = new ChordPhrase(List.of(Am, CM, Am, Em), cm);
        var chordsV2 = new ChordPhrase(List.of(Am, Dm, CM, Am), cm);
        var chordsEnd = new ChordPhrase(List.of(Am, Dm, Em, Am), ce);

        // --- Bass (dotted-quarter root notes, 2 per measure) ---
        var bassV1 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.MF),
                        nd(A,2,QUARTER), nd(A,2,QUARTER), nd(C,3,QUARTER), nd(C,3,QUARTER),
                        nd(A,2,QUARTER), nd(A,2,QUARTER), nd(E,2,QUARTER), nd(E,2,QUARTER)),
                attacca());
        var bassV2 = new MelodicPhrase(
                List.of(nd(A,2,QUARTER), nd(A,2,QUARTER), nd(D,3,QUARTER), nd(D,3,QUARTER),
                        nd(C,3,QUARTER), nd(C,3,QUARTER), nd(A,2,QUARTER), nd(A,2,QUARTER)),
                attacca());
        var bassEnd = new MelodicPhrase(
                List.of(nd(A,2,QUARTER), nd(A,2,QUARTER), nd(D,3,QUARTER), nd(D,3,QUARTER),
                        nd(E,2,QUARTER), nd(E,2,QUARTER), nd(A,2,QUARTER), nd(A,2,QUARTER)),
                end());

        // --- Drums (6/8 march: kick-hat-hat-snare-hat-hat per measure) ---
        var drumMeasure = List.<PhraseNode>of(
                d(BASS_DRUM, EIGHTH), d(CLOSED_HI_HAT, EIGHTH), d(CLOSED_HI_HAT, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(CLOSED_HI_HAT, EIGHTH), d(CLOSED_HI_HAT, EIGHTH));
        var drumMeasureCrash = List.<PhraseNode>of(
                d(CRASH_CYMBAL, EIGHTH), d(CLOSED_HI_HAT, EIGHTH), d(CLOSED_HI_HAT, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(CLOSED_HI_HAT, EIGHTH), d(CLOSED_HI_HAT, EIGHTH));

        var drumPhrase1Nodes = new ArrayList<PhraseNode>();
        drumPhrase1Nodes.add(new DynamicNode(Dynamic.MF));
        drumPhrase1Nodes.addAll(drumMeasure);
        drumPhrase1Nodes.addAll(drumMeasure);
        drumPhrase1Nodes.addAll(drumMeasure);
        drumPhrase1Nodes.addAll(drumMeasure);

        var drumPhrase2Nodes = new ArrayList<PhraseNode>();
        drumPhrase2Nodes.addAll(drumMeasure);
        drumPhrase2Nodes.addAll(drumMeasure);
        drumPhrase2Nodes.addAll(drumMeasure);
        drumPhrase2Nodes.addAll(drumMeasure);

        var drumPhrase3Nodes = new ArrayList<PhraseNode>();
        drumPhrase3Nodes.add(new DynamicNode(Dynamic.F));
        drumPhrase3Nodes.addAll(drumMeasureCrash);
        drumPhrase3Nodes.addAll(drumMeasure);
        drumPhrase3Nodes.addAll(drumMeasure);
        drumPhrase3Nodes.addAll(drumMeasure);

        var drumsV1  = new DrumPhrase(drumPhrase1Nodes, attacca());
        var drumsV2  = new DrumPhrase(drumPhrase2Nodes, attacca());
        var drumsEnd = new DrumPhrase(drumPhrase3Nodes, end());

        // --- Sections: 4 bars × 6/8 = 192/64 each ---
        final var KEY = new KeySignature(A, Mode.MINOR);
        final var TS = new TimeSignature(6, 8);
        final var SECTION_DURATION = Duration.ofSixtyFourths(4 * TS.barSixtyFourths());

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Lead",   FLUTE),
                new TrackDecl.MusicTrackDecl("Chords", ACOUSTIC_GUITAR_NYLON),
                new TrackDecl.MusicTrackDecl("Bass",   ACOUSTIC_BASS),
                new TrackDecl.MusicTrackDecl("Drums",  DRUM_KIT)
        );

        final var sections = List.of(
                section("Verse 1", SECTION_DURATION, TS, verse1, chordsV1, bassV1, drumsV1),
                section("Verse 2", SECTION_DURATION, TS, verse2, chordsV2, bassV2, drumsV2),
                section("Ending",  SECTION_DURATION, TS, ending, chordsEnd, bassEnd, drumsEnd)
        );

        return Piece.ofSections(id.title(), id.composer(), KEY, TS,
                new Tempo(132, QUARTER), trackDecls, sections);
    }

    private static Section section(String name, Duration duration, TimeSignature ts,
                                   Phrase lead, Phrase chords, Phrase bass, Phrase drums) {
        return Section.named(name)
                .duration(duration)
                .timeSignature(ts)
                .track("Lead",   lead)
                .track("Chords", chords)
                .track("Bass",   bass)
                .track("Drums",  drums)
                .build();
    }
}
