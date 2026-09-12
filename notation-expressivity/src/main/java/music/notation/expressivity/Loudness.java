package music.notation.expressivity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import music.notation.event.Dynamic;

import java.util.Objects;

/**
 * Sum type for an authored or computed loudness anchored in the
 * dynamics sidecar. Two shapes:
 *
 * <ul>
 *   <li>{@link Named} — a symbolic mark from a score
 *       ({@code mp}, {@code f}, …). Carries the {@link Dynamic} enum
 *       value verbatim so engravers/renderers can re-emit the glyph
 *       and round-trippers (MusicXML, MIDI files with dynamic-bearing
 *       metadata) preserve authorial intent.</li>
 *   <li>{@link Raw} — a numeric loudness in {@code [0.0, 1.0]} with no
 *       symbolic equivalent. Produced by sources that work in
 *       continuous loudness: MusicXML's {@code <sound dynamics="54.44"/>}
 *       percentages, MIDI recorder input, AutoVelocity jitter,
 *       drum-pattern velocity tables, hairpin interpolation.</li>
 * </ul>
 *
 * <p>Both variants resolve to a synth-agnostic {@link #level()} in
 * {@code [0.0, 1.0]} — what the codec multiplies by 127 at the MIDI
 * emit boundary, or what a physical-modelling synth scales into its
 * own attack range.</p>
 *
 * <p>Hairpin markers ({@link Dynamic#CRESCENDO}, {@link Dynamic#DECRESCENDO})
 * are <em>not</em> permitted in {@link Named} — they're spans, not
 * set-points, and will land on a separate hairpin-span side-channel
 * (see follow-up). Constructing {@code Named(CRESCENDO)} throws.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(Loudness.Named.class),
        @JsonSubTypes.Type(Loudness.Raw.class)
})
public sealed interface Loudness {

    /** Resolved loudness in {@code [0.0, 1.0]}. */
    double level();

    /** Symbolic dynamic mark — preserves the authored glyph. */
    record Named(Dynamic mark) implements Loudness {
        public Named { Objects.requireNonNull(mark, "mark"); }
        @Override public double level() { return mark.level(); }
    }

    /** Numeric loudness with no symbolic equivalent. */
    record Raw(double level) implements Loudness {
        public Raw {
            if (level < 0.0 || level > 1.0 || Double.isNaN(level)) {
                throw new IllegalArgumentException(
                        "level must be in [0.0, 1.0]: " + level);
            }
        }
    }

    // ── Convenience constructors ─────────────────────────────────────

    static Loudness of(Dynamic mark)  { return new Named(mark); }
    static Loudness of(double level)  { return new Raw(level); }
}
