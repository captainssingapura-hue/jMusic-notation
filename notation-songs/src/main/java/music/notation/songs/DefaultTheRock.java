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
import static music.notation.pitch.Accidental.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

final class DefaultTheRock implements PieceContentProvider<TheRock> {

    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Override
    public Piece create() {
        final var id = new TheRock();

        var P = StaffPhraseBuilder.in(TS, QUARTER);   // default = QUARTER for march

        // ── Main theme (G minor) ──
        var phrase1 = P
                // Bar 1: D Bb A G (four quarters)
                .bar().f()
                    .r(EIGHTH).o5(D, EIGHTH).o5(B, FLAT, EIGHTH).o5(A, EIGHTH).o5d(G, QUARTER).r(EIGHTH)
                // Bar 2: Bb G Eb- (Q Q H)
                .bar()
                    .o5(B, FLAT, EIGHTH).o5(G, EIGHTH).o6d(E, FLAT, QUARTER).r(EIGHTH).o6(C, QUARTER)
                // Bar 4: G A Bb C Bb A_ (eighths, A held as dotted quarter)
                .bar()
                    .o6(D, QUARTER).o5(B, FLAT, QUARTER).r(EIGHTH).o5d(G, EIGHTH).o5d(A, EIGHTH)
                .bar()
                    .o5(B, FLAT, QUARTER).o6(C, QUARTER).slurStart().o6(C, SIXTEENTH).slurEnd().o5(B, FLAT, QUARTER).r(SIXTEENTH).o5(A, EIGHTH)
                .bar()
                    .o5(G, EIGHTH).o5(A, QUARTER).ff().r(EIGHTH).o5d(D, EIGHTH).fff().o6(D, FLAT, SIXTEENTH).o6(C, SIXTEENTH).o5d(B, FLAT, EIGHTH)
                .bar().o5(A, EIGHTH).o5(G, HALF).rd(QUARTER)
                .build(end());

        var melody = new Track("Melody", FRENCH_HORN, List.of(phrase1));

        // ── Power chords (strings) — Gm, Eb, Cm, Dm, Gm, F ──
        final var dMinor  = new MinorTriad(D, 3);
        final var gMinor  = new MinorTriad(G, 3);
        final var ebMajor = new MajorTriad(E, FLAT, 3);
        final var cMinor  = new MinorTriad(C, 3);
        final var fMajor  = new MajorTriad(F, 3);
        var Dm    = chord(WHOLE, dMinor);
        var Gm    = chord(WHOLE, gMinor);
        var EbMaj = chord(WHOLE, ebMajor);
        var Cm    = chord(WHOLE, cMinor);
        var Fmaj  = chord(WHOLE, fMajor);
        var ce   = new PhraseMarking(PhraseConnection.CAESURA, false);

        var chords = new Track("Strings", STRING_ENSEMBLE_1, List.of(
                new ChordPhrase(List.of(Gm, EbMaj, Cm, Dm, Gm, Fmaj), ce)));

        // ── Drums: military march, 6 bars ──
        var drumBar = List.<PhraseNode>of(
                d(BASS_DRUM, EIGHTH), d(BASS_DRUM, EIGHTH),
                d(CLOSED_HI_HAT, EIGHTH), d(CLOSED_HI_HAT, EIGHTH),
                d(ACOUSTIC_SNARE, EIGHTH), d(ACOUSTIC_SNARE, EIGHTH),
                d(CLOSED_HI_HAT, EIGHTH), d(CLOSED_HI_HAT, EIGHTH));

        var dpNodes = new ArrayList<PhraseNode>();
        dpNodes.add(new DynamicNode(Dynamic.F));
        for (int i = 0; i < 6; i++) dpNodes.addAll(drumBar);
        var drums = new Track("Drums", DRUM_KIT, List.of(new DrumPhrase(dpNodes, end())));

        return new Piece(id.title(), id.composer(),
                new KeySignature(G, Mode.MINOR), TS, new Tempo(108, QUARTER),
                List.of(melody, chords, drums));
    }
}
