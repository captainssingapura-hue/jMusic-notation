package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A melodic phrase — a sequence of {@link PhraseNode}s optionally structured
 * into {@link Bar}s, optionally carrying parallel {@link VoiceOverlay}s.
 *
 * <p><b>Voices.</b> A {@code MelodicPhrase} may carry one or more
 * {@link VoiceOverlay}s — bar-aligned parallel voices sharing the main line's
 * instrument and timeline. Each voice must declare exactly as many bar slots
 * as the main phrase has bars, and every non-empty override bar must match
 * its counterpart's {@code expectedSixtyFourths}. Voices are the
 * replacement for the legacy {@code AuxBar} side channel.</p>
 */
public record MelodicPhrase(
        List<PhraseNode> nodes,
        List<Bar> bars,
        PhraseMarking marking,
        List<VoiceOverlay> voices
) implements Phrase {

    public MelodicPhrase {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("MelodicPhrase must contain at least one node");
        }
        nodes = List.copyOf(nodes);
        bars = List.copyOf(bars);
        voices = List.copyOf(voices);
        validateVoices(bars, voices);
    }

    /** Backwards-compatible constructor for phrases built without bar structure. */
    public MelodicPhrase(List<PhraseNode> nodes, PhraseMarking marking) {
        this(nodes, List.of(), marking, List.of());
    }

    /** Backwards-compatible constructor: nodes + bars + marking, no voices. */
    public MelodicPhrase(List<PhraseNode> nodes, List<Bar> bars, PhraseMarking marking) {
        this(nodes, bars, marking, List.of());
    }

    private static void validateVoices(List<Bar> bars, List<VoiceOverlay> voices) {
        if (voices.isEmpty()) return;
        if (bars.isEmpty()) {
            throw new IllegalArgumentException(
                    "MelodicPhrase with voices must have a bar structure (bars is empty)");
        }
        for (int v = 0; v < voices.size(); v++) {
            VoiceOverlay overlay = voices.get(v);
            if (overlay.size() != bars.size()) {
                throw new IllegalArgumentException(
                        "Voice " + v + ": overlay has " + overlay.size()
                                + " bar slots but main phrase has " + bars.size());
            }
            for (int i = 0; i < bars.size(); i++) {
                Optional<Bar> maybe = overlay.at(i);
                if (maybe.isEmpty()) continue;
                int actual = maybe.get().expectedSixtyFourths();
                int expected = bars.get(i).expectedSixtyFourths();
                if (actual != expected) {
                    throw new IllegalArgumentException(
                            "Voice " + v + ", bar " + i + ": overlay bar is "
                                    + actual + "/64 but main bar is " + expected + "/64");
                }
            }
        }
    }

    /**
     * Build a phrase from validated bars. Each {@link Bar} has already verified
     * its own duration at construction time. This method additionally checks that
     * middle bars match the time signature (first/last may be partial pickups).
     *
     * <p>After flattening bars into a node list, slur regions are resolved:
     * <ul>
     *   <li><b>Same-pitch tie</b> — consecutive same-pitch notes bridged by
     *       {@link SlurStart}/{@link SlurEnd} are merged into a single
     *       {@link NoteNode} with combined duration.</li>
     *   <li><b>Different-pitch slur</b> — the markers are preserved so the
     *       playback layer can apply legato overlap.</li>
     * </ul>
     *
     * <p>Any {@link AuxBar}s attached to the input bars are collected into
     * parallel {@link VoiceOverlay}s on the result. Silence at bar {@code i}
     * of voice {@code v} is expressed as {@code Optional.empty()} — no
     * rest-padding synthesis.</p>
     */
    public static MelodicPhrase fromBars(TimeSignature ts, PhraseMarking marking, Bar... bars) {
        int expected = ts.barSixtyFourths();
        for (int i = 0; i < bars.length; i++) {
            int actual = bars[i].expectedSixtyFourths();
            if (actual != expected) {
                boolean isFirstOrLast = (i == 0 || i == bars.length - 1);
                if (isFirstOrLast && actual < expected) {
                    continue;
                }
                throw new IllegalArgumentException(
                        "Bar " + (i + 1) + "/" + bars.length + ": expected " + expected
                                + " sixty-fourths (" + ts.beats() + "/" + ts.beatValue()
                                + ") but got " + actual);
            }
        }
        var flat = new ArrayList<PhraseNode>();
        for (Bar bar : bars) {
            flat.addAll(bar.nodes());
        }
        // Two-stage resolution:
        //   1. resolveTies — merge consecutive NoteNodes flagged by .tieNext()
        //      (preferred for same-pitch ties; chains naturally).
        //   2. resolveSlurs — legacy same-pitch merge via SlurStart/SlurEnd;
        //      also preserves different-pitch legato markers.
        List<PhraseNode> resolved = resolveSlurs(resolveTies(flat));
        List<VoiceOverlay> voices = collectVoices(bars);
        return new MelodicPhrase(resolved, List.of(bars), marking, voices);
    }

    /**
     * Column-major gather of {@link AuxBar}s across all bars: for voice index
     * {@code v}, produce {@code List<Optional<Bar>>} where entry {@code i} is
     * {@code Optional.of(wrappedAuxBar)} if bar {@code i} has an aux at slot
     * {@code v}, else {@code Optional.empty()}.
     */
    private static List<VoiceOverlay> collectVoices(Bar[] bars) {
        int maxVoices = 0;
        for (Bar bar : bars) {
            maxVoices = Math.max(maxVoices, bar.auxBars().size());
        }
        if (maxVoices == 0) return List.of();

        var result = new ArrayList<VoiceOverlay>(maxVoices);
        for (int v = 0; v < maxVoices; v++) {
            var overlayBars = new ArrayList<Optional<Bar>>(bars.length);
            for (Bar main : bars) {
                List<AuxBar> aux = main.auxBars();
                if (v < aux.size()) {
                    // Wrap the aux nodes into a properly-sized Bar matching main.
                    overlayBars.add(Optional.of(padAuxToBar(aux.get(v), main.expectedSixtyFourths())));
                } else {
                    overlayBars.add(Optional.empty());
                }
            }
            result.add(new VoiceOverlay(overlayBars));
        }
        return result;
    }

    /**
     * Wrap an {@link AuxBar}'s nodes into a {@link Bar} matching the main bar's
     * size. If the aux content is shorter than the main bar, trailing rest
     * is appended so the overlay bar's duration exactly matches.
     */
    private static Bar padAuxToBar(AuxBar aux, int expectedSixtyFourths) {
        int total = 0;
        for (PhraseNode n : aux.nodes()) {
            total += Bar.nodeSixtyFourths(n);
        }
        if (total == expectedSixtyFourths) {
            return new Bar(expectedSixtyFourths, aux.nodes(), List.of());
        }
        if (total > expectedSixtyFourths) {
            throw new IllegalArgumentException(
                    "Aux voice content totals " + total + "/64 but bar is only "
                            + expectedSixtyFourths + "/64");
        }
        var padded = new ArrayList<>(aux.nodes());
        padded.add(new RestNode(Duration.ofSixtyFourths(expectedSixtyFourths - total)));
        return new Bar(expectedSixtyFourths, padded, List.of());
    }

    // ── Tie resolution (melody-level) ──────────────────────────────────────

    /**
     * Merge {@link NoteNode}s flagged with {@code tiedToNext} into their
     * successor same-pitch {@link NoteNode}. Zero-duration markers between
     * the tied pair (dynamics, legacy slur markers, tempo markers) are
     * preserved in place and don't break the tie.
     *
     * <p>Ties chain: after merging a pair, the merged node carries the
     * second note's tie flag, so a sequence of three (each flagged) collapses
     * into one in a single left-to-right pass.</p>
     *
     * <p>Throws {@link IllegalStateException} if a tied note has no same-pitch
     * successor (different pitches, non-note follow-up, or end of phrase).</p>
     */
    static List<PhraseNode> resolveTies(List<PhraseNode> nodes) {
        var result = new ArrayList<PhraseNode>(nodes.size());
        int i = 0;
        while (i < nodes.size()) {
            if (!(nodes.get(i) instanceof NoteNode first) || !first.hasTie()) {
                result.add(nodes.get(i));
                i++;
                continue;
            }
            // first is tied — walk forward, absorbing same-pitch notes until the chain ends.
            int combined = first.duration().sixtyFourths();
            var interleavedMarkers = new ArrayList<PhraseNode>();
            int scan = i + 1;
            boolean stillTied = true;
            while (stillTied) {
                // Skip zero-duration markers between tied notes.
                while (scan < nodes.size() && isZeroDurationMarker(nodes.get(scan))) {
                    interleavedMarkers.add(nodes.get(scan));
                    scan++;
                }
                if (scan >= nodes.size()) {
                    throw new IllegalStateException(
                            "tieNext() at phrase index " + i
                                    + " has no following note to tie to (pitches " + first.pitches() + ")");
                }
                if (!(nodes.get(scan) instanceof NoteNode next)) {
                    throw new IllegalStateException(
                            "tieNext() at phrase index " + i
                                    + " requires a following note, but found "
                                    + nodes.get(scan).getClass().getSimpleName());
                }
                if (!first.pitches().equals(next.pitches())) {
                    throw new IllegalStateException(
                            "tieNext() pitch mismatch at phrase index " + i
                                    + ": previous " + first.pitches() + " ≠ next " + next.pitches());
                }
                combined += next.duration().sixtyFourths();
                scan++;
                stillTied = next.hasTie();
            }
            var merged = new NoteNode(
                    first.pitches(),
                    Duration.ofSixtyFourths(combined),
                    first.articulations(),
                    first.ornament(),
                    first.graceNotes(),
                    first.equalDivision(),
                    false // chain terminated
            );
            result.add(merged);
            result.addAll(interleavedMarkers);
            i = scan;
        }
        return result;
    }

    // ── Slur / tie resolution ──────────────────────────────────────────────

    /**
     * Walk the flat node list and resolve slur regions.
     *
     * <p>Pattern: {@code NoteNode(P,D1), SlurStart, NoteNode(P,D2), SlurEnd}
     * with the same pitch P → merged into {@code NoteNode(P, D1+D2)}.
     * The merge chains: if the merged note is followed by another
     * SlurStart+same-pitch, it keeps merging.</p>
     *
     * <p>If the pitches differ, the SlurStart/SlurEnd markers are kept for
     * the playback layer to interpret as legato.</p>
     */
    static List<PhraseNode> resolveSlurs(List<PhraseNode> nodes) {
        var result = new ArrayList<PhraseNode>(nodes.size());

        for (int i = 0; i < nodes.size(); ) {
            // Look for pattern: NoteNode, SlurStart, ..., NoteNode, SlurEnd
            if (i + 3 < nodes.size()
                    && nodes.get(i) instanceof NoteNode before
                    && nodes.get(i + 1) instanceof SlurStart) {

                // Skip zero-duration markers between SlurStart and the next NoteNode
                int j = i + 2;
                while (j < nodes.size() && isZeroDurationMarker(nodes.get(j))
                        && !(nodes.get(j) instanceof SlurEnd)) {
                    j++;
                }

                if (j < nodes.size() && nodes.get(j) instanceof NoteNode after
                        && j + 1 < nodes.size() && nodes.get(j + 1) instanceof SlurEnd
                        && before.pitches().equals(after.pitches())) {
                    // Same pitch(es) — merge into one sustained note
                    int combined = before.duration().sixtyFourths()
                            + after.duration().sixtyFourths();
                    var merged = NoteNode.poly(
                            Duration.ofSixtyFourths(combined), before.pitches());
                    // Replace "before" with merged, skip SlurStart..SlurEnd
                    result.add(merged);
                    i = j + 2; // past SlurEnd

                    // Chain: if another SlurStart+same-pitch follows, keep merging
                    while (i + 2 < nodes.size()
                            && nodes.get(i) instanceof SlurStart
                            && nodes.get(i + 1) instanceof NoteNode next
                            && nodes.get(i + 2) instanceof SlurEnd
                            && next.pitches().equals(merged.pitches())) {
                        int chainCombined = merged.duration().sixtyFourths()
                                + next.duration().sixtyFourths();
                        merged = NoteNode.poly(
                                Duration.ofSixtyFourths(chainCombined), merged.pitches());
                        result.set(result.size() - 1, merged);
                        i += 3;
                    }
                    continue;
                }
            }
            // No merge — emit node as-is
            result.add(nodes.get(i));
            i++;
        }
        return result;
    }

    private static boolean isZeroDurationMarker(PhraseNode node) {
        return node instanceof DynamicNode
                || node instanceof SlurStart
                || node instanceof SlurEnd;
    }
}
