package music.notation.experiments.pi;

import music.notation.experiments.blues.major.BluesMajorConcretizer;
import music.notation.experiments.blues.major.BluesMajorNote;
import music.notation.experiments.blues.minor.BluesMinorConcretizer;
import music.notation.experiments.blues.minor.BluesMinorNote;
import music.notation.experiments.chinese.gong.GongConcretizer;
import music.notation.experiments.chinese.gong.GongNote;
import music.notation.experiments.chinese.jue.JueConcretizer;
import music.notation.experiments.chinese.jue.JueNote;
import music.notation.experiments.chinese.shang.ShangConcretizer;
import music.notation.experiments.chinese.shang.ShangNote;
import music.notation.experiments.chinese.yu.YuConcretizer;
import music.notation.experiments.chinese.yu.YuNote;
import music.notation.experiments.chinese.zhi.ZhiConcretizer;
import music.notation.experiments.chinese.zhi.ZhiNote;
import music.notation.experiments.hirajoshi.HirajoshiConcretizer;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.insen.InsenConcretizer;
import music.notation.experiments.insen.InsenNote;
import music.notation.experiments.iwato.IwatoConcretizer;
import music.notation.experiments.iwato.IwatoNote;
import music.notation.performance.MidiCodec;
import music.notation.performance.Performance;
import music.notation.performance.Swing;
import music.notation.expressivity.TrackId;
import music.notation.experiments.ryukyu.RyukyuConcretizer;
import music.notation.experiments.ryukyu.RyukyuNote;
import music.notation.experiments.scale.ScaleFactory;
import music.notation.experiments.scale.ScaleNote;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.yo.YoConcretizer;
import music.notation.experiments.yo.YoNote;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Maps the first 250 digits of π to notes in a chosen scale and plays the
 * result as a three-voice canon, with a synced multi-voice piano-roll TUI.
 *
 * <p>Voices:</p>
 * <ul>
 *   <li><b>Voice 0 (lead)</b> — high register, enters at tick 0.</li>
 *   <li><b>Voice 1 (mid)</b> — one octave below, enters 8 notes later.</li>
 *   <li><b>Voice 2 (bass)</b> — two octaves below, enters 16 notes later.</li>
 * </ul>
 *
 * <p>All three voices play the same digit-driven melody. Each voice is a
 * separate {@link music.notation.performance.Track} on its own
 * MIDI channel — the canon emerges automatically from the time-staggered
 * entries.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   mvn -pl notation-experiments exec:java \
 *       -Dexec.mainClass=music.notation.experiments.pi.PlayPiSong \
 *       -Dexec.args="--scale hirajoshi"
 * </pre>
 *
 * <p>Pass {@code --scale &lt;name&gt;} (default: {@code hirajoshi}).
 * Pass {@code --silent} / {@code -s} to skip audio playback (useful in
 * sandboxes); the TUI is still printed.</p>
 *
 * <p>Available scales:</p>
 * <ul>
 *   <li>Japanese pentatonic — {@code hirajoshi}, {@code yo}, {@code insen},
 *       {@code iwato}, {@code ryukyu}</li>
 *   <li>Chinese pentatonic — {@code gong}, {@code shang}, {@code jue},
 *       {@code zhi}, {@code yu}</li>
 *   <li>Blues hexatonic — {@code blues-major}, {@code blues-minor}</li>
 * </ul>
 */
public final class PlayPiSong {

    private static final String DEFAULT_SCALE = "hirajoshi";

    /** Voice 0 sits at this octave; lower voices are stacked below it. */
    private static final int  LEAD_OCTAVE = 5;
    /** Brisk eighth-note feel. */
    private static final long NOTE_MS     = 220;
    /** Notes between successive voice entries (the canon's "delay"). */
    private static final int  CANON_ENTRY_DIGITS = 8;

    private static final int GM_KOTO   = 107;
    private static final int GM_PIZZ   = 45;    // pizzicato strings — Chinese pentatonic
    private static final int GM_GUITAR = 27;    // electric guitar clean — blues

    /** Three voices, each its own Track, octave-staggered with delayed entries. */
    private static final List<PiSong.Voice> CANON_VOICES = List.of(
            new PiSong.Voice(new TrackId("lead"), LEAD_OCTAVE,     0 * CANON_ENTRY_DIGITS),
            new PiSong.Voice(new TrackId("mid"),  LEAD_OCTAVE - 1, 1 * CANON_ENTRY_DIGITS),
            new PiSong.Voice(new TrackId("bass"), LEAD_OCTAVE - 2, 2 * CANON_ENTRY_DIGITS)
    );

    private PlayPiSong() {}

    public static void main(String[] args) throws Exception {
        String scaleName = parseScale(args).toLowerCase(Locale.ROOT);
        boolean silent = isSilent(args);
        Double swingOverride = parseSwing(args); // null => use scale default
        String midiOut = parseStringFlag(args, "--midi-out");

        String digits = loadPiDigits();
        Performance performance = buildForScale(scaleName, digits);

        // Auto-swing for blues; otherwise rely on CLI override.
        double swingRatio = swingOverride != null
                ? swingOverride
                : (isBlues(scaleName) ? Swing.TRIPLET : Swing.NONE);
        if (swingRatio != Swing.NONE) {
            performance = Swing.apply(performance, swingRatio);
        }

        int leadNotes = performance.score().tracks().get(0).notes().size();
        long durationMs = (leadNotes + (CANON_VOICES.size() - 1) * CANON_ENTRY_DIGITS) * NOTE_MS;

        String swingLabel = swingRatio == Swing.NONE
                ? "straight"
                : String.format("swing %.2f", swingRatio);
        System.out.printf("pi canon - %s, 3 voices, %d notes/voice, %.1fs, %s%n",
                scaleName, leadNotes, durationMs / 1000.0, swingLabel);

        var roll = new CanonRollDisplay("pi canon (" + scaleName + ", " + swingLabel + ")", performance);
        byte[] midi = MidiCodec.toMidi(performance);

        if (midiOut != null) {
            Files.write(Path.of(midiOut), midi);
            System.out.println("  saved MIDI to " + midiOut + " (" + midi.length + " bytes)");
        }

        if (silent) {
            roll.printWhole(performance);
            System.out.println("  (silent: " + midi.length + " bytes of MIDI generated, "
                    + "playback skipped)");
            return;
        }

        playWithSyncedDisplay(performance, midi, roll);
    }

    private static Performance buildForScale(String name, String digits) {
        return switch (name) {
            // Japanese pentatonic
            case "hirajoshi" -> canon(digits, HirajoshiNote::ofIndex, HirajoshiConcretizer.inC(), GM_KOTO);
            case "yo"        -> canon(digits, YoNote::ofIndex,        YoConcretizer.inC(),        GM_KOTO);
            case "insen"     -> canon(digits, InsenNote::ofIndex,     InsenConcretizer.inC(),     GM_KOTO);
            case "iwato"     -> canon(digits, IwatoNote::ofIndex,     IwatoConcretizer.inC(),     GM_KOTO);
            case "ryukyu"    -> canon(digits, RyukyuNote::ofIndex,    RyukyuConcretizer.inC(),    GM_KOTO);
            // Chinese pentatonic
            case "gong"  -> canon(digits, GongNote::ofIndex,  GongConcretizer.inC(),  GM_PIZZ);
            case "shang" -> canon(digits, ShangNote::ofIndex, ShangConcretizer.inC(), GM_PIZZ);
            case "jue"   -> canon(digits, JueNote::ofIndex,   JueConcretizer.inC(),   GM_PIZZ);
            case "zhi"   -> canon(digits, ZhiNote::ofIndex,   ZhiConcretizer.inC(),   GM_PIZZ);
            case "yu"    -> canon(digits, YuNote::ofIndex,    YuConcretizer.inC(),    GM_PIZZ);
            // Blues hexatonic
            case "blues-major", "bluesmajor" ->
                    canon(digits, BluesMajorNote::ofIndex, BluesMajorConcretizer.inC(), GM_GUITAR);
            case "blues-minor", "bluesminor", "blues" ->
                    canon(digits, BluesMinorNote::ofIndex, BluesMinorConcretizer.inC(), GM_GUITAR);
            default -> throw new IllegalArgumentException(
                    "unknown scale: '" + name + "'. Try one of: " + String.join(", ", scaleNames()));
        };
    }

    private static <N extends ScaleNote> Performance canon(
            String digits, ScaleFactory<N> factory, ScalePitchResolver<N> resolver, int program) {
        return PiSong.buildCanon(digits, factory, resolver, CANON_VOICES, NOTE_MS, program);
    }

    private static java.util.List<String> scaleNames() {
        var m = new LinkedHashMap<String, String>();
        for (String s : new String[]{
                "hirajoshi", "yo", "insen", "iwato", "ryukyu",
                "gong", "shang", "jue", "zhi", "yu",
                "blues-major", "blues-minor"}) {
            m.put(s, s);
        }
        return new java.util.ArrayList<>(m.keySet());
    }

    private static String loadPiDigits() throws IOException {
        try (InputStream in = PlayPiSong.class.getResourceAsStream("/pi_250.txt")) {
            if (in == null) throw new IOException("resource pi_250.txt not found on classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private static String parseScale(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--scale".equals(a) && i + 1 < args.length) return args[i + 1];
            if (a.startsWith("--scale=")) return a.substring("--scale=".length());
        }
        return DEFAULT_SCALE;
    }

    private static boolean isSilent(String[] args) {
        for (String a : args) {
            if ("--silent".equals(a) || "-s".equals(a)) return true;
        }
        return false;
    }

    /**
     * Returns the swing ratio override, or {@code null} to use the
     * scale-family default (triplet swing for blues, none otherwise).
     *
     * <ul>
     *   <li>{@code --no-swing} → {@code Swing.NONE} (force straight)</li>
     *   <li>{@code --swing} → {@code Swing.TRIPLET} (force triplet feel)</li>
     *   <li>{@code --swing=0.6} → custom ratio</li>
     * </ul>
     */
    private static Double parseSwing(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--no-swing".equals(a)) return Swing.NONE;
            if ("--swing".equals(a)) {
                // bare flag, optional value next
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    return Double.parseDouble(args[i + 1]);
                }
                return Swing.TRIPLET;
            }
            if (a.startsWith("--swing=")) {
                return Double.parseDouble(a.substring("--swing=".length()));
            }
        }
        return null;
    }

    private static boolean isBlues(String scaleName) {
        return scaleName.startsWith("blues");
    }

    private static String parseStringFlag(String[] args, String flag) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (flag.equals(a) && i + 1 < args.length) return args[i + 1];
            if (a.startsWith(flag + "=")) return a.substring(flag.length() + 1);
        }
        return null;
    }

    /**
     * Start MIDI playback in the background, then drive the TUI on the
     * main thread one row at a time so the printed grid stays in sync
     * with what the speakers are doing.
     */
    private static void playWithSyncedDisplay(Performance perf, byte[] midi, CanonRollDisplay roll)
            throws Exception {
        Sequencer sequencer = MidiSystem.getSequencer();
        sequencer.open();
        try {
            Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(midi));
            sequencer.setSequence(seq);

            roll.printHeader();
            sequencer.start();

            long total = roll.totalMs();
            for (long t = 0; t <= total; t += CanonRollDisplay.ROW_MILLIS) {
                roll.printRow(perf, t);
                Thread.sleep(CanonRollDisplay.ROW_MILLIS);
            }
            // Tail: let the final note ring out.
            Thread.sleep(600);
            roll.printFooter();
        } finally {
            sequencer.stop();
            sequencer.close();
        }
    }
}
