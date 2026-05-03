package music.notation.play;

import music.notation.event.Instrument;

import java.util.Objects;

/**
 * Per-channel patch selection. Carries a canonical {@link Instrument}
 * (the GM intent — "this should be a piano") plus an optional
 * {@code (bank, program)} override that takes priority at apply time
 * (the more-specific patch loaded from a SoundFont, e.g.
 * "use Steinway Concert at bank 1, program 0 instead of GM piano").
 *
 * <p>The {@link Instrument} field is always present so any UI surface
 * can group / classify patches by GM family even when the actual
 * sounding bytes come from a soundbank. Override fields are
 * {@code null} for plain GM selections.</p>
 */
public record PatchRef(
        Instrument instrument,
        Integer bankOverride,
        Integer programOverride,
        String displayNameOverride
) {
    public PatchRef {
        Objects.requireNonNull(instrument, "instrument");
    }

    /** Plain GM selection — no soundbank override. */
    public static PatchRef gm(Instrument instrument) {
        return new PatchRef(instrument, null, null, null);
    }

    /**
     * Soundbank-specific selection. {@code instrument} is the GM family
     * the patch is classified under (used for UI grouping); the actual
     * sound comes from the {@code (bank, program)} pair.
     */
    public static PatchRef custom(Instrument instrument, int bank, int program, String displayName) {
        return new PatchRef(instrument, bank, program, displayName);
    }

    /** Wrap a {@link SoundBankInstrument} as a per-track patch override. */
    public static PatchRef soundbank(SoundBankInstrument sbi) {
        if (sbi == null) return null;
        return new PatchRef(sbi.family(), sbi.bank(), sbi.program(), sbi.displayName());
    }

    /** Effective bank: override if present, else 128 for drum kit, else 0. */
    public int effectiveBank() {
        if (bankOverride != null) return bankOverride;
        return instrument == Instrument.DRUM_KIT ? 128 : 0;
    }

    /** Effective program: override if present, else the GM program. */
    public int effectiveProgram() {
        return programOverride != null ? programOverride : instrument.program();
    }

    /** Display name for UI labels — override if set, else the GM enum's pretty name. */
    public String effectiveDisplayName() {
        if (displayNameOverride != null && !displayNameOverride.isBlank()) return displayNameOverride;
        return instrument.name();
    }

    /** Whether this selection diverges from the plain GM mapping. */
    public boolean isCustom() {
        return bankOverride != null || programOverride != null;
    }
}
