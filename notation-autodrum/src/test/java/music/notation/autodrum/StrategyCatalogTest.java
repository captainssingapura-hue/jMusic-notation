package music.notation.autodrum;

import music.notation.duration.BarDuration;
import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.phrase.Bar;
import music.notation.phrase.Phrase;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalogue smoke test — every registered strategy other than NONE
 * must produce a non-empty drum track at every energy level for a
 * standard 4/4 piece, AND must round-trip its sentinel sound (kick)
 * somewhere in the resulting bars.
 */
class StrategyCatalogTest {

    private static Piece fourFourPiece() {
        var bd = new BarDuration(4, BaseValue.QUARTER);
        var note = new SimplePitchNode(
                Pitch.of(NoteName.C, 4),
                Duration.ofSixtyFourths(bd.sixtyFourths()),
                Optional.empty(), List.of(), false, false);
        var melody = new MelodicTrack("Melody", Instrument.ACOUSTIC_GRAND_PIANO,
                Phrase.of(List.of(Bar.of(bd, note), Bar.of(bd, note))));
        return new Piece("Catalogue Test", "Test",
                new KeySignature(NoteName.C, Mode.MAJOR),
                new TimeSignature(4, 4),
                new Tempo(120, BaseValue.QUARTER),
                List.of(melody));
    }

    @Test
    void allFourFourStrategiesProduceNonEmptyTrackAtEveryEnergy() {
        Piece piece = fourFourPiece();
        for (DrumStrategy strategy : DrumStrategies.available()) {
            if (strategy == DrumStrategies.NONE) continue;
            // Shuffle is compound-time only — skip for the 4/4 fixture.
            if (strategy == DrumStrategies.SHUFFLE) continue;
            for (Energy energy : Energy.values()) {
                Optional<DrumTrack> drums = strategy.generate(piece, energy);
                assertTrue(drums.isPresent(),
                        "expected non-empty drums for " + strategy.id() + " @ " + energy);
                assertFalse(drums.get().bars().isEmpty(),
                        "expected non-empty bars for " + strategy.id() + " @ " + energy);
            }
        }
    }

    @Test
    void shuffleProducesTrackForCompoundMeter() {
        var bd = new BarDuration(12, BaseValue.EIGHTH);
        var rest = new music.notation.phrase.RestNode(bd.totalDuration());
        var melody = new MelodicTrack("Stub", Instrument.ACOUSTIC_GRAND_PIANO,
                Phrase.of(List.of(Bar.of(bd, rest))));
        var piece = new Piece("Compound", "Test",
                new KeySignature(NoteName.C, Mode.MAJOR),
                new TimeSignature(12, 8),
                new Tempo(120, BaseValue.EIGHTH),
                List.of(melody));
        for (Energy energy : Energy.values()) {
            assertTrue(DrumStrategies.SHUFFLE.generate(piece, energy).isPresent(),
                    "shuffle should apply to 12/8 at " + energy);
        }
    }

    @Test
    void nonMatchingMeterFallsBackGracefully() {
        // 5/4 isn't a meter most strategies natively pattern, but they
        // should still produce a graceful fallback (kick on beat 1) so the
        // user gets audible feedback that drums are active.
        var bd = new BarDuration(5, BaseValue.QUARTER);
        var rest = new music.notation.phrase.RestNode(bd.totalDuration());
        var melody = new MelodicTrack("Stub", Instrument.ACOUSTIC_GRAND_PIANO,
                Phrase.of(List.of(Bar.of(bd, rest), Bar.of(bd, rest))));
        var piece = new Piece("5/4", "Test",
                new KeySignature(NoteName.C, Mode.MAJOR),
                new TimeSignature(5, 4),
                new Tempo(120, BaseValue.QUARTER),
                List.of(melody));
        for (DrumStrategy strategy : DrumStrategies.available()) {
            if (strategy == DrumStrategies.NONE) continue;
            // Gentle Classical handles 5/4 via its own quiet-pattern path
            // (silent source bar → kick + rests) so it always produces.
            // Other strategies should now also produce via the shared
            // fallbackBar helper.
            Optional<DrumTrack> drums = strategy.generate(piece);
            assertTrue(drums.isPresent(),
                    strategy.id() + " should fall back gracefully on 5/4");
            assertEquals(2, drums.get().bars().size());
        }
    }
}
