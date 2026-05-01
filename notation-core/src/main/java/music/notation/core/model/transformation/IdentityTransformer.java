package music.notation.core.model.transformation;

import music.notation.core.model.AbstractNote;

public record IdentityTransformer<T extends AbstractNote>() implements Transformer<T,T> {
    @Override
    public T forward(T t) {
        return t;
    }

    @Override
    public T reverse(T t) {
        return t;
    }
}
