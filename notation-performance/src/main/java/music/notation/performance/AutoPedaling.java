package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.expressivity.*;

import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *       (release-and-re-press), in <em>musical</em> position via
 *       direct {@link Duration} arithmetic.</li>
 *   <li>Additional {@link PedalState#CHANGE CHANGE} at any
 *       <b>mid-bar bass-note movement</b> — when the lowest sounding
 *       pitch (restricted to the bass register, MIDI &lt; 60) changes
 *       from one onset to the next. This mirrors how pianists pedal:
 *       the bass note is what binds harmony, so each new bass = new
 *       chord = new pedal.</li>
 *   <li>{@link PedalState#UP UP} clamped to the last note's tail.</li>
 * </ol>
 *
 * <p>Bass changes within {@link #MIN_GAP} of an existing bar boundary
 * or another bass change are dropped to avoid over-pedaling (chromatic
 * walks, ornaments, ghost-bass passages). The pitch threshold prevents
 * treble-only melodic motion from triggering CHANGEs — those pieces
 * fall back to plain bar-only auto-pedal.</p>
 *
 * <p>Post-ms→Duration migration the heuristic is purely musical:
 * bar boundaries, debouncing windows, and chord-group tolerances are
 * all rational Durations. Tempo never enters the math; editing the
 * TempoTrack doesn't shift any pedal event's musical anchor.</p>
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
     * Minimum musical distance between two consecutive emitted CHANGEs.
     * Suppresses over-pedaling on chromatic bass walks, grace notes,
     * and bass onsets that land near a bar boundary. One 16th note —
     * matches the old 200 ms threshold at 120 bpm but is now
     * tempo-independent: a sweep that's a 16th apart musically gets
     * collapsed regardless of bpm.
     */
    static final Duration MIN_GAP = Duration.of(1, 16);

    /**
     * Onsets within this musical tolerance are treated as the same
     * chord for bass-detection purposes. One 128th note — covers the
     * slight skew between an MXL left-hand and right-hand part, or a
     * notated chord spread across a tiny humanisation interval. The
     * old 25 ms ms-tolerance equates to roughly a 192nd at 120 bpm;
     * 1/128 is musically the closest standard subdivision.
     */
    static final Duration CHORD_GROUP_TOL = Duration.of(1, 128);

    private AutoPedaling() {}

    /**
     * Generate a bass-aware auto-pedaling. Returns {@link Pedaling#empty()}
     * when the performance is empty, when no pitched tracks are present,
     * or when the time signature is null.
     *
     * <p>Bar boundaries derive from the time signature alone — pure
     * musical arithmetic, no tempo involved. Mid-bar CHANGEs are added
     * at bass-note movements; see the class doc for the heuristic.</p>
     */
    public static Pedaling generate(Performance performance, TimeSignature ts) {
        if (performance == null || ts == null) return Pedaling.empty();
        if (performance.score().tracks().isEmpty()) return Pedaling.empty();

        // One bar in whole-note fractions: beats × (1/beatValue).
        // e.g. 4/4 → 4 × 1/4 = 1 (whole), 3/4 → 3 × 1/4 = 3/4, 6/8 → 6/8.
        Duration barDuration = Duration.of(ts.beats(), ts.beatValue());
        if (barDuration.isZero()) return Pedaling.empty();

        Duration total = computeTotal(performance);
        if (total.isZero()) return Pedaling.empty();

        List<Duration> barBoundaries = computeBarBoundaries(barDuration, total);
        List<Duration> bassChanges   = findBassChangeOnsets(performance);

        List<PedalChange> changes = mergeIntoTimeline(barBoundaries, bassChanges, total);
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

    /**
     * Pure transform: returns the input {@link Performance} with an
     * auto-generated {@link Pedaling} merged into its {@code pedaling()}
     * side-channel, filtered to sustain-receptive instruments only.
     *
     * <p>If the performance already declares any pedaling, it's
     * returned unchanged — user-authored pedaling always wins. Tracks
     * absent from {@link Instrumentation} default to program 0
     * (piano), so legacy paths that don't populate the side-channel
     * continue to get pedal as before.</p>
     */
    public static Performance augment(Performance perf, TimeSignature ts) {
        if (perf == null) return null;
        if (!perf.pedaling().byTrack().isEmpty()) {
            return perf;   // user-authored pedaling wins
        }
        Pedaling auto = generate(perf, ts);
        if (auto.byTrack().isEmpty()) return perf;
        Pedaling filtered = filterToSustainInstruments(auto, perf.instruments());
        if (filtered.byTrack().isEmpty()) return perf;
        return perf.withPedaling(filtered);
    }

    /** GM program numbers (0-indexed) where a sustain pedal makes musical sense. */
    static final Set<Integer> SUSTAIN_FRIENDLY = Set.of(
            0, 1, 2, 3, 4, 5, 6, 7,        // pianos / harpsichord / e-piano / clavinet
            8, 9, 10, 11,                  // chromatic perc — celesta, glock, music box, vibes
            16, 17, 18, 19, 20, 21         // organs
    );

    private static Pedaling filterToSustainInstruments(
            Pedaling auto, Instrumentation instruments) {
        Map<TrackId, PedalControl> keep = new LinkedHashMap<>();
        for (Map.Entry<TrackId, PedalControl> e : auto.byTrack().entrySet()) {
            int program = primaryProgramOf(e.getKey(), instruments);
            if (SUSTAIN_FRIENDLY.contains(program)) {
                keep.put(e.getKey(), e.getValue());
            }
        }
        return keep.isEmpty() ? Pedaling.empty() : new Pedaling(keep);
    }

    /**
     * The track's first declared {@link InstrumentChange#program()},
     * or 0 (piano) if none is declared. Mid-piece program changes are
     * ignored for the purpose of pedal-eligibility — once a track gets
     * pedal, it stays on pedal.
     */
    private static int primaryProgramOf(TrackId id, Instrumentation instruments) {
        InstrumentControl ic = instruments.byTrack().get(id);
        if (ic == null || ic.changes().isEmpty()) return 0;
        return ic.changes().get(0).program();
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** Bar-boundary musical positions strictly inside (0, total). */
    private static List<Duration> computeBarBoundaries(Duration barDuration, Duration total) {
        List<Duration> out = new ArrayList<>();
        for (long bar = 1; ; bar++) {
            Duration boundary = barDuration.times(bar);
            if (boundary.compareDuration(total) >= 0) break;
            out.add(boundary);
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
    private static List<Duration> findBassChangeOnsets(Performance performance) {
        record Onset(Duration at, int midi) {}
        List<Onset> bassOnsets = new ArrayList<>();
        for (Track t : performance.score().tracks()) {
            if (t.kind() != TrackKind.PITCHED) continue;
            for (ConcreteNote n : t.notes()) {
                if (n instanceof PitchedNote pn && pn.midi() < BASS_PITCH_THRESHOLD) {
                    bassOnsets.add(new Onset(pn.at(), pn.midi()));
                }
            }
        }
        if (bassOnsets.isEmpty()) return List.of();
        bassOnsets.sort(Comparator.comparing(Onset::at, (a, b) -> a.compareDuration(b)));

        List<Duration> bassChanges = new ArrayList<>();
        Duration groupStart = bassOnsets.get(0).at();
        int  groupBass  = bassOnsets.get(0).midi();
        int  prevBass   = Integer.MIN_VALUE;
        for (int i = 1; i < bassOnsets.size(); i++) {
            Onset o = bassOnsets.get(i);
            if (o.at().minus(groupStart).compareDuration(CHORD_GROUP_TOL) <= 0) {
                if (o.midi() < groupBass) groupBass = o.midi();
            } else {
                if (prevBass != Integer.MIN_VALUE && groupBass != prevBass) {
                    bassChanges.add(groupStart);
                }
                prevBass   = groupBass;
                groupStart = o.at();
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
     * Combine bar boundaries (always emitted) and bass changes
     * (debounced against existing changes by {@link #MIN_GAP}) into a
     * single timeline bracketed by DOWN @ 0 and UP @ total.
     */
    private static List<PedalChange> mergeIntoTimeline(List<Duration> barBoundaries,
                                                       List<Duration> bassChanges,
                                                       Duration total) {
        List<PedalChange> changes = new ArrayList<>();
        changes.add(new PedalChange(Duration.zero(), PedalState.DOWN));

        // Bar boundaries are bedrock — emit them all (sorted ascending).
        // TreeSet uses our value-based Duration comparator so 1/4 and
        // 2/8 collapse to the same key.
        Comparator<Duration> byValue = (a, b) -> a.compareDuration(b);
        TreeSet<Duration> emitted = new TreeSet<>(byValue);
        emitted.add(Duration.zero());
        for (Duration boundary : barBoundaries) {
            changes.add(new PedalChange(boundary, PedalState.CHANGE));
            emitted.add(boundary);
        }

        // Insert bass changes that don't crowd an existing change. Walk
        // in position order so inter-bass debouncing is consistent.
        List<Duration> sortedBass = new ArrayList<>(bassChanges);
        sortedBass.sort(byValue);
        for (Duration bass : sortedBass) {
            if (bass.isZero() || bass.compareDuration(total) >= 0) continue;
            Duration lower = emitted.floor(bass);
            Duration upper = emitted.ceiling(bass);
            boolean tooClose =
                    (lower != null && bass.minus(lower).compareDuration(MIN_GAP) < 0) ||
                    (upper != null && upper.minus(bass).compareDuration(MIN_GAP) < 0);
            if (tooClose) continue;
            changes.add(new PedalChange(bass, PedalState.CHANGE));
            emitted.add(bass);
        }

        // Trailing release at end-of-music.
        changes.add(new PedalChange(total, PedalState.UP));
        return changes;
    }

    /** Latest note-end across all tracks — defines the piece's musical length. */
    private static Duration computeTotal(Performance performance) {
        Duration max = Duration.zero();
        for (Track t : performance.score().tracks()) {
            for (ConcreteNote n : t.notes()) {
                Duration end = n.endAt();
                if (end.compareDuration(max) > 0) max = end;
            }
        }
        return max;
    }
}
