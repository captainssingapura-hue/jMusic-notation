package music.notation.phrase;

import java.util.List;

/**
 * Predicate + assertion utilities for "is this a monophonic phrase" —
 * a single melodic line with no chords and no simultaneously-sounding
 * notes. Used by the lyrics editor (and any future single-line tool) to
 * gate input: a phrase must be monophonic before a per-note lyric grid
 * makes sense, because every note position must be unambiguously
 * "the next sung note."
 *
 * <p>Definitions:</p>
 * <ul>
 *   <li>A {@link PolyPitchNode} (chord) is never monophonic.</li>
 *   <li>A {@link VoiceOverlay} attached to a phrase makes the parent
 *       phrase polyphonic — the overlay sounds in parallel.</li>
 *   <li>{@link GraceNote}s do not count against monophony — they are
 *       always single-pitch by construction and treated as part of the
 *       owning note's articulation, not as separate voices.</li>
 * </ul>
 *
 * <p>Notes that <i>could</i> overlap in time via tuplet timing
 * (mixed-rate sub-divisions in the same line) are not detected here;
 * concrete tick-overlap detection happens later in the pipeline. This
 * helper is a fast structural check on the authoring representation.</p>
 */
public final class Monophony {

    private Monophony() {}

    /**
     * True when {@code nodes} is a single-line melody — no chord nodes,
     * no other parallel voices. Rests, padding, dynamics, and tempo
     * markers are ignored (they don't sound a pitch).
     */
    public static boolean isMonophonic(List<PhraseNode> nodes) {
        if (nodes == null) return true;
        for (PhraseNode n : nodes) {
            if (n instanceof PolyPitchNode) return false;
        }
        return true;
    }

    /** True when the phrase carries no parallel voice overlays AND its nodes are monophonic. */
    public static boolean isMonophonic(MelodicPhrase phrase) {
        if (phrase == null) return true;
        if (!phrase.voices().isEmpty()) return false;
        return isMonophonic(phrase.nodes());
    }

    /**
     * Throw {@link IllegalArgumentException} with a descriptive message
     * if {@code nodes} is not monophonic. The message identifies the
     * first chord position so callers can point the user at it.
     */
    public static void assertMonophonic(List<PhraseNode> nodes) {
        if (nodes == null) return;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) instanceof PolyPitchNode p) {
                throw new IllegalArgumentException(
                        "phrase is not monophonic: node " + i + " is a chord ("
                                + p.pitches().size() + " pitches)");
            }
        }
    }

    /** {@link #assertMonophonic(List)} variant that also rejects parallel voice overlays. */
    public static void assertMonophonic(MelodicPhrase phrase) {
        if (phrase == null) return;
        if (!phrase.voices().isEmpty()) {
            throw new IllegalArgumentException(
                    "phrase is not monophonic: " + phrase.voices().size()
                            + " parallel voice overlay(s) attached");
        }
        assertMonophonic(phrase.nodes());
    }

    // ── Top-line extraction ──────────────────────────────────────────────

    /**
     * Onsets within this tolerance (ms) are treated as one chord for the
     * purpose of top-line extraction. Matches the convention used by
     * {@code AutoPedaling.CHORD_GROUP_TOL_MS} — a slight skew between
     * "simultaneous" notes is the rule, not the exception, after MXL or
     * audio-to-MIDI quantization.
     */
    public static final long CHORD_GROUP_TOL_MS = 25;

    /**
     * Result of {@link #extractTopLine}: the kept onsets (the melody)
     * plus a count of how many input notes were dropped as
     * accompaniment / inner voice / chord-bottom.
     */
    public record TopLine<N>(List<N> melody, int dropped) {
        public TopLine {
            melody = List.copyOf(melody);
        }
    }

    /**
     * Extract the top-line "melody" from a list of audible onsets that
     * may overlap. The algorithm proceeds in two passes:
     *
     * <ol>
     *   <li><b>Chord grouping</b> — consecutive onsets whose
     *       {@code tickMs} are within {@link #CHORD_GROUP_TOL_MS} of the
     *       group's first onset collapse into a single "chord event"
     *       represented by the highest-pitched note in the group.
     *       Catches MIDI quantization noise (e.g. a "5-voice chord"
     *       whose notes were recorded 1–10 ms apart) and notated chords
     *       on a single track.</li>
     *   <li><b>Top-line filter</b> — walk the grouped events in time
     *       order. Maintain the set of <em>currently sounding</em> notes
     *       (notes whose onset ≤ cursor and end &gt; cursor). An event
     *       is kept as a melody onset iff its pitch is ≥ the max pitch
     *       in the currently-sounding set. Lower-pitched onsets while a
     *       higher note is still ringing get dropped as inner voice /
     *       accompaniment.</li>
     * </ol>
     *
     * <p>Equality is on midi pitch only. The caller supplies a
     * {@code pitchOf} / {@code tickMsOf} / {@code endMsOf} accessor so
     * this works against any note-shaped record (the actual
     * {@code PitchedNote} type lives in {@code notation-performance},
     * which we don't depend on here).</p>
     *
     * <p><b>Use case</b> — vocal-to-MIDI converters frequently emit
     * overlapping notes from a single sung phrase ("double triggers").
     * The lyrics editor needs a single melody-onset stream against
     * which to lay out per-note glyphs; this method gives it one even
     * when the source track isn't strictly monophonic.</p>
     */
    public static <N> TopLine<N> extractTopLine(
            List<N> notes,
            java.util.function.ToIntFunction<N> pitchOf,
            java.util.function.ToLongFunction<N> tickMsOf,
            java.util.function.ToLongFunction<N> endMsOf) {
        if (notes == null || notes.isEmpty()) {
            return new TopLine<>(List.of(), 0);
        }
        List<N> sorted = new java.util.ArrayList<>(notes);
        sorted.sort(java.util.Comparator.comparingLong(tickMsOf));

        // Pass 1: chord-group by onset tolerance, keep highest pitch per group.
        List<N> grouped = new java.util.ArrayList<>();
        int i = 0;
        int chordDropped = 0;
        while (i < sorted.size()) {
            long groupStart = tickMsOf.applyAsLong(sorted.get(i));
            int j = i;
            N top = sorted.get(i);
            int topPitch = pitchOf.applyAsInt(top);
            while (j < sorted.size()
                    && tickMsOf.applyAsLong(sorted.get(j)) - groupStart <= CHORD_GROUP_TOL_MS) {
                N candidate = sorted.get(j);
                int candidatePitch = pitchOf.applyAsInt(candidate);
                if (candidatePitch > topPitch) {
                    top = candidate;
                    topPitch = candidatePitch;
                }
                j++;
            }
            grouped.add(top);
            chordDropped += (j - i - 1);
            i = j;
        }

        // Pass 2: top-line filter — drop events that land under a still-sounding higher note.
        List<N> melody = new java.util.ArrayList<>();
        // Active = sounding notes' (endMs, midi); small N → flat list fine.
        java.util.ArrayList<long[]> active = new java.util.ArrayList<>();
        int overlapDropped = 0;
        for (N n : grouped) {
            long t = tickMsOf.applyAsLong(n);
            // Purge notes whose end has passed.
            active.removeIf(a -> a[0] <= t);
            int currentTop = Integer.MIN_VALUE;
            for (long[] a : active) {
                if (a[1] > currentTop) currentTop = (int) a[1];
            }
            int pitch = pitchOf.applyAsInt(n);
            if (pitch >= currentTop) {
                melody.add(n);
                active.add(new long[] { endMsOf.applyAsLong(n), pitch });
            } else {
                overlapDropped++;
            }
        }

        return new TopLine<>(melody, chordDropped + overlapDropped);
    }
}
