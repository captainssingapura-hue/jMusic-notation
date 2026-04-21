package music.notation.songs.nursery.marylamb;

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

public final class DefaultMaryHadALittleLamb implements PieceContentProvider<MaryHadALittleLamb> {

    @Override
    public Piece create() {
        final var id = new MaryHadALittleLamb();

        // --- Melody ---
        var phrase1 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.MF),
                        n(E,5,QUARTER), n(D,5,QUARTER), n(C,5,QUARTER), n(D,5,QUARTER),
                        n(E,5,QUARTER), n(E,5,QUARTER), orn(E,5,HALF, MORDENT)),
                breath());
        var phrase2 = new MelodicPhrase(
                List.of(n(D,5,QUARTER), n(D,5,QUARTER), orn(D,5,HALF, TURN),
                        n(E,5,QUARTER), n(G,5,QUARTER), orn(G,5,HALF, MORDENT)),
                breath());
        var phrase3 = new MelodicPhrase(
                List.of(n(E,5,QUARTER), n(D,5,QUARTER), n(C,5,QUARTER), n(D,5,QUARTER),
                        n(E,5,QUARTER), n(E,5,QUARTER), n(E,5,QUARTER), n(E,5,QUARTER)),
                attacca());
        var phrase4 = new MelodicPhrase(
                List.of(n(D,5,QUARTER), n(D,5,QUARTER), n(E,5,QUARTER), n(D,5,QUARTER),
                        orn(C,5,HALF, LOWER_MORDENT), new RestNode(Duration.of(HALF))),
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

        // --- Drums (light 4/4: kick-hat-snare-hat, 2 bars per phrase) ---
        var dp = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));
        var dpFirst = new java.util.ArrayList<PhraseNode>();
        dpFirst.add(new DynamicNode(Dynamic.MP));
        dpFirst.addAll(dp);

        // --- Sections: 4 lyric phrases × 2 bars each ---
        final var KEY = new KeySignature(C, Mode.MAJOR);
        final var TS = new TimeSignature(4, 4);
        final var SECTION_DURATION = Duration.ofSixtyFourths(2 * 64);

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Chords", STRING_ENSEMBLE_1),
                new TrackDecl.MusicTrackDecl("Drums",  DRUM_KIT)
        );

        final var sections = List.of(
                section("Mary had a little lamb",       SECTION_DURATION, TS,
                        phrase1, new ChordPhrase(List.of(I, I, I, V), cm), new DrumPhrase(dpFirst, attacca())),
                section("Little lamb, little lamb",     SECTION_DURATION, TS,
                        phrase2, new ChordPhrase(List.of(V, V, I, I), cm), new DrumPhrase(dp, attacca())),
                section("Mary had a little lamb (rep)", SECTION_DURATION, TS,
                        phrase3, new ChordPhrase(List.of(I, I, I, IV), cm), new DrumPhrase(dp, attacca())),
                section("Its fleece was white as snow", SECTION_DURATION, TS,
                        phrase4, new ChordPhrase(List.of(V, V, V, I), ce), new DrumPhrase(dp, end()))
        );

        return Piece.ofSections(id.title(), id.composer(), KEY, TS,
                new Tempo(112, QUARTER), trackDecls, sections);
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
