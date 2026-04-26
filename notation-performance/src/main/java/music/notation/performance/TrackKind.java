package music.notation.performance;

/**
 * The kind of a track: melodic or percussion. MIDI 1.0 reserves channel 9 for
 * percussion, so at most one DRUM track exists per Score; all other tracks are PITCHED.
 */
public enum TrackKind { PITCHED, DRUM }
