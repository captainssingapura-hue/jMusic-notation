package music.notation.performance;

import music.notation.duration.BaseValue;
import music.notation.duration.Dotted;
import music.notation.duration.Duration;
import music.notation.duration.RawTuplet;
import music.notation.duration.Triplet;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A quantizer constraint profile — defines which durations the
 * {@link Quantizer} considers "legal targets" when snapping raw
 * MIDI timings.
 *
 * <p>Different MIDI sources benefit from different profiles:</p>
 * <ul>
 *   <li>Sheet-music-derived MIDI of a folk/pop tune: {@link #STANDARD}
 *       (powers-of-2 + dotted variants, no tuplets)</li>
 *   <li>Mozart, Beethoven, mainstream classical: {@link #WITH_TRIPLETS}
 *       (adds triplet eighths/sixteenths/etc.)</li>
 *   <li>Chopin, Liszt, Debussy: {@link #FULL} (adds quintuplets and
 *       septuplets)</li>
 *   <li>Live MIDI keyboard improvisation: {@link #IMPROV} (the same
 *       as {@link #FULL} but with a slight bias toward standard
 *       subdivisions when timings are ambiguous)</li>
 * </ul>
 *
 * <p>Custom profiles can be assembled via {@link #builder()}.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class QuantizerProfile {

    /** Sort durations by ascending value (smallest first). */
    public static final Comparator<Duration> BY_VALUE = Duration::compareDuration;

    private final List<Duration> legalDurations;     // sorted ascending by value, deduped

    private QuantizerProfile(Set<Duration> durations) {
        // Canonicalise + sort. Duration's value-equality uses equalsDuration,
        // not Java equals — dedupe by canonical (RawDuration) form.
        var canonical = new TreeSet<Duration>(BY_VALUE);
        for (var d : durations) {
            canonical.add(d.canonical());
        }
        this.legalDurations = List.copyOf(canonical);
    }

    /** All legal durations in this profile, sorted ascending by value. */
    public List<Duration> legalDurations() {
        return legalDurations;
    }

    // ── Preset profiles ───────────────────────────────────────────────

    /**
     * Powers-of-two and their single-dotted variants only — no tuplets.
     * Equivalent to the legacy 64-base behaviour. Right for folk songs,
     * pop tunes, anything that's authored as straight rhythm.
     */
    public static final QuantizerProfile STANDARD = builder()
            .withBaseDurations()
            .build();

    /**
     * Standard plus triplets at every level. Right for mainstream
     * classical (Mozart, Beethoven), Romantic music with occasional
     * triplet feel, and most pop/rock arrangements.
     */
    public static final QuantizerProfile WITH_TRIPLETS = builder()
            .withBaseDurations()
            .withTriplets()
            .build();

    /**
     * Standard + triplets + quintuplets + septuplets. Right for
     * Chopin, Liszt, Debussy, jazz with complex tuplet feel.
     */
    public static final QuantizerProfile FULL = builder()
            .withBaseDurations()
            .withTriplets()
            .withQuintuplets()
            .withSeptuplets()
            .build();

    /**
     * Permissive profile for live MIDI keyboard input. Same legal set
     * as {@link #FULL} for now; future extension can bias scoring
     * toward standard subdivisions when timings are ambiguous.
     */
    public static final QuantizerProfile IMPROV = FULL;

    // ── Builder ───────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final LinkedHashSet<Duration> durations = new LinkedHashSet<>();

        private Builder() {}

        /**
         * Add the seven powers-of-two ({@code BaseValue} constants from
         * WHOLE down to SIXTY_FOURTH) plus single-dotted variants —
         * the legacy 64-base set.
         */
        public Builder withBaseDurations() {
            durations.add(BaseValue.WHOLE);
            durations.add(BaseValue.HALF);
            durations.add(BaseValue.QUARTER);
            durations.add(BaseValue.EIGHTH);
            durations.add(BaseValue.SIXTEENTH);
            durations.add(BaseValue.THIRTY_SECOND);
            durations.add(BaseValue.SIXTY_FOURTH);
            durations.add(Dotted.WHOLE);
            durations.add(Dotted.HALF);
            durations.add(Dotted.QUARTER);
            durations.add(Dotted.EIGHTH);
            durations.add(Dotted.SIXTEENTH);
            durations.add(Dotted.THIRTY_SECOND);
            return this;
        }

        /** Add 128th notes — for very fast keyboard ornaments. */
        public Builder withVeryFast() {
            durations.add(BaseValue.HUNDRED_TWENTY_EIGHTH);
            return this;
        }

        /** Add triplets at half/quarter/eighth/sixteenth/thirty-second levels. */
        public Builder withTriplets() {
            durations.add(Triplet.HALF);
            durations.add(Triplet.QUARTER);
            durations.add(Triplet.EIGHTH);
            durations.add(Triplet.SIXTEENTH);
            durations.add(Triplet.THIRTY_SECOND);
            return this;
        }

        /** Add quintuplets at quarter/eighth/sixteenth/thirty-second levels. */
        public Builder withQuintuplets() {
            durations.add(RawTuplet.ofStandard(5, BaseValue.QUARTER));
            durations.add(RawTuplet.ofStandard(5, BaseValue.EIGHTH));
            durations.add(RawTuplet.ofStandard(5, BaseValue.SIXTEENTH));
            durations.add(RawTuplet.ofStandard(5, BaseValue.THIRTY_SECOND));
            return this;
        }

        /** Add septuplets at eighth/sixteenth/thirty-second levels. */
        public Builder withSeptuplets() {
            durations.add(RawTuplet.ofStandard(7, BaseValue.EIGHTH));
            durations.add(RawTuplet.ofStandard(7, BaseValue.SIXTEENTH));
            durations.add(RawTuplet.ofStandard(7, BaseValue.THIRTY_SECOND));
            return this;
        }

        /** Add a single duration explicitly. */
        public Builder add(Duration d) {
            durations.add(d);
            return this;
        }

        public QuantizerProfile build() {
            if (durations.isEmpty()) {
                throw new IllegalStateException(
                        "QuantizerProfile must contain at least one duration");
            }
            return new QuantizerProfile(Collections.unmodifiableSet(durations));
        }
    }
}
