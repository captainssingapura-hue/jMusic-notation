package music.notation.transcribe;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;

import java.util.Locale;

/** Parsing, naming and spelling helpers for {@link KeySignature}s (major and minor only). */
public final class Keys {

    private Keys() {}

    private static final int[] LETTER_PC = {0, 2, 4, 5, 7, 9, 11};      // C D E F G A B
    private static final String[] MAJOR_NAMES = {"C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};
    private static final String[] MINOR_NAMES = {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B"};
    private static final String[] SHARP_SPELLING = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] FLAT_SPELLING  = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    /**
     * Parse {@code "Bb major"}, {@code "F# minor"}, {@code "Eb"} (major),
     * {@code "c minor"}. Accepts {@code b}/{@code ♭} and {@code #}/{@code ♯}.
     */
    public static KeySignature parse(String text) {
        String s = text.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("empty key");
        char letter = Character.toUpperCase(s.charAt(0));
        int li = "CDEFGAB".indexOf(letter);
        if (li < 0) throw new IllegalArgumentException("key must start with a note letter: '" + text + "'");
        int i = 1;
        Accidental acc = Accidental.NATURAL;
        if (i < s.length() && (s.charAt(i) == '#' || s.charAt(i) == '♯')) { acc = Accidental.SHARP; i++; }
        else if (i < s.length() && (s.charAt(i) == 'b' || s.charAt(i) == '♭')) { acc = Accidental.FLAT; i++; }
        String rest = s.substring(i).trim().toLowerCase(Locale.ROOT);
        Mode mode = switch (rest) {
            case "", "major", "maj" -> Mode.MAJOR;
            case "minor", "min", "m" -> Mode.MINOR;
            default -> throw new IllegalArgumentException("unknown mode '" + rest + "' in key '" + text + "'");
        };
        return new KeySignature(NoteName.values()[li], acc, mode);
    }

    /** Conventional key for a tonic pitch class (0 = C). */
    public static KeySignature of(int tonicPc, Mode mode) {
        String name = (mode == Mode.MINOR ? MINOR_NAMES : MAJOR_NAMES)[Math.floorMod(tonicPc, 12)];
        return parse(name + (mode == Mode.MINOR ? " minor" : " major"));
    }

    public static int tonicPc(KeySignature k) {
        int pc = LETTER_PC[k.tonic().ordinal()];
        pc += switch (k.accidental()) {
            case DOUBLE_FLAT -> -2; case FLAT -> -1; case NATURAL -> 0; case SHARP -> 1; case DOUBLE_SHARP -> 2;
        };
        return Math.floorMod(pc, 12);
    }

    public static String name(KeySignature k) {
        String acc = switch (k.accidental()) {
            case DOUBLE_FLAT -> "bb"; case FLAT -> "b"; case NATURAL -> ""; case SHARP -> "#"; case DOUBLE_SHARP -> "##";
        };
        String mode = k.mode() == Mode.MINOR ? "minor" : k.mode() == Mode.MAJOR ? "major" : k.mode().name().toLowerCase(Locale.ROOT);
        return k.tonic().name() + acc + " " + mode;
    }

    /** Signature as sharps (+) or flats (−), via the relative major. */
    public static int fifths(KeySignature k) {
        int majorPc = k.mode() == Mode.MINOR ? (tonicPc(k) + 3) % 12 : tonicPc(k);
        int f = Math.floorMod(majorPc * 7, 12);           // steps round the circle of fifths
        if (f > 6) f -= 12;
        if (f == 6 && k.accidental() == Accidental.FLAT) f = -6;
        return f;
    }

    /** Note name with octave (C4 = 60), spelled with flats in flat keys and sharps otherwise. */
    public static String noteName(int midi, KeySignature key) {
        String[] names = key != null && fifths(key) < 0 ? FLAT_SPELLING : SHARP_SPELLING;
        return names[Math.floorMod(midi, 12)] + (Math.floorDiv(midi, 12) - 1);
    }
}
