package music.notation.play;

import music.notation.event.Instrument;
import music.notation.performance.InstrumentControl;
import music.notation.performance.Instrumentation;
import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.performance.TrackId;
import music.notation.phrase.*;
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
     * <p>Phase 3b: instrument overrides are applied to the concretized
     * {@link Performance}'s {@link Instrumentation}. <b>Volume overrides
     * are silently ignored</b> — the new {@link Performance} model has no
     * volume side-channel yet. UI mixing is a known regression to be
     * restored by a future {@code Volume} side-channel + codec emission
     * of MIDI CC #7. See {@code .docs/agent-delegation-retrospective.md}.</p>
     *
     * @param trackInstruments per-track list of instruments
     * @param trackVolumes     per-track list of volumes (silently dropped — pending Phase 3 follow-up)
     */
    public static Sequence buildSequence(Piece piece, List<List<Instrument>> trackInstruments,
                                         List<List<Integer>> trackVolumes)
            throws InvalidMidiDataException {
        try {
            Performance perf = PieceConcretizer.concretize(piece);
            Performance overridden = applyInstrumentOverrides(perf, piece, trackInstruments);
            byte[] bytes = MidiCodec.toMidi(overridden);
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
                new Instrumentation(newInstr), base.articulations());
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
            defaults.add(List.of(track.defaultInstrument()));
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
            defaults.add(List.of(track.defaultInstrument()));
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
    public static long computeLeadingPaddingTicks(Piece piece) {
        long globalMin = Long.MAX_VALUE;
        for (Track track : piece.tracks()) {
            // Control tracks carry only tempo markers inside VoidPhrase-shaped
            // content; their "leading padding" would be the entire piece and
            // would clobber the audible minimum. Skip them.
            if (piece.isControlTrack(track.name())) continue;
            globalMin = Math.min(globalMin, leadingPaddingForTrack(track));
            for (Track auxTrack : track.auxTracks()) {
                globalMin = Math.min(globalMin, leadingPaddingForTrack(auxTrack));
            }
        }
        return globalMin == Long.MAX_VALUE ? 0 : globalMin;
    }

    private static long leadingPaddingForTrack(Track track) {
        long padding = 0;
        for (Phrase phrase : track.phrases()) {
            long[] result = leadingPaddingForPhrase(phrase);
            padding += result[0];
            if (result[1] == 0) {
                // Hit a non-padding node — done
                return padding;
            }
            // result[1] == 1 means entire phrase was padding, continue
        }
        return padding;
    }

    /**
     * Returns [paddingTicks, allPadding] where allPadding is 1 if the
     * entire phrase consisted of only padding (and zero-duration markers).
     */
    private static long[] leadingPaddingForPhrase(Phrase phrase) {
        return switch (phrase) {
            case MelodicPhrase mp -> leadingPaddingForNodes(mp.nodes());
            case DrumPhrase dp -> leadingPaddingForNodes(dp.nodes());
            case ChordPhrase cp -> new long[]{0, cp.chords().isEmpty() ? 1 : 0};
            case RestPhrase rp -> new long[]{0, 0}; // rest is real content
            case VoidPhrase vp -> new long[]{0, 0}; // void is real (silent) content
            case LyricPhrase lp -> new long[]{0, 0}; // lyrics are real content
            case ShiftedPhrase sp -> leadingPaddingForPhrase(sp.source());
            case LayeredPhrase lp -> leadingPaddingForPhrase(lp.resolve());
        };
    }

    private static long[] leadingPaddingForNodes(List<PhraseNode> nodes) {
        long padding = 0;
        for (PhraseNode node : nodes) {
            switch (node) {
                case PaddingNode p -> padding += MidiMapper.toTicks(p.duration());
                case DynamicNode d -> {} // zero duration, skip
                case TempoChangeNode t -> {}
                case TempoTransitionStartNode t -> {}
                case TempoTransitionEndNode t -> {}
                default -> { return new long[]{padding, 0}; } // NoteNode, RestNode, etc.
            }
        }
        return new long[]{padding, 1}; // all padding
    }

}
