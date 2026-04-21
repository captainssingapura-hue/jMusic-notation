package music.notation.songs.nursery.twotigers;

import music.notation.chord.MajorTriad;
import music.notation.chord.MinorTriad;
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

    private static final Duration VERSE_DURATION = Duration.ofSixtyFourths(8 * 64);

    @Override
    public Piece create() {
        final var id = new TwoTigers();

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody", ACOUSTIC_GRAND_PIANO),
                new TrackDecl.MusicTrackDecl("Chords", ACOUSTIC_GUITAR_NYLON),
                new TrackDecl.MusicTrackDecl("Drums",  DRUM_KIT)
        );

        // ── Verse 1: C major ──────────────────────────────────────────

        var v1Melody = List.<Phrase>of(
                MOTIF_A, MOTIF_A, MOTIF_B, MOTIF_B,
                MOTIF_C, MOTIF_C, MOTIF_D, MOTIF_D_BREATH);

        var I  = chord(WHOLE, new MajorTriad(C, 3));
        var IV = chord(WHOLE, new MajorTriad(F, 3));
        var V  = chord(WHOLE, new MajorTriad(G, 3));
        var v1Chords = new ChordPhrase(
                List.of(I, I, IV, I, I, I, V, I), attacca());

        var v1Drums = drumVerse(Dynamic.MF, attacca());

        final var verse1 = Section.named("Verse 1 (C major)")
                .duration(VERSE_DURATION)
                .timeSignature(TS)
                .track("Melody", v1Melody)
                .track("Chords", v1Chords)
                .track("Drums",  v1Drums)
                .build();

        // ── Verse 2: D minor — same motifs, shifted ───────────────────

        var shift = new ShiftedPhrase.Factory(C_MAJOR, D_MINOR);
        var v2Melody = List.<Phrase>of(
                shift.apply(MOTIF_A), shift.apply(MOTIF_A),
                shift.apply(MOTIF_B), shift.apply(MOTIF_B),
                shift.apply(MOTIF_C), shift.apply(MOTIF_C),
                shift.apply(MOTIF_D), shift.apply(MOTIF_D_END));

        var i  = chord(WHOLE, new MinorTriad(D, 3));
        var iv = chord(WHOLE, new MinorTriad(G, 3));
        var v  = chord(WHOLE, new MinorTriad(A, 3));
        var v2Chords = new ChordPhrase(
                List.of(i, i, iv, i, i, i, v, i), end());

        var v2Drums = drumVerse(Dynamic.F, end());

        final var verse2 = Section.named("Verse 2 (D minor)")
                .duration(VERSE_DURATION)
                .timeSignature(TS)
                .track("Melody", v2Melody)
                .track("Chords", v2Chords)
                .track("Drums",  v2Drums)
                .build();

        return Piece.ofSections(id.title(), id.composer(),
                C_MAJOR, TS, new Tempo(132, QUARTER),
                trackDecls,
                List.of(verse1, verse2));
    }

    /** Drum line for one verse: a dynamic marker + 8 bars of the basic pattern. */
    private static DrumPhrase drumVerse(Dynamic dyn, PhraseMarking marking) {
        var drumBar = List.<PhraseNode>of(
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));
        var nodes = new ArrayList<PhraseNode>();
        nodes.add(new DynamicNode(dyn));
        for (int j = 0; j < 8; j++) nodes.addAll(drumBar);
        return new DrumPhrase(nodes, marking);
    }
}
