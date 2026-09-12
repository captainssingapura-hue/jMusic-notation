package music.notation.expressivity;

import music.notation.duration.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Per-track lyric timeline — a sparse list of {@link LyricEvent}s
 * sorted by musical position. Each event attaches one Unicode glyph
 * (or the {@code '_'} continuation marker) to a melodic onset.
 *
 * <p>The canonical form is sorted by {@link LyricEvent#at()} with at
 * most one event per position. Two events at the same position —
 * which never makes musical sense — get the <b>last</b> one kept
 * (matches the "latest write wins" convention used elsewhere in the
 * side-channel layer).</p>
 *
 * <p>An empty line means "no codec-emitted lyric meta events" — the
 * track plays unchanged.</p>
 */
public record LyricLine(List<LyricEvent> events) {
    public LyricLine {
        Objects.requireNonNull(events, "events");
        List<LyricEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(LyricEvent::at,
                (a, b) -> a.compareDuration(b)));
        // Same-position dedup: keep the last event at each position.
        List<LyricEvent> deduped = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            LyricEvent cur = sorted.get(i);
            boolean isLastAtPosition =
                    i + 1 == sorted.size()
                    || sorted.get(i + 1).at().compareDuration(cur.at()) != 0;
            if (isLastAtPosition) deduped.add(cur);
        }
        events = List.copyOf(deduped);
    }

    public static LyricLine empty() { return new LyricLine(List.of()); }
}
