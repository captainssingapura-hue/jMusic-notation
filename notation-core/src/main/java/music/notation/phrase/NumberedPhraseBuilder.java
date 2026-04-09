package music.notation.phrase;

import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.event.Ornament;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.NumberedPitch;
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

    // ── Slur ──

    /** Open a slur at this point (typically the end of a bar, before a tie/legato across the bar line). */
    public NumberedPhraseBuilder slurStart() { current.add(new SlurStart()); return this; }

    /** Close a slur at this point (typically after the first note(s) of the next bar). */
    public NumberedPhraseBuilder slurEnd()   { current.add(new SlurEnd());   return this; }

    /**
     * Convenience: open a slur at the end of the current bar.
     * The next bar should begin with the tied/slurred note(s) followed by {@code slurEnd()}.
     */
    public NumberedPhraseBuilder slur() { return slurStart(); }

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

    // ── Internals ──

    private void flush() {
        if (current != null) {
            bars.add(new Bar(ts.barSixtyFourths(), current, List.of()));
            current = null;
        }
    }

    private NumberedPhraseBuilder note(Deg d, int octaveOffset, BaseValue dur) {
        current.add(NoteNode.of(pitch(d, octaveOffset), Duration.of(dur)));
        return this;
    }

    private NumberedPhraseBuilder dotted(Deg d, int octaveOffset, BaseValue dur) {
        current.add(NoteNode.of(pitch(d, octaveOffset), Duration.dotted(dur)));
        return this;
    }

    private NumberedPhraseBuilder orn(Deg d, int octaveOffset, BaseValue dur, Ornament ornament) {
        current.add(NoteNode.ornamented(pitch(d, octaveOffset), Duration.of(dur), ornament));
        return this;
    }

    private NumberedPhraseBuilder polyNote(int octaveOffset, BaseValue dur, Deg[] degrees) {
        var pitches = new java.util.ArrayList<music.notation.pitch.Pitch>(degrees.length);
        for (Deg d : degrees) {
            pitches.add(pitch(d, octaveOffset));
        }
        current.add(NoteNode.poly(Duration.of(dur), pitches));
        return this;
    }

    private NumberedPitch pitch(Deg d, int octaveOffset) {
        return NumberedPitch.of(tonic, tonicAccidental, d.value(), baseOctave + octaveOffset);
    }
}
