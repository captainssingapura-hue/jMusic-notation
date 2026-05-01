package music.notation.experiments.yo;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record YoConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<YoNote>,
                   Concretizer<TimedNote<YoNote>, PitchedNote> {

    public YoConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static YoConcretizer inC() { return new YoConcretizer(0); }
    public static YoConcretizer inD() { return new YoConcretizer(2); }

    @Override
    public int midi(YoNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<YoNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
