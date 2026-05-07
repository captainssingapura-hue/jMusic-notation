package music.notation.performance;

import music.notation.event.Instrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentMapTest {

    @Test
    void program0_isAcousticGrandPiano() {
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO,
                InstrumentMap.forProgram(0).orElseThrow());
    }

    @Test
    void everyNonDrumInstrument_roundTripsThroughItsProgram() {
        for (Instrument inst : Instrument.values()) {
            if (inst == Instrument.DRUM_KIT) continue;
            var found = InstrumentMap.forProgram(inst.program());
            assertTrue(found.isPresent(), "no mapping for " + inst);
            // First-wins: don't assert exact identity (multiple enums may
            // share a program); just assert *some* GM enum is returned.
        }
    }

    @Test
    void drumKit_isExcludedFromTheMap() {
        // Program 0 maps to ACOUSTIC_GRAND_PIANO, never DRUM_KIT.
        assertNotEquals(Instrument.DRUM_KIT, InstrumentMap.forProgram(0).orElseThrow());
    }

    @Test
    void unmappedProgram_returnsEmpty() {
        // 200 is outside 0-127. 999 likewise.
        assertTrue(InstrumentMap.forProgram(200).isEmpty());
        assertTrue(InstrumentMap.forProgram(999).isEmpty());
    }

    @Test
    void forProgramOrDefault_fallsBackToPiano() {
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO,
                InstrumentMap.forProgramOrDefault(999));
    }

    @Test
    void contains_matchesPresence() {
        assertTrue(InstrumentMap.contains(0));
        assertFalse(InstrumentMap.contains(999));
    }
}
