package music.notation.experiments.chinese.zhi;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record ZhiConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<ZhiNote>,
                   Concretizer<TimedNote<ZhiNote>, PitchedNote> {

    public ZhiConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static ZhiConcretizer inC() { return new ZhiConcretizer(0); }
    public static ZhiConcretizer inD() { return new ZhiConcretizer(2); }

    @Override
    public int midi(ZhiNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<ZhiNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
