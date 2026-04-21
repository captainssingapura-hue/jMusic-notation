package music.notation.structure;

import music.notation.event.Instrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link TrackDecl} sealed interface and its two record
 * variants. Validates name constraints and instrument presence.
 */
class TrackDeclTest {

    @Test
    void musicTrackDeclHoldsNameAndInstrument() {
        var decl = new TrackDecl.MusicTrackDecl("Melody", Instrument.ACOUSTIC_GRAND_PIANO);
        assertEquals("Melody", decl.name());
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO, decl.defaultInstrument());
    }

    @Test
    void controlTrackDeclHoldsNameOnly() {
        var decl = new TrackDecl.ControlTrackDecl("Tempo");
        assertEquals("Tempo", decl.name());
    }

    @Test
    void musicTrackDeclRejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackDecl.MusicTrackDecl("", Instrument.ACOUSTIC_GRAND_PIANO));
        assertThrows(IllegalArgumentException.class,
                () -> new TrackDecl.MusicTrackDecl("   ", Instrument.ACOUSTIC_GRAND_PIANO));
    }

    @Test
    void musicTrackDeclRejectsNullInstrument() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackDecl.MusicTrackDecl("Melody", null));
    }

    @Test
    void controlTrackDeclRejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackDecl.ControlTrackDecl(""));
    }

    @Test
    void sealedHierarchyIsCovered() {
        // Compile-time check: a switch over TrackDecl without default must
        // handle both variants. If a new variant is added, this will fail
        // to compile.
        TrackDecl t = new TrackDecl.MusicTrackDecl("X", Instrument.ACOUSTIC_GRAND_PIANO);
        String kind = switch (t) {
            case TrackDecl.MusicTrackDecl m -> "music";
            case TrackDecl.ControlTrackDecl c -> "control";
        };
        assertEquals("music", kind);
    }
}
