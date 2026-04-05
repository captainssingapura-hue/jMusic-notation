package music.notation.phrase;

import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.event.Ornament;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for staff-notation melodic phrases using absolute pitch names.
 *
 * <p>Usage:
 * <pre>{@code
 * var P = StaffPhraseBuilder.in(TS);           // default duration: EIGHTH
 *
 * var melody = P
 *     .bar().mf().o5(C).o5(D).o5(E, QUARTER).o5(F, QUARTER)
 *     .bar().o5(G, HALF).o5(E, HALF)
 *     .bar().o4(A).o4(B, FLAT).o5(C, QUARTER).r(QUARTER)
 *     .build(breath());
 * }</pre>
 *
 * <p>Each {@code oN} method family targets octave N (2–7). Overloads:</p>
 * <ul>
 *   <li>{@code o4(C)}              — natural, default duration</li>
 *   <li>{@code o4(C, QUARTER)}     — natural, explicit duration</li>
 *   <li>{@code o4(B, FLAT)}        — accidental, default duration</li>
 *   <li>{@code o4(B, FLAT, QUARTER)} — accidental, explicit duration</li>
 *   <li>{@code o4d(C, QUARTER)}    — dotted natural</li>
 *   <li>{@code o4(C, QUARTER, TRILL)} — ornamented</li>
 * </ul>
 *
 * <p>Bar management, slurs, rests, and dynamics work identically to
 * {@link NumberedPhraseBuilder}. The builder is reusable after {@code build()}.</p>
 */
public final class StaffPhraseBuilder {

    private final TimeSignature ts;
    private final BaseValue defaultDur;

    private final List<Bar> bars = new ArrayList<>();
    private List<PhraseNode> current; // null until bar() is called

    private StaffPhraseBuilder(TimeSignature ts, BaseValue defaultDur) {
        this.ts = ts;
        this.defaultDur = defaultDur;
    }

    /** Create a builder with default duration EIGHTH. */
    public static StaffPhraseBuilder in(TimeSignature ts) {
        return new StaffPhraseBuilder(ts, BaseValue.EIGHTH);
    }

    /** Create a builder with a custom default note duration. */
    public static StaffPhraseBuilder in(TimeSignature ts, BaseValue defaultDur) {
        return new StaffPhraseBuilder(ts, defaultDur);
    }

    // ── Bar management ──

    public StaffPhraseBuilder bar() {
        flush();
        current = new ArrayList<>();
        return this;
    }

    public MelodicPhrase build(PhraseMarking marking) {
        flush();
        var result = MelodicPhrase.fromBars(ts, marking, bars.toArray(Bar[]::new));
        bars.clear();
        return result;
    }

    // ── Slur ──

    public StaffPhraseBuilder slurStart() { current.add(new SlurStart()); return this; }
    public StaffPhraseBuilder slurEnd()   { current.add(new SlurEnd());   return this; }
    public StaffPhraseBuilder slur()      { return slurStart(); }

    // ── Octave 2 ──

    public StaffPhraseBuilder o2(NoteName n)                              { return note(n, 2, defaultDur); }
    public StaffPhraseBuilder o2(NoteName n, BaseValue v)                 { return note(n, 2, v); }
    public StaffPhraseBuilder o2(NoteName n, Accidental a)                { return note(n, a, 2, defaultDur); }
    public StaffPhraseBuilder o2(NoteName n, Accidental a, BaseValue v)   { return note(n, a, 2, v); }
    public StaffPhraseBuilder o2d(NoteName n, BaseValue v)                { return dotted(n, 2, v); }
    public StaffPhraseBuilder o2d(NoteName n, Accidental a, BaseValue v)  { return dotted(n, a, 2, v); }
    public StaffPhraseBuilder o2(NoteName n, BaseValue v, Ornament o)                { return orn(n, 2, v, o); }
    public StaffPhraseBuilder o2(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 2, v, o); }

    // ── Octave 3 ──

    public StaffPhraseBuilder o3(NoteName n)                              { return note(n, 3, defaultDur); }
    public StaffPhraseBuilder o3(NoteName n, BaseValue v)                 { return note(n, 3, v); }
    public StaffPhraseBuilder o3(NoteName n, Accidental a)                { return note(n, a, 3, defaultDur); }
    public StaffPhraseBuilder o3(NoteName n, Accidental a, BaseValue v)   { return note(n, a, 3, v); }
    public StaffPhraseBuilder o3d(NoteName n, BaseValue v)                { return dotted(n, 3, v); }
    public StaffPhraseBuilder o3d(NoteName n, Accidental a, BaseValue v)  { return dotted(n, a, 3, v); }
    public StaffPhraseBuilder o3(NoteName n, BaseValue v, Ornament o)                { return orn(n, 3, v, o); }
    public StaffPhraseBuilder o3(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 3, v, o); }

    // ── Octave 4 ──

    public StaffPhraseBuilder o4(NoteName n)                              { return note(n, 4, defaultDur); }
    public StaffPhraseBuilder o4(NoteName n, BaseValue v)                 { return note(n, 4, v); }
    public StaffPhraseBuilder o4(NoteName n, Accidental a)                { return note(n, a, 4, defaultDur); }
    public StaffPhraseBuilder o4(NoteName n, Accidental a, BaseValue v)   { return note(n, a, 4, v); }
    public StaffPhraseBuilder o4d(NoteName n, BaseValue v)                { return dotted(n, 4, v); }
    public StaffPhraseBuilder o4d(NoteName n, Accidental a, BaseValue v)  { return dotted(n, a, 4, v); }
    public StaffPhraseBuilder o4(NoteName n, BaseValue v, Ornament o)                { return orn(n, 4, v, o); }
    public StaffPhraseBuilder o4(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 4, v, o); }

    // ── Octave 5 ──

    public StaffPhraseBuilder o5(NoteName n)                              { return note(n, 5, defaultDur); }
    public StaffPhraseBuilder o5(NoteName n, BaseValue v)                 { return note(n, 5, v); }
    public StaffPhraseBuilder o5(NoteName n, Accidental a)                { return note(n, a, 5, defaultDur); }
    public StaffPhraseBuilder o5(NoteName n, Accidental a, BaseValue v)   { return note(n, a, 5, v); }
    public StaffPhraseBuilder o5d(NoteName n, BaseValue v)                { return dotted(n, 5, v); }
    public StaffPhraseBuilder o5d(NoteName n, Accidental a, BaseValue v)  { return dotted(n, a, 5, v); }
    public StaffPhraseBuilder o5(NoteName n, BaseValue v, Ornament o)                { return orn(n, 5, v, o); }
    public StaffPhraseBuilder o5(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 5, v, o); }

    // ── Octave 6 ──

    public StaffPhraseBuilder o6(NoteName n)                              { return note(n, 6, defaultDur); }
    public StaffPhraseBuilder o6(NoteName n, BaseValue v)                 { return note(n, 6, v); }
    public StaffPhraseBuilder o6(NoteName n, Accidental a)                { return note(n, a, 6, defaultDur); }
    public StaffPhraseBuilder o6(NoteName n, Accidental a, BaseValue v)   { return note(n, a, 6, v); }
    public StaffPhraseBuilder o6d(NoteName n, BaseValue v)                { return dotted(n, 6, v); }
    public StaffPhraseBuilder o6d(NoteName n, Accidental a, BaseValue v)  { return dotted(n, a, 6, v); }
    public StaffPhraseBuilder o6(NoteName n, BaseValue v, Ornament o)                { return orn(n, 6, v, o); }
    public StaffPhraseBuilder o6(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 6, v, o); }

    // ── Octave 7 ──

    public StaffPhraseBuilder o7(NoteName n)                              { return note(n, 7, defaultDur); }
    public StaffPhraseBuilder o7(NoteName n, BaseValue v)                 { return note(n, 7, v); }
    public StaffPhraseBuilder o7(NoteName n, Accidental a)                { return note(n, a, 7, defaultDur); }
    public StaffPhraseBuilder o7(NoteName n, Accidental a, BaseValue v)   { return note(n, a, 7, v); }
    public StaffPhraseBuilder o7d(NoteName n, BaseValue v)                { return dotted(n, 7, v); }
    public StaffPhraseBuilder o7d(NoteName n, Accidental a, BaseValue v)  { return dotted(n, a, 7, v); }
    public StaffPhraseBuilder o7(NoteName n, BaseValue v, Ornament o)                { return orn(n, 7, v, o); }
    public StaffPhraseBuilder o7(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 7, v, o); }

    // ── Grace note ──

    public StaffPhraseBuilder grace(NoteName n, int oct)                            { return addGrace(n, Accidental.NATURAL, oct, false); }
    public StaffPhraseBuilder grace(NoteName n, Accidental a, int oct)              { return addGrace(n, a, oct, false); }
    public StaffPhraseBuilder accentedGrace(NoteName n, int oct)                    { return addGrace(n, Accidental.NATURAL, oct, true); }
    public StaffPhraseBuilder accentedGrace(NoteName n, Accidental a, int oct)      { return addGrace(n, a, oct, true); }

    // ── Rest ──

    public StaffPhraseBuilder r(BaseValue v) {
        current.add(new RestNode(Duration.of(v)));
        return this;
    }

    public StaffPhraseBuilder rd(BaseValue v) {
        current.add(new RestNode(Duration.dotted(v)));
        return this;
    }

    // ── Dynamic shortcuts ──

    public StaffPhraseBuilder dyn(Dynamic d) { current.add(new DynamicNode(d)); return this; }
    public StaffPhraseBuilder ppp() { return dyn(Dynamic.PPP); }
    public StaffPhraseBuilder pp()  { return dyn(Dynamic.PP); }
    public StaffPhraseBuilder p()   { return dyn(Dynamic.P); }
    public StaffPhraseBuilder mp()  { return dyn(Dynamic.MP); }
    public StaffPhraseBuilder mf()  { return dyn(Dynamic.MF); }
    public StaffPhraseBuilder f()   { return dyn(Dynamic.F); }
    public StaffPhraseBuilder ff()  { return dyn(Dynamic.FF); }
    public StaffPhraseBuilder fff() { return dyn(Dynamic.FFF); }

    // ── Internals ──

    private void flush() {
        if (current != null) {
            bars.add(new Bar(ts.barSixtyFourths(), current));
            current = null;
        }
    }

    private StaffPhraseBuilder note(NoteName n, int oct, BaseValue dur) {
        current.add(NoteNode.of(Pitch.of(n, oct), Duration.of(dur)));
        return this;
    }

    private StaffPhraseBuilder note(NoteName n, Accidental a, int oct, BaseValue dur) {
        current.add(NoteNode.of(Pitch.of(n, a, oct), Duration.of(dur)));
        return this;
    }

    private StaffPhraseBuilder dotted(NoteName n, int oct, BaseValue dur) {
        current.add(NoteNode.of(Pitch.of(n, oct), Duration.dotted(dur)));
        return this;
    }

    private StaffPhraseBuilder dotted(NoteName n, Accidental a, int oct, BaseValue dur) {
        current.add(NoteNode.of(Pitch.of(n, a, oct), Duration.dotted(dur)));
        return this;
    }

    private StaffPhraseBuilder orn(NoteName n, int oct, BaseValue dur, Ornament ornament) {
        current.add(NoteNode.ornamented(Pitch.of(n, oct), Duration.of(dur), ornament));
        return this;
    }

    private StaffPhraseBuilder addGrace(NoteName n, Accidental a, int oct, boolean accented) {
        current.add(new GraceNote(Pitch.of(n, a, oct), accented));
        return this;
    }

    private StaffPhraseBuilder orn(NoteName n, Accidental a, int oct, BaseValue dur, Ornament ornament) {
        current.add(NoteNode.ornamented(Pitch.of(n, a, oct), Duration.of(dur), ornament));
        return this;
    }
}
