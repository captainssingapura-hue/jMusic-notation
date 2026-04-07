package music.notation.songs;

import music.notation.structure.Collection;

import java.util.List;

/**
 * The built-in song collection shipped with the application.
 */
public final class DefaultCollection implements Collection {

    @Override
    public String name() {
        return "Built-in Songs";
    }

    @Override
    public List<Entry<?>> entries() {
        return List.of(
                Entry.of(new TwinkleStar(), new DefaultTwinkleStar()),
                Entry.of(new OdeToJoy(), new DefaultOdeToJoy()),
                Entry.of(new MaryHadALittleLamb(), new DefaultMaryHadALittleLamb()),
                Entry.of(new AntsGoMarching(), new DefaultAntsGoMarching()),
                Entry.of(new PachelbelCanon(), new DefaultPachelbelCanon()),
                Entry.of(new BlueLotus(), new DefaultBlueLotus()),
                Entry.of(new TwoTigers(), new DefaultTwoTigers()),
                Entry.of(new TwoTigersCanon(), new DefaultTwoTigersCanon()),
                Entry.of(new TheRock(), new DefaultTheRock()),
                Entry.of(new BachInvention13(), new DefaultBachInvention13()),
                Entry.of(new Internationale(), new ManualInternationale())
        );
    }
}
