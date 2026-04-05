package music.notation.songs;

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

final class DefaultOdeToJoy implements PieceContentProvider<OdeToJoy> {

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

        var melody = new Track("Melody", VIOLIN, List.of(phrase1, phrase2, phrase3, phrase4));

        // --- Chords ---
        final var CMaj = new MajorTriad(C, 3);
        final var FMaj = new MajorTriad(F, 3);
        final var GMaj = new MajorTriad(G, 3);
        var I  = chord(HALF, CMaj);
        var IV = chord(HALF, FMaj);
        var V  = chord(HALF, GMaj);
        var cm = new PhraseMarking(PhraseConnection.ATTACCA, false);
        var ce = new PhraseMarking(PhraseConnection.CAESURA, false);

        var accompaniment = new Track("Chords", ACOUSTIC_GRAND_PIANO, List.of(
                new ChordPhrase(List.of(I, I, V, I), cm),
                new ChordPhrase(List.of(I, IV, V, I), cm),
                new ChordPhrase(List.of(I, I, V, I), cm),
                new ChordPhrase(List.of(I, IV, V, I), ce)));

        // --- Drums (stately 4/4: kick-hat-snare-hat, 2 measures per phrase) ---
        var dp = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));

        var dpFirst = new java.util.ArrayList<PhraseNode>();
        dpFirst.add(new DynamicNode(Dynamic.MF));
        dpFirst.addAll(dp);

        var drums = new Track("Drums", DRUM_KIT, List.of(
                new DrumPhrase(dpFirst, attacca()),
                new DrumPhrase(dp, attacca()),
                new DrumPhrase(dp, attacca()),
                new DrumPhrase(dp, end())));

        return new Piece(id.title(), id.composer(),
                new KeySignature(C, Mode.MAJOR), new TimeSignature(4, 4),
                new Tempo(108, QUARTER), List.of(melody, accompaniment, drums));
    }
}
