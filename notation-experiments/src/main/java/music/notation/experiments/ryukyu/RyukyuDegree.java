package music.notation.experiments.ryukyu;

/**
 * Degrees of the <em>Ryukyu</em> (琉球) scale — the Okinawan pentatonic.
 *
 * <p>Interval pattern from the tonic, in semitones: {@code 0, 4, 5, 7, 11}
 * (pattern {@code 4-1-2-4-1}). Degrees map to {@code I, III, IV, V, VII}
 * — major-third and major-seventh give it a bright, distinctive "Okinawan
 * folk" colour that stands apart from the mainland Japanese scales.</p>
 *
 * <p>Example (C tonic): C, E, F, G, B.</p>
 */
public enum RyukyuDegree {
    I(0), III(4), IV(5), V(7), VII(11);

    private final int semitonesFromTonic;

    RyukyuDegree(int semitonesFromTonic) {
        this.semitonesFromTonic = semitonesFromTonic;
    }

    public int semitonesFromTonic() { return semitonesFromTonic; }
    public int index() { return ordinal(); }

    public static RyukyuDegree ofIndex(int index) {
        final var values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
