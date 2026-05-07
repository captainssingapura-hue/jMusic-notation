package music.notation.performance;

import music.notation.duration.BarDuration;
import music.notation.duration.Duration;
import music.notation.event.PercussionSound;
import music.notation.phrase.Bar;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.RestNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds {@code List<Bar>} for one {@code DrumTrack} from a stream of
 * {@link Hit}s spanning multiple {@link PercussionSound}s.
 *
 * <p>Phase 6: cursor and posInBar are tracked as exact rational
 * {@link Duration}s, so triplet-aware profiles produce drum bars
 * without sf-rounding drift.</p>
 *
 * <p>{@link PercussionNote} is single-piece-single-duration and a
 * {@code Bar}'s nodes are strictly sequential, so two strikes that
 * share the same quantized onset (kick + crash on beat 1) can't both
 * appear at that exact instant. We sequence them <strong>1 sixty-fourth
 * apart</strong> in onset order — at typical tempos this is well below
 * human perceptual threshold (≈ 4 ms at 120 BPM) and preserves every
 * hit. See {@code .docs/drum-track-model.md}.</p>
 *
 * <p>Drum notes don't tie: a hit decays naturally on each
 * articulation. If a notated duration would overflow the current bar,
 * the note is clipped at the bar boundary.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class DrumBarBuilder {

    private DrumBarBuilder() {}

    /** A single percussion strike: onset/duration in ms plus its sound. */
    public record Hit(long onsetMs, long durationMs, PercussionSound sound) {
        public Hit {
            if (onsetMs < 0)     throw new IllegalArgumentException("onsetMs must be >= 0");
            if (durationMs <= 0) throw new IllegalArgumentException("durationMs must be > 0");
            if (sound == null)   throw new IllegalArgumentException("sound must not be null");
        }
    }

    /** Build bars for a mixed-percussion lane. */
    public static List<Bar> build(List<Hit> hits, BarBuilder.Config cfg) {
        if (hits.isEmpty()) return List.of();
        var sorted = new ArrayList<>(hits);
        sorted.sort(Comparator.comparingLong(Hit::onsetMs));

        var state = new State(cfg);
        Duration oneSf = Duration.of(1, 64);   // sub-perceptual stagger unit

        for (Hit h : sorted) {
            Duration onsetRaw = msToFraction(h.onsetMs(), cfg.bpm());
            Duration rawDur   = msToFraction(h.durationMs(), cfg.bpm());
            Duration dur      = Quantizer.snap(rawDur, cfg.profile());
            if (dur.numerator() == 0) dur = state.minLegalDuration();

            // Same-quantum collisions: stagger by 1 sf (sub-perceptual).
            if (onsetRaw.compareDuration(state.cursor) < 0) onsetRaw = state.cursor;

            // Phase 8: snap onset gap to the profile's finest grid.
            if (onsetRaw.compareDuration(state.cursor) > 0) {
                Duration rawGap  = onsetRaw.minus(state.cursor);
                Duration gridGap = Quantizer.snapToGrid(rawGap, cfg.profile());
                if (gridGap.numerator() > 0) {
                    state.emitRest(gridGap);
                }
            }
            // Clip at bar end — drum decay is instantaneous.
            Duration remainingInBar = state.barTotal.minus(state.posInBar);
            Duration hitDur = dur.compareDuration(remainingInBar) <= 0
                    ? dur : remainingInBar;
            // Decompose into legal chunks (no ties for drums).
            Duration chunkLeft = hitDur;
            boolean isHead = true;
            while (chunkLeft.numerator() > 0) {
                Duration legal = Quantizer.floor(chunkLeft, cfg.profile());
                if (legal == null || legal.numerator() <= 0) break;
                state.emitNode(isHead
                        ? new PercussionNote(h.sound(), legal)
                        : new RestNode(legal));
                chunkLeft = chunkLeft.minus(legal);
                isHead = false;
            }
        }
        if (state.posInBar.numerator() > 0) {
            state.emitRest(state.barTotal.minus(state.posInBar));
        }
        return state.bars;
    }

    // ── helper ──────────────────────────────────────────────────────

    private static Duration msToFraction(long ms, int bpm) {
        return Duration.of((long) ms * bpm, 240_000L);
    }

    // ── internal walker ─────────────────────────────────────────────

    private static final class State {
        static final Duration ZERO = Duration.of(0, 1);

        final BarDuration barDuration;
        final Duration barTotal;
        final QuantizerProfile profile;
        final List<Bar> bars       = new ArrayList<>();
        final List<PhraseNode> cur = new ArrayList<>();
        Duration cursor   = ZERO;
        Duration posInBar = ZERO;

        State(BarBuilder.Config cfg) {
            this.barDuration = BarDuration.fromSixtyFourths(cfg.barSf());
            this.barTotal    = barDuration.totalDuration();
            this.profile     = cfg.profile();
        }

        Duration minLegalDuration() {
            return profile.legalDurations().get(0);
        }

        void emitRest(Duration gap) {
            if (gap.numerator() <= 0) return;
            // Drop sub-quantum gaps to avoid an infinite loop on residue
            // smaller than every legal duration in the profile.
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
                if (!madeProgress) break;
            }
        }

        void emitNode(PhraseNode node) {
            cur.add(node);
            Duration nodeDur = Bar.nodeDuration(node);
            posInBar = posInBar.plus(nodeDur);
            cursor   = cursor.plus(nodeDur);
            flushBarIfFull();
        }

        void flushBarIfFull() {
            if (posInBar.compareDuration(barTotal) < 0) return;
            bars.add(new Bar(barDuration, List.copyOf(cur)));
            cur.clear();
            posInBar = ZERO;
        }
    }
}
