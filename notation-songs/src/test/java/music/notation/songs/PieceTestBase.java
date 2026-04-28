package music.notation.songs;

import music.notation.phrase.Bar;
import music.notation.phrase.GraceNote;
import music.notation.phrase.PaddingNode;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.PitchNode;
import music.notation.phrase.RestNode;
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
 * Base class for per-piece tests. Subclasses supply a
 * {@link PieceContentProvider} via {@link #provider()}; this class
 * validates the resulting {@link Piece} against a standard set of
 * structural invariants.
 *
 * <p>Phase 4d: assertions read the bar list (via {@link Track#bars()})
 * directly. The legacy phrase pattern-match path is gone.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PieceTestBase {

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
    void everyTrackHasAtLeastOneBar() {
        for (final Track track : piece.tracks()) {
            assertFalse(track.bars().isEmpty(),
                    "Track '" + track.name() + "' should have at least one bar");
        }
    }

    @Test
    protected void allTracksHaveSameDuration() {
        final List<Track> tracks = piece.tracks();
        final int[] durations = new int[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            durations[i] = tracks.get(i).bars().stream()
                    .mapToInt(Bar::expectedSixtyFourths)
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
    void everyBarMatchesTimeSignature() {
        final int barUnits = piece.timeSig().barSixtyFourths();
        for (final Track track : piece.tracks()) {
            final List<Bar> bars = track.bars();
            for (int bi = 0; bi < bars.size(); bi++) {
                final int idx = bi;
                final Bar bar = bars.get(idx);
                assertEquals(barUnits, bar.expectedSixtyFourths(),
                        () -> "Track '" + track.name() + "', bar " + (idx + 1)
                                + ": expected " + barUnits + " sixty-fourths, got "
                                + bar.expectedSixtyFourths());
            }
        }
    }

    @Test
    void allMidiNotesInRange() {
        for (final Track track : piece.tracks()) {
            for (final Bar bar : track.bars()) {
                for (final PhraseNode node : bar.nodes()) {
                    checkNodeMidi(track.name(), node);
                }
            }
            for (final Track auxTrack : track.auxTracks()) {
                for (final Bar bar : auxTrack.bars()) {
                    for (final PhraseNode node : bar.nodes()) {
                        checkNodeMidi(auxTrack.name(), node);
                    }
                }
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    static void checkNodeMidi(final String trackName, final PhraseNode node) {
        switch (node) {
            case PitchNode n -> {
                for (final Pitch p : n.pitches()) {
                    final int midi = MidiMapper.toMidiNote(p);
                    assertTrue(midi >= 0 && midi <= 127,
                            trackName + ": note MIDI " + midi + " out of range");
                }
                for (final GraceNote g : n.graceNotes()) {
                    final int midi = MidiMapper.toMidiNote(g.pitch());
                    assertTrue(midi >= 0 && midi <= 127,
                            trackName + ": grace note MIDI " + midi + " out of range");
                }
            }
            case PercussionNote pn -> {
                final int midi = pn.sound().midiNote();
                assertTrue(midi >= 0 && midi <= 127,
                        trackName + ": percussion MIDI " + midi + " out of range");
            }
            case RestNode r -> {}
            case PaddingNode p -> {}
            // Zero-duration markers and dropped legacy nodes — no MIDI to validate.
            default -> {}
        }
    }
}
