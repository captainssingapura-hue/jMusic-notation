package music.notation.performance;

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
 *   <li>Per-note velocity in source MIDI — every NOTE_ON the codec
 *       emits uses a fixed velocity of 80; incoming velocities are
 *       read but discarded.</li>
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
    /** Fixed velocity used for every NOTE_ON the codec emits. */
    private static final int DEFAULT_VELOCITY = 80;
    private static final int MIDI_FILE_TYPE_MULTI_TRACK = 1;
    private static final int DRUM_CHANNEL = 9;
    private static final int META_TEMPO = 0x51;
    private static final int META_TRACK_NAME = 0x03;

    private MidiCodec() {}

    // ═══════════════════════════════════════════════════════════════════
    //  WRITE: Performance  →  MIDI bytes
    // ═══════════════════════════════════════════════════════════════════

    public static byte[] toMidi(Performance p) {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, PPQ);

            TempoMap tempoMap = buildTempoMap(p.tempo());

            // Track 0 — conductor: tempo meta events only.
            javax.sound.midi.Track conductor = sequence.createTrack();
            for (TempoChange tc : p.tempo().changes()) {
                addTempoMeta(conductor, tc, tempoMap);
            }

            // Assign channels to Score tracks.
            List<Track> scoreTracks = p.score().tracks();
            Map<TrackId, Integer> channelByTrack = assignChannels(scoreTracks);

            for (Track t : scoreTracks) {
                javax.sound.midi.Track mt = sequence.createTrack();
                int channel = channelByTrack.get(t.id());

                addTrackName(mt, t.id().name());

                InstrumentControl ic = p.instruments().byTrack().get(t.id());
                if (ic != null) {
                    for (InstrumentChange change : ic.changes()) {
                        addProgramChange(mt, change, channel, tempoMap);
                    }
                }

                VolumeControl vc = p.volume().byTrack().get(t.id());
                if (vc != null) {
                    for (VolumeChange change : vc.changes()) {
                        addVolumeChange(mt, change, channel, tempoMap);
                    }
                }

                emitNotes(mt, t.notes(), channel, tempoMap);
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

    private static TempoMap buildTempoMap(TempoTrack tempo) {
        TempoMap map = new TempoMap(DEFAULT_BPM, PPQ);
        for (TempoChange c : tempo.changes()) {
            map.addChange(c.tickMs(), c.bpm());
        }
        return map;
    }

    private static void addTempoMeta(javax.sound.midi.Track track, TempoChange t, TempoMap map)
            throws InvalidMidiDataException {
        long midiTick = map.msToTick(t.tickMs());
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

    private static void addProgramChange(javax.sound.midi.Track track, InstrumentChange change,
                                         int channel, TempoMap map)
            throws InvalidMidiDataException {
        long midiTick = map.msToTick(change.tickMs());
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
                                        int channel, TempoMap map)
            throws InvalidMidiDataException {
        long midiTick = map.msToTick(change.tickMs());
        ShortMessage msg = new ShortMessage();
        msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, /*CC #7=*/ 7, change.level());
        track.add(new MidiEvent(msg, midiTick));
    }

    private static void addNotePair(javax.sound.midi.Track track, long tickMs, long durationMs,
                                    int pitch, int channel, TempoMap map)
            throws InvalidMidiDataException {
        long onTick  = map.msToTick(tickMs);
        long offTick = map.msToTick(tickMs + durationMs);

        ShortMessage on = new ShortMessage();
        on.setMessage(ShortMessage.NOTE_ON, channel, pitch, DEFAULT_VELOCITY);
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
                                  int channel, TempoMap tempoMap)
            throws InvalidMidiDataException {
        int i = 0;
        while (i < notes.size()) {
            ConcreteNote head = notes.get(i);
            int pitch = pitchOf(head);
            long startTickMs = head.tickMs();
            long endTickMs = head.offTickMs();

            // Extend chain while head is tied and the next note is a
            // same-pitch, immediately-following PitchedNote.
            int j = i;
            while (j < notes.size() - 1 && isTiedForward(notes.get(j))) {
                ConcreteNote next = notes.get(j + 1);
                if (next instanceof PitchedNote nextPn
                        && nextPn.midi() == pitch
                        && nextPn.tickMs() == endTickMs) {
                    endTickMs = nextPn.offTickMs();
                    j++;
                } else {
                    // Chain broken: tie flag set but successor doesn't match.
                    // Silently emit independently — the flag survives in the
                    // model but the codec can't honour an inconsistent tie.
                    break;
                }
            }

            addNotePair(track, startTickMs, endTickMs - startTickMs,
                    pitch, channel, tempoMap);
            i = j + 1;
        }
    }

    private static int pitchOf(ConcreteNote n) {
        return switch (n) {
            case PitchedNote pn -> pn.midi();
            case DrumNote dn -> dn.piece();
        };
    }

    private static boolean isTiedForward(ConcreteNote n) {
        return n instanceof PitchedNote pn && pn.tiedToNext();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  READ: MIDI bytes  →  Performance
    // ═══════════════════════════════════════════════════════════════════

    public static Performance fromMidi(byte[] bytes) {
        try {
            Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(bytes));
            int ppq = sequence.getResolution();

            // 1. Build tempo map + raw TempoChange list from all tempo meta events.
            List<TempoChange> tempoChanges = new ArrayList<>();
            TempoMap tempoMap = readTempoMap(sequence, ppq, tempoChanges);

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
                    long tickMs = tempoMap.tickToMs(midiTick);

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
                                    lane.programChanges.add(new InstrumentChange(tickMs, sm.getData1()));
                                    channelsByTrack.computeIfAbsent(ti, k -> new HashSet<>()).add(channel);
                                }
                                case ShortMessage.NOTE_ON -> {
                                    channelsByTrack.computeIfAbsent(ti, k -> new HashSet<>()).add(channel);
                                    if (sm.getData2() > 0) {
                                        outstanding.computeIfAbsent(
                                                        noteKey(channel, sm.getData1()), k -> new ArrayList<>())
                                                .add(new Pending(ti, channel, tickMs));
                                    } else {
                                        matchOff(outstanding, lanes, channel, sm.getData1(), tickMs);
                                    }
                                }
                                case ShortMessage.NOTE_OFF ->
                                        matchOff(outstanding, lanes, channel, sm.getData1(), tickMs);
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
            List<Track> outTracks = new ArrayList<>();
            Map<TrackId, InstrumentControl> instrMap = new LinkedHashMap<>();
            Set<String> usedNames = new HashSet<>();
            int unnamedCounter = 0;

            for (Map.Entry<LaneKey, Lane> entry : lanes.entrySet()) {
                LaneKey key = entry.getKey();
                Lane lane = entry.getValue();
                if (lane.notes.isEmpty() && lane.programChanges.isEmpty()) continue;

                TrackKind kind = (key.channel == DRUM_CHANNEL) ? TrackKind.DRUM : TrackKind.PITCHED;

                // Synthesize TrackId.
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
                    if (kind == TrackKind.DRUM) {
                        notes.add(new DrumNote(rn.tickMs, rn.durationMs, rn.pitch));
                    } else {
                        notes.add(new PitchedNote(rn.tickMs, rn.durationMs, rn.pitch));
                    }
                }
                outTracks.add(new Track(id, kind, notes));

                if (!lane.programChanges.isEmpty()) {
                    instrMap.put(id, new InstrumentControl(lane.programChanges));
                }
            }

            Score score = new Score(outTracks);
            return new Performance(
                    score,
                    new TempoTrack(tempoChanges),
                    new Instrumentation(instrMap),
                    Articulations.empty());
        } catch (Exception e) {
            throw new IllegalStateException("fromMidi failed", e);
        }
    }

    private static String uniqueName(String base, Set<String> used) {
        if (!used.contains(base)) return base;
        int n = 2;
        while (used.contains(base + "_" + n)) n++;
        return base + "_" + n;
    }

    private static TempoMap readTempoMap(Sequence sequence, int ppq, List<TempoChange> outChanges) {
        // Collect raw (tick, bpm) sorted by tick across all MIDI tracks.
        TreeMap<Long, Integer> byTick = new TreeMap<>();
        List<long[]> raw = new ArrayList<>();
        for (javax.sound.midi.Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent ev = track.get(i);
                if (ev.getMessage() instanceof MetaMessage meta && meta.getType() == META_TEMPO) {
                    raw.add(new long[]{ev.getTick(), bpmFromTempoMeta(meta)});
                }
            }
        }
        raw.sort(Comparator.comparingLong(a -> a[0]));

        // Deduplicate by tick — if two tempo metas land on the same tick, last wins
        // (matches the ordering that would actually play).
        for (long[] r : raw) byTick.put(r[0], (int) r[1]);

        TempoMap map = new TempoMap(DEFAULT_BPM, ppq);
        for (Map.Entry<Long, Integer> e : byTick.entrySet()) {
            long atMs = map.tickToMs(e.getKey());
            map.addChange(atMs, e.getValue());
            outChanges.add(new TempoChange(atMs, e.getValue()));
        }
        return map;
    }

    private static int bpmFromTempoMeta(MetaMessage meta) {
        byte[] d = meta.getData();
        int us = ((d[0] & 0xFF) << 16) | ((d[1] & 0xFF) << 8) | (d[2] & 0xFF);
        return (int) Math.round(60_000_000.0 / us);
    }

    private static void matchOff(Map<Integer, List<Pending>> outstanding,
                                 Map<LaneKey, Lane> lanes,
                                 int channel, int pitch, long offMs) {
        List<Pending> list = outstanding.get(noteKey(channel, pitch));
        if (list == null || list.isEmpty()) return;
        Pending pending = list.remove(0);
        long safeOff = Math.max(offMs, pending.onMs + 1);
        long duration = safeOff - pending.onMs;
        Lane lane = lanes.computeIfAbsent(new LaneKey(pending.midiTrackIndex, pending.channel), k -> new Lane());
        lane.notes.add(new RawNote(pending.onMs, duration, pitch));
    }

    private static int noteKey(int channel, int pitch) {
        return (channel << 8) | pitch;
    }

    private record Pending(int midiTrackIndex, int channel, long onMs) {}
    private record RawNote(long tickMs, long durationMs, int pitch) {}
    private record LaneKey(int midiTrackIndex, int channel) {}
    private static final class Lane {
        final List<RawNote> notes = new ArrayList<>();
        final List<InstrumentChange> programChanges = new ArrayList<>();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Tempo map
    // ═══════════════════════════════════════════════════════════════════

    private static final class TempoMap {
        private final int ppq;
        private record Segment(long msAtChange, long tickAtChange, int bpm) {}
        private final List<Segment> segments = new ArrayList<>();

        TempoMap(int initialBpm, int ppq) {
            this.ppq = ppq;
            segments.add(new Segment(0, 0, initialBpm));
        }

        void addChange(long atMs, int bpm) {
            Segment prev = segments.get(segments.size() - 1);
            if (atMs <= prev.msAtChange) {
                if (segments.size() == 1 && atMs == 0) {
                    segments.set(0, new Segment(0, 0, bpm));
                    return;
                }
                return;
            }
            long tickAtChange = prev.tickAtChange + msToTicksInSegment(prev, atMs - prev.msAtChange);
            segments.add(new Segment(atMs, tickAtChange, bpm));
        }

        long msToTick(long ms) {
            Segment seg = segmentContainingMs(ms);
            return seg.tickAtChange + msToTicksInSegment(seg, ms - seg.msAtChange);
        }

        long tickToMs(long tick) {
            Segment seg = segmentContainingTick(tick);
            return seg.msAtChange + ticksToMsInSegment(seg, tick - seg.tickAtChange);
        }

        private Segment segmentContainingMs(long ms) {
            Segment chosen = segments.get(0);
            for (Segment s : segments) {
                if (s.msAtChange <= ms) chosen = s;
                else break;
            }
            return chosen;
        }

        private Segment segmentContainingTick(long tick) {
            Segment chosen = segments.get(0);
            for (Segment s : segments) {
                if (s.tickAtChange <= tick) chosen = s;
                else break;
            }
            return chosen;
        }

        private long msToTicksInSegment(Segment s, long deltaMs) {
            return Math.round((double) deltaMs * s.bpm * ppq / 60_000.0);
        }

        private long ticksToMsInSegment(Segment s, long deltaTicks) {
            return Math.round((double) deltaTicks * 60_000.0 / (s.bpm * ppq));
        }
    }
}
