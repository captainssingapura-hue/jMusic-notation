package music.notation.phrase;

import music.notation.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One-shot bar-scoped sub-builder. Created by
 * {@link StaffPhraseBuilderTyped#bar()} / {@code pickup()} / {@code ending()}
 * and terminated by {@link #done()} which appends the finished {@link Bar}
 * to the parent phrase builder and returns control there.
 *
 * <p>No {@code bar()} or {@code build()} methods exist here — the compiler
 * forces callers to chain through {@code .done()} before starting the next
 * bar or building the phrase, making "forgot to close the bar" unrepresentable.</p>
 *
 * <p>Per-bar {@link Kind} distinguishes normal bars from {@link Kind#PICKUP
 * pickup} bars — only pickups auto-pad, prepending a leading
 * {@link PaddingNode} at {@link #done()} time so the anacrusis sums to a full
 * bar. Every other form of padding (including trailing / ending) must be
 * declared explicitly via {@link #pad(Duration)}.</p>
 */
public final class BarBuilderTyped extends NoteAcceptor<BarBuilderTyped> {

    /** Distinguishes normal bars from pickups (leading pad via {@link PaddingNode}). */
    enum Kind { NORMAL, PICKUP }

    private final StaffPhraseBuilderTyped parent;
    private final Kind kind;
    private final List<AuxBar> auxSlots = new ArrayList<>();

    BarBuilderTyped(StaffPhraseBuilderTyped parent, BuilderContext ctx,
                    Duration activeDur, Kind kind) {
        super(ctx, activeDur);
        this.parent = parent;
        this.kind = kind;
    }

    // ── Aux (voice overlay — whole bar, lambda-scoped) ──────────

    /**
     * Attach a whole-bar voice overlay to this bar. The lambda receives a
     * one-shot {@link AuxBarBuilderTyped}; on lambda exit its content is
     * captured as an {@link AuxBar} and attached to this bar's aux slots.
     * Multiple {@code aux(...)} calls on the same bar add successive voice
     * indices (0, 1, 2, ...).
     */
    public BarBuilderTyped aux(Consumer<AuxBarBuilderTyped> content) {
        return aux(null, content);
    }

    /** Aux overlay with an explicit per-bar default duration for the sub-builder. */
    public BarBuilderTyped aux(Duration perBarDefault, Consumer<AuxBarBuilderTyped> content) {
        requireNotConsumed();
        var aux = new AuxBarBuilderTyped(ctx, perBarDefault);
        content.accept(aux);
        auxSlots.add(aux.toAuxBar());
        return this;
    }

    // ── Termination ──────────────────────────────────────────────

    /**
     * Close this bar and return control to the phrase builder. The collected
     * nodes (plus any aux slots) are frozen into a {@link Bar} and appended
     * to the phrase. Any further call on this {@code BarBuilderTyped} throws.
     */
    public StaffPhraseBuilderTyped done() {
        requireNotConsumed();
        consumed = true;

        final int barSize = ctx.ts().barSixtyFourths();
        final List<PhraseNode> nodes = current;

        if (kind == Kind.PICKUP) {
            // Pickup: auto-prepend leading PaddingNode so the bar sums to
            // barSize. This is a convenience for the anacrusis case where
            // the content starts mid-bar; every other kind of padding must
            // be declared explicitly via {@link #pad(Duration)}.
            int total = nodes.stream().mapToInt(Bar::nodeSixtyFourths).sum();
            int padding = barSize - total;
            if (padding > 0) {
                nodes.addFirst(new PaddingNode(Duration.ofSixtyFourths(padding)));
            }
        }
        // NORMAL: no auto-padding. Bar's constructor validates exact fit.

        parent.appendBar(new Bar(barSize, nodes, auxSlots));
        return parent;
    }
}
