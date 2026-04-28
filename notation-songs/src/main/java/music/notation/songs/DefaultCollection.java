package music.notation.songs;

import music.notation.songs.anthem.internationale.Internationale;
import music.notation.songs.anthem.internationale.ManualInternationale;
import music.notation.songs.classical.bachinvention.BachInvention13;
import music.notation.songs.classical.bachinvention.ManualBachInvention13;
import music.notation.songs.classical.furelise.FurElise;
import music.notation.songs.classical.furelise.ManualFurElise;
import music.notation.songs.folk.tianheihei.PianoTianHeiHei;
import music.notation.songs.folk.tianheihei.TianHeiHei;
import music.notation.structure.Collection;

import java.util.List;

/**
 * The built-in song collection shipped with the application.
 *
 * <p>Phase 4c.3 trimmed this to the four manually-authored songs that
 * survive the Phrase cutover. The other 24 song providers have been
 * deleted and will be regenerated later (out of scope for Phase 4).</p>
 */
public final class DefaultCollection implements Collection {

    @Override
    public String name() {
        return "Built-in Songs";
    }

    @Override
    public List<Entry<?>> entries() {
        return List.of(
                Entry.of(new BachInvention13(),  new ManualBachInvention13()),
                Entry.of(new Internationale(),   new ManualInternationale()),
                Entry.of(new TianHeiHei(),       new PianoTianHeiHei()),
                Entry.of(new FurElise(),         new ManualFurElise())
        );
    }
}
