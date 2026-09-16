package music.notation.expressivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Per-track hairpin timeline — sparse list of {@link HairpinSpan}s
 * sorted by start position. Overlaps are permitted by the model (the
 * record's compact constructor doesn't reject them) but are
 * typically an authoring error; the {@code MusicXmlParser} logs a
 * warning when it encounters them.
 *
 * <p>An empty control means "no hairpins on this track" — the
 * codec/renderer falls back to flat playback between the discrete
 * {@link VelocityChange}/{@link VolumeChange} set-points.</p>
 */
public record HairpinControl(List<HairpinSpan> spans) {
    public HairpinControl {
        Objects.requireNonNull(spans, "spans");
        List<HairpinSpan> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparing(HairpinSpan::from,
                (a, b) -> a.compareDuration(b)));
        spans = List.copyOf(sorted);
    }

    public static HairpinControl empty() {
        return new HairpinControl(List.of());
    }
}
