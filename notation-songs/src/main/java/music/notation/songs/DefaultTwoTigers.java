package music.notation.songs;

import music.notation.chord.MajorTriad;
import music.notation.chord.MinorTriad;
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

final class DefaultTwoTigers implements PieceContentProvider<TwoTigers> {

    private static final TimeSignature TS = new TimeSignature(4, 4);
    private static final KeySignature C_MAJOR = new KeySignature(C, Mode.MAJOR);
    private static final KeySignature D_MINOR = new KeySignature(D, Mode.MINOR);

    @Override
    public Piece create() {
        final var id = new TwoTigers();

        var P = StaffPhraseBuilder.in(TS);  // default EIGHTH

        // ── Melody in C major (absolute pitch names) ──

        // A: "两只老虎 两只老虎" — C D E C | C D E C
        var a = P
                .bar().mf().o5(QUARTER, C).o5(QUARTER, D).o5(QUARTER, E).o5(QUARTER, C)
                .bar().o5(QUARTER, C).o5(QUARTER, D).o5(QUARTER, E).o5(QUARTER, C)
                .build(attacca());

        // B: "跑得快 跑得快" — E F G - | E F G -
        var b = P
                .bar().o5(QUARTER, E).o5(QUARTER, F).o5(HALF, G)
                .bar().o5(QUARTER, E).o5(QUARTER, F).o5(HALF, G)
                .build(attacca());

        // C: "一只没有眼睛 一只没有尾巴" — G̲A̲ G̲F̲ E C | G̲A̲ G̲F̲ E C
        var c = P
                .bar().o5(G).o5(A).o5(G).o5(F).o5(QUARTER, E).o5(QUARTER, C)
                .bar().o5(G).o5(A).o5(G).o5(F).o5(QUARTER, E).o5(QUARTER, C)
                .build(attacca());

        // D: "真奇怪 真奇怪" — C G₃ C - | C G₃ C -
        var d = P
                .bar().o5(QUARTER, C).o4(QUARTER, G).o5(HALF, C)
                .bar().o5(QUARTER, C).o4(QUARTER, G).o5(HALF, C)
                .build(breath());

        // ── Section 2: same melody shifted to D minor ──
        var aMinor = new ShiftedPhrase(a, C_MAJOR, D_MINOR);
        var bMinor = new ShiftedPhrase(b, C_MAJOR, D_MINOR);
        var cMinor = new ShiftedPhrase(c, C_MAJOR, D_MINOR);

        var dEnd = P
                .bar().o5(QUARTER, C).o4(QUARTER, G).o5(HALF, C)
                .bar().o5(QUARTER, C).o4(QUARTER, G).o5(HALF, C)
                .build(end());
        var dMinor = new ShiftedPhrase(dEnd, C_MAJOR, D_MINOR);

        var melody = new Track("Melody", ACOUSTIC_GRAND_PIANO, List.of(
                a, b, c, d,
                aMinor, bMinor, cMinor, dMinor));

        // ── Chords: I–IV–V–I in C, then i–iv–v–i in Dm ──
        final var cMaj  = new MajorTriad(C, 3);
        final var fMaj  = new MajorTriad(F, 3);
        final var gMaj  = new MajorTriad(G, 3);
        var I  = chord(WHOLE, cMaj);
        var IV = chord(WHOLE, fMaj);
        var V  = chord(WHOLE, gMaj);
        var cm = new PhraseMarking(PhraseConnection.ATTACCA, false);

        var cMajChords = new ChordPhrase(List.of(I, I, IV, I, I, I, V, I), cm);

        final var dMin = new MinorTriad(D, 3);
        final var gMin = new MinorTriad(G, 3);
        final var aMin = new MinorTriad(A, 3);
        var Dm = chord(WHOLE, dMin);
        var Gm = chord(WHOLE, gMin);
        var Am = chord(WHOLE, aMin);
        var ce = new PhraseMarking(PhraseConnection.CAESURA, false);

        var dMinChords = new ChordPhrase(List.of(Dm, Dm, Gm, Dm, Dm, Dm, Am, Dm), ce);

        var chords = new Track("Chords", ACOUSTIC_GUITAR_NYLON, List.of(cMajChords, dMinChords));

        // ── Drums: 16 bars of 4/4 ──
        var drumBar = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));
        var dpNodes = new ArrayList<PhraseNode>();
        dpNodes.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 8; i++) dpNodes.addAll(drumBar);
        var drumPhrase1 = new DrumPhrase(dpNodes, attacca());

        var dpNodes2 = new ArrayList<PhraseNode>();
        dpNodes2.add(new DynamicNode(Dynamic.F));
        for (int i = 0; i < 8; i++) dpNodes2.addAll(drumBar);
        var drumPhrase2 = new DrumPhrase(dpNodes2, end());

        var drums = new Track("Drums", DRUM_KIT, List.of(drumPhrase1, drumPhrase2));

        return new Piece(id.title(), id.composer(),
                C_MAJOR, TS, new Tempo(132, QUARTER),
                List.of(melody, chords, drums));
    }
}
