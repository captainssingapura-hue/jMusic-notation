package music.notation.songs;

import music.notation.songs.anthem.internationale.GrungeInternationale;
import music.notation.songs.anthem.internationale.Internationale;
import music.notation.songs.anthem.internationale.ManualInternationale;
import music.notation.songs.classical.bachinvention.BachInvention13;
import music.notation.songs.classical.bachinvention.ManualBachInvention13;
import music.notation.songs.classical.furelise.FurElise;
import music.notation.songs.classical.furelise.ManualFurElise;
import music.notation.songs.classical.furelise.SoulTechnoFurElise;
import music.notation.songs.classical.traumerei.DefaultTraumerei;
import music.notation.songs.classical.traumerei.Traumerei;
import music.notation.songs.folk.tianheihei.PianoTianHeiHei;
import music.notation.songs.folk.tianheihei.TianHeiHei;
import music.notation.songs.folk.tianheihei.U2RockTianHeiHei;
import music.notation.songs.folk.zainayaoyuan.BluesZaiNaYaoYuan;
import music.notation.songs.folk.zainayaoyuan.PianoZaiNaYaoYuan;
import music.notation.songs.folk.zainayaoyuan.ZaiNaYaoYuan;
import music.notation.songs.nursery.xiaohongmao.XiaoHongMao;
import music.notation.songs.nursery.xiaohongmao.XuWeiXiaoHongMao;
import music.notation.structure.Collection;

import java.util.List;

/**
 * The built-in song collection shipped with the application.
 *
 * <p>Phase 4c.3 trimmed this to the four manually-authored songs that
 * survive the AuthorPhrase cutover. The other 24 song providers have been
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
                Entry.of(new Internationale(),   new ManualInternationale(), new GrungeInternationale()),
                Entry.of(new TianHeiHei(),       new PianoTianHeiHei(), new U2RockTianHeiHei()),
                Entry.of(new FurElise(),         new ManualFurElise(), new SoulTechnoFurElise()),
                Entry.of(new Traumerei(),        new DefaultTraumerei()),
                Entry.of(new XiaoHongMao(),      new XuWeiXiaoHongMao()),
                Entry.of(new ZaiNaYaoYuan(),     new PianoZaiNaYaoYuan(), new BluesZaiNaYaoYuan())
        );
    }
}
