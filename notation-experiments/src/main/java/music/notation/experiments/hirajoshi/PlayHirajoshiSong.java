package music.notation.experiments.hirajoshi;

import music.notation.performance.ConcreteNote;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;
import java.util.Arrays;

/**
 * Plays the {@link HirajoshiSong#demo demo melody} via the unified
 * {@link Performance} - same concrete form as chord concretization,
 * so the playback loop is uniform across all demos.
 *
 * <p>Pass {@code --silent} / {@code -s} for visual-only output.</p>
 */
public final class PlayHirajoshiSong {

    private static final int GM_PROGRAM_KOTO = 107;
    private static final int GM_PROGRAM_SHAMISEN = 106;

    private PlayHirajoshiSong() {}

    public static void main(String[] args) {
        final boolean silent = isSilent(args);

        playInKey("Hirajoshi in C (Koto)",     HirajoshiConcretizer.inC(), GM_PROGRAM_KOTO, silent);
        if (!silent) pause(500);
        playInKey("Hirajoshi in D (Shamisen)", HirajoshiConcretizer.inD(), GM_PROGRAM_SHAMISEN, silent);
    }

    private static void playInKey(String label,
                                  HirajoshiConcretizer concretizer,
                                  int program,
                                  boolean silent) {
        final var performance = HirajoshiSong.concretize(HirajoshiSong.demo(), concretizer);
        final var roll = new PianoRollDisplay(label, performance);

        if (silent) {
            roll.printWhole(performance);
            return;
        }

        roll.printHeader();

        Synthesizer synth = null;
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            MidiChannel channel = synth.getChannels()[0];
            channel.programChange(program);

            long now = 0;
            for (Track track : performance.score().tracks()) {
                for (ConcreteNote n : track.notes()) {
                    if (!(n instanceof PitchedNote note)) continue;

                    if (note.tickMs() > now) {
                        pause(note.tickMs() - now);
                        now = note.tickMs();
                    }
                    channel.noteOn(note.midi(), 80);
                    roll.printAttack(note);

                    long rowCursor = now + PianoRollDisplay.ROW_MILLIS;
                    pause(PianoRollDisplay.ROW_MILLIS);
                    now += PianoRollDisplay.ROW_MILLIS;
                    while (rowCursor < note.offTickMs()) {
                        roll.printSustain(note, rowCursor);
                        pause(PianoRollDisplay.ROW_MILLIS);
                        rowCursor += PianoRollDisplay.ROW_MILLIS;
                        now = rowCursor;
                    }
                    channel.noteOff(note.midi());
                }
            }
        } catch (MidiUnavailableException e) {
            System.err.println("  (no synthesizer available: " + e.getMessage() + ")");
            roll.printWhole(performance);
        } finally {
            if (synth != null && synth.isOpen()) {
                synth.close();
            }
        }
        roll.printFooter();
    }

    private static boolean isSilent(String[] args) {
        return Arrays.stream(args).anyMatch(a -> "--silent".equals(a) || "-s".equals(a));
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
