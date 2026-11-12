package music.notation.transcribe;

import music.notation.duration.Duration;
import music.notation.structure.KeySignature;
import music.notation.structure.TimeSignature;

/**
 * How real time maps onto bars, and the key to write in.
 *
 * <pre>{@code
 *   { "bpm": 92, "downbeatSec": 1.30, "timeSignature": { "beats": 4, "beatValue": 4 },
 *     "key": "auto", "grid": "1/16" }
 * }</pre>
 *
 * @param bpm           quarter notes per minute — the take's tempo
 *                      (known when the singer had a click or count-in)
 * @param downbeatSec   file time of the first bar's beat 1; {@code null}
 *                      = the first note's onset. Notes before it become
 *                      a pickup.
 * @param timeSignature bar length; default 4/4
 * @param key           {@code "auto"} to estimate from the notes, or a
 *                      key such as {@code "Bb major"}, {@code "E minor"}
 * @param grid          onsets and releases snap to multiples of this
 *                      (whole-note fraction, e.g. {@code "1/16"}; use
 *                      {@code "1/12"} for eighth-note triplets). Must
 *                      divide the bar.
 */
public record TimingSpec(int bpm, Double downbeatSec, TimeSignature timeSignature, String key, Duration grid) {

    public static final String AUTO = "auto";
    public static final TimingSpec DEFAULT = new TimingSpec(120, null, null, null, null);

    public TimingSpec {
        if (bpm < 20 || bpm > 400) throw new IllegalArgumentException("bpm must be in 20–400: " + bpm);
        if (downbeatSec != null && !(downbeatSec >= 0)) throw new IllegalArgumentException("downbeatSec must be >= 0");
        if (timeSignature == null) timeSignature = new TimeSignature(4, 4);
        if (key == null || key.isBlank()) key = AUTO;
        if (!key.equalsIgnoreCase(AUTO)) Keys.parse(key);                       // validate early
        if (grid == null) grid = Duration.of(1, 16);
        if (grid.compareDuration(Duration.zero()) <= 0) throw new IllegalArgumentException("grid must be > 0");
        long num = (long) timeSignature.beats() * grid.denominator();
        long den = (long) timeSignature.beatValue() * grid.numerator();
        if (num % den != 0)
            throw new IllegalArgumentException("grid " + grid.numerator() + "/" + grid.denominator()
                    + " does not divide a bar of " + timeSignature.beats() + "/" + timeSignature.beatValue());
    }

    public TimingSpec(int bpm) { this(bpm, null, null, null, null); }

    public boolean autoKey() { return key.equalsIgnoreCase(AUTO); }

    /** The requested key, or {@code null} for {@code "auto"}. */
    public KeySignature fixedKey() { return autoKey() ? null : Keys.parse(key); }

    /** Grid steps in one bar. */
    public int stepsPerBar() {
        return (int) (((long) timeSignature.beats() * grid.denominator())
                / ((long) timeSignature.beatValue() * grid.numerator()));
    }

    /** Length of a whole note in milliseconds at this tempo. */
    public double wholeNoteMs() { return 240_000.0 / bpm; }

    public double gridWholes() { return (double) grid.numerator() / grid.denominator(); }

    public TimingSpec withDownbeat(double sec) { return new TimingSpec(bpm, sec, timeSignature, key, grid); }
}
