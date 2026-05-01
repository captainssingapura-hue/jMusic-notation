package music.notation.phrase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Leaf {@link Phrase} — directly wraps a list of {@link Bar}s plus
 * optional named aux voices authored sparsely.
 *
 * <p>{@code auxBarsSparse} is keyed by voice name then by primary
 * bar index. Names are normalised (null/blank → {@code "default"};
 * duplicates after normalisation are rejected). Each entry's bar must
 * match its primary's {@code expectedSixtyFourths}. Gaps (indices
 * absent in the inner map) expand to {@link Bar#silent(int)} when
 * {@link #auxBars()} is called.</p>
 */
public record LeafPhrase(
        String name,
        List<Bar> bars,
        Map<String, Map<Integer, Bar>> auxBarsSparse
) implements Phrase {

    public LeafPhrase {
        Objects.requireNonNull(name, "name must not be null (use \"\" for anonymous)");
        bars = List.copyOf(bars);
        auxBarsSparse = normaliseAux(auxBarsSparse, bars);
    }

    /** Backwards-compat ctor: no aux voices. */
    public LeafPhrase(String name, List<Bar> bars) {
        this(name, bars, Map.of());
    }

    @Override
    public Map<String, List<Bar>> auxBars() {
        if (auxBarsSparse.isEmpty()) return Map.of();
        var out = new LinkedHashMap<String, List<Bar>>(auxBarsSparse.size());
        for (var e : auxBarsSparse.entrySet()) {
            var dense = new ArrayList<Bar>(bars.size());
            for (int i = 0; i < bars.size(); i++) {
                Bar b = e.getValue().get(i);
                dense.add(b != null ? b : Bar.silent(bars.get(i).expectedSixtyFourths()));
            }
            out.put(e.getKey(), List.copyOf(dense));
        }
        return Map.copyOf(out);
    }

    private static Map<String, Map<Integer, Bar>> normaliseAux(
            Map<String, Map<Integer, Bar>> in, List<Bar> primary) {
        if (in == null || in.isEmpty()) return Map.of();
        var out = new LinkedHashMap<String, Map<Integer, Bar>>(in.size());
        for (var e : in.entrySet()) {
            String raw = e.getKey();
            String n = (raw == null || raw.isBlank()) ? "default" : raw;
            if (out.containsKey(n)) {
                throw new IllegalArgumentException(
                        "LeafPhrase: duplicate aux voice name '" + n + "'");
            }
            Map<Integer, Bar> entries = e.getValue();
            if (entries == null || entries.isEmpty()) {
                out.put(n, Map.of());
                continue;
            }
            var copy = new LinkedHashMap<Integer, Bar>(entries.size());
            for (var ie : entries.entrySet()) {
                int idx = ie.getKey();
                if (idx < 0 || idx >= primary.size()) {
                    throw new IllegalArgumentException(
                            "Aux voice '" + n + "': bar index " + idx
                                    + " out of range [0, " + primary.size() + ")");
                }
                Bar bar = ie.getValue();
                int expected = primary.get(idx).expectedSixtyFourths();
                if (bar.expectedSixtyFourths() != expected) {
                    throw new IllegalArgumentException(
                            "Aux voice '" + n + "', bar " + idx + ": expected "
                                    + expected + "/64 to match primary but got "
                                    + bar.expectedSixtyFourths() + "/64");
                }
                copy.put(idx, bar);
            }
            out.put(n, Map.copyOf(copy));
        }
        return Map.copyOf(out);
    }
}
