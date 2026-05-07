package music.notation.mxl;

import music.notation.performance.MidiCodec;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;

/**
 * Minimal CLI entry: read an {@code .mxl} file, parse it to a
 * {@link music.notation.performance.Performance}, encode it as MIDI via
 * {@link MidiCodec}, and play it through the JVM's default
 * {@link Sequencer}.
 *
 * <p>Usage:</p>
 * <pre>
 * mvn -pl notation-mxl -am package
 * mvn -pl notation-mxl exec:java -Dexec.mainClass=music.notation.mxl.MxlPlay \
 *     -Dexec.args="C:/path/to/score.mxl"
 * </pre>
 *
 * <p>The intent is a quick sanity demo, not a UI. Actual audio output
 * needs a working MIDI synthesizer on the host (true on a typical
 * desktop, false in headless CI).</p>
 */
public final class MxlPlay {

    private MxlPlay() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: MxlPlay <path-to-.mxl>");
            System.exit(2);
        }
        Path mxl = Path.of(args[0]);
        System.out.println("Reading: " + mxl);
        play(MxlReader.read(mxl));
    }

    /** Print a one-line summary, encode to MIDI, and play through the JVM synth. */
    public static void play(MxlImport imp) throws Exception {
        var perf = imp.performance();
        long totalNotes = perf.score().tracks().stream()
                .mapToLong(t -> t.notes().size()).sum();
        System.out.printf("Loaded %d tracks, %d notes; initial tempo %d bpm; key %s; time %d/%d%n",
                perf.score().tracks().size(),
                totalNotes,
                perf.tempo().changes().isEmpty() ? -1 : perf.tempo().changes().get(0).bpm(),
                KeyDisplay.format(imp.key()),
                imp.timeSig().beats(), imp.timeSig().beatValue());

        byte[] midi = MidiCodec.toMidi(perf);
        Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(midi));

        try (Sequencer sequencer = MidiSystem.getSequencer()) {
            sequencer.open();
            sequencer.setSequence(sequence);
            System.out.println("Playing... (Ctrl-C to stop)");
            sequencer.start();
            while (sequencer.isRunning()) {
                Thread.sleep(200);
            }
        }
        System.out.println("Done.");
    }
}
