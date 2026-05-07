package music.notation.experiments.pi;

import music.notation.expressivity.Articulations;
import music.notation.performance.ConcreteNote;
import music.notation.performance.InstrumentControl;
import music.notation.performance.Instrumentation;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.TempoTrack;
import music.notation.performance.Track;
import music.notation.expressivity.TrackId;
import music.notation.performance.TrackKind;
import music.notation.experiments.scale.ScaleFactory;
import music.notation.experiments.scale.ScaleNote;
import music.notation.experiments.scale.ScalePitchResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the digits of π to a sequence of notes in some scale, producing a
 * {@link Performance} ready for MIDI rendering or playback.
 *
 * <p>The mapping is uniform across scale families:</p>
 * <pre>
 *     degreeIndex = digit % degreeCount
 *     octave      = baseOctave + (digit / degreeCount)
 * </pre>
 *
 * <p>Concrete consequences:</p>
 * <ul>
 *   <li><b>Pentatonic</b> (5 degrees): digits 0–4 sit in {@code baseOctave};
 *       digits 5–9 sit in {@code baseOctave + 1}. Two-octave span, ten
 *       distinct pitches.</li>
 *   <li><b>Hexatonic blues</b> (6 degrees): digits 0–5 sit in
 *       {@code baseOctave}; digits 6–9 occupy degrees 0–3 of
 *       {@code baseOctave + 1}. Same two-octave footprint, different
 *       internal layout — the "blue note" lands on digit 2 (minor blues)
 *       or digit 2 (major blues) depending on the scale.</li>
 * </ul>
 *
 * <p>Non-digit characters (e.g. the leading {@code "3."} of π) are
 * skipped, so you can pass the raw text from {@code pi_250.txt}
 * unchanged.</p>
 */
public final class PiSong {

    private static final TrackId TRACK_ID = new TrackId("pi");

    private PiSong() {}

    /**
     * Build a Performance from the supplied digit string.
     *
     * @param digits        any string; non-digit chars are skipped
     * @param factory       constructs scale notes by (degreeIndex, octave)
     * @param resolver      resolves a scale note to a MIDI pitch
     * @param baseOctave    octave for digits in the lower half of the range
     * @param noteMs        duration of every note (uniform)
     * @param program       GM program number for the track instrument
     */
    public static <N extends ScaleNote> Performance build(
            String digits,
            ScaleFactory<N> factory,
            ScalePitchResolver<N> resolver,
            int baseOctave,
            long noteMs,
            int program) {

        if (digits == null || digits.isEmpty()) {
            throw new IllegalArgumentException("digits must be non-empty");
        }
        if (noteMs <= 0) {
            throw new IllegalArgumentException("noteMs must be positive: " + noteMs);
        }

        // Probe degreeCount via a dummy note (every ScaleNote knows its own count).
        final int degreeCount = factory.create(0, baseOctave).degreeCount();

        var notes = new ArrayList<ConcreteNote>();
        long cursor = 0;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') continue;
            int digit = c - '0';

            int degreeIndex = digit % degreeCount;
            int octave = baseOctave + (digit / degreeCount);

            N note = factory.create(degreeIndex, octave);
            int midi = resolver.midi(note);

            notes.add(new PitchedNote(cursor, noteMs, midi));
            cursor += noteMs;
        }

        Track track = new Track(TRACK_ID, TrackKind.PITCHED, notes);
        Score score = new Score(List.of(track));
        return new Performance(
                score,
                TempoTrack.empty(),
                Instrumentation.single(TRACK_ID, program),
                Articulations.empty());
    }

    public static TrackId trackId() {
        return TRACK_ID;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Canon: same melody, multiple voices, octave-staggered, time-offset
    // ═══════════════════════════════════════════════════════════════

    /** A canon voice: TrackId + base octave + entry delay (in notes from voice 0). */
    public record Voice(TrackId id, int baseOctave, int entryNoteOffset) {
        public Voice {
            java.util.Objects.requireNonNull(id, "id");
            if (entryNoteOffset < 0) {
                throw new IllegalArgumentException(
                        "entryNoteOffset must be non-negative: " + entryNoteOffset);
            }
        }
    }

    /**
     * Build a canon: every voice plays the same digit-driven melody, but
     * each voice sits in its own octave and enters after a fixed delay
     * (in notes from voice 0). Voices are independent {@link Track}s — the
     * codec writes each on its own MIDI channel.
     *
     * <p>Voice 0 starts at tick 0. Voice 1 starts at
     * {@code voices[1].entryNoteOffset() * noteMs}, and so on.</p>
     */
    public static <N extends ScaleNote> Performance buildCanon(
            String digits,
            ScaleFactory<N> factory,
            ScalePitchResolver<N> resolver,
            List<Voice> voices,
            long noteMs,
            int program) {

        if (digits == null || digits.isEmpty()) {
            throw new IllegalArgumentException("digits must be non-empty");
        }
        if (voices == null || voices.isEmpty()) {
            throw new IllegalArgumentException("voices must be non-empty");
        }
        if (noteMs <= 0) {
            throw new IllegalArgumentException("noteMs must be positive: " + noteMs);
        }

        final int degreeCount = factory.create(0, voices.get(0).baseOctave()).degreeCount();
        final List<Track> tracks = new ArrayList<>(voices.size());
        final Map<TrackId, InstrumentControl> instr = new LinkedHashMap<>();

        for (Voice v : voices) {
            var notes = new ArrayList<music.notation.performance.ConcreteNote>();
            long entryTickMs = (long) v.entryNoteOffset() * noteMs;
            long cursor = entryTickMs;
            for (int i = 0; i < digits.length(); i++) {
                char c = digits.charAt(i);
                if (c < '0' || c > '9') continue;
                int digit = c - '0';
                int degreeIndex = digit % degreeCount;
                int octave = v.baseOctave() + (digit / degreeCount);
                int midi = resolver.midi(factory.create(degreeIndex, octave));
                notes.add(new PitchedNote(cursor, noteMs, midi));
                cursor += noteMs;
            }
            tracks.add(new Track(v.id(), TrackKind.PITCHED, notes));
            instr.put(v.id(), InstrumentControl.constant(program));
        }

        return new Performance(
                new Score(tracks),
                TempoTrack.empty(),
                new Instrumentation(instr),
                Articulations.empty());
    }
}
