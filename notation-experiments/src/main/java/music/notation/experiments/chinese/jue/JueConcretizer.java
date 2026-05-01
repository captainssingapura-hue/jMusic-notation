package music.notation.experiments.chinese.jue;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

public record JueConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<JueNote>,
                   Concretizer<TimedNote<JueNote>, PitchedNote> {

    public JueConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static JueConcretizer inC() { return new JueConcretizer(0); }
    public static JueConcretizer inD() { return new JueConcretizer(2); }

    @Override
    public int midi(JueNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<JueNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
