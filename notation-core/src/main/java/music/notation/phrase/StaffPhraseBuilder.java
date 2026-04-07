package music.notation.phrase;

import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.event.Ornament;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for staff-notation melodic phrases using absolute pitch names.
 *
 * <p>Notes are poly by default — pass one or more {@link NoteName}s to each
 * {@code oN} call.  Duration is either the builder's default or an explicit
 * {@link Duration} (which includes {@link BaseValue} constants directly,
 * plus dotted variants via {@code QUARTER.dot()}).
 *
 * <p>Usage:
 * <pre>{@code
 * var P = StaffPhraseBuilder.in(KEY, TS, EIGHTH);
 *
 * var melody = P
 *     .bar().mf().o5(C).o5(D).o5(QUARTER, E).o5(QUARTER, F)
 *     .bar().o5(HALF, G).o5(HALF, E)
 *     .bar().o4(QUARTER.dot(), A, C, E).r(QUARTER)   // dotted poly chord
 *     .build(breath());
 * }</pre>
 *
 * <p>Each {@code oN} method family targets octave N (2–7). Overloads:</p>
 * <ul>
 *   <li>{@code o4(A)}                     — default duration, key-aware</li>
 *   <li>{@code o4(A, C, E)}              — default duration, poly chord</li>
 *   <li>{@code o4(QUARTER, A)}           — explicit duration</li>
 *   <li>{@code o4(QUARTER.dot(), A, C)}  — dotted poly</li>
 *   <li>{@code o4(A, FLAT)}              — accidental override, default duration</li>
 *   <li>{@code o4(QUARTER, A, FLAT)}     — accidental override, explicit duration</li>
 * </ul>
 */
public final class StaffPhraseBuilder {

    private final TimeSignature ts;
    private final Duration defaultDur;
    private final Map<NoteName, Accidental> keyAccidentals;

    private final List<Bar> bars = new ArrayList<>();
    private List<PhraseNode> current; // null until bar() is called

    private StaffPhraseBuilder(TimeSignature ts, Duration defaultDur,
                               Map<NoteName, Accidental> keyAccidentals) {
        this.ts = ts;
        this.defaultDur = defaultDur;
        this.keyAccidentals = keyAccidentals;
    }

    /** Create a builder with default duration EIGHTH, no key signature. */
    public static StaffPhraseBuilder in(TimeSignature ts) {
        return new StaffPhraseBuilder(ts, BaseValue.EIGHTH, Map.of());
    }

    /** Create a builder with a custom default note duration, no key signature. */
    public static StaffPhraseBuilder in(TimeSignature ts, Duration defaultDur) {
        return new StaffPhraseBuilder(ts, defaultDur, Map.of());
    }

    /**
     * Create a builder with a key signature. Notes without an explicit
     * accidental will use the sharps/flats implied by the key.
     */
    public static StaffPhraseBuilder in(KeySignature key, TimeSignature ts) {
        return new StaffPhraseBuilder(ts, BaseValue.EIGHTH, keyAccidentals(key));
    }

    /** Create a builder with a key signature and custom default duration. */
    public static StaffPhraseBuilder in(KeySignature key, TimeSignature ts, Duration defaultDur) {
        return new StaffPhraseBuilder(ts, defaultDur, keyAccidentals(key));
    }

    // ── Key signature → accidental map ──

    private static final NoteName[] SHARP_ORDER = { NoteName.F, NoteName.C, NoteName.G, NoteName.D, NoteName.A, NoteName.E, NoteName.B };
    private static final NoteName[] FLAT_ORDER  = { NoteName.B, NoteName.E, NoteName.A, NoteName.D, NoteName.G, NoteName.C, NoteName.F };

    private static final Map<NoteName, Integer> MAJOR_SHARPS = Map.of(
            NoteName.C, 0, NoteName.G, 1, NoteName.D, 2, NoteName.A, 3,
            NoteName.E, 4, NoteName.B, 5, NoteName.F, 6
    );

    private static final Map<NoteName, Integer> MAJOR_FLATS = Map.of(
            NoteName.C, 0, NoteName.F, 1, NoteName.B, 2, NoteName.E, 3,
            NoteName.A, 4, NoteName.D, 5, NoteName.G, 6
    );

    private static final Map<NoteName, NoteName> MINOR_TO_MAJOR = Map.of(
            NoteName.A, NoteName.C, NoteName.E, NoteName.G, NoteName.B, NoteName.D,
            NoteName.F, NoteName.A, NoteName.C, NoteName.E, NoteName.G, NoteName.B,
            NoteName.D, NoteName.F
    );

    private static Map<NoteName, Accidental> keyAccidentals(final KeySignature key) {
        final NoteName majorTonic = key.mode() == Mode.MINOR
                ? MINOR_TO_MAJOR.getOrDefault(key.tonic(), key.tonic())
                : key.tonic();

        final var map = new EnumMap<NoteName, Accidental>(NoteName.class);

        if (MAJOR_SHARPS.containsKey(majorTonic) && MAJOR_SHARPS.get(majorTonic) > 0) {
            final int count = MAJOR_SHARPS.get(majorTonic);
            for (int i = 0; i < count; i++) {
                map.put(SHARP_ORDER[i], Accidental.SHARP);
            }
        } else if (MAJOR_FLATS.containsKey(majorTonic) && MAJOR_FLATS.get(majorTonic) > 0) {
            final int count = MAJOR_FLATS.get(majorTonic);
            for (int i = 0; i < count; i++) {
                map.put(FLAT_ORDER[i], Accidental.FLAT);
            }
        }

        return Map.copyOf(map);
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

    public StaffPhraseBuilder o2(NoteName... ns)                           { return notes(2, defaultDur, ns); }
    public StaffPhraseBuilder o2(Duration d, NoteName... ns)               { return notes(2, d, ns); }
    public StaffPhraseBuilder o2(NoteName n, Accidental a)                 { return acc(n, a, 2, defaultDur); }
    public StaffPhraseBuilder o2(Duration d, NoteName n, Accidental a)     { return acc(n, a, 2, d); }
    public StaffPhraseBuilder o2(NoteName n, BaseValue v, Ornament o)                { return orn(n, 2, v, o); }
    public StaffPhraseBuilder o2(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 2, v, o); }

    // ── Octave 3 ──

    public StaffPhraseBuilder o3(NoteName... ns)                           { return notes(3, defaultDur, ns); }
    public StaffPhraseBuilder o3(Duration d, NoteName... ns)               { return notes(3, d, ns); }
    public StaffPhraseBuilder o3(NoteName n, Accidental a)                 { return acc(n, a, 3, defaultDur); }
    public StaffPhraseBuilder o3(Duration d, NoteName n, Accidental a)     { return acc(n, a, 3, d); }
    public StaffPhraseBuilder o3(NoteName n, BaseValue v, Ornament o)                { return orn(n, 3, v, o); }
    public StaffPhraseBuilder o3(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 3, v, o); }

    // ── Octave 4 ──

    public StaffPhraseBuilder o4(NoteName... ns)                           { return notes(4, defaultDur, ns); }
    public StaffPhraseBuilder o4(Duration d, NoteName... ns)               { return notes(4, d, ns); }
    public StaffPhraseBuilder o4(NoteName n, Accidental a)                 { return acc(n, a, 4, defaultDur); }
    public StaffPhraseBuilder o4(Duration d, NoteName n, Accidental a)     { return acc(n, a, 4, d); }
    public StaffPhraseBuilder o4(NoteName n, BaseValue v, Ornament o)                { return orn(n, 4, v, o); }
    public StaffPhraseBuilder o4(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 4, v, o); }

    // ── Octave 5 ──

    public StaffPhraseBuilder o5(NoteName... ns)                           { return notes(5, defaultDur, ns); }
    public StaffPhraseBuilder o5(Duration d, NoteName... ns)               { return notes(5, d, ns); }
    public StaffPhraseBuilder o5(NoteName n, Accidental a)                 { return acc(n, a, 5, defaultDur); }
    public StaffPhraseBuilder o5(Duration d, NoteName n, Accidental a)     { return acc(n, a, 5, d); }
    public StaffPhraseBuilder o5(NoteName n, BaseValue v, Ornament o)                { return orn(n, 5, v, o); }
    public StaffPhraseBuilder o5(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 5, v, o); }

    // ── Octave 6 ──

    public StaffPhraseBuilder o6(NoteName... ns)                           { return notes(6, defaultDur, ns); }
    public StaffPhraseBuilder o6(Duration d, NoteName... ns)               { return notes(6, d, ns); }
    public StaffPhraseBuilder o6(NoteName n, Accidental a)                 { return acc(n, a, 6, defaultDur); }
    public StaffPhraseBuilder o6(Duration d, NoteName n, Accidental a)     { return acc(n, a, 6, d); }
    public StaffPhraseBuilder o6(NoteName n, BaseValue v, Ornament o)                { return orn(n, 6, v, o); }
    public StaffPhraseBuilder o6(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 6, v, o); }

    // ── Octave 7 ──

    public StaffPhraseBuilder o7(NoteName... ns)                           { return notes(7, defaultDur, ns); }
    public StaffPhraseBuilder o7(Duration d, NoteName... ns)               { return notes(7, d, ns); }
    public StaffPhraseBuilder o7(NoteName n, Accidental a)                 { return acc(n, a, 7, defaultDur); }
    public StaffPhraseBuilder o7(Duration d, NoteName n, Accidental a)     { return acc(n, a, 7, d); }
    public StaffPhraseBuilder o7(NoteName n, BaseValue v, Ornament o)                { return orn(n, 7, v, o); }
    public StaffPhraseBuilder o7(NoteName n, Accidental a, BaseValue v, Ornament o)  { return orn(n, a, 7, v, o); }

    // ── Grace note ──

    public StaffPhraseBuilder grace(NoteName n, int oct)                            { return addGrace(n, resolve(n), oct, false); }
    public StaffPhraseBuilder grace(NoteName n, Accidental a, int oct)              { return addGrace(n, a, oct, false); }
    public StaffPhraseBuilder accentedGrace(NoteName n, int oct)                    { return addGrace(n, resolve(n), oct, true); }
    public StaffPhraseBuilder accentedGrace(NoteName n, Accidental a, int oct)      { return addGrace(n, a, oct, true); }

    // ── Rest ──

    public StaffPhraseBuilder r(Duration d) {
        current.add(new RestNode(d));
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

    private Accidental resolve(NoteName n) {
        return keyAccidentals.getOrDefault(n, Accidental.NATURAL);
    }

    private void flush() {
        if (current != null) {
            bars.add(new Bar(ts.barSixtyFourths(), current));
            current = null;
        }
    }

    private StaffPhraseBuilder notes(int oct, Duration dur, NoteName... ns) {
        final var pitches = new ArrayList<Pitch>(ns.length);
        for (final NoteName n : ns) {
            pitches.add(Pitch.of(n, resolve(n), oct));
        }
        current.add(NoteNode.poly(dur, pitches));
        return this;
    }

    private StaffPhraseBuilder acc(NoteName n, Accidental a, int oct, Duration dur) {
        current.add(NoteNode.of(Pitch.of(n, a, oct), dur));
        return this;
    }

    private StaffPhraseBuilder orn(NoteName n, int oct, BaseValue dur, Ornament ornament) {
        current.add(NoteNode.ornamented(Pitch.of(n, resolve(n), oct), Duration.of(dur), ornament));
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
