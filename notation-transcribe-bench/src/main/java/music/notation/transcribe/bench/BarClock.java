package music.notation.transcribe.bench;

import music.notation.duration.Duration;
import music.notation.transcribe.TimingSpec;
import music.notation.transcribe.Transcription;

/**
 * Maps between file time (seconds in the audio) and the transcription's
 * musical time, so the grid and the quantised notes can be drawn over
 * the waveform and the transcription can be played in step with it.
 * Musical time 0 is the start of bar 1, which sits {@code pickupBars}
 * bars before the downbeat.
 */
public record BarClock(double barOneSec, double wholeNoteSec, double barSec, double beatSec) {

    public static BarClock of(Transcription t) {
        return of(t.request().timing(), t.downbeatSec(), t.pickupBars());
    }

    public static BarClock of(TimingSpec timing, double downbeatSec, int pickupBars) {
        double whole = timing.wholeNoteMs() / 1000.0;
        double bar = whole * timing.timeSignature().beats() / timing.timeSignature().beatValue();
        double beat = whole / timing.timeSignature().beatValue();
        return new BarClock(downbeatSec - pickupBars * bar, whole, bar, beat);
    }

    /** File time of a musical position. */
    public double fileSec(Duration at) {
        return barOneSec + wholeNoteSec * at.numerator() / at.denominator();
    }

    /** File time of beat 1 of bar {@code bar} (1-based). */
    public double barStartSec(int bar) { return barOneSec + (bar - 1) * barSec; }
}
