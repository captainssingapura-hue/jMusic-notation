package music.notation.structure;

import music.notation.phrase.Bar;

import java.util.List;

/**
 * A named percussion track holding bars of drum content directly — the
 * planned replacement for the {@link Track} + {@link
 * music.notation.phrase.DrumPhrase} pairing.
 *
 * <p><b>Status: Phase 4a sketch.</b> Not yet wired into the playback or
 * build pipeline; existing songs continue to use {@link Track} with
 * {@link music.notation.event.Instrument#DRUM_KIT}.</p>
 *
 * <p>Compared to today's {@code Track} for percussion:</p>
 * <ul>
 *   <li>The kind is in the type — no need for the
 *       {@code defaultInstrument == DRUM_KIT} sentinel check.</li>
 *   <li>{@code bars} replaces {@code phrases} — bars hold percussion
 *       events directly with no {@code DrumPhrase} wrapper. Bar
 *       contents in a drum track are expected to be
 *       {@link music.notation.phrase.PercussionNote} (validation lands
 *       in a later phase as the migration concretises).</li>
 *   <li>{@code auxTracks} is typed as {@code List<DrumTrack>} —
 *       auxiliary voices on a drum track are themselves drums.</li>
 * </ul>
 *
 * <p>The kit (e.g. Standard, Jazz, Brush, Power) is implicit at MIDI
 * render time — drum tracks always route to MIDI channel 9 where the
 * GM percussion map applies.</p>
 */
public record DrumTrack(
        String name,
        List<Bar> bars,
        List<DrumTrack> auxTracks
) {
    public DrumTrack {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DrumTrack name must be non-blank");
        }
        bars = List.copyOf(bars);
        auxTracks = List.copyOf(auxTracks);
    }

    /** Convenience: a drum track with no aux tracks. */
    public static DrumTrack of(String name, List<Bar> bars) {
        return new DrumTrack(name, bars, List.of());
    }

    /** Convenience: a drum track with bars passed varargs-style. */
    public static DrumTrack of(String name, Bar... bars) {
        return new DrumTrack(name, List.of(bars), List.of());
    }
}
