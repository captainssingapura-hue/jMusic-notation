package music.notation.performance;

import music.notation.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Microtiming humaniser as a pure {@link Performance}-to-{@link Performance}
 * transform. Replaces the legacy {@code HumanizerSetup.apply(Sequence)}
 * Sequence-mutating path: by jittering at the {@link Performance} layer
 * before {@link MidiCodec#toMidi}, the codec emits the already-jittered
 * note times directly — no post-codec walker required.
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>Each note's onset is shifted by a Gaussian sample with mean 0
 *       and σ = {@code maxJitterMs / 3} (so &gt;99.7% of jitter falls
 *       within ±{@code maxJitterMs}).</li>
 *   <li>Note duration is left untouched, so length is preserved.</li>
 *   <li>{@code drumsOnly = true} jitters only DRUM-kind tracks; other
 *       tracks pass through unchanged.</li>
 *   <li>{@code seed = 0} uses a fresh non-deterministic RNG (matches
 *       legacy convention). Any non-zero seed produces deterministic
 *       output — same input + same seed ⇒ byte-identical jittered
 *       Performance.</li>
 *   <li>Resulting onset is clamped to ≥ 0 — a Gaussian sample of
 *       −50 ms on a note at 10 ms lands the note at 0, not −40.</li>
 * </ul>
 *
 * <h2>How wall-clock ms enters a Duration-anchored model</h2>
 *
 * <p>Notes carry musical {@link Duration} positions, not ms. Jitter is
 * perceptual, so it's expressed in ms. The transform bridges with a
 * {@link TimeMapper} built from the Performance's
 * {@link TempoTrack}: each note's musical position is converted to ms
 * (forward), the jitter is added in ms space, then the new ms is
 * converted back to a musical position (inverse). At constant tempo
 * the back-and-forth is value-preserving; under tempo changes the
 * jitter remains exactly the declared σ in wall-clock terms.</p>
 */
public final class HumanizerTransform {

    private HumanizerTransform() {}

    /**
     * Configuration for {@link #apply(Performance, Params)}.
     *
     * @param maxJitterMs   3σ envelope of the timing jitter, in ms.
     *                      0 = no jitter (apply is a no-op).
     * @param drumsOnly     when true, only DRUM-kind tracks are jittered.
     * @param seed          0 = non-deterministic (fresh Random); non-zero
     *                      = deterministic (seeded Random).
     */
    public record Params(int maxJitterMs, boolean drumsOnly, long seed) {
        public Params {
            if (maxJitterMs < 0)   maxJitterMs = 0;
            if (maxJitterMs > 200) maxJitterMs = 200;
        }

        public boolean isOff() { return maxJitterMs <= 0; }

        public static final Params OFF    = new Params(0,  true, 0);
        public static final Params LIGHT  = new Params(5,  true, 0);
        public static final Params MEDIUM = new Params(10, true, 0);
        public static final Params LOOSE  = new Params(20, true, 0);
    }

    /**
     * Return a copy of {@code perf} whose every note's onset is shifted
     * by a Gaussian sample with σ = {@code params.maxJitterMs / 3}.
     * Returns the input unchanged when {@code params.isOff()} or when
     * {@code perf} is null.
     */
    public static Performance apply(Performance perf, Params params) {
        if (perf == null) return null;
        if (params == null || params.isOff()) return perf;

        Random rng = (params.seed == 0) ? new Random() : new Random(params.seed);
        double sigmaMs = params.maxJitterMs / 3.0;
        TimeMapper mapper = new TimeMapper(perf.tempo());

        List<Track> jittered = new ArrayList<>(perf.score().tracks().size());
        boolean anyChange = false;
        for (Track t : perf.score().tracks()) {
            if (params.drumsOnly && t.kind() != TrackKind.DRUM) {
                jittered.add(t);
                continue;
            }
            Track newTrack = jitterTrack(t, rng, sigmaMs, mapper);
            jittered.add(newTrack);
            if (newTrack != t) anyChange = true;
        }
        if (!anyChange) return perf;
        return perf.withScore(new Score(jittered));
    }

    private static Track jitterTrack(Track t, Random rng, double sigmaMs, TimeMapper mapper) {
        List<ConcreteNote> notes = t.notes();
        List<ConcreteNote> out = new ArrayList<>(notes.size());
        for (ConcreteNote n : notes) {
            long offsetMs = Math.round(rng.nextGaussian() * sigmaMs);
            if (offsetMs == 0) {
                out.add(n);
                continue;
            }
            long currentMs = mapper.toMs(n.at());
            long newMs = Math.max(0, currentMs + offsetMs);
            if (newMs == currentMs) {
                out.add(n);
                continue;
            }
            Duration newAt = mapper.toDuration(newMs);
            out.add(shifted(n, newAt));
        }
        return new Track(t.id(), t.kind(), out);
    }

    /**
     * Return a copy of {@code n} at the new musical position. Duration
     * and pitch are preserved verbatim. Switch on the sealed type:
     * {@link PitchedNote} (canonical), {@link ShiftedNote} (transposed
     * view — the wrap is preserved; only the inner original's position
     * shifts), and {@link DrumNote}.
     */
    private static ConcreteNote shifted(ConcreteNote n, Duration newAt) {
        return switch (n) {
            case PitchedNote pn -> new PitchedNote(newAt, pn.duration(),
                                                   pn.midi(), pn.tiedToNext());
            case ShiftedNote sn -> new ShiftedNote(
                    new PitchedNote(newAt, sn.original().duration(),
                                     sn.original().midi(), sn.original().tiedToNext()),
                    sn.semitoneShift());
            case DrumNote dn    -> new DrumNote(newAt, dn.duration(), dn.piece());
        };
    }
}
