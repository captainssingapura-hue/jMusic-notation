package music.notation.phrase;

/**
 * Marker for entities that may be tied into a successor of the same
 * structural identity. Phase 1 declares the contract everywhere it
 * belongs ({@link PitchNode} and {@code PitchedNote}); the later phases
 * fold tie-coalescing into the codec on emission.
 */
public interface Tieable {
    boolean tiedToNext();
    Tieable withTiedToNext();
}
