package music.notation.phrase;

/**
 * Scale degrees 1–7 for the numbered-notation fluent DSL.
 *
 * <p>Static-import for concise melodies:
 * {@code import static music.notation.phrase.Deg.*;}
 */
public enum Deg {
    _1(1), _2(2), _3(3), _4(4), _5(5), _6(6), _7(7);

    private final int value;

    Deg(int value) { this.value = value; }

    public int value() { return value; }
}
