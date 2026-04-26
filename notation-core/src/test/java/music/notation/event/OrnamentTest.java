package music.notation.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sealed-ADT coverage for {@link Ornament} and the backward-compat singletons.
 */
class OrnamentTest {

    @Test
    void singletonEqualsFreshlyConstructedRecord() {
        assertEquals(Ornament.TRILL, new Trill());
        assertEquals(Ornament.MORDENT, new Mordent());
        assertEquals(Ornament.LOWER_MORDENT, new LowerMordent());
        assertEquals(Ornament.TURN, new Turn());
        assertEquals(Ornament.TREMOLO, new Tremolo());
        assertEquals(Ornament.APPOGGIATURA, new Appoggiatura());
        assertEquals(Ornament.ACCIACCATURA, new Acciaccatura());
    }

    @Test
    void sealedSwitchExhaustiveOverAllSevenVariants() {
        // Compiles only if the sealed permits clause covers every arm.
        for (Ornament o : new Ornament[]{
                Ornament.TRILL, Ornament.MORDENT, Ornament.LOWER_MORDENT,
                Ornament.TURN, Ornament.TREMOLO, Ornament.APPOGGIATURA, Ornament.ACCIACCATURA
        }) {
            String name = switch (o) {
                case Trill t -> "trill";
                case Mordent m -> "mordent";
                case LowerMordent m -> "lower_mordent";
                case Turn t -> "turn";
                case Tremolo t -> "tremolo";
                case Appoggiatura a -> "appoggiatura";
                case Acciaccatura a -> "acciaccatura";
            };
            assertNotNull(name);
        }
    }
}
