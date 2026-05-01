package music.notation.experiments.chinese.shang;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record ShangConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<ShangNote>,
                   Concretizer<TimedNote<ShangNote>, PitchedNote> {

    public ShangConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static ShangConcretizer inC() { return new ShangConcretizer(0); }
    public static ShangConcretizer inD() { return new ShangConcretizer(2); }

    @Override
    public int midi(ShangNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<ShangNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
