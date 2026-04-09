package music.notation.phrase;

import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.event.Ornament;
import music.notation.pitch.Accidental;
import music.notation.pitch.AccidentedNote;
import music.notation.pitch.Note;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.pitch.ShiftedNote;
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
    private boolean isPickup;         // true when current bar is a pickup

    // ── Per-bar default duration ──
    private Duration activeDur;  // per-bar override; null → use builder defaultDur

    // ── Aux bar state ──
    private List<PhraseNode> primaryNodes;                       // saved when first aux() is called
    private List<AuxBar> pendingAuxBars = new ArrayList<>();     // completed aux bars for current bar
    private boolean inAux;                                       // currently building aux content
    private List<List<AuxBar>> allBarAuxBars = new ArrayList<>(); // aux bars per bar, accumulated during build
    private final List<Boolean> pickupFlags = new ArrayList<>(); // true if bar at index was a pickup
    private List<MelodicPhrase> lastAuxPhrases = List.of();      // aux phrases from most recent build()

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
        isPickup = false;
        activeDur = null;
        return this;
    }

    /** Start a new bar with a per-bar default duration. */
    public StaffPhraseBuilder bar(Duration barDefault) {
        bar();
        activeDur = barDefault;
        return this;
    }

    /**
     * Start a pickup (anacrusis) bar. Notes added after this call form the
     * pickup; when the bar is finalized a {@link PaddingNode} is prepended
     * to fill the remaining beats.
     */
    public StaffPhraseBuilder pickup() {
        flush();
        current = new ArrayList<>();
        isPickup = true;
        activeDur = null;
        return this;
    }

    /** Start a pickup bar with a per-bar default duration. */
    public StaffPhraseBuilder pickup(Duration barDefault) {
        pickup();
        activeDur = barDefault;
        return this;
    }

    /**
     * Finalize the current bar as an ending — a {@link PaddingNode} is
     * appended to fill any remaining beats after the notes already added.
     */
    public StaffPhraseBuilder ending() {
        if (current == null) {
            throw new IllegalStateException("ending() called without a preceding bar()");
        }

        List<PhraseNode> barNodes;
        List<AuxBar> barAuxBars;

        if (inAux) {
            pendingAuxBars.add(new AuxBar(current));
            barNodes = primaryNodes;
            barAuxBars = List.copyOf(pendingAuxBars);
            primaryNodes = null;
            pendingAuxBars = new ArrayList<>();
            inAux = false;
        } else {
            barNodes = current;
            barAuxBars = List.of();
        }

        final int noteTotal = barNodes.stream().mapToInt(Bar::nodeSixtyFourths).sum();
        final int padding = ts.barSixtyFourths() - noteTotal;
        if (padding > 0) {
            barNodes.add(new PaddingNode(Duration.ofSixtyFourths(padding)));
        }
        bars.add(new Bar(ts.barSixtyFourths(), barNodes, barAuxBars));
        allBarAuxBars.add(barAuxBars);
        pickupFlags.add(false); // ending() is never a pickup
        current = null;
        return this;
    }

    /**
     * Terminate the primary bar content (or previous aux bar) and start
     * building an auxiliary bar. Aux bars play simultaneously with the
     * primary content, sharing the same instrument.
     */
    public StaffPhraseBuilder aux() {
        if (current == null) {
            throw new IllegalStateException("aux() called without a preceding bar()");
        }
        if (!inAux) {
            // First aux in this bar — save primary content
            primaryNodes = current;
        } else {
            // Subsequent aux — wrap current as completed AuxBar
            pendingAuxBars.add(new AuxBar(current));
        }
        current = new ArrayList<>();
        inAux = true;
        activeDur = null;
        return this;
    }

    /** Start an aux bar with a per-bar default duration. */
    public StaffPhraseBuilder aux(Duration auxDefault) {
        aux();
        activeDur = auxDefault;
        return this;
    }

    public MelodicPhrase build(PhraseMarking marking) {
        flush();
        var result = MelodicPhrase.fromBars(ts, marking, bars.toArray(Bar[]::new));
        bars.clear();
        // Build aux phrases from collected aux bars, then reset
        lastAuxPhrases = buildAuxPhrasesInternal(marking);
        allBarAuxBars = new ArrayList<>();
        pickupFlags.clear();
        return result;
    }

    /**
     * Returns the aux phrases produced by the most recent {@link #build(PhraseMarking)}
     * call. Each aux voice index (1st aux bar per bar → voice 0, etc.) becomes a
     * separate phrase. Bars without aux content at a given voice index get rest padding.
     *
     * @return list of aux phrases (empty if no aux bars were used)
     */
    public List<MelodicPhrase> auxPhrases() {
        return lastAuxPhrases;
    }

    private List<MelodicPhrase> buildAuxPhrasesInternal(PhraseMarking marking) {
        int maxVoices = 0;
        for (List<AuxBar> barAux : allBarAuxBars) {
            maxVoices = Math.max(maxVoices, barAux.size());
        }
        if (maxVoices == 0) {
            return List.of();
        }

        var result = new ArrayList<MelodicPhrase>(maxVoices);
        for (int v = 0; v < maxVoices; v++) {
            var auxBars = new ArrayList<Bar>();
            for (int b = 0; b < allBarAuxBars.size(); b++) {
                List<AuxBar> barAux = allBarAuxBars.get(b);
                boolean pickup = b < pickupFlags.size() && pickupFlags.get(b);
                int barSize = ts.barSixtyFourths();
                if (v < barAux.size()) {
                    var nodes = new ArrayList<>(barAux.get(v).nodes());
                    int total = nodes.stream().mapToInt(Bar::nodeSixtyFourths).sum();
                    int gap = barSize - total;
                    if (gap > 0) {
                        // Use PaddingNode for pickup bars so leading-padding detection works
                        PhraseNode fill = pickup
                                ? new PaddingNode(Duration.ofSixtyFourths(gap))
                                : new RestNode(Duration.ofSixtyFourths(gap));
                        nodes.add(fill);
                    }
                    auxBars.add(new Bar(barSize, nodes, List.of()));
                } else {
                    // No aux at this voice index — fill entire bar
                    PhraseNode fill = pickup
                            ? new PaddingNode(Duration.ofSixtyFourths(barSize))
                            : new RestNode(Duration.ofSixtyFourths(barSize));
                    auxBars.add(new Bar(barSize, List.of(fill), List.of()));
                }
            }
            result.add(MelodicPhrase.fromBars(ts, marking, auxBars.toArray(Bar[]::new)));
        }
        return result;
    }

    // ── Slur ──

    public StaffPhraseBuilder slurStart() { current.add(new SlurStart()); return this; }
    public StaffPhraseBuilder slurEnd()   { current.add(new SlurEnd());   return this; }
    public StaffPhraseBuilder slur()      { return slurStart(); }

    // ── Octave 2 ──

    public StaffPhraseBuilder o2(Note... ns)                  { return notes(2, currentDur(), ns); }
    public StaffPhraseBuilder o2(Duration d, Note... ns)      { return notes(2, d, ns); }
    public StaffPhraseBuilder o2(Note n, BaseValue v, Ornament o)  { return orn(n, 2, v, o); }

    // ── Octave 3 ──

    public StaffPhraseBuilder o3(Note... ns)                  { return notes(3, currentDur(), ns); }
    public StaffPhraseBuilder o3(Duration d, Note... ns)      { return notes(3, d, ns); }
    public StaffPhraseBuilder o3(Note n, BaseValue v, Ornament o)  { return orn(n, 3, v, o); }

    // ── Octave 4 ──

    public StaffPhraseBuilder o4(Note... ns)                  { return notes(4, currentDur(), ns); }
    public StaffPhraseBuilder o4(Duration d, Note... ns)      { return notes(4, d, ns); }
    public StaffPhraseBuilder o4(Note n, BaseValue v, Ornament o)  { return orn(n, 4, v, o); }

    // ── Octave 5 ──

    public StaffPhraseBuilder o5(Note... ns)                  { return notes(5, currentDur(), ns); }
    public StaffPhraseBuilder o5(Duration d, Note... ns)      { return notes(5, d, ns); }
    public StaffPhraseBuilder o5(Note n, BaseValue v, Ornament o)  { return orn(n, 5, v, o); }

    // ── Octave 6 ──

    public StaffPhraseBuilder o6(Note... ns)                  { return notes(6, currentDur(), ns); }
    public StaffPhraseBuilder o6(Duration d, Note... ns)      { return notes(6, d, ns); }
    public StaffPhraseBuilder o6(Note n, BaseValue v, Ornament o)  { return orn(n, 6, v, o); }

    // ── Octave 7 ──

    public StaffPhraseBuilder o7(Note... ns)                  { return notes(7, currentDur(), ns); }
    public StaffPhraseBuilder o7(Duration d, Note... ns)      { return notes(7, d, ns); }
    public StaffPhraseBuilder o7(Note n, BaseValue v, Ornament o)  { return orn(n, 7, v, o); }

    // ── Grace note ──

    public StaffPhraseBuilder grace(Note n, int oct)          { return addGrace(n, oct, false); }
    public StaffPhraseBuilder accentedGrace(Note n, int oct)  { return addGrace(n, oct, true); }

    // ── Rest ──

    /** Rest using the current active duration (per-bar or builder default). */
    public StaffPhraseBuilder r() {
        current.add(new RestNode(currentDur()));
        return this;
    }

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

    /** Per-bar duration if set, otherwise the builder-level default. */
    private Duration currentDur() {
        return activeDur != null ? activeDur : defaultDur;
    }

    private Pitch resolve(Note n, int oct) {
        final int effectiveOct = oct + n.octaveShift();
        final NoteName name = n.noteName();
        final Accidental acc = resolveAccidental(n);
        return Pitch.of(name, acc, effectiveOct);
    }

    private Accidental resolveAccidental(Note n) {
        return switch (n) {
            case NoteName name -> keyAccidentals.getOrDefault(name, Accidental.NATURAL);
            case AccidentedNote an -> an.accidental();
            case ShiftedNote sn -> resolveAccidental(sn.base());
        };
    }

    private void flush() {
        if (current != null) {
            List<PhraseNode> barNodes;
            List<AuxBar> barAuxBars;

            if (inAux) {
                // Wrap final aux bar
                pendingAuxBars.add(new AuxBar(current));
                barNodes = primaryNodes;
                barAuxBars = List.copyOf(pendingAuxBars);
                // Reset aux state
                primaryNodes = null;
                pendingAuxBars = new ArrayList<>();
                inAux = false;
            } else {
                barNodes = current;
                barAuxBars = List.of();
            }

            boolean wasPickup = isPickup;
            if (isPickup) {
                final int noteTotal = barNodes.stream().mapToInt(Bar::nodeSixtyFourths).sum();
                final int padding = ts.barSixtyFourths() - noteTotal;
                if (padding > 0) {
                    barNodes.addFirst(new PaddingNode(Duration.ofSixtyFourths(padding)));
                }
                isPickup = false;
            }

            bars.add(new Bar(ts.barSixtyFourths(), barNodes, barAuxBars));
            allBarAuxBars.add(barAuxBars);
            pickupFlags.add(wasPickup);
            current = null;
        }
    }

    private StaffPhraseBuilder notes(int oct, Duration dur, Note... ns) {
        final var pitches = new ArrayList<Pitch>(ns.length);
        for (final Note n : ns) {
            pitches.add(resolve(n, oct));
        }
        current.add(NoteNode.poly(dur, pitches));
        return this;
    }

    private StaffPhraseBuilder orn(Note n, int oct, BaseValue dur, Ornament ornament) {
        current.add(NoteNode.ornamented(resolve(n, oct), Duration.of(dur), ornament));
        return this;
    }

    private StaffPhraseBuilder addGrace(Note n, int oct, boolean accented) {
        current.add(new GraceNote(resolve(n, oct), accented));
        return this;
    }
}
