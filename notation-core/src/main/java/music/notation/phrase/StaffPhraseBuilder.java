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
    //
    // Aux (voice overlay) is bar-aligned: one or more aux slots per main bar,
    // each carrying a whole bar's worth of content. When the caller opens aux
    // via .aux() the main bar's nodes are stashed in `primaryNodes`; subsequent
    // aux() calls cycle the accumulated slot into `pendingAuxBars`. On bar
    // close (flush/ending), the completed slots are attached to the Bar and
    // eventually surfaced as VoiceOverlays on the resulting MelodicPhrase.
    private List<PhraseNode> primaryNodes;                       // saved when first aux() is called
    private List<AuxBar> pendingAuxBars = new ArrayList<>();     // completed aux bars for current bar
    private boolean inAux;                                       // currently building aux content


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
    //
    // The accidentals added by a key signature are the first N notes of the
    // circle-of-fifths sharp/flat order. The count depends on (tonic,
    // accidental, mode). Major and Aeolian/Minor are supported exactly;
    // other modes (Dorian, Phrygian, Lydian, Mixolydian, Locrian) fall back
    // to their parent major via scale-degree offsets — users can always
    // override with explicit .s() / .f() / .n() per-note.

    private static final NoteName[] SHARP_ORDER = { NoteName.F, NoteName.C, NoteName.G, NoteName.D, NoteName.A, NoteName.E, NoteName.B };
    private static final NoteName[] FLAT_ORDER  = { NoteName.B, NoteName.E, NoteName.A, NoteName.D, NoteName.G, NoteName.C, NoteName.F };

    private static Map<NoteName, Accidental> keyAccidentals(final KeySignature key) {
        final int[] sf = sharpsAndFlats(key);
        final int sharps = sf[0];
        final int flats  = sf[1];

        final var map = new EnumMap<NoteName, Accidental>(NoteName.class);
        for (int i = 0; i < sharps; i++) map.put(SHARP_ORDER[i], Accidental.SHARP);
        for (int i = 0; i < flats;  i++) map.put(FLAT_ORDER[i],  Accidental.FLAT);
        return Map.copyOf(map);
    }

    /**
     * Count of sharps and flats in the key signature, as {@code {sharps, flats}}.
     * Exactly one of the two is non-zero (or both zero for C major / A minor).
     */
    private static int[] sharpsAndFlats(final KeySignature key) {
        final Mode mode = key.mode();
        final NoteName tonic = key.tonic();
        final Accidental acc = key.accidental();

        // Aeolian is enharmonically equivalent to natural minor.
        if (mode == Mode.MAJOR)                                    return majorKeyCount(tonic, acc);
        if (mode == Mode.MINOR || mode == Mode.AEOLIAN)            return minorKeyCount(tonic, acc);
        // Other modes: approximate via relative-major offset (semitone shift).
        // Best-effort; users can add explicit accidentals per note as needed.
        return modalKeyCount(tonic, acc, mode);
    }

    /** Sharps/flats for a major key. Returns {0,0} for unknown combinations. */
    private static int[] majorKeyCount(NoteName tonic, Accidental acc) {
        return switch (acc) {
            case NATURAL -> switch (tonic) {
                case C -> new int[]{0, 0};
                case G -> new int[]{1, 0};
                case D -> new int[]{2, 0};
                case A -> new int[]{3, 0};
                case E -> new int[]{4, 0};
                case B -> new int[]{5, 0};
                case F -> new int[]{0, 1};   // F major has Bb
            };
            case SHARP -> switch (tonic) {
                case F -> new int[]{6, 0};   // F# major
                case C -> new int[]{7, 0};   // C# major
                default -> new int[]{0, 0};
            };
            case FLAT -> switch (tonic) {
                case B -> new int[]{0, 2};   // Bb major
                case E -> new int[]{0, 3};   // Eb major
                case A -> new int[]{0, 4};   // Ab major
                case D -> new int[]{0, 5};   // Db major
                case G -> new int[]{0, 6};   // Gb major
                case C -> new int[]{0, 7};   // Cb major
                default -> new int[]{0, 0};
            };
            default -> new int[]{0, 0};
        };
    }

    /** Sharps/flats for a natural minor key. Returns {0,0} for unknown combinations. */
    private static int[] minorKeyCount(NoteName tonic, Accidental acc) {
        return switch (acc) {
            case NATURAL -> switch (tonic) {
                case A -> new int[]{0, 0};
                case E -> new int[]{1, 0};
                case B -> new int[]{2, 0};
                case D -> new int[]{0, 1};
                case G -> new int[]{0, 2};
                case C -> new int[]{0, 3};
                case F -> new int[]{0, 4};
            };
            case SHARP -> switch (tonic) {
                case F -> new int[]{3, 0};   // F# minor
                case C -> new int[]{4, 0};   // C# minor
                case G -> new int[]{5, 0};   // G# minor
                case D -> new int[]{6, 0};   // D# minor
                case A -> new int[]{7, 0};   // A# minor
                default -> new int[]{0, 0};
            };
            case FLAT -> switch (tonic) {
                case B -> new int[]{0, 5};   // Bb minor
                case E -> new int[]{0, 6};   // Eb minor
                case A -> new int[]{0, 7};   // Ab minor
                default -> new int[]{0, 0};
            };
            default -> new int[]{0, 0};
        };
    }

    /**
     * Best-effort sharps/flats for a non-major, non-minor mode. Uses the mode's
     * semitone offset from its parent major to shift the sharp count.
     * (e.g. D Dorian is 2 semitones above C major → same signature as C major,
     * which has 0 sharps/flats.)
     */
    private static int[] modalKeyCount(NoteName tonic, Accidental acc, Mode mode) {
        // Default: treat as major for now; users can override accidentals per note.
        // Future improvement: compute parent major by semitone offset per mode.
        return majorKeyCount(tonic, acc);
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

    /**
     * Finalize the current build into a {@link MelodicPhrase}.
     *
     * <p>Any {@code .aux(...)} overlays declared during construction travel
     * with the returned phrase as {@link VoiceOverlay}s — there is no side
     * channel to extract. Bars without overlay content at a given voice index
     * are simply silent (no rest-padding synthesis).</p>
     */
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

    // ── Tie (self-closing; chains naturally) ──

    /**
     * Tie the previously-emitted note into the next same-pitch note. No explicit
     * end is needed — the following note is automatically absorbed at phrase
     * construction. Chains naturally: {@code .tieNext().tieNext()} collapses
     * three notes into one. Throws {@link IllegalStateException} if there is
     * no preceding {@link NoteNode} in the current bar.
     */
    public StaffPhraseBuilder tieNext() {
        if (current == null || current.isEmpty()) {
            throw new IllegalStateException("tieNext() requires a preceding note");
        }
        int last = current.size() - 1;
        PhraseNode prev = current.get(last);
        if (!(prev instanceof NoteNode note)) {
            throw new IllegalStateException(
                    "tieNext() requires a preceding note, but found " + prev.getClass().getSimpleName());
        }
        current.set(last, note.withTiedToNext());
        return this;
    }

    // ── Octave 1 ──

    public StaffPhraseBuilder o1(Note... ns)                  { return notes(1, currentDur(), ns); }
    public StaffPhraseBuilder o1(Duration d, Note... ns)      { return notes(1, d, ns); }
    public StaffPhraseBuilder o1(Note n, BaseValue v, Ornament o)  { return orn(n, 1, v, o); }

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

    // ── Grace note (sub-builder pattern) ──

    /**
     * Begin a grace-note sequence. Returns a {@link GraceNoteBuilder} that
     * collects grace pitches; call {@code .main(oct, note)} to emit the
     * main note and return to this builder.
     */
    public GraceNoteBuilder grace(Note n, int oct)          { return new GraceNoteBuilder(this, new GraceNote(resolve(n, oct), false)); }
    public GraceNoteBuilder accentedGrace(Note n, int oct)  { return new GraceNoteBuilder(this, new GraceNote(resolve(n, oct), true)); }

    // ── Triplet (direct single-call) ──

    /**
     * Emit a triplet: three notes sharing {@code dur} equally (each plays {@code dur/3}).
     * All three notes share the same octave.
     */
    public StaffPhraseBuilder triplet(Duration dur, int oct, Note note1, Note note2, Note note3) {
        var graces = java.util.List.of(
                new GraceNote(resolve(note1, oct), false),
                new GraceNote(resolve(note2, oct), false)
        );
        current.add(NoteNode.tuplet(graces, dur, java.util.List.of(resolve(note3, oct))));
        return this;
    }

    /** Triplet using the builder's current default duration. */
    public StaffPhraseBuilder triplet(int oct, Note note1, Note note2, Note note3) {
        return triplet(currentDur(), oct, note1, note2, note3);
    }

    /**
     * Collects one or more grace notes, then terminates with {@code main()}
     * to create a single {@link NoteNode} whose duration absorbs the graces.
     * Each grace plays briefly; the main note keeps the remaining duration.
     */
    public static final class GraceNoteBuilder {
        private final StaffPhraseBuilder parent;
        private final java.util.ArrayList<GraceNote> graces = new java.util.ArrayList<>();

        GraceNoteBuilder(StaffPhraseBuilder parent, GraceNote first) {
            this.parent = parent;
            graces.add(first);
        }

        public GraceNoteBuilder grace(Note n, int oct) {
            graces.add(new GraceNote(parent.resolve(n, oct), false));
            return this;
        }

        public GraceNoteBuilder accentedGrace(Note n, int oct) {
            graces.add(new GraceNote(parent.resolve(n, oct), true));
            return this;
        }

        /** Emit the main note using the builder's current default duration. */
        public StaffPhraseBuilder main(int oct, Note... ns) {
            return emit(parent.currentDur(), oct, ns);
        }

        /** Emit the main note with an explicit duration. */
        public StaffPhraseBuilder main(Duration d, int oct, Note... ns) {
            return emit(d, oct, ns);
        }

        private StaffPhraseBuilder emit(Duration dur, int oct, Note... ns) {
            final var pitches = new java.util.ArrayList<Pitch>(ns.length);
            for (final Note n : ns) pitches.add(parent.resolve(n, oct));
            parent.current.add(NoteNode.graced(graces, dur, pitches));
            return parent;
        }
    }

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

    // ── Tempo ──

    public StaffPhraseBuilder tempo(int bpm) { current.add(new TempoChangeNode(bpm)); return this; }
    public StaffPhraseBuilder transitionStart() { current.add(new TempoTransitionStartNode()); return this; }
    public StaffPhraseBuilder transitionEnd(int targetBpm) {
        current.add(new TempoTransitionEndNode(targetBpm, TransitionMethod.LINEAR)); return this;
    }
    public StaffPhraseBuilder transitionEnd(int targetBpm, TransitionMethod method) {
        current.add(new TempoTransitionEndNode(targetBpm, method)); return this;
    }
    public StaffPhraseBuilder accelStart()         { return transitionStart(); }
    public StaffPhraseBuilder accel(int targetBpm) { return transitionEnd(targetBpm); }
    public StaffPhraseBuilder ritStart()           { return transitionStart(); }
    public StaffPhraseBuilder rit(int targetBpm)   { return transitionEnd(targetBpm); }

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

            if (isPickup) {
                final int noteTotal = barNodes.stream().mapToInt(Bar::nodeSixtyFourths).sum();
                final int padding = ts.barSixtyFourths() - noteTotal;
                if (padding > 0) {
                    barNodes.addFirst(new PaddingNode(Duration.ofSixtyFourths(padding)));
                }
                isPickup = false;
            }

            bars.add(new Bar(ts.barSixtyFourths(), barNodes, barAuxBars));
            current = null;
        }
    }

    private static boolean isPickupBar(Bar bar) {
        return !bar.nodes().isEmpty() && bar.nodes().getFirst() instanceof PaddingNode;
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

}
