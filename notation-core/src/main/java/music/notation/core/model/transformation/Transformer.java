package music.notation.core.model.transformation;

import music.notation.core.model.AbstractNote;

/**
 * Functional Object
 */
public interface Transformer<A extends AbstractNote, B extends AbstractNote> {
    B forward(A a);
    A reverse(B b);

    default <C extends AbstractNote> Transformer<A,C> andThen(Transformer<B,C> next){
        return new ChainedTransformer<>(this, next);
    }

    static <T extends AbstractNote> Transformer<T, T> identity() {
        return new IdentityTransformer<>();
    }
}
