package music.notation.performance;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Pure structural content of a note — what pitch/piece, when, how long. Notes carry
 * no track field (they are owned by the {@link Track} that contains them) and no
 * velocity (dynamics are a separate sparse side-channel, deferred).
 *
 * <h2>Sealed split</h2>
 * <ul>
 *   <li>{@link PitchedLike} — melodic content. Itself a sealed family with
 *       {@link PitchedNote} (canonical authored note) and {@link ShiftedNote}
 *       (a transposed-view wrapper preserving the original). Both expose
 *       {@link PitchedLike#midi()} for effective playback value.</li>
 *   <li>{@link DrumNote} — percussion. The {@code piece} field is a kit
 *       selector, not a pitch, and is deliberately not transposable.</li>
 * </ul>
 *
 * <p>Consumers that care only about the effective playback value
 * (the codec) read {@code midi()} polymorphically. Consumers that care
 * about the original (the UI's show-both rendering) pattern-match on
 * {@link ShiftedNote} to recover both pitches.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = PitchedNote.class, name = "PitchedNote"),
        @Type(value = ShiftedNote.class, name = "ShiftedNote"),
        @Type(value = DrumNote.class,    name = "DrumNote")
})
public sealed interface ConcreteNote
        extends music.notation.core.model.ConcreteNote
        permits PitchedLike, DrumNote {
    long tickMs();
    long durationMs();
    default long offTickMs() { return tickMs() + durationMs(); }
}
