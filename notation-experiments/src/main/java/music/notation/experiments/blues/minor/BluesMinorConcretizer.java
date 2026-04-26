package music.notation.experiments.blues.minor;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record BluesMinorConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<BluesMinorNote>,
                   Concretizer<TimedNote<BluesMinorNote>, PitchedNote> {

    public BluesMinorConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static BluesMinorConcretizer inC() { return new BluesMinorConcretizer(0); }
    public static BluesMinorConcretizer inA() { return new BluesMinorConcretizer(9); }

    @Override
    public int midi(BluesMinorNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<BluesMinorNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
