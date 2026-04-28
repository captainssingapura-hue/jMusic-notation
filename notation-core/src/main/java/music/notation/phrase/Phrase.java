package music.notation.phrase;

public sealed interface Phrase permits MelodicPhrase, RestPhrase, ChordPhrase, LayeredPhrase {

    PhraseMarking marking();
}
