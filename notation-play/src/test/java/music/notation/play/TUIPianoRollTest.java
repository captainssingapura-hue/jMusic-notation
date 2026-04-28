package music.notation.play;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.phrase.Bar;
import music.notation.phrase.BarPhrase;
import music.notation.phrase.ConnectingMode;
import music.notation.phrase.PaddingNode;
import music.notation.phrase.PitchNode;
import music.notation.pitch.NoteName;
import music.notation.pitch.StaffPitch;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
import music.notation.structure.Piece;
import music.notation.structure.Tempo;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless validation that {@link TUIPianoRoll} reads the same value
 * object as {@link PieceConcretizer} — both walk
 * {@link music.notation.structure.Track#bars()} and must agree on
 * note positions.
 */
class TUIPianoRollTest {

    private static final KeySignature C_MAJOR = new KeySignature(NoteName.C, Mode.MAJOR);
    private static final TimeSignature TS_4_4 = new TimeSignature(4, 4);
    private static final Tempo TEMPO_120 = new Tempo(120, QUARTER);

    private static StaffPitch p(NoteName n, int o) { return StaffPitch.of(n, o); }

    /** Single quarter at C4 — one onset at sf=0, sustains to sf=15. */
    @Test
    void singleNote_emitsOneHitAtZero() {
        Bar bar = Bar.of(64,
                PitchNode.of(p(NoteName.C, 4), Duration.of(QUARTER)),
                new PaddingNode(Duration.ofSixtyFourths(48)));
        var track = MelodicTrack.of("M", Instrument.ACOUSTIC_GRAND_PIANO, bar);
        var piece = new Piece("T", "x", C_MAJOR, TS_4_4, TEMPO_120, List.<music.notation.structure.Track>of(track));

        var roll = TUIPianoRoll.render(piece);

        assertEquals(1, roll.hits().size());
        var h = roll.hits().get(0);
        assertEquals("M", h.trackName());
        assertEquals(0, h.sf());
        assertEquals(16, h.durSf());
        assertEquals(60, h.midi());  // C4 = MIDI 60
    }

    /** ELIDED join — pickup audible at end of merged bar. */
    @Test
    void elidedJoin_pickupAtEndOfMergedBar() {
        // bar 1: 48sf audible C4 + 16sf trailing pad.
        Bar lastBar = Bar.of(64,
                PitchNode.of(p(NoteName.C, 4), Duration.ofSixtyFourths(48)),
                new PaddingNode(Duration.ofSixtyFourths(16)));
        // pickup bar: 48sf leading pad + 16sf audible D4.
        Bar pickupBar = Bar.of(64,
                new PaddingNode(Duration.ofSixtyFourths(48)),
                PitchNode.of(p(NoteName.D, 4), Duration.ofSixtyFourths(16)));
        // bar 3: 64sf audible E4.
        Bar nextBar = Bar.of(64, PitchNode.of(p(NoteName.E, 4), Duration.ofSixtyFourths(64)));

        var phrase = BarPhrase.join(ConnectingMode.ELIDED,
                BarPhrase.of(lastBar),
                BarPhrase.of(pickupBar, nextBar));

        var track = new MelodicTrack("M", Instrument.ACOUSTIC_GRAND_PIANO, phrase, List.of());
        var piece = new Piece("T", "x", C_MAJOR, TS_4_4, TEMPO_120,
                List.<music.notation.structure.Track>of(track));

        var roll = TUIPianoRoll.render(piece);
        var hits = roll.hits();

        // Expect 3 hits: C4 at 0 (48sf), D4 at 48 (pickup absorbed at end of merged bar, 16sf), E4 at 64 (next bar, 64sf).
        assertEquals(3, hits.size(), "C4 + D4-pickup + E4");
        assertEquals(60, hits.get(0).midi());
        assertEquals(0, hits.get(0).sf());
        assertEquals(62, hits.get(1).midi());
        assertEquals(48, hits.get(1).sf(), "pickup audible at end of merged bar");
        assertEquals(16, hits.get(1).durSf());
        assertEquals(64, hits.get(2).midi());
        assertEquals(64, hits.get(2).sf(), "next bar starts immediately after merged bar");
    }

    /** UI parity: TUI hits and concretizer hits must match in tick-converted positions. */
    @Test
    void tuiHitsMatchConcretizerNotes_forSimplePiece() {
        Bar bar1 = Bar.of(64,
                PitchNode.of(p(NoteName.C, 4), Duration.of(QUARTER)),
                PitchNode.of(p(NoteName.D, 4), Duration.of(QUARTER)),
                PitchNode.of(p(NoteName.E, 4), Duration.of(QUARTER)),
                PitchNode.of(p(NoteName.F, 4), Duration.of(QUARTER)));
        var track = MelodicTrack.of("M", Instrument.ACOUSTIC_GRAND_PIANO, bar1);
        var piece = new Piece("T", "x", C_MAJOR, TS_4_4, TEMPO_120,
                List.<music.notation.structure.Track>of(track));

        var roll = TUIPianoRoll.render(piece);
        var perf = PieceConcretizer.concretize(piece);
        var perfNotes = perf.score().tracks().get(0).notes();

        assertEquals(roll.hits().size(), perfNotes.size(),
                "TUI and concretizer must emit the same number of notes");
        for (int i = 0; i < roll.hits().size(); i++) {
            int tuiMidi = roll.hits().get(i).midi();
            int perfMidi = ((music.notation.performance.PitchedNote) perfNotes.get(i)).midi();
            assertEquals(perfMidi, tuiMidi, "Note " + i + " midi mismatch");
        }
    }

    @Test
    void formatTrack_rendersGridWithBarBoundaries() {
        Bar bar = Bar.of(64,
                PitchNode.of(p(NoteName.C, 4), Duration.of(QUARTER)),
                new PaddingNode(Duration.ofSixtyFourths(48)));
        var track = MelodicTrack.of("M", Instrument.ACOUSTIC_GRAND_PIANO, bar);
        var piece = new Piece("T", "x", C_MAJOR, TS_4_4, TEMPO_120,
                List.<music.notation.structure.Track>of(track));

        String out = TUIPianoRoll.render(piece).formatTrack("M");
        // Row for MIDI 60: 'o' at sf=0, '#'×15 sustain, '.'×48 silence.
        // Bar boundary should NOT appear in this single 64-sf piece (no inner boundary).
        String c4Row = out.lines().filter(l -> l.startsWith(" 60 ")).findFirst().orElseThrow();
        assertTrue(c4Row.contains("o###############"), "onset + 15 sustain at start");
        assertTrue(c4Row.endsWith("................"), "trailing silence");
    }
}
