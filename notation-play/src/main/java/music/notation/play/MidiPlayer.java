package music.notation.play;

import music.notation.event.Instrument;
import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.phrase.*;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import javax.sound.midi.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
     * @param piece            the piece to render
     * @param trackInstruments per-track list of instruments; each track's phrases are
     *                         interpreted once, then duplicated onto a separate MIDI
     *                         channel for every instrument in that track's list
     */
    public static Sequence buildSequence(Piece piece, List<List<Instrument>> trackInstruments)
            throws InvalidMidiDataException {
        // Default all volumes to 100 (out of 127)
        var defaultVolumes = new ArrayList<List<Integer>>();
        for (var instruments : trackInstruments) {
            defaultVolumes.add(instruments.stream().map(i -> 100).toList());
        }
        return buildSequence(piece, trackInstruments, defaultVolumes);
    }

    /**
     * Build a MIDI Sequence with explicit instrument and volume assignments per track.
     *
     * @param trackInstruments per-track list of instruments
     * @param trackVolumes     per-track list of volumes (0–127), parallel to trackInstruments
     */
    public static Sequence buildSequence(Piece piece, List<List<Instrument>> trackInstruments,
                                         List<List<Integer>> trackVolumes)
            throws InvalidMidiDataException {
        Sequence sequence = new Sequence(Sequence.PPQ, MidiMapper.TICKS_PER_QUARTER);

        int[] nextChannel = {0};
        boolean[] tempoAdded = {false};
        List<Track> tracks = piece.tracks();

        // Pass 1 — control tracks emit tempo events into their own MIDI tracks.
        // Marking tempoAdded[0] = true here prevents music-track fallback below
        // from duplicating tempo on the first music track.
        for (Track track : tracks) {
            if (piece.isControlTrack(track.name())) {
                renderControlTrack(sequence, track, tempoAdded, piece);
            }
        }

        // Pass 2 — music tracks. Skips control tracks. First music track still
        // emits an initial tempo event if no control track has (backward-compat
        // for flat-constructed pieces that don't use control tracks at all).
        for (int t = 0; t < tracks.size(); t++) {
            Track track = tracks.get(t);
            if (piece.isControlTrack(track.name())) continue;

            List<Instrument> instruments = trackInstruments.get(t);
            List<Integer> volumes = trackVolumes.get(t);

            renderTrack(sequence, track, instruments, volumes, nextChannel, tempoAdded, piece);

            for (Track auxTrack : track.auxTracks()) {
                renderTrack(sequence, auxTrack, instruments, volumes,
                        nextChannel, tempoAdded, piece);
            }
        }
        return sequence;
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
                case SlurStart s -> {}
                case SlurEnd s -> {}
                case TempoChangeNode t -> {}
                case TempoTransitionStartNode t -> {}
                case TempoTransitionEndNode t -> {}
                default -> { return new long[]{padding, 0}; } // NoteNode, RestNode, etc.
            }
        }
        return new long[]{padding, 1}; // all padding
    }

    /**
     * Render a control track: emit its tempo events (and an initial tempo at
     * tick 0 if none was declared) into a dedicated MIDI track. No channel,
     * no program change, no note events. Marks {@code tempoAdded[0] = true}
     * so music tracks skip fallback tempo emission.
     */
    private static void renderControlTrack(Sequence sequence, Track track,
                                           boolean[] tempoAdded, Piece piece)
            throws InvalidMidiDataException {
        PhraseInterpreter interpreter = new PhraseInterpreter(0, 80, piece.tempo().bpm());
        for (Phrase phrase : track.phrases()) {
            interpreter.interpret(phrase);
        }
        List<PlayEvent.TempoChange> tempoEvents = interpreter.getEvents().stream()
                .filter(e -> e instanceof PlayEvent.TempoChange)
                .map(e -> (PlayEvent.TempoChange) e)
                .toList();

        javax.sound.midi.Track midiTrack = sequence.createTrack();

        if (!tempoAdded[0]) {
            boolean hasTempoAtZero = tempoEvents.stream().anyMatch(t -> t.tick() == 0);
            if (!hasTempoAtZero) {
                addTempoEvent(midiTrack, 0, piece.tempo().bpm());
            }
            for (PlayEvent.TempoChange tc : tempoEvents) {
                addTempoEvent(midiTrack, tc.tick(), tc.bpm());
            }
            tempoAdded[0] = true;
        } else {
            // Unexpected — another control track already claimed tempo. Still
            // emit this track's tempo events onto its own MIDI track (MIDI
            // readers merge tempo events across tracks), but without the
            // initial-tempo fallback.
            for (PlayEvent.TempoChange tc : tempoEvents) {
                addTempoEvent(midiTrack, tc.tick(), tc.bpm());
            }
        }
    }

    private static void renderTrack(Sequence sequence, Track track, List<Instrument> instruments,
                                     List<Integer> volumes, int[] nextChannel,
                                     boolean[] tempoAdded, Piece piece)
            throws InvalidMidiDataException {
        PhraseInterpreter interpreter = new PhraseInterpreter(0, 80, piece.tempo().bpm());
        for (Phrase phrase : track.phrases()) {
            interpreter.interpret(phrase);
        }
        List<PlayEvent> allEvents = interpreter.getEvents();
        List<PlayEvent> noteEvents = allEvents.stream()
                .filter(e -> !(e instanceof PlayEvent.ProgramChange)
                        && !(e instanceof PlayEvent.TempoChange))
                .toList();
        List<PlayEvent.TempoChange> tempoEvents = allEvents.stream()
                .filter(e -> e instanceof PlayEvent.TempoChange)
                .map(e -> (PlayEvent.TempoChange) e)
                .toList();

        for (int idx = 0; idx < instruments.size(); idx++) {
            Instrument instrument = instruments.get(idx);
            int volume = idx < volumes.size() ? volumes.get(idx) : 100;

            javax.sound.midi.Track midiTrack = sequence.createTrack();

            boolean isDrum = instrument == Instrument.DRUM_KIT;
            int channel = isDrum ? 9 : nextChannel[0]++;
            if (!isDrum && channel == 9) {
                channel = nextChannel[0]++;
            }

            if (!tempoAdded[0]) {
                boolean hasTempoAtZero = tempoEvents.stream().anyMatch(t -> t.tick() == 0);
                if (!hasTempoAtZero) {
                    addTempoEvent(midiTrack, 0, piece.tempo().bpm());
                }
                for (PlayEvent.TempoChange tc : tempoEvents) {
                    addTempoEvent(midiTrack, tc.tick(), tc.bpm());
                }
                tempoAdded[0] = true;
            }

            if (!isDrum) {
                ShortMessage pcMsg = new ShortMessage();
                pcMsg.setMessage(ShortMessage.PROGRAM_CHANGE, channel, instrument.program(), 0);
                midiTrack.add(new MidiEvent(pcMsg, 0));
            }

            // Set channel volume (MIDI CC #7)
            ShortMessage volMsg = new ShortMessage();
            volMsg.setMessage(ShortMessage.CONTROL_CHANGE, channel, 7,
                    Math.clamp(volume, 0, 127));
            midiTrack.add(new MidiEvent(volMsg, 0));

            for (PlayEvent event : noteEvents) {
                switch (event) {
                    case PlayEvent.NoteOn on -> {
                        ShortMessage msg = new ShortMessage();
                        msg.setMessage(ShortMessage.NOTE_ON, channel, on.midiNote(), on.velocity());
                        midiTrack.add(new MidiEvent(msg, on.tick()));
                    }
                    case PlayEvent.NoteOff off -> {
                        ShortMessage msg = new ShortMessage();
                        msg.setMessage(ShortMessage.NOTE_OFF, channel, off.midiNote(), 0);
                        midiTrack.add(new MidiEvent(msg, off.tick()));
                    }
                    case PlayEvent.ProgramChange pc -> {} // handled above
                    case PlayEvent.TempoChange tc -> {} // handled above, per-track
                }
            }
        }
    }

    private static void addTempoEvent(javax.sound.midi.Track track, long tick, int bpm)
            throws InvalidMidiDataException {
        int mpq = 60_000_000 / bpm;
        byte[] data = new byte[]{
                (byte) ((mpq >> 16) & 0xFF),
                (byte) ((mpq >> 8) & 0xFF),
                (byte) (mpq & 0xFF)
        };
        MetaMessage tempo = new MetaMessage();
        tempo.setMessage(0x51, data, 3);
        track.add(new MidiEvent(tempo, tick));
    }
}
