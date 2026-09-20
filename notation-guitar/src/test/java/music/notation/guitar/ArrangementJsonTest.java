package music.notation.guitar;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.*;

class ArrangementJsonTest {

    @Test
    void minimalRequestGetsDefaults() {
        var r = ArrangementJson.requestFromJson("""
                { "source": { "type": "library", "title": "Some Piece" }, "track": "Solo 1" }
                """);
        assertEquals(new PieceSource.Library("Some Piece", ""), r.source());
        assertEquals("Solo 1", r.track());
        assertNull(r.bars());
        assertEquals(Tuning.STANDARD, r.tuning());
        assertEquals(HandConstraints.DEFAULT, r.hand());
        assertEquals(CostWeights.DEFAULT, r.weights());
        assertEquals(BreakdownSpec.DEFAULT, r.breakdown());
    }

    @Test
    void fullRequestRoundTrips() {
        var r = new ArrangementRequest(
                new PieceSource.Folder("R:/pieces/x"), "Lead",
                new BarRange(5, 12), Tuning.DROP_D.withCapo(2),
                new HandConstraints(20, 5, 6, 12, false, true),
                new CostWeights(1, 2, 3, 4, -0.5, 6, 2, 3),
                new BreakdownSpec(BreakdownSpec.Mode.POSITION, 4, 0.5));
        String json = ArrangementJson.toJson(r);
        assertTrue(json.contains("\"type\" : \"folder\""), json);
        assertEquals(r, ArrangementJson.requestFromJson(json));
    }

    @Test
    void unknownFieldsAreErrors() {
        var ex = assertThrows(UncheckedIOException.class, () -> ArrangementJson.requestFromJson("""
                { "source": { "type": "library", "title": "X" }, "track": "T", "hnad": { "maxFret": 12 } }
                """));
        assertTrue(ex.getMessage().contains("hnad"), ex.getMessage());
    }

    @Test
    void invalidValuesAreRejectedByTheValueObjects() {
        assertThrows(UncheckedIOException.class, () -> ArrangementJson.requestFromJson("""
                { "source": { "type": "library", "title": "X" }, "track": "T", "bars": { "fromBar": 0, "toBar": 3 } }
                """));
        assertThrows(IllegalArgumentException.class, () -> new HandConstraints(22, 4, 2, 15, true, false));
        assertThrows(IllegalArgumentException.class, () -> new Tuning("t", java.util.List.of(), 0));
    }

    @Test
    void exampleRequestIsValidJson() {
        var example = GuitarArrangeCli.exampleRequest();
        assertEquals(example, ArrangementJson.requestFromJson(ArrangementJson.toJson(example)));
    }
}
