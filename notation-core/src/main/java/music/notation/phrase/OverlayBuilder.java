package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.structure.KeySignature;
import music.notation.structure.TimeSignature;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Dedicated builder for constructing a {@link LayeredPhrase} — a sparse overlay
 * of replacement bars on an existing base phrase.
 *
 * <p>Each {@code at()} call specifies a bar index and the replacement content.
 * A fresh {@link StaffPhraseBuilderTyped} is used internally per bar; the
 * consumer never needs to manage builder state or call {@code .done()} —
 * the overlay builder finalizes the bar for you.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * var overlay = OverlayBuilder.over(basePhrase, KEY, TS, EIGHTH)
 *     .at(1, b -> b.o4(C).o4(D).o4(E).o4(F))
 *     .at(3, b -> b.o4(HALF, G).pad(HALF))   // explicit trailing pad
 *     .build(attacca());
 * }</pre>
 *
 * <p>If a bar needs trailing padding (previously the {@code endingAt(...)}
 * helper), declare it explicitly with {@link BarBuilderTyped#pad(Duration)}
 * — the bar's sum must match the time signature exactly or construction
 * throws.</p>
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
     * Replace the bar at {@code barIndex} with content written by the given consumer.
     * The consumer receives a {@link BarBuilderTyped} already opened — add notes,
     * rests, dynamics, padding, etc. — no need to call {@code .done()}.
     */
    public OverlayBuilder at(int barIndex, Consumer<BarBuilderTyped> barContent) {
        return replace(barIndex, newBuilder().bar(), barContent);
    }

    /**
     * Replace the bar at {@code barIndex} with a per-bar default duration.
     */
    public OverlayBuilder at(int barIndex, Duration barDefault, Consumer<BarBuilderTyped> barContent) {
        return replace(barIndex, newBuilder().bar(barDefault), barContent);
    }

    private OverlayBuilder replace(int barIndex, BarBuilderTyped bar, Consumer<BarBuilderTyped> barContent) {
        barContent.accept(bar);
        var P = bar.done();
        var phrase = P.build(new PhraseMarking(PhraseConnection.ATTACCA, true));
        if (phrase.bars().size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly 1 bar from overlay content, got " + phrase.bars().size());
        }
        overrides.put(barIndex, phrase.bars().getFirst());
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

    private StaffPhraseBuilderTyped newBuilder() {
        return key != null
                ? StaffPhraseBuilderTyped.in(key, ts, defaultDur)
                : StaffPhraseBuilderTyped.in(ts, defaultDur);
    }
}
