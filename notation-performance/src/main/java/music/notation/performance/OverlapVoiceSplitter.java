package music.notation.performance;

import music.notation.performance.OnsetGrouper.GroupedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tier 2 voice separation — partitions a grouped-event stream into
 * <em>N</em> monophonic voices using time-overlap as the splitting
 * signal.
 *
 * <p>The principle: a single voice plays only one event at a time. If
 * a new event starts while an existing event is still sounding, the
 * new event must belong to a different voice. Greedy assignment with
 * a time + pitch cost picks "the voice that just freed up and whose
 * last pitch is closest to this event."</p>
 *
 * <p>Designed to run <em>after</em> {@link PitchBandSplitter}: feed
 * the high band into one call and the low band into another. Tier 2
 * resolves remaining in-band overlap (e.g. melody + sustained pad on
 * the right hand). Monophonic input passes through as a single voice.</p>
 *
 * <p>See {@code .docs/voice-separation/tier-2-overlap-voice.md}.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class OverlapVoiceSplitter {

    private OverlapVoiceSplitter() {}

    /**
     * Tunable knobs for the cost function used to pick which existing
     * voice a new event extends.
     *
     * @param maxVoices   upper bound on the number of output voices.
     *                    Beyond this, events are folded into the
     *                    pitch-nearest voice (lossy).
     * @param timeWeight  cost per ms of voice inactivity. Higher
     *                    values make freshly-freed voices preferred.
     * @param pitchWeight cost per MIDI semitone of pitch distance
     *                    from the voice's last centroid. Higher
     *                    values discourage voice-crossing.
     */
    public record Config(int maxVoices, double timeWeight, double pitchWeight) {
        public Config {
            if (maxVoices < 1)    throw new IllegalArgumentException("maxVoices must be >= 1");
            if (timeWeight < 0)   throw new IllegalArgumentException("timeWeight must be >= 0");
            if (pitchWeight < 0)  throw new IllegalArgumentException("pitchWeight must be >= 0");
        }

        /** Sensible defaults: 4 voices, 1 ms vs 16 ms per semitone. */
        public static Config defaults() { return new Config(4, 1.0, 16.0); }
    }

    /** Result of a split: voices sorted by descending mean pitch (voice 0 = highest). */
    public record SplitResult(List<List<GroupedEvent>> voices) {
        public SplitResult {
            voices = List.copyOf(voices);
        }
        public int size() { return voices.size(); }
    }

    public static SplitResult split(List<GroupedEvent> events) {
        return split(events, Config.defaults());
    }

    public static SplitResult split(List<GroupedEvent> events, Config cfg) {
        if (events.isEmpty()) return new SplitResult(List.of());

        // Defensive: ensure events are in onset order.
        var sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparingLong(GroupedEvent::onsetMs));

        var voices = new ArrayList<VoiceTracker>();

        for (GroupedEvent ev : sorted) {
            VoiceTracker target = pickVoiceForEvent(voices, ev, cfg);
            if (target == null) {
                // No free voice and we're under the cap — start a new one.
                target = new VoiceTracker();
                voices.add(target);
            }
            target.append(ev);
        }
        return finalize(voices);
    }

    /**
     * Choose the existing voice that should claim {@code ev}. Returns
     * {@code null} if (a) no voice is free and (b) we haven't yet hit
     * {@code maxVoices} — caller starts a new voice in that case.
     * Returns the pitch-nearest voice (forced overlap) when full.
     */
    private static VoiceTracker pickVoiceForEvent(
            List<VoiceTracker> voices, GroupedEvent ev, Config cfg) {
        VoiceTracker bestFree = null;
        double bestFreeCost   = Double.POSITIVE_INFINITY;
        VoiceTracker bestAny  = null;
        double bestAnyPitchD  = Double.POSITIVE_INFINITY;

        for (VoiceTracker v : voices) {
            double pitchD = Math.abs(v.lastCentroid - ev.centroid());
            if (pitchD < bestAnyPitchD) {
                bestAnyPitchD = pitchD;
                bestAny = v;
            }
            if (v.lastEndMs <= ev.onsetMs()) {   // voice is free
                double timeGap = ev.onsetMs() - v.lastEndMs;
                double cost    = timeGap * cfg.timeWeight()
                               + pitchD  * cfg.pitchWeight();
                if (cost < bestFreeCost) {
                    bestFreeCost = cost;
                    bestFree     = v;
                }
            }
        }
        if (bestFree != null) return bestFree;
        // No free voice. Spawn another if we have headroom.
        if (voices.size() < cfg.maxVoices()) return null;
        // Cap reached: force-assign to nearest. Last note in that voice
        // gets overlapped (acceptable trade-off for slice-1).
        return bestAny;
    }

    /** Sort voices by descending mean pitch and pack into the result. */
    private static SplitResult finalize(List<VoiceTracker> voices) {
        voices.sort((a, b) -> Double.compare(b.meanPitch(), a.meanPitch()));
        var out = new ArrayList<List<GroupedEvent>>(voices.size());
        for (VoiceTracker v : voices) {
            if (!v.events.isEmpty()) out.add(List.copyOf(v.events));
        }
        return new SplitResult(out);
    }

    /** Mutable per-voice scratch: events + cached "last end" + "last pitch centroid". */
    private static final class VoiceTracker {
        final List<GroupedEvent> events = new ArrayList<>();
        long lastEndMs = Long.MIN_VALUE;
        double lastCentroid = 0;
        double pitchSum = 0;
        int    pitchCount = 0;

        void append(GroupedEvent ev) {
            events.add(ev);
            long end = ev.onsetMs() + ev.durationMs();
            if (end > lastEndMs) lastEndMs = end;
            lastCentroid = ev.centroid();
            pitchSum   += ev.centroid();
            pitchCount += 1;
        }

        double meanPitch() {
            return pitchCount == 0 ? 0 : pitchSum / pitchCount;
        }
    }
}
