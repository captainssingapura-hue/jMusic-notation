package music.notation.songs;

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

final class DefaultAntsGoMarching implements PieceContentProvider<AntsGoMarching> {

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

        var lead = new Track("Lead", FLUTE, List.of(verse1, verse2, ending));

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

        var chords = new Track("Chords", ACOUSTIC_GUITAR_NYLON, List.of(
                new ChordPhrase(List.of(Am, CM, Am, Em), cm),
                new ChordPhrase(List.of(Am, Dm, CM, Am), cm),
                new ChordPhrase(List.of(Am, Dm, Em, Am), ce)));

        // --- Bass (dotted-quarter root notes, 2 per measure) ---
        var bass = new Track("Bass", ACOUSTIC_BASS, List.of(
                new MelodicPhrase(
                        List.of(new DynamicNode(Dynamic.MF),
                                nd(A,2,QUARTER), nd(A,2,QUARTER), nd(C,3,QUARTER), nd(C,3,QUARTER),
                                nd(A,2,QUARTER), nd(A,2,QUARTER), nd(E,2,QUARTER), nd(E,2,QUARTER)),
                        attacca()),
                new MelodicPhrase(
                        List.of(nd(A,2,QUARTER), nd(A,2,QUARTER), nd(D,3,QUARTER), nd(D,3,QUARTER),
                                nd(C,3,QUARTER), nd(C,3,QUARTER), nd(A,2,QUARTER), nd(A,2,QUARTER)),
                        attacca()),
                new MelodicPhrase(
                        List.of(nd(A,2,QUARTER), nd(A,2,QUARTER), nd(D,3,QUARTER), nd(D,3,QUARTER),
                                nd(E,2,QUARTER), nd(E,2,QUARTER), nd(A,2,QUARTER), nd(A,2,QUARTER)),
                        end())));

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

        var drums = new Track("Drums", DRUM_KIT, List.of(
                new DrumPhrase(drumPhrase1Nodes, attacca()),
                new DrumPhrase(drumPhrase2Nodes, attacca()),
                new DrumPhrase(drumPhrase3Nodes, end())));

        return new Piece(id.title(), id.composer(),
                new KeySignature(A, Mode.MINOR), new TimeSignature(6, 8),
                new Tempo(132, QUARTER), List.of(lead, chords, bass, drums));
    }
}
