package music.notation.lyrics;

import music.notation.expressivity.Articulations;
import music.notation.expressivity.LyricEvent;
import music.notation.expressivity.LyricLine;
import music.notation.expressivity.Lyrics;
import music.notation.expressivity.Pedaling;
import music.notation.expressivity.TrackId;
import music.notation.expressivity.Velocities;
import music.notation.expressivity.Volume;
import music.notation.mxl.MxlImport;
import music.notation.performance.Instrumentation;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.TempoTrack;
import music.notation.performance.Track;
import music.notation.performance.TrackKind;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import music.notation.duration.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit checks for {@link LyricsModel} — the only piece of the editor
 * that's testable without spinning up JavaFX. Covers:
 * <ul>
 *   <li>Cells are built one-per-audible-onset, sorted by musical position.</li>
 *   <li>Existing {@link Lyrics} side-channel seeds the cells correctly.</li>
 *   <li>{@code setCellCodePoint} updates the version counter.</li>
 *   <li>{@code toLyricLine} skips empty cells and emits one event per
 *       glyph/continuation cell.</li>
 *   <li>{@code toSavableImport} folds the new line into the import's
 *       {@code Performance.lyrics()}.</li>
 * </ul>
 */
class LyricsModelTest {

    private static final TrackId MELODY = new TrackId("Melody");

    /** n quarter notes from the start. */
    private static Duration q(long n) { return Duration.of(n, 4); }

    private static MxlImport buildImport(LyricLine seedLine) {
        // Four quarter notes on consecutive beats, all C4.
        List<music.notation.performance.ConcreteNote> notes = List.of(
                new PitchedNote(q(0), q(1), 60),
                new PitchedNote(q(1), q(1), 60),
                new PitchedNote(q(2), q(1), 60),
                new PitchedNote(q(3), q(1), 60));
        Track melody = new Track(MELODY, TrackKind.PITCHED, notes);

        Lyrics lyrics = (seedLine == null || seedLine.events().isEmpty())
                ? Lyrics.empty()
                : Lyrics.single(MELODY, seedLine);

        Performance perf = new Performance(
                new Score(List.of(melody)),
                TempoTrack.constant(120),
                Instrumentation.empty(),
                Volume.empty(),
                Articulations.empty(),
                Pedaling.empty(),
                Velocities.empty(),
                lyrics);

        return new MxlImport(
                "test-piece", perf,
                new TimeSignature(4, 4),
                new KeySignature(NoteName.C, Accidental.NATURAL, Mode.MAJOR),
                "");
    }

    @Test
    void buildsOneCellPerAudibleOnset() {
        LyricsModel m = new LyricsModel(buildImport(null), null, MELODY);
        assertEquals(4, m.cellCount());
        // Cells in position order.
        for (int i = 0; i < 4; i++) {
            assertTrue(q(i).equalsDuration(m.cellAt(i).at));
            assertEquals(60, m.cellAt(i).midi);
            assertTrue(m.cellAt(i).isEmpty(), "no seed → all cells empty");
        }
    }

    @Test
    void existingLyricsSideChannel_seedsCells() {
        LyricLine seed = new LyricLine(List.of(
                new LyricEvent(q(0), '一'),
                new LyricEvent(q(1), '_'),
                new LyricEvent(q(2), '_'),
                new LyricEvent(q(3), '_')));
        LyricsModel m = new LyricsModel(buildImport(seed), null, MELODY);
        assertEquals("一", m.cellAt(0).character());
        assertTrue(m.cellAt(1).isContinuation());
        assertTrue(m.cellAt(2).isContinuation());
        assertTrue(m.cellAt(3).isContinuation());
    }

    @Test
    void setCellCodePoint_bumpsVersion() {
        LyricsModel m = new LyricsModel(buildImport(null), null, MELODY);
        int v0 = m.versionProperty().get();
        m.setCellCodePoint(0, 'a');
        assertEquals(v0 + 1, m.versionProperty().get());
        // No-op edit doesn't bump the version.
        m.setCellCodePoint(0, 'a');
        assertEquals(v0 + 1, m.versionProperty().get());
    }

    @Test
    void clearCellAndMarkContinuation_helpers() {
        LyricsModel m = new LyricsModel(buildImport(null), null, MELODY);
        m.setCellCodePoint(0, 'a');
        m.markContinuation(1);
        m.clearCell(2);
        assertEquals('a', m.cellAt(0).codePoint());
        assertTrue(m.cellAt(1).isContinuation());
        assertTrue(m.cellAt(2).isEmpty());
    }

    @Test
    void toLyricLine_skipsEmptyCells() {
        LyricsModel m = new LyricsModel(buildImport(null), null, MELODY);
        m.setCellCodePoint(0, '一');
        m.markContinuation(1);
        // index 2 stays empty
        m.setCellCodePoint(3, '二');

        LyricLine out = m.toLyricLine();
        assertEquals(3, out.events().size());
        assertTrue(q(0).equalsDuration(out.events().get(0).at()));
        assertEquals('一',  out.events().get(0).codePoint());
        assertTrue(q(1).equalsDuration(out.events().get(1).at()));
        assertEquals('_',   out.events().get(1).codePoint());
        assertTrue(q(3).equalsDuration(out.events().get(2).at()));
        assertEquals('二',  out.events().get(2).codePoint());
    }

    @Test
    void toSavableImport_foldsLineIntoLyricsSideChannel() {
        LyricsModel m = new LyricsModel(buildImport(null), null, MELODY);
        m.setCellCodePoint(0, 'h');
        m.setCellCodePoint(1, 'i');

        MxlImport saved = m.toSavableImport();
        Lyrics ly = saved.performance().lyrics();
        assertTrue(ly.byTrack().containsKey(MELODY));
        assertEquals(2, ly.byTrack().get(MELODY).events().size());
    }

    @Test
    void toSavableImport_dropsTrackWhenAllCellsEmpty() {
        // Start with a seed, then clear all cells. The savable import's
        // Lyrics should not list this track (canonical empty-line filter).
        LyricLine seed = new LyricLine(List.of(new LyricEvent(q(0), 'x')));
        LyricsModel m = new LyricsModel(buildImport(seed), null, MELODY);
        for (int i = 0; i < m.cellCount(); i++) m.clearCell(i);

        MxlImport saved = m.toSavableImport();
        assertTrue(saved.performance().lyrics().byTrack().isEmpty(),
                "all-empty cells → no track entry in Lyrics");
    }

    @Test
    void currentNoteIndex_clampsToValidRange() {
        LyricsModel m = new LyricsModel(buildImport(null), null, MELODY);
        m.setCurrentNoteIndex(-5);
        assertEquals(0, m.currentNoteIndex());
        m.setCurrentNoteIndex(1000);
        assertEquals(3, m.currentNoteIndex());
    }
}
