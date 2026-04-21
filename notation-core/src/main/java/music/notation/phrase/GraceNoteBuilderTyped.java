package music.notation.phrase;

import music.notation.duration.Duration;
import music.notation.pitch.Note;
import music.notation.pitch.Pitch;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-builder for a run of grace notes attached to a following main note.
 * Terminates with {@code main(...)} which emits a single {@link NoteNode}
 * carrying the grace list and returns control to the parent.
 *
 * <p>Parameterized on the parent's self-type so control returns to the
 * correct concrete builder (either {@link BarBuilderTyped} or
 * {@link AuxBarBuilderTyped}).</p>
 */
public final class GraceNoteBuilderTyped<SELF extends NoteAcceptor<SELF>> {

    private final SELF parent;
    private final List<GraceNote> graces = new ArrayList<>();

    GraceNoteBuilderTyped(SELF parent, GraceNote first) {
        this.parent = parent;
        graces.add(first);
    }

    public GraceNoteBuilderTyped<SELF> grace(Note n, int oct) {
        graces.add(new GraceNote(parent.resolve(n, oct), false));
        return this;
    }

    public GraceNoteBuilderTyped<SELF> accentedGrace(Note n, int oct) {
        graces.add(new GraceNote(parent.resolve(n, oct), true));
        return this;
    }

    /** Emit the main note using the parent's current default duration. */
    public SELF main(int oct, Note... ns) {
        return emit(parent.currentDur(), oct, ns);
    }

    /** Emit the main note with an explicit duration. */
    public SELF main(Duration d, int oct, Note... ns) {
        return emit(d, oct, ns);
    }

    private SELF emit(Duration dur, int oct, Note... ns) {
        final var pitches = new ArrayList<Pitch>(ns.length);
        for (final Note n : ns) {
            pitches.add(parent.resolve(n, oct));
        }
        parent.current.add(NoteNode.graced(graces, dur, pitches));
        return parent;
    }
}
