package music.notation.phrase;

import music.notation.duration.Duration;

/**
 * Whole-bar aux (voice overlay) sub-builder, used exclusively through a lambda
 * on {@link BarBuilderTyped#aux(java.util.function.Consumer)}.
 *
 * <p>Emits a single {@link AuxBar} matching its parent bar's duration. Lambda
 * scope guarantees the instance cannot escape — by the time the lambda returns,
 * the parent bar has captured the aux content and the builder is unreachable.
 * There is no {@code done()} or {@code aux()} here: aux slots cannot nest,
 * and termination is implicit at lambda exit.</p>
 *
 * <p>If the aux content totals less than one bar, trailing {@link RestNode}
 * is appended by {@link MelodicPhrase#fromBars} when the parent phrase is
 * constructed. Over-long aux content throws at phrase construction time.</p>
 */
public final class AuxBarBuilderTyped extends NoteAcceptor<AuxBarBuilderTyped> {

    AuxBarBuilderTyped(BuilderContext ctx, Duration activeDur) {
        super(ctx, activeDur);
    }

    /** Freeze the collected nodes into an {@link AuxBar}. */
    AuxBar toAuxBar() {
        consumed = true;
        return new AuxBar(current);
    }
}
