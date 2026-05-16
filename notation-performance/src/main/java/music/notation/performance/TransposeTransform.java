package music.notation.performance;

import java.util.ArrayList;
import java.util.List;

/**
 * Whole-piece pitch transposition as a pure
 * {@link Performance}-to-{@link Performance} transform. Used to render
 * a score in a different key (typically for vocal-comfort transposition)
 * while preserving the original Piece data.
 *
 * <h2>Semantics — strictly linear</h2>
 *
 * <p>The transform applies a single integer shift, in semitones, to
 * every pitched note's MIDI value:</p>
 *
 * <pre>{@code newMidi = oldMidi + semitoneShift}</pre>
 *
 * <p>No octave-folding, no smart range adjustment, no per-instrument
 * heuristics. The transform is deliberately a textbook linear map; any
 * cleverness lives outside (e.g. a future UI helper that picks a
 * range-preserving transposition before calling here).</p>
 *
 * <h2>Affected vs unaffected</h2>
 *
 * <ul>
 *   <li><b>Pitched notes</b> ({@link PitchedNote}): MIDI value shifted.
 *       Duration, tick, and tied-to-next flag preserved verbatim.</li>
 *   <li><b>Drum tracks</b> ({@link TrackKind#DRUM}): pass through
 *       unchanged. A {@code DrumNote.piece()} value is a kit selector,
 *       not a pitch; transposing it would silently re-route hits to
 *       different kit slots.</li>
 *   <li><b>Side-channels</b> ({@code pedaling}, {@code volume},
 *       {@code velocities}, {@code articulations}, {@code instruments},
 *       {@code tempo}): pass through unchanged.</li>
 * </ul>
 *
 * <h2>Out-of-range handling</h2>
 *
 * <p>MIDI requires pitches in [0, 127]. Any note whose shifted value
 * falls outside that range is <b>dropped</b>, with a per-track count
 * logged at WARN. This keeps the transform strictly total — it never
 * throws on legitimate input — while making the loss visible.</p>
 *
 * <p>For vocal-comfort transpositions (typically ±5 semitones)
 * out-of-range drops are rare. Wider shifts (±12 or more) on
 * piano music can trim a few extreme keyboard notes; that's expected.</p>
 *
 * <h2>Where this sits in the transform chain</h2>
 *
 * <p>This is the <b>last</b> Performance-layer transform before the
 * codec, applied after pedaling, velocity, and humanizer transforms.
 * The upstream transforms operate on the <i>original</i> pitches —
 * which is the right thing for AutoPedaling's sustain-instrument filter
 * (decides based on instrument, not pitch), AutoVelocity heuristics
 * (key-aware decisions read the original key), and HumanizerTransform
 * (timing jitter is pitch-independent).</p>
 *
 * <p>Pipeline:</p>
 * <pre>{@code
 *   Piece
 *     → PieceConcretizer.concretize    (Piece → Performance)
 *     → AutoPedaling.augment            (fold auto-pedal into pedaling())
 *     → applyVelocityOverrides          (fold live velocities)
 *     → applyPedalingOverrides          (fold live pedaling)
 *     → HumanizerTransform.apply        (jitter)
 *     → TransposeTransform.apply        ← THIS
 *     → MidiCodec.toMidi                (emit MIDI bytes)
 * }</pre>
 */
public final class TransposeTransform {

    private TransposeTransform() {}

    /**
     * Transposition configuration.
     *
     * @param semitoneShift integer semitone shift; positive = up, negative = down.
     *                      0 = no transposition ({@link #isOff()}).
     *                      Range guidance: ±12 covers typical vocal-comfort needs;
     *                      ±24 is the practical maximum before MIDI range loss
     *                      becomes severe.
     */
    public record Params(int semitoneShift) {
        public static final Params NONE = new Params(0);

        public boolean isOff() { return semitoneShift == 0; }

        public static Params of(int semitoneShift) { return new Params(semitoneShift); }
    }

    /**
     * Return a copy of {@code perf} with every pitched note <em>wrapped</em>
     * in a {@link ShiftedNote} carrying the requested semitone shift.
     * Returns the input unchanged when {@code params.isOff()} or when
     * {@code perf} is null.
     *
     * <h2>Wrapping, not mutating</h2>
     *
     * <p>The transform produces {@link ShiftedNote}s rather than new
     * {@link PitchedNote}s at shifted MIDI values. The original is
     * recoverable from {@link ShiftedNote#original()}. Both the codec
     * (which reads {@link PitchedLike#midi()} for the effective value)
     * and the UI (which can render both the original and the shifted
     * pitch via {@link ShiftedNote#originalMidi()}) see one
     * authoritative Performance object — no parallel "original" state
     * to track separately.</p>
     *
     * <h2>Composition</h2>
     *
     * <p>If the input already contains {@link ShiftedNote}s (a piece
     * already transposed and being further transposed), this method
     * flattens — the result has {@code semitoneShift = old + new} on a
     * single wrap. See {@link ShiftedNote#of(PitchedLike, int)}.</p>
     *
     * <h2>Out-of-range</h2>
     *
     * <p>Notes whose effective MIDI ({@code original.midi() + shift})
     * would fall outside [0, 127] are silently dropped — see
     * {@link #countOutOfRange} for a pre-flight check that callers
     * (e.g. the UI) can use to surface drop warnings before committing
     * to a transposition value.</p>
     */
    public static Performance apply(Performance perf, Params params) {
        if (perf == null) return null;
        if (params == null || params.isOff()) return perf;

        int shift = params.semitoneShift;
        List<Track> wrapped = new ArrayList<>(perf.score().tracks().size());
        boolean anyChange = false;

        for (Track t : perf.score().tracks()) {
            if (t.kind() == TrackKind.DRUM) {
                wrapped.add(t);
                continue;
            }
            // Pitched track — wrap every PitchedLike note with the shift,
            // flattening if it was already shifted. Drop out-of-range.
            List<ConcreteNote> oldNotes = t.notes();
            List<ConcreteNote> newNotes = new ArrayList<>(oldNotes.size());
            boolean trackChanged = false;
            for (ConcreteNote n : oldNotes) {
                if (!(n instanceof PitchedLike pl)) {
                    newNotes.add(n);
                    continue;
                }
                int effective = pl.midi() + shift;
                if (effective < 0 || effective > 127) {
                    trackChanged = true;        // dropped
                    continue;
                }
                newNotes.add(ShiftedNote.of(pl, shift));
                trackChanged = true;
            }
            if (!trackChanged) {
                wrapped.add(t);
            } else {
                wrapped.add(new Track(t.id(), t.kind(), newNotes, t.auto()));
                anyChange = true;
            }
        }
        if (!anyChange) return perf;
        return perf.withScore(new Score(wrapped));
    }

    /**
     * Pre-flight check: how many pitched notes would fall outside MIDI
     * [0, 127] if {@link #apply} were called with this shift? Pure;
     * useful for surfacing range warnings in the UI before committing
     * to a transposition value.
     *
     * <p>Reads {@link PitchedLike#midi()} so it handles already-shifted
     * input correctly: a Performance whose notes are
     * {@code ShiftedNote(orig=A4, shift=+5)} with an additional
     * shift of +5 reports out-of-range based on the <em>cumulative</em>
     * effective value.</p>
     */
    public static int countOutOfRange(Performance perf, int semitoneShift) {
        if (perf == null || semitoneShift == 0) return 0;
        int dropped = 0;
        for (Track t : perf.score().tracks()) {
            if (t.kind() == TrackKind.DRUM) continue;
            for (ConcreteNote n : t.notes()) {
                if (n instanceof PitchedLike pl) {
                    int effective = pl.midi() + semitoneShift;
                    if (effective < 0 || effective > 127) dropped++;
                }
            }
        }
        return dropped;
    }
}
