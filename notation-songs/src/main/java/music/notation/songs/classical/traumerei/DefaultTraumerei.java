package music.notation.songs.classical.traumerei;

import music.notation.duration.Duration;
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

    StaffPhraseBuilder newBuilder(){
        return StaffPhraseBuilder.in(KEY, TS, EIGHTH);
    }

    @Override
    public Piece create() {
        final var id = new Traumerei();

        // ── Build each section for RH (soprano + alto) ──────────────
        final var rhPickup  = buildRhPickup();


        // ── Build each section for LH (bass + tenor) ────────────────
        final var lhPickup  = buildLhPickup();

        final var soprano = List.<Phrase>of(
                rhPickup, buildSopranoSectionA1(),
                buildSopranoSectionB(),
                buildSopranoSectionC()
        );
        final var altoPickupPhrase = buildAltoPickup();
        final var altoA1Phrase     = buildAltoSectionA1();
        final var altoBPhrase      = buildAltoSectionB();
        final var altoCPhrase      = buildAltoSectionC();
        final var altoPhrases = List.of(altoPickupPhrase, altoA1Phrase, altoBPhrase, altoCPhrase);

        // Aux voice(s) for alto section B — previously dropped because the
        // builder's .auxPhrases() was never extracted. The aux Track mirrors
        // alto's per-section timing with rest phrases for sections that have
        // no aux content, so it plays in parallel with the alto main line.
        final List<Track> altoAuxTracks = new ArrayList<>();
        final var altoBAux = altoSectionBAux();
        for (int voice = 0; voice < altoBAux.size(); voice++) {
            final var auxPhrases = List.<Phrase>of(
                    restMatching(altoPickupPhrase),
                    restMatching(altoA1Phrase),
                    altoBAux.get(voice),
                    restMatching(altoCPhrase)
            );
            altoAuxTracks.add(new Track(
                    "Alto Aux " + (voice + 1), ACOUSTIC_GRAND_PIANO, auxPhrases, List.of()));
        }
        final var tenor = List.<Phrase>of(
                lhPickup, buildTenorSectionA1(),
                buildTenorSectionB(),
                buildTenorSectionC()
        );
        final var bass = List.of(
                buildTenorPickup(), buildBassSectionA1(),
                buildBassSectionB(),
                buildBassSectionC()
        );

        final var rightHand = new Track("Soprano", ACOUSTIC_GRAND_PIANO, soprano, List.of());
        final var alto = new Track("Alto", ACOUSTIC_GRAND_PIANO, altoPhrases, altoAuxTracks);
        final var leftHand  = new Track("Tenor", ACOUSTIC_GRAND_PIANO, tenor, List.of());
        final var tenorHand = new Track("Bass", ACOUSTIC_GRAND_PIANO, bass, List.of());

        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(66, QUARTER),
                List.of(rightHand, alto, leftHand, tenorHand));
    }

    // ── RIGHT HAND: Soprano (main) + Alto (aux) ────────────────────
    // --- Pick up ---
    private  Phrase buildRhPickup() {
       return newBuilder()
                .pickup().p()
                    .o4(QUARTER,C)   // pickup: quarter note C4
                .build(attacca());
    }

    private  Phrase buildAltoPickup() {
        return newBuilder()
                .pickup().p()
                .r(QUARTER)   // pickup: quarter note E4
                .build(attacca());
    }

    private  Phrase buildLhPickup() {
        return newBuilder()
                .pickup().r(QUARTER)// pickup: quarter rest
                .build(attacca());
    }

    private  Phrase buildTenorPickup() {
        return newBuilder()
                .pickup().p()
                .r(QUARTER)   // pickup: quarter note E4
                .build(attacca());
    }


    //Section A1
    private  Phrase buildSopranoSectionA1() {
        return newBuilder()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A)
                .bar(EIGHTH).o5(C).o5(F).o5(HALF,F).o5(E).o5(D)
                .bar(EIGHTH).o5(C).o5(F).o4(G).o4(A).o4(B).o5(D).o4(F).o4(E,G)
                .bar(EIGHTH).o4(F,A).o5(C).o4(HALF,E,G).o4(QUARTER,C)
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A)
                .bar(EIGHTH).o5(C).o5(A).o5(QUARTER.dot(),A).o5(G).o5(F).o5(E)
                .bar(EIGHTH).o5(F).o5(A).o5(D).o5(F).o5(QUARTER.dot(),E).o5(E.s())
                .bar(QUARTER).o5(D).o5(E).o5(HALF,C)
                .build(attacca());
    }
    private  Phrase buildAltoSectionA1() {
        return newBuilder()
                .bar(EIGHTH).r(QUARTER).o4(HALF.dot(),C,F.lower(1))
                .bar(QUARTER).r().o4(HALF.dot(),F).tieNext()
                .bar(QUARTER).o4(F).o4(HALF,E).o4(C).tieNext()
                .bar(QUARTER).o4(C).o4(HALF,C).o4(C)
                .bar(EIGHTH).r(QUARTER).o4(HALF.dot(),C,F.lower(1))
                .bar(QUARTER).r().o4(HALF.dot(),G,A)
                .bar(EIGHTH).o4(QUARTER.dot(),G).o4(A.f()).o4(G).o5(C).o4(E).o4(F.s())
                .bar().o4(HALF,F).o4(QUARTER.dot(),E).o4(EIGHTH,C)
                .build(attacca());
    }

    private  Phrase buildTenorSectionA1() {
        return newBuilder()
                .bar(QUARTER).o2(F).o3(HALF.dot(),C,A).tieNext()
                .bar(EIGHTH).o3(QUARTER,C,A).o4(HALF,D).o4(C).o3(B)
                .bar(EIGHTH).o3(QUARTER,A).o3(B).o3(A).o3(QUARTER,G).o3(F).o3(G)
                .bar(QUARTER).o3(A).o3(HALF,G).r()
                .bar(QUARTER).o2(F).o3(HALF.dot(),C,A).tieNext()
                .bar(EIGHTH).o3(QUARTER,C,A).o4(HALF.dot(),E)
                .bar(EIGHTH).o4(QUARTER.dot(),D).o4(C).tieNext().o4(QUARTER.dot(),C).o4(C)
                .bar(EIGHTH).o3(B.n()).o4(G).o3(A).o3(B.n()).o4(HALF,C)
                .build(attacca());
    }

    private  Phrase buildBassSectionA1() {
        return newBuilder()
                .bar(WHOLE).o2(F).tieNext()
                .bar().o2(QUARTER,F).o3(HALF.dot(),F) //TODO Add grace notes later
                .bar(EIGHTH).o3(QUARTER,C).o3(HALF,C).o2(A).o3(C)
                .bar(EIGHTH).o3(QUARTER,F).o3(C).o3(D).o3(C).o2(B).o2(G).o2(A)
                .bar(WHOLE).o2(F).tieNext()
                .bar().o2(QUARTER,F).o3(HALF.dot(),A)
                .bar(EIGHTH).o3(QUARTER.dot(),D,A).o3(F).o3(QUARTER.dot(),G).o3(A.n())
                .bar().o3(HALF,G).o3(C).o3(D).o2(B.f()).o2(G)
                .build(attacca());
    }


    //Section B
    private  Phrase buildSopranoSectionB() {
        return newBuilder()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A)
                .bar(EIGHTH).f().o5(C).o5(E.f()).o5(HALF,E.f()).o5(D).o5(C)
                .bar(EIGHTH).o4(B).o5(D).o4(G).o4(A).o4(QUARTER.dot(),B).o4(A)
                .bar(EIGHTH).o4(QUARTER.dot(),G).o4(QUARTER.dot(),D).r().o4(F)
                .bar(EIGHTH).o4(HALF,B).tieNext().o4(B).o4(A).o4(B).o5(D)
                .bar(EIGHTH).o5(F).o5(B).o5(HALF,B).o5(A).o5(G)
                .bar(EIGHTH).o5(F).o5(A).o5(D).o5(E).o5(QUARTER.dot(),F).o5(E)
                .bar(EIGHTH).o5(QUARTER.dot(),D).o4(A).o4(QUARTER,A).o4(QUARTER,G)
                    .aux(EIGHTH).r(HALF).r(QUARTER.dot()).o4(C)  //For the small note to start next section.
                .build(attacca());
    }
    /** Alto section B. Returns the main line; aux voices are extracted via {@link #altoSectionBAux}. */
    private  Phrase buildAltoSectionB() {
        return buildAltoSectionBBuilder().build(attacca());
    }

    /** Aux voices for alto section B (the {@code .aux(...)} content on the 6th bar). */
    private  List<MelodicPhrase> altoSectionBAux() {
        var P = buildAltoSectionBBuilder();
        P.build(attacca());            // must call build() to populate auxPhrases()
        return P.auxPhrases();
    }

    /**
     * Shared builder for alto section B. Exposed so both the main phrase and
     * the aux phrases can be extracted (aux content is otherwise silently
     * dropped when only {@code build()} is called).
     */
    private  StaffPhraseBuilder buildAltoSectionBBuilder() {
        return newBuilder()
                .bar(EIGHTH).r(QUARTER).o4(HALF.dot(),C,F.lower(1))
                .bar(EIGHTH).f().o5(C).o4(B.f()).ff().o4(HALF,A).tieNext().o4(QUARTER,A)
                .bar(EIGHTH).o4(QUARTER.dot(),G).o4(E.f()).o4(D).o4(G).o3(B).o4(C)
                .bar(EIGHTH).o3(B).o4(D).o3(G).o3(QUARTER,A).o3(G).o2(G).o2(A)
                .bar().r(QUARTER).ff().o5(HALF.dot(), B.lower(1),F)
                .bar(QUARTER).r().o5(HALF,E).o5(E)
                    .aux(EIGHTH).r(HALF).r().o5(QUARTER,C.s()).r()
                .bar(EIGHTH).o5(QUARTER.dot(),D).o4(B).o4(A).o5(D).o4(F).o4(G)
                .bar(EIGHTH).o4(F).o4(A).o4(D).o4(C.s(),E).o4(QUARTER,F).o4(QUARTER,E);
    }

    private  Phrase buildTenorSectionB() {
        return newBuilder()
                .bar(QUARTER).o2(F).o3(HALF.dot(),C,A)
                .bar(EIGHTH).r().o3(G).o3(F.s()).o3(A).o4(HALF,D).tieNext()
                    .aux(EIGHTH).r().r().o3(F.s()).o3(A).o4(D).o4(F.s()).r()
                .bar(EIGHTH).o4(QUARTER.dot(),D).r().r(HALF)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar(EIGHTH).r().o3(G,D.higher(1)).o4(C.s()).o4(E).o4(HALF,A).tieNext()
                .bar(QUARTER).o4(A).r(HALF.dot())
                .bar(EIGHTH).o4(F).o4(A).o4(D).o4(C.s(),E).o4(QUARTER,D).o4(QUARTER,C)
                .build(attacca());
    }

    private  Phrase buildBassSectionB() {
        return newBuilder()
                .bar(WHOLE).o2(F)
                .bar(EIGHTH).r().o3(C).o3(HALF,D).tieNext().o3(D).o3(F.s())
                .bar(EIGHTH).o3(QUARTER.dot(),G).o3(C,F.s()).o3(QUARTER.dot(), D,G).o3(E.f())
                .bar(EIGHTH).o3(QUARTER.dot(),D).o3(QUARTER,D).o3(G).o2(G).o2(A)
                .bar(QUARTER).fff().o1(B).o3(HALF.dot(),F,D.higher(1))
                .bar(EIGHTH).r().o3(G,D.higher(1)).o3(HALF,A).tieNext().o3(A).o4(C.s())
                .bar(EIGHTH).o4(QUARTER.dot(),D).o3(G,C.s().higher(1)).o3(QUARTER.dot(),A,D.higher(1)).o3(B)
                .bar(EIGHTH).o3(HALF,A).tieNext().o3(A).o3(B).tieNext().o3(B).o3(C)
                .build(attacca());
    }


    //-----Section C -----//
    //Section A1
    private  Phrase buildSopranoSectionC() {
        return newBuilder()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A)
                .bar(EIGHTH).o5(C).o5(F).o5(HALF,F).o5(E).o5(D)
                .bar(EIGHTH).o5(C).o5(F).o4(G).o4(A).o4(B).o5(D).o4(F).o4(E,G)
                .bar(EIGHTH).o4(F,A).o5(C).o4(HALF,E,G).o4(QUARTER,C)
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A)
                .bar(EIGHTH).o5(C).o5(A).ff().o5(QUARTER.dot(),A).o5(G).o5(F).o5(D)
                .bar(EIGHTH).ritStart().o5(C).o5(F).o4(G).o4(A).o4(B).o5(D).o4(G).o4(A,F.s())
                .bar(EIGHTH).o4(G,B).o5(D).f().o4(D).p().o4(E).ppp().o4(HALF,F).rit(33)
                .build(end());
    }
    private  Phrase buildAltoSectionC() {
        return newBuilder()
                .bar(EIGHTH).r(QUARTER).o4(HALF.dot(),C,F.lower(1))
                .bar(QUARTER).r().o4(HALF.dot(),F).tieNext()
                .bar(QUARTER).o4(F).o4(HALF,E).o4(C).tieNext()
                .bar(QUARTER).o4(C).o4(HALF,C).o4(C)
                .bar(EIGHTH).r(QUARTER).o4(HALF.dot(),C,F.lower(1))
                .bar(QUARTER).r().o4(HALF.dot(),F,G,D.higher(1))
                .bar(QUARTER).o4(QUARTER,F).o4(HALF,E).o4(D).tieNext()
                .bar(QUARTER).o4(D).o3(B).o3(HALF,A)
                .build(end());
    }

    private  Phrase buildTenorSectionC() {
        return newBuilder()
                .bar(QUARTER).o2(F).o3(HALF.dot(),C,A).tieNext()
                .bar(EIGHTH).o3(QUARTER,C,A).o4(HALF,D).o4(C).o3(B)
                .bar(EIGHTH).o3(QUARTER,A).o3(B).o3(A).o3(QUARTER,G).o3(F).o3(G)
                .bar(QUARTER).o3(A).o3(HALF,G).r()
                .bar(QUARTER).o2(F).o3(HALF.dot(),C,A).tieNext()
                .bar(EIGHTH).o3(QUARTER,C,A).o3(HALF.dot(),B.n())
                .bar(EIGHTH).o3(QUARTER,A,C.higher(1)).o3(B).o3(A).o3(QUARTER,G).o3(G).o3(A)
                    .aux(QUARTER).r().o4(HALF,C.f()).r()
                .bar(QUARTER).o3(B).o3(C,G).o3(HALF,C)
                .build(end());
    }

    private  Phrase buildBassSectionC() {
        return newBuilder()
                .bar(WHOLE).o2(F).tieNext()
                .bar().o2(QUARTER,F).o3(HALF.dot(),F) //TODO Add grace notes later
                .bar(EIGHTH).o3(QUARTER,C).o3(HALF,C).o2(A).o3(C)
                .bar(EIGHTH).o3(QUARTER,F).o3(C).o3(D).o3(C).o2(B).o2(G).o2(A)
                .bar(WHOLE).o2(F).tieNext()
                .bar().o2(QUARTER,F).o2(HALF.dot(),G,B.n())
                .bar(EIGHTH).o3(QUARTER,C).o3(HALF,C).o2(B).o3(D)
                .bar(QUARTER).o3(G).o3(EIGHTH,C).o2(EIGHTH,C).o2(HALF,F)
                .build(end());
    }


    /**
     * Build a silent phrase whose total duration matches the given phrase, so
     * an aux Track's timeline aligns bar-for-bar with its parent voice Track.
     */
    private static Phrase restMatching(Phrase phrase) {
        int sf = Bar.phraseSixtyFourths(phrase);
        return new RestPhrase(Duration.ofSixtyFourths(sf), attacca());
    }

    /** Quick playback for audition. */
    public static void main(String[] args) throws Exception {
        music.notation.play.PlayPiece.play(new DefaultTraumerei());
    }
}
