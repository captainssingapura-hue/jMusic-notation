package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.structure.KeySignature;
import music.notation.structure.TimeSignature;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Dedicated builder for constructing a {@link LayeredPhrase} — a sparse overlay
 * of replacement bars on an existing base phrase.
 *
 * <p>Each {@code at()} or {@code endingAt()} call specifies a bar index and the
 * replacement content. A fresh {@link StaffPhraseBuilder} is used internally for
 * each bar, so the caller never needs to manage builder state.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * var overlay = OverlayBuilder.over(basePhrase, KEY, TS, EIGHTH)
 *     .at(1, b -> b.o4(C).o4(D).o4(E))
 *     .endingAt(3, b -> b.o4(HALF, G))
 *     .build(attacca());
 * }</pre>
 */
public final class OverlayBuilder {

    private final Phrase base;
    private final KeySignature key;
    private final TimeSignature ts;
    private final Duration defaultDur;
    private final SortedMap<Integer, Bar> overrides = new TreeMap<>();

    private OverlayBuilder(Phrase base, KeySignature key, TimeSignature ts, Duration defaultDur) {
        this.base = base;
        this.key = key;
        this.ts = ts;
        this.defaultDur = defaultDur;
    }

    /** Create an overlay builder over the given base phrase. */
    public static OverlayBuilder over(Phrase base, KeySignature key, TimeSignature ts, Duration defaultDur) {
        return new OverlayBuilder(base, key, ts, defaultDur);
    }

    /** Create an overlay builder with no key signature. */
    public static OverlayBuilder over(Phrase base, TimeSignature ts, Duration defaultDur) {
        return new OverlayBuilder(base, null, ts, defaultDur);
    }

    /**
     * Replace the bar at {@code barIndex} with content built by the given function.
     * The function receives a {@link StaffPhraseBuilder} with {@code bar()} already called —
     * just add notes and return.
     */
    public OverlayBuilder at(int barIndex, Function<StaffPhraseBuilder, StaffPhraseBuilder> barContent) {
        var P = newBuilder();
        barContent.apply(P.bar());
        overrides.put(barIndex, extractSingleBar(P));
        return this;
    }

    /**
     * Like {@link #at}, but adds an {@code ending()} after the bar content —
     * a trailing {@link PaddingNode} fills remaining beats.
     */
    public OverlayBuilder endingAt(int barIndex, Function<StaffPhraseBuilder, StaffPhraseBuilder> barContent) {
        var P = newBuilder();
        barContent.apply(P.bar());
        P.ending();
        overrides.put(barIndex, extractSingleBar(P));
        return this;
    }

    /**
     * Replace the bar at {@code barIndex} with a per-bar default duration.
     * The function receives a builder with {@code bar(barDefault)} already called.
     */
    public OverlayBuilder at(int barIndex, Duration barDefault, Function<StaffPhraseBuilder, StaffPhraseBuilder> barContent) {
        var P = newBuilder();
        barContent.apply(P.bar(barDefault));
        overrides.put(barIndex, extractSingleBar(P));
        return this;
    }

    /**
     * Like {@link #at(int, Duration, Function)}, but with trailing padding.
     */
    public OverlayBuilder endingAt(int barIndex, Duration barDefault, Function<StaffPhraseBuilder, StaffPhraseBuilder> barContent) {
        var P = newBuilder();
        barContent.apply(P.bar(barDefault));
        P.ending();
        overrides.put(barIndex, extractSingleBar(P));
        return this;
    }

    /** Build the layered phrase, inheriting the base phrase's marking. */
    public LayeredPhrase build() {
        return new LayeredPhrase(base, overrides, ts, base.marking());
    }

    /** Build the layered phrase with an explicit marking. */
    public LayeredPhrase build(PhraseMarking marking) {
        return new LayeredPhrase(base, overrides, ts, marking);
    }

    private StaffPhraseBuilder newBuilder() {
        return key != null
                ? StaffPhraseBuilder.in(key, ts, defaultDur)
                : StaffPhraseBuilder.in(ts, defaultDur);
    }

    private static Bar extractSingleBar(StaffPhraseBuilder P) {
        // Use a dummy marking — we only need the bar structure
        var phrase = P.build(new PhraseMarking(PhraseConnection.ATTACCA, true));
        if (phrase.bars().size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly 1 bar from overlay content, got " + phrase.bars().size());
        }
        return phrase.bars().getFirst();
    }
}
