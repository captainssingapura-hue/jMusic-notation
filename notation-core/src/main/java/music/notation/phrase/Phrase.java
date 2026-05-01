package music.notation.phrase;

import java.util.List;
import java.util.Map;

/**
 * Recursive structural container for bars.
 *
 * <p><b>Phase 4d transitional name.</b> This type will be renamed to
 * {@code AuthorPhrase} in the final cutover step, replacing the old sealed
 * {@code AuthorPhrase} family ({@code MelodicPhrase}, {@code DrumPhrase}, …).
 * It coexists with the old name during 4d.1–4d.2 to allow incremental
 * migration without a single big-bang change.</p>
 *
 * <p>Two cases:</p>
 * <ul>
 *   <li>{@link LeafPhrase} — wraps a list of {@link Bar}s directly.</li>
 *   <li>{@link JoinedPhrase} — composes multiple child {@code Phrase}s
 *       with a {@link ConnectingMode} controlling their stitch.</li>
 * </ul>
 *
 * <p>The canonical contract is {@link #bars()} — every Phrase
 * resolves into a flat {@link Bar} list. {@link JoinedPhrase}
 * materialises its join semantics inside that method (lazy/cached).
 * Downstream consumers (concretizer, UI walkers, codec) only ever see
 * {@code List<Bar>} and never inspect {@link ConnectingMode} —
 * elision logic lives in exactly one place.</p>
 *
 * <p>{@code name} is required-string-with-empty-default (matches the
 * project doctrine: no {@code Optional<String>}). Empty name = anonymous.</p>
 */
public sealed interface Phrase permits LeafPhrase, JoinedPhrase {

    /** Optional grouping label. Empty string when anonymous. */
    String name();

    /**
     * The flat bar list this phrase resolves to. For {@link LeafPhrase}
     * this is the stored bars; for {@link JoinedPhrase} this materialises
     * the configured {@link ConnectingMode} stitch across children.
     *
     * <p>Implementations may compute lazily but must return an immutable
     * list and produce equal results across calls.</p>
     */
    List<Bar> bars();

    /**
     * Named auxiliary voices, expanded to dense bar lists matching
     * {@link #bars()} length. Gaps in the authored sparse form are
     * filled with {@link Bar#silent(int)}. Empty map = no aux voices.
     * Default implementation returns empty.
     */
    default Map<String, List<Bar>> auxBars() {
        return Map.of();
    }

    // ── Convenience factories ────────────────────────────────────────

    /** Anonymous leaf carrying the given bars. */
    static Phrase of(Bar... bars) {
        return new LeafPhrase("", List.of(bars));
    }

    /** Anonymous leaf carrying the given bars (list form). */
    static Phrase of(List<Bar> bars) {
        return new LeafPhrase("", bars);
    }

    /** Anonymous leaf with bars and aux voices (name → barIndex → Bar, sparse). */
    static Phrase of(List<Bar> bars, Map<String, Map<Integer, Bar>> auxBarsSparse) {
        return new LeafPhrase("", bars, auxBarsSparse);
    }

    /** Named leaf carrying the given bars. */
    static Phrase named(String name, Bar... bars) {
        return new LeafPhrase(name, List.of(bars));
    }

    /** Anonymous joiner over the given children with the given mode. */
    static Phrase join(ConnectingMode mode, Phrase... children) {
        return new JoinedPhrase("", List.of(children), mode);
    }

    /** Named joiner over the given children with the given mode. */
    static Phrase joinNamed(String name, ConnectingMode mode, Phrase... children) {
        return new JoinedPhrase(name, List.of(children), mode);
    }
}
