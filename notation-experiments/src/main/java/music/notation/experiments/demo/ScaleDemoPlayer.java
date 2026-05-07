package music.notation.experiments.demo;

import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.hirajoshi.HirajoshiSong;
import music.notation.experiments.hirajoshi.PianoRollDisplay;
import music.notation.performance.ConcreteNote;
import music.notation.performance.Instrumentation;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.Track;
import music.notation.expressivity.TrackId;
import music.notation.performance.TrackKind;
import music.notation.experiments.scale.ScaleNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Shared playback primitive for the scale-tour demos. Takes an abstract
 * motif (in any source {@link ScaleNote} type), a scale mapping, and a
 * {@link ScalePitchResolver} for the target scale — builds a unified
 * {@link Performance} and either plays it through a synthesiser
 * with a synced piano-roll TUI or prints the TUI silently.
 */
public final class ScaleDemoPlayer {

    public static final int GM_PROGRAM_KOTO = 107;
    public static final long GAP_BETWEEN_SCALES_MS = 900;

    private static final TrackId DEMO_TRACK_ID = new TrackId("demo");

    private ScaleDemoPlayer() {}

    /**
     * Transpose an abstract motif into a target scale, build a
     * {@link Performance} from it, and render it with a piano-roll TUI
     * (+ audio unless {@code silent}).
     *
     * @param toScale       maps each source note to a target-scale note
     * @param pitchResolver resolves target notes to MIDI integers (scale
     *                      concretizers implement this natively)
     */
    public static <From extends ScaleNote, To extends ScaleNote> void playScale(
            String title,
            List<TimedNote<From>> motif,
            Function<From, To> toScale,
            ScalePitchResolver<To> pitchResolver,
            boolean silent) {

        final var performance = buildPerformance(motif, toScale, pitchResolver);
        final var roll = new PianoRollDisplay(title, performance);

        if (silent) {
            roll.printWhole(performance);
            return;
        }

        roll.printHeader();
        playWithSyncedDisplay(performance, roll);
        roll.printFooter();
    }

    private static <From extends ScaleNote, To extends ScaleNote> Performance buildPerformance(
            List<TimedNote<From>> motif,
            Function<From, To> toScale,
            ScalePitchResolver<To> pitchResolver) {
        long cursor = 0;
        var notes = new ArrayList<ConcreteNote>(motif.size());
        for (var tn : motif) {
            To target = toScale.apply(tn.note());
            int midi = pitchResolver.midi(target);
            notes.add(new PitchedNote(cursor, tn.durationMillis(), midi));
            cursor += tn.durationMillis();
        }
        Track track = new Track(DEMO_TRACK_ID, TrackKind.PITCHED, notes);
        Score score = new Score(List.of(track));
        return new Performance(
                score,
                music.notation.performance.TempoTrack.empty(),
                Instrumentation.single(DEMO_TRACK_ID, GM_PROGRAM_KOTO),
                music.notation.expressivity.Articulations.empty());
    }

    private static void playWithSyncedDisplay(Performance performance, PianoRollDisplay roll) {
        Synthesizer synth = null;
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            MidiChannel channel = synth.getChannels()[0];
            channel.programChange(GM_PROGRAM_KOTO);

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
    }

    public static boolean isSilent(String[] args) {
        return Arrays.stream(args).anyMatch(a -> "--silent".equals(a) || "-s".equals(a));
    }

    public static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Default motif used by the Japanese / Chinese tour demos. */
    public static List<TimedNote<HirajoshiNote>> defaultMotif() {
        return HirajoshiSong.demo();
    }
}
