package music.notation.autodrum;

import music.notation.duration.BarDuration;

/**
 * Feature-aware pattern resolver — looks up the {@link PatternSpec} a
 * strategy wants for a particular bar, given the bar's duration, the
 * staged {@link Energy}, the source's {@link BarFeatures}, and the
 * 0-based bar index in the piece.
 *
 * <p>Returning {@code null} signals "no specific pattern for this bar"
 * and triggers {@link Patterns#fallbackBar(BarDuration)} downstream
 * (graceful kick on beat 1).</p>
 */
@FunctionalInterface
public interface PatternResolver {
    PatternSpec resolve(BarDuration bd, Energy energy,
                        BarFeatures features, int barIndex);
}
