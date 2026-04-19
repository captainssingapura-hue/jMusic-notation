package music.notation.songs.nursery.twotigers;

import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.StaffPhraseBuilder;
import music.notation.structure.TimeSignature;

import static music.notation.duration.BaseValue.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * The 4 atomic motifs of Two Tigers (两只老虎).
 *
 * <p>Shared by every arrangement so the musical material is
 * defined exactly once.</p>
 */
final class TwoTigersMotifs {

    private TwoTigersMotifs() {}

    private static final TimeSignature TS = new TimeSignature(4, 4);

    /** Fresh single-use builder. Each motif constructs and consumes its own. */
    private static StaffPhraseBuilder builder() {
        return StaffPhraseBuilder.in(TS);
    }

    /** 两只老虎 — C D E C */
    static final MelodicPhrase MOTIF_A = builder()
            .bar().mf().o5(QUARTER, C).o5(QUARTER, D).o5(QUARTER, E).o5(QUARTER, C)
            .build(attacca());

    /** 跑得快 — E F G – */
    static final MelodicPhrase MOTIF_B = builder()
            .bar().o5(QUARTER, E).o5(QUARTER, F).o5(HALF, G)
            .build(attacca());

    /** 一只没有眼睛 — G̲A̲ G̲F̲ E C */
    static final MelodicPhrase MOTIF_C = builder()
            .bar().o5(G).o5(A).o5(G).o5(F).o5(QUARTER, E).o5(QUARTER, C)
            .build(attacca());

    /** 真奇怪 — C G₄ C – */
    static final MelodicPhrase MOTIF_D = builder()
            .bar().o5(QUARTER, C).o4(QUARTER, G).o5(HALF, C)
            .build(attacca());

    /** Same as {@link #MOTIF_D} but with a breath marking. */
    static final MelodicPhrase MOTIF_D_BREATH = builder()
            .bar().o5(QUARTER, C).o4(QUARTER, G).o5(HALF, C)
            .build(breath());

    /** Same as {@link #MOTIF_D} but with a final ending. */
    static final MelodicPhrase MOTIF_D_END = builder()
            .bar().o5(QUARTER, C).o4(QUARTER, G).o5(HALF, C)
            .build(end());
}
