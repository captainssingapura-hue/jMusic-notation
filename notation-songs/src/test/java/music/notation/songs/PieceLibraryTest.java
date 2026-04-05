package music.notation.songs;

import music.notation.phrase.*;
import music.notation.play.MidiMapper;
import music.notation.pitch.Pitch;
import music.notation.structure.Piece;
import music.notation.structure.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PieceLibraryTest {

    static Stream<String> allTitles() {
        return PieceLibrary.titles().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTitles")
    void pieceConstructsSuccessfully(String title) {
        Piece piece = PieceLibrary.get(title);
        assertNotNull(piece, "Piece should not be null");
        assertFalse(piece.title().isBlank(), "Title should not be blank");
        assertFalse(piece.composer().isBlank(), "Composer should not be blank");
        assertFalse(piece.tracks().isEmpty(), "Piece should have at least one track");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTitles")
    void allTracksHaveSameDuration(String title) {
        Piece piece = PieceLibrary.get(title);
        List<Track> tracks = piece.tracks();

        int[] durations = new int[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            durations[i] = track.phrases().stream()
                    .mapToInt(Bar::phraseSixtyFourths)
                    .sum();
        }

        int expected = durations[0];
        for (int i = 1; i < durations.length; i++) {
            int idx = i;
            assertEquals(expected, durations[idx],
                    () -> "Track '" + tracks.get(idx).name()
                            + "' duration (" + durations[idx] + " sixty-fourths) differs from '"
                            + tracks.get(0).name() + "' (" + expected + " sixty-fourths)");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTitles")
    void allMidiNotesInRange(String title) {
        Piece piece = PieceLibrary.get(title);
        for (Track track : piece.tracks()) {
            for (Phrase phrase : track.phrases()) {
                checkMidiRange(track.name(), phrase);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTitles")
    void everyTrackHasAtLeastOnePhrase(String title) {
        Piece piece = PieceLibrary.get(title);
        for (Track track : piece.tracks()) {
            assertFalse(track.phrases().isEmpty(),
                    "Track '" + track.name() + "' should have at least one phrase");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allTitles")
    void everyPhraseDurationIsWholeNumberOfBars(String title) {
        Piece piece = PieceLibrary.get(title);
        int barUnits = piece.timeSig().barSixtyFourths();

        for (Track track : piece.tracks()) {
            for (int pi = 0; pi < track.phrases().size(); pi++) {
                int idx = pi;
                Phrase phrase = track.phrases().get(idx);
                int total = Bar.phraseSixtyFourths(phrase);
                int remainder = total % barUnits;
                assertEquals(0, remainder,
                        () -> "Track '" + track.name() + "', phrase " + (idx + 1)
                                + ": total " + total + " sixty-fourths is not a whole number of "
                                + barUnits + "-unit bars (remainder: " + remainder + ")");
            }
        }
    }

    @Test
    void libraryContainsExpectedPieces() {
        List<String> titles = PieceLibrary.titles();
        assertTrue(titles.size() >= 7, "Should have at least 7 pieces, got " + titles.size());
        assertTrue(titles.contains("Blue Lotus (蓝莲花)"));
        assertTrue(titles.contains("Pachelbel's Canon"));
        assertTrue(titles.contains("Two Tigers (两只老虎)"));
    }

    // --- helpers ---

    private static void checkMidiRange(String trackName, Phrase phrase) {
        switch (phrase) {
            case MelodicPhrase mp -> mp.nodes().forEach(n -> checkNodeMidi(trackName, n));
            case DrumPhrase dp -> dp.nodes().forEach(n -> checkNodeMidi(trackName, n));
            case ChordPhrase cp -> cp.chords().forEach(c -> {
                for (Pitch p : c.pitches()) {
                    int midi = MidiMapper.toMidiNote(p);
                    assertTrue(midi >= 0 && midi <= 127,
                            trackName + ": chord pitch MIDI " + midi + " out of range");
                }
            });
            case RestPhrase rp -> {} // no pitches
            case ShiftedPhrase sp -> checkMidiRange(trackName, sp.source()); // delegate to source
        }
    }

    private static void checkNodeMidi(String trackName, PhraseNode node) {
        switch (node) {
            case NoteNode n -> {
                for (Pitch p : n.pitches()) {
                    int midi = MidiMapper.toMidiNote(p);
                    assertTrue(midi >= 0 && midi <= 127,
                            trackName + ": note MIDI " + midi + " out of range");
                }
            }
            case GraceNote g -> {
                int midi = MidiMapper.toMidiNote(g.pitch());
                assertTrue(midi >= 0 && midi <= 127,
                        trackName + ": grace note MIDI " + midi + " out of range");
            }
            case PercussionNote pn -> {
                int midi = pn.sound().midiNote();
                assertTrue(midi >= 0 && midi <= 127,
                        trackName + ": percussion MIDI " + midi + " out of range");
            }
            case RestNode r -> {}
            case DynamicNode d -> {}
            case SlurStart s -> {}
            case SlurEnd s -> {}
            case SubPhrase sp -> checkMidiRange(trackName, sp.phrase());
        }
    }

}
