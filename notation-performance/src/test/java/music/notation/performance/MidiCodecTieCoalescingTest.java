package music.notation.performance;

import org.junit.jupiter.api.Test;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3d: codec-level tie coalescing.
 *
 * <p>Verifies that a {@link PitchedNote} with {@code tiedToNext == true},
 * followed on the same track by a same-pitch immediately-adjacent
 * PitchedNote, is emitted as a single sustained MIDI note (one NOTE_ON,
 * one NOTE_OFF) rather than two re-articulated notes. Tests cover the
 * happy path, broken-tie cases (chain falls back to per-note emission),
 * three-way chains, and DrumNote (never coalesced).</p>
 */
class MidiCodecTieCoalescingTest {

    private static final TrackId LEAD = new TrackId("lead");
    private static final TrackId DRUMS = new TrackId("drums");

    // ── Happy path ─────────────────────────────────────────────────

    @Test
    void twoTiedSamePitchNotesEmitOneSustainedNote() {
        // C4 quarter (tickMs=0..500, tied) → C4 quarter (500..1000) = sustained C4 0..1000
        var perf = singleTrackPerformance(
                new PitchedNote(0, 500, 60, true),
                new PitchedNote(500, 500, 60, false));

        var counts = countShortMessages(MidiCodec.toMidi(perf));
        assertEquals(1, counts.noteOn, "tied chain emits a single NOTE_ON");
        assertEquals(1, counts.noteOff, "tied chain emits a single NOTE_OFF");
    }

    @Test
    void threeWayChainEmitsOneSustainedNote() {
        var perf = singleTrackPerformance(
                new PitchedNote(0,    500, 60, true),
                new PitchedNote(500,  500, 60, true),
                new PitchedNote(1000, 500, 60, false));

        var counts = countShortMessages(MidiCodec.toMidi(perf));
        assertEquals(1, counts.noteOn);
        assertEquals(1, counts.noteOff);
    }

    @Test
    void noTiesEmitsOneNotePairPerNote() {
        // Three independent notes — three NOTE_ON / NOTE_OFF pairs.
        var perf = singleTrackPerformance(
                new PitchedNote(0,    500, 60, false),
                new PitchedNote(500,  500, 62, false),
                new PitchedNote(1000, 500, 64, false));

        var counts = countShortMessages(MidiCodec.toMidi(perf));
        assertEquals(3, counts.noteOn);
        assertEquals(3, counts.noteOff);
    }

    // ── Broken-tie cases ───────────────────────────────────────────

    @Test
    void tieWithMismatchedPitchEmitsAsTwoSeparateNotes() {
        // C4 tied → D4: pitch mismatch breaks the chain; emit as two notes.
        var perf = singleTrackPerformance(
                new PitchedNote(0, 500, 60, true),
                new PitchedNote(500, 500, 62, false));

        var counts = countShortMessages(MidiCodec.toMidi(perf));
        assertEquals(2, counts.noteOn, "broken tie => two NOTE_ONs");
        assertEquals(2, counts.noteOff);
    }

    @Test
    void tieWithGapEmitsAsTwoSeparateNotes() {
        // C4 ends at 500, next C4 starts at 600 — gap of 100ms breaks the chain.
        var perf = singleTrackPerformance(
                new PitchedNote(0, 500, 60, true),
                new PitchedNote(600, 500, 60, false));

        var counts = countShortMessages(MidiCodec.toMidi(perf));
        assertEquals(2, counts.noteOn);
        assertEquals(2, counts.noteOff);
    }

    @Test
    void tieAtEndOfTrackEmitsAsSingleNote() {
        // Last note has tiedToNext=true but there's no successor — emit alone.
        var perf = singleTrackPerformance(
                new PitchedNote(0, 500, 60, true));

        var counts = countShortMessages(MidiCodec.toMidi(perf));
        assertEquals(1, counts.noteOn);
        assertEquals(1, counts.noteOff);
    }

    @Test
    void chainPartiallyBreaksOnSecondPair() {
        // C(tied) → C(tied) → D — first pair coalesces, then D emits alone.
        var perf = singleTrackPerformance(
                new PitchedNote(0,    500, 60, true),
                new PitchedNote(500,  500, 60, true),
                new PitchedNote(1000, 500, 62, false));

        var counts = countShortMessages(MidiCodec.toMidi(perf));
        assertEquals(2, counts.noteOn, "C-chain coalesces, then D");
        assertEquals(2, counts.noteOff);
    }

    // ── Drum notes never coalesce ──────────────────────────────────

    @Test
    void drumNotesAreNeverCoalesced() {
        // Even if drum hits look adjacent, DrumNote doesn't implement Tieable.
        var bar1 = new DrumNote(0, 250, Drums.KICK);
        var bar2 = new DrumNote(250, 250, Drums.KICK);
        var perf = new Performance(
                new Score(List.of(new Track(DRUMS, TrackKind.DRUM, List.of(bar1, bar2)))),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());

        var counts = countShortMessages(MidiCodec.toMidi(perf));
        assertEquals(2, counts.noteOn, "drum hits stay separate");
        assertEquals(2, counts.noteOff);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private static Performance singleTrackPerformance(PitchedNote... notes) {
        return new Performance(
                new Score(List.of(
                        new Track(LEAD, TrackKind.PITCHED, List.of(notes)))),
                TempoTrack.empty(),
                Instrumentation.empty(),
                Articulations.empty());
    }

    private record MessageCounts(int noteOn, int noteOff) {}

    /** Counts NOTE_ON (vel > 0) and NOTE_OFF events across all MIDI tracks. */
    private static MessageCounts countShortMessages(byte[] midiBytes) {
        try {
            Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(midiBytes));
            int on = 0, off = 0;
            for (var track : seq.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    var msg = track.get(i).getMessage();
                    if (msg instanceof ShortMessage sm) {
                        if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                            on++;
                        } else if (sm.getCommand() == ShortMessage.NOTE_OFF
                                || (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                            off++;
                        }
                    } else if (msg instanceof MetaMessage) {
                        // ignore meta events (track name, tempo)
                    }
                }
            }
            return new MessageCounts(on, off);
        } catch (Exception e) {
            throw new RuntimeException("failed to parse MIDI bytes", e);
        }
    }
}
