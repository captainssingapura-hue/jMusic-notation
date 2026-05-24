package music.notation.performance;

import music.notation.expressivity.*;

import org.junit.jupiter.api.Test;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the drum-sentinel set emitted on write for {@link TrackKind#DRUM}
 * tracks: track-name suffix (when not already drum-named), Instrument Name
 * meta event {@code 0x04}, Bank Select MSB → GM drum bank (120), and a
 * default Program Change committing the bank when no {@link InstrumentControl}
 * is provided. Pitched tracks must remain untouched by the sentinel logic.
 *
 * <p>The sentinels exist to survive importers that don't honour MIDI channel
 * 10 = drums — e.g. ACE Studio, which otherwise imports drum tracks as
 * Piano. None of the sentinels are read back by {@link MidiCodec#fromMidi};
 * round-trip drum identification still flows entirely through channel 9.</p>
 */
class MidiCodecDrumSentinelTest {

    private static final int META_TRACK_NAME      = 0x03;
    private static final int META_INSTRUMENT_NAME = 0x04;
    private static final int DRUM_CHANNEL         = 9;
    private static final int CC_BANK_SELECT_MSB   = 0;
    private static final int DRUM_BANK_GM         = 120;

    // ── Track name decoration ─────────────────────────────────────────

    @Test
    void undecoratedDrumName_getsDrumsSuffix() {
        Performance p = perfWithDrums(new TrackId("kick room"));
        Sequence seq = readSequence(MidiCodec.toMidi(p));
        String name = findTrackName(seq, /* skip conductor */ 1);
        assertEquals("kick room (Drums)", name);
    }

    @Test
    void alreadyDrumNamedTrack_passesThroughUnchanged() {
        // "drum" / "percussion" / "kit" keywords (case-insensitive) suppress
        // the suffix so repeated round-trips don't accumulate it.
        assertNameUnchanged("Drums");
        assertNameUnchanged("drums");
        assertNameUnchanged("Percussion");
        assertNameUnchanged("Drum Kit");
        assertNameUnchanged("MY KIT");
    }

    @Test
    void pitchedTrackName_isNotDecorated() {
        Track pitched = new Track(new TrackId("lead"), TrackKind.PITCHED,
                List.of(new PitchedNote(0, 500, 60)));
        Performance p = new Performance(
                new Score(List.of(pitched)),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
        Sequence seq = readSequence(MidiCodec.toMidi(p));
        assertEquals("lead", findTrackName(seq, 1),
                "pitched tracks must not receive any drum suffix");
    }

    // ── Instrument Name meta event ────────────────────────────────────

    @Test
    void drumTrack_carriesInstrumentNameMeta() {
        Performance p = perfWithDrums(new TrackId("Drums"));
        Sequence seq = readSequence(MidiCodec.toMidi(p));
        String instrumentName = findInstrumentName(seq, 1);
        assertEquals("Drum Kit", instrumentName,
                "drum track must carry an Instrument Name meta event = \"Drum Kit\"");
    }

    @Test
    void pitchedTrack_hasNoInstrumentNameMeta() {
        Track pitched = new Track(new TrackId("lead"), TrackKind.PITCHED,
                List.of(new PitchedNote(0, 500, 60)));
        Performance p = new Performance(
                new Score(List.of(pitched)),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
        Sequence seq = readSequence(MidiCodec.toMidi(p));
        assertNull(findInstrumentName(seq, 1),
                "pitched tracks must not emit an Instrument Name meta event");
    }

    // ── Bank Select + Program Change ──────────────────────────────────

    @Test
    void drumTrack_emitsBankSelectMsbAtTickZero() {
        Performance p = perfWithDrums(new TrackId("Drums"));
        Sequence seq = readSequence(MidiCodec.toMidi(p));
        boolean found = findBankSelectMsb(seq, 1, DRUM_CHANNEL, DRUM_BANK_GM);
        assertTrue(found,
                "drum track must emit Bank Select MSB = 120 on channel 10 at tick 0");
    }

    @Test
    void drumTrackWithoutInstrumentControl_getsDefaultProgramChange() {
        // When the score has no Instrumentation entry for the drum track,
        // a default Program Change 0 is injected so the Bank Select MSB
        // commits. Otherwise the bank select dangles and may be ignored.
        Performance p = perfWithDrums(new TrackId("Drums"));
        assertTrue(p.instruments().byTrack().isEmpty(),
                "precondition: this fixture has no Instrumentation entries");
        Sequence seq = readSequence(MidiCodec.toMidi(p));
        boolean found = findProgramChange(seq, 1, DRUM_CHANNEL, 0);
        assertTrue(found,
                "drum track without explicit Instrumentation must still carry "
                        + "a default Program Change so Bank Select MSB takes effect");
    }

    @Test
    void drumTrackWithExplicitInstrument_preservesUserProgram() {
        Track drums = new Track(new TrackId("Drums"), TrackKind.DRUM,
                List.of(new DrumNote(0, 100, 36)));
        Performance p = new Performance(
                new Score(List.of(drums)),
                TempoTrack.empty(),
                Instrumentation.single(drums.id(), /*program=*/ 16),  // alt drum kit
                Articulations.empty());
        Sequence seq = readSequence(MidiCodec.toMidi(p));
        // User's program-change must be present; we don't add an extra default.
        assertTrue(findProgramChange(seq, 1, DRUM_CHANNEL, 16),
                "explicit user Program Change must be preserved on drum track");
        assertFalse(findProgramChange(seq, 1, DRUM_CHANNEL, 0),
                "no extra default Program Change when user supplied one");
    }

    @Test
    void pitchedTrack_emitsNoBankSelectMsb() {
        Track pitched = new Track(new TrackId("lead"), TrackKind.PITCHED,
                List.of(new PitchedNote(0, 500, 60)));
        Performance p = new Performance(
                new Score(List.of(pitched)),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
        Sequence seq = readSequence(MidiCodec.toMidi(p));
        // Channel 0 is where assignChannels puts the lone pitched track.
        assertFalse(findBankSelectMsb(seq, 1, /*channel=*/ 0, /*any value via 120*/ DRUM_BANK_GM),
                "pitched tracks must not carry the drum-bank Bank Select");
    }

    // ── Round-trip semantics ──────────────────────────────────────────

    @Test
    void roundTripStillIdentifiesDrumsByChannel() {
        // Sentinels are write-only; the reader uses channel 9 only.
        Performance p = perfWithDrums(new TrackId("Drums"));
        Performance back = MidiCodec.fromMidi(MidiCodec.toMidi(p));
        assertEquals(1, back.score().tracks().size());
        assertEquals(TrackKind.DRUM, back.score().tracks().get(0).kind());
    }

    @Test
    void roundTripIsIdempotentOnAlreadyDecoratedName() {
        // A track named "Drums" should pass through unchanged across many
        // round-trips — the decoration must not accumulate the suffix.
        Performance p = perfWithDrums(new TrackId("Drums"));
        for (int i = 0; i < 3; i++) {
            p = MidiCodec.fromMidi(MidiCodec.toMidi(p));
        }
        String finalName = p.score().tracks().get(0).id().name();
        assertEquals("drums", finalName.toLowerCase(java.util.Locale.ROOT),
                "drum-named tracks must not accumulate suffix across round-trips; "
                        + "the reader's drum-coalescing names the track \"drums\"");
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static void assertNameUnchanged(String original) {
        Performance p = perfWithDrums(new TrackId(original));
        Sequence seq = readSequence(MidiCodec.toMidi(p));
        assertEquals(original, findTrackName(seq, 1),
                "name containing a drum keyword must pass through unchanged: " + original);
    }

    private static Performance perfWithDrums(TrackId id) {
        Track drums = new Track(id, TrackKind.DRUM,
                List.of(new DrumNote(0, 100, 36),
                        new DrumNote(500, 100, 38)));
        return new Performance(
                new Score(List.of(drums)),
                TempoTrack.empty(), Instrumentation.empty(), Articulations.empty());
    }

    private static Sequence readSequence(byte[] bytes) {
        try {
            return MidiSystem.getSequence(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new AssertionError("failed to parse emitted MIDI", e);
        }
    }

    private static String findTrackName(Sequence seq, int trackIdx) {
        return findMetaText(seq, trackIdx, META_TRACK_NAME);
    }

    private static String findInstrumentName(Sequence seq, int trackIdx) {
        return findMetaText(seq, trackIdx, META_INSTRUMENT_NAME);
    }

    private static String findMetaText(Sequence seq, int trackIdx, int metaType) {
        javax.sound.midi.Track track = seq.getTracks()[trackIdx];
        for (int i = 0; i < track.size(); i++) {
            MidiEvent ev = track.get(i);
            if (ev.getMessage() instanceof MetaMessage meta && meta.getType() == metaType) {
                return new String(meta.getData(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static boolean findBankSelectMsb(Sequence seq, int trackIdx, int channel, int value) {
        javax.sound.midi.Track track = seq.getTracks()[trackIdx];
        for (int i = 0; i < track.size(); i++) {
            MidiEvent ev = track.get(i);
            if (ev.getMessage() instanceof ShortMessage sm
                    && sm.getCommand() == ShortMessage.CONTROL_CHANGE
                    && sm.getChannel() == channel
                    && sm.getData1() == CC_BANK_SELECT_MSB
                    && sm.getData2() == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean findProgramChange(Sequence seq, int trackIdx, int channel, int program) {
        javax.sound.midi.Track track = seq.getTracks()[trackIdx];
        for (int i = 0; i < track.size(); i++) {
            MidiEvent ev = track.get(i);
            if (ev.getMessage() instanceof ShortMessage sm
                    && sm.getCommand() == ShortMessage.PROGRAM_CHANGE
                    && sm.getChannel() == channel
                    && sm.getData1() == program) {
                return true;
            }
        }
        return false;
    }
}
