package music.notation.songs.folk.tianheihei;

import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * 天黑黑 (Tian Hei Hei) — Two-hand piano arrangement.
 * Right hand reuses PianoTianHeiHei melody; left hand is manual.
 */
public final class ManualTianHeiHei implements PieceContentProvider<TianHeiHei> {

    private final PianoTianHeiHei rh = new PianoTianHeiHei();

    private StaffPhraseBuilder newBuilder() {
        return StaffPhraseBuilder.in(PianoTianHeiHei.KEY, PianoTianHeiHei.TS, QUARTER);
    }

    @Override
    public Piece create() {
        final var id = new TianHeiHei();
        return new Piece(id.title(), id.composer(),
                PianoTianHeiHei.KEY, PianoTianHeiHei.TS,
                new Tempo(120, QUARTER),
                List.of(rightHand(), leftHand()));
    }

    private Track rightHand() {
        final var mainMelody1 = rh.buildMelodyMain1();
        final var tianHei1 = rh.buildMelodyTianHeiHei1();
        final var chorus1 = rh.buildMelodyMain2();
        final var ending = rh.buildEnding();
        var phrases = List.<Phrase>of(
                rh.buildMelodyPre(),
                mainMelody1, tianHei1, mainMelody1, tianHei1,
                chorus1, rh.buildBridge(), chorus1,
                rh.overrideMelodyMain2(),
                ending, tianHei1
        );
        return Track.of("Right Hand", ACOUSTIC_GRAND_PIANO, phrases);
    }

    private Track leftHand() {
        final var lhMain1 = buildLHMain1();
        final var lhTianHei = buildLHTianHei();
        final var lhChorus = buildLHChorus();
        final var lhEnding = buildLHEnding();
        var phrases = List.<Phrase>of(
                buildLHPre(),
                lhMain1, lhTianHei, lhMain1, lhTianHei,
                lhChorus, buildLHBridge(), lhChorus,
                overrideLHChorus(),
                lhEnding, lhTianHei
        );
        return Track.of("Left Hand", ACOUSTIC_GRAND_PIANO, phrases);
    }

    // ── Left hand stubs — fill in notes, keep bar counts and markings ──

    /** Pre: 4 bars, elision. */
    MelodicPhrase buildLHPre() {
        // TODO: fill in left hand
        return newBuilder()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .build(elision());
    }

    /** Verse: pickup(EIGHTH) + 8 bars, attacca. Pickup = 2 eighths. */
    MelodicPhrase buildLHMain1() {
        // TODO: fill in left hand
        return newBuilder()
                .pickup(EIGHTH).r().r()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .build(attacca());
    }

    /** 天黑黑 refrain: 6 bars, attacca. */
    MelodicPhrase buildLHTianHei() {
        // TODO: fill in left hand
        return newBuilder()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(HALF).ending()
                .build(elision());
    }

    /** Chorus: pickup(EIGHTH) + 8 bars, elision. Pickup = 3 eighths. */
    MelodicPhrase buildLHChorus() {
        // TODO: fill in left hand
        return newBuilder()
                .pickup(EIGHTH).r().r().r()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE).ending()
                .build(elision());
    }

    /** Bridge: pickup(EIGHTH) + 8 bars, elision. Pickup = 2 eighths. */
    MelodicPhrase buildLHBridge() {
        // TODO: fill in left hand
        return newBuilder()
                .pickup(EIGHTH).r().r()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE).ending()
                .build(elision());
    }

    /** Override chorus bar 8, elision. */
    Phrase overrideLHChorus() {
        return OverlayBuilder.over(buildLHChorus(), PianoTianHeiHei.KEY, PianoTianHeiHei.TS, QUARTER)
                .endingAt(8, b -> b.r(WHOLE))
                .build(elision());
    }

    /** Ending: pickup(EIGHTH) + 12 bars, end. Pickup = 2 eighths. */
    MelodicPhrase buildLHEnding() {
        // TODO: fill in left hand
        return newBuilder()
                .pickup(EIGHTH).r().r()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .build(end());
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ManualTianHeiHei());
    }
}
