package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Piece-wide sparse time-signature timeline, anchored at musical
 * positions. Empty means the piece has no explicit time signature on
 * the playback timeline (the runtime / importer-level static default
 * applies); consecutive same-value entries are deduped to keep the
 * representation canonical, matching the {@link TempoTrack}
 * convention.
 *
 * <p>An MXL importer fills this when the score contains mid-piece
 * <code>&lt;time&gt;</code> elements; the first entry, if present,
 * mirrors the {@link MusicalImport}'s static initial time
 * signature.</p>
 */
public record TimeSignatureTrack(List<TimeSignatureChange> changes) {
    public TimeSignatureTrack {
        Objects.requireNonNull(changes, "changes");
        List<TimeSignatureChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparing(TimeSignatureChange::at,
                (a, b) -> a.compareDuration(b)));
        List<TimeSignatureChange> deduped = new ArrayList<>(sorted.size());
        TimeSignature last = null;
        for (TimeSignatureChange c : sorted) {
            if (!c.timeSig().equals(last)) {
                deduped.add(c);
                last = c.timeSig();
            }
        }
        changes = List.copyOf(deduped);
    }

    public static TimeSignatureTrack empty() {
        return new TimeSignatureTrack(List.of());
    }

    public static TimeSignatureTrack constant(TimeSignature ts) {
        return new TimeSignatureTrack(List.of(new TimeSignatureChange(Duration.zero(), ts)));
    }
}
