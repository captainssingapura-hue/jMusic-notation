package music.notation.core.model.transformation;

import music.notation.core.model.AbstractNote;

public record ChainedTransformer<A extends AbstractNote, B extends AbstractNote, C extends AbstractNote>(Transformer<A,B> fst, Transformer<B,C> snd) implements Transformer<A,C>{

    @Override
    public C forward(A a) {
        return snd.forward(fst.forward(a));
    }

    @Override
    public A reverse(C c) {
        return fst.reverse(snd.reverse(c));
    }
}
