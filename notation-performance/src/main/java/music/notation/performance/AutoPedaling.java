package music.notation.performance;

import music.notation.expressivity.*;

import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Generates a sustain-pedal {@link Pedaling} timeline for a
 * {@link Performance} that doesn't carry one of its own. Used as a
 * fallback when an MXL source omits {@code <pedal>} markings — every
 * piano piece gets the pedal-rich playback typical of how pianists
 * actually perform.
 *
 * <p>Heuristic (V2 — bass-aware):</p>
 * <ol>
 *   <li>{@link PedalState#DOWN DOWN} at piece start.</li>
 *   <li>{@link PedalState#CHANGE CHANGE} at every bar boundary
 *       (release-and-re-press), in tempo-aware ms via
 *       {@link TempoConversion}.</li>
 *   <li>Additional {@link PedalState#CHANGE CHANGE} at any **mid-bar
 *       bass-note movement** — when the lowest sounding pitch
 *       (restricted to the bass register, MIDI &lt; 60) changes from
 *       one onset to the next. This mirrors how pianists pedal: the
 *       bass note is what binds harmony, so each new bass = new
 *       chord = new pedal.</li>
 *   <li>{@link PedalState#UP UP} clamped to the last note's tail.</li>
 * </ol>
 *
 * <p>Bass changes within {@link #MIN_GAP_MS} of an existing bar
 * boundary or another bass change are dropped to avoid over-pedaling
 * (chromatic walks, ornaments, ghost-bass passages). The pitch
 * threshold prevents treble-only melodic motion from triggering CHANGEs
 * — those pieces fall back to plain bar-only auto-pedal.</p>
 *
 * <p>Drum tracks are skipped — the damper pedal is a piano-instrument
 * concept.</p>
 */
public final class AutoPedaling {

    /**
     * Notes at or above this MIDI value are NOT considered "bass" for
     * harmonic-change detection. Middle C — typical hand split for a
     * piano part. Treble-only pieces never trigger mid-bar CHANGEs.
     */
    static final int BASS_PITCH_THRESHOLD = 60;

    /**
     * Minimum ms between two consecutive emitted CHANGEs. Suppresses
     * over-pedaling on chromatic bass walks, grace notes, and bass
     * onsets that land near a bar boundary. ≈ 16th note at 120 bpm.
     */
    static final long MIN_GAP_MS = 200;

    /**
     * Onsets within this tolerance are treated as the same chord for
     * bass-detection purposes. Covers the slight skew between an MXL
     * import's left-hand and right-hand parts, or a notated chord
     * spread across a tiny humanisation interval.
     */
    static final long CHORD_GROUP_TOL_MS = 25;

    private AutoPedaling() {}

    /**
     * Generate a bass-aware auto-pedaling. Returns {@link Pedaling#empty()}
     * when the performance is empty, when no pitched tracks are present,
     * or when the time signature is null.
     *
     * <p>Bar boundaries follow the performance's own {@link TempoTrack}
     * — accelerandi, ritardandi and tempo set-points are honoured.
     * Mid-bar CHANGEs are added at bass-note movements; see the class
     * doc for the heuristic.</p>
     */
    public static Pedaling generate(Performance performance, TimeSignature ts) {
        if (performance == null || ts == null) return Pedaling.empty();
        if (performance.score().tracks().isEmpty()) return Pedaling.empty();

        double quartersPerBar = ts.beats() * 4.0 / ts.beatValue();
        if (quartersPerBar <= 0) return Pedaling.empty();

        long totalMs = computeTotalMs(performance);
        if (totalMs <= 0) return Pedaling.empty();

        TempoTrack tempos = performance.tempo();
        List<Long> barBoundaries = computeBarBoundariesMs(quartersPerBar, totalMs, tempos);
        List<Long> bassChanges   = findBassChangeOnsets(performance);

        List<PedalChange> changes = mergeIntoTimeline(barBoundaries, bassChanges, totalMs);
        PedalControl control = new PedalControl(changes);

        // Apply to every PITCHED track. Drum tracks never pedal.
        Map<TrackId, PedalControl> map = new LinkedHashMap<>();
        for (Track track : performance.score().tracks()) {
            if (track.kind() == TrackKind.PITCHED) {
                map.put(track.id(), control);
            }
        }
        return map.isEmpty() ? Pedaling.empty() : new Pedaling(map);
    }

    /**
     * Backwards-compat wrapper: ignores the {@code referenceBpm}
     * argument and reads tempo from {@code performance.tempo()} instead.
     *
     * @deprecated prefer {@link #generate(Performance, TimeSignature)}
     */
    @Deprecated
    public static Pedaling generate(Performance performance,
                                     TimeSignature ts,
                                     int ignoredReferenceBpm) {
        return generate(performance, ts);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** Bar-boundary ms positions strictly inside (0, totalMs). */
    private static List<Long> computeBarBoundariesMs(double quartersPerBar,
                                                      long totalMs,
                                                      TempoTrack tempos) {
        List<Long> out = new ArrayList<>();
        for (int bar = 1; ; bar++) {
            double boundaryQuarters = bar * quartersPerBar;
            long boundaryMs = TempoConversion.quartersToMs(tempos, boundaryQuarters);
            if (boundaryMs >= totalMs) break;
            out.add(boundaryMs);
        }
        return out;
    }

    /**
     * Walk pitched onsets in time order, group near-coincident onsets
     * into chords, and emit each onset where the chord's bass pitch
     * differs from the previous chord's bass pitch. Only notes below
     * {@link #BASS_PITCH_THRESHOLD} are considered "bass" — treble-only
     * passages return an empty list.
     */
    private static List<Long> findBassChangeOnsets(Performance performance) {
        record Onset(long tickMs, int midi) {}
        List<Onset> bassOnsets = new ArrayList<>();
        for (Track t : performance.score().tracks()) {
            if (t.kind() != TrackKind.PITCHED) continue;
            for (ConcreteNote n : t.notes()) {
                if (n instanceof PitchedNote pn && pn.midi() < BASS_PITCH_THRESHOLD) {
                    bassOnsets.add(new Onset(pn.tickMs(), pn.midi()));
                }
            }
        }
        if (bassOnsets.isEmpty()) return List.of();
        bassOnsets.sort(Comparator.comparingLong(Onset::tickMs));

        List<Long> bassChanges = new ArrayList<>();
        long groupStart = bassOnsets.get(0).tickMs();
        int  groupBass  = bassOnsets.get(0).midi();
        int  prevBass   = Integer.MIN_VALUE;
        for (int i = 1; i < bassOnsets.size(); i++) {
            Onset o = bassOnsets.get(i);
            if (o.tickMs() - groupStart <= CHORD_GROUP_TOL_MS) {
                if (o.midi() < groupBass) groupBass = o.midi();
            } else {
                if (prevBass != Integer.MIN_VALUE && groupBass != prevBass) {
                    bassChanges.add(groupStart);
                }
                prevBass   = groupBass;
                groupStart = o.tickMs();
                groupBass  = o.midi();
            }
        }
        // Close final group.
        if (prevBass != Integer.MIN_VALUE && groupBass != prevBass) {
            bassChanges.add(groupStart);
        }
        return bassChanges;
    }

    /**
     * Combine bar boundaries (always emitted) and bass changes (debounced
     * against existing changes by {@link #MIN_GAP_MS}) into a single
     * timeline bracketed by DOWN @ 0 and UP @ totalMs.
     */
    private static List<PedalChange> mergeIntoTimeline(List<Long> barBoundaries,
                                                       List<Long> bassChanges,
                                                       long totalMs) {
        List<PedalChange> changes = new ArrayList<>();
        changes.add(new PedalChange(0, PedalState.DOWN));

        // Bar boundaries are bedrock — emit them all (sorted ascending).
        TreeSet<Long> emittedMs = new TreeSet<>();
        emittedMs.add(0L);
        for (long boundaryMs : barBoundaries) {
            changes.add(new PedalChange(boundaryMs, PedalState.CHANGE));
            emittedMs.add(boundaryMs);
        }

        // Insert bass changes that don't crowd an existing change. Walk
        // in tick order so inter-bass debouncing is consistent.
        List<Long> sortedBass = new ArrayList<>(bassChanges);
        sortedBass.sort(Long::compare);
        for (long bassMs : sortedBass) {
            if (bassMs <= 0 || bassMs >= totalMs) continue;
            Long lower = emittedMs.floor(bassMs);
            Long upper = emittedMs.ceiling(bassMs);
            boolean tooClose =
                    (lower != null && bassMs - lower < MIN_GAP_MS) ||
                    (upper != null && upper - bassMs < MIN_GAP_MS);
            if (tooClose) continue;
            changes.add(new PedalChange(bassMs, PedalState.CHANGE));
            emittedMs.add(bassMs);
        }

        // Trailing release at end-of-music.
        changes.add(new PedalChange(totalMs, PedalState.UP));
        return changes;
    }

    /** Latest note-end across all tracks — defines the piece's duration in ms. */
    private static long computeTotalMs(Performance performance) {
        long max = 0;
        for (Track t : performance.score().tracks()) {
            for (ConcreteNote n : t.notes()) {
                long end = n.tickMs() + n.durationMs();
                if (end > max) max = end;
            }
        }
        return max;
    }
}
