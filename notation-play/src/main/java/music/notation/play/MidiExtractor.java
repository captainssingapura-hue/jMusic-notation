package music.notation.play;

import music.notation.performance.InstrumentMap;
import music.notation.performance.PercussionMap;
import music.notation.performance.Quantizer;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Extracts a Standard MIDI File into human-readable text score files,
 * one per track. The output format is designed to be both readable and
 * easily translatable into Java {@code StaffPhraseBuilderTyped} code.
 *
 * <p>Usage: {@code java MidiExtractor <file.mid> [output-dir]}</p>
 *
 * <p>Output format per track file:</p>
 * <pre>
 * # Title (from MIDI metadata if available)
 * key: C
 * time: 4/4
 * tempo: 120
 *
 * --- track: Track 1 (ACOUSTIC_GRAND_PIANO)
 *
 * | 1
 * o4 C QUARTER
 * o4 E EIGHTH
 * r EIGHTH
 * o4 G HALF
 *
 * | 2
 * o4 A,C#,E QUARTER    (chord)
 * ...
 * </pre>
 */
public final class MidiExtractor {

    private static final int PPQ_DEFAULT = 480;
    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    // Code-emit names for each legal duration. Quantization grid lives
    // in {@link Quantizer}; this map only serves the text-rendering side.
    private static final Map<Integer, String> DURATION_NAMES = new HashMap<>();
    static {
        DURATION_NAMES.put(1,  "SIXTY_FOURTH");
        DURATION_NAMES.put(2,  "THIRTY_SECOND");
        DURATION_NAMES.put(3,  "THIRTY_SECOND.dot()");
        DURATION_NAMES.put(4,  "SIXTEENTH");
        DURATION_NAMES.put(6,  "SIXTEENTH.dot()");
        DURATION_NAMES.put(8,  "EIGHTH");
        DURATION_NAMES.put(12, "EIGHTH.dot()");
        DURATION_NAMES.put(16, "QUARTER");
        DURATION_NAMES.put(24, "QUARTER.dot()");
        DURATION_NAMES.put(32, "HALF");
        DURATION_NAMES.put(48, "HALF.dot()");
        DURATION_NAMES.put(64, "WHOLE");
    }

    /** Code-emit name for a GM program (delegates to {@link InstrumentMap}). */
    private static String instrumentNameFor(int program) {
        return InstrumentMap.forProgram(program).map(Enum::name).orElse(null);
    }

    /** Code-emit name for a drum-channel note (delegates to {@link PercussionMap}). */
    private static String drumNameFor(int midiNote) {
        return PercussionMap.forNote(midiNote).map(Enum::name).orElse(null);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java MidiExtractor <file.mid> [output-dir]");
            System.exit(1);
        }
        File midiFile = new File(args[0]);
        Path outputDir = args.length > 1 ? Path.of(args[1]) : midiFile.toPath().getParent();
        if (outputDir == null) outputDir = Path.of(".");
        Files.createDirectories(outputDir);

        extract(midiFile, outputDir);
    }

    public static void extract(File midiFile, Path outputDir) throws InvalidMidiDataException, IOException {
        Sequence sequence = MidiSystem.getSequence(midiFile);
        int ppq = sequence.getResolution();
        if (sequence.getDivisionType() != Sequence.PPQ) {
            System.err.println("Warning: non-PPQ division type. Timing may be approximate.");
        }

        // Sixty-fourths per tick: 1 quarter = 16 sixty-fourths, 1 quarter = ppq ticks
        // So 1 tick = 16.0 / ppq sixty-fourths
        double sfPerTick = 16.0 / ppq;

        // Global: extract tempo events from all tracks
        int initialTempo = 120;
        int timeSigNum = 4, timeSigDen = 4;
        String title = baseName(midiFile);
        List<TempoEntry> tempoMap = new ArrayList<>();

        // First pass: collect metadata from all tracks
        for (javax.sound.midi.Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage msg = event.getMessage();
                if (msg instanceof MetaMessage meta) {
                    switch (meta.getType()) {
                        case 0x03 -> { // Track name — use first non-empty as title
                            String name = new String(meta.getData()).trim();
                            if (!name.isEmpty() && title.equals(baseName(midiFile))) {
                                title = name;
                            }
                        }
                        case 0x51 -> { // Tempo
                            byte[] data = meta.getData();
                            int mpq = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                            int bpm = 60_000_000 / mpq;
                            tempoMap.add(new TempoEntry(event.getTick(), bpm));
                            if (event.getTick() == 0) initialTempo = bpm;
                        }
                        case 0x58 -> { // Time signature
                            byte[] data = meta.getData();
                            if (data.length >= 2) {
                                timeSigNum = data[0] & 0xFF;
                                timeSigDen = 1 << (data[1] & 0xFF);
                            }
                        }
                    }
                }
            }
        }
        if (tempoMap.isEmpty()) tempoMap.add(new TempoEntry(0, initialTempo));
        tempoMap.sort(Comparator.comparingLong(e -> e.tick));

        int sfPerBar = timeSigNum * (64 / timeSigDen);

        // Second pass: extract each track
        javax.sound.midi.Track[] tracks = sequence.getTracks();
        int trackIndex = 0;

        for (javax.sound.midi.Track track : tracks) {
            // Collect events for this track
            String trackName = null;
            String instrumentName = "ACOUSTIC_GRAND_PIANO";
            boolean isDrum = false;
            int channel = -1;

            // NoteOn events keyed by note number for matching NoteOff
            Map<Integer, Long> pendingNotes = new HashMap<>();
            List<NoteEvent> notes = new ArrayList<>();

            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage msg = event.getMessage();

                if (msg instanceof MetaMessage meta && meta.getType() == 0x03) {
                    trackName = new String(meta.getData()).trim();
                }

                if (msg instanceof ShortMessage sm) {
                    int cmd = sm.getCommand();
                    int ch = sm.getChannel();
                    if (channel == -1) channel = ch;
                    isDrum = (ch == 9);

                    if (cmd == ShortMessage.PROGRAM_CHANGE) {
                        String mapped = instrumentNameFor(sm.getData1());
                        instrumentName = mapped != null ? mapped : "PROGRAM_" + sm.getData1();
                    } else if (cmd == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                        pendingNotes.put(sm.getData1(), event.getTick());
                    } else if (cmd == ShortMessage.NOTE_OFF
                            || (cmd == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                        Long startTick = pendingNotes.remove(sm.getData1());
                        if (startTick != null) {
                            long durationTicks = event.getTick() - startTick;
                            notes.add(new NoteEvent(sm.getData1(), startTick, durationTicks));
                        }
                    }
                }
            }

            // Skip empty tracks (metadata-only)
            if (notes.isEmpty()) {
                trackIndex++;
                continue;
            }

            if (isDrum) instrumentName = "DRUM_KIT";
            if (trackName == null || trackName.isEmpty()) trackName = "Track " + trackIndex;

            // Sort by start tick, then by pitch (for chord grouping)
            notes.sort(Comparator.comparingLong(NoteEvent::startTick).thenComparingInt(NoteEvent::midiNote));

            // Write output file
            String fileName = sanitizeFileName(trackName) + ".txt";
            Path outFile = outputDir.resolve(fileName);

            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outFile))) {
                pw.println("# " + title);
                pw.println("key: C");
                pw.println("time: " + timeSigNum + "/" + timeSigDen);
                pw.println("tempo: " + initialTempo);
                pw.println();
                pw.println("--- track: " + trackName + " (" + instrumentName + ")");
                pw.println();

                // Write tempo changes (if any beyond initial)
                for (TempoEntry te : tempoMap) {
                    if (te.tick > 0) {
                        int bar = (int) (te.tick * sfPerTick / sfPerBar) + 1;
                        pw.println("# tempo change at bar ~" + bar + ": " + te.bpm + " BPM");
                    }
                }
                if (tempoMap.size() > 1) pw.println();

                // Group notes into bars and write
                writeNotes(pw, notes, sfPerTick, sfPerBar, isDrum);
            }

            System.out.println("Wrote: " + outFile);
            trackIndex++;
        }
    }

    private static void writeNotes(PrintWriter pw, List<NoteEvent> notes,
                                    double sfPerTick, int sfPerBar, boolean isDrum) {
        int currentBar = -1;
        double cursorSf = 0; // current position within bar, in sixty-fourths
        int i = 0;

        while (i < notes.size()) {
            NoteEvent note = notes.get(i);
            int bar = (int) (note.startTick * sfPerTick / sfPerBar) + 1;
            double posInBar = (note.startTick * sfPerTick) % sfPerBar;

            // Bar header
            if (bar != currentBar) {
                // Fill leading rest bars (before any notes)
                if (currentBar == -1) {
                    for (int b = 1; b < bar; b++) {
                        pw.println("| " + b);
                        pw.println("r " + durationName(sfPerBar));
                        pw.println();
                    }
                } else {
                    // Fill gap bars between last note's bar and this note's bar
                    for (int b = currentBar + 1; b < bar; b++) {
                        pw.println("| " + b);
                        pw.println("r " + durationName(sfPerBar));
                        pw.println();
                    }
                }
                pw.println("| " + bar);
                currentBar = bar;
                cursorSf = 0;
            }

            // Insert rest if there's a gap before this note
            int noteSfPos = quantizeSf(posInBar);
            int cursorPos = quantizeSf(cursorSf);
            if (noteSfPos > cursorPos) {
                int gapSf = noteSfPos - cursorPos;
                emitRests(pw, gapSf);
            }

            // Check for chord: notes starting at the same tick
            List<NoteEvent> chord = new ArrayList<>();
            chord.add(note);
            while (i + 1 < notes.size() && notes.get(i + 1).startTick == note.startTick) {
                i++;
                chord.add(notes.get(i));
            }

            // Quantize duration from the first note
            int durationSf = quantizeSf(note.durationTicks * sfPerTick);
            String durStr = durationName(durationSf);

            // Advance cursor
            cursorSf = posInBar + durationSf;

            if (isDrum) {
                // Drum notes: one per line with percussion name
                for (NoteEvent dn : chord) {
                    String mapped = drumNameFor(dn.midiNote);
                    String drumName = mapped != null ? mapped : "NOTE_" + dn.midiNote;
                    pw.println(drumName + " " + durStr);
                }
            } else if (chord.size() == 1) {
                // Single note
                pw.println(noteName(note.midiNote) + " " + durStr);
            } else {
                // Chord: o4 C,E,G QUARTER
                int octave = (chord.getFirst().midiNote / 12) - 1;
                StringJoiner pitches = new StringJoiner(",");
                for (NoteEvent cn : chord) {
                    int o = (cn.midiNote / 12) - 1;
                    String n = NOTE_NAMES[cn.midiNote % 12];
                    if (o != octave) {
                        pitches.add(n + "(o" + o + ")");
                    } else {
                        pitches.add(n);
                    }
                }
                pw.println("o" + octave + " " + pitches + " " + durStr);
            }

            i++;
        }
        pw.println();
    }

    /**
     * Emit rests that sum to the given sixty-fourths, using the largest standard
     * durations that fit (greedy decomposition).
     */
    private static void emitRests(PrintWriter pw, int totalSf) {
        int remaining = totalSf;
        while (remaining > 0) {
            Integer dur = Quantizer.floor(remaining);
            if (dur == null) break;
            pw.println("r " + DURATION_NAMES.get(dur));
            remaining -= dur;
        }
    }

    private static String noteName(int midiNote) {
        int octave = (midiNote / 12) - 1;
        String name = NOTE_NAMES[midiNote % 12];
        return "o" + octave + " " + name;
    }

    /** Snap a fractional sixty-fourths value to the nearest legal duration. */
    private static int quantizeSf(double rawSf) {
        return Quantizer.snap(rawSf);
    }

    private static String durationName(int sf) {
        String name = DURATION_NAMES.get(sf);
        if (name != null) return name;
        // Try quantizing to the nearest legal value, annotate the original.
        int quantized = Quantizer.snap(sf);
        name = DURATION_NAMES.get(quantized);
        if (name != null) return name + " # ~" + sf + "sf";
        // Fallback: express as sixty-fourths
        return sf + "sf";
    }

    private static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_").replaceAll("_+", "_");
    }

    private record NoteEvent(int midiNote, long startTick, long durationTicks) {}
    private record TempoEntry(long tick, int bpm) {}
}
