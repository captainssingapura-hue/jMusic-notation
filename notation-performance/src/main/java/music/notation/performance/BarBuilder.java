package music.notation.performance;

import music.notation.duration.BarDuration;
import music.notation.duration.Duration;
import music.notation.event.Ornament;
import music.notation.performance.OnsetGrouper.GroupedEvent;
import music.notation.phrase.Bar;
import music.notation.phrase.GraceNote;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.PitchNode;
import music.notation.phrase.PolyPitchNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.SimplePitchNode;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Converts a single monophonic-in-time voice (a list of
 * {@link GroupedEvent}s) into a flat {@code List<Bar>} suitable for a
 * {@link music.notation.structure.MelodicTrack}.
 *
 * <p>Phase 6: the internal walker tracks {@code cursor} and
 * {@code posInBar} as exact rational {@link Duration}s. Tuplet
 * profiles ({@link QuantizerProfile#WITH_TRIPLETS} and beyond) snap
 * to exact tuplet values and the walker advances in those exact
 * fractions — no precision loss across triplet groups, and bar
 * validation passes for any rational content.</p>
 *
 * <p>Bar-spanning notes are split into a tie chain via rational
 * arithmetic in {@link State#emitEvent}: each chunk is exactly the
 * remainder of the current bar, then any leftover continues into the
 * next bar. The resulting chunk durations may not always correspond
 * to single named note values (e.g. {@code 5/64} after triplet
 * content); decomposing such chunks into chains of legal named
 * values for cleaner score rendering is Phase 7's job.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class BarBuilder {

    private BarBuilder() {}

    /** Conversion knobs: bar size, tempo, and quantizer profile. */
    public record Config(int barSf, int bpm, QuantizerProfile profile) {
        public Config {
            if (barSf <= 0)        throw new IllegalArgumentException("barSf must be > 0");
            if (bpm   <= 0)        throw new IllegalArgumentException("bpm must be > 0");
            if (profile == null)   throw new IllegalArgumentException("profile must not be null");
        }

        /** Backward-compat: defaults profile to {@link QuantizerProfile#STANDARD}. */
        public Config(int barSf, int bpm) {
            this(barSf, bpm, QuantizerProfile.STANDARD);
        }

        /** Sixty-fourths per millisecond at this tempo (legacy helper). */
        public double sfPerMs() { return bpm * 16.0 / 60_000.0; }
    }

    /** Build bars from a single voice. Always returns at least one bar. */
    public static List<Bar> build(List<GroupedEvent> voice, Config cfg) {
        var state = new State(cfg);

        for (GroupedEvent ev : voice) {
            // Convert ms → exact rational fraction-of-whole.
            Duration onsetRaw = msToFraction(ev.onsetMs(), cfg.bpm());
            Duration durRaw   = msToFraction(ev.durationMs(), cfg.bpm());
            // Snap duration via profile.
            Duration dur = Quantizer.snap(durRaw, cfg.profile());
            if (dur.numerator() == 0) dur = state.minLegalDuration();

            // Tolerate small overlap from rounding: clamp onset to cursor.
            if (onsetRaw.compareDuration(state.cursor) < 0) onsetRaw = state.cursor;

            // Phase 8: snap the gap-from-cursor to the profile's finest
            // grid. Sub-half-grid gaps round to zero — eliminates the
            // accumulated drift between unsnapped onsets and snapped
            // durations.
            if (onsetRaw.compareDuration(state.cursor) > 0) {
                Duration rawGap = onsetRaw.minus(state.cursor);
                Duration gridGap = Quantizer.snapToGrid(rawGap, cfg.profile());
                if (gridGap.numerator() > 0) {
                    state.emitRest(gridGap);
                }
            }
            state.emitEvent(ev.pitches(), dur);
        }
        // Trailing fill so the final bar always closes to the bar duration.
        if (state.posInBar.numerator() > 0) {
            state.emitRest(state.barTotal.minus(state.posInBar));
        }
        return state.bars;
    }

    // ── Conversion helper: ms × bpm / 240000 = fraction-of-whole ─────

    private static Duration msToFraction(long ms, int bpm) {
        return Duration.of((long) ms * bpm, 240_000L);
    }

    // ── Internal walker ─────────────────────────────────────────────

    private static final class State {
        static final Duration ZERO = Duration.of(0, 1);

        final BarDuration barDuration;
        final Duration barTotal;       // == barDuration.totalDuration() cached
        final QuantizerProfile profile;
        final List<Bar> bars       = new ArrayList<>();
        final List<PhraseNode> cur = new ArrayList<>();
        Duration cursor   = ZERO;      // absolute position since piece start
        Duration posInBar = ZERO;
        int barIdx        = 0;

        State(Config cfg) {
            this.barDuration = BarDuration.fromSixtyFourths(cfg.barSf());
            this.barTotal    = barDuration.totalDuration();
            this.profile     = cfg.profile();
        }

        /** Smallest legal duration in the profile (used as min-quantum fallback). */
        Duration minLegalDuration() {
            return profile.legalDurations().get(0);
        }

        /** Fill {@code gap} with rest nodes, decomposing greedily and crossing bars. */
        void emitRest(Duration gap) {
            if (gap.numerator() <= 0) return;
            // Drop sub-quantum gaps to avoid an infinite loop when the
            // requested gap is smaller than every legal duration in the
            // profile (can happen when an unsnapped onset falls between
            // a snapped cursor and a snapped legal grid line).
            Duration smallest = minLegalDuration();
            if (gap.compareDuration(smallest) < 0) return;

            Duration remaining = gap;
            while (remaining.compareDuration(ZERO) > 0) {
                Duration remainingInBar = barTotal.minus(posInBar);
                Duration chunk = remaining.compareDuration(remainingInBar) <= 0
                        ? remaining : remainingInBar;
                boolean madeProgress = false;
                while (chunk.compareDuration(ZERO) > 0) {
                    Duration legal = Quantizer.floor(chunk, profile);
                    if (legal == null || legal.numerator() <= 0) break;
                    cur.add(new RestNode(legal));
                    chunk     = chunk.minus(legal);
                    posInBar  = posInBar.plus(legal);
                    cursor    = cursor.plus(legal);
                    remaining = remaining.minus(legal);
                    madeProgress = true;
                }
                flushBarIfFull();
                // Safety: if the inner loop couldn't reduce `chunk` (no legal
                // duration fits the residue), break the outer loop too —
                // otherwise we'd spin forever on sub-legal residue.
                if (!madeProgress) break;
            }
        }

        /**
         * Emit a pitched event of duration {@code totalDur}, splitting
         * across bar boundaries with a tie chain when the duration
         * overflows the current bar. Operates entirely on rational
         * {@link Duration} arithmetic — no int-sf truncation.
         *
         * <p>Caveat: the chunk that fills out a partial bar may have a
         * duration that doesn't correspond to a single named note value
         * (e.g. {@code 5/64}). Bar validation still works because totals
         * sum exactly. Decomposing such chunks into chains of legal
         * named values for cleaner score rendering is Phase 7.</p>
         */
        void emitEvent(List<Integer> pitches, Duration totalDur) {
            Duration remaining = totalDur;
            while (remaining.numerator() > 0) {
                Duration remainingInBar = barTotal.minus(posInBar);
                Duration chunk = remaining.compareDuration(remainingInBar) <= 0
                        ? remaining : remainingInBar;
                boolean tied = chunk.compareDuration(remaining) < 0;
                cur.add(makePitchNode(pitches, chunk, tied));
                posInBar  = posInBar.plus(chunk);
                cursor    = cursor.plus(chunk);
                remaining = remaining.minus(chunk);
                flushBarIfFull();
            }
        }

        void flushBarIfFull() {
            if (posInBar.compareDuration(barTotal) < 0) return;
            bars.add(new Bar(barDuration, List.copyOf(cur)));
            cur.clear();
            posInBar = ZERO;
            barIdx++;
        }
    }

    // ── PhraseNode construction ─────────────────────────────────────

    private static PitchNode makePitchNode(List<Integer> pitches, Duration dur, boolean tied) {
        if (pitches.size() == 1) {
            return new SimplePitchNode(midiToPitch(pitches.get(0)), dur,
                    Optional.<Ornament>empty(), List.<GraceNote>of(), false, tied);
        }
        var staffPitches = new ArrayList<Pitch>(pitches.size());
        for (int m : pitches) staffPitches.add(midiToPitch(m));
        return new PolyPitchNode(staffPitches, dur, List.<GraceNote>of(), false, tied);
    }

    /** Map a MIDI note number to a {@link Pitch} using sharp spellings. */
    static Pitch midiToPitch(int midi) {
        int octave = (midi / 12) - 1;
        int pc     = midi % 12;
        return switch (pc) {
            case 0  -> Pitch.of(NoteName.C, octave);
            case 1  -> Pitch.of(NoteName.C, Accidental.SHARP, octave);
            case 2  -> Pitch.of(NoteName.D, octave);
            case 3  -> Pitch.of(NoteName.D, Accidental.SHARP, octave);
            case 4  -> Pitch.of(NoteName.E, octave);
            case 5  -> Pitch.of(NoteName.F, octave);
            case 6  -> Pitch.of(NoteName.F, Accidental.SHARP, octave);
            case 7  -> Pitch.of(NoteName.G, octave);
            case 8  -> Pitch.of(NoteName.G, Accidental.SHARP, octave);
            case 9  -> Pitch.of(NoteName.A, octave);
            case 10 -> Pitch.of(NoteName.A, Accidental.SHARP, octave);
            case 11 -> Pitch.of(NoteName.B, octave);
            default -> throw new IllegalStateException("midi " + midi);
        };
    }
}
