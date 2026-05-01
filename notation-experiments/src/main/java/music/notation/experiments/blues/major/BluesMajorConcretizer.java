package music.notation.experiments.blues.major;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record BluesMajorConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<BluesMajorNote>,
                   Concretizer<TimedNote<BluesMajorNote>, PitchedNote> {

    public BluesMajorConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static BluesMajorConcretizer inC() { return new BluesMajorConcretizer(0); }
    public static BluesMajorConcretizer inA() { return new BluesMajorConcretizer(9); }

    @Override
    public int midi(BluesMajorNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<BluesMajorNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
