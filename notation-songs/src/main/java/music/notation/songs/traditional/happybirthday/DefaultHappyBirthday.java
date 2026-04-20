package music.notation.songs.traditional.happybirthday;

import music.notation.chord.DominantSeventh;
import music.notation.chord.MajorTriad;
import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * "Happy Birthday to You" — default arrangement.
 *
 * <p>Simple two-hand piano: melody on the right hand, dotted-half chord stabs
 * on the left hand following the standard I–V7–I progression. C major, 3/4,
 * 112 BPM (celebratory but not rushed).</p>
 */
public final class DefaultHappyBirthday implements PieceContentProvider<HappyBirthday> {

    static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    static final TimeSignature TS = new TimeSignature(3, 4);

    private StaffPhraseBuilderTyped b() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Override
    public Piece create() {
        final var id = new HappyBirthday();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(112, QUARTER),
                List.of(melody(), leftHand()));
    }

    // ── Melody (Right Hand) ───────────────────────────────────────────

    private Track melody() {
        return Track.of("Melody", ACOUSTIC_GRAND_PIANO, List.of(
                line1(), line2(), line3(), line4()));
    }

    /** "Happy birthday to you" — first phrase, ends on dominant V7 feel (B4). */
    MelodicPhrase line1() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G).done()               // "Hap-py"
                .bar(QUARTER).o4(A).o4(G).o5(C).done()           // "birth-day to"
                .bar().o4(HALF, B).pad(QUARTER).done()               // "you" (HALF + trail 16 for elision)
                .build(elision());
    }

    /** "Happy birthday to you" — second phrase, resolves higher to C5. */
    MelodicPhrase line2() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G).done()               // "Hap-py"
                .bar(QUARTER).o4(A).o4(G).o5(D).done()           // "birth-day to"
                .bar().o5(HALF, C).pad(QUARTER).done()               // "you" (C5 HALF + trail 16)
                .build(elision());
    }

    /** "Happy birthday dear [NAME]" — octave leap up for emphasis. */
    MelodicPhrase line3() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G).done()               // "Hap-py"
                .bar(QUARTER).o5(G).o5(E).o5(C).done()           // "birth-day dear"  (high G, E, C)
                .bar().o4(HALF, B).pad(QUARTER).done()               // "NAME" held, trail 16 for elision
                .build(elision());
    }

    /** "Happy birthday to you" — final phrase, F5 pickup for the classic dramatic leap. */
    MelodicPhrase line4() {
        return b()
                .pickup(EIGHTH).o5(F).o5(F).done()               // "Hap-py" — dramatic F5 leap
                .bar(QUARTER).o5(E).o5(C).o5(D).done()           // "birth-day to"
                .bar().o5(HALF.dot(), C).done()                  // "you" — dotted half, full resolution
                .build(end());
    }

    // ── Left Hand: dotted-half chord stabs, I-V7-I ─────────────────────

    private Track leftHand() {
        final var CMaj = new MajorTriad(C, 3);
        final var FMaj = new MajorTriad(F, 3);
        final var G7   = new DominantSeventh(G, 3);

        // 12 structural bars (4 phrases × 3 bars each including pickup bar).
        // Progression loosely follows the standard Happy Birthday harmony:
        //   line 1:  C  C  G7     → tension on the final beat of line 1
        //   line 2:  G7 C  C      → resolution
        //   line 3:  C  C  F      → subdominant colour for "dear NAME"
        //   line 4:  F  C  C      → F pulls to C, final I
        return Track.of("Accompaniment", ACOUSTIC_GRAND_PIANO, List.of(
                new ChordPhrase(List.of(
                        dchord(HALF, CMaj),      dchord(HALF, CMaj),     dchord(HALF, G7),
                        dchord(HALF, G7),        dchord(HALF, CMaj),     dchord(HALF, CMaj),
                        dchord(HALF, CMaj),      dchord(HALF, CMaj),     dchord(HALF, FMaj),
                        dchord(HALF, FMaj),      dchord(HALF, CMaj),     dchord(HALF, CMaj)
                ), end())
        ));
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new DefaultHappyBirthday());
    }
}
