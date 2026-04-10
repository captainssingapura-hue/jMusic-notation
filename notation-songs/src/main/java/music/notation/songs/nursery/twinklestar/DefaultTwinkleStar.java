package music.notation.songs.nursery.twinklestar;

import music.notation.chord.MajorTriad;
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

public final class DefaultTwinkleStar implements PieceContentProvider<TwinkleStar> {

    @Override
    public Piece create() {
        final var id = new TwinkleStar();

        // --- Melody ---
        var m1a = new MelodicPhrase(
                List.of(n(C,5,QUARTER), n(C,5,QUARTER), n(G,5,QUARTER), n(G,5,QUARTER)),
                attacca());
        var m1b = new MelodicPhrase(
                List.of(n(A,5,QUARTER), n(A,5,QUARTER), orn(G,5,HALF, MORDENT)),
                breath());

        var phrase1 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.MF), new SubPhrase(m1a), new SubPhrase(m1b)),
                breath());
        var phrase2 = new MelodicPhrase(
                List.of(n(F,5,QUARTER), n(F,5,QUARTER), n(E,5,QUARTER), n(E,5,QUARTER),
                        n(D,5,QUARTER), n(D,5,QUARTER), orn(C,5,HALF, TURN)),
                breath());
        var phrase3 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.F),
                        new GraceNote(p(A,5), false),
                        n(G,5,QUARTER), n(G,5,QUARTER), n(F,5,QUARTER), n(F,5,QUARTER),
                        orn(E,5,QUARTER, TRILL), n(E,5,QUARTER), n(D,5,HALF)),
                breath());
        var phrase4 = new MelodicPhrase(
                List.of(new GraceNote(p(A,5), true),
                        n(G,5,QUARTER), n(G,5,QUARTER), n(F,5,QUARTER), n(F,5,QUARTER),
                        n(E,5,QUARTER), n(E,5,QUARTER), orn(D,5,HALF, MORDENT)),
                breath());
        var m5b = new MelodicPhrase(
                List.of(n(A,5,QUARTER), n(A,5,QUARTER), orn(G,5,HALF, TREMOLO)),
                breath());
        var phrase5 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.MP), new SubPhrase(m1a), new SubPhrase(m5b)),
                breath());
        var phrase6 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.P),
                        n(F,5,QUARTER), n(F,5,QUARTER), n(E,5,QUARTER), n(E,5,QUARTER),
                        n(D,5,QUARTER), n(D,5,QUARTER), orn(C,5,HALF, LOWER_MORDENT)),
                end());

        var melody = Track.of("Melody", FLUTE, List.of(
                phrase1, phrase2, phrase3, phrase4, phrase5, phrase6));

        // --- Chords ---
        final var CMaj = new MajorTriad(C, 3);
        final var FMaj = new MajorTriad(F, 3);
        final var GMaj = new MajorTriad(G, 3);
        var I  = chord(HALF, CMaj);
        var IV = chord(HALF, FMaj);
        var V  = chord(HALF, GMaj);
        var cm = new PhraseMarking(PhraseConnection.ATTACCA, false);
        var ce = new PhraseMarking(PhraseConnection.CAESURA, false);

        var accompaniment = Track.of("Chords", ACOUSTIC_GUITAR_NYLON, List.of(
                new ChordPhrase(List.of(I, I, IV, I), cm),
                new ChordPhrase(List.of(IV, I, V, I), cm),
                new ChordPhrase(List.of(I, V, I, V), cm),
                new ChordPhrase(List.of(I, V, I, V), cm),
                new ChordPhrase(List.of(I, I, IV, I), cm),
                new ChordPhrase(List.of(IV, I, V, I), ce)));

        // --- Drums (gentle 4/4: kick-hat-snare-hat per measure, 2 measures per phrase) ---
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
                new DrumPhrase(dp, attacca()),
                new DrumPhrase(dp, attacca()),
                new DrumPhrase(dp, end())));

        return new Piece(id.title(), id.composer(),
                new KeySignature(C, Mode.MAJOR), new TimeSignature(4, 4),
                new Tempo(120, QUARTER), List.of(melody, accompaniment, drums));
    }
}
