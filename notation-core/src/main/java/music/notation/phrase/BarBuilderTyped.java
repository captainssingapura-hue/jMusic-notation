package music.notation.phrase;

import music.notation.duration.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One-shot bar-scoped sub-builder. Created by
 * {@link StaffPhraseBuilderTyped#bar()} / {@code pickup()} / {@code ending()}
 * and terminated by {@link #done()} which appends the finished {@link Bar}
 * (plus any aux voices declared inline) to the parent phrase builder.
 *
 * <p>Per-bar {@link Kind} distinguishes normal bars from {@link Kind#PICKUP
 * pickup} bars — only pickups auto-pad. Aux content authored via
 * {@link #aux(Consumer)} or {@link #aux(String, Consumer)} flows up to the
 * staff builder under the named voice (default name = {@code "default"}).</p>
 */
public final class BarBuilderTyped extends NoteAcceptor<BarBuilderTyped> {

    enum Kind { NORMAL, PICKUP }

    private final StaffPhraseBuilderTyped parent;
    private final Kind kind;
    /** Aux content authored on this bar, name → aux Bar. Built at done() time. */
    private final Map<String, AuxBarBuilderTyped> auxBuilders = new LinkedHashMap<>();

    BarBuilderTyped(StaffPhraseBuilderTyped parent, BuilderContext ctx,
                    Duration activeDur, Kind kind) {
        super(ctx, activeDur);
        this.parent = parent;
        this.kind = kind;
    }

    // ── Aux ─────────────────────────────────────────────────────

    /** Aux on the default voice (name = {@code "default"}). */
    public BarBuilderTyped aux(Consumer<AuxBarBuilderTyped> content) {
        return aux("default", null, content);
    }

    /** Aux on the default voice with an explicit per-bar default duration. */
    public BarBuilderTyped aux(Duration perBarDefault, Consumer<AuxBarBuilderTyped> content) {
        return aux("default", perBarDefault, content);
    }

    /** Aux on a named voice. */
    public BarBuilderTyped aux(String name, Consumer<AuxBarBuilderTyped> content) {
        return aux(name, null, content);
    }

    /** Aux on a named voice with an explicit per-bar default duration. */
    public BarBuilderTyped aux(String name, Duration perBarDefault,
                               Consumer<AuxBarBuilderTyped> content) {
        requireNotConsumed();
        String n = (name == null || name.isBlank()) ? "default" : name;
        if (auxBuilders.containsKey(n)) {
            throw new IllegalStateException(
                    "Bar already has aux content for voice '" + n + "'");
        }
        var ab = new AuxBarBuilderTyped(ctx, perBarDefault);
        content.accept(ab);
        auxBuilders.put(n, ab);
        return this;
    }

    // ── Termination ─────────────────────────────────────────────

    public StaffPhraseBuilderTyped done() {
        requireNotConsumed();
        consumed = true;

        final int barSize = ctx.ts().barSixtyFourths();
        final List<PhraseNode> nodes = current;

        if (kind == Kind.PICKUP) {
            int total = nodes.stream().mapToInt(Bar::nodeSixtyFourths).sum();
            int padding = barSize - total;
            if (padding > 0) {
                nodes.addFirst(new PaddingNode(Duration.ofSixtyFourths(padding)));
            }
        }

        Bar bar = new Bar(music.notation.duration.BarDuration.fromSixtyFourths(barSize), nodes);
        Map<String, Bar> auxForBar;
        if (auxBuilders.isEmpty()) {
            auxForBar = Map.of();
        } else {
            auxForBar = new LinkedHashMap<>(auxBuilders.size());
            for (var e : auxBuilders.entrySet()) {
                auxForBar.put(e.getKey(), e.getValue().toBar(barSize));
            }
        }
        parent.appendBar(bar, auxForBar);
        return parent;
    }
}
