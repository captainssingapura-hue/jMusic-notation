package music.notation.performance;

/**
 * Musical articulation intent. The codec currently writes nothing and reads nothing
 * for articulation, so {@link Articulations} is a write-only authoring primitive:
 * round-tripping a Performance with non-empty articulation will lose it (documented
 * in {@code MidiCodec}).
 */
public enum Articulation { LEGATO, STACCATO, TENUTO, MARCATO, ACCENT, NORMAL }
