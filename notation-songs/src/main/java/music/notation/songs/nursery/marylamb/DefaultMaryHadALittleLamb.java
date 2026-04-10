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

        var melody = Track.of("Melody", ACOUSTIC_GRAND_PIANO, List.of(
                phrase1, phrase2, phrase3, phrase4));

        // --- Chords ---
        final var CMaj = new MajorTriad(C, 3);
        final var FMaj = new MajorTriad(F, 3);
        final var GMaj = new MajorTriad(G, 3);
        var I  = chord(HALF, CMaj);
        var IV = chord(HALF, FMaj);
        var V  = chord(HALF, GMaj);
        var cm = new PhraseMarking(PhraseConnection.ATTACCA, false);
        var ce = new PhraseMarking(PhraseConnection.CAESURA, false);

        var accompaniment = Track.of("Chords", STRING_ENSEMBLE_1, List.of(
                new ChordPhrase(List.of(I, I, I, V), cm),
                new ChordPhrase(List.of(V, V, I, I), cm),
                new ChordPhrase(List.of(I, I, I, IV), cm),
                new ChordPhrase(List.of(V, V, V, I), ce)));

        // --- Drums (light 4/4: kick-hat-snare-hat, 2 measures per phrase) ---
        var dp = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));

        var dpFirst = new java.util.ArrayList<PhraseNode>();
        dpFirst.add(new DynamicNode(Dynamic.MP));
        dpFirst.addAll(dp);

        var drums = Track.of("Drums", DRUM_KIT, List.of(
                new DrumPhrase(dpFirst, attacca()),
                new DrumPhrase(dp, attacca()),
                new DrumPhrase(dp, attacca()),
                new DrumPhrase(dp, end())));

        return new Piece(id.title(), id.composer(),
                new KeySignature(C, Mode.MAJOR), new TimeSignature(4, 4),
                new Tempo(112, QUARTER), List.of(melody, accompaniment, drums));
    }
}
