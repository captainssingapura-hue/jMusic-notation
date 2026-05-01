package music.notation.experiments.chinese.yu;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record YuConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<YuNote>,
                   Concretizer<TimedNote<YuNote>, PitchedNote> {

    public YuConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static YuConcretizer inC() { return new YuConcretizer(0); }
    public static YuConcretizer inD() { return new YuConcretizer(2); }

    @Override
    public int midi(YuNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<YuNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
