package music.notation.songs;

import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

final class DefaultTwoTigersCanon implements PieceContentProvider<TwoTigersCanon> {

    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Override
    public Piece create() {
        final var id = new TwoTigersCanon();

        var P = StaffPhraseBuilder.in(TS);

        // ── Melody phrases (same as Two Tigers) ──

        // A: C D E C | C D E C
        var a = P
                .bar().mf().o5(C, QUARTER).o5(D, QUARTER).o5(E, QUARTER).o5(C, QUARTER)
                .bar().o5(C, QUARTER).o5(D, QUARTER).o5(E, QUARTER).o5(C, QUARTER)
                .build(attacca());

        // B: E F G - | E F G -
        var b = P
                .bar().o5(E, QUARTER).o5(F, QUARTER).o5(G, HALF)
                .bar().o5(E, QUARTER).o5(F, QUARTER).o5(G, HALF)
                .build(attacca());

        // C: G̲A̲ G̲F̲ E C | G̲A̲ G̲F̲ E C
        var c = P
                .bar().o5(G).o5(A).o5(G).o5(F).o5(E, QUARTER).o5(C, QUARTER)
                .bar().o5(G).o5(A).o5(G).o5(F).o5(E, QUARTER).o5(C, QUARTER)
                .build(attacca());

        // D: C G₃ C - | C G₃ C -
        var d = P
                .bar().o5(C, QUARTER).o4(G, QUARTER).o5(C, HALF)
                .bar().o5(C, QUARTER).o4(G, QUARTER).o5(C, HALF)
                .build(attacca());

        // D with final ending
        var dEnd = P
                .bar().o5(C, QUARTER).o4(G, QUARTER).o5(C, HALF)
                .bar().o5(C, QUARTER).o4(G, QUARTER).o5(C, HALF)
                .build(end());

        // ── 1-bar rest (one bar of silence) ──
        var rest1 = new MelodicPhrase(
                List.of(new RestNode(Duration.of(WHOLE))),
                attacca());

        // ── Voice 1 (Piano): A B C D A B C D _ _ ──
        var voice1 = new Track("Voice 1", ACOUSTIC_GRAND_PIANO, List.of(
                a, b, c, d,
                a, b, c, dEnd,
                rest1, rest1));

        // ── Voice 2 (Strings): _ A B C D A B C D _ ──
        var voice2 = new Track("Voice 2", STRING_ENSEMBLE_1, List.of(
                rest1,
                a, b, c, d,
                a, b, c, dEnd,
                rest1));

        // ── Voice 3 (Flute): _ _ A B C D A B C D ──
        var voice3 = new Track("Voice 3", FLUTE, List.of(
                rest1, rest1,
                a, b, c, d,
                a, b, c, dEnd));

        // ── Drums: 18 bars of 4/4 ──
        var drumBar = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));

        var dpNodes = new ArrayList<PhraseNode>();
        dpNodes.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 18; i++) dpNodes.addAll(drumBar);
        var drums = new Track("Drums", DRUM_KIT, List.of(new DrumPhrase(dpNodes, end())));

        return new Piece(id.title(), id.composer(),
                new KeySignature(C, Mode.MAJOR), TS, new Tempo(144, QUARTER),
                List.of(voice1, voice2, voice3, drums));
    }
}
