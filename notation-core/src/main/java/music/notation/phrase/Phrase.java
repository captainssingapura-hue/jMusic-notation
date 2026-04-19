package music.notation.phrase;

public sealed interface Phrase permits MelodicPhrase, RestPhrase, VoidPhrase, ChordPhrase, DrumPhrase, ShiftedPhrase, LyricPhrase, LayeredPhrase {

    PhraseMarking marking();
}
