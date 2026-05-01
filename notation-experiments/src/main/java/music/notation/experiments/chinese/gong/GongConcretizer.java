package music.notation.experiments.chinese.gong;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record GongConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<GongNote>,
                   Concretizer<TimedNote<GongNote>, PitchedNote> {

    public GongConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static GongConcretizer inC() { return new GongConcretizer(0); }
    public static GongConcretizer inD() { return new GongConcretizer(2); }

    @Override
    public int midi(GongNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<GongNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
