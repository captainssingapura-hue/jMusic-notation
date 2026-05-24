package music.notation.input;

import music.notation.performance.PitchedNote;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * State-machine tests for {@link PitchedNoteSegmenter}. Drive the
 * segmenter with synthetic frame streams and verify the emitted note
 * events match the expected sequence — onsets, durations, pitches,
 * velocity ranges.
 */
class PitchedNoteSegmenterTest {

    private static final int FRAME_MS = 50;

    private RecordingListener events;
    private PitchedNoteSegmenter segmenter;

    void useDefaultConfig() {
        events = new RecordingListener();
        segmenter = new PitchedNoteSegmenter(events);
    }

    void useConfig(PitchedNoteSegmenter.Config c) {
        events = new RecordingListener();
        segmenter = new PitchedNoteSegmenter(c, events);
    }

    // ── Single-note basics ────────────────────────────────────────────

    @Test
    void singleSteadyPitch_emitsOneNote() {
        useDefaultConfig();
        // 10 frames at MIDI 60, 50 ms each → one note, 500 ms long, MIDI 60.
        feedSteady(60.0, 10, 0, 0.2);
        segmenter.flush(500);

        assertEquals(1, events.completed.size());
        PitchedNote n = events.completed.get(0);
        assertEquals(0, n.tickMs());
        assertEquals(500, n.durationMs());
        assertEquals(60, n.midi());
    }

    @Test
    void notesBelowMinStable_areNotEmitted() {
        useDefaultConfig();
        // Only 1 stable frame, then silence — never crosses minStableFrames=2.
        segmenter.onFrame(new PitchEstimate(0, 60.0, 0.9, 0.2));
        segmenter.onFrame(PitchEstimate.silent(50, 0.0));
        segmenter.onFrame(PitchEstimate.silent(100, 0.0));
        segmenter.flush(150);
        assertTrue(events.completed.isEmpty(),
                "a one-frame blip must not become a note");
    }

    @Test
    void unconfidentFramesAreIgnored() {
        useDefaultConfig();
        // Pitch is present but RMS too low → treated as no-pitch.
        feedSteady(60.0, 5, 0, /*rms=*/ 0.001);   // below default minRmsForPitch=0.005
        segmenter.flush(250);
        assertTrue(events.completed.isEmpty());
    }

    // ── Note separation ───────────────────────────────────────────────

    @Test
    void pitchJump_completesOldAndStartsNew() {
        useDefaultConfig();
        feedSteady(60.0, 4, 0,   0.2);    // 200 ms at C4
        feedSteady(64.0, 4, 200, 0.3);    // 200 ms at E4
        segmenter.flush(400);

        assertEquals(2, events.completed.size());
        assertEquals(60, events.completed.get(0).midi());
        assertEquals(64, events.completed.get(1).midi());
    }

    @Test
    void silentGap_completesNoteAndNextNoteStartsFresh() {
        useDefaultConfig();
        feedSteady(60.0, 4, 0,   0.2);    // C4 for 200 ms
        feedSilence(3, 200);              // 150 ms silence (≥ silenceFramesToEnd=2)
        feedSteady(60.0, 4, 350, 0.2);    // C4 again for 200 ms
        segmenter.flush(550);

        assertEquals(2, events.completed.size(),
                "silent gap must close the first note and start a new one");
        assertEquals(60, events.completed.get(0).midi());
        assertEquals(60, events.completed.get(1).midi());
        assertTrue(events.completed.get(1).tickMs() > events.completed.get(0).tickMs() + 100,
                "second note starts after the silent gap, not immediately");
    }

    @Test
    void vibratoWithinTolerance_isOneNote() {
        useDefaultConfig();
        // Pitch wobbles ±30 cents around 60 → within default ±50 cents tolerance.
        for (int i = 0; i < 10; i++) {
            double wobble = 0.3 * Math.sin(i * 0.7);
            segmenter.onFrame(new PitchEstimate(i * FRAME_MS, 60.0 + wobble, 0.9, 0.2));
        }
        segmenter.flush(10 * FRAME_MS);
        assertEquals(1, events.completed.size(),
                "vibrato within tolerance must produce exactly one note");
        assertEquals(60, events.completed.get(0).midi());
    }

    // ── Onset events ──────────────────────────────────────────────────

    @Test
    void onNoteStart_firesAtSegmenterPromotion() {
        useDefaultConfig();
        feedSteady(72.0, 5, 0, 0.3);
        segmenter.flush(250);

        assertEquals(1, events.starts.size());
        // Onset reflects the very first stable frame's tick.
        assertEquals(0, events.starts.get(0).tickMs);
        assertEquals(72, events.starts.get(0).midi);
    }

    @Test
    void onPitchEstimate_firesForEveryFrame_includingSilence() {
        useDefaultConfig();
        segmenter.onFrame(PitchEstimate.silent(0, 0.0));
        segmenter.onFrame(new PitchEstimate(50, 60.0, 0.9, 0.2));
        segmenter.onFrame(PitchEstimate.silent(100, 0.0));
        assertEquals(3, events.estimates.size());
    }

    // ── Velocity from RMS ────────────────────────────────────────────

    @Test
    void velocityScalesWithLoudness() {
        useDefaultConfig();
        feedSteady(60.0, 5, 0, 0.05);
        segmenter.flush(250);
        int quietVel = events.completedVelocities.get(0);

        useDefaultConfig();   // fresh
        feedSteady(60.0, 5, 0, 0.6);
        segmenter.flush(250);
        int loudVel = events.completedVelocities.get(0);

        assertTrue(loudVel > quietVel,
                "louder RMS must yield higher velocity; got loud=" + loudVel + " quiet=" + quietVel);
        assertTrue(quietVel >= 40, "quiet velocity must be at least the audible floor (40); got " + quietVel);
        assertTrue(loudVel <= 127, "velocity must clamp to 127; got " + loudVel);
    }

    @Test
    void rmsToVelocityClampsAndCurves() {
        assertEquals(40, PitchedNoteSegmenter.rmsToVelocity(0.0));
        assertEquals(40, PitchedNoteSegmenter.rmsToVelocity(-1.0));
        assertEquals(127, PitchedNoteSegmenter.rmsToVelocity(1.0));
        assertEquals(127, PitchedNoteSegmenter.rmsToVelocity(2.0));
        // Square-root curve: rms=0.25 → sqrt=0.5 → velocity ≈ 40 + 0.5*87 ≈ 84.
        int mid = PitchedNoteSegmenter.rmsToVelocity(0.25);
        assertTrue(Math.abs(mid - 84) <= 1, "mid-range velocity ≈ 84, got " + mid);
    }

    // ── Flush behaviour ───────────────────────────────────────────────

    @Test
    void flushClosesHeldNote() {
        useDefaultConfig();
        feedSteady(60.0, 3, 0, 0.3);
        // No silence frames — without flush, the note never closes.
        assertTrue(events.completed.isEmpty(), "precondition: note still held");
        segmenter.flush(150);
        assertEquals(1, events.completed.size());
        assertEquals(150, events.completed.get(0).durationMs());
    }

    @Test
    void flushOnSilentSegmenterIsNoop() {
        useDefaultConfig();
        feedSilence(5, 0);
        segmenter.flush(250);
        assertTrue(events.completed.isEmpty());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void feedSteady(double midi, int frames, long startTickMs, double rms) {
        for (int i = 0; i < frames; i++) {
            segmenter.onFrame(new PitchEstimate(
                    startTickMs + (long) i * FRAME_MS, midi, 0.9, rms));
        }
    }

    private void feedSilence(int frames, long startTickMs) {
        for (int i = 0; i < frames; i++) {
            segmenter.onFrame(PitchEstimate.silent(startTickMs + (long) i * FRAME_MS, 0.0));
        }
    }

    private record StartEvent(long tickMs, int midi, int velocity) {}

    private static final class RecordingListener implements NoteInputListener {
        final List<PitchEstimate> estimates = new ArrayList<>();
        final List<StartEvent>    starts    = new ArrayList<>();
        final List<PitchedNote>   completed = new ArrayList<>();
        final List<Integer>       completedVelocities = new ArrayList<>();

        @Override public void onPitchEstimate(PitchEstimate e) { estimates.add(e); }
        @Override public void onNoteStart(long tickMs, int midi, int velocity) {
            starts.add(new StartEvent(tickMs, midi, velocity));
        }
        @Override public void onNoteCompleted(PitchedNote n, int velocity) {
            completed.add(n);
            completedVelocities.add(velocity);
        }
    }
}
