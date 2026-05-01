package music.notation.performance;

import music.notation.structure.KeySignature;
import music.notation.structure.TimeSignature;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of reading an external MIDI file: the canonical
 * {@link Performance} plus the time-signature and key-signature meta
 * events extracted from the input (defaults applied where absent).
 *
 * <p>Imports are session-ephemeral — neither {@code Performance} nor
 * this wrapper carries any model-level structure ({@code Piece},
 * {@code Phrase}). They are consumed by playback and visualisation
 * paths directly, then dropped when the user loads something else.</p>
 */
public record MidiImport(
        String displayName,
        Performance performance,
        TimeSignature timeSig,
        KeySignature key
) {
    public MidiImport {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(performance, "performance");
        Objects.requireNonNull(timeSig, "timeSig");
        Objects.requireNonNull(key, "key");
    }

    /** First tempo in the imported tempo track, else 120 bpm. */
    public int initialBpm() {
        return performance.tempo().changes().stream()
                .findFirst()
                .map(TempoChange::bpm)
                .orElse(120);
    }

    /** Total duration of the imported piece in milliseconds. */
    public long totalMs() {
        long max = 0;
        for (var t : performance.score().tracks()) {
            for (var n : t.notes()) {
                long end = n.tickMs() + n.durationMs();
                if (end > max) max = end;
            }
        }
        return max;
    }

    public Optional<String> source() {
        return Optional.of(displayName);
    }
}
