package music.notation.play;

import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.event.PercussionSound;
import music.notation.expressivity.TrackId;
import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.phrase.Bar;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.PitchNode;
import music.notation.pitch.NoteName;
import music.notation.pitch.StaffPitch;
import music.notation.structure.DrumTrack;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
import music.notation.structure.Piece;
import music.notation.structure.Tempo;
import music.notation.structure.TimeSignature;
import music.notation.structure.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static music.notation.duration.BaseValue.QUARTER;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the per-track exclusion overload on {@link MidiPlayer#exportPiece}.
 *
 * <p>Each test builds a small multi-track {@link Piece}, exports it with
 * one or more tracks excluded, then re-imports the resulting MIDI via
 * {@link MidiCodec#fromMidi} to confirm the excluded track(s) produced
 * no events. The empty-exclusion case must behave identically to the
 * legacy single-arg overload.</p>
 */
class MidiPlayerExportFilterTest {

    private static final KeySignature C_MAJOR = new KeySignature(NoteName.C, Mode.MAJOR);
    private static final TimeSignature TS_4_4 = new TimeSignature(4, 4);
    private static final Tempo TEMPO_120 = new Tempo(120, QUARTER);

    private static MelodicTrack pitchedTrack(String name, NoteName note) {
        PhraseNode n = PitchNode.of(StaffPitch.of(note, 4), Duration.of(QUARTER));
        var bar = Bar.of(64, n, n, n, n);
        return MelodicTrack.of(name, Instrument.ACOUSTIC_GRAND_PIANO, bar);
    }

    private static DrumTrack drumTrack(String name) {
        PhraseNode hit = new PercussionNote(PercussionSound.BASS_DRUM, Duration.of(QUARTER));
        var bar = Bar.of(64, hit, hit, hit, hit);
        return DrumTrack.of(name, bar);
    }

    private static Piece pieceOf(Track... tracks) {
        return new Piece("Test", "x", C_MAJOR, TS_4_4, TEMPO_120, List.of(tracks));
    }

    private static ChannelSetup setupFor(Piece piece) {
        var instruments = new java.util.ArrayList<Instrument>();
        var volumes     = new java.util.ArrayList<Integer>();
        for (int i = 0; i < piece.tracks().size(); i++) {
            instruments.add(null);
            volumes.add(null);
        }
        return ChannelSetup.from(piece, instruments, volumes);
    }

    private static Performance readBack(File file) throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return MidiCodec.fromMidi(bytes);
    }

    // ── Tests ─────────────────────────────────────────────────────────

    @Test
    void emptyExclusionExportsAllTracks(@TempDir Path tmp) throws Exception {
        Piece piece = pieceOf(
                pitchedTrack("Lead",    NoteName.C),
                pitchedTrack("Harmony", NoteName.E),
                drumTrack("Drums"));
        File out = tmp.resolve("all.mid").toFile();

        MidiPlayer player = new MidiPlayer();
        player.exportPiece(piece, out, /*isAudio=*/ false, /*keepAuthentic=*/ true,
                setupFor(piece), null, Set.of());

        Performance back = readBack(out);
        // Three input tracks → 2 pitched lanes + 1 coalesced drum track.
        // (MidiCodec's read coalesces all channel-9 lanes into one DRUM Track.)
        assertEquals(3, back.score().tracks().size(),
                "empty exclusion must round-trip every input track");
    }

    @Test
    void excludingOnePitchedTrackDropsItFromExport(@TempDir Path tmp) throws Exception {
        Piece piece = pieceOf(
                pitchedTrack("Lead",    NoteName.C),
                pitchedTrack("Harmony", NoteName.E));
        File out = tmp.resolve("filtered.mid").toFile();

        MidiPlayer player = new MidiPlayer();
        player.exportPiece(piece, out, false, true, setupFor(piece), null,
                Set.of(new TrackId("Harmony")));

        Performance back = readBack(out);
        assertEquals(1, back.score().tracks().size(),
                "excluding one track must shrink the exported roster");
        assertEquals("Lead", back.score().tracks().get(0).id().name(),
                "the surviving track must be the one not excluded");
    }

    @Test
    void excludingDrumTrackProducesNoDrumLane(@TempDir Path tmp) throws Exception {
        Piece piece = pieceOf(
                pitchedTrack("Lead", NoteName.C),
                drumTrack("Drums"));
        File out = tmp.resolve("nodrum.mid").toFile();

        MidiPlayer player = new MidiPlayer();
        player.exportPiece(piece, out, false, true, setupFor(piece), null,
                Set.of(new TrackId("Drums")));

        Performance back = readBack(out);
        assertEquals(1, back.score().tracks().size());
        assertEquals("Lead", back.score().tracks().get(0).id().name());
        assertEquals(music.notation.performance.TrackKind.PITCHED,
                back.score().tracks().get(0).kind(),
                "the surviving track must be pitched (drum was excluded)");
    }

    @Test
    void excludingAllPitchedLeavesDrumOnly(@TempDir Path tmp) throws Exception {
        Piece piece = pieceOf(
                pitchedTrack("Lead",    NoteName.C),
                pitchedTrack("Harmony", NoteName.E),
                drumTrack("Drums"));
        File out = tmp.resolve("drumonly.mid").toFile();

        MidiPlayer player = new MidiPlayer();
        player.exportPiece(piece, out, false, true, setupFor(piece), null,
                Set.of(new TrackId("Lead"), new TrackId("Harmony")));

        Performance back = readBack(out);
        assertEquals(1, back.score().tracks().size());
        assertEquals(music.notation.performance.TrackKind.DRUM,
                back.score().tracks().get(0).kind(),
                "only the drum track should survive");
    }

    @Test
    void unknownTrackIdInExclusionIsNoop(@TempDir Path tmp) throws Exception {
        // Excluding a non-existent track ID must not silently drop any
        // real track. Safety net for stale UI state.
        Piece piece = pieceOf(
                pitchedTrack("Lead",    NoteName.C),
                pitchedTrack("Harmony", NoteName.E));
        File out = tmp.resolve("noop.mid").toFile();

        MidiPlayer player = new MidiPlayer();
        player.exportPiece(piece, out, false, true, setupFor(piece), null,
                Set.of(new TrackId("Ghost")));   // doesn't exist

        Performance back = readBack(out);
        assertEquals(2, back.score().tracks().size(),
                "unknown exclusion key must not drop real tracks");
    }

    @Test
    void excludedTrackWithPopulatedSideChannels_isClean(@TempDir Path tmp) throws Exception {
        // Regression: the early filterTracks implementation called
        // perf.withScore(newScore) without filtering side channels, leaving
        // Instrumentation / Pedaling / Velocities entries that referenced
        // dropped tracks. The Performance constructor rejects that as
        // "instruments references tracks not in score: [Excluded]".
        //
        // Real-world pieces (and any MXL import) carry per-track Instrumentation
        // entries, so the bug surfaced immediately in the app even though the
        // earlier filter tests (with empty side channels) all passed.
        Piece piece = pieceOf(
                pitchedTrack("Lead",    NoteName.C),
                pitchedTrack("Excluded", NoteName.E));
        File out = tmp.resolve("excluded.mid").toFile();

        MidiPlayer player = new MidiPlayer();
        // Provide an explicit ChannelSetup with programs assigned per track;
        // this is what causes PieceConcretizer to populate the Instrumentation
        // side channel for both tracks. The excluded track's Instrumentation
        // entry must be dropped along with the track.
        assertDoesNotThrow(() ->
                player.exportPiece(piece, out, /*isAudio=*/ false, /*keepAuthentic=*/ false,
                        setupFor(piece), null,
                        Set.of(new TrackId("Excluded"))),
                "exporting with an excluded track whose side channels are populated must not throw");

        Performance back = readBack(out);
        assertEquals(1, back.score().tracks().size());
        assertEquals("Lead", back.score().tracks().get(0).id().name());
    }

    @Test
    void legacyOverloadStillExportsAllTracks(@TempDir Path tmp) throws Exception {
        // The 6-arg overload (no excludedTracks) must behave identically
        // to the 7-arg overload called with Set.of().
        Piece piece = pieceOf(
                pitchedTrack("Lead",    NoteName.C),
                pitchedTrack("Harmony", NoteName.E));
        File outLegacy   = tmp.resolve("legacy.mid").toFile();
        File outExplicit = tmp.resolve("explicit.mid").toFile();

        MidiPlayer player = new MidiPlayer();
        player.exportPiece(piece, outLegacy,   false, true, setupFor(piece), null);
        player.exportPiece(piece, outExplicit, false, true, setupFor(piece), null, Set.of());

        byte[] a = Files.readAllBytes(outLegacy.toPath());
        byte[] b = Files.readAllBytes(outExplicit.toPath());
        assertArrayEquals(a, b,
                "legacy overload must emit byte-identical output to empty-set explicit overload");
    }
}
