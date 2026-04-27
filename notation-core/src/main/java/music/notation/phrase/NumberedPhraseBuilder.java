package music.notation.phrase;

import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.event.Ornament;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Octave;
import music.notation.pitch.StaffPitch;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for numbered-notation (简谱) melodic phrases.
 *
 * <p>Usage:
 * <pre>{@code
 * var P = NumberedPhraseBuilder.in(E, FLAT, 4, TS);
 *
 * var verse = P
 *     .bar().mf().l(_5).l(_6)                        // pickup
 *     .bar().n(_1, QUARTER).n(_2, QUARTER)            // full bar
 *           .n(_3, QUARTER).n(_2).n(_1)
 *     .bar().l(_6, HALF).r(QUARTER).r(QUARTER)
 *     .build(attacca());
 * }</pre>
 *
 * <p>Default note duration is {@link BaseValue#EIGHTH}.
 * Methods: {@code n} (current octave), {@code l} (lower), {@code h} (higher).
 * Each {@code bar()} call starts a new bar; {@code build()} validates all bars
 * against the time signature.</p>
 *
 * <p>The builder is reusable — after {@code build()}, call {@code bar()} to
 * start the next phrase.</p>
 */
public final class NumberedPhraseBuilder {

    private final NoteName tonic;
    private final Accidental tonicAccidental;
    private final int baseOctave;
    private final TimeSignature ts;
    private final BaseValue defaultDur;

    private final List<Bar> bars = new ArrayList<>();
    private List<PhraseNode> current; // null until bar() is called

    private NumberedPhraseBuilder(NoteName tonic, Accidental tonicAccidental,
                                   int baseOctave, TimeSignature ts, BaseValue defaultDur) {
        this.tonic = tonic;
        this.tonicAccidental = tonicAccidental;
        this.baseOctave = baseOctave;
        this.ts = ts;
        this.defaultDur = defaultDur;
    }

    /** Create a builder for the given tonic, base octave, and time signature. Default duration: EIGHTH. */
    public static NumberedPhraseBuilder in(NoteName tonic, Accidental acc,
                                            int octave, TimeSignature ts) {
        return new NumberedPhraseBuilder(tonic, acc, octave, ts, BaseValue.EIGHTH);
    }

    /** Create a builder with a custom default note duration. */
    public static NumberedPhraseBuilder in(NoteName tonic, Accidental acc,
                                            int octave, TimeSignature ts, BaseValue defaultDur) {
        return new NumberedPhraseBuilder(tonic, acc, octave, ts, defaultDur);
    }

    // ── Bar management ──

    /** Start a new bar. If a bar is already in progress, it is closed and added. */
    public NumberedPhraseBuilder bar() {
        flush();
        current = new ArrayList<>();
        return this;
    }

    /** Close the last bar, validate all bars, and return the built phrase. Resets the builder. */
    public MelodicPhrase build(PhraseMarking marking) {
        flush();
        var result = MelodicPhrase.fromBars(ts, marking, bars.toArray(Bar[]::new));
        bars.clear();
        return result;
    }

    // ── Current octave ──

    public NumberedPhraseBuilder n(Deg d)                { return note(d, 0, defaultDur); }
    public NumberedPhraseBuilder n(Deg d, BaseValue v)   { return note(d, 0, v); }

    // ── Lower octave ──

    public NumberedPhraseBuilder l(Deg d)                { return note(d, -1, defaultDur); }
    public NumberedPhraseBuilder l(Deg d, BaseValue v)   { return note(d, -1, v); }

    // ── Higher octave ──

    public NumberedPhraseBuilder h(Deg d)                { return note(d, 1, defaultDur); }
    public NumberedPhraseBuilder h(Deg d, BaseValue v)   { return note(d, 1, v); }

    // ── Dotted notes ──

    public NumberedPhraseBuilder nd(Deg d, BaseValue v)  { return dotted(d, 0, v); }
    public NumberedPhraseBuilder ld(Deg d, BaseValue v)  { return dotted(d, -1, v); }
    public NumberedPhraseBuilder hd(Deg d, BaseValue v)  { return dotted(d, 1, v); }

    // ── Ornamented notes ──

    public NumberedPhraseBuilder n(Deg d, BaseValue v, Ornament o)  { return orn(d, 0, v, o); }
    public NumberedPhraseBuilder l(Deg d, BaseValue v, Ornament o)  { return orn(d, -1, v, o); }
    public NumberedPhraseBuilder h(Deg d, BaseValue v, Ornament o)  { return orn(d, 1, v, o); }

    // ── Poly (multiple pitches, same duration) ──

    /** Poly note at current octave, default duration. */
    public NumberedPhraseBuilder poly(Deg... degrees) { return polyNote(0, defaultDur, degrees); }

    /** Poly note at current octave, specified duration. */
    public NumberedPhraseBuilder poly(BaseValue v, Deg... degrees) { return polyNote(0, v, degrees); }

    // ── Rest ──

    public NumberedPhraseBuilder r(BaseValue v) {
        current.add(new RestNode(Duration.of(v)));
        return this;
    }

    // ── Dynamic shortcuts ──

    public NumberedPhraseBuilder dyn(Dynamic d) { current.add(new DynamicNode(d)); return this; }
    public NumberedPhraseBuilder ppp() { return dyn(Dynamic.PPP); }
    public NumberedPhraseBuilder pp()  { return dyn(Dynamic.PP); }
    public NumberedPhraseBuilder p()   { return dyn(Dynamic.P); }
    public NumberedPhraseBuilder mp()  { return dyn(Dynamic.MP); }
    public NumberedPhraseBuilder mf()  { return dyn(Dynamic.MF); }
    public NumberedPhraseBuilder f()   { return dyn(Dynamic.F); }
    public NumberedPhraseBuilder ff()  { return dyn(Dynamic.FF); }
    public NumberedPhraseBuilder fff() { return dyn(Dynamic.FFF); }

    // ── Tempo ──

    public NumberedPhraseBuilder tempo(int bpm) { current.add(new TempoChangeNode(bpm)); return this; }
    public NumberedPhraseBuilder transitionStart() { current.add(new TempoTransitionStartNode()); return this; }
    public NumberedPhraseBuilder transitionEnd(int targetBpm) {
        current.add(new TempoTransitionEndNode(targetBpm, TransitionMethod.LINEAR)); return this;
    }
    public NumberedPhraseBuilder transitionEnd(int targetBpm, TransitionMethod method) {
        current.add(new TempoTransitionEndNode(targetBpm, method)); return this;
    }
    public NumberedPhraseBuilder accelStart()         { return transitionStart(); }
    public NumberedPhraseBuilder accel(int targetBpm) { return transitionEnd(targetBpm); }
    public NumberedPhraseBuilder ritStart()           { return transitionStart(); }
    public NumberedPhraseBuilder rit(int targetBpm)   { return transitionEnd(targetBpm); }

    // ── Internals ──

    private void flush() {
        if (current != null) {
            bars.add(new Bar(ts.barSixtyFourths(), current, List.of()));
            current = null;
        }
    }

    private NumberedPhraseBuilder note(Deg d, int octaveOffset, BaseValue dur) {
        current.add(PitchNode.of(pitch(d, octaveOffset), Duration.of(dur)));
        return this;
    }

    private NumberedPhraseBuilder dotted(Deg d, int octaveOffset, BaseValue dur) {
        current.add(PitchNode.of(pitch(d, octaveOffset), Duration.dotted(dur)));
        return this;
    }

    private NumberedPhraseBuilder orn(Deg d, int octaveOffset, BaseValue dur, Ornament ornament) {
        current.add(PitchNode.ornamented(pitch(d, octaveOffset), Duration.of(dur), ornament));
        return this;
    }

    private NumberedPhraseBuilder polyNote(int octaveOffset, BaseValue dur, Deg[] degrees) {
        var pitches = new java.util.ArrayList<music.notation.pitch.Pitch>(degrees.length);
        for (Deg d : degrees) {
            pitches.add(pitch(d, octaveOffset));
        }
        current.add(PitchNode.poly(Duration.of(dur), pitches));
        return this;
    }

    private StaffPitch pitch(Deg d, int octaveOffset) {
        return degreeToStaffPitch(d.value(), baseOctave + octaveOffset);
    }

    /** Major scale semitone offsets for degrees 1–7. */
    private static final int[] MAJOR_SCALE = {0, 2, 4, 5, 7, 9, 11};

    /** Note names in cyclic letter order, starting from C. */
    private static final NoteName[] LETTER_ORDER = {
            NoteName.C, NoteName.D, NoteName.E, NoteName.F, NoteName.G, NoteName.A, NoteName.B
    };

    /** Natural-semitone-from-C for each NoteName. */
    private static int naturalSemitone(NoteName name) {
        return switch (name) {
            case C -> 0;
            case D -> 2;
            case E -> 4;
            case F -> 5;
            case G -> 7;
            case A -> 9;
            case B -> 11;
        };
    }

    private static int letterIndex(NoteName name) {
        return switch (name) {
            case C -> 0;
            case D -> 1;
            case E -> 2;
            case F -> 3;
            case G -> 4;
            case A -> 5;
            case B -> 6;
        };
    }

    private static int tonicAccidentalOffset(Accidental a) {
        return switch (a) {
            case DOUBLE_FLAT -> -2;
            case FLAT -> -1;
            case NATURAL -> 0;
            case SHARP -> 1;
            case DOUBLE_SHARP -> 2;
        };
    }

    /**
     * Translate a numbered-notation degree (1–7) at the given absolute octave
     * into a {@link StaffPitch} that produces the same MIDI value as the
     * legacy {@code NumberedPitch + MidiMapper.numberedPitchToMidi} pipeline
     * would have produced.
     *
     * <p>The note letter is the {@code (degree-1)}-th letter beyond the tonic
     * in cyclic letter order; the accidental is whatever bridges that letter's
     * natural semitone to the diatonic-major-scale semitone offset; the
     * octave is bumped by one for every wrap past B.</p>
     */
    private StaffPitch degreeToStaffPitch(int degree, int octave) {
        int tonicLetterIdx = letterIndex(tonic);
        int targetLetterIdx = (tonicLetterIdx + degree - 1) % 7;
        int letterWraps = (tonicLetterIdx + degree - 1) / 7;

        NoteName resultName = LETTER_ORDER[targetLetterIdx];

        // The MIDI semitone that the numbered pipeline would have produced.
        // Note: we compute the offset from the tonic's natural semitone, then
        // add it to the chosen letter's natural semitone (with a possible
        // 12-semitone wrap accounted for via letterWraps), and let the
        // accidental absorb the difference.
        int numberedSemitone = naturalSemitone(tonic) + tonicAccidentalOffset(tonicAccidental)
                + MAJOR_SCALE[degree - 1];
        int letterNaturalSemitone = naturalSemitone(resultName) + 12 * letterWraps;
        int accidentalSemitones = numberedSemitone - letterNaturalSemitone;

        Accidental acc = switch (accidentalSemitones) {
            case -2 -> Accidental.DOUBLE_FLAT;
            case -1 -> Accidental.FLAT;
            case 0  -> Accidental.NATURAL;
            case 1  -> Accidental.SHARP;
            case 2  -> Accidental.DOUBLE_SHARP;
            default -> throw new IllegalStateException(
                    "degreeToStaffPitch: accidental delta " + accidentalSemitones
                            + " outside [-2,+2] for tonic=" + tonic + tonicAccidental
                            + " degree=" + degree);
        };

        return new StaffPitch(resultName, acc, new Octave(octave + letterWraps));
    }
}
