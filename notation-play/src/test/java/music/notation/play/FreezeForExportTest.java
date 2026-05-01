package music.notation.play;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.phrase.Bar;
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

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.util.List;

import static music.notation.duration.BaseValue.QUARTER;
import static org.junit.jupiter.api.Assertions.*;

/** Pure tests for {@link MidiPlayer#freezeForExport(Sequence, ChannelSetup, TempoSetup, Region)}. */
class FreezeForExportTest {

    private static final KeySignature C_MAJOR = new KeySignature(NoteName.C, Mode.MAJOR);
    private static final TimeSignature TS_4_4 = new TimeSignature(4, 4);
    private static final Tempo TEMPO_120 = new Tempo(120, QUARTER);

    private static Piece simplePiece() {
        var node = PitchNode.of(StaffPitch.of(NoteName.C, 4), Duration.of(QUARTER));
        var bar = Bar.of(64, node, node, node, node);
        var melody = MelodicTrack.of("M", Instrument.ACOUSTIC_GRAND_PIANO, bar);
        return new Piece("T", "x", C_MAJOR, TS_4_4, TEMPO_120,
                List.<music.notation.structure.Track>of(melody));
    }

    @Test
    void buildNoteSequence_dropsProgramChangeAndVolumeEvents() throws Exception {
        Sequence withControls = MidiPlayer.buildSequence(simplePiece());
        Sequence noteOnly = MidiPlayer.buildNoteSequence(simplePiece());

        assertTrue(countCommand(withControls, ShortMessage.PROGRAM_CHANGE) > 0,
                "baseline buildSequence should emit PROGRAM_CHANGE");
        assertEquals(0, countCommand(noteOnly, ShortMessage.PROGRAM_CHANGE),
                "buildNoteSequence must strip PROGRAM_CHANGE");
        assertEquals(0, countCC7(noteOnly), "buildNoteSequence must strip CC #7");

        // NOTE_ON survives.
        assertTrue(countCommand(noteOnly, ShortMessage.NOTE_ON) > 0,
                "note-only should still contain NOTE_ON");
    }

    @Test
    void freezeForExport_addsBakedProgramAndVolumeAtTickZero() throws Exception {
        Sequence noteOnly = MidiPlayer.buildNoteSequence(simplePiece());
        var setup = ChannelSetup.from(simplePiece(),
                List.of(Instrument.FLUTE), List.of(80));

        Sequence frozen = MidiPlayer.freezeForExport(
                noteOnly, setup, TempoSetup.unity(), Region.full());

        // Find PC at tick 0 with program == FLUTE.program(); CC #7 == 80.
        boolean foundProgram = false;
        boolean foundVolume = false;
        for (Track t : frozen.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                MidiEvent ev = t.get(i);
                if (ev.getTick() != 0) continue;
                if (ev.getMessage() instanceof ShortMessage sm) {
                    if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE
                            && sm.getData1() == Instrument.FLUTE.program()) {
                        foundProgram = true;
                    } else if (sm.getCommand() == ShortMessage.CONTROL_CHANGE
                            && sm.getData1() == 7 && sm.getData2() == 80) {
                        foundVolume = true;
                    }
                }
            }
        }
        assertTrue(foundProgram, "PROGRAM_CHANGE for FLUTE expected at tick 0");
        assertTrue(foundVolume, "CC #7 = 80 expected at tick 0");
    }

    @Test
    void freezeForExport_unityTempoLeavesTempoEventUnchanged() throws Exception {
        Sequence noteOnly = MidiPlayer.buildNoteSequence(simplePiece());
        var setup = ChannelSetup.from(simplePiece(),
                List.of(Instrument.FLUTE), List.of(100));

        Sequence frozen = MidiPlayer.freezeForExport(
                noteOnly, setup, TempoSetup.unity(), Region.full());

        // Find tempo meta in original and in frozen — should match.
        long origMicros = firstTempoMicros(noteOnly);
        long frozenMicros = firstTempoMicros(frozen);
        assertEquals(origMicros, frozenMicros);
    }

    @Test
    void freezeForExport_factor2HalvesMicrosPerQuarter() throws Exception {
        Sequence noteOnly = MidiPlayer.buildNoteSequence(simplePiece());
        var setup = ChannelSetup.from(simplePiece(),
                List.of(Instrument.FLUTE), List.of(100));

        long origMicros = firstTempoMicros(noteOnly);
        Sequence frozen = MidiPlayer.freezeForExport(
                noteOnly, setup, TempoSetup.factor(2.0), Region.full());

        long frozenMicros = firstTempoMicros(frozen);
        // Factor 2 = playing twice as fast = half the microseconds per quarter.
        assertEquals(origMicros / 2, frozenMicros, 1, "tempo should be scaled by 1/factor");
    }

    @Test
    void freezeForExport_partialRegion_filtersAndShifts() throws Exception {
        // Build a long sequence: 4 bars × 4 quarters = 16 quarters.
        var bar1 = bar4q(NoteName.C);
        var bar2 = bar4q(NoteName.D);
        var bar3 = bar4q(NoteName.E);
        var bar4 = bar4q(NoteName.F);
        var melody = MelodicTrack.of("M", Instrument.ACOUSTIC_GRAND_PIANO,
                bar1, bar2, bar3, bar4);
        Piece piece = new Piece("T", "x", C_MAJOR, TS_4_4, TEMPO_120,
                List.<music.notation.structure.Track>of(melody));

        Sequence noteOnly = MidiPlayer.buildNoteSequence(piece);
        var setup = ChannelSetup.from(piece,
                List.of(Instrument.ACOUSTIC_GRAND_PIANO), List.of(100));

        // Bar 2 + 3 region = ticks [resolution*4, resolution*12). resolution=PPQ=480.
        long ppq = noteOnly.getResolution();
        long start = ppq * 4;
        long end = ppq * 12;
        Sequence frozen = MidiPlayer.freezeForExport(
                noteOnly, setup, TempoSetup.unity(), new Region(start, end));

        // Verify: frozen has NOTE_ONs and they're all shifted to start at 0.
        long firstNoteOn = -1;
        long lastNoteOn = -1;
        for (Track t : frozen.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                MidiEvent ev = t.get(i);
                if (ev.getMessage() instanceof ShortMessage sm
                        && sm.getCommand() == ShortMessage.NOTE_ON) {
                    if (firstNoteOn < 0) firstNoteOn = ev.getTick();
                    lastNoteOn = ev.getTick();
                }
            }
        }
        assertTrue(firstNoteOn >= 0, "frozen region should contain note-on events");
        assertEquals(0, firstNoteOn, "first note in region should be shifted to tick 0");
        assertTrue(lastNoteOn < (end - start), "last note must be within the shifted region");
    }

    private static Bar bar4q(NoteName n) {
        var node = PitchNode.of(StaffPitch.of(n, 4), Duration.of(QUARTER));
        return Bar.of(64, node, node, node, node);
    }

    @SuppressWarnings("unused")
    private static Bar barWithRest() {
        return Bar.of(64, new PaddingNode(Duration.ofSixtyFourths(64)));
    }

    private static int countCommand(Sequence seq, int command) {
        int c = 0;
        for (Track t : seq.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                if (t.get(i).getMessage() instanceof ShortMessage sm && sm.getCommand() == command) {
                    c++;
                }
            }
        }
        return c;
    }

    private static int countCC7(Sequence seq) {
        int c = 0;
        for (Track t : seq.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                if (t.get(i).getMessage() instanceof ShortMessage sm
                        && sm.getCommand() == ShortMessage.CONTROL_CHANGE
                        && sm.getData1() == 7) {
                    c++;
                }
            }
        }
        return c;
    }

    private static long firstTempoMicros(Sequence seq) {
        for (Track t : seq.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                if (t.get(i).getMessage() instanceof MetaMessage mm && mm.getType() == 0x51) {
                    byte[] data = mm.getData();
                    return ((data[0] & 0xff) << 16) | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
                }
            }
        }
        return -1;
    }
}
