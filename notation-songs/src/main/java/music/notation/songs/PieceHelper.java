package music.notation.songs;

import music.notation.chord.Chord;
import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.ChordEvent;
import music.notation.event.Ornament;
import music.notation.event.PercussionSound;
import music.notation.phrase.*;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;

import java.util.List;

import static music.notation.phrase.PhraseConnection.*;

public final class PieceHelper {

    private PieceHelper() {}

    public static NoteNode n(NoteName name, int oct, BaseValue dur) {
        return NoteNode.of(Pitch.of(name, oct), Duration.of(dur));
    }

    public static NoteNode orn(NoteName name, int oct, BaseValue dur, Ornament o) {
        return NoteNode.ornamented(Pitch.of(name, oct), Duration.of(dur), o);
    }

    public static NoteNode nd(NoteName name, int oct, BaseValue dur) {
        return NoteNode.of(Pitch.of(name, oct), Duration.dotted(dur));
    }

    public static ChordEvent chord(BaseValue dur, Pitch... pitches) {
        return new ChordEvent(List.of(pitches), Duration.of(dur), List.of());
    }

    public static ChordEvent chord(BaseValue dur, Chord chord) {
        return new ChordEvent(chord.pitches(), Duration.of(dur), List.of());
    }

    public static ChordEvent dchord(BaseValue dur, Pitch... pitches) {
        return new ChordEvent(List.of(pitches), Duration.dotted(dur), List.of());
    }

    public static ChordEvent dchord(BaseValue dur, Chord chord) {
        return new ChordEvent(chord.pitches(), Duration.dotted(dur), List.of());
    }

    public static PercussionNote d(PercussionSound sound, BaseValue dur) {
        return new PercussionNote(sound, Duration.of(dur));
    }

    public static NoteNode ns(NoteName name, int oct, BaseValue dur) {
        return NoteNode.of(Pitch.of(name, Accidental.SHARP, oct), Duration.of(dur));
    }

    public static Pitch p(NoteName name, int oct) {
        return Pitch.of(name, oct);
    }

    public static Pitch ps(NoteName name, int oct) {
        return Pitch.of(name, Accidental.SHARP, oct);
    }

    public static PhraseMarking breath()  { return new PhraseMarking(BREATH, true); }
    public static PhraseMarking attacca() { return new PhraseMarking(ATTACCA, true); }
    public static PhraseMarking elision() { return new PhraseMarking(ELISION, true); }
    public static PhraseMarking end()     { return new PhraseMarking(CAESURA, true); }
}
