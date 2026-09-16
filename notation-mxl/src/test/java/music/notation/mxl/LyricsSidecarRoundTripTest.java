package music.notation.mxl;

import music.notation.expressivity.LyricEvent;
import music.notation.expressivity.LyricLine;
import music.notation.expressivity.Lyrics;
import music.notation.expressivity.TrackId;
import music.notation.expressivity.Articulations;
import music.notation.performance.Instrumentation;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.TempoTrack;
import music.notation.performance.Track;
import music.notation.performance.TrackKind;
import music.notation.performance.Performance;
import music.notation.expressivity.Pedaling;
import music.notation.expressivity.Velocities;
import music.notation.expressivity.Volume;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1.1 contract: {@code lyrics.json} sidecar round-trips through
 * {@link MxlSplitJsonWriter} / {@link MxlSplitJsonReader} losslessly.
 * Built from a hand-constructed {@link MxlImport} so the test is
 * independent of any specific MXL fixture.
 */
class LyricsSidecarRoundTripTest {

    private static MxlImport buildImport(Lyrics lyrics) {
        TrackId melodyId = new TrackId("Melody");
        // Four quarter notes, C4 (MIDI 60), each lasting 500 ms at 120 bpm.
        List<music.notation.performance.ConcreteNote> notes = List.of(
                new PitchedNote(    0L, 500L, 60),
                new PitchedNote(  500L, 500L, 60),
                new PitchedNote( 1000L, 500L, 60),
                new PitchedNote( 1500L, 500L, 60));
        Track melody = new Track(melodyId, TrackKind.PITCHED, notes);

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
                "lyrics-roundtrip-fixture",
                perf,
                new TimeSignature(4, 4),
                new KeySignature(NoteName.C, Accidental.NATURAL, Mode.MAJOR),
                "");
    }

    @Test
    void lyricsSidecar_writesFile_andRoundTrips(@TempDir Path tmp) throws Exception {
        TrackId melodyId = new TrackId("Melody");
        // 一 _ _ _ — single CJK syllable held for four quarter notes.
        LyricLine line = new LyricLine(List.of(
                new LyricEvent(    0, '一'),
                LyricEvent.continuation( 500),
                LyricEvent.continuation(1000),
                LyricEvent.continuation(1500)));
        Lyrics lyrics = Lyrics.single(melodyId, line);

        MxlImport written = buildImport(lyrics);
        Path pieceDir = tmp.resolve("piece");
        MxlSplitJsonWriter.write(written, pieceDir);

        // The sidecar must exist on disk and be non-trivial.
        Path sidecar = pieceDir.resolve("lyrics.json");
        assertTrue(Files.exists(sidecar), "lyrics.json should be written when non-empty");
        String text = Files.readString(sidecar);
        assertTrue(text.contains("Melody"), "sidecar should reference the track name");
        assertTrue(text.contains("19968"),  // codepoint of 一 = U+4E00 = 19968
                "sidecar should encode the CJK glyph by codepoint");
        assertTrue(text.contains("95"),     // codepoint of '_' = 95
                "sidecar should encode the continuation marker");

        MxlImport reloaded = MxlSplitJsonReader.read(pieceDir);
        assertEquals(written.performance().lyrics(), reloaded.performance().lyrics(),
                "lyrics side-channel must round-trip losslessly");
    }

    @Test
    void emptyLyrics_doesNotWriteSidecar(@TempDir Path tmp) throws Exception {
        MxlImport written = buildImport(Lyrics.empty());
        Path pieceDir = tmp.resolve("piece");
        MxlSplitJsonWriter.write(written, pieceDir);

        assertFalse(Files.exists(pieceDir.resolve("lyrics.json")),
                "empty Lyrics → no sidecar (keeps the folder uncluttered)");

        MxlImport reloaded = MxlSplitJsonReader.read(pieceDir);
        assertTrue(reloaded.performance().lyrics().byTrack().isEmpty(),
                "missing sidecar → Lyrics.empty() on read");
    }

    @Test
    void sidecarSurvivesNonBmpGlyph(@TempDir Path tmp) throws Exception {
        // 🎵 (U+1F3B5) — non-BMP. Code point fits in int but needs a
        // surrogate pair as a Java String. Sanity-check that the
        // codepoint integer survives the JSON round-trip.
        TrackId melodyId = new TrackId("Melody");
        LyricLine line = new LyricLine(List.of(new LyricEvent(0, 0x1F3B5)));
        MxlImport written = buildImport(Lyrics.single(melodyId, line));

        Path pieceDir = tmp.resolve("piece");
        MxlSplitJsonWriter.write(written, pieceDir);
        MxlImport reloaded = MxlSplitJsonReader.read(pieceDir);

        LyricLine reloadedLine =
                reloaded.performance().lyrics().byTrack().get(melodyId);
        assertEquals(1, reloadedLine.events().size());
        assertEquals(0x1F3B5, reloadedLine.events().get(0).codePoint());
        assertEquals("🎵", reloadedLine.events().get(0).character());
    }
}
