package music.notation.play;

import music.notation.event.Instrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PatchRefTest {

    @Test
    void gmDefaultsToBank0AndInstrumentProgram() {
        var ref = PatchRef.gm(Instrument.ACOUSTIC_GRAND_PIANO);
        assertEquals(0, ref.effectiveBank());
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO.program(), ref.effectiveProgram());
        assertFalse(ref.isCustom());
        assertEquals("ACOUSTIC_GRAND_PIANO", ref.effectiveDisplayName());
    }

    @Test
    void gmDrumKitDefaultsToBank128() {
        var ref = PatchRef.gm(Instrument.DRUM_KIT);
        assertEquals(128, ref.effectiveBank());
        assertFalse(ref.isCustom());
    }

    @Test
    void customOverridesTakePriority() {
        var ref = PatchRef.custom(Instrument.ACOUSTIC_GRAND_PIANO, 1, 0, "Steinway Concert");
        assertEquals(1, ref.effectiveBank());
        assertEquals(0, ref.effectiveProgram());
        assertEquals("Steinway Concert", ref.effectiveDisplayName());
        assertTrue(ref.isCustom());
        // The GM identity is still preserved for grouping/UI purposes.
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO, ref.instrument());
    }

    @Test
    void customCanRouteThroughDrumKitFamily() {
        var ref = PatchRef.custom(Instrument.DRUM_KIT, 128, 8, "Room Kit");
        assertEquals(128, ref.effectiveBank());
        assertEquals(8, ref.effectiveProgram());
        assertEquals(Instrument.DRUM_KIT, ref.instrument());
    }

    @Test
    void instrumentMustBeNonNull() {
        assertThrows(NullPointerException.class, () -> new PatchRef(null, 0, 0, null));
    }
}
