package music.notation.songs.traditional.happybirthday;

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
 * "Happy Birthday" — Chopin-style romantic nocturne arrangement.
 *
 * <p>Slow, expressive (76 BPM, lento-ish). The right-hand melody sings above
 * a wide-spanning left-hand arpeggio pattern reminiscent of Chopin nocturnes
 * (bass quarter + 4 flowing eighths per bar). Grace notes decorate the
 * melodic peaks. Tempo and texture invite rubato in live performance.</p>
 */
public final class ChopinHappyBirthday implements PieceContentProvider<HappyBirthday> {

    private static final KeySignature KEY = DefaultHappyBirthday.KEY;   // C major
    private static final TimeSignature TS = DefaultHappyBirthday.TS;    // 3/4

    private StaffPhraseBuilderTyped b() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Chopin (Nocturne)"; }

    private static final Duration SONG_DURATION = Duration.ofSixtyFourths(12 * 48);

    @Override
    public Piece create() {
        final var id = new HappyBirthday();

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody",        ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Accompaniment", ACOUSTIC_GRAND_PIANO)
        );

        final var song = Section.named("Happy Birthday (Chopin)")
                .duration(SONG_DURATION)
                .timeSignature(TS)
                .track("Melody",        List.of(line1(), line2(), line3(), line4()))
                .track("Accompaniment", nocturne())
                .build();

        return Piece.ofSections(id.title(), id.composer(), KEY, TS,
                new Tempo(76, QUARTER),
                trackDecls,
                List.of(song));
    }

    /** Line 1: "Happy birthday to you" — soft lyrical entry, grace into "you". */
    MelodicPhrase line1() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G).done()
                .bar(QUARTER).o4(A).o4(G).o5(C).done()
                // Grace note C5 leaning into the sustained B4 — Chopin's "sigh" gesture.
                .bar().grace(C, 5).main(HALF, 4, B).pad(QUARTER).done()
                .build(elision());
    }

    /** Line 2: "Happy birthday to you" — small appoggiatura on the higher C5. */
    MelodicPhrase line2() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G).done()
                .bar(QUARTER).o4(A).o4(G).o5(D).done()
                // Appoggiatura-style grace leaning into C5.
                .bar().grace(D, 5).main(HALF, 5, C).pad(QUARTER).done()
                .build(elision());
    }

    /** Line 3: "Happy birthday dear NAME" — tender double-grace into NAME. */
    MelodicPhrase line3() {
        return b()
                .pickup(EIGHTH).o4(G).o4(G).done()
                .bar(QUARTER).o5(G).o5(E).o5(C).done()
                // Two grace notes (D-C) caressing into the held B4.
                .bar().grace(D, 5).grace(C, 5).main(HALF, 4, B).pad(QUARTER).done()
                .build(elision());
    }

    /** Line 4: "Happy birthday to you" — unadorned resolution, tenuto held C5. */
    MelodicPhrase line4() {
        return b()
                .pickup(EIGHTH).o5(F).o5(F).done()
                .bar(QUARTER).o5(E).o5(C).o5(D).done()
                .bar().o5(HALF.dot(), C).done()
                .build(end());
    }

    // ── Left Hand: nocturne arpeggios (bass quarter + 4 flowing eighths) ──

    /** 12-bar nocturne LH arpeggios matching the RH phrase structure. */
    private MelodicPhrase nocturne() {
        var bb = b();
        //   1: C   2: C   3: G7    (line 1)
        //   4: G7  5: C   6: C     (line 2)
        //   7: C   8: C   9: F     (line 3, subdominant on "dear NAME")
        //  10: F  11: C  12: C     (line 4, final resolution)
        bb = barC(bb);  bb = barC(bb);  bb = barG7(bb);
        bb = barG7(bb); bb = barC(bb);  bb = barC(bb);
        bb = barC(bb);  bb = barC(bb);  bb = barF(bb);
        bb = barF(bb);  bb = barC(bb);  bb = barCFinal(bb);
        return bb.build(end());
    }

    /** C-major nocturne bar: low C bass, then E-G-C-G arpeggio (fills upper register). */
    private static StaffPhraseBuilderTyped barC(StaffPhraseBuilderTyped bb) {
        return bb.bar(EIGHTH).o2(QUARTER, C).o3(E).o3(G).o4(C).o3(G).done();
    }

    /** G7 nocturne bar: low G bass, then B-D-F-D arpeggio (brings in the ♭7 colour). */
    private static StaffPhraseBuilderTyped barG7(StaffPhraseBuilderTyped bb) {
        return bb.bar(EIGHTH).o2(QUARTER, G).o2(B).o3(D).o3(F).o3(D).done();
    }

    /** F-major nocturne bar: low F bass, A-C-F-C arpeggio (warm subdominant colour). */
    private static StaffPhraseBuilderTyped barF(StaffPhraseBuilderTyped bb) {
        return bb.bar(EIGHTH).o2(QUARTER, F).o2(A).o3(C).o3(F).o3(C).done();
    }

    /** Final C: spacious dotted-half bass + mid-register triad, letting the sonority ring. */
    private static StaffPhraseBuilderTyped barCFinal(StaffPhraseBuilderTyped bb) {
        return bb.bar().o2(HALF.dot(), C, E, G).done();   // wide-voiced C major chord, full-bar hold
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new ChopinHappyBirthday());
    }
}
