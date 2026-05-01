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

import java.util.ArrayList;
import java.util.List;

/**
 * Shared abstract base for {@link BarBuilderTyped} and {@link AuxBarBuilderTyped}.
 * Holds the mutable node list being accumulated and every note-emission method.
 *
 * <p>F-bounded self type so methods return the concrete subclass, preserving
 * fluent chaining without casts:</p>
 * <pre>{@code
 * b.o5(C).o5(D).mf().o5(QUARTER, E)   // each call returns BarBuilderTyped
 * }</pre>
 *
 * <p>Package-private: only the typed-builder classes extend it. One-shot
 * enforcement is provided by {@link #consumed}; subclasses set the flag when
 * their terminating operation runs, and every public note-emission method
 * checks it.</p>
 */
abstract class NoteAcceptor<SELF extends NoteAcceptor<SELF>> {

    final BuilderContext ctx;
    final List<PhraseNode> current = new ArrayList<>();
    Duration activeDur; // per-bar override; null → use ctx.defaultDur()
    boolean consumed;

    NoteAcceptor(BuilderContext ctx, Duration activeDur) {
        this.ctx = ctx;
        this.activeDur = activeDur;
    }

    /** Narrowing self-cast for fluent chaining. */
    @SuppressWarnings("unchecked")
    protected final SELF self() {
        return (SELF) this;
    }

    final void requireNotConsumed() {
        if (consumed) {
            throw new IllegalStateException(
                    getClass().getSimpleName() + " already terminated — fluent chain "
                            + "continued past done()/lambda exit; builders are one-shot.");
        }
    }

    // ── Octave 1–7 ───────────────────────────────────────────────

    public SELF o1(Note... ns)             { return notes(1, currentDur(), ns); }
    public SELF o1(Duration d, Note... ns) { return notes(1, d, ns); }
    public SELF o1(Note n, BaseValue v, Ornament o) { return orn(n, 1, v, o); }

    public SELF o2(Note... ns)             { return notes(2, currentDur(), ns); }
    public SELF o2(Duration d, Note... ns) { return notes(2, d, ns); }
    public SELF o2(Note n, BaseValue v, Ornament o) { return orn(n, 2, v, o); }

    public SELF o3(Note... ns)             { return notes(3, currentDur(), ns); }
    public SELF o3(Duration d, Note... ns) { return notes(3, d, ns); }
    public SELF o3(Note n, BaseValue v, Ornament o) { return orn(n, 3, v, o); }

    public SELF o4(Note... ns)             { return notes(4, currentDur(), ns); }
    public SELF o4(Duration d, Note... ns) { return notes(4, d, ns); }
    public SELF o4(Note n, BaseValue v, Ornament o) { return orn(n, 4, v, o); }

    public SELF o5(Note... ns)             { return notes(5, currentDur(), ns); }
    public SELF o5(Duration d, Note... ns) { return notes(5, d, ns); }
    public SELF o5(Note n, BaseValue v, Ornament o) { return orn(n, 5, v, o); }

    public SELF o6(Note... ns)             { return notes(6, currentDur(), ns); }
    public SELF o6(Duration d, Note... ns) { return notes(6, d, ns); }
    public SELF o6(Note n, BaseValue v, Ornament o) { return orn(n, 6, v, o); }

    public SELF o7(Note... ns)             { return notes(7, currentDur(), ns); }
    public SELF o7(Duration d, Note... ns) { return notes(7, d, ns); }
    public SELF o7(Note n, BaseValue v, Ornament o) { return orn(n, 7, v, o); }

    // ── Rest ─────────────────────────────────────────────────────

    /** Rest using the current active duration (per-bar or builder default). */
    public SELF r() {
        requireNotConsumed();
        current.add(new RestNode(currentDur()));
        return self();
    }

    public SELF r(Duration d) {
        requireNotConsumed();
        current.add(new RestNode(d));
        return self();
    }

    // ── Padding (structural silence) ─────────────────────────────

    /**
     * Insert a {@link PaddingNode} of the given duration. Distinct from
     * {@link #r(Duration)}: padding is <em>structural</em> silence treated
     * specially by the playback layer (leading padding is skipped; trailing
     * padding participates in elision boundaries). Use this at the end of a
     * final bar for the trailing tail that used to be written as
     * {@code .ending()}.
     */
    public SELF pad(Duration d) {
        requireNotConsumed();
        current.add(new PaddingNode(d));
        return self();
    }

    // ── Dynamic shortcuts ────────────────────────────────────────

    public SELF dyn(Dynamic d) {
        requireNotConsumed();
        current.add(new DynamicNode(d));
        return self();
    }
    public SELF ppp() { return dyn(Dynamic.PPP); }
    public SELF pp()  { return dyn(Dynamic.PP); }
    public SELF p()   { return dyn(Dynamic.P); }
    public SELF mp()  { return dyn(Dynamic.MP); }
    public SELF mf()  { return dyn(Dynamic.MF); }
    public SELF f()   { return dyn(Dynamic.F); }
    public SELF ff()  { return dyn(Dynamic.FF); }
    public SELF fff() { return dyn(Dynamic.FFF); }

    // ── Tie (self-closing; chains naturally) ────────────────────

    /**
     * Tie the previously-emitted note into the next same-pitch note. Chains
     * naturally; throws if there is no preceding {@link NoteNode}.
     */
    public SELF tieNext() {
        requireNotConsumed();
        if (current.isEmpty()) {
            throw new IllegalStateException("tieNext() requires a preceding note");
        }
        int last = current.size() - 1;
        PhraseNode prev = current.get(last);
        if (!(prev instanceof PitchNode note)) {
            throw new IllegalStateException(
                    "tieNext() requires a preceding note, but found "
                            + prev.getClass().getSimpleName());
        }
        current.set(last, note.withTiedToNext());
        return self();
    }

    // ── Tempo ────────────────────────────────────────────────────

    public SELF tempo(int bpm) {
        requireNotConsumed();
        current.add(new TempoChangeNode(bpm));
        return self();
    }

    public SELF transitionStart() {
        requireNotConsumed();
        current.add(new TempoTransitionStartNode());
        return self();
    }

    public SELF transitionEnd(int targetBpm) {
        return transitionEnd(targetBpm, TransitionMethod.LINEAR);
    }

    public SELF transitionEnd(int targetBpm, TransitionMethod method) {
        requireNotConsumed();
        current.add(new TempoTransitionEndNode(targetBpm, method));
        return self();
    }

    public SELF accelStart()           { return transitionStart(); }
    public SELF accel(int targetBpm)   { return transitionEnd(targetBpm); }
    public SELF ritStart()             { return transitionStart(); }
    public SELF rit(int targetBpm)     { return transitionEnd(targetBpm); }

    // ── Triplet (direct single-call) ─────────────────────────────

    /**
     * Emit a triplet: three notes sharing {@code dur} equally (each plays {@code dur/3}).
     * All three notes share the same octave.
     */
    public SELF triplet(Duration dur, int oct, Note note1, Note note2, Note note3) {
        requireNotConsumed();
        var graces = List.of(
                new GraceNote(resolve(note1, oct), false),
                new GraceNote(resolve(note2, oct), false)
        );
        current.add(PitchNode.tuplet(graces, dur, List.of(resolve(note3, oct))));
        return self();
    }

    /** Triplet using the builder's current default duration. */
    public SELF triplet(int oct, Note note1, Note note2, Note note3) {
        return triplet(currentDur(), oct, note1, note2, note3);
    }

    // ── Grace notes (sub-builder pattern) ────────────────────────

    public GraceNoteBuilderTyped<SELF> grace(Note n, int oct) {
        requireNotConsumed();
        return new GraceNoteBuilderTyped<>(self(), new GraceNote(resolve(n, oct), false));
    }

    public GraceNoteBuilderTyped<SELF> accentedGrace(Note n, int oct) {
        requireNotConsumed();
        return new GraceNoteBuilderTyped<>(self(), new GraceNote(resolve(n, oct), true));
    }

    // ── Internals ────────────────────────────────────────────────

    /** Per-bar duration if set, otherwise the builder-level default. */
    final Duration currentDur() {
        return activeDur != null ? activeDur : ctx.defaultDur();
    }

    /** Resolve a {@link Note} at a given octave into a concrete {@link Pitch}. */
    final Pitch resolve(Note n, int oct) {
        final int effectiveOct = oct + n.octaveShift();
        final NoteName name = n.noteName();
        final Accidental acc = resolveAccidental(n);
        return Pitch.of(name, acc, effectiveOct);
    }

    private Accidental resolveAccidental(Note n) {
        return switch (n) {
            case NoteName name -> ctx.keyAccidentals().getOrDefault(name, Accidental.NATURAL);
            case AccidentedNote an -> an.accidental();
            case ShiftedNote sn -> resolveAccidental(sn.base());
        };
    }

    private SELF notes(int oct, Duration dur, Note... ns) {
        requireNotConsumed();
        final var pitches = new ArrayList<Pitch>(ns.length);
        for (final Note n : ns) {
            pitches.add(resolve(n, oct));
        }
        current.add(PitchNode.poly(dur, pitches));
        return self();
    }

    private SELF orn(Note n, int oct, BaseValue dur, Ornament ornament) {
        requireNotConsumed();
        current.add(PitchNode.ornamented(resolve(n, oct), Duration.of(dur), ornament));
        return self();
    }
}
