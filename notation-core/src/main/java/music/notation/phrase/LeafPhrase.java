package music.notation.phrase;

import java.util.List;
import java.util.Objects;

/**
 * Leaf {@link BarPhrase} — directly wraps a list of {@link Bar}s.
 *
 * @param name  optional grouping label (empty string when anonymous)
 * @param bars  the bars this phrase carries; copied defensively
 */
public record LeafPhrase(String name, List<Bar> bars) implements BarPhrase {
    public LeafPhrase {
        Objects.requireNonNull(name, "name must not be null (use \"\" for anonymous)");
        bars = List.copyOf(bars);
    }
}
