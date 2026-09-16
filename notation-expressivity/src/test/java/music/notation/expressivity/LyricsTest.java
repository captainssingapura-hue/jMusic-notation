package music.notation.expressivity;

import music.notation.duration.Duration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LyricsTest {

    /** Convenience for the test: a Duration at "N quarters from start". */
    private static Duration qq(long quarters) {
        return Duration.of(quarters, 4);
    }

    @Test
    void lyricEvent_validatesCodePointAndAt() {
        assertThrows(NullPointerException.class,
                () -> new LyricEvent(null, 'a'),
                "null at rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new LyricEvent(Duration.zero(), -1),
                "invalid code point rejected");
    }

    @Test
    void lyricEvent_continuationFactory() {
        LyricEvent c = LyricEvent.continuation(qq(2));
        assertTrue(c.isContinuation());
        assertEquals("_", c.character());
        assertTrue(qq(2).equalsDuration(c.at()));
    }

    @Test
    void lyricEvent_nonBmpRoundTrips() {
        // 🎵 = U+1F3B5
        LyricEvent ev = new LyricEvent(Duration.zero(), 0x1F3B5);
        assertEquals("🎵", ev.character());
        assertEquals(0x1F3B5, ev.codePoint());
    }

    @Test
    void lyricLine_sortsByPosition() {
        // Input deliberately out of order — canonical form must sort.
        LyricLine line = new LyricLine(List.of(
                new LyricEvent(qq(8), 'c'),
                new LyricEvent(qq(0), 'a'),
                new LyricEvent(qq(4), 'b')));
        var events = line.events();
        assertEquals(3, events.size());
        assertTrue(qq(0).equalsDuration(events.get(0).at()));
        assertTrue(qq(4).equalsDuration(events.get(1).at()));
        assertTrue(qq(8).equalsDuration(events.get(2).at()));
    }

    @Test
    void lyricLine_dedupesSamePosition_keepingLastWriteWins() {
        // Two events at the same position — only the second ('y') survives.
        LyricLine line = new LyricLine(List.of(
                new LyricEvent(qq(2), 'x'),
                new LyricEvent(qq(2), 'y'),
                new LyricEvent(qq(4), 'z')));
        assertEquals(2, line.events().size());
        assertEquals('y', line.events().get(0).codePoint());
        assertEquals('z', line.events().get(1).codePoint());
    }

    @Test
    void lyrics_dropsEmptyLines() {
        // A track keyed to an empty line is filtered out → canonical equality.
        TrackId t1 = new TrackId("Melody");
        TrackId t2 = new TrackId("Empty");
        Lyrics ly = new Lyrics(Map.of(
                t1, new LyricLine(List.of(new LyricEvent(Duration.zero(), 'a'))),
                t2, LyricLine.empty()));
        assertEquals(1, ly.byTrack().size());
        assertTrue(ly.byTrack().containsKey(t1));
        assertFalse(ly.byTrack().containsKey(t2));
    }

    @Test
    void lyrics_emptyAndSingleFactories() {
        assertTrue(Lyrics.empty().byTrack().isEmpty());
        var line = new LyricLine(List.of(new LyricEvent(Duration.zero(), 'q')));
        var single = Lyrics.single(new TrackId("M"), line);
        assertEquals(1, single.byTrack().size());
        assertEquals(line, single.byTrack().values().iterator().next());
    }
}
