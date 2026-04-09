package music.notation.play;

import music.notation.event.Instrument;
import music.notation.phrase.*;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import javax.sound.midi.*;
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
     * Convenience for tests and simple playback.
     */
    public static Sequence buildSequence(Piece piece) throws InvalidMidiDataException {
        var defaults = new ArrayList<List<Instrument>>();
        for (Track track : piece.tracks()) {
            defaults.add(List.of(track.defaultInstrument()));
        }
        return buildSequence(piece, defaults);
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
        Sequence sequence = new Sequence(Sequence.PPQ, MidiMapper.TICKS_PER_QUARTER);

        int[] nextChannel = {0};
        boolean[] tempoAdded = {false};
        List<Track> tracks = piece.tracks();

        for (int t = 0; t < tracks.size(); t++) {
            Track track = tracks.get(t);
            List<Instrument> instruments = trackInstruments.get(t);

            // Interpret and render main track
            renderTrack(sequence, track, instruments, nextChannel, tempoAdded, piece);

            // Render aux tracks — each uses its own default instrument
            for (Track auxTrack : track.auxTracks()) {
                renderTrack(sequence, auxTrack, List.of(auxTrack.defaultInstrument()),
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
        stop();
        paused = false;

        Sequence sequence = buildSequence(piece, trackInstruments);
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
            case LyricPhrase lp -> new long[]{0, 0}; // lyrics are real content
            case ShiftedPhrase sp -> leadingPaddingForPhrase(sp.source());
        };
    }

    private static long[] leadingPaddingForNodes(List<PhraseNode> nodes) {
        long padding = 0;
        for (PhraseNode node : nodes) {
            switch (node) {
                case PaddingNode p -> padding += MidiMapper.toTicks(p.duration());
                case DynamicNode d -> {} // zero duration, skip
                case GraceNote g -> { return new long[]{padding, 0}; }
                case SlurStart s -> {}
                case SlurEnd s -> {}
                default -> { return new long[]{padding, 0}; } // NoteNode, RestNode, etc.
            }
        }
        return new long[]{padding, 1}; // all padding
    }

    private static void renderTrack(Sequence sequence, Track track, List<Instrument> instruments,
                                     int[] nextChannel, boolean[] tempoAdded, Piece piece)
            throws InvalidMidiDataException {
        PhraseInterpreter interpreter = new PhraseInterpreter(0, 80);
        for (Phrase phrase : track.phrases()) {
            interpreter.interpret(phrase);
        }
        List<PlayEvent> noteEvents = interpreter.getEvents().stream()
                .filter(e -> !(e instanceof PlayEvent.ProgramChange))
                .toList();

        for (Instrument instrument : instruments) {
            javax.sound.midi.Track midiTrack = sequence.createTrack();

            boolean isDrum = instrument == Instrument.DRUM_KIT;
            int channel = isDrum ? 9 : nextChannel[0]++;
            if (!isDrum && channel == 9) {
                channel = nextChannel[0]++;
            }

            if (!tempoAdded[0]) {
                addTempoEvent(midiTrack, piece.tempo().bpm());
                tempoAdded[0] = true;
            }

            if (!isDrum) {
                ShortMessage pcMsg = new ShortMessage();
                pcMsg.setMessage(ShortMessage.PROGRAM_CHANGE, channel, instrument.program(), 0);
                midiTrack.add(new MidiEvent(pcMsg, 0));
            }

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
                }
            }
        }
    }

    private static void addTempoEvent(javax.sound.midi.Track track, int bpm) throws InvalidMidiDataException {
        int mpq = 60_000_000 / bpm;
        byte[] data = new byte[]{
                (byte) ((mpq >> 16) & 0xFF),
                (byte) ((mpq >> 8) & 0xFF),
                (byte) (mpq & 0xFF)
        };
        MetaMessage tempo = new MetaMessage();
        tempo.setMessage(0x51, data, 3);
        track.add(new MidiEvent(tempo, 0));
    }
}
