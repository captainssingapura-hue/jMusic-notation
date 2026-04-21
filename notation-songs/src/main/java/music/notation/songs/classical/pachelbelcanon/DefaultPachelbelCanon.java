package music.notation.songs.classical.pachelbelcanon;

import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.Ornament.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

public final class DefaultPachelbelCanon implements PieceContentProvider<PachelbelCanon> {

    @Override
    public Piece create() {
        final var id = new PachelbelCanon();

        // Pachelbel's Canon in D major, 4/4
        // Ground bass: D-A-Bm-F#m-G-D-G-A (each chord = half note, 4 measures per cycle)
        // Three violins play the same melody in canon, staggered by one cycle (4 measures)
        // 8 cycles total = 32 measures — full three-voice texture from cycle 3 onwards

        // --- Define the 8 melodic cycles (each = 4 measures = 16 quarter notes) ---

        // Cycle 1: The iconic descending theme (half notes)
        var c1 = List.<PhraseNode>of(
                ns(F,5,HALF), n(E,5,HALF), n(D,5,HALF), ns(C,5,HALF),
                n(B,4,HALF), n(A,4,HALF), n(B,4,HALF), ns(C,5,HALF));

        // Cycle 2: Quarter-note paired movement
        var c2 = List.<PhraseNode>of(
                n(D,5,QUARTER), ns(F,5,QUARTER), n(E,5,QUARTER), n(A,5,QUARTER),
                ns(F,5,QUARTER), n(D,5,QUARTER), n(E,5,QUARTER), ns(C,5,QUARTER),
                n(D,5,QUARTER), n(B,4,QUARTER), n(A,4,QUARTER), n(D,5,QUARTER),
                n(B,4,QUARTER), n(G,4,QUARTER), n(A,4,QUARTER), ns(C,5,QUARTER));

        // Cycle 3: Eighth-note arpeggiated figurations (ascending)
        var c3 = List.<PhraseNode>of(
                ns(F,5,EIGHTH), n(D,5,EIGHTH), n(A,5,EIGHTH), ns(F,5,EIGHTH),
                n(E,5,EIGHTH), ns(C,5,EIGHTH), n(A,5,EIGHTH), n(E,5,EIGHTH),
                n(D,5,EIGHTH), n(B,4,EIGHTH), ns(F,5,EIGHTH), n(D,5,EIGHTH),
                ns(C,5,EIGHTH), n(A,4,EIGHTH), n(E,5,EIGHTH), ns(C,5,EIGHTH),
                n(B,4,EIGHTH), n(G,4,EIGHTH), n(D,5,EIGHTH), n(B,4,EIGHTH),
                n(A,4,EIGHTH), ns(F,4,EIGHTH), n(D,5,EIGHTH), n(A,4,EIGHTH),
                n(B,4,EIGHTH), n(G,4,EIGHTH), n(D,5,EIGHTH), n(B,4,EIGHTH),
                ns(C,5,EIGHTH), n(E,5,EIGHTH), n(A,4,EIGHTH), ns(C,5,EIGHTH));

        // Cycle 4: Eighth-note descending scale runs
        var c4 = List.<PhraseNode>of(
                n(A,5,EIGHTH), ns(F,5,EIGHTH), n(D,5,EIGHTH), ns(F,5,EIGHTH),
                n(A,5,EIGHTH), n(E,5,EIGHTH), ns(C,5,EIGHTH), n(E,5,EIGHTH),
                ns(F,5,EIGHTH), n(D,5,EIGHTH), n(B,4,EIGHTH), n(D,5,EIGHTH),
                n(E,5,EIGHTH), ns(C,5,EIGHTH), n(A,4,EIGHTH), ns(C,5,EIGHTH),
                n(D,5,EIGHTH), n(B,4,EIGHTH), n(G,4,EIGHTH), n(B,4,EIGHTH),
                n(D,5,EIGHTH), n(A,4,EIGHTH), ns(F,4,EIGHTH), n(A,4,EIGHTH),
                n(D,5,EIGHTH), n(B,4,EIGHTH), n(G,4,EIGHTH), n(B,4,EIGHTH),
                n(E,5,EIGHTH), ns(C,5,EIGHTH), n(A,4,EIGHTH), ns(C,5,EIGHTH));

        // Cycle 5: Singing quarter-note melody (high register)
        var c5 = List.<PhraseNode>of(
                n(A,5,QUARTER), ns(F,5,QUARTER), n(E,5,QUARTER), n(A,5,QUARTER),
                ns(F,5,QUARTER), n(D,5,QUARTER), ns(C,5,QUARTER), n(E,5,QUARTER),
                n(D,5,QUARTER), ns(F,5,QUARTER), n(A,4,QUARTER), n(D,5,QUARTER),
                n(B,4,QUARTER), n(D,5,QUARTER), ns(C,5,QUARTER), n(A,4,QUARTER));

        // Cycle 6: Mixed rhythm — dotted quarters and eighths
        var c6 = List.<PhraseNode>of(
                ns(F,5,QUARTER), n(E,5,EIGHTH), n(D,5,EIGHTH), n(E,5,QUARTER), ns(C,5,EIGHTH), n(E,5,EIGHTH),
                n(D,5,QUARTER), n(B,4,EIGHTH), n(D,5,EIGHTH), ns(C,5,QUARTER), n(A,4,EIGHTH), ns(C,5,EIGHTH),
                n(B,4,QUARTER), n(G,4,EIGHTH), n(B,4,EIGHTH), n(A,4,QUARTER), ns(F,4,EIGHTH), n(A,4,EIGHTH),
                n(G,4,QUARTER), n(B,4,EIGHTH), n(D,5,EIGHTH), n(A,4,QUARTER), ns(C,5,EIGHTH), n(E,5,EIGHTH));

        // Cycle 7: Climactic eighth-note sequences
        var c7 = List.<PhraseNode>of(
                n(D,5,EIGHTH), ns(F,5,EIGHTH), n(A,5,EIGHTH), ns(F,5,EIGHTH),
                ns(C,5,EIGHTH), n(E,5,EIGHTH), n(A,5,EIGHTH), n(E,5,EIGHTH),
                n(B,4,EIGHTH), n(D,5,EIGHTH), ns(F,5,EIGHTH), n(D,5,EIGHTH),
                n(A,4,EIGHTH), ns(C,5,EIGHTH), n(E,5,EIGHTH), ns(C,5,EIGHTH),
                n(G,4,EIGHTH), n(B,4,EIGHTH), n(D,5,EIGHTH), n(B,4,EIGHTH),
                ns(F,4,EIGHTH), n(A,4,EIGHTH), n(D,5,EIGHTH), ns(F,5,EIGHTH),
                n(G,4,EIGHTH), n(B,4,EIGHTH), n(D,5,EIGHTH), n(G,5,EIGHTH),
                n(A,4,EIGHTH), ns(C,5,EIGHTH), n(E,5,EIGHTH), n(A,5,EIGHTH));

        // Cycle 8: Resolution — return to half-note theme with final ornament
        var c8 = List.<PhraseNode>of(
                ns(F,5,HALF), n(E,5,HALF), n(D,5,HALF), ns(C,5,HALF),
                n(B,4,HALF), n(A,4,HALF),
                n(G,4,QUARTER), n(A,4,QUARTER), orn(D,5,HALF, TURN));

        // --- 4-measure rest (one canon cycle of silence) ---
        var restCycle = new MelodicPhrase(
                List.of(new RestNode(Duration.of(WHOLE)), new RestNode(Duration.of(WHOLE)),
                        new RestNode(Duration.of(WHOLE)), new RestNode(Duration.of(WHOLE))),
                attacca());

        // --- Cello ground bass (repeats 8 times) ---
        // D-A-Bm-F#m-G-D-G-A, each note a half note
        var bassNotes = List.<PhraseNode>of(
                n(D,3,HALF), n(A,2,HALF), n(B,2,HALF), ns(F,2,HALF),
                n(G,2,HALF), n(D,3,HALF), n(G,2,HALF), n(A,2,HALF));

        // --- Per-cycle phrases for each track ---
        // Violin I:   c1  c2  c3  c4  c5  c6  c7  c8       (all 8 cycles)
        // Violin II:  --  c1  c2  c3  c4  c5  c6  c7       (enters 1 cycle later)
        // Violin III: --  --  c1  c2  c3  c4  c5  c6       (enters 2 cycles later)

        var v1 = List.<Phrase>of(
                mp(Dynamic.MP, c1, attacca()),
                new MelodicPhrase(c2, attacca()),
                mp(Dynamic.MF, c3, attacca()),
                new MelodicPhrase(c4, attacca()),
                new MelodicPhrase(c5, attacca()),
                new MelodicPhrase(c6, attacca()),
                mp(Dynamic.F, c7, attacca()),
                mp(Dynamic.P, c8, end()));

        var v2 = List.<Phrase>of(
                restCycle,
                mp(Dynamic.MP, c1, attacca()),
                new MelodicPhrase(c2, attacca()),
                mp(Dynamic.MF, c3, attacca()),
                new MelodicPhrase(c4, attacca()),
                new MelodicPhrase(c5, attacca()),
                new MelodicPhrase(c6, attacca()),
                mp(Dynamic.F, c7, end()));

        var v3 = List.<Phrase>of(
                restCycle, restCycle,
                mp(Dynamic.MP, c1, attacca()),
                new MelodicPhrase(c2, attacca()),
                mp(Dynamic.MF, c3, attacca()),
                new MelodicPhrase(c4, attacca()),
                new MelodicPhrase(c5, attacca()),
                new MelodicPhrase(c6, end()));

        var cello = List.<Phrase>of(
                mp(Dynamic.MF, bassNotes, attacca()),
                new MelodicPhrase(bassNotes, attacca()),
                new MelodicPhrase(bassNotes, attacca()),
                new MelodicPhrase(bassNotes, attacca()),
                new MelodicPhrase(bassNotes, attacca()),
                new MelodicPhrase(bassNotes, attacca()),
                new MelodicPhrase(bassNotes, attacca()),
                new MelodicPhrase(bassNotes, end()));

        // --- Sections: 8 cycles × 4 bars × 4/4 = 256/64 each ---
        final var KEY = new KeySignature(D, Mode.MAJOR);
        final var TS = new TimeSignature(4, 4);
        final var CYCLE_DURATION = Duration.ofSixtyFourths(4 * 64);

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Violin I",   VIOLIN),
                new TrackDecl.MusicTrackDecl("Violin II",  VIOLIN),
                new TrackDecl.MusicTrackDecl("Violin III", VIOLIN),
                new TrackDecl.MusicTrackDecl("Cello",      CELLO)
        );

        final var sections = new ArrayList<Section>();
        for (int i = 0; i < 8; i++) {
            sections.add(Section.named("Cycle " + (i + 1))
                    .duration(CYCLE_DURATION)
                    .timeSignature(TS)
                    .track("Violin I",   v1.get(i))
                    .track("Violin II",  v2.get(i))
                    .track("Violin III", v3.get(i))
                    .track("Cello",      cello.get(i))
                    .build());
        }

        return Piece.ofSections(id.title(), id.composer(), KEY, TS,
                new Tempo(66, QUARTER), trackDecls, sections);
    }

    /** Create a MelodicPhrase with a dynamic prepended. */
    private static MelodicPhrase mp(Dynamic dyn, List<PhraseNode> notes, PhraseMarking marking) {
        var nodes = new ArrayList<PhraseNode>();
        nodes.add(new DynamicNode(dyn));
        nodes.addAll(notes);
        return new MelodicPhrase(nodes, marking);
    }
}
