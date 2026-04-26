package music.notation.experiments.iwato;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record IwatoConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<IwatoNote>,
                   Concretizer<TimedNote<IwatoNote>, PitchedNote> {

    public IwatoConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static IwatoConcretizer inC() { return new IwatoConcretizer(0); }
    public static IwatoConcretizer inD() { return new IwatoConcretizer(2); }

    @Override
    public int midi(IwatoNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<IwatoNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
