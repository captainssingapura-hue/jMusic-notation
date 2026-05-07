package music.notation.autodrum;

import music.notation.structure.DrumTrack;
import music.notation.structure.Piece;

import java.util.Optional;

/**
 * Pluggable drum-accompaniment generator.
 *
 * <p>Each {@code DrumStrategy} inspects a source {@link Piece} and decides
 * whether (and how) to lay down a percussion track. The result is a
 * {@link DrumTrack} that callers layer onto the Piece for live playback —
 * the strategy itself is stateless and never mutates the source.</p>
 *
 * <p>To add a new strategy, implement this interface and register the
 * instance in {@link DrumStrategies#available()}. Strategies are expected
 * to be cheap to instantiate and side-effect-free; one instance can be
 * shared across the application.</p>
 *
 * <p>{@link #generate(Piece)} returns {@link Optional#empty()} when the
 * strategy decides the piece is best left dry — for example, unsupported
 * time signature, or the source already carries its own drum track.
 * The default {@link #appliesTo(Piece)} implementation captures the
 * "no existing drums" rule once for all strategies.</p>
 */
public interface DrumStrategy {

    /** Stable identifier (kebab-case). Useful for logs and future persistence. */
    String id();

    /** Display name shown in pickers. */
    String displayName();

    /** One-liner shown in tooltips. */
    String description();

    /**
     * Whether this strategy may apply to {@code source}. The default rule
     * is universal: a piece that already has a {@link DrumTrack} should
     * not be overlaid with an auto-generated one.
     *
     * <p>Strategies may tighten this further (e.g. time-signature checks)
     * by overriding and combining with {@code DrumStrategy.super.appliesTo}.</p>
     */
    default boolean appliesTo(Piece source) {
        return source.tracks().stream().noneMatch(t -> t instanceof DrumTrack);
    }

    /**
     * Produce the accompaniment at the given {@link Energy} level.
     * Returns {@link Optional#empty()} when the strategy declines (per
     * {@link #appliesTo}, time-signature mismatch, empty piece, etc.).
     * The returned {@link DrumTrack} should have the same number of bars
     * as the source and is intended to be appended to the Piece's track
     * list.
     */
    Optional<DrumTrack> generate(Piece source, Energy energy);

    /** Convenience overload — defaults to {@link Energy#MEDIUM}. */
    default Optional<DrumTrack> generate(Piece source) {
        return generate(source, Energy.MEDIUM);
    }

    /**
     * Velocity-aware variant — returns the {@link DrumTrack} plus the
     * matching {@link music.notation.expressivity.VelocityControl
     * VelocityControl} when the strategy's patterns declare per-slot
     * velocities. Default implementation wraps {@link #generate} with
     * an empty velocity timeline; strategies opting in should override
     * to thread {@link Patterns#generateTrackWithVelocities} instead.
     */
    default Optional<GeneratedDrums> generateWithVelocities(Piece source, Energy energy) {
        return GeneratedDrums.wrap(generate(source, energy));
    }
}
