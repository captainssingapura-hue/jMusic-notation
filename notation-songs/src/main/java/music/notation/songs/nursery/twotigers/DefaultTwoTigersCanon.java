package music.notation.songs.nursery.twotigers;

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

    @Override
    public Piece create() {
        final var id = new TwoTigers();

        // ── 1-bar rest ─────────────────────────────────────────────────

        var rest1 = new MelodicPhrase(
                List.of(new RestNode(WHOLE)), attacca());

        // ── Compose voices from shared motifs ──────────────────────────
        // Each voice plays the verse (A A B B C C D D) twice.
        // Voices are staggered by 2 bars (1 section = 2 motif bars).

        // Voice 1 (Piano):   [verse] [verse] _ _
        var voice1 = Track.of("Voice 1", ACOUSTIC_GRAND_PIANO, List.of(
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D,
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D_END,
                rest1, rest1));

        // Voice 2 (Strings): _ [verse] [verse] _
        var voice2 = Track.of("Voice 2", STRING_ENSEMBLE_1, List.of(
                rest1,
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D,
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D_END,
                rest1));

        // Voice 3 (Flute):   _ _ [verse] [verse]
        var voice3 = Track.of("Voice 3", FLUTE, List.of(
                rest1, rest1,
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D,
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B, MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D_END));

        // ── Drums: 18 bars ─────────────────────────────────────────────

        var drumBar = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));

        var dpNodes = new ArrayList<PhraseNode>();
        dpNodes.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 18; i++) dpNodes.addAll(drumBar);

        var drums = Track.of("Drums", DRUM_KIT,
                List.of(new DrumPhrase(dpNodes, end())));

        return new Piece(id.title(), id.composer(),
                new KeySignature(C, Mode.MAJOR), new TimeSignature(4, 4),
                new Tempo(144, QUARTER),
                List.of(voice1, voice2, voice3, drums));
    }
}
