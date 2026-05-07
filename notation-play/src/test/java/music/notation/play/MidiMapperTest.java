package music.notation.play;

import music.notation.duration.BaseValue;
import music.notation.duration.Dotted;
import music.notation.duration.Duration;
import music.notation.duration.RawTuplet;
import music.notation.duration.Triplet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MidiMapperTest {

    private static final int PPQ = 480;   // matches MidiMapper.TICKS_PER_QUARTER

    // ── Power-of-two durations: should match the legacy path bit-for-bit ─

    @Test
    void quarterNote_isPpqTicks() {
        assertEquals(PPQ, MidiMapper.toTicks(BaseValue.QUARTER));
    }

    @Test
    void wholeNote_isFourPpq() {
        assertEquals(4L * PPQ, MidiMapper.toTicks(BaseValue.WHOLE));
    }

    @Test
    void eighthNote_isHalfPpq() {
        assertEquals(PPQ / 2, MidiMapper.toTicks(BaseValue.EIGHTH));
    }

    @Test
    void sixtyFourthNote_is30Ticks() {
        // 480 / 16 = 30 ticks
        assertEquals(30, MidiMapper.toTicks(BaseValue.SIXTY_FOURTH));
    }

    // ── Dotted: still exact ────────────────────────────────────────────

    @Test
    void dottedQuarter_is720Ticks() {
        // dotted quarter = 3/8 of whole = 3/8 × 1920 = 720
        assertEquals(720, MidiMapper.toTicks(Dotted.QUARTER));
    }

    @Test
    void doubleDottedQuarter_is840Ticks() {
        // 7/16 × 1920 = 840
        assertEquals(840, MidiMapper.toTicks(new Dotted(BaseValue.QUARTER, 2)));
    }

    // ── Tuplets: this is what Phase 3 actually unlocks ─────────────────

    @Test
    void tripletEighth_is160Ticks() {
        // 1/12 of whole = 1920 / 12 = 160 ticks (exact integer at PPQ=480).
        // Old (lossy) math: sf=5 (rounded from 5.33), 5 × 480 / 16 = 150. WRONG.
        assertEquals(160, MidiMapper.toTicks(Triplet.EIGHTH));
    }

    @Test
    void tripletQuarter_is320Ticks() {
        // 1/6 of whole = 1920 / 6 = 320 ticks
        assertEquals(320, MidiMapper.toTicks(Triplet.QUARTER));
    }

    @Test
    void quintupletSixteenth_is96Ticks() {
        // 1/20 of whole = 1920 / 20 = 96 ticks
        assertEquals(96, MidiMapper.toTicks(RawTuplet.ofStandard(5, BaseValue.SIXTEENTH)));
    }

    @Test
    void quintupletEighth_is192Ticks() {
        // 1/10 of whole = 1920 / 10 = 192 ticks
        assertEquals(192, MidiMapper.toTicks(RawTuplet.ofStandard(5, BaseValue.EIGHTH)));
    }

    @Test
    void septupletEighth_is137Ticks() {
        // 1/14 of whole = 1920 / 14 = 137.142… → integer truncation → 137.
        // Tiny rounding — but consistent for every septuplet note.
        assertEquals(137, MidiMapper.toTicks(RawTuplet.ofStandard(7, BaseValue.EIGHTH)));
    }

    // ── Three triplet eighths sum to one quarter ───────────────────────

    @Test
    void threeTripletEighths_sumToQuarter() {
        long three = 3 * MidiMapper.toTicks(Triplet.EIGHTH);
        long quarter = MidiMapper.toTicks(BaseValue.QUARTER);
        assertEquals(quarter, three);
    }

    @Test
    void fiveQuintupletSixteenths_sumToQuarter() {
        long five = 5 * MidiMapper.toTicks(RawTuplet.ofStandard(5, BaseValue.SIXTEENTH));
        long quarter = MidiMapper.toTicks(BaseValue.QUARTER);
        assertEquals(quarter, five);
    }

    // ── Arbitrary rational (escape hatch) ──────────────────────────────

    @Test
    void rawDuration_works() {
        // 7/32 (double-dotted eighth) = 1920 × 7/32 = 420 ticks
        assertEquals(420, MidiMapper.toTicks(Duration.of(7, 32)));
    }
}
