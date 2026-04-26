package music.notation.performance;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Pure structural content of a note — what pitch/piece, when, how long. Notes carry
 * no track field (they are owned by the {@link Track} that contains them) and no
 * velocity (dynamics are a separate sparse side-channel, deferred). Sealed split:
 * {@link PitchedNote} for melodic content, {@link DrumNote} for percussion.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = PitchedNote.class, name = "PitchedNote"),
        @Type(value = DrumNote.class,    name = "DrumNote")
})
public sealed interface ConcreteNote
        extends music.notation.core.model.ConcreteNote
        permits PitchedNote, DrumNote {
    long tickMs();
    long durationMs();
    default long offTickMs() { return tickMs() + durationMs(); }
}
