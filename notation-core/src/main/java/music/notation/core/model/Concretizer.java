package music.notation.core.model;

/**
 * Making an abstract note concrete
 * @param <A>
 * @param <C>
 */
public interface Concretizer<A extends AbstractNote, C extends ConcreteNote> {
    C concretize(A a);
}
