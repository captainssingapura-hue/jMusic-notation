package music.notation.experiments.scale;

/**
 * Reconstructs a {@link ScaleNote} of some concrete subtype from an
 * {@code (degreeIndex, octave)} pair. Usually supplied as a method
 * reference, e.g. {@code HirajoshiNote::ofIndex}.
 *
 * <p>This is the companion to {@code ScaleNote#degreeIndex()} /
 * {@code ScaleNote#octave()}: together they describe the scale-note
 * identity, which is exactly what cross-scale transforms move around.</p>
 */
@FunctionalInterface
public interface ScaleFactory<N extends ScaleNote> {
    N create(int degreeIndex, int octave);
}
