package music.notation.play;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegionTest {

    @Test
    void fullIsUnbounded() {
        var r = Region.full();
        assertEquals(0L, r.startTick());
        assertEquals(Long.MAX_VALUE, r.endTick());
        assertTrue(r.isFull());
    }

    @Test
    void boundedRegion() {
        var r = new Region(100, 500);
        assertEquals(400L, r.length());
        assertFalse(r.isFull());
    }

    @Test
    void rejectsNegativeStart() {
        assertThrows(IllegalArgumentException.class, () -> new Region(-1, 100));
    }

    @Test
    void rejectsEmptyOrInverted() {
        assertThrows(IllegalArgumentException.class, () -> new Region(100, 100));
        assertThrows(IllegalArgumentException.class, () -> new Region(200, 100));
    }
}
