package music.notation.performance;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import music.notation.duration.Duration;

/**
 * Pure structural content of a note — what pitch / piece, when it
 * starts in the track's musical timeline, and how long it lasts.
 * Notes carry no track field (they are owned by the {@link Track}
 * that contains them) and no velocity (dynamics live in a separate
 * sparse side-channel).
 *
 * <h2>Time model — musical positions only</h2>
 *
 * <p>The position {@link #at()} and length {@link #duration()} are
 * both {@link Duration} values — exact rational fractions of a whole
 * note. <b>Wall-clock milliseconds are not stored anywhere in the
 * data model.</b> The codec multiplies by PPQ and walks the
 * {@link TempoTrack} at emit time to produce ticks / ms; consumers
 * that want wall-clock for a UI playhead derive it the same way.
 * This makes the model tempo-independent: changing tempo doesn't
 * rewrite a single note.</p>
 *
 * <h2>Sealed split</h2>
 * <ul>
 *   <li>{@link PitchedLike} — melodic content. Itself a sealed family
 *       with {@link PitchedNote} (canonical authored note) and
 *       {@link ShiftedNote} (transposed-view wrapper preserving the
 *       original). Both expose {@link PitchedLike#midi()}.</li>
 *   <li>{@link DrumNote} — percussion. The {@code piece} field is a
 *       kit selector, not a pitch, and is deliberately not
 *       transposable.</li>
 * </ul>
 *
 * <p>Consumers that care about the effective playback value (the codec)
 * read {@code midi()} polymorphically. Consumers that care about the
 * original (the UI's show-both rendering) pattern-match on
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

    /** Musical onset position from the start of the owning {@link Track}. */
    Duration at();

    /** Musical length of this note. */
    Duration duration();

    /** Musical end position (start + length). */
    default Duration endAt() {
        return at().plus(duration());
    }
}
