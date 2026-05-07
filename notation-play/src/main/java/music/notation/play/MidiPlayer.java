package music.notation.play;

import music.notation.event.Instrument;
import music.notation.performance.InstrumentControl;
import music.notation.performance.Instrumentation;
import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.expressivity.TrackId;
import music.notation.expressivity.Volume;
import music.notation.expressivity.VolumeControl;
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
     * Variant of {@link #buildNoteSequence(Piece)} that folds a
     * {@link music.notation.expressivity.Velocities} side-channel into
     * the concretized {@link Performance} before sequence build, so
     * NOTE_ON bytes carry the per-note velocity.
     */
    public static Sequence buildNoteSequence(Piece piece,
                                             music.notation.expressivity.Velocities velocities)
            throws InvalidMidiDataException {
        Sequence seq = buildSequence(piece, velocities);
        stripChannelControlEvents(seq);
        return seq;
    }

    /**
     * Note-only sequence built from an imported {@link music.notation.performance.Performance}.
     * Re-encodes via {@link MidiCodec#toMidi} (lossless for the supported feature set)
     * and strips channel-control events so the live {@link ChannelSetup}
     * fully owns program / volume / pan.
     */
    public static Sequence buildNoteSequence(music.notation.performance.Performance performance)
            throws InvalidMidiDataException {
        try {
            byte[] bytes = MidiCodec.toMidi(performance);
            Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(bytes));
            stripChannelControlEvents(seq);
            return seq;
        } catch (IOException e) {
            throw new InvalidMidiDataException("Failed to materialise Performance into Sequence: " + e.getMessage());
        }
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
                Integer bank = channelSetup.banks().get(channel);
                // Bank-select before PC so the receiver resolves the right patch.
                // Channel 9 (drums) skipped — GM ignores bank-select on rhythm.
                if (bank != null && channel != 9) {
                    int msb = bank > 127 ? (bank >> 7) & 0x7f : bank & 0x7f;
                    int lsb = bank > 127 ? bank & 0x7f : 0;
                    ShortMessage bMsb = new ShortMessage();
                    bMsb.setMessage(ShortMessage.CONTROL_CHANGE, channel, /*CC #0*/ 0, msb);
                    outTrack.add(new MidiEvent(bMsb, 0));
                    ShortMessage bLsb = new ShortMessage();
                    bLsb.setMessage(ShortMessage.CONTROL_CHANGE, channel, /*CC #32*/ 32, lsb);
                    outTrack.add(new MidiEvent(bLsb, 0));
                }
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
                Integer pan = channelSetup.pans().get(channel);
                if (pan != null) {
                    ShortMessage cc = new ShortMessage();
                    cc.setMessage(ShortMessage.CONTROL_CHANGE, channel, /*CC #10*/ 10, pan);
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
        return buildSequence(piece, music.notation.expressivity.Velocities.empty());
    }

    /**
     * Build a MIDI Sequence from a {@link Piece} with the supplied
     * {@link music.notation.expressivity.Velocities Velocities} folded
     * into the concretized {@link Performance}.
     */
    public static Sequence buildSequence(Piece piece,
                                         music.notation.expressivity.Velocities velocities)
            throws InvalidMidiDataException {
        try {
            Performance perf = PieceConcretizer.concretize(piece);
            Performance withVelocities = applyVelocityOverrides(perf, velocities);
            byte[] bytes = MidiCodec.toMidi(withVelocities);
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
        return buildSequence(piece, trackInstruments, trackVolumes,
                music.notation.expressivity.Velocities.empty());
    }

    /**
     * Build a MIDI Sequence with explicit instrument, volume, AND
     * per-note velocity assignments. The velocities side-channel is
     * folded into the concretized {@link Performance} before
     * {@link MidiCodec#toMidi}, so the codec emits the correct
     * NOTE_ON velocity bytes.
     *
     * <p>Pass {@link music.notation.expressivity.Velocities#empty()}
     * to keep uniform default velocity (no per-note dynamics).</p>
     */
    public static Sequence buildSequence(Piece piece, List<List<Instrument>> trackInstruments,
                                         List<List<Integer>> trackVolumes,
                                         music.notation.expressivity.Velocities velocities)
            throws InvalidMidiDataException {
        try {
            Performance perf = PieceConcretizer.concretize(piece);
            Performance withInstruments = applyInstrumentOverrides(perf, piece, trackInstruments);
            Performance withVolumes = applyVolumeOverrides(withInstruments, piece, trackVolumes);
            Performance withVelocities = applyVelocityOverrides(withVolumes, velocities);
            byte[] bytes = MidiCodec.toMidi(withVelocities);
            return MidiSystem.getSequence(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new InvalidMidiDataException("buildSequence(...) failed: " + e.getMessage());
        }
    }

    /**
     * Replace the {@link Performance}'s velocities side-channel. Empty
     * input → no-op (returns the input unchanged).
     */
    private static Performance applyVelocityOverrides(Performance perf,
                                                      music.notation.expressivity.Velocities velocities) {
        if (velocities == null || velocities.byTrack().isEmpty()) return perf;
        return new Performance(
                perf.score(),
                perf.tempo(),
                perf.instruments(),
                perf.volume(),
                perf.articulations(),
                perf.pedaling(),
                velocities);
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

    // ── Phase 5.1: ChannelSetup + TempoSetup live-control surface ──

    /** The note-only sequence currently loaded (held for export). */
    private Sequence currentNoteSequence;
    /** The most-recently applied channel setup (held for export + reapply on stop/start). */
    private ChannelSetup currentSetup;
    /** The most-recently applied tempo setup (held for export). */
    private TempoSetup currentTempo = TempoSetup.unity();
    /** The most-recently applied swing setup (held for restart-at-tick). */
    private SwingSetup currentSwing = SwingSetup.OFF;
    /**
     * The currently-staged humanizer setup. Applied to the note sequence
     * after swing on every {@code start} / {@code restartAt}. Hosts call
     * {@link #setHumanizer} before {@link #start} and
     * {@link #applyHumanizer} for live changes.
     */
    private HumanizerSetup currentHumanizer = HumanizerSetup.OFF;
    /**
     * Sustain-pedal info from an MXL import. The {@link music.notation.structure.Piece}
     * doesn't carry pedaling, so {@code NotationApp} stages it here before
     * {@code start()} so the Sequence can be post-processed by
     * {@link PedalInjector}.
     */
    private music.notation.expressivity.Pedaling currentPedaling =
            music.notation.expressivity.Pedaling.empty();
    /** When false, {@link #currentPedaling} is ignored even if non-empty. */
    private boolean pedalEnabled = true;
    /** Tempo timeline for ms→tick conversion in {@link PedalInjector}. */
    private music.notation.performance.TempoTrack currentPedalTempos =
            music.notation.performance.TempoTrack.empty();
    /**
     * Per-note velocity timelines applied to the next sequence build.
     * Empty means uniform default velocity. Augmented onto the
     * concretized {@link Performance} before {@link MidiCodec#toMidi}
     * so the codec emits the right NOTE_ON velocity bytes.
     */
    private music.notation.expressivity.Velocities currentVelocities =
            music.notation.expressivity.Velocities.empty();
    /** Layered soundbanks applied on next start() / restartAt(). */
    private SoundbankSetup soundbankSetup = SoundbankSetup.empty();

    /**
     * Stage a soundbank list to be applied on the next {@link #start} call.
     * Hot-reload during playback is intentionally not supported — switching
     * the synth's instrument table mid-stream silences any pending notes.
     */
    public void setSoundbankSetup(SoundbankSetup setup) {
        this.soundbankSetup = setup == null ? SoundbankSetup.empty() : setup;
    }

    public SoundbankSetup getSoundbankSetup() { return soundbankSetup; }
    /** The piece currently loaded (held for leading-pad lookup). */
    private Piece currentPiece;
    /**
     * Note-sequence factory bound at start() time. Lets restartAt / applySwing
     * rebuild the underlying note sequence regardless of whether the source
     * was a {@link Piece} or an imported {@link music.notation.performance.Performance}.
     */
    private NoteSequenceFactory baseFactory;

    @FunctionalInterface
    private interface NoteSequenceFactory {
        Sequence build() throws Exception;
    }

    /** Start playback with default instruments. */
    public void start(Piece piece) throws Exception {
        start(piece, ChannelSetup.from(piece, null, null), TempoSetup.unity());
    }

    /**
     * Backwards-compat overload: per-track instrument lists. Internally
     * derives a {@link ChannelSetup} (uses each list's first element).
     */
    public void start(Piece piece, List<List<Instrument>> trackInstruments) throws Exception {
        start(piece, trackInstruments, null);
    }

    /**
     * Backwards-compat overload: per-track instrument + volume lists.
     * Internally derives a {@link ChannelSetup}.
     */
    public void start(Piece piece, List<List<Instrument>> trackInstruments,
                      List<List<Integer>> trackVolumes) throws Exception {
        var ins = firstOf(trackInstruments);
        var vol = firstOf(trackVolumes);
        start(piece, ChannelSetup.from(piece, ins, vol), TempoSetup.unity());
    }

    /**
     * Phase 5.1 functional entry point: start playback driven by a
     * {@link ChannelSetup} (programs + volumes) and {@link TempoSetup}
     * (tempo factor). The Sequencer is loaded with a note-only Sequence
     * (no PC/CC); the setup is applied directly to the Synthesizer
     * channels. Subsequent live changes go through {@link #applySetup}
     * and {@link #applyTempo}.
     */
    public void start(Piece piece, ChannelSetup channelSetup, TempoSetup tempoSetup) throws Exception {
        start(piece, channelSetup, tempoSetup, SwingSetup.OFF);
    }

    /**
     * Phase 7.2: start playback with swing applied to the note sequence.
     * Subsequent live swing changes go through {@link #applySwing}, which
     * rebuilds the sequence and resumes at the requested tick.
     */
    public void start(Piece piece, ChannelSetup channelSetup,
                      TempoSetup tempoSetup, SwingSetup swingSetup) throws Exception {
        stop();
        paused = false;

        Sequence sequence = injectPedal(swingSetup.apply(buildNoteSequence(piece, currentVelocities)));
        sequence = currentHumanizer.apply(sequence);
        sequencer = MidiSystem.getSequencer(false);
        synthesizer = MidiSystem.getSynthesizer();
        synthesizer.open();
        soundbankSetup.apply(synthesizer);
        sequencer.open();
        sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
        sequencer.setSequence(sequence);

        currentNoteSequence = sequence;
        currentPiece = piece;
        currentSetup = channelSetup;
        currentTempo = tempoSetup;
        currentSwing = swingSetup;
        baseFactory = () -> buildNoteSequence(piece, currentVelocities);

        channelSetup.apply(synthesizer);
        tempoSetup.apply(sequencer);

        long paddingOffset = computeLeadingPaddingTicks(piece);
        if (paddingOffset > 0) {
            sequencer.setTickPosition(swingSetup.mapTick(paddingOffset, sequence.getResolution()));
        }
        sequencer.start();
    }

    /**
     * Start playback for an imported {@link music.notation.performance.Performance}.
     * Pure shim: re-encodes the Performance to a note-only Sequence, then
     * proceeds exactly as the Piece-based path. {@link #currentPiece} is
     * left null — Piece-only operations (scale rebuild, leading-padding
     * pickup) are not relevant for imports.
     */
    public void start(music.notation.performance.Performance performance,
                      ChannelSetup channelSetup, TempoSetup tempoSetup,
                      SwingSetup swingSetup) throws Exception {
        stop();
        paused = false;

        // Performance-based path already routes Pedaling through MidiCodec;
        // the post-process pedalInjector is a no-op (currentPedaling left
        // empty here so we don't double-emit).
        Sequence sequence = currentHumanizer.apply(swingSetup.apply(buildNoteSequence(performance)));
        sequencer = MidiSystem.getSequencer(false);
        synthesizer = MidiSystem.getSynthesizer();
        synthesizer.open();
        soundbankSetup.apply(synthesizer);
        sequencer.open();
        sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
        sequencer.setSequence(sequence);

        currentNoteSequence = sequence;
        currentPiece = null;
        currentSetup = channelSetup;
        currentTempo = tempoSetup;
        currentSwing = swingSetup;
        baseFactory = () -> buildNoteSequence(performance);

        channelSetup.apply(synthesizer);
        tempoSetup.apply(sequencer);
        sequencer.start();
    }

    /**
     * Reusable restart primitive: stop the running sequencer, rebuild the
     * note sequence under {@code swingSetup}, re-apply channel/tempo, and
     * resume at {@code resumeTick}. {@code resumeTick} is interpreted in
     * the new (swung) tick space — bar/beat-aligned ticks are stable across
     * swing ratios so callers can pass them directly. No-op if not running.
     */
    public void restartAt(long resumeTick, ChannelSetup channelSetup,
                          TempoSetup tempoSetup, SwingSetup swingSetup) throws Exception {
        if (sequencer == null || baseFactory == null) return;
        sequencer.stop();
        Sequence seq = currentHumanizer.apply(injectPedal(swingSetup.apply(baseFactory.build())));
        sequencer.setSequence(seq);
        currentNoteSequence = seq;
        currentSetup = channelSetup;
        currentTempo = tempoSetup;
        currentSwing = swingSetup;
        channelSetup.apply(synthesizer);
        tempoSetup.apply(sequencer);
        long bound = Math.max(0, Math.min(resumeTick, seq.getTickLength()));
        sequencer.setTickPosition(bound);
        sequencer.start();
    }

    /**
     * Live swing change. Stops, rebuilds with new swing, resumes at the
     * given tick (typically the current playhead, snapped to a bar, or 0).
     */
    public void applySwing(SwingSetup swingSetup, long resumeTick) throws Exception {
        if (swingSetup == null) return;
        if (sequencer == null) {
            currentSwing = swingSetup;
            return;
        }
        restartAt(resumeTick, currentSetup, currentTempo, swingSetup);
    }

    /**
     * Stage a {@link HumanizerSetup} to be applied on the next
     * {@link #start} or {@link #restartAt}. Idempotent. Use
     * {@link #applyHumanizer(HumanizerSetup, long)} to take effect on a
     * running sequencer.
     */
    public void setHumanizer(HumanizerSetup setup) {
        this.currentHumanizer = setup == null ? HumanizerSetup.OFF : setup;
    }

    public HumanizerSetup getHumanizer() { return currentHumanizer; }

    /**
     * Live humaniser change. Stops, rebuilds with the new jitter setting,
     * resumes at the given tick. No-op if not running (the new setup is
     * staged for the next {@link #start}).
     */
    public void applyHumanizer(HumanizerSetup setup, long resumeTick) throws Exception {
        if (setup == null) return;
        currentHumanizer = setup;
        if (sequencer == null) return;
        restartAt(resumeTick, currentSetup, currentTempo, currentSwing);
    }

    /**
     * Stage a {@link music.notation.expressivity.Pedaling} (typically from
     * an MXL import) plus the tempo timeline used to map ms positions to
     * MIDI ticks. Applied to the Piece-based playback sequence on the
     * next {@link #start} or {@link #restartAt}. No-op when
     * {@link #setPedalEnabled} is false or the pedaling is empty.
     */
    public void setPedaling(music.notation.expressivity.Pedaling pedaling,
                            music.notation.performance.TempoTrack tempos) {
        this.currentPedaling = (pedaling == null)
                ? music.notation.expressivity.Pedaling.empty() : pedaling;
        this.currentPedalTempos = (tempos == null)
                ? music.notation.performance.TempoTrack.empty() : tempos;
    }

    /**
     * Backwards-compat overload accepting a constant bpm.
     *
     * @deprecated use
     *     {@link #setPedaling(music.notation.expressivity.Pedaling,
     *                          music.notation.performance.TempoTrack)}
     */
    @Deprecated
    public void setPedaling(music.notation.expressivity.Pedaling pedaling, int referenceBpm) {
        setPedaling(pedaling, referenceBpm > 0
                ? music.notation.performance.TempoTrack.constant(referenceBpm)
                : music.notation.performance.TempoTrack.empty());
    }

    /** Live toggle for honour-or-ignore pedaling. Default: true. */
    public void setPedalEnabled(boolean enabled) { this.pedalEnabled = enabled; }
    public boolean isPedalEnabled()              { return pedalEnabled; }

    /**
     * Stage a {@link music.notation.expressivity.Velocities} side-channel
     * (typically auto-generated, or imported from MXL dynamics) for the
     * next library-piece sequence build. The Performance-based start
     * path consumes the source's own velocities and ignores this setter.
     */
    public void setVelocities(music.notation.expressivity.Velocities velocities) {
        this.currentVelocities = (velocities == null)
                ? music.notation.expressivity.Velocities.empty() : velocities;
    }

    /** Live pedal toggle on a running sequencer — rebuilds + resumes. */
    public void applyPedalEnabled(boolean enabled, long resumeTick) throws Exception {
        this.pedalEnabled = enabled;
        if (sequencer == null) return;
        restartAt(resumeTick, currentSetup, currentTempo, currentSwing);
    }

    private Sequence injectPedal(Sequence seq) throws InvalidMidiDataException {
        if (!pedalEnabled || currentPedaling.byTrack().isEmpty()) return seq;
        return PedalInjector.inject(seq, currentPedaling, currentPedalTempos);
    }

    /**
     * Live channel-control change: re-apply a {@link ChannelSetup}.
     * Idempotent — call any time. No-op if not running.
     */
    public void applySetup(ChannelSetup setup) {
        if (setup == null) return;
        currentSetup = setup;
        if (synthesizer != null && synthesizer.isOpen()) {
            setup.apply(synthesizer);
        }
    }

    /**
     * Live tempo change: re-apply a {@link TempoSetup}. Idempotent.
     * No-op if not running.
     */
    public void applyTempo(TempoSetup tempo) {
        if (tempo == null) return;
        currentTempo = tempo;
        if (sequencer != null && sequencer.isOpen()) {
            tempo.apply(sequencer);
        }
    }

    public ChannelSetup currentSetup() { return currentSetup; }
    public TempoSetup currentTempo() { return currentTempo; }

    private static <T> List<T> firstOf(List<List<T>> nested) {
        if (nested == null) return null;
        var out = new ArrayList<T>(nested.size());
        for (var lst : nested) {
            out.add(lst != null && !lst.isEmpty() ? lst.get(0) : null);
        }
        return out;
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

    /**
     * Export the currently-loaded piece (started via
     * {@link #start(Piece, ChannelSetup, TempoSetup)} or any of its
     * overloads) as a self-contained MIDI file. Reflects whatever
     * live setup has been applied via {@link #applySetup} /
     * {@link #applyTempo}.
     *
     * <p>Use {@link Region#full()} for whole-song export, or a
     * bounded {@link Region} for partial export.</p>
     */
    public void exportTo(File file, Region region)
            throws InvalidMidiDataException, IOException {
        if (currentNoteSequence == null || currentSetup == null) {
            throw new IllegalStateException(
                    "Nothing to export — start(...) the player first.");
        }
        Sequence frozen = freezeForExport(currentNoteSequence, currentSetup, currentTempo, region);
        int[] types = MidiSystem.getMidiFileTypes(frozen);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(frozen, fileType, file);
    }

    /** Whole-song export. Equivalent to {@code exportTo(file, Region.full())}. */
    public void exportTo(File file) throws InvalidMidiDataException, IOException {
        exportTo(file, Region.full());
    }

    public static void exportMidi(Piece piece, List<List<Instrument>> trackInstruments, File file)
            throws InvalidMidiDataException, IOException {
        Sequence sequence = buildSequence(piece, trackInstruments);
        int[] types = MidiSystem.getMidiFileTypes(sequence);
        int fileType = (types.length > 1) ? 1 : types[0]; // prefer type 1 (multi-track)
        MidiSystem.write(sequence, fileType, file);
    }

    /**
     * Export a piece using a {@link ChannelSetup} (programs + volumes +
     * pans). Builds a note-only sequence and freezes the setup at tick
     * 0 — the same code path live playback uses, so an exported file
     * matches what would be heard.
     */
    public static void exportMidi(Piece piece, ChannelSetup channelSetup, File file)
            throws InvalidMidiDataException, IOException {
        exportMidi(piece, channelSetup, file,
                music.notation.expressivity.Pedaling.empty(),
                music.notation.performance.TempoTrack.empty());
    }

    /**
     * Export with optional sustain-pedal injection. The {@link Piece}
     * model doesn't carry pedal info, so callers that have a
     * {@link music.notation.expressivity.Pedaling} (typically from an MXL
     * import) pass it explicitly to materialise CC #64 events in the
     * exported MIDI — same code path live playback uses via
     * {@link PedalInjector}. The supplied tempo timeline is used to
     * convert pedal-event ms positions to ticks (rubato-aware).
     */
    public static void exportMidi(Piece piece, ChannelSetup channelSetup, File file,
                                   music.notation.expressivity.Pedaling pedaling,
                                   music.notation.performance.TempoTrack tempos)
            throws InvalidMidiDataException, IOException {
        Sequence noteSeq = buildNoteSequence(piece);
        if (pedaling != null && !pedaling.byTrack().isEmpty()) {
            noteSeq = PedalInjector.inject(noteSeq, pedaling, tempos);
        }
        Sequence frozen = freezeForExport(noteSeq, channelSetup, TempoSetup.unity(), Region.full());
        int[] types = MidiSystem.getMidiFileTypes(frozen);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(frozen, fileType, file);
    }

    /**
     * Backwards-compat export overload accepting a constant reference
     * bpm. Prefer the {@link music.notation.performance.TempoTrack}
     * overload for rubato-aware export.
     *
     * @deprecated use
     *     {@link #exportMidi(Piece, ChannelSetup, File,
     *         music.notation.expressivity.Pedaling,
     *         music.notation.performance.TempoTrack)}
     */
    @Deprecated
    public static void exportMidi(Piece piece, ChannelSetup channelSetup, File file,
                                   music.notation.expressivity.Pedaling pedaling,
                                   int referenceBpm)
            throws InvalidMidiDataException, IOException {
        exportMidi(piece, channelSetup, file, pedaling,
                referenceBpm > 0
                        ? music.notation.performance.TempoTrack.constant(referenceBpm)
                        : music.notation.performance.TempoTrack.empty());
    }

    /**
     * Export an imported {@link music.notation.performance.Performance}
     * with current per-track instrument / volume / pan applied. Used by
     * the UI's export button when the loaded source is an import.
     */
    public static void exportMidi(music.notation.performance.Performance performance,
                                  ChannelSetup channelSetup, File file)
            throws InvalidMidiDataException, IOException {
        Sequence noteSeq = buildNoteSequence(performance);
        Sequence frozen = freezeForExport(noteSeq, channelSetup, TempoSetup.unity(), Region.full());
        int[] types = MidiSystem.getMidiFileTypes(frozen);
        int fileType = (types.length > 1) ? 1 : types[0];
        MidiSystem.write(frozen, fileType, file);
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
            globalMin = Math.min(globalMin, leadingPaddingForBars(track.bars()));
            // Aux voices participate in the pickup-offset calculation:
            // a piece-wide minimum still gives us the earliest audible
            // event across primary + aux content.
            if (track instanceof MelodicTrack mt) {
                for (var auxBars : mt.auxBars().values()) {
                    globalMin = Math.min(globalMin, leadingPaddingForBars(auxBars));
                }
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
        return leadingPaddingForBars(track.bars());
    }

    private static long leadingPaddingForBars(java.util.List<music.notation.phrase.Bar> bars) {
        long padding = 0;
        for (var bar : bars) {
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
