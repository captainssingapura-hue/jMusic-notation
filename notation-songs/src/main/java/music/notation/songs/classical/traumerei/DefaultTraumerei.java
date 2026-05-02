package music.notation.songs.classical.traumerei;

import music.notation.phrase.*;
import music.notation.structure.*;

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
 * Four voices (SATB): Soprano + Alto on the right hand, Tenor + Bass on the left.</p>
 */
public final class DefaultTraumerei implements PieceContentProvider<Traumerei> {

    private static final KeySignature KEY = new KeySignature(F, Mode.MAJOR);
    private static final TimeSignature TS  = new TimeSignature(4, 4);

    private StaffPhraseBuilderTyped newBuilder() {
        return StaffPhraseBuilderTyped.in(KEY, TS, EIGHTH);
    }

    @Override
    public Piece create() {
        final var id = new Traumerei();

        var soprano = joinMelodicPhrases("Soprano", ACOUSTIC_GRAND_PIANO, List.of(
                buildSopranoPickup(),
                buildSopranoSectionA(),
                buildSopranoSectionB(),
                buildSopranoSectionC()));
        var alto = joinMelodicPhrases("Alto", ACOUSTIC_GRAND_PIANO, List.of(
                buildAltoPickup(),
                buildAltoSectionA(),
                buildAltoSectionB(),
                buildAltoSectionC()));
        var tenor = joinMelodicPhrases("Tenor", ACOUSTIC_GRAND_PIANO, List.of(
                buildTenorPickup(),
                buildTenorSectionA(),
                buildTenorSectionB(),
                buildTenorSectionC()));
        var bass = joinMelodicPhrases("Bass", ACOUSTIC_GRAND_PIANO, List.of(
                buildBassPickup(),
                buildBassSectionA(),
                buildBassSectionB(),
                buildBassSectionC()));

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS,
                new Tempo(66, QUARTER),
                List.of(soprano, alto, tenor, bass),
                List.of());
    }

    // ── Pickups ─────────────────────────────────────────────────────

    private MelodicPhrase buildSopranoPickup() {
        return newBuilder()
                .pickup().p().o4(QUARTER, C).done()
                .build(attacca());
    }

    private MelodicPhrase buildAltoPickup() {
        return newBuilder()
                .pickup().p().r(QUARTER).done()
                .build(attacca());
    }

    private MelodicPhrase buildTenorPickup() {
        return newBuilder()
                .pickup().p().r(QUARTER).done()
                .build(attacca());
    }

    private MelodicPhrase buildBassPickup() {
        return newBuilder()
                .pickup().r(QUARTER).done()
                .build(attacca());
    }

    // ── Section A ───────────────────────────────────────────────────

    private MelodicPhrase buildSopranoSectionA() {
        return newBuilder()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A).done()
                .bar(EIGHTH).o5(C).o5(F).o5(HALF,F).o5(E).o5(D).done()
                .bar(EIGHTH).o5(C).o5(F).o4(G).o4(A).o4(B).o5(D).o4(F).o4(E,G).done()
                .bar(EIGHTH).o4(F,A).o5(C).o4(HALF,E,G).o4(QUARTER,C).done()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A).done()
                .bar(EIGHTH).o5(C).o5(A).o5(QUARTER.dot(),A).o5(G).o5(F).o5(E).done()
                .bar(EIGHTH).o5(F).o5(A).o5(D).o5(F).o5(QUARTER.dot(),E).o5(E.f()).done()
                .bar(QUARTER).o5(D).o5(E).o5(HALF,C).done()
                .build(attacca());
    }

    private MelodicPhrase buildAltoSectionA() {
        return newBuilder()
                .bar(EIGHTH).r(QUARTER).o4(HALF.dot(),C,F.lower(1)).done()
                .bar(QUARTER).r().o4(HALF.dot(),F).tieNext().done()
                .bar(QUARTER).o4(F).o4(HALF,E).o4(C).tieNext().done()
                .bar(QUARTER).o4(C).o4(HALF,C).o4(C).done()
                .bar(EIGHTH).r(QUARTER).o4(HALF.dot(),C,F.lower(1)).done()
                .bar(QUARTER).r().o4(HALF.dot(),G,A).done()
                .bar(EIGHTH).o4(QUARTER.dot(),G).o4(A.f()).o4(G).o5(C).o4(E).o4(F.s()).done()
                .bar().o4(HALF,F).o4(QUARTER.dot(),E).o4(EIGHTH,C).done()
                .build(attacca());
    }

    private MelodicPhrase buildTenorSectionA() {
        return newBuilder()
                .bar(QUARTER).o2(F).o3(HALF.dot(),C,A).tieNext().done()
                .bar(EIGHTH).o3(QUARTER,C,A).o4(HALF,D).o4(C).o3(B).done()
                .bar(EIGHTH).o3(QUARTER,A).o3(B).o3(A).o3(QUARTER,G).o3(F).o3(G).done()
                .bar(QUARTER).o3(A).o3(HALF,G).r().done()
                .bar(QUARTER).o2(F).o3(HALF.dot(),C,A).tieNext().done()
                .bar(EIGHTH).o3(QUARTER,C,A).o4(HALF.dot(),E).done()
                .bar(EIGHTH).o4(QUARTER.dot(),D).o4(C).tieNext().o4(QUARTER.dot(),C).o4(C).done()
                .bar(EIGHTH).o3(B.n()).o4(G).o3(A).o3(B.n()).o4(HALF,C).done()
                .build(attacca());
    }

    private MelodicPhrase buildBassSectionA() {
        return newBuilder()
                .bar(WHOLE).o2(F).tieNext().done()
                .bar().o2(QUARTER,F).o3(HALF.dot(),F).done()
                .bar(EIGHTH).o3(QUARTER,C).o3(HALF,C).o2(A).o3(C).done()
                .bar(EIGHTH).o3(QUARTER,F).o3(C).o3(D).o3(C).o2(B).o2(G).o2(A).done()
                .bar(WHOLE).o2(F).tieNext().done()
                .bar().o2(QUARTER,F).o3(HALF.dot(),A).done()
                .bar(EIGHTH).o3(QUARTER.dot(),D,A).o3(F).o3(QUARTER.dot(),G).o3(A.n()).done()
                .bar().o3(HALF,G).o3(C).o3(D).o2(B.f()).o2(G).done()
                .build(attacca());
    }

    // ── Section B ───────────────────────────────────────────────────

    private MelodicPhrase buildSopranoSectionB() {
        return newBuilder()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A).done()
                .bar(EIGHTH).f().o5(C).o5(E.f()).o5(HALF,E.f()).o5(D).o5(C).done()
                .bar(EIGHTH).o4(B).o5(D).o4(G).o4(A).o4(QUARTER.dot(),B).o4(A).done()
                .bar(EIGHTH).o4(QUARTER.dot(),G).o4(QUARTER.dot(),D).r().o4(F).done()
                .bar(EIGHTH).o4(HALF,B).tieNext().o4(B).o4(A).o4(B).o5(D).done()
                .bar(EIGHTH).o5(F).o5(B).o5(HALF,B).o5(A).o5(G).done()
                .bar(EIGHTH).o5(F).o5(A).o5(D).o5(F).o5(QUARTER.dot(),E).o5(E).done()
                .bar(EIGHTH).o5(QUARTER.dot(),D).o4(A).o4(QUARTER,A).o4(QUARTER,G)
                    .aux(EIGHTH, a -> a.r(HALF).r(QUARTER.dot()).o4(C))
                    .done()
                .build(attacca());
    }

    private MelodicPhrase buildAltoSectionB() {
        return newBuilder()
                .bar(EIGHTH).r(QUARTER).o4(HALF.dot(),C,F.lower(1)).done()
                .bar(EIGHTH).f().o5(C).o4(B.f()).ff().o4(HALF,A).tieNext().o4(QUARTER,A).done()
                .bar(EIGHTH).o4(QUARTER.dot(),G).o4(E.f()).o4(D).o4(G).o3(B).o4(C).done()
                .bar(EIGHTH).o3(B).o4(D).o3(G).o3(QUARTER,A).o3(G).o2(G).o2(A).done()
                .bar().r(QUARTER).ff().o5(HALF.dot(), B.lower(1),F).done()
                .bar(QUARTER).r().o5(HALF,E).o5(E)
                    .aux(EIGHTH, a -> a.r(HALF).r().o5(QUARTER,C.s()).r())
                    .done()
                .bar(EIGHTH).o5(QUARTER.dot(),D).o4(B).o4(A).o5(D).o4(F).o4(G).done()
                .bar(EIGHTH).o4(F).o4(A).o4(D).o4(C.s(),E).o4(QUARTER,F).o4(QUARTER,E).done()
                .build(attacca());
    }

    private MelodicPhrase buildTenorSectionB() {
        return newBuilder()
                .bar(QUARTER).o2(F).o3(HALF.dot(),C,A).done()
                .bar(EIGHTH).r().o3(G).o3(F.s()).o3(A).o4(HALF,D).tieNext()
                    .aux(EIGHTH, a -> a.r().r().o3(F.s()).o3(A).o4(D).o4(F.s()).r())
                    .done()
                .bar(EIGHTH).o4(QUARTER.dot(),D).r().r(HALF).done()
                .bar().r(WHOLE).done()
                .bar().r(WHOLE).done()
                .bar(EIGHTH).r().o3(G,D.higher(1)).o4(C.s()).o4(E).o4(HALF,A).tieNext().done()
                .bar(QUARTER).o4(A).r(HALF.dot()).done()
                .bar(EIGHTH).o4(F).o4(A).o4(D).o4(C.s(),E).o4(QUARTER,D).o4(QUARTER,C).done()
                .build(attacca());
    }

    private MelodicPhrase buildBassSectionB() {
        return newBuilder()
                .bar(WHOLE).o2(F).done()
                .bar(EIGHTH).r().o3(C).o3(HALF,D).tieNext().o3(D).o3(F.s()).done()
                .bar(EIGHTH).o3(QUARTER.dot(),G).o3(C,F.s()).o3(QUARTER.dot(), D,G).o3(E.f()).done()
                .bar(EIGHTH).o3(QUARTER.dot(),D).o3(QUARTER,D).o3(G).o2(G).o2(A).done()
                .bar(QUARTER).fff().o1(B).o3(HALF.dot(),F,D.higher(1)).done()
                .bar(EIGHTH).r().o3(G,D.higher(1)).o3(HALF,A).tieNext().o3(A).o4(C.s()).done()
                .bar(EIGHTH).o4(QUARTER.dot(),D).o3(G,C.s().higher(1)).o3(QUARTER.dot(),A,D.higher(1)).o3(B).done()
                .bar(EIGHTH).o3(HALF,A).tieNext().o3(A).o3(B).tieNext().o3(B).o3(C).done()
                .build(attacca());
    }

    // ── Section C (recap A') ────────────────────────────────────────

    private MelodicPhrase buildSopranoSectionC() {
        return newBuilder()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A).done()
                .bar(EIGHTH).o5(C).o5(F).o5(HALF,F).o5(E).o5(D).done()
                .bar(EIGHTH).o5(C).o5(F).o4(G).o4(A).o4(B).o5(D).o4(F).o4(E,G).done()
                .bar(EIGHTH).o4(F,A).o5(C).o4(HALF,E,G).o4(QUARTER,C).done()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A).done()
                .bar(EIGHTH).o5(C).o5(A).ff().o5(QUARTER.dot(),A).o5(G).o5(F).o5(D).done()
                .bar(EIGHTH).ritStart().o5(C).o5(F).o4(G).o4(A).o4(B).o5(D).o4(G).o4(A,F.s()).done()
                .bar(EIGHTH).o4(G,B).o5(D).f().o4(D).p().o4(E).ppp().o4(HALF,F).rit(33)
                    .aux(EIGHTH, b -> b.r(QUARTER).r().o4(C).tieNext().o4(HALF,C))
                    .done()
                .build(end());
    }

    private MelodicPhrase buildAltoSectionC() {
        return newBuilder()
                .bar(EIGHTH).r(QUARTER).o4(HALF.dot(),C,F.lower(1)).done()
                .bar(QUARTER).r().o4(HALF.dot(),F).tieNext().done()
                .bar(QUARTER).o4(F).o4(HALF,E).o4(C).tieNext().done()
                .bar(QUARTER).o4(C).o4(HALF,C).o4(C).done()
                .bar(EIGHTH).r(QUARTER).o4(HALF.dot(),C,F.lower(1)).done()
                .bar(QUARTER).r().o4(HALF.dot(),F,G,D.higher(1)).done()
                .bar(QUARTER).o4(QUARTER,F).o4(HALF,E).o4(D).tieNext().done()
                .bar(QUARTER).o4(D).o3(B).o3(HALF,A).done()
                .build(end());
    }

    private MelodicPhrase buildTenorSectionC() {
        return newBuilder()
                .bar(QUARTER).o2(F).o3(HALF.dot(),C,A).tieNext().done()
                .bar(EIGHTH).o3(QUARTER,C,A).o4(HALF,D).o4(C).o3(B).done()
                .bar(EIGHTH).o3(QUARTER,A).o3(B).o3(A).o3(QUARTER,G).o3(F).o3(G).done()
                .bar(QUARTER).o3(A).o3(HALF,G).r().done()
                .bar(QUARTER).o2(F).o3(HALF.dot(),C,A).tieNext().done()
                .bar(EIGHTH).o3(QUARTER,C,A).o3(HALF.dot(),B.n()).done()
                .bar(EIGHTH).o3(QUARTER,A,C.higher(1)).o3(B).o3(A).o3(QUARTER,G).o3(G).o3(A)
                    .aux(QUARTER, a -> a.r().o4(HALF,C.f()).r())
                    .done()
                .bar(QUARTER).o3(B).o3(C,G).o3(HALF,C).done()
                .build(end());
    }

    private MelodicPhrase buildBassSectionC() {
        return newBuilder()
                .bar(WHOLE).o2(F).tieNext().done()
                .bar().o2(QUARTER,F).o3(HALF.dot(),F).done()
                .bar(EIGHTH).o3(QUARTER,C).o3(HALF,C).o2(A).o3(C).done()
                .bar(EIGHTH).o3(QUARTER,F).o3(C).o3(D).o3(C).o2(B).o2(G).o2(A).done()
                .bar(WHOLE).o2(F).tieNext().done()
                .bar().o2(QUARTER,F).o2(HALF.dot(),G,B.n()).done()
                .bar(EIGHTH).o3(QUARTER,C).o3(HALF,C).o2(B).o3(D).done()
                .bar(QUARTER).o3(G).o3(EIGHTH,C).o2(EIGHTH,C).o2(HALF,F).done()
                .build(end());
    }

    /** Quick playback for audition. */
    public static void main(String[] args) throws Exception {
        music.notation.play.PlayPiece.play(new DefaultTraumerei());
    }
}