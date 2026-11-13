package music.notation.transcribe;

import music.notation.duration.Duration;

/**
 * A transcribed note placed on the bar grid.
 *
 * @param at       onset from the start of bar 1 (whole-note fractions)
 * @param duration snapped length
 * @param midi     pitch
 * @param cents    tuning of the sung note relative to {@code midi}
 * @param velocity 1–127, relative to the loudest note of the take
 * @param bar      1-based bar number (bar 1 is the pickup bar if there is one)
 * @param beat     1-based beat within the bar, in the time signature's beat unit
 * @param source   the unquantised note it came from
 */
public record QuantizedNote(Duration at, Duration duration, int midi, double cents, int velocity,
                            int bar, double beat, TranscribedNote source) {
}
