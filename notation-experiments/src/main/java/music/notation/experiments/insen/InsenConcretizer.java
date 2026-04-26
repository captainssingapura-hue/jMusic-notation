package music.notation.experiments.insen;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record InsenConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<InsenNote>,
                   Concretizer<TimedNote<InsenNote>, PitchedNote> {

    public InsenConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static InsenConcretizer inC() { return new InsenConcretizer(0); }
    public static InsenConcretizer inD() { return new InsenConcretizer(2); }

    @Override
    public int midi(InsenNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<InsenNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
