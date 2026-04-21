package music.notation.songs.classical.bachinvention;

import music.notation.duration.Duration;
import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * Invention No. 13 in A Minor (BWV 784) — J.S. Bach.
 *
 * <p>Manual staff-notation transcription using {@link StaffPhraseBuilderTyped}.</p>
 */
public final class ManualBachInvention13 implements PieceContentProvider<BachInvention13> {

    static final KeySignature KEY = new KeySignature(A, Mode.MINOR);
    static final TimeSignature TS = new TimeSignature(4, 4);

    StaffPhraseBuilderTyped newBuilder(){
        return StaffPhraseBuilderTyped.in(KEY, TS, SIXTEENTH);
    }

    @Override
    public Piece create() {
        final var id = new BachInvention13();

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Right Hand", ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Left Hand",  ACOUSTIC_GRAND_PIANO)
        );

        final var s1 = Section.named("Section 1")
                .duration(Duration.ofSixtyFourths(6 * 64))
                .timeSignature(TS)
                .track("Right Hand", buildRhSection1())
                .track("Left Hand",  buildLhSection1())
                .build();

        final var s2 = Section.named("Section 2")
                .duration(Duration.ofSixtyFourths(7 * 64))
                .timeSignature(TS)
                .track("Right Hand", buildRhSection2())
                .track("Left Hand",  buildLhSection2())
                .build();

        final var s3 = Section.named("Section 3")
                .duration(Duration.ofSixtyFourths(12 * 64))
                .timeSignature(TS)
                .track("Right Hand", buildRhSection3())
                .track("Left Hand",  buildLhSection3())
                .build();

        return Piece.ofSections(id.title(), id.composer(),
                KEY, TS,
                new Tempo(72, QUARTER),
                trackDecls,
                List.of(s1, s2, s3));
    }

    // ── Right Hand ──

    MelodicPhrase buildRhSection1() {
        return newBuilder()
                .bar(SIXTEENTH).r(SIXTEENTH).o4(E).o4(A).o5(C).o4(B).o4(E).o4(B).o5(D).o5(EIGHTH,C).o5(EIGHTH,E).o4(EIGHTH,G.s()).o5(EIGHTH,E).done()
                .bar(SIXTEENTH).o4(A).o4(E).o4(A).o5(C).o4(B).o4(E).o4(B).o5(D).o5(EIGHTH,C).o4(EIGHTH,A).r(QUARTER).done()
                .bar(SIXTEENTH).r(SIXTEENTH).o5(E).o5(C).o5(E).o4(A).o5(C).o4(E).o4(G).o4(EIGHTH,F).o4(EIGHTH,A).o5(EIGHTH,D).o5(EIGHTH,F).done()
                .bar(SIXTEENTH).o5(F).o5(D).o4(B).o5(D).o4(G).o4(B).o4(D).o4(F).o4(EIGHTH,E).o4(EIGHTH,G).o5(EIGHTH,C).o5(EIGHTH,E).done()
                .bar(SIXTEENTH).o5(E).o5(C).o4(A).o5(C).o4(EIGHTH,F).o5(EIGHTH,D).slurStart().o5(D).slurEnd().o4(B).o4(G).o4(B).o4(EIGHTH,E).o5(EIGHTH,C).slurStart().done()
                .bar(SIXTEENTH).o5(C).slurEnd().o4(A).o4(F).o4(A).o4(EIGHTH,D).o4(EIGHTH,B).o5(EIGHTH,C).r(EIGHTH).r(QUARTER).done()
                .build(attacca());
    }

    MelodicPhrase buildRhSection2() {
        return newBuilder()
                .bar().r(SIXTEENTH).o4(G).o5(C).o5(E).o5(C).o4(G).o5(D).o5(F).o5(EIGHTH,E).o5(EIGHTH,G).o4(EIGHTH,B).o5(EIGHTH,G).done()
                .bar().o5(C).o4(G).o5(C).o5(E).o5(D).o4(G).o5(E).o5(F).o5(EIGHTH,E).o5(EIGHTH,C).o5(EIGHTH,G).o5(EIGHTH,E).done()
                .bar().o6(C).o5(A).o5(E).o5(A).o5(C).o5(E).o4(A).o5(C).o5(EIGHTH,D).o5(EIGHTH,F.s()).o5(EIGHTH,A).o6(EIGHTH,C).done()
                .bar().o5(B).o5(G).o5(D).o5(G).o4(B).o5(D).o4(G).o4(B).o5(EIGHTH,C).o5(EIGHTH,E).o5(EIGHTH,G).o5(EIGHTH,B).done()
                .bar().o5(A).o5(F.s()).o5(D.s()).o5(F.s()).o4(B).o5(D.s()).o4(F.s()).o4(A).o4(EIGHTH,G).o5(EIGHTH,G).slurStart().o5(G).slurEnd().o5(E).o5(C).o5(E).done()
                .bar().o4(EIGHTH,A).o5(EIGHTH,F.s()).slurStart().o5(F.s()).slurEnd().o5(D).o4(B).o5(D).o4(EIGHTH,G).o5(EIGHTH,E).slurStart().o5(E).slurEnd().o5(C).o4(A).o5(C).done()
                .bar().o4(F.s()).o5(G).o5(F.s()).o5(E).o5(D.s()).o5(F.s()).o4(B).o5(D.s()).o5(EIGHTH,E).r(EIGHTH).r(QUARTER).done()
                .build(attacca());
    }

    MelodicPhrase buildRhSection3() {
        return newBuilder()
                .bar().r(SIXTEENTH).o5(G).o5(B.f()).o5(G).o5(E).o5(G).o5(C.s()).o5(E).o5(G).o5(E).o5(C.s()).o5(E).o4(A).r(SIXTEENTH).r(EIGHTH).done()
                .bar().r(SIXTEENTH).o5(F).o5(A).o5(F).o5(D).o5(F).o4(B).o5(D).o5(F).o5(D).o4(B).o5(D).o4(G).r(SIXTEENTH).r(EIGHTH).done()
                .bar().r(SIXTEENTH).o5(E).o5(G).o5(E).o5(C).o5(E).o4(A).o5(C).o5(D.s()).o5(C).o4(A).o5(C).o4(F.s()).r(SIXTEENTH).r(EIGHTH).done()
                .bar().r(SIXTEENTH).o5(D).o5(F).o5(D).o4(B).o5(D).o4(G.s()).o4(B).o5(D).o4(B).o4(G).o4(B).o4(E).r(SIXTEENTH).r(EIGHTH).done()
                .bar().r(SIXTEENTH).o4(E).o4(A).o5(C).o4(B).o4(E).o4(B).o5(D).o5(EIGHTH,C).o4(EIGHTH,A).o4(EIGHTH,G.s()).o4(EIGHTH,E).done()
                .bar().o4(A).o5(C).o5(E).o5(C).o4(A).o5(C).o4(F.s()).o4(A).o5(C).o4(A).o4(F.s()).o4(A).o4(D.s()).o5(C).o4(B).o4(A).done()
                .bar().o4(G.s()).o4(B).o5(D).o4(B).o4(G.s()).o4(B).o4(D).o4(F).o4(G.s()).o4(F).o4(D).o4(F).o3(B).o4(F).o4(E).o4(D).done()
                .bar().o4(C).o4(E).o4(A).o4(E).o4(C).o4(E).o3(A).o4(C).o4(D.s()).o4(C).o2(A).o4(C).o3(F.s()).o4(C).o3(B).o3(A).done()
                .bar().o3(EIGHTH,G.s()).o4(EIGHTH,B).o4(EIGHTH,G.s()).o4(EIGHTH,E).r(SIXTEENTH).o4(E).o4(A).o5(C).o4(B).o4(E).o4(B).o5(D).done()
                .bar().o5(C).o4(A).o5(C).o5(E).o5(D).o4(B).o5(D).o5(F).o5(E).o5(C).o5(E).o5(G).o5(F).o5(E).o5(D).o5(C).done()
                .bar().o4(B).o5(C).o5(D).o5(E).o5(F).o5(D).o5(G.s()).o5(D).o5(B).o5(D).o5(C).o5(A).o5(F).o5(D).o4(B).o5(D).done()
                .bar().o4(G.s()).o4(B).o5(C).o4(A).o4(E).o4(A).o4(B).o4(G.s()).o4(A).o4(E).o4(C).o4(E).o3(QUARTER,A).done()
                .build(attacca());
    }

    // ── Left Hand ──

    MelodicPhrase buildLhSection1() {
        return newBuilder()
                .bar(SIXTEENTH).o2(EIGHTH,A).o3(QUARTER,A).o3(EIGHTH,G.s()).o3(A).o3(E).o3(A).o4(C).o3(B).o3(E).o3(B).o4(D).done()
                .bar(SIXTEENTH).o4(EIGHTH,C).o3(EIGHTH,A).o3(EIGHTH,G.s()).o3(EIGHTH,E).o3(A).o3(E).o3(A).o4(C).o3(B).o3(E).o3(B).o4(D).done()
                .bar(SIXTEENTH).o4(EIGHTH,C).o3(EIGHTH,A).o4(EIGHTH,C).o3(EIGHTH,A).o4(D).o3(A).o3(F).o3(A).o3(D).o3(F).o2(A).o3(C).done()
                .bar(SIXTEENTH).o2(EIGHTH,B).o3(EIGHTH,D).o3(EIGHTH,G).o3(EIGHTH,B).slurStart().o3(B).slurEnd().o3(G).o3(E).o3(G).o3(C).o3(E).o2(G).o2(B).done()
                .bar(SIXTEENTH).o2(EIGHTH,A).o3(EIGHTH,C).o3(D).o3(F).o2(B).o3(D).o2(EIGHTH,G).o2(EIGHTH,B).o3(C).o3(E).o2(A).o3(C).done()
                .bar(SIXTEENTH).o2(EIGHTH,F).o2(EIGHTH,D).o2(G).o3(G).o3(F).o3(G).o3(C).o3(G).o4(C).o4(E).o4(D).o3(G).o4(D).o4(F).done()
                .build(attacca());
    }

    MelodicPhrase buildLhSection2() {
        return newBuilder()
                .bar().o4(EIGHTH,E).o4(EIGHTH,C).o3(EIGHTH,B).o3(EIGHTH,G).o4(C).o3(G).o4(C).o4(E).o4(D).o3(G).o4(D).o4(F).done()
                .bar().o4(EIGHTH,E).o4(EIGHTH,C).r(QUARTER).r(SIXTEENTH).o4(G).o4(E).o4(G).o4(C).o4(E).o3(G).o3(B).done()
                .bar().o3(EIGHTH,A).o4(EIGHTH,C).o4(EIGHTH,E).o4(EIGHTH,G).o4(F.s()).o4(A).o4(D).o4(F.s()).o3(A).o4(D).o3(F.s()).o3(A).done()
                .bar().o3(EIGHTH,F).o3(EIGHTH,B).o4(EIGHTH,D).o4(EIGHTH,F.s()).o4(E).o4(G).o4(C).o4(E).o3(G).o4(C).o3(E).o3(G).done()
                .bar().o3(EIGHTH,F.s()).o3(EIGHTH,A).o3(EIGHTH,B).o4(EIGHTH,D.s()).r(SIXTEENTH).o4(E).o4(C).o4(E).o3(A).o4(C).o4(E).o4(G).done()
                .bar().o4(F.s()).o4(D).o3(B).o4(D).o3(G).o3(B).o4(D).o4(F.s()).o4(E).o4(C).o3(A).o4(C).o3(F.s()).o3(A).o4(EIGHTH,C).slurStart().done()
                .bar().o4(C).slurEnd().o3(B).o4(C).o3(A).o3(EIGHTH,B).o2(EIGHTH,B).o3(E).o4(E).o3(B).o3(G).o3(E).o2(B).o2(G).o2(B).done()
                .build(attacca());
    }

    MelodicPhrase buildLhSection3() {
        return newBuilder()
                .bar().o2(EIGHTH,E).o3(EIGHTH,E).o3(EIGHTH,G).o3(EIGHTH,B.f()).o3(EIGHTH,C.s()).r(EIGHTH).r(SIXTEENTH).o4(G).o4(F).o4(E).done()
                .bar().o4(EIGHTH,D).o3(EIGHTH,D).o3(EIGHTH,F).o3(EIGHTH,A.f()).o2(EIGHTH,B).r(EIGHTH).r(SIXTEENTH).o4(F).o4(E).o4(D).done()
                .bar().o4(EIGHTH,C).o3(EIGHTH,C).o3(EIGHTH,E).o3(EIGHTH,F.s()).o2(EIGHTH,A).r(EIGHTH).r(SIXTEENTH).o4(E).o4(E.s()).o4(C.s()).done()
                .bar().o3(EIGHTH,B).o2(EIGHTH,B).o3(EIGHTH,D).o3(EIGHTH,F).o2(EIGHTH,G).r(EIGHTH).r(SIXTEENTH).o4(D).o4(C).o3(B).done()
                .bar().o4(EIGHTH,C).o2(EIGHTH,A).o3(EIGHTH,G.s()).o3(EIGHTH,E).o3(A).o3(E).o3(A).o4(C).o3(B).o3(E).o3(B).o4(D).done()
                .bar().o4(C).o4(E).o4(A).o4(E).o4(C).o4(E).o3(A).o4(C).o3(F.s()).o3(A).o4(C).o3(A).o3(F.s()).o3(A).o3(D.s()).o3(F.s()).done()
                .bar().o3(EIGHTH,E).o3(EIGHTH,G.s()).o3(EIGHTH,B).o3(EIGHTH,G.s()).o3(EIGHTH,E).o2(EIGHTH,B).o2(EIGHTH,G.s()).o2(EIGHTH,E).done()
                .bar(EIGHTH).o2(A).o3(C).o3(E).o3(C).o2(A).o3(C).o2(D.s()).r(EIGHTH).done()
                .bar().r(SIXTEENTH).o3(B).o3(G.s()).o3(E).o3(D).o3(B).o3(G.s()).o3(D).o3(EIGHTH,C).o3(EIGHTH,E).o2(EIGHTH,G.s()).o3(EIGHTH,E).done()
                .bar(EIGHTH).o2(A).o3(F.s()).o2(B).o3(G.s()).o3(C).o3(A).o3(D).o3(B.f()).done()
                .bar(EIGHTH).o3(G.s()).o3(F).o3(D).o2(B).o2(G.s()).o2(A).o2(D).o2(E).done()
                .bar(EIGHTH).o2(F).o2(D.s()).o2(E).o3(E).o2(HALF,A).done()
                .build(attacca());
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ManualBachInvention13());
    }
}
