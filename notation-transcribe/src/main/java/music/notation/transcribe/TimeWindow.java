package music.notation.transcribe;

/**
 * A stretch of the audio file to analyse, in seconds from the start of
 * the file. {@code endSec} of {@code 0} means "to the end".
 *
 * <pre>{@code  { "startSec": 12.5, "endSec": 40 } }</pre>
 */
public record TimeWindow(double startSec, double endSec) {

    public static final TimeWindow WHOLE = new TimeWindow(0, 0);

    public TimeWindow {
        if (!(startSec >= 0)) throw new IllegalArgumentException("startSec must be >= 0: " + startSec);
        if (!(endSec >= 0)) throw new IllegalArgumentException("endSec must be >= 0: " + endSec);
        if (endSec != 0 && endSec <= startSec)
            throw new IllegalArgumentException("endSec must be after startSec: " + startSec + "–" + endSec);
    }

    public boolean toEnd() { return endSec == 0; }
}
