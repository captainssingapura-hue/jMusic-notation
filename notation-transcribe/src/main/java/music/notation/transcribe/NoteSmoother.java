package music.notation.transcribe;

import music.notation.structure.KeySignature;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pitch clean-up of segmented notes, in two steps around key finding:
 *
 * <ol>
 *   <li>{@link #fixShapes} — key-independent:
 *     <ul>
 *       <li><b>Octave</b>: a short note (≤ {@code octaveMaxMs}) that is
 *           9+ semitones from both neighbours, and within 5 of both once
 *           moved an octave, is moved that octave — a detector octave
 *           error, not a leap.</li>
 *       <li><b>Blip</b>: a short note (≤ {@code blipMaxMs}, and under
 *           half the length of both neighbours) touching both is
 *           absorbed into the note after it when it lies strictly between
 *           them (a fragment of a slide), or when both neighbours share a
 *           pitch it is at most a whole tone from (a scoop or flick into a
 *           re-attack). The neighbours stay separate notes.</li>
 *     </ul>
 *   </li>
 *   <li>{@link #snap} — against the key: an out-of-key note whose cents
 *       lean at least {@code snapCents} towards an in-key semitone is
 *       moved onto it; its cents are re-measured from the new pitch.
 *       Clearly sung chromatic notes (|cents| below the threshold, or
 *       leaning towards another out-of-key note) stay.</li>
 * </ol>
 *
 * "Neighbours" are notes no more than {@code neighbourGapMs} apart.
 * Equal-length fast notes are never blips, because each is as long as
 * its neighbours.
 */
public final class NoteSmoother {

    /** Notes after a step, and what it changed. */
    public record Result(List<TranscribedNote> notes, List<SmoothingEdit> edits) {
        public Result {
            notes = List.copyOf(notes);
            edits = List.copyOf(edits);
        }
    }

    private final SmoothingSpec spec;

    public NoteSmoother(SmoothingSpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    // ── step 1: octave errors and blips ────────────────────────────────

    public Result fixShapes(List<TranscribedNote> input) {
        var notes = new ArrayList<>(input);
        var edits = new ArrayList<SmoothingEdit>();
        if (spec.octaveMaxMs() > 0) foldOctaves(notes, edits);
        if (spec.blipMaxMs() > 0) absorbBlips(notes, edits);
        return new Result(notes, edits);
    }

    private void foldOctaves(List<TranscribedNote> notes, List<SmoothingEdit> edits) {
        for (int i = 1; i + 1 < notes.size(); i++) {
            TranscribedNote p = notes.get(i - 1), n = notes.get(i), q = notes.get(i + 1);
            if (n.durationMs() > spec.octaveMaxMs() || !near(p, n) || !near(n, q)) continue;
            for (int shift : new int[]{12, -12}) {
                int m = n.midi() + shift;
                if (m < 0 || m > 127) continue;
                if (Math.abs(n.midi() - p.midi()) >= 9 && Math.abs(n.midi() - q.midi()) >= 9
                        && Math.abs(m - p.midi()) <= 5 && Math.abs(m - q.midi()) <= 5) {
                    notes.set(i, withPitch(n, m, n.cents()));
                    edits.add(new SmoothingEdit(SmoothingEdit.Kind.OCTAVE, n.startMs(), n.endMs(), n.midi(), m));
                    break;
                }
            }
        }
    }

    private void absorbBlips(List<TranscribedNote> notes, List<SmoothingEdit> edits) {
        int i = 1;
        while (i + 1 < notes.size()) {
            TranscribedNote p = notes.get(i - 1), n = notes.get(i), q = notes.get(i + 1);
            boolean shortOne = n.durationMs() <= spec.blipMaxMs()
                    && 2 * n.durationMs() < p.durationMs() && 2 * n.durationMs() < q.durationMs();
            if (!shortOne || !near(p, n) || !near(n, q)) { i++; continue; }

            boolean between = Math.min(p.midi(), q.midi()) < n.midi() && n.midi() < Math.max(p.midi(), q.midi());
            boolean flick = p.midi() == q.midi() && n.midi() != p.midi() && Math.abs(n.midi() - p.midi()) <= 2;
            if (between || flick) {
                // A slide fragment, or a scoop / flick into a re-attack: either way it leads into q.
                // p and q stay separate — the segmenter found a new note starting there.
                notes.set(i + 1, merged(q, List.of(n, q)));
                notes.remove(i);
                edits.add(new SmoothingEdit(SmoothingEdit.Kind.BLIP, n.startMs(), n.endMs(), n.midi(), q.midi()));
                continue;                                   // re-check from the same index
            }
            i++;
        }
    }

    // ── step 2: snap to the key ────────────────────────────────────────

    public Result snap(List<TranscribedNote> input, KeySignature key) {
        if (spec.snapCents() <= 0 || key == null) return new Result(input, List.of());
        boolean[] scale = scale(key);
        var notes = new ArrayList<TranscribedNote>(input.size());
        var edits = new ArrayList<SmoothingEdit>();
        for (TranscribedNote n : input) {
            int dir = (int) Math.signum(n.cents());
            int target = n.midi() + dir;
            if (!scale[Math.floorMod(n.midi(), 12)] && dir != 0 && Math.abs(n.cents()) >= spec.snapCents()
                    && target >= 0 && target <= 127 && scale[Math.floorMod(target, 12)]) {
                notes.add(withPitch(n, target, n.cents() - 100.0 * dir));
                edits.add(new SmoothingEdit(SmoothingEdit.Kind.SNAP, n.startMs(), n.endMs(), n.midi(), target));
            } else {
                notes.add(n);
            }
        }
        return new Result(notes, edits);
    }

    /**
     * Pitch classes belonging to {@code key}, indexed 0 = C. Minor
     * includes the raised 7th (leading note); a key without a mode
     * accepts every pitch class, so nothing snaps.
     */
    static boolean[] scale(KeySignature key) {
        int[] steps = switch (key.mode()) {
            case MAJOR -> new int[]{0, 2, 4, 5, 7, 9, 11};
            case MINOR -> new int[]{0, 2, 3, 5, 7, 8, 10, 11};
            case AEOLIAN -> new int[]{0, 2, 3, 5, 7, 8, 10};
            case DORIAN -> new int[]{0, 2, 3, 5, 7, 9, 10};
            case PHRYGIAN -> new int[]{0, 1, 3, 5, 7, 8, 10};
            case LYDIAN -> new int[]{0, 2, 4, 6, 7, 9, 11};
            case MIXOLYDIAN -> new int[]{0, 2, 4, 5, 7, 9, 10};
            case LOCRIAN -> new int[]{0, 1, 3, 5, 6, 8, 10};
            case NONE -> new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        };
        boolean[] in = new boolean[12];
        int tonic = Keys.tonicPc(key);
        for (int s : steps) in[(tonic + s) % 12] = true;
        return in;
    }

    // ── helpers ────────────────────────────────────────────────────────

    private boolean near(TranscribedNote a, TranscribedNote b) {
        return b.startMs() - a.endMs() <= spec.neighbourGapMs();
    }

    private static TranscribedNote withPitch(TranscribedNote n, int midi, double cents) {
        return new TranscribedNote(n.startMs(), n.endMs(), midi, cents, n.confidence(), n.peakRms());
    }

    /** One note spanning {@code parts} with {@code keep}'s pitch; confidence duration-weighted, loudness the peak. */
    private static TranscribedNote merged(TranscribedNote keep, List<TranscribedNote> parts) {
        double start = Double.MAX_VALUE, end = -Double.MAX_VALUE, conf = 0, len = 0, peak = 0;
        for (TranscribedNote x : parts) {
            start = Math.min(start, x.startMs());
            end = Math.max(end, x.endMs());
            conf += x.confidence() * x.durationMs();
            len += x.durationMs();
            peak = Math.max(peak, x.peakRms());
        }
        return new TranscribedNote(start, end, keep.midi(), keep.cents(), conf / len, peak);
    }
}
