package music.notation.songs.nursery.twotigers;

import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;
import static music.notation.songs.nursery.twotigers.TwoTigersMotifs.MOTIF_A;
import static music.notation.songs.nursery.twotigers.TwoTigersMotifs.MOTIF_B;
import static music.notation.songs.nursery.twotigers.TwoTigersMotifs.MOTIF_C;
import static music.notation.songs.nursery.twotigers.TwoTigersMotifs.MOTIF_D;
import static music.notation.songs.nursery.twotigers.TwoTigersMotifs.MOTIF_D_END;

/**
 * Two Tigers Canon — 3-voice round using the same shared motifs.
 *
 * <p>Each voice enters 2 bars after the previous, playing the full
 * A A B B C C D D verse twice, offset in time. One bar of rest
 * pads each voice so all three tracks have equal duration.</p>
 */
public final class DefaultTwoTigersCanon implements PieceContentProvider<TwoTigers> {

    @Override
    public String subtitle() { return "Canon"; }

    private static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);
    // 18 bars total: 2 full verses (16 motifs = 16 bars) + 2 bars of padding
    // distributed across voices for the staggered entries / exits.
    private static final Duration TOTAL_DURATION = Duration.ofSixtyFourths(18 * 64);

    @Override
    public Piece create() {
        final var id = new TwoTigers();

        var rest1 = new MelodicPhrase(
                List.of(new RestNode(WHOLE)), attacca());

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Voice 1", ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Voice 2", STRING_ENSEMBLE_1),
                new TrackDecl.MusicTrackDecl("Voice 3", FLUTE),
                new TrackDecl.MusicTrackDecl("Drums",   DRUM_KIT)
        );

        // Each voice plays the verse (A A B B C C D D) twice; entries are
        // staggered by one bar. The canon runs as one continuous section
        // since every voice's timeline threads through without natural
        // intermediate boundaries.
        List<Phrase> voice1 = List.of(
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D,
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D_END,
                rest1, rest1);

        List<Phrase> voice2 = List.of(rest1,
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D,
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D_END,
                rest1);

        List<Phrase> voice3 = List.of(rest1, rest1,
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D,
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D_END);

        // Drums: continuous 18-bar groove underneath.
        var drumBar = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));
        var dpNodes = new ArrayList<PhraseNode>();
        dpNodes.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 18; i++) dpNodes.addAll(drumBar);
        var drums = new DrumPhrase(dpNodes, end());

        final var canon = Section.named("Canon")
                .duration(TOTAL_DURATION)
                .timeSignature(TS)
                .track("Voice 1", voice1)
                .track("Voice 2", voice2)
                .track("Voice 3", voice3)
                .track("Drums",   drums)
                .build();

        return Piece.ofSections(id.title(), id.composer(),
                KEY, TS, new Tempo(144, QUARTER),
                trackDecls,
                List.of(canon));
    }
}
