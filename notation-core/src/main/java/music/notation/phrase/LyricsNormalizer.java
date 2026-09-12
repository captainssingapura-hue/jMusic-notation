package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.duration.RawDuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pairs a raw lyrics phrase against a melodic phrase to produce a third
 * <b>normalized</b> lyrics sequence whose nodes line up one-to-one with the
 * melody's audible onsets.
 *
 * <h2>Why</h2>
 * Authors write lyrics in <b>syllable units</b> — one glyph per sung
 * syllable, with that glyph's duration covering the whole syllable's
 * slot regardless of how many melodic notes share it. Engravers and
 * exporters, in contrast, need <b>per-note</b> lyric attribution:
 * each notehead either carries a glyph, the continuation marker
 * {@link LyricNode#CONTINUATION_CODE_POINT '_'}, or nothing (rest).
 *
 * <p>This class is the bridge. Given:</p>
 * <ul>
 *   <li>A raw lyrics phrase — {@link LyricNode}s (one per syllable) and
 *       optional {@link RestNode}s.</li>
 *   <li>A melodic phrase — any {@link PhraseNode}s; {@link PitchNode} /
 *       {@link PercussionNote} count as audible onsets, {@link RestNode}
 *       / {@link PaddingNode} as silences. Zero-duration markers
 *       ({@link DynamicNode}, tempo events) are skipped.</li>
 * </ul>
 *
 * <p>It walks both timelines on a shared 64ths cursor and emits, for each
 * melody node in order:</p>
 * <ul>
 *   <li>An audible note whose onset falls inside the <i>n</i>th raw-syllable
 *       slot → the first such note for that slot gets the syllable glyph;
 *       subsequent notes get {@code '_'} (continuation).</li>
 *   <li>An audible note that lands during a raw <em>rest</em> (or past the
 *       end of the raw lyrics) → a {@link RestNode} of matching duration.</li>
 *   <li>A melody rest → a {@link RestNode} of matching duration, regardless
 *       of what the raw lyrics show at that point.</li>
 * </ul>
 *
 * <p>The output is therefore <b>melody-shaped</b>: same node count, same
 * durations, same onset structure — only the contents differ. This makes
 * it directly usable as an aux phrase aligned to the melody, or as a
 * source for per-note exporters (MusicXML {@code <lyric>}, MIDI lyric
 * meta events, on-screen captions).</p>
 *
 * <h2>Edge cases</h2>
 * <ul>
 *   <li><b>Raw shorter than melody</b> — any melody nodes past the raw
 *       lyrics' total duration emit {@code RestNode}s. Nothing is invented.</li>
 *   <li><b>Raw longer than melody</b> — trailing raw syllables that have
 *       no melody onset to anchor to are silently dropped. They had no
 *       place to render anyway.</li>
 *   <li><b>Raw syllable spans no audible onset</b> (e.g. lands entirely
 *       inside a melody rest) — that syllable is dropped. There's no
 *       notehead to attach it to. Callers needing to detect this can
 *       diff the raw glyph count against the output's non-continuation
 *       LyricNode count.</li>
 * </ul>
 */
public final class LyricsNormalizer {

    private LyricsNormalizer() {}

    /**
     * Normalize {@code rawLyrics} against {@code melody}; see the class
     * javadoc for the algorithm. The result is a flat list of
     * {@link LyricNode} and {@link RestNode}s whose total duration
     * equals the sum of {@code melody}'s node durations.
     *
     * @param rawLyrics syllable-aligned lyrics ({@link LyricNode}s and
     *                  optional {@link RestNode}s only — other node types
     *                  trigger {@link IllegalArgumentException})
     * @param melody    any phrase nodes; sounded onsets drive output
     */
    public static List<PhraseNode> normalize(List<PhraseNode> rawLyrics, List<PhraseNode> melody) {
        Objects.requireNonNull(rawLyrics, "rawLyrics");
        Objects.requireNonNull(melody, "melody");

        // Pre-flatten the raw lyrics into a tick-indexed run: each entry has
        // a [start64, end64) span in 64ths and the glyph (null for a rest /
        // gap). Walking this is O(N+M) when paired with the melody walk.
        List<RawSpan> spans = flattenRawLyrics(rawLyrics);

        List<PhraseNode> out = new ArrayList<>(melody.size());
        int cursor64 = 0;
        int spanIdx = 0;
        int glyphsEmittedInCurrentSpan = 0;

        for (PhraseNode node : melody) {
            int dur64 = Bar.nodeSixtyFourths(node);
            // Zero-duration markers pass through untouched — they don't
            // consume a lyric slot and don't advance the cursor.
            if (dur64 == 0) {
                continue;
            }

            // Advance through any raw spans that ended at or before the cursor.
            // Spans we leave behind reset the "first-glyph-of-span" counter so
            // the next span starts fresh.
            while (spanIdx < spans.size() && spans.get(spanIdx).end64 <= cursor64) {
                spanIdx++;
                glyphsEmittedInCurrentSpan = 0;
            }

            boolean audible = isAudibleOnset(node);
            if (!audible) {
                // Melody silence (RestNode, PaddingNode) — always passes
                // through as a rest of matching duration.
                out.add(new RestNode(rawFromSixtyFourths(dur64)));
                cursor64 += dur64;
                continue;
            }

            // Audible onset. Decide which (if any) raw span it sits inside.
            RawSpan span = (spanIdx < spans.size()) ? spans.get(spanIdx) : null;
            boolean inActiveSyllable = span != null
                    && span.codePoint != null
                    && cursor64 >= span.start64
                    && cursor64 <  span.end64;

            if (!inActiveSyllable) {
                // No glyph to attribute — past end of raw, or aligned with a
                // raw rest. Emit a placeholder rest of the melody note's
                // duration so the output stays melody-shaped.
                out.add(new RestNode(rawFromSixtyFourths(dur64)));
            } else if (glyphsEmittedInCurrentSpan == 0) {
                // First audible onset inside this syllable → carries the glyph.
                out.add(new LyricNode(span.codePoint, rawFromSixtyFourths(dur64)));
                glyphsEmittedInCurrentSpan = 1;
            } else {
                // Subsequent onset(s) inside the same syllable → continuation.
                out.add(LyricNode.continuation(rawFromSixtyFourths(dur64)));
                glyphsEmittedInCurrentSpan++;
            }
            cursor64 += dur64;
        }

        return List.copyOf(out);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** Audible onsets are pitched notes and percussion. Everything else is silent / structural. */
    private static boolean isAudibleOnset(PhraseNode n) {
        return n instanceof PitchNode || n instanceof PercussionNote;
    }

    /** Walk the raw lyrics into a flat list of (start64, end64, codePoint?) runs. */
    private static List<RawSpan> flattenRawLyrics(List<PhraseNode> rawLyrics) {
        List<RawSpan> spans = new ArrayList<>(rawLyrics.size());
        int t64 = 0;
        for (PhraseNode n : rawLyrics) {
            int dur64 = Bar.nodeSixtyFourths(n);
            if (dur64 == 0) continue;
            Integer codePoint = switch (n) {
                case LyricNode ln -> ln.codePoint();
                case RestNode r  -> null;
                case PaddingNode p -> null;
                default -> throw new IllegalArgumentException(
                        "rawLyrics may only contain LyricNode / RestNode / PaddingNode; got "
                                + n.getClass().getSimpleName());
            };
            spans.add(new RawSpan(t64, t64 + dur64, codePoint));
            t64 += dur64;
        }
        return spans;
    }

    /**
     * Build a {@link Duration} from a 64ths count. We use {@link RawDuration}
     * with a denominator of 64 — exact and round-trip-safe for any value
     * the existing {@link Bar} helpers produced.
     */
    private static Duration rawFromSixtyFourths(int sixtyFourths) {
        return new RawDuration(sixtyFourths, 64);
    }

    /** A flattened raw-lyrics span. {@code codePoint == null} means "rest / no glyph". */
    private record RawSpan(int start64, int end64, Integer codePoint) {}
}
