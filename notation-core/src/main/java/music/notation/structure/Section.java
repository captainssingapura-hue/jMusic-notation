package music.notation.structure;

import music.notation.duration.Duration;
import music.notation.phrase.Bar;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseConnection;
import music.notation.phrase.PhraseMarking;
import music.notation.phrase.VoidPhrase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A homogeneous vertical slice of a {@link Piece}: a named region that
 * contains a {@link SectionTrack} for every track declared at piece level.
 *
 * <p>Sections provide:</p>
 * <ul>
 *   <li><b>Alignment correctness by construction</b> — every track (and
 *       every aux voice within) must sum to the section's declared
 *       {@link #duration}. Mismatches throw at construction with a
 *       localised message (section name + track name).</li>
 *   <li><b>Reuse</b> — sections are immutable records; the same section
 *       can appear multiple times in a piece's section list.</li>
 *   <li><b>Modulation</b> — each section may override the piece-level
 *       scale via {@link #keyOverride}.</li>
 * </ul>
 *
 * <p>A section carries no track-ordering; ordering is declared once at
 * the piece level via {@code List<TrackDecl>}. The section's
 * {@code tracks} map uses {@link LinkedHashMap} preservation when built
 * via {@link Builder} but ordering is not load-bearing — the piece
 * re-keys by declared name during the join.</p>
 */
public record Section(
        String name,
        Duration duration,
        Optional<KeySignature> keyOverride,
        Map<String, SectionTrack> tracks
) {

    public Section {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Section name must be non-blank");
        }
        Objects.requireNonNull(duration, "duration");
        if (duration.sixtyFourths() <= 0) {
            throw new IllegalArgumentException(
                    "Section '" + name + "': duration must be positive, got " + duration.sixtyFourths());
        }
        Objects.requireNonNull(keyOverride, "keyOverride");
        tracks = Map.copyOf(tracks);
        validateDurations(name, duration, tracks);
    }

    /** Start a fluent builder for a section with the given name. */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /**
     * Verify every phrase list (main and aux) in every track totals exactly
     * the section's declared duration. Error messages carry section + track
     * name + declared vs. actual counts to make misalignments diagnose
     * themselves.
     */
    private static void validateDurations(String sectionName, Duration duration,
                                          Map<String, SectionTrack> tracks) {
        final int expected = duration.sixtyFourths();
        for (var entry : tracks.entrySet()) {
            final String trackName = entry.getKey();
            final SectionTrack st = entry.getValue();
            final int actual = totalSixtyFourths(st.phrases());
            if (actual != expected) {
                throw new IllegalArgumentException(String.format(
                        "Section '%s' / track '%s': phrases total %d/64 but section duration is %d/64 (diff %+d/64)",
                        sectionName, trackName, actual, expected, actual - expected));
            }
            for (int auxIdx = 0; auxIdx < st.auxTracks().size(); auxIdx++) {
                SectionTrack aux = st.auxTracks().get(auxIdx);
                int auxActual = totalSixtyFourths(aux.phrases());
                if (auxActual != expected) {
                    throw new IllegalArgumentException(String.format(
                            "Section '%s' / track '%s' / aux[%d]: phrases total %d/64 but section duration is %d/64 (diff %+d/64)",
                            sectionName, trackName, auxIdx, auxActual, expected, auxActual - expected));
                }
            }
        }
    }

    private static int totalSixtyFourths(List<Phrase> phrases) {
        int total = 0;
        for (Phrase p : phrases) {
            total += Bar.phraseSixtyFourths(p);
        }
        return total;
    }

    // ── Fluent builder ─────────────────────────────────────────────────

    /**
     * Fluent builder for {@link Section}. Use for readability in long
     * declarations; the raw record constructor works for terse cases.
     *
     * <p>{@code .track(name, phrases)} declares content for a named track;
     * {@code .silent(name)} declares the track is silent for this section
     * and auto-fills a full-length {@link VoidPhrase}. At {@link #build()}
     * time the builder asserts every declared track has been supplied,
     * delegates to the record constructor, and the record's invariant
     * then verifies duration alignment.</p>
     */
    public static final class Builder {
        private final String name;
        private Duration duration;
        private TimeSignature ts;   // needed if .silent(...) is used (for VoidPhrase construction)
        private Optional<KeySignature> keyOverride = Optional.empty();
        private final Map<String, SectionTrack> tracks = new LinkedHashMap<>();

        Builder(String name) {
            this.name = name;
        }

        /**
         * Declare the section's total duration. Required before any
         * {@code silent(...)} call (so the VoidPhrase can be sized) and
         * before {@link #build()}.
         */
        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Declare the section's time signature — needed only if {@code silent(...)}
         * is used; VoidPhrase requires a TimeSignature for its bar-sixty-fourths.
         */
        public Builder timeSignature(TimeSignature ts) {
            this.ts = ts;
            return this;
        }

        /** Optional scale override for this section. Inherits from piece if absent. */
        public Builder scale(KeySignature keyOverride) {
            this.keyOverride = Optional.ofNullable(keyOverride);
            return this;
        }

        /** Add a track's content by phrases (no aux voices). */
        public Builder track(String trackName, List<Phrase> phrases) {
            return track(trackName, new SectionTrack(phrases, List.of()));
        }

        /** Add a track's content by single phrase (no aux voices). */
        public Builder track(String trackName, Phrase phrase) {
            return track(trackName, new SectionTrack(List.of(phrase), List.of()));
        }

        /** Add a track's content with aux voices declared. */
        public Builder track(String trackName, SectionTrack sectionTrack) {
            if (tracks.containsKey(trackName)) {
                throw new IllegalArgumentException(
                        "Section '" + name + "': track '" + trackName + "' already declared");
            }
            tracks.put(trackName, sectionTrack);
            return this;
        }

        /**
         * Declare a track silent for this section — auto-fills a full-length
         * {@link VoidPhrase}. Requires {@link #duration} and
         * {@link #timeSignature} to have been set first.
         */
        public Builder silent(String trackName) {
            if (duration == null) {
                throw new IllegalStateException(
                        "Section '" + name + "': .silent('" + trackName + "') requires .duration(...) set first");
            }
            if (ts == null) {
                throw new IllegalStateException(
                        "Section '" + name + "': .silent('" + trackName + "') requires .timeSignature(...) set first");
            }
            int barSize = ts.barSixtyFourths();
            if (duration.sixtyFourths() % barSize != 0) {
                throw new IllegalArgumentException(
                        "Section '" + name + "': duration " + duration.sixtyFourths()
                                + "/64 is not a whole number of " + barSize + "/64 bars");
            }
            int bars = duration.sixtyFourths() / barSize;
            VoidPhrase voidPhrase = VoidPhrase.ofBars(ts, bars,
                    new PhraseMarking(PhraseConnection.ATTACCA, false));
            return track(trackName, new SectionTrack(List.of(voidPhrase), List.of()));
        }

        public Section build() {
            Objects.requireNonNull(duration,
                    "Section '" + name + "': .duration(...) must be set before .build()");
            // Preserve builder insertion order in the resulting map.
            return new Section(name, duration, keyOverride,
                    new LinkedHashMap<>(tracks));
        }
    }
}
