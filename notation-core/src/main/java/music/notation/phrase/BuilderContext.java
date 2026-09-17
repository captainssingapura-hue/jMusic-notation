package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.structure.TimeSignature;

import java.util.Map;

/**
 * Immutable settings shared by a {@link StaffPhraseBuilderTyped} and the
 * bar builders it opens. {@code octaveShift} is added to every note's
 * written octave at resolve time — the way to author one part and play
 * it in another register (a male voice doubling a female line an octave
 * down) without duplicating the notation.
 */
record BuilderContext(
        TimeSignature ts,
        Duration defaultDur,
        Map<NoteName, Accidental> keyAccidentals,
        int octaveShift
) {
    BuilderContext {
        keyAccidentals = Map.copyOf(keyAccidentals);
    }

    BuilderContext(TimeSignature ts, Duration defaultDur, Map<NoteName, Accidental> keyAccidentals) {
        this(ts, defaultDur, keyAccidentals, 0);
    }

    BuilderContext withOctaveShift(int shift) {
        return new BuilderContext(ts, defaultDur, keyAccidentals, shift);
    }
}
