package music.notation.experiments.hirajoshi;

import music.notation.duration.Duration;
import music.notation.performance.ConcreteNote;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.Track;
import music.notation.expressivity.TrackId;
import music.notation.performance.TrackKind;
import music.notation.experiments.scale.ScalePitchResolver;
import music.notation.experiments.scale.TimedNote;

import java.util.ArrayList;
import java.util.List;

import static music.notation.experiments.hirajoshi.HirajoshiDegree.*;

/**
 * A small demo melody written purely in abstract {@link HirajoshiNote}
 * terms - no bound tonic, no concrete pitch.
 *
 * <p>Shape: a simple four-bar lament with an ascending arch, descending
 * counter, and final resolution on the tonic. The "Japanese" quality
 * comes from the {@link HirajoshiDegree} pattern (2-1-4-1-4 semitones),
 * not from any hard-coded pitches.</p>
 */
public final class HirajoshiSong {

    private HirajoshiSong() {}

    private static final int Q  = 500;   // quarter at 120 BPM
    private static final int HN = 1000;  // half at 120 BPM

    public static List<TimedNote<HirajoshiNote>> demo() {
        return List.of(
                TimedNote.of(HirajoshiNote.of(I,   4), Q),
                TimedNote.of(HirajoshiNote.of(III, 4), Q),
                TimedNote.of(HirajoshiNote.of(V,   4), Q),
                TimedNote.of(HirajoshiNote.of(III, 4), Q),

                TimedNote.of(HirajoshiNote.of(VI,  4), Q),
                TimedNote.of(HirajoshiNote.of(V,   4), Q),
                TimedNote.of(HirajoshiNote.of(III, 4), Q),
                TimedNote.of(HirajoshiNote.of(II,  4), Q),

                TimedNote.of(HirajoshiNote.of(I,   4), Q),
                TimedNote.of(HirajoshiNote.of(II,  4), Q),
                TimedNote.of(HirajoshiNote.of(III, 4), Q),
                TimedNote.of(HirajoshiNote.of(V,   4), Q),

                TimedNote.of(HirajoshiNote.of(III, 4), HN),
                TimedNote.of(HirajoshiNote.of(I,   4), HN)
        );
    }

    /**
     * Concretize the whole melody into a unified {@link Performance}
     * using the given pitch resolver. Onsets are computed by walking the
     * melody in order; each {@code TimedNote}'s duration becomes its
     * {@link PitchedNote#duration()} (ms projected at 120 bpm).
     *
     * <p>Works for any {@link ScalePitchResolver} - use a
     * {@link HirajoshiConcretizer} for an authentic rendering, or a
     * different scale's resolver after a {@code ScaleTranspose} to
     * re-colour the contour.</p>
     */
    public static <N extends music.notation.experiments.scale.ScaleNote> Performance concretize(
            List<TimedNote<N>> melody,
            ScalePitchResolver<N> resolver) {
        // The melody is authored in ms; project at the default 120 bpm
        // (whole note = 2000 ms) into the musical Duration timeline.
        Duration cursor = Duration.zero();
        var notes = new ArrayList<ConcreteNote>(melody.size());
        for (var tn : melody) {
            Duration dur = Duration.of(tn.durationMillis(), 2000);
            notes.add(new PitchedNote(cursor, dur, resolver.midi(tn.note())));
            cursor = cursor.plus(dur);
        }
        TrackId id = new TrackId("hirajoshi");
        Track track = new Track(id, TrackKind.PITCHED, notes);
        return Performance.of(new Score(List.of(track)));
    }
}
