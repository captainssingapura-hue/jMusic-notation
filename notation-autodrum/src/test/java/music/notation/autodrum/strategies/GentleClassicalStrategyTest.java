package music.notation.autodrum.strategies;

import music.notation.autodrum.DrumStrategies;
import music.notation.autodrum.DrumStrategy;
import music.notation.duration.BarDuration;
import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.Instrument;
import music.notation.event.PercussionSound;
import music.notation.phrase.Bar;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.SimplePitchNode;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.structure.DrumTrack;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
import music.notation.structure.Piece;
import music.notation.structure.Tempo;
import music.notation.structure.TimeSignature;
import music.notation.structure.Track;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GentleClassicalStrategyTest {

    private static final BarDuration BD_4_4 = new BarDuration(4, BaseValue.QUARTER);
    private static final BarDuration BD_3_4 = new BarDuration(3, BaseValue.QUARTER);
    private static final BarDuration BD_6_8 = new BarDuration(6, BaseValue.EIGHTH);

    private static Bar c4Bar(BarDuration bd) {
        // A bar containing a single C4 note that fills the bar.
        return Bar.of(bd, new SimplePitchNode(
                Pitch.of(NoteName.C, 4),
                Duration.ofSixtyFourths(bd.sixtyFourths()),
                Optional.empty(),
                List.of(),
                false,
                false));
    }

    private static Bar restBar(BarDuration bd) {
        return Bar.of(bd, new RestNode(Duration.ofSixtyFourths(bd.sixtyFourths())));
    }

    private static Piece pieceWith(BarDuration bd, List<Bar> bars) {
        var melody = new MelodicTrack("Melody", Instrument.ACOUSTIC_GRAND_PIANO, Phrase.of(bars));
        return new Piece("Test", "Test",
                new KeySignature(NoteName.C, Mode.MAJOR),
                new TimeSignature(bd.unitCount(), 4 /* approximation; 6/8 wouldn't reach this test */),
                new Tempo(120, BaseValue.QUARTER),
                List.of(melody));
    }

    @Test
    void fourFourGeneratesKickStickKickStickPerBar() {
        var piece = pieceWith(BD_4_4, List.of(c4Bar(BD_4_4), c4Bar(BD_4_4)));
        DrumTrack drums = DrumStrategies.GENTLE_CLASSICAL.generate(piece).orElseThrow();

        assertEquals("Auto Drum", drums.name());
        assertEquals(2, drums.bars().size());

        Bar firstBar = drums.bars().get(0);
        List<PhraseNode> nodes = firstBar.nodes();
        assertEquals(4, nodes.size());
        assertEquals(PercussionSound.BASS_DRUM,  ((PercussionNote) nodes.get(0)).sound());
        assertEquals(PercussionSound.SIDE_STICK, ((PercussionNote) nodes.get(1)).sound());
        assertEquals(PercussionSound.BASS_DRUM,  ((PercussionNote) nodes.get(2)).sound());
        assertEquals(PercussionSound.SIDE_STICK, ((PercussionNote) nodes.get(3)).sound());
    }

    @Test
    void threeFourGeneratesKickStickStick() {
        var piece = pieceWith(BD_3_4, List.of(c4Bar(BD_3_4)));
        DrumTrack drums = DrumStrategies.GENTLE_CLASSICAL.generate(piece).orElseThrow();
        List<PhraseNode> nodes = drums.bars().get(0).nodes();
        assertEquals(3, nodes.size());
        assertEquals(PercussionSound.BASS_DRUM,  ((PercussionNote) nodes.get(0)).sound());
        assertEquals(PercussionSound.SIDE_STICK, ((PercussionNote) nodes.get(1)).sound());
        assertEquals(PercussionSound.SIDE_STICK, ((PercussionNote) nodes.get(2)).sound());
    }

    @Test
    void silentSourceBarYieldsKickPlusRests() {
        var piece = pieceWith(BD_4_4, List.of(c4Bar(BD_4_4), restBar(BD_4_4)));
        DrumTrack drums = DrumStrategies.GENTLE_CLASSICAL.generate(piece).orElseThrow();

        // Bar 0 has a note in the source → full pattern (4 nodes).
        assertEquals(4, drums.bars().get(0).nodes().size());

        // Bar 1 is a rest in the source → quiet pattern: kick + 3 rests.
        List<PhraseNode> quiet = drums.bars().get(1).nodes();
        assertEquals(4, quiet.size());
        assertEquals(PercussionSound.BASS_DRUM, ((PercussionNote) quiet.get(0)).sound());
        for (int i = 1; i < 4; i++) {
            assertTrue(quiet.get(i) instanceof RestNode, "expected rest at position " + i);
        }
    }

    @Test
    void skipsWhenSourceAlreadyHasDrumTrack() {
        var melody = new MelodicTrack("Melody", Instrument.ACOUSTIC_GRAND_PIANO,
                Phrase.of(List.of(c4Bar(BD_4_4))));
        var existingDrums = DrumTrack.of("Drums", c4DrumBar());
        var piece = new Piece("Test", "Test",
                new KeySignature(NoteName.C, Mode.MAJOR),
                new TimeSignature(4, 4),
                new Tempo(120, BaseValue.QUARTER),
                List.of(melody, existingDrums));

        Optional<DrumTrack> generated = DrumStrategies.GENTLE_CLASSICAL.generate(piece);
        assertTrue(generated.isEmpty(), "should skip when source already has a DrumTrack");
        assertFalse(DrumStrategies.GENTLE_CLASSICAL.appliesTo(piece));
    }

    @Test
    void pickupMeasureFollowedByStandardBarsStillProducesDrums() {
        // Bar 0 is a 1-eighth pickup; bars 1+ are 3/8. The pickup gets a
        // silent rest bar; subsequent bars receive the kick/stick pattern.
        var pickupBd = new BarDuration(1, BaseValue.EIGHTH);
        var threeEightBd = new BarDuration(3, BaseValue.EIGHTH);
        var pickup = Bar.of(pickupBd, new SimplePitchNode(
                Pitch.of(NoteName.C, 4),
                Duration.of(BaseValue.EIGHTH),
                Optional.empty(), List.of(), false, false));
        var fullBar = Bar.of(threeEightBd, new SimplePitchNode(
                Pitch.of(NoteName.C, 4),
                Duration.ofSixtyFourths(threeEightBd.sixtyFourths()),
                Optional.empty(), List.of(), false, false));

        var melody = new MelodicTrack("Melody", Instrument.ACOUSTIC_GRAND_PIANO,
                Phrase.of(List.of(pickup, fullBar, fullBar)));
        var piece = new Piece("Pickup", "Test",
                new KeySignature(NoteName.A, Mode.MINOR),
                new TimeSignature(3, 8),
                new Tempo(120, BaseValue.EIGHTH),
                List.of(melody));

        DrumTrack drums = DrumStrategies.GENTLE_CLASSICAL.generate(piece).orElseThrow();
        assertEquals(3, drums.bars().size());
        // Pickup bar — single-unit fallback (kick on the down-beat with no
        // remainder since the bar is one eighth long).
        assertEquals(1, drums.bars().get(0).nodes().size());
        assertEquals(PercussionSound.BASS_DRUM,
                ((PercussionNote) drums.bars().get(0).nodes().get(0)).sound());
        // Standard bars — three eighth-note hits.
        for (int i = 1; i < 3; i++) {
            List<PhraseNode> nodes = drums.bars().get(i).nodes();
            assertEquals(3, nodes.size(),
                    "expected kick/stick/stick on bar " + i);
            assertEquals(PercussionSound.BASS_DRUM,
                    ((PercussionNote) nodes.get(0)).sound());
        }
    }

    @Test
    void unsupportedTimeSignatureFallsBackToPulse() {
        // 5/4 has no native pattern in this strategy — graceful fallback
        // emits a kick on beat 1 + rests so the user still hears drums.
        var bd = new BarDuration(5, BaseValue.QUARTER);
        var piece = pieceWith(bd, List.of(c4Bar(bd)));
        DrumTrack drums = DrumStrategies.GENTLE_CLASSICAL.generate(piece).orElseThrow();
        assertEquals(1, drums.bars().size());
        // 5 nodes: kick on beat 1, then 4 rests.
        var nodes = drums.bars().get(0).nodes();
        assertEquals(5, nodes.size());
        assertEquals(PercussionSound.BASS_DRUM,
                ((PercussionNote) nodes.get(0)).sound());
        for (int i = 1; i < 5; i++) {
            assertTrue(nodes.get(i) instanceof RestNode);
        }
    }

    @Test
    void noStrategyAlwaysReturnsEmpty() {
        var piece = pieceWith(BD_4_4, List.of(c4Bar(BD_4_4)));
        DrumStrategy none = DrumStrategies.NONE;
        assertTrue(none.generate(piece).isEmpty());
        assertFalse(none.appliesTo(piece));
    }

    private static Bar c4DrumBar() {
        return Bar.of(BD_4_4,
                new PercussionNote(PercussionSound.BASS_DRUM, Duration.of(BaseValue.QUARTER)),
                new PercussionNote(PercussionSound.BASS_DRUM, Duration.of(BaseValue.QUARTER)),
                new PercussionNote(PercussionSound.BASS_DRUM, Duration.of(BaseValue.QUARTER)),
                new PercussionNote(PercussionSound.BASS_DRUM, Duration.of(BaseValue.QUARTER)));
    }
}
