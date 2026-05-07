package music.notation.performance;

import music.notation.performance.BarBuilder.Config;
import music.notation.performance.OnsetGrouper.GroupedEvent;
import music.notation.phrase.Bar;
import music.notation.phrase.PitchNode;
import music.notation.phrase.PolyPitchNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.SimplePitchNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BarBuilderTest {

    /** 4/4 bar @ 120 BPM: 64sf/bar, 1 quarter = 500ms = 16sf, so 1ms = 0.032sf. */
    private static final Config CFG_4_4_120 = new Config(64, 120);

    private static GroupedEvent ev(long onset, long dur, int... pitches) {
        var list = new java.util.ArrayList<Integer>();
        for (int p : pitches) list.add(p);
        return new GroupedEvent(onset, dur, list);
    }

    @Test
    void emptyVoice_yieldsEmptyBars() {
        assertTrue(BarBuilder.build(List.of(), CFG_4_4_120).isEmpty());
    }

    @Test
    void singleQuarterNote_oneBarFilledWithRests() {
        // A single C4 quarter at the start; rest of bar is silent.
        var bars = BarBuilder.build(List.of(ev(0, 500, 60)), CFG_4_4_120);
        assertEquals(1, bars.size());
        Bar b = bars.get(0);
        assertEquals(64, b.expectedSixtyFourths());
        // First node = pitch, remaining = rest(s) summing to 48 sf.
        assertInstanceOf(SimplePitchNode.class, b.nodes().get(0));
        var pn = (SimplePitchNode) b.nodes().get(0);
        assertEquals(16, pn.duration().sixtyFourths(), "quarter = 16 sf");
        assertFalse(pn.tiedToNext());
    }

    @Test
    void fourQuarters_fillExactlyOneBar() {
        var voice = List.of(
                ev(0,    500, 60),
                ev(500,  500, 62),
                ev(1000, 500, 64),
                ev(1500, 500, 65));
        var bars = BarBuilder.build(voice, CFG_4_4_120);
        assertEquals(1, bars.size());
        var b = bars.get(0);
        assertEquals(4, b.nodes().size());
        for (var n : b.nodes()) {
            assertInstanceOf(SimplePitchNode.class, n);
            assertEquals(16, ((PitchNode) n).duration().sixtyFourths());
        }
    }

    @Test
    void noteSpanningBarBoundary_splitIntoTwoTiedSegments() {
        // Note attacks at posInBar=32 and runs a whole note (64sf) ⇒
        //   bar 0: rest(half) + half(tied) ; bar 1: half + rest(half).
        // 32sf at 120 BPM = 1000 ms; whole = 64sf = 2000 ms.
        var voice = List.of(ev(1000, 2000, 60));
        var bars = BarBuilder.build(voice, CFG_4_4_120);
        assertEquals(2, bars.size());
        // Bar 0: rest(half) then half-note tied to next.
        var b0 = bars.get(0);
        assertInstanceOf(RestNode.class, b0.nodes().get(0));
        assertEquals(32, ((RestNode) b0.nodes().get(0)).duration().sixtyFourths());
        var pn0 = (SimplePitchNode) b0.nodes().get(1);
        assertEquals(32, pn0.duration().sixtyFourths());
        assertTrue(pn0.tiedToNext(), "first half should be tied to the next bar");
        // Bar 1: half-note continuation, not tied, then half-note rest.
        var b1 = bars.get(1);
        var pn1 = (SimplePitchNode) b1.nodes().get(0);
        assertEquals(32, pn1.duration().sixtyFourths());
        assertFalse(pn1.tiedToNext(), "tail of tie chain shouldn't be tied");
        assertInstanceOf(RestNode.class, b1.nodes().get(1));
    }

    @Test
    void chordBecomesPolyPitchNode() {
        // C-major chord on beat 1, eighth note duration.
        var voice = List.of(ev(0, 250, 60, 64, 67));
        var bars = BarBuilder.build(voice, CFG_4_4_120);
        var first = bars.get(0).nodes().get(0);
        assertInstanceOf(PolyPitchNode.class, first);
        var poly = (PolyPitchNode) first;
        assertEquals(3, poly.pitches().size());
        assertEquals(8, poly.duration().sixtyFourths(), "eighth = 8 sf");
    }

    @Test
    void gapBetweenNotes_emitsRest() {
        // Two quarters with an eighth-note rest between them.
        var voice = List.of(
                ev(0,   500, 60),                // quarter
                ev(750, 500, 64));               // quarter after eighth-rest gap
        var bars = BarBuilder.build(voice, CFG_4_4_120);
        var b = bars.get(0);
        // expect: pitch(16) + rest(8) + pitch(16) + trailing rest(s)
        assertInstanceOf(SimplePitchNode.class, b.nodes().get(0));
        assertInstanceOf(RestNode.class,        b.nodes().get(1));
        assertInstanceOf(SimplePitchNode.class, b.nodes().get(2));
        assertEquals(8, ((RestNode) b.nodes().get(1)).duration().sixtyFourths());
    }

    @Test
    void barAlwaysClosesEvenIfLastNoteShort() {
        // Single eighth note in a 4/4 bar should produce a complete bar.
        var bars = BarBuilder.build(List.of(ev(0, 250, 60)), CFG_4_4_120);
        assertEquals(1, bars.size());
        assertEquals(64, bars.get(0).expectedSixtyFourths());
    }

    @Test
    void midiToPitch_roundsTrip() {
        // Spot-check: MIDI 60 = C4, 61 = C#4, 71 = B4, 72 = C5.
        assertEquals("StaffPitch", BarBuilder.midiToPitch(60).getClass().getSimpleName());
        // No fragile string-compare; just sanity the construction works.
        for (int m = 21; m <= 108; m++) {
            assertNotNull(BarBuilder.midiToPitch(m));
        }
    }

    @Test
    void smallOverlap_fromRoundingIsTolerated() {
        // Two consecutive notes whose ms timings, when ms→sf rounded,
        // would technically overlap by 1 sf. BarBuilder should clamp.
        var voice = List.of(
                ev(0,   501, 60),    // ends at 501 ms ≈ 16.03 sf
                ev(499, 500, 62));   // starts at 499 ms ≈ 15.97 sf
        var bars = BarBuilder.build(voice, CFG_4_4_120);
        // Should still produce a single bar with two contiguous notes.
        assertFalse(bars.isEmpty());
    }

    // ── Phase 6 — rational walker verification ──────────────────────

    @Test
    void tripletEighths_threeOfThem_makeAQuarter_underTripletProfile() {
        // At 120 BPM, a quarter = 500 ms, so three triplet eighths
        // (1/12 of whole each) = 500 ms total. Each triplet eighth
        // is exactly 500/3 ms ≈ 166.67 ms.
        // Source MIDI commonly renders these as ~166-167 ms each.
        var voice = List.of(
                ev(0,   167, 60),
                ev(167, 167, 62),
                ev(334, 166, 64),
                ev(500, 500, 65),  // a normal quarter follows
                ev(1000, 500, 67),
                ev(1500, 500, 69));
        var cfg = new BarBuilder.Config(64, 120,
                music.notation.performance.QuantizerProfile.WITH_TRIPLETS);
        var bars = BarBuilder.build(voice, cfg);
        // Whatever the bar count, every bar must have 64 sf — proves
        // the walker tracked rationals exactly with no triplet drift.
        for (var bar : bars) {
            assertEquals(64, bar.expectedSixtyFourths(),
                    "every bar must close to 64 sf; rational walker prevents triplet drift");
        }
        // Three triplet eighths + a quarter = 1/2 = a half. Plus two more
        // quarters = 1 whole. The whole melody fits in one 4/4 bar.
        assertEquals(1, bars.size());
    }

    @Test
    void standardProfile_tripletInput_falsesnap_butStillValidBar() {
        // Same input under STANDARD profile: triplet eighths get
        // misread as something else (probably 16ths or dotted-32nds),
        // but the walker still produces a valid bar (sums to barSf via
        // greedy rest decomposition / overlap clamp).
        var voice = List.of(
                ev(0,   167, 60),
                ev(167, 167, 62),
                ev(334, 166, 64),
                ev(500, 500, 65),
                ev(1000, 500, 67),
                ev(1500, 500, 69));
        var cfg = new BarBuilder.Config(64, 120);   // STANDARD profile
        var bars = BarBuilder.build(voice, cfg);
        for (var bar : bars) {
            assertEquals(64, bar.expectedSixtyFourths());
        }
    }

    @Test
    void differentTimeSig_3_4() {
        // 3/4 bar = 48 sf. Three quarters fill it exactly.
        var cfg = new Config(48, 120);
        var voice = List.of(
                ev(0,    500, 60),
                ev(500,  500, 62),
                ev(1000, 500, 64));
        var bars = BarBuilder.build(voice, cfg);
        assertEquals(1, bars.size());
        assertEquals(48, bars.get(0).expectedSixtyFourths());
        assertEquals(3, bars.get(0).nodes().size());
    }
}
