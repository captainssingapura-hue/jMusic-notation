package music.notation.phrase;

import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.structure.KeySignature;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Typed, one-shot, stratified builder for melodic phrases.
 *
 * <p>The public surface is split into three scoped types so the compiler
 * enforces correct call order:</p>
 *
 * <ul>
 *   <li>{@code StaffPhraseBuilderTyped} — accepts bars, builds the phrase.</li>
 *   <li>{@link BarBuilderTyped} — collects notes and aux voices for one bar;
 *       terminated by {@code done()}.</li>
 *   <li>{@link AuxBarBuilderTyped} — whole-bar voice overlay inside a bar,
 *       always lambda-scoped.</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * var phrase = StaffPhraseBuilderTyped.in(KEY, TS, EIGHTH)
 *         .pickup().mf().o4(QUARTER, E).done()
 *         .bar().o5(C).o5(D).o5(QUARTER, E).o5(QUARTER, F).done()
 *         .bar(QUARTER).o5(HALF, G)
 *             .aux(HALF, a -> a.r().o4(C)).done()
 *         .bar().o5(HALF, C).pad(HALF).done()   // explicit trailing pad
 *         .build(end());
 * }</pre>
 *
 * <p>Padding is always explicit: no {@code .ending()} auto-pads the last
 * bar. Use {@link BarBuilderTyped#pad(Duration)} wherever you need
 * structural silence. Pickups are the one exception — {@code .pickup()}
 * auto-prepends leading padding so the anacrusis sums to a full bar.</p>
 *
 * <p>Every builder instance in the chain is <b>one-shot</b>: once
 * {@code done()} (on a {@code BarBuilderTyped}) or {@code build()} (here)
 * returns, the instance refuses further calls with
 * {@link IllegalStateException}. Re-use is explicitly disallowed — each
 * phrase needs a fresh top-level builder.</p>
 */
public final class StaffPhraseBuilderTyped {

    private final BuilderContext ctx;
    private final List<Bar> bars = new ArrayList<>();
    private boolean consumed;

    private StaffPhraseBuilderTyped(BuilderContext ctx) {
        this.ctx = ctx;
    }

    // ── Factories ────────────────────────────────────────────────

    public static StaffPhraseBuilderTyped in(TimeSignature ts) {
        return new StaffPhraseBuilderTyped(new BuilderContext(ts, BaseValue.EIGHTH, Map.of()));
    }

    public static StaffPhraseBuilderTyped in(TimeSignature ts, Duration defaultDur) {
        return new StaffPhraseBuilderTyped(new BuilderContext(ts, defaultDur, Map.of()));
    }

    public static StaffPhraseBuilderTyped in(KeySignature key, TimeSignature ts) {
        return new StaffPhraseBuilderTyped(
                new BuilderContext(ts, BaseValue.EIGHTH, KeyAccidentals.forKey(key)));
    }

    public static StaffPhraseBuilderTyped in(KeySignature key, TimeSignature ts, Duration defaultDur) {
        return new StaffPhraseBuilderTyped(
                new BuilderContext(ts, defaultDur, KeyAccidentals.forKey(key)));
    }

    // ── Bar openers ──────────────────────────────────────────────

    public BarBuilderTyped bar()                        { return openBar(null, BarBuilderTyped.Kind.NORMAL); }
    public BarBuilderTyped bar(Duration perBarDefault)  { return openBar(perBarDefault, BarBuilderTyped.Kind.NORMAL); }

    /**
     * Start a pickup (anacrusis) bar — the only bar kind that auto-pads.
     * A leading {@link PaddingNode} is prepended at {@code done()} so content
     * starting mid-bar sums to a full bar. Every other form of padding
     * (trailing silence, mid-bar gap) must be declared explicitly via
     * {@link BarBuilderTyped#pad(Duration)}.
     */
    public BarBuilderTyped pickup()                        { return openBar(null, BarBuilderTyped.Kind.PICKUP); }
    public BarBuilderTyped pickup(Duration perBarDefault)  { return openBar(perBarDefault, BarBuilderTyped.Kind.PICKUP); }

    private BarBuilderTyped openBar(Duration perBarDefault, BarBuilderTyped.Kind kind) {
        requireNotConsumed();
        return new BarBuilderTyped(this, ctx, perBarDefault, kind);
    }

    // ── Termination ──────────────────────────────────────────────

    /**
     * Finalize the phrase. Any aux overlays declared along the way travel
     * with the result as {@link VoiceOverlay}s on the returned
     * {@link MelodicPhrase}.
     */
    public MelodicPhrase build(PhraseMarking marking) {
        requireNotConsumed();
        consumed = true;
        return MelodicPhrase.fromBars(ctx.ts(), marking, bars.toArray(Bar[]::new));
    }

    // ── Package-private callback from BarBuilderTyped.done() ────

    void appendBar(Bar bar) {
        bars.add(bar);
    }

    // ── One-shot guard ───────────────────────────────────────────

    private void requireNotConsumed() {
        if (consumed) {
            throw new IllegalStateException(
                    "StaffPhraseBuilderTyped already built — construct a fresh builder "
                            + "for each phrase; builders are one-shot by design.");
        }
    }
}
