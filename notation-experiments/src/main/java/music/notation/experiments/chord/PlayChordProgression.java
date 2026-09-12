package music.notation.experiments.chord;

import music.notation.experiments.chinese.gong.GongConcretizer;
import music.notation.experiments.chinese.gong.GongNote;
import music.notation.experiments.demo.ScaleDemoPlayer;
import music.notation.performance.ConcreteNote;
import music.notation.performance.Instrumentation;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.Track;
import music.notation.expressivity.TrackId;
import music.notation.performance.TrackKind;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plays a chord progression in three {@link ChordShape}s, now driven
 * entirely through {@link Performance} - the unified concrete form.
 *
 * <p>Pass {@code --silent} / {@code -s} for visual-only output.</p>
 */
public final class PlayChordProgression {

    private static final int GM_PROGRAM_ACOUSTIC_GUITAR = 24;  // Nylon
    private static final long GAP_BETWEEN_SHAPES_MS = 900;

    private static final TrackId PROGRESSION_TRACK = new TrackId("progression");

    private PlayChordProgression() {}

    public static void main(String[] args) {
        final boolean silent = ScaleDemoPlayer.isSilent(args);
        final var concretizer = new ChordConcretizer<>(GongConcretizer.inC(), PROGRESSION_TRACK);

        renderShape("Block chords (all tones simultaneous)",
                ChordProgression.demoIn(ChordShape.BLOCK), concretizer, silent);
        if (!silent) ScaleDemoPlayer.pause(GAP_BETWEEN_SHAPES_MS);

        renderShape("Arpeggio up (tones ascending through the list)",
                ChordProgression.demoIn(ChordShape.ARPEGGIO_UP), concretizer, silent);
        if (!silent) ScaleDemoPlayer.pause(GAP_BETWEEN_SHAPES_MS);

        renderShape("Arpeggio down (tones descending)",
                ChordProgression.demoIn(ChordShape.ARPEGGIO_DOWN), concretizer, silent);
    }

    private static void renderShape(String label,
                                    List<ScaleChord<GongNote>> chords,
                                    ChordConcretizer<GongNote> concretizer,
                                    boolean silent) {
        System.out.println();
        System.out.println("  === " + label + " ===");
        System.out.println();

        // Build one Track covering the whole progression by extracting the
        // notes from each per-chord Track and shifting their tickMs.
        var notes = new ArrayList<ConcreteNote>();
        long cursor = 0;
        for (var chord : chords) {
            Track piece = concretizer.concretize(chord);
            for (ConcreteNote n : piece.notes()) {
                notes.add(shiftBy(n, cursor));
            }
            cursor += chord.durationMs();
        }
        Track wholeTrack = new Track(PROGRESSION_TRACK, TrackKind.PITCHED, notes);
        Score score = new Score(List.of(wholeTrack));
        Performance whole = new Performance(
                score,
                music.notation.performance.TempoTrack.empty(),
                Instrumentation.single(PROGRESSION_TRACK, GM_PROGRAM_ACOUSTIC_GUITAR),
                music.notation.expressivity.Articulations.empty());

        printPerformanceText(whole);

        if (silent) return;
        playPerformance(whole);
    }

    /** Text view: one line per note, sorted canonically. */
    private static void printPerformanceText(Performance perf) {
        System.out.println("  onset   | off     | pitch | event");
        System.out.println("  --------+---------+-------+---------");
        var mapper = new music.notation.performance.TimeMapper(perf.tempo());
        for (Track track : perf.score().tracks()) {
            for (ConcreteNote n : track.notes()) {
                if (n instanceof PitchedNote pn) {
                    System.out.printf(
                            "  %5dms | %5dms | %-5s | note%n",
                            mapper.toMs(pn.at()), mapper.toMs(pn.endAt()),
                            noteName(pn.midi()));
                }
            }
        }
    }

    /**
     * Play a {@code Performance} through the default JVM synth.
     */
    private static void playPerformance(Performance perf) {
        Synthesizer synth = null;
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            MidiChannel channel = synth.getChannels()[0];
            channel.programChange(GM_PROGRAM_ACOUSTIC_GUITAR);

            record Dispatch(long timeMs, Runnable action) {}
            var schedule = new ArrayList<Dispatch>();
            var mapper = new music.notation.performance.TimeMapper(perf.tempo());

            for (Track track : perf.score().tracks()) {
                for (ConcreteNote n : track.notes()) {
                    if (n instanceof PitchedNote pn) {
                        schedule.add(new Dispatch(mapper.toMs(pn.at()),
                                () -> channel.noteOn(pn.midi(), 80)));
                        schedule.add(new Dispatch(mapper.toMs(pn.endAt()),
                                () -> channel.noteOff(pn.midi())));
                    }
                }
            }
            schedule.sort(Comparator.comparingLong(Dispatch::timeMs));

            long now = 0;
            for (var d : schedule) {
                if (d.timeMs() > now) {
                    ScaleDemoPlayer.pause(d.timeMs() - now);
                    now = d.timeMs();
                }
                d.action().run();
            }
        } catch (MidiUnavailableException e) {
            System.err.println("  (no synthesizer available: " + e.getMessage() + ")");
        } finally {
            if (synth != null && synth.isOpen()) {
                synth.close();
            }
        }
    }

    /**
     * Shift a note's musical onset by {@code deltaMs} expressed as a
     * Duration. The chord-progression experiment supplies offsets in
     * ms; here we project at the default 120 bpm into the Duration
     * timeline so the resulting Performance is consistently musical.
     */
    private static ConcreteNote shiftBy(ConcreteNote n, long deltaMs) {
        music.notation.duration.Duration delta =
                music.notation.duration.Duration.of(deltaMs, 2000);
        if (n instanceof PitchedNote pn) {
            return new PitchedNote(pn.at().plus(delta), pn.duration(), pn.midi());
        }
        if (n instanceof music.notation.performance.DrumNote dn) {
            return new music.notation.performance.DrumNote(
                    dn.at().plus(delta), dn.duration(), dn.piece());
        }
        throw new IllegalStateException("unknown ConcreteNote: " + n);
    }

    private static final String[] FLAT_NAMES = {
            "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"
    };

    private static String noteName(int midi) {
        int pc = Math.floorMod(midi, 12);
        int oct = Math.floorDiv(midi, 12) - 1;
        return FLAT_NAMES[pc] + oct;
    }
}
