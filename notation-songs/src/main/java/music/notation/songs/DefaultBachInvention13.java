package music.notation.songs;

import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

final class DefaultBachInvention13 implements PieceContentProvider<BachInvention13> {

    private static final RestNode R_WHOLE = new RestNode(Duration.of(WHOLE));

    @Override
    public Piece create() {
        final var id = new BachInvention13();

        // ── RIGHT HAND ──────────────────────────────────────────────

        // mm. 1–4: Exposition
        final var rh1 = List.<PhraseNode>of(
                // m1: Subject in A minor
                n(A,4,SIXTEENTH), n(B,4,SIXTEENTH), n(C,5,SIXTEENTH), n(D,5,SIXTEENTH),
                n(E,5,SIXTEENTH), n(F,5,SIXTEENTH), n(E,5,SIXTEENTH), n(D,5,SIXTEENTH),
                n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH), ns(G,4,SIXTEENTH),
                n(A,4,EIGHTH), n(E,5,EIGHTH),
                // m2: Counter-melody (8ths) over LH answer
                n(C,5,EIGHTH), n(D,5,EIGHTH), n(E,5,EIGHTH), n(C,5,EIGHTH),
                n(D,5,EIGHTH), n(B,4,EIGHTH), n(C,5,EIGHTH), n(A,4,EIGHTH),
                // m3: Free counterpoint
                ns(G,4,SIXTEENTH), n(A,4,SIXTEENTH), n(B,4,SIXTEENTH), n(C,5,SIXTEENTH),
                n(D,5,EIGHTH), n(E,5,EIGHTH),
                n(D,5,SIXTEENTH), n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH),
                ns(G,4,EIGHTH), n(A,4,EIGHTH),
                // m4: Cadence to C major
                n(B,4,SIXTEENTH), n(C,5,SIXTEENTH), n(D,5,SIXTEENTH), n(E,5,SIXTEENTH),
                n(F,5,SIXTEENTH), n(E,5,SIXTEENTH), n(D,5,SIXTEENTH), n(C,5,SIXTEENTH),
                n(D,5,EIGHTH), n(B,4,EIGHTH),
                n(C,5,QUARTER));

        // mm. 5–8: Episode 1 + middle entry
        final var rh2 = List.<PhraseNode>of(
                // m5: Descending sequence in C major (16ths)
                n(E,5,SIXTEENTH), n(D,5,SIXTEENTH), n(C,5,SIXTEENTH), n(B,4,SIXTEENTH),
                n(C,5,SIXTEENTH), n(D,5,SIXTEENTH), n(E,5,SIXTEENTH), n(C,5,SIXTEENTH),
                n(D,5,SIXTEENTH), n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH),
                n(B,4,SIXTEENTH), n(C,5,SIXTEENTH), n(D,5,SIXTEENTH), n(B,4,SIXTEENTH),
                // m6: Sequence continues
                n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH), n(G,4,SIXTEENTH),
                n(A,4,SIXTEENTH), n(B,4,SIXTEENTH), n(C,5,SIXTEENTH), n(A,4,SIXTEENTH),
                n(B,4,EIGHTH), n(C,5,EIGHTH),
                n(D,5,EIGHTH), n(G,4,EIGHTH),
                // m7: Counter-melody (8ths) over LH subject in C
                n(E,5,EIGHTH), n(F,5,EIGHTH), n(G,5,EIGHTH), n(E,5,EIGHTH),
                n(F,5,EIGHTH), n(D,5,EIGHTH), n(E,5,EIGHTH), n(C,5,EIGHTH),
                // m8: Subject in G major
                n(G,4,SIXTEENTH), n(A,4,SIXTEENTH), n(B,4,SIXTEENTH), n(C,5,SIXTEENTH),
                n(D,5,SIXTEENTH), n(E,5,SIXTEENTH), n(D,5,SIXTEENTH), n(C,5,SIXTEENTH),
                n(B,4,SIXTEENTH), n(A,4,SIXTEENTH), n(G,4,SIXTEENTH), ns(F,4,SIXTEENTH),
                n(G,4,EIGHTH), n(D,5,EIGHTH));

        // mm. 9–12: Episode 2 — modulating back to A minor
        final var rh3 = List.<PhraseNode>of(
                // m9
                n(B,4,EIGHTH), n(C,5,EIGHTH),
                n(D,5,SIXTEENTH), n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH),
                n(B,4,EIGHTH), n(A,4,EIGHTH),
                ns(G,4,SIXTEENTH), n(A,4,SIXTEENTH), n(B,4,SIXTEENTH), ns(G,4,SIXTEENTH),
                // m10
                n(A,4,SIXTEENTH), n(B,4,SIXTEENTH), n(C,5,SIXTEENTH), n(D,5,SIXTEENTH),
                n(E,5,EIGHTH), n(D,5,EIGHTH),
                n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH), ns(G,4,SIXTEENTH),
                n(A,4,EIGHTH), n(B,4,EIGHTH),
                // m11: Sequence (16ths)
                n(C,5,SIXTEENTH), n(D,5,SIXTEENTH), n(E,5,SIXTEENTH), n(C,5,SIXTEENTH),
                n(D,5,SIXTEENTH), n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH),
                n(B,4,SIXTEENTH), n(C,5,SIXTEENTH), n(D,5,SIXTEENTH), n(B,4,SIXTEENTH),
                n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH), ns(G,4,SIXTEENTH),
                // m12: Building to recapitulation
                n(A,4,EIGHTH), n(B,4,EIGHTH),
                n(C,5,EIGHTH), n(D,5,EIGHTH),
                n(E,5,SIXTEENTH), n(F,5,SIXTEENTH), n(E,5,SIXTEENTH), n(D,5,SIXTEENTH),
                n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH), n(B,4,SIXTEENTH));

        // mm. 13–16: Recapitulation + coda
        final var rh4 = List.<PhraseNode>of(
                // m13: Subject returns in A minor
                n(A,4,SIXTEENTH), n(B,4,SIXTEENTH), n(C,5,SIXTEENTH), n(D,5,SIXTEENTH),
                n(E,5,SIXTEENTH), n(F,5,SIXTEENTH), n(E,5,SIXTEENTH), n(D,5,SIXTEENTH),
                n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH), ns(G,4,SIXTEENTH),
                n(A,4,EIGHTH), n(E,5,EIGHTH),
                // m14: Counter-melody
                n(C,5,EIGHTH), n(B,4,EIGHTH),
                n(A,4,EIGHTH), n(C,5,EIGHTH),
                n(B,4,SIXTEENTH), n(A,4,SIXTEENTH), ns(G,4,SIXTEENTH), n(A,4,SIXTEENTH),
                n(B,4,EIGHTH), n(E,5,EIGHTH),
                // m15: Cadential preparation
                n(F,5,SIXTEENTH), n(E,5,SIXTEENTH), n(D,5,SIXTEENTH), n(C,5,SIXTEENTH),
                n(D,5,SIXTEENTH), n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH),
                ns(G,4,SIXTEENTH), n(A,4,SIXTEENTH), n(B,4,SIXTEENTH), n(C,5,SIXTEENTH),
                n(D,5,EIGHTH), n(E,5,EIGHTH),
                // m16: Final cadence (V–i)
                n(B,4,SIXTEENTH), n(C,5,SIXTEENTH), n(B,4,SIXTEENTH), n(A,4,SIXTEENTH),
                ns(G,4,EIGHTH), n(B,4,EIGHTH),
                n(A,4,HALF));

        // ── LEFT HAND ───────────────────────────────────────────────

        // mm. 1–4
        final var lh1 = List.<PhraseNode>of(
                // m1: Tacet
                R_WHOLE,
                // m2: Answer at the 5th (E minor)
                n(E,3,SIXTEENTH), ns(F,3,SIXTEENTH), n(G,3,SIXTEENTH), n(A,3,SIXTEENTH),
                n(B,3,SIXTEENTH), n(C,4,SIXTEENTH), n(B,3,SIXTEENTH), n(A,3,SIXTEENTH),
                n(G,3,SIXTEENTH), ns(F,3,SIXTEENTH), n(E,3,SIXTEENTH), ns(D,3,SIXTEENTH),
                n(E,3,EIGHTH), n(B,3,EIGHTH),
                // m3: Continuation
                n(C,4,EIGHTH), n(D,4,EIGHTH),
                n(E,4,SIXTEENTH), n(F,4,SIXTEENTH), n(E,4,SIXTEENTH), n(D,4,SIXTEENTH),
                n(C,4,EIGHTH), n(A,3,EIGHTH),
                n(B,3,EIGHTH), n(E,3,EIGHTH),
                // m4
                n(A,3,EIGHTH), n(G,3,EIGHTH),
                n(F,3,EIGHTH), n(D,3,EIGHTH),
                n(G,3,SIXTEENTH), n(A,3,SIXTEENTH), n(B,3,SIXTEENTH), n(G,3,SIXTEENTH),
                n(C,3,QUARTER));

        // mm. 5–8
        final var lh2 = List.<PhraseNode>of(
                // m5: 8th-note accompaniment
                n(C,3,EIGHTH), n(D,3,EIGHTH), n(E,3,EIGHTH), n(F,3,EIGHTH),
                n(G,3,EIGHTH), n(A,3,EIGHTH), n(G,3,EIGHTH), n(F,3,EIGHTH),
                // m6
                n(E,3,EIGHTH), n(D,3,EIGHTH),
                n(C,3,EIGHTH), n(D,3,EIGHTH),
                n(G,3,SIXTEENTH), n(A,3,SIXTEENTH), n(B,3,SIXTEENTH), n(C,4,SIXTEENTH),
                n(B,3,SIXTEENTH), n(A,3,SIXTEENTH), n(G,3,SIXTEENTH), ns(F,3,SIXTEENTH),
                // m7: Subject in C major
                n(C,3,SIXTEENTH), n(D,3,SIXTEENTH), n(E,3,SIXTEENTH), n(F,3,SIXTEENTH),
                n(G,3,SIXTEENTH), n(A,3,SIXTEENTH), n(G,3,SIXTEENTH), n(F,3,SIXTEENTH),
                n(E,3,SIXTEENTH), n(D,3,SIXTEENTH), n(C,3,SIXTEENTH), n(B,2,SIXTEENTH),
                n(C,3,EIGHTH), n(G,3,EIGHTH),
                // m8
                n(E,3,EIGHTH), n(F,3,EIGHTH), n(G,3,EIGHTH), n(E,3,EIGHTH),
                n(D,3,EIGHTH), n(C,3,EIGHTH), n(B,2,EIGHTH), n(G,2,EIGHTH));

        // mm. 9–12
        final var lh3 = List.<PhraseNode>of(
                // m9
                n(G,3,SIXTEENTH), ns(F,3,SIXTEENTH), n(E,3,SIXTEENTH), n(D,3,SIXTEENTH),
                n(E,3,EIGHTH), n(F,3,EIGHTH),
                n(G,3,SIXTEENTH), n(F,3,SIXTEENTH), n(E,3,SIXTEENTH), n(D,3,SIXTEENTH),
                n(E,3,EIGHTH), n(E,2,EIGHTH),
                // m10
                n(A,3,EIGHTH), n(G,3,EIGHTH),
                n(F,3,SIXTEENTH), n(E,3,SIXTEENTH), n(D,3,SIXTEENTH), n(C,3,SIXTEENTH),
                n(D,3,EIGHTH), n(E,3,EIGHTH),
                n(F,3,EIGHTH), n(D,3,EIGHTH),
                // m11
                n(A,3,EIGHTH), n(A,3,EIGHTH), n(F,3,EIGHTH), n(D,3,EIGHTH),
                n(G,3,EIGHTH), n(G,3,EIGHTH), n(E,3,EIGHTH), n(E,2,EIGHTH),
                // m12
                n(F,3,SIXTEENTH), n(E,3,SIXTEENTH), n(D,3,SIXTEENTH), n(C,3,SIXTEENTH),
                n(A,2,SIXTEENTH), n(B,2,SIXTEENTH), n(C,3,SIXTEENTH), n(D,3,SIXTEENTH),
                n(E,3,EIGHTH), n(D,3,EIGHTH),
                n(E,3,EIGHTH), n(E,2,EIGHTH));

        // mm. 13–16
        final var lh4 = List.<PhraseNode>of(
                // m13
                n(A,3,EIGHTH), n(G,3,EIGHTH),
                n(F,3,EIGHTH), n(D,3,EIGHTH),
                n(E,3,SIXTEENTH), n(F,3,SIXTEENTH), n(E,3,SIXTEENTH), n(D,3,SIXTEENTH),
                n(C,3,EIGHTH), n(A,2,EIGHTH),
                // m14: Answer returns in A minor
                n(A,2,SIXTEENTH), n(B,2,SIXTEENTH), n(C,3,SIXTEENTH), n(D,3,SIXTEENTH),
                n(E,3,SIXTEENTH), n(F,3,SIXTEENTH), n(E,3,SIXTEENTH), n(D,3,SIXTEENTH),
                n(C,3,SIXTEENTH), n(B,2,SIXTEENTH), n(A,2,SIXTEENTH), ns(G,2,SIXTEENTH),
                n(A,2,EIGHTH), n(E,3,EIGHTH),
                // m15
                n(D,3,EIGHTH), n(E,3,EIGHTH), n(F,3,EIGHTH), n(D,3,EIGHTH),
                n(E,3,EIGHTH), n(C,3,EIGHTH), n(D,3,EIGHTH), n(E,3,EIGHTH),
                // m16: Final cadence (V–i)
                n(D,3,EIGHTH), n(E,3,EIGHTH),
                n(E,2,QUARTER),
                n(A,2,HALF));

        // ── Assemble tracks ─────────────────────────────────────────

        final var rightHand = new Track("Right Hand", ACOUSTIC_GRAND_PIANO, List.of(
                mp(Dynamic.MF, rh1, attacca()),
                new MelodicPhrase(rh2, attacca()),
                mp(Dynamic.MP, rh3, attacca()),
                mp(Dynamic.MF, rh4, end())));

        final var leftHand = new Track("Left Hand", ACOUSTIC_GRAND_PIANO, List.of(
                mp(Dynamic.MF, lh1, attacca()),
                new MelodicPhrase(lh2, attacca()),
                mp(Dynamic.MP, lh3, attacca()),
                mp(Dynamic.MF, lh4, end())));

        return new Piece(id.title(), id.composer(),
                new KeySignature(A, Mode.MINOR), new TimeSignature(4, 4),
                new Tempo(112, QUARTER), List.of(rightHand, leftHand));
    }

    /** Create a MelodicPhrase with a dynamic prepended. */
    private static MelodicPhrase mp(Dynamic dyn, List<PhraseNode> notes, PhraseMarking marking) {
        final var nodes = new ArrayList<PhraseNode>();
        nodes.add(new DynamicNode(dyn));
        nodes.addAll(notes);
        return new MelodicPhrase(nodes, marking);
    }
}
