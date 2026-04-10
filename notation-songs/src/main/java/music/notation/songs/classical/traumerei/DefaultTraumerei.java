package music.notation.songs.classical.traumerei;

import music.notation.phrase.*;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * Schumann — Träumerei (Dreaming), Op. 15 No. 7
 *
 * <p>From <em>Kinderszenen</em> (Scenes from Childhood), 1838.
 * F major, 4/4, ♩ ≈ 66. Structure: pickup + ||: A (bars 1–8) :|| B (9–16) | A' (17–24).
 * Four voices: Soprano + Alto (right hand), Tenor + Bass (left hand).
 * Transcribed from the Breitkopf &amp; Härtel edition (No. 39917).</p>
 */
public final class DefaultTraumerei implements PieceContentProvider<Traumerei> {

    private static final KeySignature KEY = new KeySignature(F, Mode.MAJOR);
    private static final TimeSignature TS  = new TimeSignature(4, 4);

    @Override
    public Piece create() {
        final var id = new Traumerei();

        // ── Build each section for RH (soprano + alto) ──────────────
        var rhPickup  = buildRhPickup();
        var rhA       = buildRhA();
        var rhB       = buildRhB();
        var rhAp      = buildRhAp();

        // ── Build each section for LH (bass + tenor) ────────────────
        var lhPickup  = buildLhPickup();
        var lhA       = buildLhA();
        var lhB       = buildLhB();
        var lhAp      = buildLhAp();

        // ── Assemble tracks: pickup, A, A (repeat), B, A' ───────────
        // Main voices
        var rhPhrases = List.<Phrase>of(rhPickup.main, rhA.main, rhA.main, rhB.main, rhAp.main);
        var lhPhrases = List.<Phrase>of(lhPickup.main, lhA.main, lhA.main, lhB.main, lhAp.main);

        // Aux voices (alto for RH, tenor for LH)
        var rhAuxPhrases = List.<Phrase>of(rhPickup.aux, rhA.aux, rhA.aux, rhB.aux, rhAp.aux);
        var lhAuxPhrases = List.<Phrase>of(lhPickup.aux, lhA.aux, lhA.aux, lhB.aux, lhAp.aux);

        var rhAuxTrack = Track.of("Alto", ACOUSTIC_GRAND_PIANO, rhAuxPhrases);
        var lhAuxTrack = Track.of("Tenor", ACOUSTIC_GRAND_PIANO, lhAuxPhrases);

        var rightHand = new Track("Right Hand", ACOUSTIC_GRAND_PIANO, rhPhrases, List.of(rhAuxTrack));
        var leftHand  = new Track("Left Hand", ACOUSTIC_GRAND_PIANO, lhPhrases, List.of(lhAuxTrack));

        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(66, QUARTER),
                List.of(rightHand, leftHand));
    }

    // ── Section record ──────────────────────────────────────────────

    /** A section's main phrase and its single aux voice phrase. */
    private record Section(MelodicPhrase main, MelodicPhrase aux) {}

    // ── RIGHT HAND: Soprano (main) + Alto (aux) ────────────────────

    private Section buildRhPickup() {
        var R = StaffPhraseBuilder.in(KEY, TS, EIGHTH);
        var main = R
                .pickup().p()
                    .o4(F)                          // pickup: eighth note F4
                .aux()
                    .r(WHOLE)                       // alto silent during pickup
                .build(attacca());
        return new Section(main, R.auxPhrases().getFirst());
    }

    private Section buildRhA() {
        var R = StaffPhraseBuilder.in(KEY, TS, EIGHTH);
        var main = R
                // Bar 1: rising arpeggio F4 → C5 → F5
                .bar()
                    .o4(A).o4(C.higher(1)).o4(EIGHTH.dot(), C.higher(1)).o5(SIXTEENTH, D)
                    .o5(QUARTER, F).o5(QUARTER, E)
                .aux()
                    .o4(QUARTER, F, A).o4(QUARTER, F, A)
                    .o4(QUARTER, A, C.higher(1)).o4(QUARTER, A, C.higher(1))
                // Bar 2: melody continues up
                .bar()
                    .o5(EIGHTH.dot(), F).o5(SIXTEENTH, A).o5(QUARTER, G)
                    .o5(QUARTER, F).o5(QUARTER, E)
                .aux()
                    .o4(QUARTER, A, C.higher(1)).o4(QUARTER, C.higher(1), E.higher(1))
                    .o4(QUARTER, C.higher(1), E.higher(1)).o4(QUARTER, A, C.higher(1))
                // Bar 3: descent
                .bar()
                    .o5(QUARTER, D).o5(EIGHTH.dot(), C).o4(SIXTEENTH, B.n())
                    .o4(QUARTER, A).o4(QUARTER, G)
                .aux()
                    .o4(QUARTER, F, B).o4(QUARTER, E, G)
                    .o4(QUARTER, C, F).o4(QUARTER, C, E)
                // Bar 4: resolution to F
                .bar()
                    .o4(HALF, F).o4(QUARTER, E)
                    .o4(EIGHTH, F).o4(EIGHTH, A)
                .aux()
                    .o4(HALF, C, A).o4(QUARTER, C)
                    .o3(QUARTER, A, C.higher(1))
                // Bar 5: = Bar 1 (second statement)
                .bar()
                    .o4(A).o4(C.higher(1)).o4(EIGHTH.dot(), C.higher(1)).o5(SIXTEENTH, D)
                    .o5(QUARTER, F).o5(QUARTER, E)
                .aux()
                    .o4(QUARTER, F, A).o4(QUARTER, F, A)
                    .o4(QUARTER, A, C.higher(1)).o4(QUARTER, A, C.higher(1))
                // Bar 6: higher peak (A5)
                .bar()
                    .o5(EIGHTH.dot(), F).o5(SIXTEENTH, A).o5(QUARTER, G)
                    .o5(QUARTER, F).o5(QUARTER, E)
                .aux()
                    .o4(QUARTER, A, C.higher(1)).o5(QUARTER, C, F)
                    .o5(QUARTER, C, E).o4(QUARTER, A, C.higher(1))
                // Bar 7: chromatic descent
                .bar()
                    .o5(QUARTER, E).o5(QUARTER, D)
                    .o5(EIGHTH, C).o4(EIGHTH, B.n()).o4(EIGHTH, A).o4(EIGHTH, G)
                .aux()
                    .o4(QUARTER, G, C.higher(1)).o4(QUARTER, F, B)
                    .o4(QUARTER, E, G).o4(QUARTER, C, E)
                // Bar 8: half cadence
                .bar()
                    .o4(HALF, A).o4(HALF, G)
                .aux()
                    .o4(HALF, C, F).o4(HALF, C, E)
                .build(attacca());
        return new Section(main, R.auxPhrases().getFirst());
    }

    private Section buildRhB() {
        var R = StaffPhraseBuilder.in(KEY, TS, EIGHTH);
        var main = R
                // Bar 9
                .bar()
                    .o4(QUARTER, A).o4(EIGHTH.dot(), B.n()).o4(SIXTEENTH, C.higher(1))
                    .o5(QUARTER, D).o5(QUARTER, C)
                .aux()
                    .o4(QUARTER, C, F).o4(QUARTER, D, F)
                    .o4(QUARTER, F, A).o4(QUARTER, E, G)
                // Bar 10
                .bar()
                    .o4(EIGHTH.dot(), B.n()).o4(SIXTEENTH, A)
                    .o4(QUARTER, G.s()).o4(HALF, A)
                .aux()
                    .o4(QUARTER, D, F).o4(QUARTER, D, E)
                    .o4(HALF, C, E)
                // Bar 11
                .bar()
                    .o4(QUARTER, A).o4(EIGHTH.dot(), B.n()).o4(SIXTEENTH, C.higher(1))
                    .o5(QUARTER, E).o5(QUARTER, D)
                .aux()
                    .o4(QUARTER, C, F).o4(QUARTER, D, F)
                    .o4(QUARTER, G, C.higher(1)).o4(QUARTER, F, B)
                // Bar 12
                .bar()
                    .o5(EIGHTH.dot(), C).o4(SIXTEENTH, B.n())
                    .o4(QUARTER, A).o4(HALF, G)
                .aux()
                    .o4(QUARTER, E, G).o4(QUARTER, C, F)
                    .o4(HALF, C, E)
                // Bar 13: pp espressivo
                .bar().pp()
                    .o5(QUARTER, C).o5(EIGHTH.dot(), D).o5(SIXTEENTH, E)
                    .o5(QUARTER, F).o5(QUARTER, E)
                .aux()
                    .o4(QUARTER, F, A).o4(QUARTER, F, B)
                    .o4(QUARTER, A, C.higher(1)).o4(QUARTER, G, C.higher(1))
                // Bar 14
                .bar()
                    .o5(EIGHTH.dot(), D).o5(SIXTEENTH, C)
                    .o4(QUARTER, B.n()).o4(HALF, A)
                .aux()
                    .o4(QUARTER, F, A).o4(QUARTER, D, G)
                    .o4(HALF, C, F)
                // Bar 15
                .bar().p()
                    .o5(QUARTER, C).o5(EIGHTH.dot(), D).o5(SIXTEENTH, E)
                    .o5(QUARTER, F).o5(QUARTER, A)
                .aux()
                    .o4(QUARTER, F, A).o4(QUARTER, F, B)
                    .o4(QUARTER, A, C.higher(1)).o4(QUARTER, C.higher(1), F.higher(1))
                // Bar 16: transition
                .bar()
                    .o5(QUARTER, G).o5(QUARTER, F)
                    .o5(QUARTER, E).o5(QUARTER, D)
                .aux()
                    .o4(QUARTER, B, E.higher(1)).o4(QUARTER, A, C.higher(1))
                    .o4(QUARTER, G, C.higher(1)).o4(QUARTER, F, B)
                .build(attacca());
        return new Section(main, R.auxPhrases().getFirst());
    }

    private Section buildRhAp() {
        var R = StaffPhraseBuilder.in(KEY, TS, EIGHTH);
        var main = R
                // Bar 17 = Bar 1
                .bar().p()
                    .o4(A).o4(C.higher(1)).o4(EIGHTH.dot(), C.higher(1)).o5(SIXTEENTH, D)
                    .o5(QUARTER, F).o5(QUARTER, E)
                .aux()
                    .o4(QUARTER, F, A).o4(QUARTER, F, A)
                    .o4(QUARTER, A, C.higher(1)).o4(QUARTER, A, C.higher(1))
                // Bar 18 = Bar 2
                .bar()
                    .o5(EIGHTH.dot(), F).o5(SIXTEENTH, A).o5(QUARTER, G)
                    .o5(QUARTER, F).o5(QUARTER, E)
                .aux()
                    .o4(QUARTER, A, C.higher(1)).o4(QUARTER, C.higher(1), E.higher(1))
                    .o4(QUARTER, C.higher(1), E.higher(1)).o4(QUARTER, A, C.higher(1))
                // Bar 19 = Bar 3
                .bar()
                    .o5(QUARTER, D).o5(EIGHTH.dot(), C).o4(SIXTEENTH, B.n())
                    .o4(QUARTER, A).o4(QUARTER, G)
                .aux()
                    .o4(QUARTER, F, B).o4(QUARTER, E, G)
                    .o4(QUARTER, C, F).o4(QUARTER, C, E)
                // Bar 20 = Bar 4
                .bar()
                    .o4(HALF, F).o4(QUARTER, E)
                    .o4(EIGHTH, F).o4(EIGHTH, A)
                .aux()
                    .o4(HALF, C, A).o4(QUARTER, C)
                    .o3(QUARTER, A, C.higher(1))
                // Bar 21: second statement
                .bar()
                    .o4(A).o4(C.higher(1)).o4(EIGHTH.dot(), C.higher(1)).o5(SIXTEENTH, D)
                    .o5(QUARTER, F).o5(QUARTER, E)
                .aux()
                    .o4(QUARTER, F, A).o4(QUARTER, F, A)
                    .o4(QUARTER, A, C.higher(1)).o4(QUARTER, A, C.higher(1))
                // Bar 22: ritardando
                .bar()
                    .o5(EIGHTH.dot(), D).o5(SIXTEENTH, C)
                    .o4(QUARTER, B.n()).o4(HALF, A)
                .aux()
                    .o4(QUARTER, F, A).o4(QUARTER, D, G)
                    .o4(HALF, C, F)
                // Bar 23
                .bar()
                    .o4(QUARTER, G).o4(EIGHTH.dot(), A).o4(SIXTEENTH, B)
                    .o5(QUARTER, C).o4(QUARTER, A)
                .aux()
                    .o4(QUARTER, C, E).o4(QUARTER, C, F)
                    .o4(QUARTER, E, G).o4(QUARTER, C, F)
                // Bar 24: final cadence
                .bar()
                    .o4(HALF, G).o4(HALF, F)
                .aux()
                    .o4(HALF, C, E).o4(HALF, C, A)
                .build(end());
        return new Section(main, R.auxPhrases().getFirst());
    }

    // ── LEFT HAND: Bass (main) + Tenor (aux) ───────────────────────

    private Section buildLhPickup() {
        var L = StaffPhraseBuilder.in(KEY, TS, QUARTER);
        var main = L
                .pickup().p()
                    .r(EIGHTH)                      // bass silent during pickup
                .aux()
                    .r(WHOLE)                       // tenor silent during pickup
                .build(attacca());
        return new Section(main, L.auxPhrases().getFirst());
    }

    private Section buildLhA() {
        var L = StaffPhraseBuilder.in(KEY, TS, QUARTER);
        var main = L
                // Bar 1
                .bar().p()
                    .o2(F).o3(QUARTER, C).o2(HALF, F)
                .aux()
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                // Bar 2
                .bar()
                    .o2(F).o3(QUARTER, C).o2(HALF, F)
                .aux()
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                // Bar 3
                .bar()
                    .o2(QUARTER, C).o2(QUARTER, D)
                    .o2(QUARTER, B).o2(QUARTER, C)
                .aux()
                    .o3(QUARTER, G, B.n().higher(0)).o3(QUARTER, F, A)
                    .o3(QUARTER, F, B).o3(QUARTER, E, G)
                // Bar 4
                .bar()
                    .o2(HALF, F).o2(QUARTER, C)
                    .o2(QUARTER, F)
                .aux()
                    .o3(HALF, A, C.higher(1)).o3(QUARTER, G)
                    .o3(QUARTER, A, C.higher(1))
                // Bar 5 = Bar 1
                .bar()
                    .o2(F).o3(QUARTER, C).o2(HALF, F)
                .aux()
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                // Bar 6 = Bar 2
                .bar()
                    .o2(F).o3(QUARTER, C).o2(HALF, F)
                .aux()
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                // Bar 7
                .bar()
                    .o2(QUARTER, C).o2(QUARTER, D)
                    .o2(QUARTER, F).o2(QUARTER, E)
                .aux()
                    .o3(QUARTER, G, B.n().higher(0)).o3(QUARTER, F, B)
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, G, B.n().higher(0))
                // Bar 8
                .bar()
                    .o2(QUARTER, F).o2(QUARTER, C)
                    .o2(HALF, C)
                .aux()
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, G, B.n().higher(0))
                    .o3(HALF, G, C.higher(1))
                .build(attacca());
        return new Section(main, L.auxPhrases().getFirst());
    }

    private Section buildLhB() {
        var L = StaffPhraseBuilder.in(KEY, TS, QUARTER);
        var main = L
                // Bar 9
                .bar()
                    .o2(QUARTER, F).o2(QUARTER, D)
                    .o2(QUARTER, G).o2(QUARTER, C)
                .aux()
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, F, A)
                    .o3(QUARTER, B, D.higher(1)).o3(QUARTER, G, B.n().higher(0))
                // Bar 10
                .bar()
                    .o2(QUARTER, D).o2(QUARTER, E)
                    .o2(HALF, A)
                .aux()
                    .o3(QUARTER, F, A).o3(QUARTER, E, G.s())
                    .o3(HALF, E, A)
                // Bar 11
                .bar()
                    .o2(QUARTER, D).o2(QUARTER, G)
                    .o2(QUARTER, C).o2(QUARTER, D)
                .aux()
                    .o3(QUARTER, F, A).o3(QUARTER, B, D.higher(1))
                    .o3(QUARTER, G, C.higher(1)).o3(QUARTER, F, B)
                // Bar 12
                .bar()
                    .o2(QUARTER, E).o2(QUARTER, F)
                    .o2(HALF, C)
                .aux()
                    .o3(QUARTER, G, C.higher(1)).o3(QUARTER, A, C.higher(1))
                    .o3(HALF, G, B.n().higher(0))
                // Bar 13: pp
                .bar().pp()
                    .o2(QUARTER, B).o2(QUARTER, A)
                    .o2(QUARTER, D).o2(QUARTER, C)
                .aux()
                    .o3(QUARTER, D, F).o3(QUARTER, C, F)
                    .o3(QUARTER, F, A).o3(QUARTER, E, G)
                // Bar 14
                .bar()
                    .o2(QUARTER, D).o2(QUARTER, G)
                    .o2(HALF, C)
                .aux()
                    .o3(QUARTER, F, A).o3(QUARTER, D, G)
                    .o3(HALF, E, G)
                // Bar 15
                .bar().p()
                    .o2(QUARTER, B).o2(QUARTER, A)
                    .o2(QUARTER, F).o2(QUARTER, F)
                .aux()
                    .o3(QUARTER, D, F).o3(QUARTER, C, F)
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                // Bar 16
                .bar()
                    .o2(QUARTER, C).o2(QUARTER, C)
                    .o2(HALF, C)
                .aux()
                    .o3(QUARTER, G, B.n().higher(0)).o3(QUARTER, G, B.n().higher(0))
                    .o3(HALF, G, B.n().higher(0))
                .build(attacca());
        return new Section(main, L.auxPhrases().getFirst());
    }

    private Section buildLhAp() {
        var L = StaffPhraseBuilder.in(KEY, TS, QUARTER);
        var main = L
                // Bar 17 = Bar 1
                .bar().p()
                    .o2(F).o3(QUARTER, C).o2(HALF, F)
                .aux()
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                // Bar 18 = Bar 2
                .bar()
                    .o2(F).o3(QUARTER, C).o2(HALF, F)
                .aux()
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                // Bar 19 = Bar 3
                .bar()
                    .o2(QUARTER, C).o2(QUARTER, D)
                    .o2(QUARTER, B).o2(QUARTER, C)
                .aux()
                    .o3(QUARTER, G, B.n().higher(0)).o3(QUARTER, F, A)
                    .o3(QUARTER, F, B).o3(QUARTER, E, G)
                // Bar 20 = Bar 4
                .bar()
                    .o2(HALF, F).o2(QUARTER, C)
                    .o2(QUARTER, F)
                .aux()
                    .o3(HALF, A, C.higher(1)).o3(QUARTER, G)
                    .o3(QUARTER, A, C.higher(1))
                // Bar 21
                .bar()
                    .o2(F).o3(QUARTER, C).o2(HALF, F)
                .aux()
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                    .o3(QUARTER, A, C.higher(1)).o3(QUARTER, A, C.higher(1))
                // Bar 22
                .bar()
                    .o2(QUARTER, D).o2(QUARTER, G)
                    .o2(HALF, C)
                .aux()
                    .o3(QUARTER, F, A).o3(QUARTER, D, G)
                    .o3(HALF, E, G)
                // Bar 23
                .bar()
                    .o2(QUARTER, B).o2(QUARTER, C)
                    .o2(QUARTER, C).o2(QUARTER, F)
                .aux()
                    .o3(QUARTER, D, F).o3(QUARTER, E, G)
                    .o3(QUARTER, G, B.n().higher(0)).o3(QUARTER, A, C.higher(1))
                // Bar 24: final
                .bar()
                    .o2(QUARTER, C).o2(QUARTER, F)
                    .o2(HALF, F)
                .aux()
                    .o3(QUARTER, E, G).o3(QUARTER, F, A)
                    .o3(HALF, F, A)
                .build(end());
        return new Section(main, L.auxPhrases().getFirst());
    }

    /** Quick playback for audition. */
    public static void main(String[] args) throws Exception {
        music.notation.play.PlayPiece.play(new DefaultTraumerei());
    }
}
