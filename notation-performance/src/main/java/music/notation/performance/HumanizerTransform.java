package music.notation.performance;

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
 *   <li>Each note's {@code tickMs} is shifted by a Gaussian sample
 *       with mean 0 and σ = {@code maxJitterMs / 3} (so &gt;99.7% of
 *       jitter falls within ±{@code maxJitterMs}).</li>
 *   <li>Note {@code durationMs} is left untouched, so duration is
 *       preserved — much simpler than the legacy approach which
 *       had to track NOTE_ON/NOTE_OFF pairs across MIDI events.</li>
 *   <li>{@code drumsOnly = true} jitters only DRUM-kind tracks; other
 *       tracks pass through unchanged.</li>
 *   <li>{@code seed = 0} uses a fresh non-deterministic RNG (matches
 *       legacy convention). Any non-zero seed produces deterministic
 *       output — same input + same seed ⇒ byte-identical jittered
 *       Performance.</li>
 *   <li>Resulting {@code tickMs} is clamped to ≥ 0 — a Gaussian
 *       sample of −50 ms on a note at tickMs=10 lands the note at 0,
 *       not −40.</li>
 * </ul>
 *
 * <h2>Why ms-anchored is cleaner than the legacy</h2>
 *
 * <p>Legacy {@code HumanizerSetup.apply(Sequence)} interpreted
 * {@code maxJitterMs} at 120 bpm and let real-time jitter scale with
 * playback tempo (a piece at 60 bpm heard 2× the jitter). At the
 * {@link Performance} layer we work in real ms anchored by
 * {@link TempoTrack} — there's no PPQ fudge, jitter is exactly the
 * declared σ in real wall-clock terms regardless of tempo.</p>
 */
public final class HumanizerTransform {

    private HumanizerTransform() {}

    /**
     * Configuration for {@link #apply(Performance, Params)}.
     *
     * @param maxJitterMs   3σ envelope of the timing jitter, in ms.
     *                      0 = no jitter (apply is a no-op).
     * @param drumsOnly     when true, only DRUM-kind tracks are jittered.
     *                      Pitched-only humanisation is unusual but supported
     *                      by setting drumsOnly = false.
     * @param seed          0 = non-deterministic (fresh Random); non-zero
     *                      = deterministic (seeded Random).
     */
    public record Params(int maxJitterMs, boolean drumsOnly, long seed) {
        public Params {
            if (maxJitterMs < 0)   maxJitterMs = 0;
            if (maxJitterMs > 200) maxJitterMs = 200;
        }

        public boolean isOff() { return maxJitterMs <= 0; }

        /** No humanisation. */
        public static final Params OFF    = new Params(0,  true, 0);
        /** ±5 ms jitter on drums. */
        public static final Params LIGHT  = new Params(5,  true, 0);
        /** ±10 ms jitter on drums. */
        public static final Params MEDIUM = new Params(10, true, 0);
        /** ±20 ms jitter on drums. */
        public static final Params LOOSE  = new Params(20, true, 0);
    }

    /**
     * Return a copy of {@code perf} whose every note's {@code tickMs}
     * is shifted by a Gaussian sample with σ = {@code params.maxJitterMs / 3}.
     * Returns the input unchanged when {@code params.isOff()} or when
     * {@code perf} is null.
     */
    public static Performance apply(Performance perf, Params params) {
        if (perf == null) return null;
        if (params == null || params.isOff()) return perf;

        Random rng = (params.seed == 0) ? new Random() : new Random(params.seed);
        double sigmaMs = params.maxJitterMs / 3.0;

        List<Track> jittered = new ArrayList<>(perf.score().tracks().size());
        boolean anyChange = false;
        for (Track t : perf.score().tracks()) {
            if (params.drumsOnly && t.kind() != TrackKind.DRUM) {
                jittered.add(t);
                continue;
            }
            Track newTrack = jitterTrack(t, rng, sigmaMs);
            jittered.add(newTrack);
            if (newTrack != t) anyChange = true;
        }
        if (!anyChange) return perf;
        return perf.withScore(new Score(jittered));
    }

    private static Track jitterTrack(Track t, Random rng, double sigmaMs) {
        List<ConcreteNote> notes = t.notes();
        List<ConcreteNote> out = new ArrayList<>(notes.size());
        for (ConcreteNote n : notes) {
            long offset = Math.round(rng.nextGaussian() * sigmaMs);
            long newTick = Math.max(0, n.tickMs() + offset);
            if (newTick == n.tickMs()) {
                out.add(n);
                continue;
            }
            out.add(shifted(n, newTick));
        }
        return new Track(t.id(), t.kind(), out);
    }

    /**
     * Return a copy of {@code n} at the new tickMs. Duration and pitch
     * are preserved verbatim. Switch on the sealed type:
     * {@link PitchedNote} (canonical), {@link ShiftedNote} (transposed
     * view — the wrap is preserved; only the inner original's tickMs
     * shifts), and {@link DrumNote}.
     */
    private static ConcreteNote shifted(ConcreteNote n, long newTick) {
        return switch (n) {
            case PitchedNote pn -> new PitchedNote(newTick, pn.durationMs(),
                                                   pn.midi(), pn.tiedToNext());
            case ShiftedNote sn -> new ShiftedNote(
                    new PitchedNote(newTick, sn.original().durationMs(),
                                     sn.original().midi(), sn.original().tiedToNext()),
                    sn.semitoneShift());
            case DrumNote dn    -> new DrumNote(newTick, dn.durationMs(), dn.piece());
        };
    }
}
