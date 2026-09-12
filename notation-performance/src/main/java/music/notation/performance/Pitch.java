package music.notation.performance;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import music.notation.event.Step;

import java.util.Objects;

/**
 * Sum type for an authored or computed pitch carried by a
 * {@link PitchedNote}. Two shapes:
 *
 * <ul>
 *   <li>{@link Spelled} — a score-authored pitch with diatonic
 *       step + chromatic alter + octave. {@code C♯4} and {@code D♭4}
 *       sound identically on a 12-TET synth (MIDI 61) but spell
 *       differently in the score; the spelling is preserved verbatim
 *       so engravers and key-signature-aware renderers re-emit the
 *       correct glyph. Survives MusicXML round-trip.</li>
 *   <li>{@link RawMidi} — a MIDI byte with no spelling. Produced by
 *       sources that can't supply spelling: MIDI keyboard recording,
 *       audio-pitch detection, NOTE_ON bytes read off a MIDI file.
 *       A key-signature-aware speller can later lift {@code RawMidi}
 *       to {@code Spelled} when context is available.</li>
 * </ul>
 *
 * <p>Both variants resolve to {@link #midi()} in {@code [0, 127]} —
 * what the codec writes as the NOTE_ON byte, or what a sampler
 * indexes its keymap by.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(Pitch.Spelled.class),
        @JsonSubTypes.Type(Pitch.RawMidi.class)
})
public sealed interface Pitch {

    /** Resolved 12-TET MIDI byte in {@code [0, 127]}. */
    int midi();

    /**
     * Authored score spelling: diatonic step, chromatic alter, octave.
     * {@code octave} follows scientific pitch notation — {@code C4} =
     * MIDI 60. {@code alter} is in {@code [-2, +2]} covering
     * double-flat through double-sharp.
     */
    record Spelled(Step step, int alter, int octave) implements Pitch {
        public Spelled {
            Objects.requireNonNull(step, "step");
            if (alter < -2 || alter > 2) {
                throw new IllegalArgumentException(
                        "alter must be in [-2, +2]: " + alter);
            }
            int midi = (octave + 1) * 12 + step.semitoneFromC() + alter;
            if (midi < 0 || midi > 127) {
                throw new IllegalArgumentException(
                        "spelled pitch " + step + (alter == 0 ? "" : "(alter=" + alter + ")")
                        + octave + " resolves to MIDI " + midi + " (out of [0,127])");
            }
        }

        @Override public int midi() {
            return (octave + 1) * 12 + step.semitoneFromC() + alter;
        }
    }

    /** MIDI byte with no spelling — recorder/MIDI-file input. */
    record RawMidi(int midi) implements Pitch {
        public RawMidi {
            if (midi < 0 || midi > 127) {
                throw new IllegalArgumentException("midi must be in [0, 127]: " + midi);
            }
        }
    }

    // ── Convenience constructors ─────────────────────────────────────

    static Pitch of(int midi) { return new RawMidi(midi); }

    static Pitch of(Step step, int alter, int octave) {
        return new Spelled(step, alter, octave);
    }
}
