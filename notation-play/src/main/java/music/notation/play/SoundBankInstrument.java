package music.notation.play;

import music.notation.event.Instrument;

import java.util.Objects;

/**
 * A specific patch from a loaded SoundFont, layered on top of the
 * canonical GM {@link Instrument} family. Each {@code Instrument} can
 * have many {@code SoundBankInstrument}s associated with it (variations
 * across one or more loaded soundbank files); the GM identity stays as
 * the spine for arrangement / role logic, while the SBI carries the
 * specific bank/program needed to render the chosen sound.
 *
 * <p>Selection in the UI follows the 1-to-many shape: pick a GM
 * {@link Instrument} first; the loaded soundbank registry then surfaces
 * variants for that family.</p>
 */
public record SoundBankInstrument(
        Instrument family,
        int bank,
        int program,
        String displayName,
        String soundbankName
) {
    public SoundBankInstrument {
        Objects.requireNonNull(family, "family");
        if (displayName == null || displayName.isBlank()) displayName = family.name();
        if (soundbankName == null) soundbankName = "";
    }

    /** Bank-MSB to send via CC #0 when binding a channel to this patch. */
    public int bankMsb() {
        return bank > 127 ? (bank >> 7) & 0x7f : bank & 0x7f;
    }

    /** Bank-LSB to send via CC #32 when binding a channel to this patch. */
    public int bankLsb() {
        return bank > 127 ? bank & 0x7f : 0;
    }

    /** True iff this patch is a drum kit (SF2 bank 128 or higher). */
    public boolean isDrumKit() {
        return bank >= 128 || family == Instrument.DRUM_KIT;
    }

    /**
     * Default 1-to-many classifier: SF2 bank/program → GM
     * {@link Instrument}. Bank ≥ 128 → drum kit; otherwise the GM
     * program slot. Replaceable via
     * {@link SoundBankRegistry.Classifier} when richer rules are
     * needed.
     */
    public static Instrument classifyToGm(int bank, int program) {
        return classifyToGm(bank, program, "");
    }

    /**
     * Classification with name hint — falls back to a name heuristic
     * for drum kits that aren't stored at SF2 bank 128 (some SF2 files
     * put drum kits at bank 0 with arbitrary program numbers, which
     * would otherwise misclassify into a melodic family).
     */
    public static Instrument classifyToGm(int bank, int program, String name) {
        if (bank >= 128) return Instrument.DRUM_KIT;
        if (looksLikeDrumKit(name)) return Instrument.DRUM_KIT;
        int p = program & 0x7f;
        for (var v : Instrument.values()) {
            if (v != Instrument.DRUM_KIT && v.program() == p) return v;
        }
        return Instrument.ACOUSTIC_GRAND_PIANO;
    }

    /** Heuristic name matcher for drum-kit patches encoded outside bank 128. */
    private static boolean looksLikeDrumKit(String name) {
        if (name == null) return false;
        String n = name.toLowerCase().trim();
        // Match common drum-kit naming patterns; explicitly avoid melodic
        // GM names that share substrings ("Synth Drum", "Percussive Organ").
        return n.endsWith(" kit")
                || n.equals("kit")
                || n.contains("drum kit")
                || n.contains("drumkit")
                || n.contains("percussion kit");
    }
}
