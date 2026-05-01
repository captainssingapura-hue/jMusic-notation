package music.notation.experiments.sakura;

import music.notation.core.model.transformation.Transformer;
import music.notation.experiments.chinese.gong.GongConcretizer;
import music.notation.experiments.chinese.gong.GongNote;
import music.notation.experiments.hirajoshi.HirajoshiConcretizer;
import music.notation.experiments.hirajoshi.HirajoshiNote;
import music.notation.experiments.scale.TimedNote;
import music.notation.experiments.transform.ScaleTranspose;
import music.notation.experiments.yo.YoConcretizer;
import music.notation.experiments.yo.YoNote;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the shape of the Sakura melody and its projections through
 * multiple scales. No audio — these are fast, deterministic checks.
 */
class SakuraSongTest {

    private static final int A_TONIC = 9;
    private static final int TOTAL_NOTES = 48;
    private static final int TOTAL_DURATION_MS = 32_000; // 16 bars × 4/4 × 500 ms/quarter

    // ── Shape ─────────────────────────────────────────────────────────

    @Test
    void melody_hasExpectedNoteCountAndTotalDuration() {
        var melody = SakuraSong.melody();
        assertEquals(TOTAL_NOTES, melody.size(),
                "Sakura is 48 timed notes across 16 bars");
        int total = melody.stream().mapToInt(TimedNote::durationMillis).sum();
        assertEquals(TOTAL_DURATION_MS, total,
                "16 bars of 4/4 at 120 BPM = 32 000 ms");
    }

    @Test
    void melody_stayWithinHirajoshiFiveDegrees() {
        var melody = SakuraSong.melody();
        // Every note must be one of the 5 Hirajoshi degrees (no out-of-scale notes).
        for (var timed : melody) {
            int idx = timed.note().degreeIndex();
            assertTrue(idx >= 0 && idx < 5,
                    "degree index out of range: " + idx);
        }
    }

    // ── Authentic rendering: A Hirajoshi ──────────────────────────────

    @Test
    void authentic_concretizesToExpectedFirstBars() {
        // In A Hirajoshi at octave 4 the degrees map to:
        //   I=A4(69)  II=B4(71)  III=C5(72)  V=E5(76)  VI=F5(77)
        // First 2 bars ("Sakura, sakura") = A A B | A A B
        var midi = SakuraSong.melody().stream()
                .map(TimedNote::note)
                .map(new HirajoshiConcretizer(A_TONIC)::midi)
                .limit(6)
                .toList();
        assertEquals(List.of(69, 69, 71, 69, 69, 71), midi);
    }

    @Test
    void authentic_climaxHitsV_onKasumiKa() {
        // Bar 8 ends with V (E5) — the climax of "kasumi ka kumo ka".
        // Note indices (0-based):
        //   bars 1-2 = indices  0-5   (3 + 3 notes)
        //   bars 3-4 = indices  6-10  (3 + 2)
        //   bars 5-6 = indices 11-15  (3 + 2)
        //   bar 7    = indices 16-18  (C C B — 3 notes)
        //   bar 8    = indices 19-21  (A A E — 3 notes); V lands at index 21.
        var midi = SakuraSong.melody().stream()
                .map(TimedNote::note)
                .map(new HirajoshiConcretizer(A_TONIC)::midi)
                .toList();
        assertEquals(76, midi.get(21),
                "V (E5=76) should land on the climax at end of bar 8");
    }

    @Test
    void authentic_finalNote_isTonic() {
        var midi = SakuraSong.melody().stream()
                .map(TimedNote::note)
                .map(new HirajoshiConcretizer(A_TONIC)::midi)
                .toList();
        assertEquals(69, midi.get(midi.size() - 1),
                "song must resolve on A4 (tonic)");
    }

    @Test
    void authentic_pitchClassSet_matchesAHirajoshi() {
        // A Hirajoshi = A, B, C, E, F → pitch classes {9, 11, 0, 4, 5}
        var pitchClasses = SakuraSong.melody().stream()
                .map(TimedNote::note)
                .map(new HirajoshiConcretizer(A_TONIC)::midi).map(m -> Math.floorMod(m, 12))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(9, 11, 0, 4, 5), pitchClasses);
    }

    // ── Cross-scale renditions ────────────────────────────────────────

    @Test
    void yoVariation_usesYoPitchClasses() {
        // A Yo = A, B, D, E, F♯ → pitch classes {9, 11, 2, 4, 6}
        Transformer<HirajoshiNote, YoNote> toYo =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, YoNote::ofIndex);
        var pitchClasses = SakuraSong.melody().stream()
                .map(t -> toYo.forward(t.note()))
                .map(new YoConcretizer(A_TONIC)::midi).map(m -> Math.floorMod(m, 12))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(9, 11, 2, 4, 6), pitchClasses);
    }

    @Test
    void gongVariation_usesGongPitchClasses() {
        // A Gong = A, B, C♯, E, F♯ → pitch classes {9, 11, 1, 4, 6}
        Transformer<HirajoshiNote, GongNote> toGong =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, GongNote::ofIndex);
        var pitchClasses = SakuraSong.melody().stream()
                .map(t -> toGong.forward(t.note()))
                .map(new GongConcretizer(A_TONIC)::midi).map(m -> Math.floorMod(m, 12))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(9, 11, 1, 4, 6), pitchClasses);
    }

    @Test
    void allVariations_preserveContourAndDuration() {
        // The degree index sequence must be identical across all three
        // renderings — that's the whole point of a cross-scale transform.
        var abstractNotes = SakuraSong.melody();

        Transformer<HirajoshiNote, YoNote> toYo =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, YoNote::ofIndex);
        Transformer<HirajoshiNote, GongNote> toGong =
                new ScaleTranspose<>(HirajoshiNote::ofIndex, GongNote::ofIndex);

        for (int i = 0; i < abstractNotes.size(); i++) {
            int srcIdx = abstractNotes.get(i).note().degreeIndex();
            int srcOct = abstractNotes.get(i).note().octave();
            int srcDur = abstractNotes.get(i).durationMillis();

            var yoNote = toYo.forward(abstractNotes.get(i).note());
            assertEquals(srcIdx, yoNote.degreeIndex(), "Yo #" + i);
            assertEquals(srcOct, yoNote.octave(),     "Yo #" + i);

            var gongNote = toGong.forward(abstractNotes.get(i).note());
            assertEquals(srcIdx, gongNote.degreeIndex(), "Gong #" + i);
            assertEquals(srcOct, gongNote.octave(),      "Gong #" + i);

            assertEquals(srcDur, abstractNotes.get(i).durationMillis(),
                    "timing untouched #" + i);
        }
    }
}
