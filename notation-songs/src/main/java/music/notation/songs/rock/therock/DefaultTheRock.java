package music.notation.songs.rock.therock;

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

public final class DefaultTheRock implements PieceContentProvider<TheRock> {

    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Override
    public Piece create() {
        final var id = new TheRock();

        var P = StaffPhraseBuilderTyped.in(TS, QUARTER);   // default = QUARTER for march

        // ── Main theme (G minor) ──
        var phrase1 = P
                // Bar 1: D Bb A G (four quarters)
                .bar().f()
                    .r(EIGHTH).o5(EIGHTH, D).o5(EIGHTH, B.f()).o5(EIGHTH, A).o5(QUARTER.dot(), G).r(EIGHTH).done()
                // Bar 2: Bb G Eb- (Q Q H)
                .bar()
                    .o5(EIGHTH, B.f()).o5(EIGHTH, G).o6(QUARTER.dot(), E.f()).r(EIGHTH).o6(QUARTER, C).done()
                // Bar 4: G A Bb C Bb A_ (eighths, A held as dotted quarter)
                .bar()
                    .o6(QUARTER, D).o5(QUARTER, B.f()).r(EIGHTH).o5(EIGHTH.dot(), G).o5(EIGHTH.dot(), A).done()
                .bar()
                    .o5(QUARTER, B.f()).o6(QUARTER, C).slurStart().o6(SIXTEENTH, C).slurEnd().o5(QUARTER, B.f()).r(SIXTEENTH).o5(EIGHTH, A).done()
                .bar()
                    .o5(EIGHTH, G).o5(QUARTER, A).ff().r(EIGHTH).o5(EIGHTH.dot(), D).fff().o6(SIXTEENTH, D.f()).o6(SIXTEENTH, C).o5(EIGHTH.dot(), B.f()).done()
                .bar().o5(EIGHTH, A).o5(HALF, G).r(QUARTER.dot()).done()
                .build(end());

        var melody = Track.of("Melody", FRENCH_HORN, List.of(phrase1));

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

        var chords = Track.of("Strings", STRING_ENSEMBLE_1, List.of(
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
        var drums = Track.of("Drums", DRUM_KIT, List.of(new DrumPhrase(dpNodes, end())));

        return new Piece(id.title(), id.composer(),
                new KeySignature(G, Mode.MINOR), TS, new Tempo(108, QUARTER),
                List.of(melody, chords, drums));
    }
}
