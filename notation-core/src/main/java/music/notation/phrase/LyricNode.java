package music.notation.phrase;

import music.notation.duration.Duration;

import java.util.Objects;

/**
 * A single-glyph lyric event with a duration. Carries one Unicode code
 * point (so non-BMP characters — emoji, rare CJK — round-trip cleanly
 * through a primitive {@code int}) and the rhythmic duration that
 * glyph occupies.
 *
 * <p>Lyric nodes are siblings to {@link PitchNode} and {@link RestNode}
 * inside the {@link PhraseNode} sealed interface: a phrase or bar may
 * mix them freely, or a dedicated "lyrics" aux phrase may hold them
 * alone alongside a melody.</p>
 *
 * <p>Lyric nodes are intentionally <b>silent</b> in playback — the codec
 * advances the cursor by the node's duration but emits no MIDI events.
 * Rendering / engraving paths read the {@link #character()} for display.
 * Western multi-character syllables ("Twin-", "-kle") are modelled as a
 * sequence of single-codepoint LyricNodes whose durations sum to the
 * syllable's slot; one-glyph-one-syllable scripts (CJK, hiragana) map
 * naturally.</p>
 *
 * <p>Static factories mirror the {@link RestNode} surface so call sites
 * read the same way:</p>
 * <pre>{@code
 *   LyricNode.of('a', QUARTER)
 *   LyricNode.of("一", QUARTER)    // single CJK glyph from a String
 *   LyricNode.of(0x1F3B5, WHOLE)   // 🎵 (musical note) — non-BMP code point
 * }</pre>
 */
public record LyricNode(int codePoint, Duration duration) implements PhraseNode {

    /**
     * Reserved code point for a <b>continuation</b> lyric — emitted by the
     * normalizer when a single source syllable is sung across multiple
     * melodic notes (a melisma). The first note carries the actual glyph;
     * each subsequent note carries this underscore. Matches the LilyPond /
     * MusicXML {@code <syllabic>middle|end</syllabic>} convention enough
     * that downstream renderers can pick it up trivially.
     */
    public static final int CONTINUATION_CODE_POINT = '_';

    public LyricNode {
        Objects.requireNonNull(duration, "duration");
        if (!Character.isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException(
                    "codePoint is not a valid Unicode code point: " + codePoint);
        }
    }

    /** True when this node is a continuation marker (its char is {@code '_'}). */
    public boolean isContinuation() {
        return codePoint == CONTINUATION_CODE_POINT;
    }

    /** Build a continuation-marker node ({@code '_'}) of the given duration. */
    public static LyricNode continuation(Duration duration) {
        return new LyricNode(CONTINUATION_CODE_POINT, duration);
    }

    /**
     * The glyph as a {@link String}. Handles non-BMP code points
     * (surrogate pairs) automatically — prefer this over {@code (char)
     * codePoint()} for any display path.
     */
    public String character() {
        return new String(Character.toChars(codePoint));
    }

    // ── Static factories ──

    /** From a BMP character (most ASCII / Latin / common CJK paths). */
    public static LyricNode of(char ch, Duration duration) {
        return new LyricNode(ch, duration);
    }

    /** From an explicit code point — supports non-BMP (emoji, rare CJK). */
    public static LyricNode of(int codePoint, Duration duration) {
        return new LyricNode(codePoint, duration);
    }

    /**
     * From a one-glyph {@link String}. Rejects empty input and any input
     * that contains more than one Unicode code point — callers that mean
     * a multi-glyph syllable must explicitly build a sequence of
     * single-glyph nodes (typically one per beat fraction).
     */
    public static LyricNode of(String glyph, Duration duration) {
        Objects.requireNonNull(glyph, "glyph");
        if (glyph.isEmpty()) {
            throw new IllegalArgumentException("glyph must not be empty");
        }
        int first = glyph.codePointAt(0);
        int firstLen = Character.charCount(first);
        if (firstLen != glyph.length()) {
            throw new IllegalArgumentException(
                    "glyph must be exactly one Unicode code point: " + glyph);
        }
        return new LyricNode(first, duration);
    }
}
