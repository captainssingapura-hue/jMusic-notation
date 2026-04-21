package music.notation.songs.classical.odetojoy;

import music.notation.chord.MajorTriad;
import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.Ornament.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

public final class DefaultOdeToJoy implements PieceContentProvider<OdeToJoy> {

    @Override
    public Piece create() {
        final var id = new OdeToJoy();

        // --- Melody ---
        var phrase1 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.MF),
                        n(E,5,QUARTER), n(E,5,QUARTER), n(F,5,QUARTER), n(G,5,QUARTER),
                        n(G,5,QUARTER), n(F,5,QUARTER), n(E,5,QUARTER), n(D,5,QUARTER)),
                attacca());
        var phrase2 = new MelodicPhrase(
                List.of(n(C,5,QUARTER), n(C,5,QUARTER), n(D,5,QUARTER), n(E,5,QUARTER),
                        orn(E,5, QUARTER, MORDENT), new RestNode(Duration.of(EIGHTH)),
                        n(D,5,EIGHTH), orn(D,5,HALF, TURN)),
                breath());
        var phrase3 = new MelodicPhrase(
                List.of(n(E,5,QUARTER), n(E,5,QUARTER), n(F,5,QUARTER), n(G,5,QUARTER),
                        n(G,5,QUARTER), n(F,5,QUARTER), n(E,5,QUARTER), n(D,5,QUARTER)),
                attacca());
        var phrase4 = new MelodicPhrase(
                List.of(n(C,5,QUARTER), n(C,5,QUARTER), n(D,5,QUARTER), n(E,5,QUARTER),
                        orn(D,5,QUARTER, MORDENT), new RestNode(Duration.of(EIGHTH)),
                        n(C,5,EIGHTH), orn(C,5,HALF, TURN)),
                end());

        // --- Chords ---
        final var CMaj = new MajorTriad(C, 3);
        final var FMaj = new MajorTriad(F, 3);
        final var GMaj = new MajorTriad(G, 3);
        var I  = chord(HALF, CMaj);
        var IV = chord(HALF, FMaj);
        var V  = chord(HALF, GMaj);
        var cm = new PhraseMarking(PhraseConnection.ATTACCA, false);
        var ce = new PhraseMarking(PhraseConnection.CAESURA, false);

        // --- Drums (stately 4/4: kick-hat-snare-hat, 2 bars per phrase) ---
        var dp = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));
        var dpFirst = new java.util.ArrayList<PhraseNode>();
        dpFirst.add(new DynamicNode(Dynamic.MF));
        dpFirst.addAll(dp);

        // --- Sections: 4 phrases × 2 bars each (classic AABA') ---
        final var KEY = new KeySignature(C, Mode.MAJOR);
        final var TS = new TimeSignature(4, 4);
        final var SECTION_DURATION = Duration.ofSixtyFourths(2 * 64);

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", VIOLIN),
                new TrackDecl.MusicTrackDecl("Chords", ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Drums",  DRUM_KIT)
        );

        final var sections = List.of(
                section("A1", SECTION_DURATION, TS,
                        phrase1, new ChordPhrase(List.of(I, I, V, I),  cm), new DrumPhrase(dpFirst, attacca())),
                section("A2", SECTION_DURATION, TS,
                        phrase2, new ChordPhrase(List.of(I, IV, V, I), cm), new DrumPhrase(dp, attacca())),
                section("B",  SECTION_DURATION, TS,
                        phrase3, new ChordPhrase(List.of(I, I, V, I),  cm), new DrumPhrase(dp, attacca())),
                section("A'", SECTION_DURATION, TS,
                        phrase4, new ChordPhrase(List.of(I, IV, V, I), ce), new DrumPhrase(dp, end()))
        );

        return Piece.ofSections(id.title(), id.composer(), KEY, TS,
                new Tempo(108, QUARTER), trackDecls, sections);
    }

    private static Section section(String name, Duration duration, TimeSignature ts,
                                   Phrase melody, Phrase chords, Phrase drums) {
        return Section.named(name)
                .duration(duration)
                .timeSignature(ts)
                .track("Melody", melody)
                .track("Chords", chords)
                .track("Drums",  drums)
                .build();
    }
}
