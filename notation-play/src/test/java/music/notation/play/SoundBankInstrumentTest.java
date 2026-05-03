package music.notation.play;

import music.notation.event.Instrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoundBankInstrumentTest {

    @Test
    void classifyToGm_bank0Program0_isAcousticGrandPiano() {
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO,
                SoundBankInstrument.classifyToGm(0, 0));
    }

    @Test
    void classifyToGm_bank128_isDrumKit() {
        assertEquals(Instrument.DRUM_KIT, SoundBankInstrument.classifyToGm(128, 0));
        assertEquals(Instrument.DRUM_KIT, SoundBankInstrument.classifyToGm(128, 16));
    }

    @Test
    void classifyToGm_program73_isFlute() {
        // Flute = GM program 73.
        assertEquals(Instrument.FLUTE, SoundBankInstrument.classifyToGm(0, 73));
    }

    @Test
    void bankSelectFor_lowBank_putsAllInMsb() {
        var sbi = new SoundBankInstrument(Instrument.ACOUSTIC_GRAND_PIANO, 5, 0, "Foo", "test.sf2");
        assertEquals(5, sbi.bankMsb());
        assertEquals(0, sbi.bankLsb());
    }

    @Test
    void bankSelectFor_highBank_splits14Bit() {
        // bank 200 = 0xC8 → MSB=1, LSB=72 (0x48)
        var sbi = new SoundBankInstrument(Instrument.ACOUSTIC_GRAND_PIANO, 200, 0, "Foo", "test.sf2");
        assertEquals(1, sbi.bankMsb());
        assertEquals(72, sbi.bankLsb());
    }

    @Test
    void isDrumKit_byBank() {
        var drumSbi = new SoundBankInstrument(Instrument.DRUM_KIT, 128, 0, "Standard Kit", "test.sf2");
        assertTrue(drumSbi.isDrumKit());
        var pianoSbi = new SoundBankInstrument(Instrument.ACOUSTIC_GRAND_PIANO, 0, 0, "Piano", "test.sf2");
        assertFalse(pianoSbi.isDrumKit());
    }

    @Test
    void displayNameDefaultsToFamilyName() {
        var sbi = new SoundBankInstrument(Instrument.FLUTE, 0, 73, "", "test.sf2");
        assertEquals("FLUTE", sbi.displayName());
    }

    @Test
    void classifyToGm_withName_catchesDrumKitsStoredOutsideBank128() {
        // Some SF2s store drum kits at bank 0 with weird program numbers; the
        // name heuristic catches them.
        assertEquals(Instrument.DRUM_KIT,
                SoundBankInstrument.classifyToGm(0, 7, "Don's Std Kit"));
        assertEquals(Instrument.DRUM_KIT,
                SoundBankInstrument.classifyToGm(0, 16, "Power Kit"));
        assertEquals(Instrument.DRUM_KIT,
                SoundBankInstrument.classifyToGm(0, 0, "drum kit"));
    }

    @Test
    void classifyToGm_withName_doesNotMisclassifyMelodicGmNames() {
        // GM #118 = Synth Drum (melodic Percussive). Must not be flagged drum kit.
        assertEquals(Instrument.SYNTH_DRUM,
                SoundBankInstrument.classifyToGm(0, 118, "Synth Drum"));
        // GM #17 = Percussive Organ. Must not match the drum heuristic.
        assertEquals(Instrument.PERCUSSIVE_ORGAN,
                SoundBankInstrument.classifyToGm(0, 17, "Percussive Organ"));
    }

    @Test
    void classifyToGm_unmappedProgram_doesNotFallBackToPiano_now() {
        // Pre-fix: any program not in the partial enum dumped to ACOUSTIC_GRAND_PIANO.
        // Post-fix: the enum covers all 128 programs.
        assertEquals(Instrument.TUBULAR_BELLS,
                SoundBankInstrument.classifyToGm(0, 14, "Tubular Bells"));
        assertEquals(Instrument.WHISTLE,
                SoundBankInstrument.classifyToGm(0, 78, "Whistle"));
    }

    @Test
    void registryEmpty_hasNoVariants() {
        var reg = SoundBankRegistry.empty();
        assertTrue(reg.isEmpty());
        assertTrue(reg.variantsFor(Instrument.ACOUSTIC_GRAND_PIANO).isEmpty());
        assertTrue(reg.familiesWithVariants().isEmpty());
    }
}
