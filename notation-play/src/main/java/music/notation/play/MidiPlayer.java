package music.notation.play;

import music.notation.event.Instrument;
import music.notation.performance.InstrumentControl;
import music.notation.performance.Instrumentation;
import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.performance.TrackId;
import music.notation.performance.Volume;
import music.notation.performance.VolumeControl;
import music.notation.phrase.*;
import music.notation.structure.DrumTrack;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import javax.sound.midi.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MidiPlayer {

    private Sequencer sequencer;
    private Synthesizer synthesizer;
    private volatile boolean paused;

    public MidiPlayer() {}

    /**
     * Build a MIDI Sequence using each track's default instrument.
     *
     * <p>Phase 3a: this single-argument overload routes through the new
     * {@link PieceConcretizer} + {@link MidiCodec} pipeline. The
     * override-bearing overloads ({@link #buildSequence(Piece, List)} and
     * {@link #buildSequence(Piece, List, List)}) still use
     * {@link PhraseInterpreter} for now; Phase 3b will migrate them
     * once the multi-instrument-per-track translation lands on the new
     * path.</p>
     */
    /**
     * Build a "note-only" {@link Sequence} for the piece — same pitches,
     * drums, and tempo events as {@link #buildSequence(Piece)} but with
     * all program-change and CC #7 (volume) events stripped.
     *
     * <p>Phase 5.1: pure projection. Pair with a {@link ChannelSetup}
     * applied directly to a {@link Synthesizer} for live control.
     * For self-contained file export, recombine via
     * {@link #freezeForExport(Sequence, ChannelSetup, TempoSetup, Region)}.</p>
     */
    public static Sequence buildNoteSequence(Piece piece) throws InvalidMidiDataException {
        Sequence seq = buildSequence(piece);
        stripChannelControlEvents(seq);
        return seq;
    }

    /**
     * Freeze a note-only sequence + channel setup + tempo setup into a
     * self-contained {@link Sequence} suitable for file export. Pure
     * combinator — caller passes in the note sequence and the setups,
     * and gets back a new Sequence with PC/CC events baked at tick 0
     * and the tempo curve scaled per the {@link TempoSetup} factor.
     *
     * <p>If {@code region} is bounded (not {@link Region#full()}),
     * only events whose tick lies in {@code [region.startTick,
     * region.endTick)} are copied, shifted by {@code -region.startTick}
     * so the exported file starts at tick 0. Strict-policy boundary
     * handling: notes spanning the region edge are clipped (both
     * NOTE_ON and NOTE_OFF are dropped if either falls outside the
     * region). For the most-recent tempo before the region start,
     * we emit a tempo meta event at output tick 0 so partial exports
     * play at the correct tempo from beat 1.</p>
     *
     * @param noteSeq      a sequence built by
     *                     {@link #buildNoteSequence(Piece)} (or any
     *                     sequence without PC/CC events)
     * @param channelSetup channel programs and volumes
     * @param tempoSetup   tempo factor (1.0 = unchanged)
     * @param region       region to export (use {@link Region#full()}
     *                     for the whole sequence)
     */
    public static Sequence freezeForExport(Sequence noteSeq,
                                           ChannelSetup channelSetup,
                                           TempoSetup tempoSetup,
                                           Region region)
            throws InvalidMidiDataException {
        Sequence out = new Sequence(noteSeq.getDivisionType(), noteSeq.getResolution());
        long start = region.startTick();
        long end = region.endTick();
        long noteSeqLen = noteSeq.getTickLength();
        long effectiveEnd = Math.min(end, Math.max(start + 1, noteSeqLen + 1));

        // Bake channel setup at tick 0 of the first output track for each
        // channel. We add one output track per input track to preserve
        // multi-track structure; PC/CC events are placed on the track
        // whose channel matches.
        for (int ti = 0; ti < noteSeq.getTracks().length; ti++) {
            javax.sound.midi.Track inTrack = noteSeq.getTracks()[ti];
            javax.sound.midi.Track outTrack = out.createTrack();

            int channel = detectChannel(inTrack);

            // Tempo carry-over: if this is the first track and region
            // starts mid-piece, emit the latest tempo BEFORE region.start
            // at output tick 0.
            if (ti == 0 && start > 0) {
                MetaMessage carriedTempo = findLatestTempoBefore(noteSeq, start);
                if (carriedTempo != null) {
                    outTrack.add(new MidiEvent(scaleTempo(carriedTempo, tempoSetup), 0));
                }
            }

            // Bake channel setup at tick 0 if a channel is detected.
            if (channel >= 0) {
                Integer prog = channelSetup.programs().get(channel);
                Integer vol = channelSetup.volumes().get(channel);
                if (prog != null) {
                    ShortMessage pc = new ShortMessage();
                    pc.setMessage(ShortMessage.PROGRAM_CHANGE, channel, prog, 0);
                    outTrack.add(new MidiEvent(pc, 0));
                }
                if (vol != null) {
                    ShortMessage cc = new ShortMessage();
                    cc.setMessage(ShortMessage.CONTROL_CHANGE, channel, /*CC #7*/ 7, vol);
                    outTrack.add(new MidiEvent(cc, 0));
                }
            }

            // Copy events in [start, effectiveEnd), shifted to [0, end-start).
            for (int i = 0; i < inTrack.size(); i++) {
                MidiEvent ev = inTrack.get(i);
                long tick = ev.getTick();
                if (tick < start || tick >= effectiveEnd) continue;
                MidiMessage msg = ev.getMessage();
                if (msg instanceof MetaMessage mm && mm.getType() == 0x51) {
                    msg = scaleTempo(mm, tempoSetup);
                }
                outTrack.add(new MidiEvent(msg, tick - start));
            }
        }
        return out;
    }

    /** Pick the MIDI channel of the first short message on this track. -1 if none. */
    private static int detectChannel(javax.sound.midi.Track track) {
        for (int i = 0; i < track.size(); i++) {
            if (track.get(i).getMessage() instanceof ShortMessage sm) {
                return sm.getChannel();
            }
        }
        return -1;
    }

    /** Latest tempo meta event with tick &lt; cutoff, across all tracks. Null if none. */
    private static MetaMessage findLatestTempoBefore(Sequence seq, long cutoff) {
        MetaMessage best = null;
        long bestTick = -1;
        for (javax.sound.midi.Track track : seq.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent ev = track.get(i);
                if (ev.getTick() >= cutoff) break; // tracks are tick-ordered
                if (ev.getMessage() instanceof MetaMessage mm && mm.getType() == 0x51) {
                    if (ev.getTick() > bestTick) {
                        best = mm;
                        bestTick = ev.getTick();
                    }
                }
            }
        }
        return best;
    }

    /** Scale a tempo meta event's microseconds-per-quarter by 1/factor (faster = lower μs/quarter). */
    private static MetaMessage scaleTempo(MetaMessage src, TempoSetup setup)
            throws InvalidMidiDataException {
        if (setup.bpmFactor() == 1.0) return src;
        byte[] data = src.getData();
        if (data.length != 3) return src;
        int microsPerQuarter = ((data[0] & 0xff) << 16) | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
        int scaled = Math.max(1, (int) Math.round(microsPerQuarter / setup.bpmFactor()));
        byte[] out = new byte[] {
                (byte) ((scaled >> 16) & 0xff),
                (byte) ((scaled >> 8) & 0xff),
                (byte) (scaled & 0xff)
        };
        MetaMessage mm = new MetaMessage();
        mm.setMessage(0x51, out, out.length);
        return mm;
    }

    /** Remove all PROGRAM_CHANGE and CC #7 events from every track in-place. */
    private static void stripChannelControlEvents(Sequence seq) {
        for (javax.sound.midi.Track track : seq.getTracks()) {
            // Collect events to remove first (avoids index shifting during walk).
            var toRemove = new ArrayList<MidiEvent>();
            for (int i = 0; i < track.size(); i++) {
                MidiEvent ev = track.get(i);
                if (ev.getMessage() instanceof ShortMessage sm) {
                    int cmd = sm.getCommand();
                    if (cmd == ShortMessage.PROGRAM_CHANGE) {
                        toRemove.add(ev);
                    } else if (cmd == ShortMessage.CONTROL_CHANGE && sm.getData1() == 7) {
                        toRemove.add(ev);
                    }
                }
            }
            for (MidiEvent ev : toRemove) track.remove(ev);
        }
    }

    public static Sequence buildSequence(Piece piece) throws InvalidMidiDataException {
        try {
            Performance perf = PieceConcretizer.concretize(piece);
            byte[] bytes = MidiCodec.toMidi(perf);
            return MidiSystem.getSequence(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new InvalidMidiDataException("buildSequence(Piece) failed: " + e.getMessage());
        }
    }

    /**
     * Build a MIDI Sequence with explicit instrument assignments per track.
     *
     * <p>Phase 3b: routes through the new {@link PieceConcretizer} +
     * {@link MidiCodec} pipeline. Per-track instrument overrides are
     * applied to the concretized {@link Performance}'s
     * {@link Instrumentation} side-channel before serialisation.</p>
     *
     * <p><b>Multi-instrument-per-track regression</b>: today's API allows
     * {@code trackInstruments.get(t).size() > 1}, which previously
     * duplicated the track onto N MIDI channels (one per instrument).
     * The new pipeline currently uses only the FIRST instrument; additional
     * slots are silently ignored. A future phase may restore this by
     * splitting Performance Tracks at concretization time.</p>
     *
     * @param piece            the piece to render
     * @param trackInstruments per-track list of instruments
     */
    public static Sequence buildSequence(Piece piece, List<List<Instrument>> trackInstruments)
            throws InvalidMidiDataException {
        return buildSequence(piece, trackInstruments, /*trackVolumes=*/ null);
    }

    /**
     * Build a MIDI Sequence with explicit instrument and volume assignments per track.
     *
     * <p>Instrument overrides are applied to the concretized
     * {@link Performance}'s {@link Instrumentation}. Volume overrides
     * (in MIDI CC #7 range, 0–127) are applied to its {@link Volume}
     * side-channel; the codec emits CC #7 events on each track's MIDI
     * channel at tick 0.</p>
     *
     * @param trackInstruments per-track list of instruments (only the first
     *                         element is used; multi-instrument-per-track is
     *                         a deferred regression)
     * @param trackVolumes     per-track list of volume levels 0–127 (only
     *                         the first element is used)
     */
    public static Sequence buildSequence(Piece piece, List<List<Instrument>> trackInstruments,
                                         List<List<Integer>> trackVolumes)
            throws InvalidMidiDataException {
        try {
            Performance perf = PieceConcretizer.concretize(piece);
            Performance withInstruments = applyInstrumentOverrides(perf, piece, trackInstruments);
            Performance withVolumes = applyVolumeOverrides(withInstruments, piece, trackVolumes);
            byte[] bytes = MidiCodec.toMidi(withVolumes);
            return MidiSystem.getSequence(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new InvalidMidiDataException("buildSequence(...) failed: " + e.getMessage());
        }
    }

    /**
     * Apply per-piece-track instrument overrides to a concretized
     * {@link Performance}. Aux tracks belonging to a piece track inherit
     * the same override (matching the legacy {@code renderTrack}
     * semantics where aux tracks use the parent's instrument list).
     *
     * <p>If a piece track has multiple instruments listed, only the
     * first is applied — see class-level note on the multi-instrument
     * regression.</p>
     */
    private static Performance applyInstrumentOverrides(
            Performance base, Piece piece, List<List<Instrument>> trackInstruments) {
        if (trackInstruments == null) return base;

        // Build piece-track name -> override program (first instrument only).
        Map<String, Integer> programByPieceName = new LinkedHashMap<>();
        List<Track> pieceTracks = piece.tracks();
        for (int t = 0; t < pieceTracks.size() && t < trackInstruments.size(); t++) {
            List<Instrument> insList = trackInstruments.get(t);
            if (insList == null || insList.isEmpty()) continue;
            programByPieceName.put(pieceTracks.get(t).name(), insList.get(0).program());
        }
        Set<String> pieceNames = programByPieceName.keySet();

        // Walk Performance tracks; map each TrackId back to a piece track name
        // (direct match, or strip " Aux N" suffix for aux Performance Tracks).
        Map<TrackId, InstrumentControl> newInstr = new LinkedHashMap<>();
        for (var pt : base.score().tracks()) {
            String perfName = pt.id().name();
            String pieceName = resolvePieceTrackName(perfName, pieceNames);
            Integer overrideProgram = pieceName == null ? null : programByPieceName.get(pieceName);
            if (overrideProgram != null) {
                newInstr.put(pt.id(), InstrumentControl.constant(overrideProgram));
            } else {
                InstrumentControl baseIc = base.instruments().byTrack().get(pt.id());
                if (baseIc != null) newInstr.put(pt.id(), baseIc);
            }
        }

        return new Performance(base.score(), base.tempo(),
                new Instrumentation(newInstr), base.volume(), base.articulations());
    }

    /**
     * Apply per-piece-track volume overrides to a concretized
     * {@link Performance}. Like {@link #applyInstrumentOverrides}, only
     * the first volume slot per piece track is used; aux Performance
     * tracks ("&lt;name&gt; Aux N") inherit the parent's volume.
     *
     * <p>Levels are clamped to MIDI CC #7 range 0–127. A null or empty
     * override list is a no-op.</p>
     */
    private static Performance applyVolumeOverrides(
            Performance base, Piece piece, List<List<Integer>> trackVolumes) {
        if (trackVolumes == null) return base;

        Map<String, Integer> levelByPieceName = new LinkedHashMap<>();
        List<Track> pieceTracks = piece.tracks();
        for (int t = 0; t < pieceTracks.size() && t < trackVolumes.size(); t++) {
            List<Integer> volList = trackVolumes.get(t);
            if (volList == null || volList.isEmpty()) continue;
            int level = Math.max(0, Math.min(127, volList.get(0)));
            levelByPieceName.put(pieceTracks.get(t).name(), level);
        }
        if (levelByPieceName.isEmpty()) return base;
        Set<String> pieceNames = levelByPieceName.keySet();

        Map<TrackId, VolumeControl> newVol = new LinkedHashMap<>(base.volume().byTrack());
        for (var pt : base.score().tracks()) {
            String perfName = pt.id().name();
            String pieceName = resolvePieceTrackName(perfName, pieceNames);
            Integer overrideLevel = pieceName == null ? null : levelByPieceName.get(pieceName);
            if (overrideLevel != null) {
                newVol.put(pt.id(), VolumeControl.constant(overrideLevel));
            }
        }

        return new Performance(base.score(), base.tempo(),
                base.instruments(), new Volume(newVol), base.articulations());
    }

    /** Resolve a Performance Track name to its parent piece-track name. */
    private static String resolvePieceTrackName(String performanceTrackName, Set<String> pieceNames) {
        if (pieceNames.contains(performanceTrackName)) return performanceTrackName;
        // PieceConcretizer names aux tracks "<base> Aux N"
        int auxMarker = performanceTrackName.lastIndexOf(" Aux ");
        if (auxMarker > 0) {
            String baseName = performanceTrackName.substring(0, auxMarker);
            if (pieceNames.contains(baseName)) return baseName;
        }
        return null;
    }

    /** Start playback with default instruments. */
    public void start(Piece piece) throws Exception {
        var defaults = new ArrayList<List<Instrument>>();
        for (Track track : piece.tracks()) {
            defaults.add(List.of(defaultInstrumentOf(track)));
        }
        start(piece, defaults);
    }

    /** Start playback with explicit instrument assignments. Non-blocking. */
    public void start(Piece piece, List<List<Instrument>> trackInstruments) throws Exception {
        start(piece, trackInstruments, null);
    }

    /** Start playback with explicit instrument and volume assignments. Non-blocking. */
    public void start(Piece piece, List<List<Instrument>> trackInstruments,
                      List<List<Integer>> trackVolumes) throws Exception {
        stop();
        paused = false;

        Sequence sequence = trackVolumes != null
                ? buildSequence(piece, trackInstruments, trackVolumes)
                : buildSequence(piece, trackInstruments);
        sequencer = MidiSystem.getSequencer(false);
        synthesizer = MidiSystem.getSynthesizer();
        synthesizer.open();
        sequencer.open();
        sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
        sequencer.setSequence(sequence);

        // Skip leading padding so pickup bars don't produce silence
        long paddingOffset = computeLeadingPaddingTicks(piece);
        if (paddingOffset > 0) {
            sequencer.setTickPosition(paddingOffset);
        }

        sequencer.start();
    }

    /** Stop playback and release resources. */
    public void stop() {
        paused = false;
        if (sequencer != null && sequencer.isOpen()) {
            sequencer.stop();
            sequencer.close();
            sequencer = null;
        }
        if (synthesizer != null && synthesizer.isOpen()) {
            synthesizer.close();
            synthesizer = null;
        }
    }

    /** Pause playback, keeping the sequencer open at the current position. */
    public void pause() {
        if (sequencer != null && sequencer.isOpen() && sequencer.isRunning()) {
            sequencer.stop();
            paused = true;
        }
    }

    /** Resume playback from the paused position. */
    public void resume() {
        if (sequencer != null && sequencer.isOpen() && !sequencer.isRunning() && paused) {
            sequencer.start();
            paused = false;
        }
    }

    /** True if playback is paused (sequencer open but not running). */
    public boolean isPaused() {
        return paused;
    }

    /** Seek to the given tick position. Works while playing or paused. */
    public void setTickPosition(long tick) {
        if (sequencer != null && sequencer.isOpen()) {
            sequencer.setTickPosition(tick);
        }
    }

    /** True if currently playing. */
    public boolean isPlaying() {
        return sequencer != null && sequencer.isRunning();
    }

    /** Current tick position of the sequencer (for visualization sync). */
    public long getTickPosition() {
        return (sequencer != null && sequencer.isOpen()) ? sequencer.getTickPosition() : 0;
    }

    /** Total tick length of the loaded sequence. */
    public long getTickLength() {
        return (sequencer != null && sequencer.isOpen()) ? sequencer.getTickLength() : 0;
    }

    /**
     * Export a piece to a Standard MIDI File (.mid), using the given instrument assignments.
     *
     * @param piece            the piece to export
     * @param trackInstruments per-track list of instruments (same format as {@link #start})
     * @param file             destination file (typically {@code .mid})
     * @throws InvalidMidiDataException if the sequence cannot be built
     * @throws IOException              if the file cannot be written
     */
    public static void exportMidi(Piece piece, List<List<Instrument>> trackInstruments,
                                   List<List<Integer>> trackVolumes, File file)
            throws InvalidMidiDataException, IOException {
        Sequence sequence = trackVolumes != null
                ? buildSequence(piece, trackInstruments, trackVolumes)
                : buildSequence(piece, trackInstruments);
        int[] types = MidiSystem.getMidiFileTypes(sequence);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(sequence, fileType, file);
    }

    public static void exportMidi(Piece piece, List<List<Instrument>> trackInstruments, File file)
            throws InvalidMidiDataException, IOException {
        Sequence sequence = buildSequence(piece, trackInstruments);
        int[] types = MidiSystem.getMidiFileTypes(sequence);
        int fileType = (types.length > 1) ? 1 : types[0]; // prefer type 1 (multi-track)
        MidiSystem.write(sequence, fileType, file);
    }

    /** Export using each track's default instrument. */
    public static void exportMidi(Piece piece, File file)
            throws InvalidMidiDataException, IOException {
        var defaults = new ArrayList<List<Instrument>>();
        for (Track track : piece.tracks()) {
            defaults.add(List.of(defaultInstrumentOf(track)));
        }
        exportMidi(piece, defaults, file);
    }

    /** Static convenience for simple blocking playback (used by demo). */
    public static void play(Piece piece) throws Exception {
        MidiPlayer player = new MidiPlayer();
        try {
            player.start(piece);
            while (player.isPlaying()) {
                Thread.sleep(100);
            }
            Thread.sleep(500);
        } finally {
            player.stop();
        }
    }

    /**
     * Compute the global leading-padding offset in ticks.
     *
     * <p>For each track, walk phrases/nodes from the start and sum
     * PaddingNode durations until a non-padding, duration-taking node
     * is encountered. The global offset is the minimum across all tracks.
     * If a track has no leading padding, the offset is 0.</p>
     */
    /** Default instrument inferred from a sealed Track variant. */
    private static Instrument defaultInstrumentOf(Track track) {
        return switch (track) {
            case MelodicTrack mt -> mt.defaultInstrument();
            case DrumTrack dt -> Instrument.DRUM_KIT;
        };
    }

    public static long computeLeadingPaddingTicks(Piece piece) {
        long globalMin = Long.MAX_VALUE;
        for (Track track : piece.tracks()) {
            globalMin = Math.min(globalMin, leadingPaddingForTrack(track));
            for (Track auxTrack : track.auxTracks()) {
                globalMin = Math.min(globalMin, leadingPaddingForTrack(auxTrack));
            }
        }
        return globalMin == Long.MAX_VALUE ? 0 : globalMin;
    }

    /**
     * Sum the leading {@link PaddingNode}/zero-duration-marker chain
     * across the track's bars until an audible (or other duration-taking)
     * node appears.
     */
    private static long leadingPaddingForTrack(Track track) {
        long padding = 0;
        for (var bar : track.bars()) {
            for (PhraseNode node : bar.nodes()) {
                switch (node) {
                    case PaddingNode p -> padding += MidiMapper.toTicks(p.duration());
                    case DynamicNode d -> {}
                    case TempoChangeNode t -> {}
                    case TempoTransitionStartNode t -> {}
                    case TempoTransitionEndNode t -> {}
                    default -> { return padding; } // hit audible / RestNode
                }
            }
        }
        return padding;
    }

}
