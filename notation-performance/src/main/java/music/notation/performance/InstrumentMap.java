package music.notation.performance;

import music.notation.event.Instrument;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reverse lookup from a MIDI program-change number (0–127) to the
 * corresponding GM Level 1 {@link Instrument} enum value.
 *
 * <p>{@link Instrument#DRUM_KIT} is intentionally <em>excluded</em> from
 * this table: it shares program 0 with {@link Instrument#ACOUSTIC_GRAND_PIANO}
 * in the GM spec, and is selected by MIDI <em>channel</em> (9) rather
 * than by program. Use {@code channel == 9} as the drum signal
 * upstream; only call this map for non-drum channels.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class InstrumentMap {

    private InstrumentMap() {}

    private static final Map<Integer, Instrument> BY_PROGRAM;
    static {
        var m = new HashMap<Integer, Instrument>();
        for (Instrument inst : Instrument.values()) {
            if (inst == Instrument.DRUM_KIT) continue;
            // First-wins: if two enum constants ever shared a program
            // number, the earlier-declared one stays canonical.
            m.putIfAbsent(inst.program(), inst);
        }
        BY_PROGRAM = Map.copyOf(m);
    }

    /**
     * Resolve a GM program number to its {@link Instrument}, if known.
     *
     * @param program MIDI program 0–127.
     * @return the matching enum, or empty if {@code program} is out of
     *         range or has no GM coverage in this build.
     */
    public static Optional<Instrument> forProgram(int program) {
        return Optional.ofNullable(BY_PROGRAM.get(program));
    }

    /**
     * Resolve a GM program number, falling back to {@link
     * Instrument#ACOUSTIC_GRAND_PIANO} when the lookup misses.
     *
     * <p>Convenient for callers (MIDI extractors, importers) that need
     * a non-null result and are happy to default to "piano" for any
     * unmapped program.</p>
     */
    public static Instrument forProgramOrDefault(int program) {
        return BY_PROGRAM.getOrDefault(program, Instrument.ACOUSTIC_GRAND_PIANO);
    }

    /** Whether {@code program} maps to a known {@link Instrument}. */
    public static boolean contains(int program) {
        return BY_PROGRAM.containsKey(program);
    }
}
