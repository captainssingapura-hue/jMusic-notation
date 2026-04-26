package music.notation.performance;

/**
 * A percussion hit; {@code piece} selects a drum from the General MIDI percussion map
 * (see {@link Drums}). Drum tracks render to MIDI channel 9; the kit is selected by
 * {@link Instrumentation} on the owning track.
 */
public record DrumNote(long tickMs, long durationMs, int piece) implements ConcreteNote {
    public DrumNote {
        if (tickMs < 0) throw new IllegalArgumentException("tickMs must be >= 0: " + tickMs);
        if (durationMs <= 0) throw new IllegalArgumentException("durationMs must be > 0: " + durationMs);
        if (piece < 0 || piece > 127) throw new IllegalArgumentException("piece must be in [0,127]: " + piece);
    }
}
