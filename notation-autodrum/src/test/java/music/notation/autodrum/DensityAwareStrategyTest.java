package music.notation.autodrum;

import music.notation.duration.BarDuration;
import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.event.PercussionSound;
import music.notation.phrase.Bar;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.SimplePitchNode;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.structure.DrumTrack;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
import music.notation.structure.Piece;
import music.notation.structure.Tempo;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Density-aware variant selection — the strategy reads
 * {@link SourceAnalysis} and picks per-bar variants per the bucket.
 */
class DensityAwareStrategyTest {

    private static final BarDuration BD = new BarDuration(4, BaseValue.QUARTER);

    /** Source bar: full 16th-note runs (very dense). */
    private static Bar denseBar() {
        var nodes = new ArrayList<PhraseNode>();
        for (int i = 0; i < 16; i++) {
            nodes.add(new SimplePitchNode(Pitch.of(NoteName.C, 4),
                    Duration.of(BaseValue.SIXTEENTH),
                    Optional.empty(), List.of(), false, false));
        }
        return Bar.of(BD, nodes.toArray(new PhraseNode[0]));
    }

    /** Source bar: a single half + a single half (sparse). */
    private static Bar sparseBar() {
        var n1 = new SimplePitchNode(Pitch.of(NoteName.C, 4), Duration.of(BaseValue.HALF),
                Optional.empty(), List.of(), false, false);
        var n2 = new SimplePitchNode(Pitch.of(NoteName.D, 4), Duration.of(BaseValue.HALF),
                Optional.empty(), List.of(), false, false);
        return Bar.of(BD, n1, n2);
    }

    /** Standard 8th-note bar — density ≈ 2.0, lands in STANDARD bucket. */
    private static Bar standardBar() {
        var nodes = new ArrayList<PhraseNode>();
        for (int i = 0; i < 8; i++) {
            nodes.add(new SimplePitchNode(Pitch.of(NoteName.C, 4),
                    Duration.of(BaseValue.EIGHTH),
                    Optional.empty(), List.of(), false, false));
        }
        return Bar.of(BD, nodes.toArray(new PhraseNode[0]));
    }

    /** Source bar: all rests — EMPTY bucket. */
    private static Bar emptyBar() {
        return Bar.of(BD, new RestNode(BD.totalDuration()));
    }

    private static Piece pieceWith(List<Bar> bars) {
        var melody = new MelodicTrack("Melody", Instrument.ACOUSTIC_GRAND_PIANO,
                Phrase.of(bars));
        return new Piece("Test", "Test",
                new KeySignature(NoteName.C, Mode.MAJOR),
                new TimeSignature(4, 4),
                new Tempo(120, BaseValue.QUARTER),
                List.of(melody));
    }

    // ── SourceAnalysis sanity ───────────────────────────────────────────

    @Test
    void analysisBucketsAlignWithBarContent() {
        var piece = pieceWith(List.of(emptyBar(), sparseBar(), standardBar(), denseBar()));
        SourceAnalysis a = SourceAnalysis.scan(piece);
        assertEquals(DensityBucket.EMPTY,    a.at(0).bucket());
        assertEquals(DensityBucket.SPARSE,   a.at(1).bucket());
        assertEquals(DensityBucket.STANDARD, a.at(2).bucket());
        assertEquals(DensityBucket.DENSE,    a.at(3).bucket());
    }

    // ── Rock 8ths variant selection ─────────────────────────────────────

    @Test
    void rockBeatThinsOutOnDenseBars() {
        // Source: standard | dense — strategy should emit standard pattern
        // for bar 0, thinned (4-slot quarter) for bar 1.
        var piece = pieceWith(List.of(standardBar(), denseBar()));
        Optional<DrumTrack> drums = DrumStrategies.ROCK_8TH.generate(piece, Energy.MEDIUM);
        assertTrue(drums.isPresent());
        assertEquals(8, drums.get().bars().get(0).nodes().size(),
                "STANDARD bar should keep 8th-note pattern (8 slots)");
        assertEquals(4, drums.get().bars().get(1).nodes().size(),
                "DENSE bar should thin to quarter K/S (4 slots)");
    }

    @Test
    void rockBeatAddsSixteenthHatFillEveryFourthSparseBar() {
        // 4 sparse bars in a row. Bar index 3 (the 4th) hits the fill period,
        // so it should emit the 16-slot 16th-hat fill pattern.
        var piece = pieceWith(List.of(sparseBar(), sparseBar(), sparseBar(), sparseBar()));
        Optional<DrumTrack> drums = DrumStrategies.ROCK_8TH.generate(piece, Energy.MEDIUM);
        assertTrue(drums.isPresent());
        for (int i = 0; i < 3; i++) {
            assertEquals(8, drums.get().bars().get(i).nodes().size(),
                    "non-fill SPARSE bar should be standard 8ths at index " + i);
        }
        assertEquals(16, drums.get().bars().get(3).nodes().size(),
                "every 4th SPARSE bar should emit the 16th-hat fill (16 slots)");
        // First slot of fill is a kick.
        assertEquals(PercussionSound.BASS_DRUM,
                ((PercussionNote) drums.get().bars().get(3).nodes().get(0)).sound());
    }

    @Test
    void rockBeatEmitsFallbackForEmptyBar() {
        var piece = pieceWith(List.of(emptyBar(), standardBar()));
        Optional<DrumTrack> drums = DrumStrategies.ROCK_8TH.generate(piece, Energy.MEDIUM);
        assertTrue(drums.isPresent());
        // Empty bar should be the fallbackBar (kick + 3 rests for 4/4).
        var bar0 = drums.get().bars().get(0);
        assertEquals(4, bar0.nodes().size());
        assertEquals(PercussionSound.BASS_DRUM,
                ((PercussionNote) bar0.nodes().get(0)).sound());
        for (int i = 1; i < 4; i++) {
            assertTrue(bar0.nodes().get(i) instanceof RestNode);
        }
    }

    // ── Other strategies thin out on DENSE ──────────────────────────────

    @Test
    void allStrategiesThinPatternOnDenseBars() {
        var piece = pieceWith(List.of(denseBar(), denseBar()));
        for (DrumStrategy s : List.of(DrumStrategies.DISCO, DrumStrategies.FUNK,
                                       DrumStrategies.JAZZ,  DrumStrategies.METAL)) {
            Optional<DrumTrack> drums = s.generate(piece, Energy.MEDIUM);
            assertTrue(drums.isPresent());
            // Each strategy's DENSE variant is a 4-slot quarter-note pattern.
            assertEquals(4, drums.get().bars().get(0).nodes().size(),
                    s.id() + " should thin to a 4-slot quarter pattern on DENSE bars");
        }
    }
}
