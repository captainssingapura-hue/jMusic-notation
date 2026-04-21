package music.notation.songs.classical.furelise;

import music.notation.duration.Duration;
import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;
import java.util.stream.Stream;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * Für Elise (WoO 59) — Two-hand piano arrangement.
 * Skeleton with motif reuse for Section A's ABACA rondo form.
 * Fill in notes in each buildXxx method.
 */
public final class ManualFurElise implements PieceContentProvider<FurElise> {

    static final KeySignature KEY = new KeySignature(A, Mode.MINOR);
    static final TimeSignature TS = new TimeSignature(3, 8);

    private StaffPhraseBuilderTyped newBuilder() {
        return StaffPhraseBuilderTyped.in(KEY, TS, EIGHTH);
    }

    @Override
    public Piece create() {
        final var id = new FurElise();

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Right Hand", ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Left Hand",  ACOUSTIC_GRAND_PIANO)
        );

        final var rhPhrases = rightHandPhrases();
        final var lhPhrases = leftHandPhrases();

        int total = 0;
        for (Phrase p : rhPhrases) total += Bar.phraseSixtyFourths(p);
        final Duration SONG_DURATION = Duration.ofSixtyFourths(total);

        // Five-part rondo A-B-A-C-A + coda rolled into a single section:
        // the phrase-level PhraseMarking on each boundary still drives
        // elision/attacca; sectioning is just about structural grouping.
        final var song = Section.named("Rondo")
                .duration(SONG_DURATION)
                .timeSignature(TS)
                .track("Right Hand", rhPhrases)
                .track("Left Hand",  lhPhrases)
                .build();

        return Piece.ofSections(id.title(), id.composer(),
                KEY, TS,
                new Tempo(76, QUARTER),
                trackDecls,
                List.of(song));
    }

    // ── Track assembly (ABACA + Coda) ──

    List<Phrase> rightHandPhrases() {
        var a = rhSectionA();
        var b = List.<Phrase>of(buildRHSectionB(), buildRHSectionABase(), buildRHSectionAAnswer(), buildRHSectionABase());
        var c = List.<Phrase>of(buildRHSectionC());
        var coda = List.<Phrase>of(buildRHSectionABase(), buildRHSectionAAnswer(), buildRHCoda());
        return concat(a,b,c,coda);
    }

    List<Phrase> leftHandPhrases() {
        var a = lhSectionA();
        var b = List.<Phrase>of(buildLHSectionB(), buildLHSectionA(), buildLHSectionAAnswer(), buildLHSectionABeforeSectionC());
        var c = List.<Phrase>of(buildLHSectionC());
        var coda = List.<Phrase>of(buildLHSectionA(), buildLHSectionAAnswer(), buildLHCoda());
        return concat(a,b,c,coda);
    }

    @SafeVarargs
    private static List<Phrase> concat(List<Phrase>... lists) {
        return Stream.of(lists).flatMap(List::stream).toList();
    }

    // ══════════════════════════════════════════════════════════════
    //  Section A — Right Hand (motif-based, ~18 bars)
    // ══════════════════════════════════════════════════════════════

    private List<Phrase> rhSectionA() {
        return List.of(
            pickUpSectionA(), buildRHSectionABase(), pickUpSectionA(), buildRHSectionABase(), buildRHSectionAAnswer(), buildRHSectionABase(),
                buildRHSectionAAnswer(), buildRHSectionABase()
        );
    }


    MelodicPhrase pickUpSectionA(){
        return newBuilder()
                .pickup(SIXTEENTH).o5(E).o5(D.s()).done()
                .build(attacca());
    }

    /** Motif with pickup: E-D#-E-D#-E-B / D-C-A. Pickup = 5 sixteenths. */
    MelodicPhrase buildRHSectionABase() {
        // TODO: fill in right hand — opening motif with pickup
        return newBuilder()
                .bar(SIXTEENTH).o5(E).o5(D.s()).o5(E).o4(B).o5(D).o5(C).done()
                .bar(SIXTEENTH).o4(EIGHTH,A).r().o4(C).o4(E).o4(A).done()
                .bar(SIXTEENTH).o4(EIGHTH,B).r().o4(E).o4(G.s()).o4(B).done()
                .bar(SIXTEENTH).o5(EIGHTH,C).r(SIXTEENTH).o4(E).o5(E).o5(D.s()).done() //Bar 4
                .bar(SIXTEENTH).o5(E).o5(D.s()).o5(E).o4(B).o5(D).o5(C).done()
                .bar(SIXTEENTH).o4(EIGHTH,A).r().o4(C).o4(E).o4(A).slurStart().done()
                .bar(SIXTEENTH).o4(EIGHTH,B).slurEnd().r().o4(E).slurStart().o5(C).o4(B).done()
                .bar(EIGHTH).o4(A).slurEnd().pad(QUARTER).done()//.r(EIGHTH).o5(E).o5(D.s()) //Bar 8
                .build(elision());
    }

    Phrase buildRHSectionAContinuation(){
        return OverlayBuilder.over(buildRHSectionABase(), KEY, TS, EIGHTH)
                .at(7, SIXTEENTH, b -> b.o4(EIGHTH,A).slurEnd().r(SIXTEENTH).o4(B).o5(C).o5(D))
                .build(attacca());
    }

    Phrase buildRHSectionAAnswer(){
        return newBuilder()
                .pickup(SIXTEENTH).o4(B).o5(C).o5(D).done()
                .bar(SIXTEENTH).o5(EIGHTH.dot(),E).o4(G).slurStart().o5(F).o5(E).done()
                .bar(SIXTEENTH).o5(EIGHTH.dot(),D).slurEnd().o4(F).slurStart().o5(E).o5(D).done()
                .bar(SIXTEENTH).o5(EIGHTH.dot(),C).slurEnd().o4(E).slurStart().o5(D).o5(C).done()
                .bar(SIXTEENTH).o4(EIGHTH,B).r(SIXTEENTH).o4(E).o5(E).r().done()
                .bar(SIXTEENTH).r().o5(E).o6(E).r().r().o5(D.s()).done()
                .bar(SIXTEENTH).o5(E).r().r().o5(D.s()).o5(E).o5(D.s()).done()
                .build(attacca());
    }





    // ══════════════════════════════════════════════════════════════
    //  Section A — Left Hand (matching motifs)
    // ══════════════════════════════════════════════════════════════

    private List<Phrase> lhSectionA() {
        final var sectionABase = buildLHSectionA();
        return List.of(
            pickupLHSectionA(), sectionABase, pickupLHSectionA(), sectionABase, buildLHSectionAAnswer(),
                sectionABase, buildLHSectionAAnswer(), sectionABase
        );
    }

    MelodicPhrase pickupLHSectionA(){
        return newBuilder()
                .pickup().r(EIGHTH).done()
                .build(attacca());
    }

    MelodicPhrase buildLHSectionA() {
        // TODO: fill in left hand — bass under opening motif
        return newBuilder()
                .bar().r(QUARTER.dot()).done()
                .bar(SIXTEENTH).o2(A).o3(E).o3(A).r().r(EIGHTH).done()
                .bar(SIXTEENTH).o2(E).o3(E).o3(G.s()).r().r(EIGHTH).done()
                .bar(SIXTEENTH).o2(A).o3(E).o3(A).r().r(EIGHTH).done()
                .bar().r(QUARTER.dot()).done()
                .bar(SIXTEENTH).o2(A).o3(E).o3(A).r().r(EIGHTH).done()
                .bar(SIXTEENTH).o2(E).o3(E).o3(G.s()).r().r(EIGHTH).done() //7
                .bar(SIXTEENTH).o2(A).o3(E).o3(A).pad(EIGHTH.dot()).done() //8 to be overriden for 2nd repeat
                .build(elision());
    }

    Phrase buildLHSectionABeforeSectionC() {
        // TODO: fill in left hand — bass under opening motif
        return OverlayBuilder.over(buildLHSectionA(), KEY, TS, EIGHTH)
                .at(7, SIXTEENTH, b -> b.o2(A).o2(A).o2(A).pad(EIGHTH.dot())) //8 to be overridden for 2nd repeat
                .build(elision());
    }

    Phrase buildLHSectionAAnswer(){
        return newBuilder()
                .pickup(EIGHTH).r().done()
                .bar(SIXTEENTH).o3(C).o3(G).o4(A).r().r(EIGHTH).done()
                .bar(SIXTEENTH).o2(G).o3(G).o3(B).r().r(EIGHTH).done()
                .bar(SIXTEENTH).o2(A).o3(E).o3(A).r().r(EIGHTH).done()
                .bar(SIXTEENTH).o2(E).o3(E).o4(E).r().r().o4(E).done()
                .bar(SIXTEENTH).o5(E).r().r().o5(D.s()).o5(E).r().done()
                .bar(SIXTEENTH).r().o5(D.s()).o5(E).r().r(EIGHTH).done()
                .build(attacca());
    }

    /** LH motif with pickup — rests matching RH pickup duration. */
    MelodicPhrase buildLHMotifFirst() {
        // TODO: fill in left hand — bass under opening motif
        return newBuilder()
                .pickup(SIXTEENTH).r().r().r().r().r().done()
                .bar().r(QUARTER.dot()).done()
                .build(attacca());
    }

    /** LH motif without pickup. Reused 2×. */
    MelodicPhrase buildLHMotif() {
        // TODO: fill in left hand — bass under motif
        return newBuilder()
                .bar().r(QUARTER.dot()).done()
                .bar().r(QUARTER.dot()).done()
                .build(attacca());
    }

    /** LH answer phrase: A-E-A arpeggiated bass. */
    MelodicPhrase buildLHAnswer() {
        // TODO: fill in left hand — bass under answer
        return newBuilder()
                .bar(SIXTEENTH).o2(A).o3(E).o3(A).r().r(EIGHTH).done()
                .bar(SIXTEENTH).o2(E).o3(E).o3(G.s()).r().r(EIGHTH).done()
                .build(attacca());
    }

    /** LH extension phrase. */
    MelodicPhrase buildLHExtension() {
        // TODO: fill in left hand — bass under extension
        return newBuilder()
                .bar(SIXTEENTH).o2(A).o3(E).o3(A).r().r(EIGHTH).done()
                .build(attacca());
    }

    Phrase overrideLHAnswer1(Phrase answer) {
        return OverlayBuilder.over(answer, KEY, TS, EIGHTH).build(attacca());
    }

    Phrase overrideLHExtCadence(Phrase ext) {
        return OverlayBuilder.over(ext, KEY, TS, EIGHTH).build(attacca());
    }

    Phrase overrideLHAnswer2(Phrase answer) {
        return OverlayBuilder.over(answer, KEY, TS, EIGHTH).build(attacca());
    }

    MelodicPhrase buildLHClose() {
        // TODO: fill in left hand — closing bass
        return newBuilder()
                .bar().r(QUARTER.dot()).done()
                .bar().r(QUARTER.dot()).done()
                .build(attacca());
    }

    // ══════════════════════════════════════════════════════════════
    //  Section B — Lyrical, C major → F major (~16 bars)
    // ══════════════════════════════════════════════════════════════

    /** RH Section B: lyrical melody in F major area. */
    MelodicPhrase buildRHSectionB() {
        // TODO: fill in right hand — Section B melody
        return newBuilder()
                .pickup(SIXTEENTH).o4(E,C.higher(1)).o4(F,C.higher(1)).o4(E,G,C.higher(1)).done()
                .bar(SIXTEENTH).o5(QUARTER,C).o5(SIXTEENTH.dot(),F).o5(THIRTY_SECOND,E).done()
                .bar(SIXTEENTH).o5(EIGHTH,E).o5(EIGHTH,D).o5(SIXTEENTH.dot(),B.f()).o5(THIRTY_SECOND,A).done()
                .bar(SIXTEENTH).o5(A).o5(G).o5(F).o5(E).o5(D).o5(C).done()
                .bar(THIRTY_SECOND).o4(EIGHTH,B.f()).o4(EIGHTH,A).o4(A).o4(G).o4(A).o4(B.f()).done()
                .bar(SIXTEENTH).o5(QUARTER,C).o5(D).o5(D.s()).done()
                .bar(SIXTEENTH).o5(EIGHTH.dot(),E).o5(E).o5(F).o4(A).done()
                .bar(SIXTEENTH).o5(EIGHTH,C).o5(THIRTY_SECOND,E).o5(THIRTY_SECOND,D).o5(THIRTY_SECOND,C).o5(THIRTY_SECOND,D).o5(SIXTEENTH.dot(),D).o4(THIRTY_SECOND,B).done()
                .bar(THIRTY_SECOND).o5(C).o5(G).o5(G).o5(G).o4(A).o5(G).o5(B).o5(G).o5(C).o5(G).o5(D).o5(G).done()
                .bar(THIRTY_SECOND).o5(E).o5(G).o6(C).o5(B).o5(A).o5(G).o5(F).o5(E).o5(D).o5(G).o5(F).o5(D).done()
                .bar(THIRTY_SECOND).o5(C).o5(G).o5(G).o5(G).o4(A).o5(G).o5(B).o5(G).o5(C).o5(G).o5(D).o5(G).done()
                .bar(THIRTY_SECOND).o5(E).o5(G).o6(C).o5(B).o5(A).o5(G).o5(F).o5(E).o5(D).o5(G).o5(F).o5(D).done()
                .bar(THIRTY_SECOND).o5(E).o5(F).o5(E).o5(D.s()).o5(E).o4(B).o5(E).o5(D.s()).o5(E).o4(B).o5(E).o5(D.s()).done()
                .bar(SIXTEENTH).o5(EIGHTH.dot(),E).o4(B).o5(E).o5(D.s()).done()
                .bar(SIXTEENTH).o5(EIGHTH.dot(),E).o4(B).o5(E).r().done()
                .bar(SIXTEENTH).r().o5(D.s()).o5(E).r().r().o5(D.s()).done()
                .build(attacca());
    }

    /** LH Section B: Alberti bass pattern. */
    MelodicPhrase buildLHSectionB() {
        // TODO: fill in left hand — Section B bass
        return newBuilder()
                .pickup(SIXTEENTH).o3(B.f(),C.higher(1)).o3(A,C.higher(1)).o3(G,B.f(),C.higher(1)).done()
                .bar(SIXTEENTH).o3(F).o3(A).o4(C).o3(A).o4(C).o3(A).done()
                .bar(SIXTEENTH).o3(F).o3(B.f()).o4(D).o3(B.f()).o4(D).o3(B.f()).done()
                .bar(SIXTEENTH).o3(F).o4(E).o3(F,G,B.f()).o4(E).o3(F,G,B.f()).o4(E).done()
                .bar(SIXTEENTH).o3(F).o3(A).o4(C).o3(A).o4(C).o3(A).done()
                .bar(SIXTEENTH).o3(F).o3(A).o4(C).o3(A).o4(C).o3(A).done()
                .bar(SIXTEENTH).o3(E).o3(A).o4(C).o3(A).o3(D,D.higher(1)).o3(F).done()
                .bar(SIXTEENTH).o3(G).o4(E).o3(G).o4(E).o3(G).o4(F).done()
                .bar(SIXTEENTH).o4(EIGHTH,C,E).r().o4(F,G).o4(E,G).o4(E,G,A).done()
                .bar(EIGHTH).o4(C,E,G).o3(F,A).o3(G,B).done()
                .bar(SIXTEENTH).o4(EIGHTH,C).r().o4(F,G).o4(E,G).o4(E,G,A).done()
                .bar(EIGHTH).o4(C,E,G).o3(F,A).o3(G,B).done()
                .bar(EIGHTH).o3(G.s(),B).r().r().done()
                .bar().r(QUARTER.dot()).done()
                .bar(SIXTEENTH).r(QUARTER).r(SIXTEENTH).o5(D.s()).slurStart().done()
                .bar(SIXTEENTH).o5(E).slurEnd().r().r().o5(D.s()).o5(E).r().done()
                .build(attacca());
    }

    // ══════════════════════════════════════════════════════════════
    //  Section C — Dramatic, A minor (~22 bars)
    // ══════════════════════════════════════════════════════════════

    /** RH Section C: dramatic repeated chords, chromatic runs. */
    MelodicPhrase buildRHSectionC() {
        // TODO: fill in right hand — Section C melody
        return newBuilder()
                .pickup(EIGHTH).r().done()
                .bar().o4(QUARTER.dot(),E,G,B.f(),C.s().higher(1)).done()
                .bar(SIXTEENTH).o4(QUARTER,F,A,D.higher(1)).o5(C.s(),E).o5(D,F).done()
                .bar(EIGHTH).o5(QUARTER,G.s().lower(1),D,F).o5(G.s().lower(1),D,F).done()
                .bar().o5(QUARTER.dot(),A.lower(1),C,E).done()
                .bar(SIXTEENTH).o4(QUARTER,F,D.higher(1)).o4(E,C.higher(1)).o4(D,B).done()
                .bar(EIGHTH).o4(QUARTER,C,F.s(),A).o4(C,A).done()
                .bar(EIGHTH).o4(C,A).o4(E,C.higher(1)).o4(D,B).done()
                .bar().o4(QUARTER.dot(),C,A).done()
                .bar().o4(QUARTER.dot(),E,G,B.f(),C.s().higher(1)).done()
                .bar(SIXTEENTH).o4(QUARTER,F,A,D.higher(1)).o5(C.s(),E).o5(D,F).done()
                .bar(EIGHTH).o5(QUARTER,D,F).o5(D,F).done()
                .bar().o5(QUARTER.dot(),D,F).done()
                .bar(SIXTEENTH).o4(QUARTER,G,E.f().higher(1)).o4(F,D.higher(1)).o4(E.f(),C.higher(1)).done()
                .bar(EIGHTH).o4(QUARTER,D,F,B.f()).o4(D,G,A).done()
                .bar(EIGHTH).o4(QUARTER,D,F.s(),G).o4(D,F.s(),G).done()
                .bar(EIGHTH).o4(QUARTER,C,E,A).r().done()
                .bar(EIGHTH).o4(D,E,B).r().r().done()
                .bar(SIXTEENTH).triplet(EIGHTH, 3, A, C.s().higher(1), E.higher(1))
                    .triplet(EIGHTH, 4, A, C.s().higher(1), E.higher(1))
                    .triplet(EIGHTH, 4, D.higher(1), C.s().higher(1), B).done()
                .bar(EIGHTH).triplet(5,A.lower(1),C,E).triplet(6,A.lower(1),C,E).triplet(6,D,C,B.lower(1)).done()
                .bar(EIGHTH).triplet(6,A.lower(1),C,E).triplet(7,A.lower(1),C,E).triplet(7,D,C,B.lower(1)).done()
                .bar(EIGHTH).triplet(6,B.f(),A,G.s()).triplet(6,G,F.s(),F).triplet(6,E,D.s(),D).done()
                .bar(EIGHTH).triplet(6,C.s(),C,B.lower(1)).triplet(5,B.f(),A,G.s()).triplet(5,G,D.s(),D).done()
                .build(attacca());
    }

    /** LH Section C: repeated bass octaves, pedal point on A. */
    MelodicPhrase buildLHSectionC() {
        // TODO: fill in left hand — Section C bass
        return newBuilder()
                .pickup(SIXTEENTH).o2(A).o2(A).o2(A).done()
                .bar(SIXTEENTH).o2(A).o2(A).o2(A).o2(A).o2(A).o2(A).done()
                .bar(SIXTEENTH).o2(A).o2(A).o2(A).o2(A).o2(A).o2(A).done()
                .bar(SIXTEENTH).o2(A).o2(A).o2(A).o2(A).o2(A).o2(A).done()
                .bar(SIXTEENTH).o2(A).o2(A).o2(A).o2(A).o2(A).o2(A).done()
                .bar(SIXTEENTH).o2(D,A).o2(D,A).o2(D,A).o2(D,A).o2(D,A).o2(D,A).done()
                .bar(SIXTEENTH).o2(D.s(),A).o2(D.s(),A).o2(D.s(),A).o2(D.s(),A).o2(D.s(),A).o2(D.s(),A).done()
                .bar(SIXTEENTH).o2(E,A).o2(E,A).o2(E,A).o2(E,A).o2(E,G.s()).o2(E,G.s()).done()
                .bar(SIXTEENTH).o2(A.lower(1),A).o2(A).o2(A).o2(A).o2(A).o2(A).done()
                .bar(SIXTEENTH).o2(A).o2(A).o2(A).o2(A).o2(A).o2(A).done()
                .bar(SIXTEENTH).o2(A).o2(A).o2(A).o2(A).o2(A).o2(A).done()
                .bar(SIXTEENTH).o2(A).o2(A).o2(A).o2(A).o2(A).o2(A).done()
                .bar(SIXTEENTH).o2(B.f()).o2(B.f()).o2(B.f()).o2(B.f()).o2(B.f()).o2(B.f()).done()
                .bar(SIXTEENTH).o2(B.f()).o2(B.f()).o2(B.f()).o2(B.f()).o2(B.f()).o2(B.f()).done()
                .bar(SIXTEENTH).o2(B.f()).o2(B.f()).o2(B.f()).o2(B.f()).o2(B.f()).o2(B.f()).done()
                .bar(SIXTEENTH).o2(B).o2(B).o2(B).o2(B).o2(B).o2(B).done()
                .bar(EIGHTH).o3(QUARTER,C).r().done()
                .bar(EIGHTH).o3(E,G.s()).r().r().done()
                .bar(EIGHTH).o2(A.lower(1)).r().o4(A.lower(1),C,E).slurStart().done()
                .bar(EIGHTH).o4(A.lower(1),C,E).slurEnd().r().o4(A.lower(1),C,E).slurStart().done()
                .bar(EIGHTH).o4(A.lower(1),C,E).slurEnd().r().o4(A.lower(1),C,E).slurStart().done()
                .bar(EIGHTH).o4(A.lower(1),C,E).slurEnd().r().r().done()
                .bar().r(QUARTER.dot()).done()
                .build(attacca());
    }

    // ══════════════════════════════════════════════════════════════
    //  Coda (~4 bars)
    // ══════════════════════════════════════════════════════════════

    /** RH Coda: concluding A minor cadence. */
    Phrase buildRHCoda() {
        return OverlayBuilder.over(buildRHSectionABase(), KEY, TS, EIGHTH)
                .at(7, QUARTER, b -> b.o4(C, A).r(EIGHTH)) //8 to be overridden for 2nd repeat
                .build(end());
    }

    /** LH Coda: final bass cadence E→A. */
    Phrase buildLHCoda() {
        return OverlayBuilder.over(buildLHSectionA(), KEY, TS, EIGHTH)
                .at(7, QUARTER, b -> b.o2(A.lower(1), A).r(EIGHTH)) //8 to be overridden for 2nd repeat
                .build(end());
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ManualFurElise());
    }
}
