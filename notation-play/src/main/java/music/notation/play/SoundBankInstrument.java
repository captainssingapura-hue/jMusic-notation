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

    /** True iff this patch is any drum kit (SF2 bank 128 or any of the GM2 kit variants). */
    public boolean isDrumKit() {
        return bank >= 128 || family.isDrumKit();
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
        int p = program & 0x7f;
        // Drum-bank or name-tagged-as-drum patches resolve to the most-specific
        // GM2 drum kit whose program matches; fall back to Standard Kit if the
        // program number doesn't line up with a known variant.
        if (bank >= 128 || looksLikeDrumKit(name)) {
            for (var v : Instrument.values()) {
                if (v.isDrumKit() && v.program() == p) return v;
            }
            return Instrument.DRUM_KIT;
        }
        // Melodic lookup — skip every drum-kit value because their program
        // numbers (8, 16, 24, …) overlap with melodic GM programs.
        for (var v : Instrument.values()) {
            if (!v.isDrumKit() && v.program() == p) return v;
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
