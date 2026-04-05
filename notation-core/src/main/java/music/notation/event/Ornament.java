package music.notation.event;

public enum Ornament {
    TRILL,           // rapid alternation with note above
    MORDENT,         // quick: note, note above, note
    LOWER_MORDENT,   // quick: note, note below, note
    TURN,            // note above, note, note below, note
    TREMOLO,         // rapid repetition of the same note
    APPOGGIATURA,    // leaning note — takes time from the main note
    ACCIACCATURA     // crushed note — very short, before the beat
}
