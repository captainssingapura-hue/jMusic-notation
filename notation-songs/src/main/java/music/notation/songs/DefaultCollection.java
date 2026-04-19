package music.notation.songs;

import music.notation.songs.anthem.internationale.Internationale;
import music.notation.songs.anthem.internationale.ManualInternationale;
import music.notation.songs.anthem.internationale.RockInternationale;
import music.notation.songs.game.contra.ContraBase;
import music.notation.songs.game.contra.DefaultContraBase;
import music.notation.songs.folk.katyusha.DefaultKatyusha;
import music.notation.songs.folk.katyusha.Katyusha;
import music.notation.songs.folk.katyusha.RockKatyusha;
import music.notation.songs.folk.tianheihei.PianoTianHeiHei;
import music.notation.songs.folk.tianheihei.U2RockTianHeiHei;
import music.notation.songs.folk.tianheihei.TianHeiHei;
import music.notation.songs.classical.furelise.FurElise;
import music.notation.songs.classical.furelise.ManualFurElise;
import music.notation.songs.classical.furelise.SoulTechnoFurElise;
import music.notation.songs.classical.bachinvention.BachInvention13;
import music.notation.songs.classical.bachinvention.ColdplayBachInvention13;
import music.notation.songs.classical.bachinvention.ManualBachInvention13;
import music.notation.songs.classical.odetojoy.DefaultOdeToJoy;
import music.notation.songs.classical.odetojoy.OdeToJoy;
import music.notation.songs.classical.pachelbelcanon.DefaultPachelbelCanon;
import music.notation.songs.classical.pachelbelcanon.PachelbelCanon;
import music.notation.songs.classical.traumerei.DefaultTraumerei;
import music.notation.songs.classical.traumerei.Traumerei;
import music.notation.songs.nursery.antsmarching.AntsGoMarching;
import music.notation.songs.nursery.antsmarching.DefaultAntsGoMarching;
import music.notation.songs.nursery.marylamb.DefaultMaryHadALittleLamb;
import music.notation.songs.nursery.marylamb.MaryHadALittleLamb;
import music.notation.songs.nursery.twinklestar.DefaultTwinkleStar;
import music.notation.songs.nursery.twinklestar.TwinkleStar;
import music.notation.songs.nursery.twotigers.DefaultTwoTigers;
import music.notation.songs.nursery.twotigers.DefaultTwoTigersCanon;
import music.notation.songs.nursery.twotigers.RockTwoTigers;
import music.notation.songs.nursery.twotigers.TwoTigers;
import music.notation.songs.rock.bluelotus.BlueLotus;
import music.notation.songs.rock.bluelotus.DefaultBlueLotus;
import music.notation.songs.rock.novemberstorm.DefaultNovemberStorm;
import music.notation.songs.rock.novemberstorm.NovemberStorm;
import music.notation.songs.rock.therock.DefaultTheRock;
import music.notation.songs.rock.therock.TheRock;
import music.notation.songs.traditional.happybirthday.BeethovenHappyBirthday;
import music.notation.songs.traditional.happybirthday.BrahmsHappyBirthday;
import music.notation.songs.traditional.happybirthday.ChopinHappyBirthday;
import music.notation.songs.traditional.happybirthday.CombinedHappyBirthday;
import music.notation.songs.traditional.happybirthday.DefaultHappyBirthday;
import music.notation.songs.traditional.happybirthday.HappyBirthday;
import music.notation.songs.traditional.happybirthday.MozartHappyBirthday;
import music.notation.songs.traditional.happybirthday.SoulTechnoCombinedHappyBirthday;
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
                Entry.of(new TwoTigers(), new DefaultTwoTigers(), new DefaultTwoTigersCanon(), new RockTwoTigers()),
                Entry.of(new TheRock(), new DefaultTheRock()),
                Entry.of(new BachInvention13(), new ManualBachInvention13(), new ColdplayBachInvention13()),
                Entry.of(new NovemberStorm(), new DefaultNovemberStorm()),
                Entry.of(new Internationale(), new ManualInternationale(), new RockInternationale()),
                Entry.of(new Traumerei(), new DefaultTraumerei()),
                Entry.of(new Katyusha(), new DefaultKatyusha(), new RockKatyusha()),
                Entry.of(new TianHeiHei(), new PianoTianHeiHei(), new U2RockTianHeiHei()),
                Entry.of(new FurElise(), new ManualFurElise(), new SoulTechnoFurElise()),
                Entry.of(new ContraBase(), new DefaultContraBase()),
                Entry.of(new HappyBirthday(), new DefaultHappyBirthday(), new MozartHappyBirthday(),
                        new ChopinHappyBirthday(), new BeethovenHappyBirthday(), new BrahmsHappyBirthday(),
                        new CombinedHappyBirthday(), new SoulTechnoCombinedHappyBirthday())
        );
    }
}
