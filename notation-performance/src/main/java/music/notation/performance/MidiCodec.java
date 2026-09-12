package music.notation.performance;

import music.notation.expressivity.*;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bidirectional bridge between {@link Performance} and MIDI bytes.
 *
 * <h2>Design stance: import is lossy by design</h2>
 *
 * <p>This codec is <b>not</b> a faithful playback engine. External MIDI is
 * treated as a <em>source of raw musical material</em>, not a recording to
 * reproduce. On {@link #fromMidi read}, the codec extracts the structural
 * skeleton — pitches, onsets, durations, instrument assignments, tempo map,
 * track structure — and discards the performance fingerprint: per-note
 * velocity dynamics, articulation hints, control-change curves, pitch
 * bend, channel pressure, polyphonic aftertouch, and system messages.</p>
 *
 * <p>The intent is to capture what the music <em>is</em> and let the
 * composer apply their own creativity on top via Performance →
 * Performance transformers ({@link Swing}, future {@code Dynamics},
 * future {@code Quantize}). Two MIDI recordings of the same piece by
 * different performers — different velocities, different microtiming —
 * import to <em>equal</em> {@link Performance}s if their note structure
 * matches. That equivalence is the point.</p>
 *
 * <p>See {@code .docs/microtiming.md} for the full discussion of why
 * the model is symbolic rather than performance-faithful, and what
 * follows from that choice (transformers, quantisation, the export-time
 * articulator).</p>
 *
 * <h2>What round-trips losslessly</h2>
 *
 * <ul>
 *   <li>{@link Score} — track roster, every {@link PitchedNote} and
 *       {@link DrumNote}, including {@link TrackId} names (via the MIDI
 *       Track Name meta event {@code 0x03}).</li>
 *   <li>{@link TempoTrack} — every {@link TempoChange} (MIDI tempo meta
 *       event {@code 0x51}).</li>
 *   <li>{@link Instrumentation} — every {@link InstrumentChange} per
 *       track (MIDI Program Change events).</li>
 * </ul>
 *
 * <p>Formally: {@code fromMidi(toMidi(p)).equals(p)} holds for any valid
 * {@code p} whose {@link Articulations} is empty <em>and</em> whose
 * {@link PitchedNote#tiedToNext()} flags are all false. Tied chains
 * coalesce to a single sounding note on write (see <i>Tie coalescing</i>
 * below); the per-note tie flag itself is not recoverable from MIDI
 * bytes alone, so it clears across a write/read round-trip.</p>
 *
 * <h2>Tie coalescing on write</h2>
 *
 * <p>A {@link PitchedNote} flagged with {@code tiedToNext == true} is
 * fused with its successor on the same track at MIDI emission time:
 * one NOTE_ON at the chain's start, one NOTE_OFF at the chain's end.
 * Coalescing requires the next note to be a same-pitch, immediately-
 * following PitchedNote (gapless). Chains of any length collapse into
 * a single sustained note; broken ties (different pitch, gap, or end of
 * track) are silently emitted as separate notes — the flag is preserved
 * in the model regardless. {@link DrumNote} is never coalesced.</p>
 *
 * <h2>What is dropped</h2>
 *
 * <ul>
 *   <li>{@link Articulations} — modelled as authoring intent, but the
 *       codec writes nothing for it and recovers nothing. A future
 *       export-time articulator may interpret these into note-off /
 *       velocity tweaks behind an explicit opt-in.</li>
 *   <li>Per-note velocity is now a side-channel (see {@link Velocities}).
 *       NOTE_ON velocities are emitted from {@code Performance.velocities()},
 *       defaulting to {@link VelocityControl#DEFAULT_VELOCITY} when no entry
 *       covers the note. {@link #fromMidi} back-fills {@link Velocities} from
 *       observed velocities — same audible result, denser shape.</li>
 *   <li>The {@code tiedToNext} flag — not recoverable on read; MIDI has
 *       no representation for "these two physical events are
 *       conceptually tied," only the coalesced sounding note.</li>
 *   <li>Control-change events, pitch-bend, channel pressure, polyphonic
 *       aftertouch, and system messages — silently dropped on read.</li>
 * </ul>
 *
 * <h2>Output format</h2>
 *
 * <p>{@link #toMidi write} emits a MIDI Type 1 file at PPQ 480, with
 * track 0 as the conductor (tempo meta events only) and one MIDI track
 * per {@link Track} in the score (drum sinks last, MIDI channel 9 by
 * convention; pitched tracks fill channels 0–8 then 10–15). Each MIDI
 * track carries a Track Name meta event so {@link TrackId}s round-trip.</p>
 *
 * <h2>Drum sentinels</h2>
 *
 * <p>Some downstream tools — notably vocal-synth editors like ACE Studio —
 * ignore the channel-10 = drums MIDI convention and import drum tracks as
 * Piano. To survive those importers, drum tracks carry a stacked sentinel
 * set in addition to the channel-9 placement:</p>
 *
 * <ul>
 *   <li><b>Track-name suffix.</b> If the track name doesn't already contain
 *       "drum" / "percussion" / "kit" (case-insensitive), {@code " (Drums)"}
 *       is appended on write. Already-decorated names pass through.</li>
 *   <li><b>Instrument Name meta event (0x04).</b> Always emitted as
 *       {@code "Drum Kit"} on drum tracks at tick 0.</li>
 *   <li><b>Bank Select MSB → 120</b> (GM drum bank) at tick 0, followed by
 *       a default Program Change to commit it. If the score already supplies
 *       Program Changes for the drum track, those run after the bank select
 *       and inherit the drum-bank setting.</li>
 * </ul>
 *
 * <p>None of the sentinels are read on {@link #fromMidi}: drum identification
 * stays driven by channel 9 alone, matching the simple write/read contract.
 * Track-name decoration is the only sentinel that survives a round-trip
 * (visibly, as a name suffix); the others are silently dropped.</p>
 *
 * <p>{@link #fromMidi read} accepts both Type 0 (single track, demuxed
 * by channel) and Type 1 (multi-track) inputs. Each (MIDI track, channel)
 * lane with notes or program changes becomes one {@link Track};
 * channel 9 maps to {@link TrackKind#DRUM}, all others to
 * {@link TrackKind#PITCHED}.</p>
 */
public final class MidiCodec {

    /** Resolution used on write; any value divisible by 4 works cleanly. */
    private static final int PPQ = 480;
    /** Default bpm used for tempo-map calculations when no tempo events exist. */
    private static final int DEFAULT_BPM = 120;
    private static final int MIDI_FILE_TYPE_MULTI_TRACK = 1;
    private static final int DRUM_CHANNEL = 9;
    private static final int META_TEMPO = 0x51;
    private static final int META_TRACK_NAME = 0x03;
    private static final int META_INSTRUMENT_NAME = 0x04;
    private static final int META_TIME_SIGNATURE = 0x58;
    private static final int META_KEY_SIGNATURE = 0x59;

    // ── Drum sentinel set (see “Drum sentinels” in the class javadoc) ──
    private static final int CC_BANK_SELECT_MSB = 0;
    /** GM drum bank — read by GM/GM2/GS importers that don't honour channel 10. */
    private static final int DRUM_BANK_GM = 120;
    /** Sentinel instrument-name meta event payload for drum tracks. */
    private static final String DRUM_INSTRUMENT_NAME = "Drum Kit";
    /** Suffix appended to a drum track's name when it doesn't already say so. */
    private static final String DRUM_NAME_SUFFIX = " (Drums)";

    private MidiCodec() {}

    // ═══════════════════════════════════════════════════════════════════
    //  WRITE: Performance  →  MIDI bytes
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Encode a {@link Performance} as a list of MIDI byte arrays — one per
     * synth slot in the multi-synth fan-out scheme.
     *
     * <p>Phase 1: always returns a singleton list. Every track lives on
     * synth 0 and the byte[] is identical to what {@link #toMidi(Performance)}
     * produces. The List wrapper is the only structural change — preparation
     * for Phase 2's actual split.</p>
     *
     * <p>Phase 2 (queued): {@link #assignChannels(List)} starts producing
     * {@link ChannelAddr} addresses. Tracks are partitioned by their
     * {@code synth} index; this method emits one byte[] per partition. The
     * order matches {@link ChannelAddr#synth()} (0 = SOURCE_PRIMARY,
     * 1 = AUTO, 2 = SOURCE_OVERFLOW). Empty partitions are omitted.</p>
     *
     * <p>For Phase-1 callers wanting the legacy single-byte[] shape, use
     * {@link #toMidi(Performance)} which asserts {@code size == 1}.</p>
     */
    public static List<byte[]> toMidiSplit(Performance p) {
        // Flatten hairpins to dense Velocities/Volume samples up front
        // so the codec proper never sees the Hairpins side-channel. The
        // resolver is a no-op when hairpins is empty.
        Performance flat = HairpinResolver.flatten(p);
        // Phase 1: singleton. Phase 2 will partition by ChannelAddr.synth().
        return List.of(toMidiInternal(flat));
    }

    /**
     * Legacy single-byte[] entrypoint. Behaviourally identical to today's
     * encode path. Internally delegates to {@link #toMidiSplit(Performance)}
     * and unwraps the singleton.
     *
     * <p>Phase 1: always works (every Performance fits one synth). Phase 2:
     * throws {@link IllegalStateException} when the Performance requires more
     * than one synth — callers in that situation must migrate to
     * {@link #toMidiSplit(Performance)}.</p>
     */
    public static byte[] toMidi(Performance p) {
        List<byte[]> split = toMidiSplit(p);
        if (split.size() != 1) {
            throw new IllegalStateException(
                    "Legacy toMidi() called on a Performance that requires "
                            + split.size() + " synths; migrate caller to toMidiSplit()");
        }
        return split.get(0);
    }

    /**
     * The actual codec body — produces a single MIDI byte[] from the
     * Performance using the legacy single-synth channel allocation.
     * Will be split into per-synth invocations during Phase 2.
     */
    private static byte[] toMidiInternal(Performance p) {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, PPQ);

            // Track 0 — conductor: tempo meta events only.
            javax.sound.midi.Track conductor = sequence.createTrack();
            for (TempoChange tc : p.tempo().changes()) {
                addTempoMeta(conductor, tc);
            }

            List<Track> scoreTracks = p.score().tracks();
            Map<TrackId, Integer> channelByTrack = assignChannels(scoreTracks);

            for (Track t : scoreTracks) {
                javax.sound.midi.Track mt = sequence.createTrack();
                int channel = channelByTrack.get(t.id());
                boolean isDrum = t.kind() == TrackKind.DRUM;

                addTrackName(mt, decorateForDrum(t.id().name(), isDrum));

                if (isDrum) {
                    // Sentinel set for vocal-synth and other tools that don't
                    // honour channel 10 = drums (e.g. ACE Studio imports drum
                    // tracks as Piano). Stack three hints: instrument-name
                    // meta event, Bank Select MSB → GM drum bank, and a
                    // default Program Change that commits the bank select.
                    addInstrumentName(mt, DRUM_INSTRUMENT_NAME);
                    addBankSelectMsb(mt, channel, DRUM_BANK_GM, 0L);
                }

                InstrumentControl ic = p.instruments().byTrack().get(t.id());
                if (ic != null) {
                    for (InstrumentChange change : ic.changes()) {
                        addProgramChange(mt, change, channel);
                    }
                } else if (isDrum) {
                    // Commit the Bank Select MSB by emitting a default
                    // Program Change. Standard kit = program 0 on channel 10.
                    addProgramChange(mt,
                            new InstrumentChange(music.notation.duration.Duration.zero(), 0),
                            channel);
                }

                VolumeControl vc = p.volume().byTrack().get(t.id());
                if (vc != null) {
                    for (VolumeChange change : vc.changes()) {
                        addVolumeChange(mt, change, channel);
                    }
                }

                PedalControl pc = p.pedaling().byTrack().get(t.id());
                if (pc != null) {
                    for (PedalChange change : pc.changes()) {
                        addPedalChange(mt, change, channel);
                    }
                }

                VelocityControl velocityCtrl = p.velocities().byTrack().get(t.id());
                emitNotes(mt, t.notes(), channel, velocityCtrl);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MidiSystem.write(sequence, MIDI_FILE_TYPE_MULTI_TRACK, baos);
            return baos.toByteArray();
        } catch (InvalidMidiDataException | IOException e) {
            throw new IllegalStateException("toMidi failed", e);
        }
    }

    private static Map<TrackId, Integer> assignChannels(List<Track> tracks) {
        Map<TrackId, Integer> result = new LinkedHashMap<>();
        int next = 0;
        int pitchedCount = 0;
        for (Track t : tracks) {
            if (t.kind() == TrackKind.DRUM) {
                result.put(t.id(), DRUM_CHANNEL);
            } else {
                if (next == DRUM_CHANNEL) next++;
                if (next > 15) {
                    throw new IllegalStateException(
                            "too many pitched tracks: max 15, got " + (pitchedCount + 1));
                }
                result.put(t.id(), next);
                next++;
                pitchedCount++;
            }
        }
        return result;
    }

    /**
     * Convert a musical {@link music.notation.duration.Duration} position
     * to MIDI ticks at {@link #PPQ} ticks-per-quarter. Tempo plays no
     * role — in the new model, MIDI ticks <em>are</em> musical positions
     * scaled by PPQ × 4 (ticks per whole note).
     */
    static long ticksFor(music.notation.duration.Duration at) {
        // ticks = at.num × PPQ × 4 / at.den
        long ticksPerWhole = (long) PPQ * 4L;
        return Math.multiplyExact(at.numerator(), ticksPerWhole) / at.denominator();
    }

    /** Inverse of {@link #ticksFor} — exact rational; no Math.round. */
    static music.notation.duration.Duration durationOfTicks(long midiTicks) {
        return music.notation.duration.Duration.of(midiTicks, (long) PPQ * 4L);
    }

    private static void addTempoMeta(javax.sound.midi.Track track, TempoChange t)
            throws InvalidMidiDataException {
        long midiTick = ticksFor(t.at());
        int usPerQuarter = 60_000_000 / t.bpm();
        byte[] data = new byte[]{
                (byte) ((usPerQuarter >> 16) & 0xFF),
                (byte) ((usPerQuarter >>  8) & 0xFF),
                (byte) ( usPerQuarter        & 0xFF),
        };
        MetaMessage msg = new MetaMessage();
        msg.setMessage(META_TEMPO, data, data.length);
        track.add(new MidiEvent(msg, midiTick));
    }

    private static void addTrackName(javax.sound.midi.Track track, String name)
            throws InvalidMidiDataException {
        byte[] data = name.getBytes(StandardCharsets.UTF_8);
        MetaMessage msg = new MetaMessage();
        msg.setMessage(META_TRACK_NAME, data, data.length);
        track.add(new MidiEvent(msg, 0L));
    }

    /**
     * Append a drum-sentinel suffix to a track name when the track is a
     * drum track <em>and</em> the existing name doesn't already contain a
     * drum-recognisable keyword. The track-name meta event is the primary
     * hint to importers that don't honour channel 10 = drums.
     *
     * <p>Pitched tracks pass through unchanged. Drum tracks already named
     * "Drums" / "drum kit" / "percussion" (case-insensitive) also pass
     * through, so already-decorated names don't accumulate the suffix
     * across round-trips.</p>
     */
    private static String decorateForDrum(String name, boolean isDrum) {
        if (!isDrum) return name;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("drum") || lower.contains("percussion") || lower.contains("kit")) {
            return name;
        }
        return name + DRUM_NAME_SUFFIX;
    }

    /**
     * Emit a MIDI Instrument Name meta event (0x04) at tick 0. Used as one
     * of the drum sentinels — some importers read this in preference to the
     * track-name meta event.
     */
    private static void addInstrumentName(javax.sound.midi.Track track, String name)
            throws InvalidMidiDataException {
        byte[] data = name.getBytes(StandardCharsets.UTF_8);
        MetaMessage msg = new MetaMessage();
        msg.setMessage(META_INSTRUMENT_NAME, data, data.length);
        track.add(new MidiEvent(msg, 0L));
    }

    /**
     * Emit a Bank Select MSB control change (CC #0). Used to point a drum
     * track at the GM drum bank (120) so importers that <em>do</em> honour
     * bank selection but ignore channel 10 still land on a drum kit.
     */
    private static void addBankSelectMsb(javax.sound.midi.Track track, int channel,
                                         int bankValue, long midiTick)
            throws InvalidMidiDataException {
        track.add(controlChange(channel, CC_BANK_SELECT_MSB, bankValue, midiTick));
    }

    private static void addProgramChange(javax.sound.midi.Track track, InstrumentChange change,
                                         int channel)
            throws InvalidMidiDataException {
        long midiTick = ticksFor(change.at());
        ShortMessage msg = new ShortMessage();
        msg.setMessage(ShortMessage.PROGRAM_CHANGE, channel, change.program(), 0);
        track.add(new MidiEvent(msg, midiTick));
    }

    /**
     * Emit a MIDI Channel Volume control change (CC #7) for a single
     * {@link VolumeChange} entry. Per the import doctrine, CC events are
     * silently dropped on read — so volume is a write-only side-channel
     * over the codec boundary.
     */
    private static void addVolumeChange(javax.sound.midi.Track track, VolumeChange change,
                                        int channel)
            throws InvalidMidiDataException {
        long midiTick = ticksFor(change.at());
        // Boundary translation: synth-agnostic [0,1] level → CC #7 byte [0,127].
        int cc7 = Math.max(0, Math.min(127, (int) Math.round(change.level() * 127.0)));
        ShortMessage msg = new ShortMessage();
        msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, /*CC #7=*/ 7, cc7);
        track.add(new MidiEvent(msg, midiTick));
    }

    /**
     * Emit a MIDI Damper / Sustain Pedal control change (CC #64) for a
     * single {@link PedalChange}. Mapping:
     * <ul>
     *   <li>{@link PedalState#DOWN}   → CC #64 = 127</li>
     *   <li>{@link PedalState#UP}     → CC #64 = 0</li>
     *   <li>{@link PedalState#CHANGE} → CC #64 = 0, then = 127, on adjacent ticks</li>
     * </ul>
     * Per the import doctrine, CC events are silently dropped on read
     * — pedal is a write-only side-channel over the codec boundary.
     */
    private static void addPedalChange(javax.sound.midi.Track track, PedalChange change,
                                       int channel)
            throws InvalidMidiDataException {
        long midiTick = ticksFor(change.at());
        if (change.state() == PedalState.CHANGE) {
            // Quick release+press: emit two events on adjacent ticks so the
            // dampers fully clear before re-engaging. Adjacent-tick spacing
            // is unconditional now (the old "tickMs + 1 → ms-tick rounding
            // hack" is gone — we work directly in ticks, so +1 always means
            // exactly one tick later, regardless of tempo).
            long releaseTick = midiTick;
            long pressTick   = midiTick + 1;
            track.add(controlChange(channel, /*CC #64=*/ 64, 0,   releaseTick));
            track.add(controlChange(channel, /*CC #64=*/ 64, 127, pressTick));
            return;
        }
        int level = (change.state() == PedalState.DOWN) ? 127 : 0;
        track.add(controlChange(channel, 64, level, midiTick));
    }

    private static MidiEvent controlChange(int channel, int controller, int value, long tick)
            throws InvalidMidiDataException {
        ShortMessage msg = new ShortMessage();
        msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, controller, value);
        return new MidiEvent(msg, tick);
    }

    private static void addNotePair(javax.sound.midi.Track track,
                                    music.notation.duration.Duration at,
                                    music.notation.duration.Duration duration,
                                    int pitch, int channel, int velocity)
            throws InvalidMidiDataException {
        long onTick  = ticksFor(at);
        long offTick = ticksFor(at.plus(duration));

        ShortMessage on = new ShortMessage();
        on.setMessage(ShortMessage.NOTE_ON, channel, pitch, velocity);
        track.add(new MidiEvent(on, onTick));

        ShortMessage off = new ShortMessage();
        off.setMessage(ShortMessage.NOTE_OFF, channel, pitch, 0);
        track.add(new MidiEvent(off, offTick));
    }

    /**
     * Emit a track's notes with tie coalescing. A {@link PitchedNote} with
     * {@code tiedToNext == true} is fused into its successor on the same
     * track at MIDI emission time: only one NOTE_ON at the chain's start
     * and one NOTE_OFF at the chain's end are written, producing a single
     * sustained sounding note instead of separate re-articulated notes.
     *
     * <p>Coalescing requires the next note to be a same-pitch, same-track,
     * immediately-following {@link PitchedNote} (gapless: its onset tick
     * equals the current note's off-tick). If the chain breaks (different
     * pitch, gap, drum note, end of track), the tie flag is silently
     * ignored for that boundary and the next note is emitted independently.
     * Chains of three or more PitchedNotes are coalesced into a single
     * NOTE_ON / NOTE_OFF spanning the whole chain.</p>
     *
     * <p>{@link DrumNote} doesn't implement {@link Tieable} and is always
     * emitted as a single NOTE_ON / NOTE_OFF pair.</p>
     */
    private static void emitNotes(javax.sound.midi.Track track, List<ConcreteNote> notes,
                                  int channel,
                                  VelocityControl velocityCtrl)
            throws InvalidMidiDataException {
        int i = 0;
        while (i < notes.size()) {
            ConcreteNote head = notes.get(i);
            int pitch = pitchOf(head);
            music.notation.duration.Duration startAt = head.at();
            music.notation.duration.Duration endAt = head.endAt();

            // Extend chain while head is tied and the next note is a
            // same-pitch, immediately-following PitchedNote.
            int j = i;
            while (j < notes.size() - 1 && isTiedForward(notes.get(j))) {
                ConcreteNote next = notes.get(j + 1);
                if (next instanceof PitchedNote nextPn
                        && nextPn.midi() == pitch
                        && nextPn.at().equalsDuration(endAt)) {
                    endAt = nextPn.endAt();
                    j++;
                } else {
                    // Chain broken: tie flag set but successor doesn't match.
                    // Silently emit independently — the flag survives in the
                    // model but the codec can't honour an inconsistent tie.
                    break;
                }
            }

            double level = (velocityCtrl == null)
                    ? VelocityControl.DEFAULT_LEVEL
                    : velocityCtrl.levelAt(startAt);
            // Boundary translation: synth-agnostic [0,1] level → NOTE_ON
            // velocity byte [1,127]. NOTE_ON vel=0 is illegal here (it's a
            // NOTE_OFF synonym in MIDI); the codec emits NOTE_OFF explicitly.
            int velocity = Math.max(1, Math.min(127, (int) Math.round(level * 127.0)));
            addNotePair(track, startAt, endAt.minus(startAt),
                    pitch, channel, velocity);
            i = j + 1;
        }
    }

    private static int pitchOf(ConcreteNote n) {
        // PitchedLike.midi() returns the EFFECTIVE midi — for ShiftedNote
        // that's original + shift; for PitchedNote it's the authored value.
        // This is the codec's emission point; it treats both uniformly.
        return switch (n) {
            case PitchedLike pl -> pl.midi();
            case DrumNote dn -> dn.piece();
        };
    }

    private static boolean isTiedForward(ConcreteNote n) {
        // Tied flag is preserved on both PitchedNote and ShiftedNote
        // (the latter delegates to its original).
        return n instanceof PitchedLike pl && pl instanceof music.notation.phrase.Tieable t
                && t.tiedToNext();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  READ: MIDI bytes  →  Performance
    // ═══════════════════════════════════════════════════════════════════

    public static Performance fromMidi(byte[] bytes) {
        try {
            Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(bytes));
            int ppq = sequence.getResolution();

            // 1. Read tempo meta events directly into Duration-anchored
            //    TempoChanges. MIDI tick is the musical position (ticks /
            //    PPQ = quarters → /4 = whole-note fractions), so we can
            //    convert without any ms math.
            List<TempoChange> tempoChanges = readTempoChanges(sequence, ppq);

            // 2. Walk MIDI tracks, collecting lanes keyed by (midi-track-index, channel).
            javax.sound.midi.Track[] midiTracks = sequence.getTracks();
            Map<LaneKey, Lane> lanes = new LinkedHashMap<>();
            Map<Integer, String> trackNames = new HashMap<>();
            Map<Integer, Set<Integer>> channelsByTrack = new HashMap<>();

            for (int ti = 0; ti < midiTracks.length; ti++) {
                javax.sound.midi.Track mt = midiTracks[ti];
                Map<Integer, List<Pending>> outstanding = new HashMap<>();

                for (int i = 0; i < mt.size(); i++) {
                    MidiEvent ev = mt.get(i);
                    long midiTick = ev.getTick();
                    music.notation.duration.Duration at = durationOfTicksWithPpq(midiTick, ppq);

                    switch (ev.getMessage()) {
                        case MetaMessage meta -> {
                            if (meta.getType() == META_TRACK_NAME) {
                                trackNames.put(ti, new String(meta.getData(), StandardCharsets.UTF_8));
                            }
                        }
                        case ShortMessage sm -> {
                            int channel = sm.getChannel();
                            int cmd = sm.getCommand();
                            switch (cmd) {
                                case ShortMessage.PROGRAM_CHANGE -> {
                                    Lane lane = lanes.computeIfAbsent(new LaneKey(ti, channel), k -> new Lane());
                                    lane.programChanges.add(new InstrumentChange(at, sm.getData1()));
                                    channelsByTrack.computeIfAbsent(ti, k -> new HashSet<>()).add(channel);
                                }
                                case ShortMessage.NOTE_ON -> {
                                    channelsByTrack.computeIfAbsent(ti, k -> new HashSet<>()).add(channel);
                                    if (sm.getData2() > 0) {
                                        outstanding.computeIfAbsent(
                                                        noteKey(channel, sm.getData1()), k -> new ArrayList<>())
                                                .add(new Pending(ti, channel, at, sm.getData2()));
                                    } else {
                                        matchOff(outstanding, lanes, channel, sm.getData1(), at);
                                    }
                                }
                                case ShortMessage.NOTE_OFF ->
                                        matchOff(outstanding, lanes, channel, sm.getData1(), at);
                                default -> {
                                    // Discard CC, pitch bend, channel pressure, poly aftertouch, etc.
                                }
                            }
                        }
                        default -> {
                            // Discard SysEx and other meta (except track name above).
                        }
                    }
                }
            }

            // 3. Build Track/Instrumentation from lanes deterministically.
            // Channel 9 lanes from multiple MIDI tracks are coalesced into a
            // single DRUM Track — Score allows at most one drum track, but
            // real-world MIDI commonly splits drum kit pieces across tracks.
            List<Track> outTracks = new ArrayList<>();
            Map<TrackId, InstrumentControl> instrMap = new LinkedHashMap<>();
            Map<TrackId, VelocityControl> velocityMap = new LinkedHashMap<>();
            Set<String> usedNames = new HashSet<>();
            int unnamedCounter = 0;

            List<ConcreteNote> drumNotes = new ArrayList<>();
            List<RawNote> drumRawNotes = new ArrayList<>();
            List<InstrumentChange> drumProgramChanges = new ArrayList<>();

            for (Map.Entry<LaneKey, Lane> entry : lanes.entrySet()) {
                LaneKey key = entry.getKey();
                Lane lane = entry.getValue();
                if (lane.notes.isEmpty() && lane.programChanges.isEmpty()) continue;

                if (key.channel == DRUM_CHANNEL) {
                    for (RawNote rn : lane.notes) {
                        drumNotes.add(new DrumNote(rn.at, rn.duration, rn.pitch));
                        drumRawNotes.add(rn);
                    }
                    drumProgramChanges.addAll(lane.programChanges);
                    continue;
                }

                // Pitched lane → its own Track.
                String baseName;
                String trackName = trackNames.get(key.midiTrackIndex);
                Set<Integer> channels = channelsByTrack.getOrDefault(key.midiTrackIndex, Set.of());
                if (trackName != null && !trackName.isBlank() && channels.size() == 1) {
                    baseName = trackName;
                } else {
                    baseName = "track_" + unnamedCounter++;
                }
                String finalName = uniqueName(baseName, usedNames);
                usedNames.add(finalName);
                TrackId id = new TrackId(finalName);

                List<ConcreteNote> notes = new ArrayList<>(lane.notes.size());
                for (RawNote rn : lane.notes) {
                    notes.add(new PitchedNote(rn.at, rn.duration, rn.pitch));
                }
                outTracks.add(new Track(id, TrackKind.PITCHED, notes));

                if (!lane.programChanges.isEmpty()) {
                    instrMap.put(id, new InstrumentControl(lane.programChanges));
                }
                VelocityControl vc = velocityControlFor(lane.notes);
                if (!vc.changes().isEmpty()) velocityMap.put(id, vc);
            }

            // Emit the coalesced drum Track if any drum events were seen.
            if (!drumNotes.isEmpty() || !drumProgramChanges.isEmpty()) {
                Comparator<music.notation.duration.Duration> byPos =
                        (a, b) -> a.compareDuration(b);
                drumNotes.sort(Comparator.comparing(ConcreteNote::at, byPos));
                drumRawNotes.sort(Comparator.comparing((RawNote rn) -> rn.at, byPos));
                drumProgramChanges.sort(Comparator.comparing(InstrumentChange::at, byPos));
                String drumName = uniqueName("drums", usedNames);
                usedNames.add(drumName);
                TrackId id = new TrackId(drumName);
                outTracks.add(new Track(id, TrackKind.DRUM, drumNotes));
                if (!drumProgramChanges.isEmpty()) {
                    instrMap.put(id, new InstrumentControl(drumProgramChanges));
                }
                VelocityControl vc = velocityControlFor(drumRawNotes);
                if (!vc.changes().isEmpty()) velocityMap.put(id, vc);
            }

            Score score = new Score(outTracks);
            return new Performance(
                    score,
                    new TempoTrack(tempoChanges),
                    new Instrumentation(instrMap),
                    Volume.empty(),
                    Articulations.empty(),
                    Pedaling.empty(),
                    new Velocities(velocityMap));
        } catch (Exception e) {
            throw new IllegalStateException("fromMidi failed", e);
        }
    }

    /**
     * Build a per-onset {@link VelocityControl} from raw note velocities.
     * Step-function semantics + dedup mean a uniform-velocity track
     * produces just one (or zero) entries.
     *
     * <p>Returns {@link VelocityControl#empty()} when the resulting
     * control would be trivially default — i.e. one entry at level
     * {@link VelocityControl#DEFAULT_LEVEL}. This preserves
     * {@code Performance} round-trip equality when the source had no
     * explicit per-note dynamics (every NOTE_ON written + read at the
     * codec default).</p>
     */
    private static VelocityControl velocityControlFor(List<RawNote> notes) {
        if (notes.isEmpty()) return VelocityControl.empty();
        List<RawNote> sorted = new ArrayList<>(notes);
        sorted.sort(Comparator.comparing((RawNote rn) -> rn.at,
                (a, b) -> a.compareDuration(b)));
        List<VelocityChange> changes = new ArrayList<>(sorted.size());
        for (RawNote rn : sorted) {
            // Boundary translation: MIDI velocity byte [0,127] → level [0,1].
            double level = Math.max(0.0, Math.min(1.0, rn.velocity / 127.0));
            changes.add(new VelocityChange(rn.at, level));
        }
        // VelocityControl's constructor sorts + dedups consecutive same-level entries.
        VelocityControl ctrl = new VelocityControl(changes);
        if (ctrl.changes().size() == 1
                && Math.abs(ctrl.changes().get(0).level() - VelocityControl.DEFAULT_LEVEL) < (1.0 / 254.0)) {
            return VelocityControl.empty();
        }
        return ctrl;
    }

    private static String uniqueName(String base, Set<String> used) {
        if (!used.contains(base)) return base;
        int n = 2;
        while (used.contains(base + "_" + n)) n++;
        return base + "_" + n;
    }

    /**
     * Read tempo meta events from a {@link Sequence} into a list of
     * {@link TempoChange}s anchored at musical positions. MIDI tick is
     * the musical position scaled by PPQ × 4 (ticks per whole note),
     * so this is a direct rational conversion — no tempo math needed.
     */
    private static List<TempoChange> readTempoChanges(Sequence sequence, int ppq) {
        // Collect raw (tick, bpm). Multiple tempo metas at the same tick:
        // last wins (matches the ordering that would actually play).
        TreeMap<Long, Integer> byTick = new TreeMap<>();
        for (javax.sound.midi.Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent ev = track.get(i);
                if (ev.getMessage() instanceof MetaMessage meta && meta.getType() == META_TEMPO) {
                    byTick.put(ev.getTick(), bpmFromTempoMeta(meta));
                }
            }
        }
        List<TempoChange> out = new ArrayList<>(byTick.size());
        for (Map.Entry<Long, Integer> e : byTick.entrySet()) {
            out.add(new TempoChange(durationOfTicksWithPpq(e.getKey(), ppq), e.getValue()));
        }
        return out;
    }

    /**
     * Convert a MIDI tick value (with a runtime-provided PPQ) to a
     * musical {@link music.notation.duration.Duration}. Used on
     * read, where the PPQ comes from the {@link Sequence}, not the
     * codec constant.
     */
    private static music.notation.duration.Duration durationOfTicksWithPpq(long ticks, int ppq) {
        return music.notation.duration.Duration.of(ticks, (long) ppq * 4L);
    }

    private static int bpmFromTempoMeta(MetaMessage meta) {
        byte[] d = meta.getData();
        int us = ((d[0] & 0xFF) << 16) | ((d[1] & 0xFF) << 8) | (d[2] & 0xFF);
        return (int) Math.round(60_000_000.0 / us);
    }

    private static void matchOff(Map<Integer, List<Pending>> outstanding,
                                 Map<LaneKey, Lane> lanes,
                                 int channel, int pitch,
                                 music.notation.duration.Duration offAt) {
        List<Pending> list = outstanding.get(noteKey(channel, pitch));
        if (list == null || list.isEmpty()) return;
        Pending pending = list.remove(0);
        music.notation.duration.Duration duration = offAt.minus(pending.onAt);
        // Floor at one tick (1 / (PPQ × 4) whole) so zero-length notes
        // from same-tick NOTE_ON/OFF pairs don't violate
        // PitchedNote's "duration > 0" invariant.
        music.notation.duration.Duration oneTick =
                music.notation.duration.Duration.of(1, (long) PPQ * 4L);
        if (duration.compareDuration(oneTick) < 0) {
            duration = oneTick;
        }
        Lane lane = lanes.computeIfAbsent(new LaneKey(pending.midiTrackIndex, pending.channel), k -> new Lane());
        lane.notes.add(new RawNote(pending.onAt, duration, pitch, pending.velocity));
    }

    private static int noteKey(int channel, int pitch) {
        return (channel << 8) | pitch;
    }

    private record Pending(int midiTrackIndex, int channel,
                            music.notation.duration.Duration onAt, int velocity) {}
    private record RawNote(music.notation.duration.Duration at,
                            music.notation.duration.Duration duration,
                            int pitch, int velocity) {}
    private record LaneKey(int midiTrackIndex, int channel) {}
    private static final class Lane {
        final List<RawNote> notes = new ArrayList<>();
        final List<InstrumentChange> programChanges = new ArrayList<>();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Convenience: read with time-sig + key-sig meta
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Read a MIDI file plus the first time-signature and key-signature
     * meta events into a {@link MidiImport}. Defaults applied when the
     * meta events are absent: 4/4 time, C-major key.
     */
    public static MidiImport fromMidiWithMeta(byte[] bytes, String displayName) {
        Performance performance = fromMidi(bytes);
        try {
            Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(bytes));
            return new MidiImport(displayName, performance,
                    readTimeSignature(sequence).orElse(
                            new music.notation.structure.TimeSignature(4, 4)),
                    readKeySignature(sequence).orElse(
                            new music.notation.structure.KeySignature(
                                    music.notation.pitch.NoteName.C,
                                    music.notation.pitch.Accidental.NATURAL,
                                    music.notation.structure.Mode.MAJOR)));
        } catch (InvalidMidiDataException | IOException e) {
            throw new IllegalStateException("Re-reading MIDI for meta extraction failed", e);
        }
    }

    private static java.util.Optional<music.notation.structure.TimeSignature> readTimeSignature(Sequence seq) {
        for (javax.sound.midi.Track t : seq.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                if (t.get(i).getMessage() instanceof MetaMessage mm
                        && mm.getType() == META_TIME_SIGNATURE) {
                    byte[] d = mm.getData();
                    if (d.length < 2) continue;
                    int numerator   = d[0] & 0xff;
                    int denomPower  = d[1] & 0xff;
                    int beatValue   = 1 << denomPower;   // 0→1, 1→2, 2→4, 3→8
                    return java.util.Optional.of(
                            new music.notation.structure.TimeSignature(numerator, beatValue));
                }
            }
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<music.notation.structure.KeySignature> readKeySignature(Sequence seq) {
        for (javax.sound.midi.Track t : seq.getTracks()) {
            for (int i = 0; i < t.size(); i++) {
                if (t.get(i).getMessage() instanceof MetaMessage mm
                        && mm.getType() == META_KEY_SIGNATURE) {
                    byte[] d = mm.getData();
                    if (d.length < 2) continue;
                    int sharps = (byte) d[0];     // -7..+7
                    int minor  = d[1] & 0xff;     // 0 = major, 1 = minor
                    return java.util.Optional.of(decodeKey(sharps, minor != 0));
                }
            }
        }
        return java.util.Optional.empty();
    }

    /** Map MIDI key-sig (sharps in [-7, +7], minor flag) to {@link music.notation.structure.KeySignature}. */
    private static music.notation.structure.KeySignature decodeKey(int sharps, boolean minor) {
        music.notation.pitch.NoteName tonic;
        music.notation.pitch.Accidental acc = music.notation.pitch.Accidental.NATURAL;
        if (!minor) {
            tonic = switch (sharps) {
                case -7 -> { acc = music.notation.pitch.Accidental.FLAT; yield music.notation.pitch.NoteName.C; }
                case -6 -> { acc = music.notation.pitch.Accidental.FLAT; yield music.notation.pitch.NoteName.G; }
                case -5 -> { acc = music.notation.pitch.Accidental.FLAT; yield music.notation.pitch.NoteName.D; }
                case -4 -> { acc = music.notation.pitch.Accidental.FLAT; yield music.notation.pitch.NoteName.A; }
                case -3 -> { acc = music.notation.pitch.Accidental.FLAT; yield music.notation.pitch.NoteName.E; }
                case -2 -> { acc = music.notation.pitch.Accidental.FLAT; yield music.notation.pitch.NoteName.B; }
                case -1 -> music.notation.pitch.NoteName.F;
                case  0 -> music.notation.pitch.NoteName.C;
                case  1 -> music.notation.pitch.NoteName.G;
                case  2 -> music.notation.pitch.NoteName.D;
                case  3 -> music.notation.pitch.NoteName.A;
                case  4 -> music.notation.pitch.NoteName.E;
                case  5 -> music.notation.pitch.NoteName.B;
                case  6 -> { acc = music.notation.pitch.Accidental.SHARP; yield music.notation.pitch.NoteName.F; }
                case  7 -> { acc = music.notation.pitch.Accidental.SHARP; yield music.notation.pitch.NoteName.C; }
                default -> music.notation.pitch.NoteName.C;
            };
            return new music.notation.structure.KeySignature(tonic, acc, music.notation.structure.Mode.MAJOR);
        }
        // Minor — relative-minor mapping.
        tonic = switch (sharps) {
            case -7 -> { acc = music.notation.pitch.Accidental.FLAT; yield music.notation.pitch.NoteName.A; }
            case -6 -> { acc = music.notation.pitch.Accidental.FLAT; yield music.notation.pitch.NoteName.E; }
            case -5 -> { acc = music.notation.pitch.Accidental.FLAT; yield music.notation.pitch.NoteName.B; }
            case -4 -> music.notation.pitch.NoteName.F;
            case -3 -> music.notation.pitch.NoteName.C;
            case -2 -> music.notation.pitch.NoteName.G;
            case -1 -> music.notation.pitch.NoteName.D;
            case  0 -> music.notation.pitch.NoteName.A;
            case  1 -> music.notation.pitch.NoteName.E;
            case  2 -> music.notation.pitch.NoteName.B;
            case  3 -> { acc = music.notation.pitch.Accidental.SHARP; yield music.notation.pitch.NoteName.F; }
            case  4 -> { acc = music.notation.pitch.Accidental.SHARP; yield music.notation.pitch.NoteName.C; }
            case  5 -> { acc = music.notation.pitch.Accidental.SHARP; yield music.notation.pitch.NoteName.G; }
            case  6 -> { acc = music.notation.pitch.Accidental.SHARP; yield music.notation.pitch.NoteName.D; }
            case  7 -> { acc = music.notation.pitch.Accidental.SHARP; yield music.notation.pitch.NoteName.A; }
            default -> music.notation.pitch.NoteName.A;
        };
        return new music.notation.structure.KeySignature(tonic, acc, music.notation.structure.Mode.MINOR);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Position arithmetic — see ticksFor() / durationOfTicks() above.
    //  The old internal TempoMap (ms↔tick converter) is gone — under
    //  the Duration-based model, MIDI ticks ARE musical positions
    //  scaled by PPQ × 4, so no tempo math is needed for positions.
    //  Wall-clock ms (when needed by a Sequencer / UI playhead) goes
    //  through TimeMapper, which takes a Duration and a TempoTrack.
    // ─────────────────────────────────────────────────────────────────
}
