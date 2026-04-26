package music.notation.experiments.hirajoshi;

import music.notation.core.model.Concretizer;
import music.notation.performance.PitchedNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

/**
 * Resolves Hirajoshi scale notes in a given key.
 *
 * <p>Implements two contracts:</p>
 * <ul>
 *   <li>{@link ScalePitchResolver} â€” {@code int midi(HirajoshiNote)}: pure
 *       pitch resolution, used by chord / melody concretizers that supply
 *       their own timing.</li>
 *   <li>{@link Concretizer}: {@code TimedNote<HirajoshiNote> â†’
 *       Note} â€” produces a single complete concrete note with
 *       timing inherited from the {@code TimedNote} wrapper.</li>
 * </ul>
 */
public record HirajoshiConcretizer(int tonicPitchClass)
        implements ScalePitchResolver<HirajoshiNote>,
                   Concretizer<TimedNote<HirajoshiNote>, PitchedNote> {

    public HirajoshiConcretizer {
        if (tonicPitchClass < 0 || tonicPitchClass > 11) {
            throw new IllegalArgumentException(
                    "tonicPitchClass must be in [0,11]: " + tonicPitchClass);
        }
    }

    public static HirajoshiConcretizer inC() { return new HirajoshiConcretizer(0); }
    public static HirajoshiConcretizer inD() { return new HirajoshiConcretizer(2); }

    @Override
    public int midi(HirajoshiNote note) {
        return 12 * (note.octave() + 1)
                + tonicPitchClass
                + note.degree().semitonesFromTonic();
    }

    @Override
    public PitchedNote concretize(TimedNote<HirajoshiNote> tn) {
        return new PitchedNote(0, tn.durationMillis(), midi(tn.note()));
    }
}
