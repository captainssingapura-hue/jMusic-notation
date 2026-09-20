package music.notation.guitar;

import java.util.List;
import java.util.Objects;

/**
 * A loopable slice of the arrangement — whole bars, with the notes that
 * fall in them and a summary of what the hand does there.
 *
 * <ul>
 *   <li>{@code index} — 1-based order in the breakdown.</li>
 *   <li>{@code bars} — the bars, 1-based inclusive.</li>
 *   <li>{@code notes} — the fingered notes starting inside the range.</li>
 *   <li>{@code fretLow}/{@code fretHigh}, {@code stringLow}/{@code stringHigh} — the neck area used.</li>
 *   <li>{@code shifts} — hand-position changes inside the range.</li>
 *   <li>{@code notesPerBeat} — density, as a first difficulty hint.</li>
 * </ul>
 */
public record PracticeRange(int index, BarRange bars, List<FingeredNote> notes,
                            int fretLow, int fretHigh, int stringLow, int stringHigh,
                            int shifts, double notesPerBeat) {

    public PracticeRange {
        Objects.requireNonNull(bars, "bars");
        Objects.requireNonNull(notes, "notes");
        notes = List.copyOf(notes);
    }

    static PracticeRange of(int index, BarRange bars, List<FingeredNote> notes, Meter meter) {
        var f = new Fingering(notes, 0);
        double beats = bars.bars() * meter.timeSignature().beats();
        return new PracticeRange(index, bars, notes,
                f.lowestFret(), f.highestFret(), f.lowestString(), f.highestString(),
                f.shifts(), notes.size() / beats);
    }

    /** One-line description, e.g. {@code #3  bars 19–20  ·  12 notes  ·  frets 6–10  ·  strings 1–4  ·  1 shift}. */
    public String summary() {
        return String.format("#%-2d %-12s · %2d notes · frets %2d–%-2d · strings %d–%d · %d shift%s · %.1f notes/beat",
                index, bars, notes.size(), fretLow, fretHigh, stringLow, stringHigh,
                shifts, shifts == 1 ? "" : "s", notesPerBeat);
    }
}
