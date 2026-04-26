package music.notation.experiments.ryukyu;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record RyukyuConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<RyukyuNote>,
                   Concretizer<TimedNote<RyukyuNote>, PitchedNote> {

    public RyukyuConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static RyukyuConcretizer inC() { return new RyukyuConcretizer(0); }
    public static RyukyuConcretizer inD() { return new RyukyuConcretizer(2); }

    @Override
    public int midi(RyukyuNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<RyukyuNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
