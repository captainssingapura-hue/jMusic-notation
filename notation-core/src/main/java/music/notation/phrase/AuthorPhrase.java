package music.notation.phrase;

public sealed interface AuthorPhrase permits MelodicPhrase, RestPhrase, ChordPhrase, LayeredPhrase {

    PhraseMarking marking();
}
