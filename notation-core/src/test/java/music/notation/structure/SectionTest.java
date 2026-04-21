package music.notation.structure;

import music.notation.duration.Duration;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseConnection;
import music.notation.phrase.PhraseMarking;
import music.notation.phrase.StaffPhraseBuilderTyped;
import music.notation.phrase.VoidPhrase;
import music.notation.pitch.NoteName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static music.notation.duration.BaseValue.*;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link Section} record and its {@link Section.Builder},
 * covering duration alignment, builder semantics, silent fills, and
 * error locality.
 */
class SectionTest {

    private static final KeySignature KEY = new KeySignature(NoteName.C, Mode.MAJOR);
    private static final TimeSignature TS_44 = new TimeSignature(4, 4);

    private static PhraseMarking attacca() {
        return new PhraseMarking(PhraseConnection.ATTACCA, false);
    }

    private static MelodicPhrase oneBar() {
        return StaffPhraseBuilderTyped.in(KEY, TS_44, QUARTER)
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .build(attacca());
    }

    private static MelodicPhrase twoBars() {
        return StaffPhraseBuilderTyped.in(KEY, TS_44, QUARTER)
                .bar().o4(C).o4(D).o4(E).o4(F).done()
                .bar().o4(G).o4(A).o4(B).o5(C).done()
                .build(attacca());
    }

    // ── Basic construction ────────────────────────────────────────────

    @Test
    void sectionBuildsWithMatchingDuration() {
        Section s = new Section(
                "Intro",
                Duration.ofSixtyFourths(64),
                java.util.Optional.empty(),
                Map.of(
                        "Melody", SectionTrack.of(oneBar()),
                        "Bass",   SectionTrack.of(oneBar())
                )
        );

        assertEquals("Intro", s.name());
        assertEquals(64, s.duration().sixtyFourths());
        assertEquals(2, s.tracks().size());
        assertTrue(s.keyOverride().isEmpty());
    }

    @Test
    void sectionRejectsBlankName() {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Section(
                " ",
                Duration.ofSixtyFourths(64),
                java.util.Optional.empty(),
                Map.of("Melody", SectionTrack.of(oneBar()))
        ));
        assertTrue(ex.getMessage().toLowerCase().contains("non-blank"));
    }

    @Test
    void sectionRejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> new Section(
                "X",
                Duration.ofSixtyFourths(0),
                java.util.Optional.empty(),
                Map.of("Melody", SectionTrack.of(oneBar()))
        ));
    }

    // ── Duration alignment ────────────────────────────────────────────

    @Test
    void sectionRejectsTrackDurationMismatch() {
        // Intro declares 128/64 (2 bars) but Melody has only 1 bar = 64/64
        var ex = assertThrows(IllegalArgumentException.class, () -> new Section(
                "Intro",
                Duration.ofSixtyFourths(128),
                java.util.Optional.empty(),
                Map.of(
                        "Melody", SectionTrack.of(oneBar()),
                        "Bass",   SectionTrack.of(twoBars())
                )
        ));
        assertTrue(ex.getMessage().contains("Intro"));
        assertTrue(ex.getMessage().contains("Melody"));
        assertTrue(ex.getMessage().contains("64/64"));
        assertTrue(ex.getMessage().contains("128/64"));
    }

    @Test
    void sectionRejectsAuxDurationMismatch() {
        // Aux track present but only 1 bar — should match section duration 128/64
        SectionTrack melody = new SectionTrack(List.of(twoBars()), List.of(
                SectionTrack.of(oneBar())
        ));
        var ex = assertThrows(IllegalArgumentException.class, () -> new Section(
                "Verse",
                Duration.ofSixtyFourths(128),
                java.util.Optional.empty(),
                Map.of("Melody", melody)
        ));
        assertTrue(ex.getMessage().contains("aux[0]"));
    }

    // ── Builder basics ────────────────────────────────────────────────

    @Test
    void builderConstructsSection() {
        Section s = Section.named("Verse")
                .duration(Duration.ofSixtyFourths(128))
                .timeSignature(TS_44)
                .track("Melody", twoBars())
                .track("Bass",   twoBars())
                .build();

        assertEquals("Verse", s.name());
        assertEquals(2, s.tracks().size());
        assertTrue(s.tracks().containsKey("Melody"));
        assertTrue(s.tracks().containsKey("Bass"));
    }

    @Test
    void builderSilentFillsVoidPhrase() {
        Section s = Section.named("Intro")
                .duration(Duration.ofSixtyFourths(256))   // 4 bars
                .timeSignature(TS_44)
                .track("Melody", StaffPhraseBuilderTyped.in(KEY, TS_44, QUARTER)
                        .bar().o4(C).o4(D).o4(E).o4(F).done()
                        .bar().o4(G).o4(A).o4(B).o5(C).done()
                        .bar().o5(D).o5(E).o5(F).o5(G).done()
                        .bar().o5(A).o5(B).o5(C.higher(1)).o5(D.higher(1)).done()
                        .build(attacca()))
                .silent("Bass")
                .silent("Drums")
                .build();

        // Silent tracks get a single full-length VoidPhrase.
        var bass = s.tracks().get("Bass");
        assertEquals(1, bass.phrases().size());
        assertInstanceOf(VoidPhrase.class, bass.phrases().get(0));
        VoidPhrase vp = (VoidPhrase) bass.phrases().get(0);
        assertEquals(4, vp.bars());
    }

    @Test
    void builderRejectsSilentWithoutDuration() {
        var ex = assertThrows(IllegalStateException.class, () -> Section.named("X")
                .timeSignature(TS_44)
                .silent("Bass"));
        assertTrue(ex.getMessage().contains("duration"));
    }

    @Test
    void builderRejectsSilentWithoutTimeSignature() {
        var ex = assertThrows(IllegalStateException.class, () -> Section.named("X")
                .duration(Duration.ofSixtyFourths(64))
                .silent("Bass"));
        assertTrue(ex.getMessage().contains("timeSignature"));
    }

    @Test
    void builderRejectsDuplicateTrackName() {
        var ex = assertThrows(IllegalArgumentException.class, () -> Section.named("X")
                .duration(Duration.ofSixtyFourths(64))
                .timeSignature(TS_44)
                .track("Melody", oneBar())
                .track("Melody", oneBar()));
        assertTrue(ex.getMessage().contains("Melody"));
        assertTrue(ex.getMessage().contains("already declared"));
    }

    // ── Key override ──────────────────────────────────────────────────

    @Test
    void sectionAllowsKeyOverride() {
        KeySignature bridge = new KeySignature(F, Mode.MINOR);
        Section s = Section.named("Bridge")
                .duration(Duration.ofSixtyFourths(64))
                .timeSignature(TS_44)
                .scale(bridge)
                .track("Melody", oneBar())
                .build();
        assertEquals(java.util.Optional.of(bridge), s.keyOverride());
    }
}
