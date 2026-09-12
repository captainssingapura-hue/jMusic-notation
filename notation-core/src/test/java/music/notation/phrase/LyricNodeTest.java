package music.notation.phrase;

import music.notation.duration.Duration;
import org.junit.jupiter.api.Test;

import static music.notation.duration.BaseValue.EIGHTH;
import static music.notation.duration.BaseValue.QUARTER;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Shape + invariants of {@link LyricNode}. The interesting cases:
 * <ul>
 *   <li>BMP and non-BMP code points round-trip through {@link LyricNode#character()}.</li>
 *   <li>Static factories from {@code char} / {@code int} / one-glyph {@code String}
 *       all converge on the same canonical record.</li>
 *   <li>{@link Bar#nodeDuration(PhraseNode)} returns the lyric's own duration
 *       (proves the sealed switch reaches the new arm).</li>
 *   <li>Empty / multi-glyph / invalid code points are rejected at construction.</li>
 * </ul>
 */
class LyricNodeTest {

    @Test
    void bmp_char_roundTrips() {
        LyricNode n = LyricNode.of('a', Duration.of(QUARTER));
        assertEquals(0x61, n.codePoint());
        assertEquals("a", n.character());
    }

    @Test
    void cjk_glyph_fromString_roundTrips() {
        LyricNode n = LyricNode.of("一", Duration.of(QUARTER));
        assertEquals("一", n.character());
        assertEquals(Duration.of(QUARTER), n.duration());
    }

    @Test
    void nonBmp_emoji_roundTrips() {
        // 🎵 = U+1F3B5 — needs a surrogate pair in a char[]. The codePoint
        // factory must keep it intact end-to-end.
        int musicalNote = 0x1F3B5;
        LyricNode n = LyricNode.of(musicalNote, Duration.of(EIGHTH));
        assertEquals(musicalNote, n.codePoint());
        assertEquals("🎵", n.character());
        assertEquals(2, n.character().length(), "non-BMP glyph occupies a surrogate pair");
    }

    @Test
    void factoryEquivalence_charAndInt_produceEqualRecords() {
        LyricNode a = LyricNode.of('z', Duration.of(QUARTER));
        LyricNode b = LyricNode.of(0x7a, Duration.of(QUARTER));
        LyricNode c = LyricNode.of("z", Duration.of(QUARTER));
        assertEquals(a, b);
        assertEquals(b, c);
    }

    @Test
    void barNodeDuration_returnsLyricsOwnDuration() {
        // Proves the sealed switch in Bar.nodeDuration() reaches the LyricNode arm
        // (a missing case would have been a compile error, but it doesn't hurt to
        // assert the result is the duration we passed in).
        LyricNode n = LyricNode.of('x', Duration.of(QUARTER));
        assertEquals(Duration.of(QUARTER), Bar.nodeDuration(n));
    }

    @Test
    void rejectsEmptyString() {
        assertThrows(IllegalArgumentException.class,
                () -> LyricNode.of("", Duration.of(QUARTER)));
    }

    @Test
    void rejectsMultiGlyphString() {
        // "ab" is two code points — must be rejected; the caller is expected
        // to split it into two LyricNodes with explicit durations each.
        assertThrows(IllegalArgumentException.class,
                () -> LyricNode.of("ab", Duration.of(QUARTER)));
    }

    @Test
    void rejectsInvalidCodePoint() {
        // -1 is not a valid Unicode code point.
        assertThrows(IllegalArgumentException.class,
                () -> LyricNode.of(-1, Duration.of(QUARTER)));
    }

    @Test
    void rejectsNullDuration() {
        assertThrows(NullPointerException.class,
                () -> LyricNode.of('a', null));
    }
}
