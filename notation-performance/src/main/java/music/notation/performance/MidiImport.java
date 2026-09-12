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
) implements MusicalImport {
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

    /**
     * Total duration of the imported piece in milliseconds, computed
     * by walking the Performance's {@link TempoTrack} through a
     * {@link TimeMapper}. The latest audible-onset's end-position
     * (rational {@link music.notation.duration.Duration}) is converted
     * to wall-clock ms at query time — nothing is stored in ms.
     */
    public long totalMs() {
        music.notation.duration.Duration maxEnd =
                music.notation.duration.Duration.zero();
        for (var t : performance.score().tracks()) {
            for (var n : t.notes()) {
                music.notation.duration.Duration end = n.endAt();
                if (end.compareDuration(maxEnd) > 0) maxEnd = end;
            }
        }
        return new TimeMapper(performance.tempo()).toMs(maxEnd);
    }

    public Optional<String> source() {
        return Optional.of(displayName);
    }
}
