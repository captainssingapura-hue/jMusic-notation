package music.notation.phrase;

public sealed interface Phrase permits MelodicPhrase, RestPhrase, ChordPhrase, DrumPhrase, ShiftedPhrase {

    PhraseMarking marking();
}
