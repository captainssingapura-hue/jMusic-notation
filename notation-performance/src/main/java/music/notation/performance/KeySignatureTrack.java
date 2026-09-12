package music.notation.performance;

import music.notation.duration.Duration;
import music.notation.structure.KeySignature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Piece-wide sparse key-signature timeline, anchored at musical
 * positions. Empty means no explicit key signature is recorded on the
 * playback timeline; the importer's static initial key applies.
 * Consecutive same-value entries are deduped, matching the
 * {@link TempoTrack} convention.
 *
 * <p>Populated by the MXL importer when the score contains mid-piece
 * <code>&lt;key&gt;</code> elements (modulations).</p>
 */
public record KeySignatureTrack(List<KeySignatureChange> changes) {
    public KeySignatureTrack {
        Objects.requireNonNull(changes, "changes");
        List<KeySignatureChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparing(KeySignatureChange::at,
                (a, b) -> a.compareDuration(b)));
        List<KeySignatureChange> deduped = new ArrayList<>(sorted.size());
        KeySignature last = null;
        for (KeySignatureChange c : sorted) {
            if (!c.key().equals(last)) {
                deduped.add(c);
                last = c.key();
            }
        }
        changes = List.copyOf(deduped);
    }

    public static KeySignatureTrack empty() {
        return new KeySignatureTrack(List.of());
    }

    public static KeySignatureTrack constant(KeySignature key) {
        return new KeySignatureTrack(List.of(new KeySignatureChange(Duration.zero(), key)));
    }
}
