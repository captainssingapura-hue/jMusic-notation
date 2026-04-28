package music.notation.phrase;

import music.notation.structure.TimeSignature;

import java.util.*;

/**
 * An immutable phrase overlay: a sparse map of bar replacements layered on a base phrase.
 * Resolution is deferred to interpretation time via {@link #resolve()}.
 *
 * <p>Layering composes recursively — the base can itself be a {@code LayeredPhrase}
 * or a {@link ShiftedPhrase}. Resolution walks the chain bottom-up.</p>
 */
public record LayeredPhrase(
        AuthorPhrase base,
        SortedMap<Integer, Bar> overrides,
        TimeSignature timeSignature,
        PhraseMarking marking
) implements AuthorPhrase {

    public LayeredPhrase {
        Objects.requireNonNull(base, "base must not be null");
        Objects.requireNonNull(timeSignature, "timeSignature must not be null");
        Objects.requireNonNull(marking, "marking must not be null");
        for (int idx : overrides.keySet()) {
            if (idx < 0) {
                throw new IllegalArgumentException("Override index must be non-negative: " + idx);
            }
        }
        overrides = Collections.unmodifiableSortedMap(new TreeMap<>(overrides));
    }

    /**
     * Resolve the layering into a concrete {@link MelodicPhrase}.
     * Extracts bars from the base (recursively for nested layers),
     * applies overrides, then builds via {@link MelodicPhrase#fromBars}
     * which handles slur resolution and validation.
     */
    public MelodicPhrase resolve() {
        List<Bar> baseBars = new ArrayList<>(extractBars(base));
        for (var entry : overrides.entrySet()) {
            int idx = entry.getKey();
            if (idx >= baseBars.size()) {
                throw new IndexOutOfBoundsException(
                        "Override index " + idx + " out of range for base with " + baseBars.size() + " bars");
            }
            baseBars.set(idx, entry.getValue());
        }
        return MelodicPhrase.fromBars(timeSignature, marking, baseBars.toArray(Bar[]::new));
    }

    /**
     * Extract the bar list from a phrase, recursively resolving layers.
     */
    static List<Bar> extractBars(AuthorPhrase phrase) {
        return switch (phrase) {
            case MelodicPhrase mp -> {
                if (mp.bars().isEmpty()) {
                    throw new IllegalStateException("Base phrase has no bar structure");
                }
                yield mp.bars();
            }
            case LayeredPhrase lp -> lp.resolve().bars();
            default -> throw new IllegalStateException(
                    "Cannot extract bars from " + phrase.getClass().getSimpleName());
        };
    }
}
