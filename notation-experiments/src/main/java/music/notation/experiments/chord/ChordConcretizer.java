package music.notation.experiments.chord;

import music.notation.core.model.Concretizer;
import music.notation.performance.ConcreteNote;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.performance.TrackId;
import music.notation.performance.TrackKind;
import music.notation.experiments.scale.ScaleNote;
import music.notation.experiments.scale.ScalePitchResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Concretizes a {@link ScaleChord} into a {@link Track}.
 *
 * <p>Takes a {@link ScalePitchResolver} (not a full
 * {@code Concretizer<N, PitchedNote>}) — the chord supplies its own
 * timing via {@link ScaleChord#durationMs()} and {@link ChordShape}, so
 * all the resolver needs to do is map each voice to a MIDI integer.</p>
 *
 * <p>Shape dispatch:</p>
 * <ul>
 *   <li>{@link ChordShape#BLOCK} — every voice attacks at tick 0 and
 *       sustains for the full duration.</li>
 *   <li>{@link ChordShape#ARPEGGIO_UP} — voices attack in list order
 *       across staggered tick slots ({@code durationMs / voiceCount}
 *       each; any remainder lands on the final slot).</li>
 *   <li>{@link ChordShape#ARPEGGIO_DOWN} — voices attack in reverse list
 *       order; same slot timing.</li>
 * </ul>
 */
public record ChordConcretizer<N extends ScaleNote>(ScalePitchResolver<N> resolver, TrackId trackId)
        implements Concretizer<ScaleChord<N>, Track> {

    public ChordConcretizer {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        if (trackId == null) {
            throw new IllegalArgumentException("trackId must not be null");
        }
    }

    @Override
    public Track concretize(ScaleChord<N> chord) {
        final int n = chord.voiceCount();
        final int[] midis = new int[n];
        for (int i = 0; i < n; i++) {
            midis[i] = resolver.midi(chord.voices().get(i));
        }

        List<ConcreteNote> notes = switch (chord.shape()) {
            case BLOCK          -> block(midis, chord.durationMs());
            case ARPEGGIO_UP    -> arpeggio(midis, chord.durationMs(), /*reverse=*/ false);
            case ARPEGGIO_DOWN  -> arpeggio(midis, chord.durationMs(), /*reverse=*/ true);
        };
        return new Track(trackId, TrackKind.PITCHED, notes);
    }

    private static List<ConcreteNote> block(int[] midis, int durationMs) {
        var notes = new ArrayList<ConcreteNote>(midis.length);
        for (int midi : midis) {
            notes.add(new PitchedNote(0L, durationMs, midi));
        }
        return notes;
    }

    private static List<ConcreteNote> arpeggio(int[] midis, int totalMs, boolean reverse) {
        final int n = midis.length;
        final int slot = Math.max(1, totalMs / n);
        final int remainder = totalMs - slot * n;

        var notes = new ArrayList<ConcreteNote>(n);
        for (int i = 0; i < n; i++) {
            int srcIdx = reverse ? (n - 1 - i) : i;
            long on = (long) i * slot;
            int  dur = (i == n - 1) ? slot + remainder : slot;
            notes.add(new PitchedNote(on, dur, midis[srcIdx]));
        }
        return notes;
    }
}
