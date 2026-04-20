package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.structure.TimeSignature;

import java.util.Map;

/**
 * Immutable configuration shared down the typed-builder chain:
 * {@link StaffPhraseBuilderTyped} → {@link BarBuilderTyped} → {@link AuxBarBuilderTyped}.
 *
 * <p>Carries the time signature, the builder-level default duration, and the
 * accidentals map derived from the key signature. Nothing in here mutates;
 * the instance is safe to pass by reference and reuse across bars and voices.</p>
 */
record BuilderContext(
        TimeSignature ts,
        Duration defaultDur,
        Map<NoteName, Accidental> keyAccidentals
) {
    BuilderContext {
        keyAccidentals = Map.copyOf(keyAccidentals);
    }
}
