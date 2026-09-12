package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-function pre-pass that flattens the {@link Hairpins} side-channel
 * into dense {@link VelocityChange}/{@link VolumeChange} entries on the
 * existing {@link Velocities}/{@link Volume} timelines. Run as a prelude
 * to {@link MidiCodec#toMidi} so the codec proper never sees hairpins.
 *
 * <h2>Algorithm</h2>
 * <p>For each {@link HairpinSpan} on each track:</p>
 * <ol>
 *   <li>Resolve the <em>start level</em> from the discrete
 *       {@link VelocityControl} at-or-before the span's start
 *       (falls back to {@link VelocityControl#DEFAULT_LEVEL} when no
 *       prior set-point exists).</li>
 *   <li>Resolve the <em>end level</em> from the discrete timeline
 *       at-or-after the span's end (falls back to one
 *       {@link #ONE_NAMED_STEP named-step} in the hairpin's direction
 *       when no following set-point exists).</li>
 *   <li>Treat any discrete set-points <em>inside</em> the span as
 *       waypoints — split the span into anchor-to-anchor segments.</li>
 *   <li>Within each segment, emit synthetic
 *       {@link VelocityChange}/{@link VolumeChange} events at
 *       {@link #SAMPLE_STEP 1/16-note} intervals, linearly
 *       interpolating between the segment endpoints.</li>
 * </ol>
 *
 * <h2>Codec contract change</h2>
 * <p>The returned {@link Performance} has {@code Hairpins.empty()} —
 * hairpins are "consumed." A consumer that needs the original spans
 * (a score renderer drawing {@code <} {@code >} glyphs) should read
 * the input {@link Performance} directly, not the flattened output.</p>
 *
 * <p>Round-trip parity: {@code Hairpins} is non-recoverable from MIDI
 * (the flattened CC #7 ramps become opaque dense set-points on
 * {@code fromMidi}). Audible behaviour is preserved; the sparse hairpin
 * shape is lost.</p>
 */
public final class HairpinResolver {

    /**
     * Sample density for ramp interpolation. Every 1/16 note keeps
     * stepping inaudible at typical tempi (~125 ms at 120 bpm) without
     * flooding the MIDI bandwidth.
     */
    public static final Duration SAMPLE_STEP = Duration.of(1, 16);

    /**
     * Conventional level gap when a hairpin has no closing discrete
     * mark — approximates "one named-step." 0.13 matches the typical
     * gap between adjacent {@link music.notation.event.Dynamic} marks
     * (e.g., p→mp = 0.39→0.50 ≈ 0.11; mp→mf = 0.50→0.63 = 0.13).
     */
    public static final double ONE_NAMED_STEP = 0.13;

    private HairpinResolver() {}

    /**
     * Flatten {@code p}'s hairpins into the {@link Velocities}/{@link Volume}
     * timelines. Returns {@code p} unchanged when no hairpins are present.
     */
    public static Performance flatten(Performance p) {
        if (p.hairpins().byTrack().isEmpty()) return p;

        Map<TrackId, VelocityControl> newVelMap = new LinkedHashMap<>(p.velocities().byTrack());
        Map<TrackId, VolumeControl>   newVolMap = new LinkedHashMap<>(p.volume().byTrack());

        for (var e : p.hairpins().byTrack().entrySet()) {
            TrackId track = e.getKey();
            HairpinControl hpc = e.getValue();

            VelocityControl velIn = newVelMap.getOrDefault(track, VelocityControl.empty());
            VolumeControl   volIn = newVolMap.getOrDefault(track, VolumeControl.empty());

            List<VelocityChange> velSamples = new ArrayList<>();
            List<VolumeChange>   volSamples = new ArrayList<>();

            for (HairpinSpan span : hpc.spans()) {
                emitSamples(span, velIn, velSamples, volSamples);
            }

            // Merge synthetic samples with the existing discrete timeline.
            // VelocityControl's compact ctor sorts by position and dedups by
            // Loudness equality. Discrete Named(F) entries are preserved
            // even when a synthetic Raw(0.76) sample lands at the same
            // position — they're not equal as Loudness records. The
            // levelAt() step-function lookup then picks the last entry
            // at-or-before the query, which is the discrete mark
            // (inserted before the samples), preserving authorial intent.
            List<VelocityChange> mergedVel = new ArrayList<>(velIn.changes().size() + velSamples.size());
            mergedVel.addAll(velIn.changes());
            mergedVel.addAll(velSamples);
            newVelMap.put(track, new VelocityControl(mergedVel));

            List<VolumeChange> mergedVol = new ArrayList<>(volIn.changes().size() + volSamples.size());
            mergedVol.addAll(volIn.changes());
            mergedVol.addAll(volSamples);
            newVolMap.put(track, new VolumeControl(mergedVol));
        }

        // Rebuild Performance with new Velocities/Volume + empty Hairpins.
        return new Performance(
                p.score(), p.tempo(), p.instruments(),
                new Volume(newVolMap),
                p.articulations(), p.pedaling(),
                new Velocities(newVelMap),
                Hairpins.empty(),
                p.lyrics(), p.timeSignatures(), p.keySignatures());
    }

    /**
     * Emit synthetic samples for one hairpin span. Walks segments
     * defined by the bracketing discrete set-points; within each
     * segment, places samples at {@link #SAMPLE_STEP} intervals
     * strictly between the segment endpoints (endpoints are anchored
     * by the existing discrete timeline and don't need duplication).
     */
    private static void emitSamples(HairpinSpan span,
                                     VelocityControl discreteVel,
                                     List<VelocityChange> velOut,
                                     List<VolumeChange>   volOut) {
        double startLevel = discreteVel.levelAt(span.from());
        double endLevel   = endLevelFor(span, discreteVel);

        // Anchor points = span.from + interior discrete positions + span.to.
        // Each anchor carries a "level" — for span.from it's startLevel,
        // for interior anchors it's the discrete level at that position,
        // for span.to it's endLevel.
        List<Anchor> anchors = new ArrayList<>();
        anchors.add(new Anchor(span.from(), startLevel));
        for (VelocityChange c : discreteVel.changes()) {
            int cmpStart = c.at().compareDuration(span.from());
            int cmpEnd   = c.at().compareDuration(span.to());
            if (cmpStart > 0 && cmpEnd < 0) {
                anchors.add(new Anchor(c.at(), c.level()));
            }
        }
        anchors.add(new Anchor(span.to(), endLevel));

        // Walk each segment and emit interpolated samples strictly inside.
        for (int i = 0; i + 1 < anchors.size(); i++) {
            Anchor a = anchors.get(i);
            Anchor b = anchors.get(i + 1);
            double segLen = asFraction(b.at.minus(a.at));
            if (segLen <= 0) continue;
            double levelSpan = b.level - a.level;

            Duration cursor = a.at.plus(SAMPLE_STEP);
            while (cursor.compareDuration(b.at) < 0) {
                double t = asFraction(cursor.minus(a.at)) / segLen;
                double level = clamp(a.level + levelSpan * t, 0.0, 1.0);
                velOut.add(new VelocityChange(cursor, level));
                volOut.add(new VolumeChange(cursor, level));
                cursor = cursor.plus(SAMPLE_STEP);
            }
        }
    }

    /**
     * The end-level for a hairpin: the discrete set-point at-or-just-after
     * {@code span.to}, or — failing that — one {@link #ONE_NAMED_STEP}
     * in the hairpin's direction from the start level.
     */
    private static double endLevelFor(HairpinSpan span, VelocityControl discreteVel) {
        for (VelocityChange c : discreteVel.changes()) {
            if (c.at().compareDuration(span.to()) >= 0) {
                return c.level();
            }
        }
        double startLevel = discreteVel.levelAt(span.from());
        return clamp(span.direction() == Direction.CRESCENDO
                ? startLevel + ONE_NAMED_STEP
                : startLevel - ONE_NAMED_STEP, 0.0, 1.0);
    }

    /** Duration → fraction-of-whole-note as a double (lossy but bounded). */
    private static double asFraction(Duration d) {
        return (double) d.numerator() / (double) d.denominator();
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private record Anchor(Duration at, double level) {}
}
