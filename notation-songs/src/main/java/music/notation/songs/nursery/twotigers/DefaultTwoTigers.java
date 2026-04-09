package music.notation.songs.nursery.twotigers;

import music.notation.chord.MajorTriad;
import music.notation.chord.MinorTriad;
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
import static music.notation.songs.nursery.twotigers.TwoTigersMotifs.MOTIF_D_BREATH;
import static music.notation.songs.nursery.twotigers.TwoTigersMotifs.MOTIF_D_END;

/**
 * Two Tigers (两只老虎) — demonstrates <em>composition</em>.
 *
 * <p>The entire piece is built from the 4 single-bar motifs in
 * {@link TwoTigersMotifs}:
 * <pre>
 *   A  = C D E C          "两只老虎"
 *   B  = E F G –          "跑得快"
 *   C_ = G̲A̲ G̲F̲ E C       "一只没有眼睛"
 *   D  = C G₄ C –         "真奇怪"
 * </pre>
 *
 * <p>Composition rules:
 * <ul>
 *   <li><strong>Repetition</strong> — each motif appears twice per section</li>
 *   <li><strong>Sequencing</strong> — sections A B C D form a verse</li>
 *   <li><strong>Transposition</strong> — {@link ShiftedPhrase} shifts the
 *       entire verse from C major to D minor</li>
 * </ul>
 *
 * <p>No musical content is duplicated — every bar in the piece is a
 * reference to, or a transformation of, one of the 4 motifs.</p>
 */
public final class DefaultTwoTigers implements PieceContentProvider<TwoTigers> {

    private static final KeySignature C_MAJOR = new KeySignature(C, Mode.MAJOR);
    private static final KeySignature D_MINOR = new KeySignature(D, Mode.MINOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Override
    public Piece create() {
        final var id = new TwoTigers();

        // ── Compose melody from shared motifs ──────────────────────────

        var shift = new ShiftedPhrase.Factory(C_MAJOR, D_MINOR);

        var melody = Track.of("Melody", ACOUSTIC_GRAND_PIANO, List.of(
                // Verse 1 (C major)
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B,
                MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D_BREATH,
                // Verse 2 (D minor) — same motifs, shifted
                shift.apply(MOTIF_A), shift.apply(MOTIF_A),
                shift.apply(MOTIF_B), shift.apply(MOTIF_B),
                shift.apply(MOTIF_C), shift.apply(MOTIF_C),
                shift.apply(MOTIF_D), shift.apply(MOTIF_D_END)));

        // ── Chords ─────────────────────────────────────────────────────

        var I  = chord(WHOLE, new MajorTriad(C, 3));
        var IV = chord(WHOLE, new MajorTriad(F, 3));
        var V  = chord(WHOLE, new MajorTriad(G, 3));

        var cMajChords = new ChordPhrase(
                List.of(I, I, IV, I, I, I, V, I), attacca());

        var i  = chord(WHOLE, new MinorTriad(D, 3));
        var iv = chord(WHOLE, new MinorTriad(G, 3));
        var v  = chord(WHOLE, new MinorTriad(A, 3));

        var dMinChords = new ChordPhrase(
                List.of(i, i, iv, i, i, i, v, i), end());

        var chords = Track.of("Chords", ACOUSTIC_GUITAR_NYLON,
                List.of(cMajChords, dMinChords));

        // ── Drums ──────────────────────────────────────────────────────

        var drumBar = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));

        var dpNodes = new ArrayList<PhraseNode>();
        dpNodes.add(new DynamicNode(Dynamic.MF));
        for (int j = 0; j < 8; j++) dpNodes.addAll(drumBar);
        var drumPhrase1 = new DrumPhrase(dpNodes, attacca());

        var dpNodes2 = new ArrayList<PhraseNode>();
        dpNodes2.add(new DynamicNode(Dynamic.F));
        for (int j = 0; j < 8; j++) dpNodes2.addAll(drumBar);
        var drumPhrase2 = new DrumPhrase(dpNodes2, end());

        var drums = Track.of("Drums", DRUM_KIT,
                List.of(drumPhrase1, drumPhrase2));

        return new Piece(id.title(), id.composer(),
                C_MAJOR, TS, new Tempo(132, QUARTER),
                List.of(melody, chords, drums));
    }
}
