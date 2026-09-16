package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.HALF;
import static music.notation.duration.BaseValue.QUARTER;
import static music.notation.duration.BaseValue.WHOLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-checks of {@link LyricsNormalizer}. Each test pins a real-world
 * shape: simple one-to-one, melisma, melisma-against-rest, lyrics shorter
 * or longer than the melody, lyric-during-melody-rest.
 */
class LyricsNormalizerTest {

    // ── Builders ────────────────────────────────────────────────────────

    private static PitchNode q(int midiOctave) {
        return PitchNode.of(Pitch.of(NoteName.C, midiOctave), Duration.of(QUARTER));
    }

    private static PitchNode h(int octave) {
        return PitchNode.of(Pitch.of(NoteName.C, octave), Duration.of(HALF));
    }

    private static LyricNode lyr(char c, music.notation.duration.BaseValue v) {
        return LyricNode.of(c, Duration.of(v));
    }

    private static RestNode rest(music.notation.duration.BaseValue v) {
        return new RestNode(Duration.of(v));
    }

    // ── Tests ───────────────────────────────────────────────────────────

    @Test
    void oneSyllablePerNote_passesGlyphsThroughOneToOne() {
        // Raw:    "a" "b" "c" "d"   (four quarter-note syllables)
        // Melody: C  C  C  C        (four quarter notes)
        // Expect: a  b  c  d        (no continuations — each syllable
        //                            owns exactly one onset)
        var raw = List.<PhraseNode>of(
                lyr('a', QUARTER), lyr('b', QUARTER),
                lyr('c', QUARTER), lyr('d', QUARTER));
        var melody = List.<PhraseNode>of(q(4), q(4), q(4), q(4));

        var out = LyricsNormalizer.normalize(raw, melody);
        assertEquals(4, out.size());
        assertEquals("a", ((LyricNode) out.get(0)).character());
        assertEquals("b", ((LyricNode) out.get(1)).character());
        assertEquals("c", ((LyricNode) out.get(2)).character());
        assertEquals("d", ((LyricNode) out.get(3)).character());
        assertTrue(out.stream().noneMatch(n -> n instanceof LyricNode l && l.isContinuation()));
    }

    @Test
    void singleSyllableSpansFourNotes_emitsCharThenThreeContinuations() {
        // Raw:    "一" (whole — one syllable held for four quarters)
        // Melody: C  C  C  C   (four quarter notes)
        // Expect: 一 _ _ _
        var raw = List.<PhraseNode>of(lyr('一', WHOLE));
        var melody = List.<PhraseNode>of(q(4), q(4), q(4), q(4));

        var out = LyricsNormalizer.normalize(raw, melody);
        assertEquals(4, out.size());
        assertEquals("一", ((LyricNode) out.get(0)).character());
        for (int i = 1; i < 4; i++) {
            assertTrue(((LyricNode) out.get(i)).isContinuation(),
                    "node " + i + " should be the '_' continuation marker");
        }
    }

    @Test
    void mixed_oneSyllable_then_melisma() {
        // Raw:    "a"(quarter) "b"(half)         (b held over 2 quarters)
        // Melody: C(quarter)   C(quarter) C(quarter)
        // Expect: a            b          _
        var raw = List.<PhraseNode>of(lyr('a', QUARTER), lyr('b', HALF));
        var melody = List.<PhraseNode>of(q(4), q(4), q(4));

        var out = LyricsNormalizer.normalize(raw, melody);
        assertEquals(3, out.size());
        assertEquals("a", ((LyricNode) out.get(0)).character());
        assertEquals("b", ((LyricNode) out.get(1)).character());
        assertTrue(((LyricNode) out.get(2)).isContinuation());
    }

    @Test
    void melodyRest_emitsRestRegardlessOfRawLyric() {
        // Raw:    "a" (whole)            — syllable held for four quarters
        // Melody: C(quarter)  rest(quarter)  C(quarter)  C(quarter)
        // Expect: a            REST          _           _
        //   The rest in the melody is unchanged; the first audible onset
        //   after the rest is still a continuation because the same raw
        //   syllable is still active.
        var raw = List.<PhraseNode>of(lyr('a', WHOLE));
        var melody = List.<PhraseNode>of(q(4), rest(QUARTER), q(4), q(4));

        var out = LyricsNormalizer.normalize(raw, melody);
        assertEquals(4, out.size());
        assertEquals("a", ((LyricNode) out.get(0)).character());
        assertInstanceOf(RestNode.class, out.get(1));
        assertTrue(((LyricNode) out.get(2)).isContinuation());
        assertTrue(((LyricNode) out.get(3)).isContinuation());
    }

    @Test
    void rawShorterThanMelody_padsWithRests() {
        // Raw:    "a"(quarter)   (only one syllable)
        // Melody: C C C C        (four quarter notes)
        // Expect: a  REST REST REST
        var raw = List.<PhraseNode>of(lyr('a', QUARTER));
        var melody = List.<PhraseNode>of(q(4), q(4), q(4), q(4));

        var out = LyricsNormalizer.normalize(raw, melody);
        assertEquals(4, out.size());
        assertEquals("a", ((LyricNode) out.get(0)).character());
        for (int i = 1; i < 4; i++) {
            assertInstanceOf(RestNode.class, out.get(i),
                    "node " + i + " has no syllable to attribute → REST");
        }
    }

    @Test
    void rawLongerThanMelody_dropsTrailingSyllables() {
        // Raw:    "a" "b" "c" "d"   (four syllables)
        // Melody: C  C              (two quarter notes)
        // Expect: a  b              ("c" and "d" silently dropped)
        var raw = List.<PhraseNode>of(
                lyr('a', QUARTER), lyr('b', QUARTER),
                lyr('c', QUARTER), lyr('d', QUARTER));
        var melody = List.<PhraseNode>of(q(4), q(4));

        var out = LyricsNormalizer.normalize(raw, melody);
        assertEquals(2, out.size());
        assertEquals("a", ((LyricNode) out.get(0)).character());
        assertEquals("b", ((LyricNode) out.get(1)).character());
    }

    @Test
    void rawRest_aligningWithMelodyOnset_emitsRest() {
        // Raw:    REST(quarter) "b"(quarter)
        // Melody: C             C
        // Expect: REST          b
        //   — the first melody onset sits inside the raw rest → no glyph.
        var raw = List.<PhraseNode>of(rest(QUARTER), lyr('b', QUARTER));
        var melody = List.<PhraseNode>of(q(4), q(4));

        var out = LyricsNormalizer.normalize(raw, melody);
        assertEquals(2, out.size());
        assertInstanceOf(RestNode.class, out.get(0));
        assertEquals("b", ((LyricNode) out.get(1)).character());
    }

    @Test
    void halfNoteMelodyWithHalfNoteSyllable_lineUpsOneToOne() {
        // Raw:    "a"(half) "b"(half)
        // Melody: C(half)   C(half)
        // Expect: a  b   — durations match, no continuation
        var raw = List.<PhraseNode>of(lyr('a', HALF), lyr('b', HALF));
        var melody = List.<PhraseNode>of(h(4), h(4));

        var out = LyricsNormalizer.normalize(raw, melody);
        assertEquals(2, out.size());
        assertEquals("a", ((LyricNode) out.get(0)).character());
        assertEquals("b", ((LyricNode) out.get(1)).character());
        // Durations should match the melody's, not the raw lyric's
        // (they happen to be identical here but the contract is "melody shape").
        assertEquals(Bar.nodeSixtyFourths(melody.get(0)),
                Bar.nodeSixtyFourths(out.get(0)));
    }

    @Test
    void outputTotalDurationEqualsMelodyTotalDuration() {
        // Whatever the raw lyrics shape, the output must be melody-shaped:
        // sum-of-durations across the output equals the melody's.
        var raw = List.<PhraseNode>of(lyr('一', WHOLE), lyr('二', HALF));
        var melody = List.<PhraseNode>of(
                q(4), q(4), rest(QUARTER), q(4), h(4), q(4));
        int melodySixteenths = melody.stream().mapToInt(Bar::nodeSixtyFourths).sum();

        var out = LyricsNormalizer.normalize(raw, melody);
        int outSixteenths = out.stream().mapToInt(Bar::nodeSixtyFourths).sum();
        assertEquals(melodySixteenths, outSixteenths);
        assertEquals(melody.size(), out.size(),
                "output is melody-shaped → one normalized node per melody node");
    }

    @Test
    void rejectsUnsupportedNodeInRawLyrics() {
        var raw = List.<PhraseNode>of(q(4));  // PitchNode is not allowed in raw lyrics
        var melody = List.<PhraseNode>of(q(4));
        assertThrows(IllegalArgumentException.class,
                () -> LyricsNormalizer.normalize(raw, melody));
    }
}
