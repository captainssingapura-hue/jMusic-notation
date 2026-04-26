package music.notation.experiments.scale;

import music.notation.core.model.AbstractNote;

/**
 * Common vocabulary for a note expressed in <em>scalar</em> (degree-based)
 * terms rather than absolute pitch. All Japanese pentatonic scales in this
 * module implement this — Hirajoshi, Yo, Insen, Iwato, Ryukyu. Each scale
 * has five degrees (indices {@code 0..4}), but the semitone intervals
 * between them differ.
 *
 * <p>Cross-scale operations (e.g.,
 * {@link music.notation.experiments.transform.ScaleTranspose}) work purely
 * at the degree-index level, preserving melodic contour while swapping the
 * colour of the scale.</p>
 */
public interface ScaleNote extends AbstractNote {

    /** 0-based position within the scale. */
    int degreeIndex();

    /** Octave number in MIDI terms (middle C = octave 4). */
    int octave();

    /** Total number of degrees in this note's scale (5 for all Japanese pentatonics). */
    int degreeCount();

    /** Short human-readable scale name — used in TUI labels and error messages. */
    String scaleName();
}
