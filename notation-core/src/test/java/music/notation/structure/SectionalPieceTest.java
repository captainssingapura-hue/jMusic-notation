package music.notation.structure;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.PhraseConnection;
import music.notation.phrase.PhraseMarking;
import music.notation.phrase.StaffPhraseBuilderTyped;
import music.notation.pitch.NoteName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the sectional {@link Piece} constructor — join logic,
 * homogeneity enforcement, and track-order preservation.
 */
class SectionalPieceTest {

    private static final KeySignature KEY = new KeySignature(NoteName.C, Mode.MAJOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);
    private static final Tempo TEMPO = new Tempo(120, QUARTER);

    private static PhraseMarking attacca() {
        return new PhraseMarking(PhraseConnection.ATTACCA, false);
    }

    private static MelodicPhrase oneBarMelody(NoteName first) {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER)
                .bar().o4(first).o4(D).o4(E).o4(F).done()
                .build(attacca());
    }

    // ── Basic join ────────────────────────────────────────────────────

    @Test
    void twoSectionsJoinIntoFlatTracks() {
        var intro = Section.named("Intro")
                .duration(Duration.ofSixtyFourths(64))
                .timeSignature(TS)
                .track("Melody", oneBarMelody(C))
                .track("Bass",   oneBarMelody(C))
                .build();

        var verse = Section.named("Verse")
                .duration(Duration.ofSixtyFourths(64))
                .timeSignature(TS)
                .track("Melody", oneBarMelody(G))
                .track("Bass",   oneBarMelody(G))
                .build();

        var decls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", Instrument.ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Bass",   Instrument.ACOUSTIC_BASS)
        );

        Piece piece = Piece.ofSections("My Song", "Me", KEY, TS, TEMPO,
                decls, List.of(intro, verse));

        assertEquals(2, piece.tracks().size());
        // Order follows trackDecls order.
        assertEquals("Melody", piece.tracks().get(0).name());
        assertEquals("Bass",   piece.tracks().get(1).name());
        // Each resolved track has 2 phrases (1 per section).
        assertEquals(2, piece.tracks().get(0).phrases().size());
        assertEquals(2, piece.tracks().get(1).phrases().size());
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO, piece.tracks().get(0).defaultInstrument());
        assertEquals(Instrument.ACOUSTIC_BASS,        piece.tracks().get(1).defaultInstrument());
    }

    @Test
    void controlTrackResolvesToPlaceholderInstrument() {
        var intro = Section.named("Intro")
                .duration(Duration.ofSixtyFourths(64))
                .timeSignature(TS)
                .track("Tempo", VoidPhraseHelper.bars(TS, 1))
                .track("Melody", oneBarMelody(C))
                .build();

        var decls = List.<TrackDecl>of(
                new TrackDecl.ControlTrackDecl("Tempo"),
                new TrackDecl.MusicTrackDecl("Melody", Instrument.ACOUSTIC_GRAND_PIANO)
        );

        Piece piece = Piece.ofSections("X", "Y", KEY, TS, TEMPO, decls, List.of(intro));
        // Control track resolves to a Track with a placeholder instrument.
        assertEquals("Tempo", piece.tracks().get(0).name());
        assertEquals(Instrument.ACOUSTIC_GRAND_PIANO, piece.tracks().get(0).defaultInstrument());
    }

    // ── Homogeneity enforcement ───────────────────────────────────────

    @Test
    void rejectsSectionMissingDeclaredTrack() {
        var intro = Section.named("Intro")
                .duration(Duration.ofSixtyFourths(64))
                .timeSignature(TS)
                .track("Melody", oneBarMelody(C))
                .build();
        // Missing "Bass"

        var decls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", Instrument.ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Bass",   Instrument.ACOUSTIC_BASS)
        );

        var ex = assertThrows(IllegalArgumentException.class,
                () -> Piece.ofSections("X", "Y", KEY, TS, TEMPO, decls, List.of(intro)));
        assertTrue(ex.getMessage().contains("Intro"));
        assertTrue(ex.getMessage().contains("Bass"));
    }

    @Test
    void rejectsSectionWithExtraTrack() {
        var intro = Section.named("Intro")
                .duration(Duration.ofSixtyFourths(64))
                .timeSignature(TS)
                .track("Melody", oneBarMelody(C))
                .track("Piano",  oneBarMelody(C))   // not declared
                .build();

        var decls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", Instrument.ACOUSTIC_GRAND_PIANO)
        );

        var ex = assertThrows(IllegalArgumentException.class,
                () -> Piece.ofSections("X", "Y", KEY, TS, TEMPO, decls, List.of(intro)));
        assertTrue(ex.getMessage().contains("Piano"));
    }

    @Test
    void rejectsDuplicateTrackNameInDecls() {
        var decls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", Instrument.ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Melody", Instrument.ACOUSTIC_BASS)
        );

        var ex = assertThrows(IllegalArgumentException.class,
                () -> Piece.ofSections("X", "Y", KEY, TS, TEMPO, decls, List.of()));
        assertTrue(ex.getMessage().contains("Duplicate"));
        assertTrue(ex.getMessage().contains("Melody"));
    }

    // ── Flat constructor still works ──────────────────────────────────

    @Test
    void flatConstructorIsUnchanged() {
        // Ensure existing code that uses the flat Piece constructor is unaffected.
        Track t = Track.of("Melody", Instrument.ACOUSTIC_GRAND_PIANO,
                List.of(oneBarMelody(C)));
        Piece piece = new Piece("X", "Y", KEY, TS, TEMPO, List.of(t));
        assertEquals(1, piece.tracks().size());
        assertEquals("Melody", piece.tracks().get(0).name());
    }

    // ── Helper (avoid import in tests of tests) ───────────────────────
    private static final class VoidPhraseHelper {
        static music.notation.phrase.VoidPhrase bars(TimeSignature ts, int bars) {
            return music.notation.phrase.VoidPhrase.ofBars(ts, bars, attacca());
        }
    }
}
