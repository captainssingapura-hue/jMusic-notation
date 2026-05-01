package music.notation.experiments.hirajoshi.transformer;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.hirajoshi.HirajoshiNote;

/**
 * Shift every note up by {@code octaves}. Degree is untouched. Reversible;
 * inverse is {@code ShiftOctave(-octaves)}.
 */
public record ShiftOctave(int octaves) implements Transformer<HirajoshiNote, HirajoshiNote> {

    @Override
    public HirajoshiNote forward(HirajoshiNote note) {
        return new HirajoshiNote(note.degree(), note.octave() + octaves);
    }

    @Override
    public HirajoshiNote reverse(HirajoshiNote note) {
        return new HirajoshiNote(note.degree(), note.octave() - octaves);
    }
}
