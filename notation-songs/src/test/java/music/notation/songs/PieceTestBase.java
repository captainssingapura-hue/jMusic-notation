package music.notation.songs;

import music.notation.phrase.*;
import music.notation.play.MidiMapper;
import music.notation.pitch.Pitch;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import music.notation.structure.Track;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for per-piece tests.  Subclasses supply a
 * {@link PieceContentProvider} via {@link #provider()}; this class
 * validates the resulting {@link Piece} against a standard set of
 * structural invariants.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PieceTestBase {

    /** Return the provider under test. */
    protected abstract PieceContentProvider<?> provider();

    private Piece piece;

    @BeforeAll
    void buildPiece() {
        piece = provider().create();
        assertNotNull(piece, "provider().create() must not return null");
    }

    // ── Structural checks ──────────────────────────────────────────

    @Test
    void pieceHasMetadata() {
        assertFalse(piece.title().isBlank(), "Title should not be blank");
        assertFalse(piece.composer().isBlank(), "Composer should not be blank");
    }

    @Test
    void pieceHasAtLeastOneTrack() {
        assertFalse(piece.tracks().isEmpty(), "Piece should have at least one track");
    }

    @Test
    void everyTrackHasAtLeastOnePhrase() {
        for (final Track track : piece.tracks()) {
            assertFalse(track.phrases().isEmpty(),
                    "Track '" + track.name() + "' should have at least one phrase");
        }
    }

    @Test
    void allTracksHaveSameDuration() {
        final List<Track> tracks = piece.tracks();
        final int[] durations = new int[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            durations[i] = tracks.get(i).phrases().stream()
                    .mapToInt(Bar::phraseSixtyFourths)
                    .sum();
        }

        final int expected = durations[0];
        for (int i = 1; i < durations.length; i++) {
            final int idx = i;
            assertEquals(expected, durations[idx],
                    () -> "Track '" + tracks.get(idx).name()
                            + "' duration (" + durations[idx] + " sixty-fourths) differs from '"
                            + tracks.get(0).name() + "' (" + expected + " sixty-fourths)");
        }
    }

    @Test
    void everyPhraseDurationIsWholeNumberOfBars() {
        final int barUnits = piece.timeSig().barSixtyFourths();
        for (final Track track : piece.tracks()) {
            for (int pi = 0; pi < track.phrases().size(); pi++) {
                final int idx = pi;
                final Phrase phrase = track.phrases().get(idx);
                final int total = Bar.phraseSixtyFourths(phrase);
                final int remainder = total % barUnits;
                assertEquals(0, remainder,
                        () -> "Track '" + track.name() + "', phrase " + (idx + 1)
                                + ": total " + total + " sixty-fourths is not a whole number of "
                                + barUnits + "-unit bars (remainder: " + remainder + ")");
            }
        }
    }

    @Test
    void allMidiNotesInRange() {
        for (final Track track : piece.tracks()) {
            for (final Phrase phrase : track.phrases()) {
                checkMidiRange(track.name(), phrase);
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    static void checkMidiRange(final String trackName, final Phrase phrase) {
        switch (phrase) {
            case MelodicPhrase mp -> mp.nodes().forEach(n -> checkNodeMidi(trackName, n));
            case DrumPhrase dp -> dp.nodes().forEach(n -> checkNodeMidi(trackName, n));
            case ChordPhrase cp -> cp.chords().forEach(c -> {
                for (final Pitch p : c.pitches()) {
                    final int midi = MidiMapper.toMidiNote(p);
                    assertTrue(midi >= 0 && midi <= 127,
                            trackName + ": chord pitch MIDI " + midi + " out of range");
                }
            });
            case RestPhrase rp -> {} // no pitches
            case ShiftedPhrase sp -> checkMidiRange(trackName, sp.source());
        }
    }

    static void checkNodeMidi(final String trackName, final PhraseNode node) {
        switch (node) {
            case NoteNode n -> {
                for (final Pitch p : n.pitches()) {
                    final int midi = MidiMapper.toMidiNote(p);
                    assertTrue(midi >= 0 && midi <= 127,
                            trackName + ": note MIDI " + midi + " out of range");
                }
            }
            case GraceNote g -> {
                final int midi = MidiMapper.toMidiNote(g.pitch());
                assertTrue(midi >= 0 && midi <= 127,
                        trackName + ": grace note MIDI " + midi + " out of range");
            }
            case PercussionNote pn -> {
                final int midi = pn.sound().midiNote();
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
