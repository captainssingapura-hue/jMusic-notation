package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.structure.MelodicTrack;
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

    /**
     * Phase 4b adapter: convert this phrase into a {@link MelodicTrack}.
     *
     * <p><b>Lossy migration.</b> Voice overlays and the phrase marking
     * (BREATH/CAESURA/ATTACCA/ELISION boundary connection) are dropped
     * — they have no representation on {@code MelodicTrack} yet. Bars
     * survive verbatim. For phrases built without bar structure (the
     * backwards-compat constructor), all nodes are wrapped into a
     * single Bar sized to their total duration.</p>
     *
     * @param name the resulting track's name
     * @param instrument the resulting track's default instrument; must not be {@link Instrument#DRUM_KIT}
     */
    public MelodicTrack toMelodicTrack(String name, Instrument instrument) {
        List<Bar> outBars;
        if (!bars.isEmpty()) {
            outBars = bars;
        } else {
            int total = 0;
            for (PhraseNode n : nodes) {
                total += Bar.nodeSixtyFourths(n);
            }
            outBars = List.of(new Bar(total, nodes, List.of()));
        }
        return new MelodicTrack(name, instrument, outBars, List.of());
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
        // Tie-merge and slur-resolution have both been removed:
        //  - Notes flagged with `.tieNext()` are preserved as two distinct
        //    PitchNodes carrying the {@link Tieable#tiedToNext()} flag; the
        //    renderer is expected to coalesce them at MIDI emission time.
        //  - SlurStart/SlurEnd nodes (and the slur builder methods that
        //    produced them) have been deleted entirely. Songs that used
        //    slurs for same-pitch ties or for legato extension will sound
        //    re-articulated / non-legato — accepted regression. See
        //    .docs/agent-delegation-retrospective.md.
        List<PhraseNode> resolved = flat;
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


}
