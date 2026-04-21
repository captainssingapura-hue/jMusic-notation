package music.notation.songs.classical.traumerei;

import music.notation.duration.Duration;
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
 * Four voices: Soprano + Alto (right hand), Tenor + Bass (left hand).
 * Transcribed from the Breitkopf &amp; Härtel edition (No. 39917).</p>
 */
public final class DefaultTraumerei implements PieceContentProvider<Traumerei> {

    private static final KeySignature KEY = new KeySignature(F, Mode.MAJOR);
    private static final TimeSignature TS  = new TimeSignature(4, 4);

    StaffPhraseBuilderTyped newBuilder(){
        return StaffPhraseBuilderTyped.in(KEY, TS, EIGHTH);
    }

    @Override
    public Piece create() {
        final var id = new Traumerei();

        // Four voices (SATB) shared across every section; declared once at
        // piece level so sections just supply content. Aux voices declared
        // via .aux(a -> ...) inside the builder methods travel with each
        // MelodicPhrase as VoiceOverlays — nothing extra to wire up here.
        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Soprano", ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Alto",    ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Tenor",   ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Bass",    ACOUSTIC_GRAND_PIANO)
        );

        // Four sections: pickup (1 bar) → A (8 bars) → B (8 bars) → C (8 bars).
        // Each section contributes one phrase per voice.
        final var pickup = Section.named("Pickup")
                .duration(Duration.ofSixtyFourths(64))           // 1 bar in 4/4
                .timeSignature(TS)
                .track("Soprano", buildRhPickup())
                .track("Alto",    buildAltoPickup())
                .track("Tenor",   buildLhPickup())
                .track("Bass",    buildTenorPickup())
                .build();

        final var sectionA = Section.named("A")
                .duration(Duration.ofSixtyFourths(8 * 64))       // 8 bars
                .timeSignature(TS)
                .track("Soprano", buildSopranoSectionA1())
                .track("Alto",    buildAltoSectionA1())
                .track("Tenor",   buildTenorSectionA1())
                .track("Bass",    buildBassSectionA1())
                .build();

        final var sectionB = Section.named("B")
                .duration(Duration.ofSixtyFourths(8 * 64))
                .timeSignature(TS)
                .track("Soprano", buildSopranoSectionB())
                .track("Alto",    buildAltoSectionB())
                .track("Tenor",   buildTenorSectionB())
                .track("Bass",    buildBassSectionB())
                .build();

        final var sectionC = Section.named("C")
                .duration(Duration.ofSixtyFourths(8 * 64))
                .timeSignature(TS)
                .track("Soprano", buildSopranoSectionC())
                .track("Alto",    buildAltoSectionC())
                .track("Tenor",   buildTenorSectionC())
                .track("Bass",    buildBassSectionC())
                .build();

        return Piece.ofSections(id.title(), id.composer(), KEY, TS,
                new Tempo(66, QUARTER),
                trackDecls,
                List.of(pickup, sectionA, sectionB, sectionC));
    }

    // ── RIGHT HAND: Soprano (main) + Alto (aux) ────────────────────
    // --- Pick up ---
    private  Phrase buildRhPickup() {
       return newBuilder()
                .pickup().p()
                    .o4(QUARTER,C)   // pickup: quarter note C4
                .done()
                .build(attacca());
    }

    private  Phrase buildAltoPickup() {
        return newBuilder()
                .pickup().p()
                .r(QUARTER)   // pickup: quarter note E4
                .done()
                .build(attacca());
    }

    private  Phrase buildLhPickup() {
        return newBuilder()
                .pickup().r(QUARTER)// pickup: quarter rest
                .done()
                .build(attacca());
    }

    private  Phrase buildTenorPickup() {
        return newBuilder()
                .pickup().p()
                .r(QUARTER)   // pickup: quarter note E4
                .done()
                .build(attacca());
    }


    //Section A1
    private  Phrase buildSopranoSectionA1() {
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
    private  Phrase buildAltoSectionA1() {
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

    private  Phrase buildTenorSectionA1() {
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

    private  Phrase buildBassSectionA1() {
        return newBuilder()
                .bar(WHOLE).o2(F).tieNext().done()
                .bar().o2(QUARTER,F).o3(HALF.dot(),F).done() //TODO Add grace notes later
                .bar(EIGHTH).o3(QUARTER,C).o3(HALF,C).o2(A).o3(C).done()
                .bar(EIGHTH).o3(QUARTER,F).o3(C).o3(D).o3(C).o2(B).o2(G).o2(A).done()
                .bar(WHOLE).o2(F).tieNext().done()
                .bar().o2(QUARTER,F).o3(HALF.dot(),A).done()
                .bar(EIGHTH).o3(QUARTER.dot(),D,A).o3(F).o3(QUARTER.dot(),G).o3(A.n()).done()
                .bar().o3(HALF,G).o3(C).o3(D).o2(B.f()).o2(G).done()
                .build(attacca());
    }


    //Section B
    private  Phrase buildSopranoSectionB() {
        return newBuilder()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A).done()
                .bar(EIGHTH).f().o5(C).o5(E.f()).o5(HALF,E.f()).o5(D).o5(C).done()
                .bar(EIGHTH).o4(B).o5(D).o4(G).o4(A).o4(QUARTER.dot(),B).o4(A).done()
                .bar(EIGHTH).o4(QUARTER.dot(),G).o4(QUARTER.dot(),D).r().o4(F).done()
                .bar(EIGHTH).o4(HALF,B).tieNext().o4(B).o4(A).o4(B).o5(D).done()
                .bar(EIGHTH).o5(F).o5(B).o5(HALF,B).o5(A).o5(G).done()
                .bar(EIGHTH).o5(F).o5(A).o5(D).o5(F).o5(QUARTER.dot(),E).o5(E).done()
                .bar(EIGHTH).o5(QUARTER.dot(),D).o4(A).o4(QUARTER,A).o4(QUARTER,G)
                    .aux(EIGHTH, a -> a.r(HALF).r(QUARTER.dot()).o4(C))  // pickup into next section
                    .done()
                .build(attacca());
    }
    private  Phrase buildAltoSectionB() {
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

    private  Phrase buildTenorSectionB() {
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

    private  Phrase buildBassSectionB() {
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


    //-----Section C -----//
    //Section A1
    private  Phrase buildSopranoSectionC() {
        return newBuilder()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A).done()
                .bar(EIGHTH).o5(C).o5(F).o5(HALF,F).o5(E).o5(D).done()
                .bar(EIGHTH).o5(C).o5(F).o4(G).o4(A).o4(B).o5(D).o4(F).o4(E,G).done()
                .bar(EIGHTH).o4(F,A).o5(C).o4(HALF,E,G).o4(QUARTER,C).done()
                .bar(EIGHTH).o4(HALF,F).tieNext().o4(F).o4(E).o4(F).o4(A).done()
                .bar(EIGHTH).o5(C).o5(A).ff().o5(QUARTER.dot(),A).o5(G).o5(F).o5(D).done()
                .bar(EIGHTH).ritStart().o5(C).o5(F).o4(G).o4(A).o4(B).o5(D).o4(G).o4(A,F.s()).done()
                .bar(EIGHTH).o4(G,B).o5(D).f().o4(D).p().o4(E).ppp().o4(HALF,F).rit(33)
                    .aux(EIGHTH, b->b.r(QUARTER).r().o4(C).tieNext().o4(HALF,C))
                .done()
                .build(end());
    }
    private  Phrase buildAltoSectionC() {
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

    private  Phrase buildTenorSectionC() {
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

    private  Phrase buildBassSectionC() {
        return newBuilder()
                .bar(WHOLE).o2(F).tieNext().done()
                .bar().o2(QUARTER,F).o3(HALF.dot(),F).done() //TODO Add grace notes later
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
