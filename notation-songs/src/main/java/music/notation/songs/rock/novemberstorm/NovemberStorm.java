package music.notation.songs.rock.novemberstorm;

import music.notation.structure.MusicalPiece;

/**
 * 十一月四日风雨大作（其二）— 陆游 (1192)
 *
 * <p>僵卧孤村不自哀，尚思为国戍轮台。<br>
 * 夜阑卧听风吹雨，铁马冰河入梦来。</p>
 */
public record NovemberStorm() implements MusicalPiece {
    @Override public String title()    { return "十一月四日风雨大作"; }
    @Override public String composer() { return "陆游 (Lu You)"; }
}
