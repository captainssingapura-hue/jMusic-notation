package music.notation.songs.nursery.twinklestar;

import music.notation.chord.MajorTriad;
import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.Ornament.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

public final class DefaultTwinkleStar implements PieceContentProvider<TwinkleStar> {

    @Override
    public Piece create() {
        final var id = new TwinkleStar();

        // --- Melody ---
        var m1a = new MelodicPhrase(
                List.of(n(C,5,QUARTER), n(C,5,QUARTER), n(G,5,QUARTER), n(G,5,QUARTER)),
                attacca());
        var m1b = new MelodicPhrase(
                List.of(n(A,5,QUARTER), n(A,5,QUARTER), orn(G,5,HALF, MORDENT)),
                breath());

        var phrase1 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.MF), new SubPhrase(m1a), new SubPhrase(m1b)),
                breath());
        var phrase2 = new MelodicPhrase(
                List.of(n(F,5,QUARTER), n(F,5,QUARTER), n(E,5,QUARTER), n(E,5,QUARTER),
                        n(D,5,QUARTER), n(D,5,QUARTER), orn(C,5,HALF, TURN)),
                breath());
        var phrase3 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.F),
                        PitchNode.graced(List.of(new GraceNote(p(A,5), false)), QUARTER, List.of(p(G,5))),
                        n(G,5,QUARTER), n(F,5,QUARTER), n(F,5,QUARTER),
                        orn(E,5,QUARTER, TRILL), n(E,5,QUARTER), n(D,5,HALF)),
                breath());
        var phrase4 = new MelodicPhrase(
                List.of(PitchNode.graced(List.of(new GraceNote(p(A,5), true)), QUARTER, List.of(p(G,5))),
                        n(G,5,QUARTER), n(F,5,QUARTER), n(F,5,QUARTER),
                        n(E,5,QUARTER), n(E,5,QUARTER), orn(D,5,HALF, MORDENT)),
                breath());
        var m5b = new MelodicPhrase(
                List.of(n(A,5,QUARTER), n(A,5,QUARTER), orn(G,5,HALF, TREMOLO)),
                breath());
        var phrase5 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.MP), new SubPhrase(m1a), new SubPhrase(m5b)),
                breath());
        var phrase6 = new MelodicPhrase(
                List.of(new DynamicNode(Dynamic.P),
                        n(F,5,QUARTER), n(F,5,QUARTER), n(E,5,QUARTER), n(E,5,QUARTER),
                        n(D,5,QUARTER), n(D,5,QUARTER), orn(C,5,HALF, LOWER_MORDENT)),
                end());

        // --- Chords ---
        final var CMaj = new MajorTriad(C, 3);
        final var FMaj = new MajorTriad(F, 3);
        final var GMaj = new MajorTriad(G, 3);
        var I  = chord(HALF, CMaj);
        var IV = chord(HALF, FMaj);
        var V  = chord(HALF, GMaj);
        var cm = new PhraseMarking(PhraseConnection.ATTACCA, false);
        var ce = new PhraseMarking(PhraseConnection.CAESURA, false);

        // --- Drums (gentle 4/4: kick-hat-snare-hat, 2 bars per phrase) ---
        var dp = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));
        var dpFirst = new java.util.ArrayList<PhraseNode>();
        dpFirst.add(new DynamicNode(Dynamic.MP));
        dpFirst.addAll(dp);

        // ── Sections: 6 lyric lines × 2 bars each ────────────────────
        final var KEY = new KeySignature(C, Mode.MAJOR);
        final var TS = new TimeSignature(4, 4);
        final var SECTION_DURATION = Duration.ofSixtyFourths(2 * 64);

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", FLUTE),
                new TrackDecl.MusicTrackDecl("Chords", ACOUSTIC_GUITAR_NYLON),
                new TrackDecl.MusicTrackDecl("Drums",  DRUM_KIT)
        );

        final var sections = List.of(
                section("Twinkle twinkle little star",   SECTION_DURATION, TS,
                        phrase1, new ChordPhrase(List.of(I, I, IV, I), cm), new DrumPhrase(dpFirst, attacca())),
                section("How I wonder what you are",     SECTION_DURATION, TS,
                        phrase2, new ChordPhrase(List.of(IV, I, V, I), cm), new DrumPhrase(dp, attacca())),
                section("Up above the world so high",    SECTION_DURATION, TS,
                        phrase3, new ChordPhrase(List.of(I, V, I, V), cm), new DrumPhrase(dp, attacca())),
                section("Like a diamond in the sky",     SECTION_DURATION, TS,
                        phrase4, new ChordPhrase(List.of(I, V, I, V), cm), new DrumPhrase(dp, attacca())),
                section("Twinkle twinkle (reprise)",     SECTION_DURATION, TS,
                        phrase5, new ChordPhrase(List.of(I, I, IV, I), cm), new DrumPhrase(dp, attacca())),
                section("How I wonder (reprise)",        SECTION_DURATION, TS,
                        phrase6, new ChordPhrase(List.of(IV, I, V, I), ce), new DrumPhrase(dp, end()))
        );

        return Piece.ofSections(id.title(), id.composer(), KEY, TS,
                new Tempo(120, QUARTER), trackDecls, sections);
    }

    private static Section section(String name, Duration duration, TimeSignature ts,
                                   Phrase melody, Phrase chords, Phrase drums) {
        return Section.named(name)
                .duration(duration)
                .timeSignature(ts)
                .track("Melody", melody)
                .track("Chords", chords)
                .track("Drums",  drums)
                .build();
    }
}
