package music.notation.songs;

import music.notation.songs.anthem.internationale.Internationale;
import music.notation.songs.folk.tianheihei.TianHeiHei;
import music.notation.structure.MelodicTrack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end verification that the aux DSL is wired through Phase 7.3:
 * songs that author aux via {@code .aux(...)} produce non-empty
 * {@link MelodicTrack#auxBars()} after going through the full
 * builder → MelodicPhrase → LeafPhrase → MelodicTrack pipeline.
 */
class AuxRestoredTest {

    @Test
    void internationale_harmonyTrackHasAuxContent() {
        var piece = PieceLibrary.get(Internationale.class);
        long melodicTracksWithAux = piece.tracks().stream()
                .filter(t -> t instanceof MelodicTrack)
                .map(t -> (MelodicTrack) t)
                .filter(mt -> !mt.auxBars().isEmpty())
                .count();
        assertTrue(melodicTracksWithAux >= 1,
                "Internationale should have at least one track carrying aux voices");
    }

    @Test
    void tianHeiHei_someTrackHasAuxContent() {
        var piece = PieceLibrary.get(TianHeiHei.class);
        long melodicTracksWithAux = piece.tracks().stream()
                .filter(t -> t instanceof MelodicTrack)
                .map(t -> (MelodicTrack) t)
                .filter(mt -> !mt.auxBars().isEmpty())
                .count();
        assertTrue(melodicTracksWithAux >= 1,
                "TianHeiHei should have at least one track carrying aux voices");
    }

    @Test
    void auxBarsAreLengthAlignedWithPrimary() {
        var piece = PieceLibrary.get(Internationale.class);
        for (var track : piece.tracks()) {
            if (!(track instanceof MelodicTrack mt)) continue;
            int primaryLen = mt.bars().size();
            for (var entry : mt.auxBars().entrySet()) {
                assertEquals(primaryLen, entry.getValue().size(),
                        "aux voice '" + entry.getKey() + "' on track '"
                                + mt.name() + "' must match primary length");
            }
        }
    }
}
