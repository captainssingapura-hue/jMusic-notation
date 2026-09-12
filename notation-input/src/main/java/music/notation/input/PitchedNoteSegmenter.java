package music.notation.input;

import music.notation.performance.PitchedNote;

/**
 * Converts a stream of {@link PitchEstimate}s into discrete
 * {@link PitchedNote} events. Pure state-machine logic; no audio
 * dependencies, fully testable with synthetic frame streams.
 *
 * <h2>State machine</h2>
 *
 * <pre>{@code
 *   SILENT ─pitch frame─▶ TENTATIVE ─stability hit─▶ HOLDING
 *      ▲                       │                       │
 *      │                       │ pitch jumps / fades   │
 *      └───────────────────────┴───────────────────────┘
 *                              │  pitch jumps OR fades
 *                              ▼
 *                       (note completed,
 *                        emit + back to SILENT)
 * }</pre>
 *
 * <ul>
 *   <li>{@code SILENT} — no note in progress. A frame with a confident
 *       pitch transitions to {@code TENTATIVE}.</li>
 *   <li>{@code TENTATIVE} — we've seen one or more pitched frames but
 *       haven't yet confirmed stability. Once {@link Config#minStableFrames}
 *       consecutive frames share the same pitch class (within
 *       {@link Config#pitchToleranceCents}), we transition to
 *       {@code HOLDING} and fire {@link NoteInputListener#onNoteStart}.</li>
 *   <li>{@code HOLDING} — a note is being held. The current pitch is
 *       averaged. A pitch jump beyond tolerance starts a new note
 *       (after closing the current one); a silent/unconfident frame
 *       starts a silence counter — if silence persists for
 *       {@link Config#silenceFramesToEnd} frames, the note is closed.</li>
 * </ul>
 *
 * <h2>Velocity</h2>
 *
 * <p>Velocity for the emitted note is derived from the maximum RMS
 * observed during the note's lifetime, mapped to MIDI {@code [40, 127]}
 * via {@link #rmsToVelocity}. Quiet notes get audible velocity (40+);
 * loud notes get full velocity.</p>
 *
 * <h2>Threading</h2>
 *
 * <p>Not thread-safe. Drive from a single thread (typically the audio
 * capture thread). Notes are emitted synchronously inside
 * {@link #onFrame}.</p>
 */
public final class PitchedNoteSegmenter {

    /**
     * Tuneable parameters. Defaults are good for typical vocal recording:
     * 50 ms minimum note, ±50 cents pitch tolerance, 80 ms silence to
     * end a note.
     */
    public record Config(
            int minStableFrames,
            double pitchToleranceCents,
            int silenceFramesToEnd,
            double minRmsForPitch) {
        public static Config defaults() {
            // At ~50 ms per frame: 2 frames stable = 100 ms before
            // declaring a note start, 2 frames silence = 100 ms gap to
            // close. Pitch tolerance ±50 cents = half a semitone.
            return new Config(/*minStableFrames=*/ 2,
                              /*pitchToleranceCents=*/ 50.0,
                              /*silenceFramesToEnd=*/ 2,
                              /*minRmsForPitch=*/ 0.005);
        }
    }

    private final Config config;
    private final NoteInputListener listener;

    // State
    private State state = State.SILENT;
    private long currentStartTickMs;
    private double currentPitchSum;     // for running average
    private int    currentPitchCount;
    private double currentMaxRms;
    private int    consecutiveStableFrames;
    private int    consecutiveSilenceFrames;
    private double anchorMidi;          // pitch the current stable run is anchored to

    public PitchedNoteSegmenter(Config config, NoteInputListener listener) {
        this.config = config;
        this.listener = listener;
    }

    public PitchedNoteSegmenter(NoteInputListener listener) {
        this(Config.defaults(), listener);
    }

    /**
     * Feed one frame's worth of pitch information. May emit zero or one
     * note (and zero or one onNoteStart) before returning.
     */
    public void onFrame(PitchEstimate est) {
        // Always forward the raw estimate for live UI feedback. Listener's
        // default no-op means most consumers can ignore.
        listener.onPitchEstimate(est);

        boolean usable = est.hasPitch() && est.rms() >= config.minRmsForPitch;

        switch (state) {
            case SILENT -> {
                if (usable) startTentative(est);
            }
            case TENTATIVE -> {
                if (!usable) {
                    // Lost the signal before confirming. Drop back to silent.
                    resetToSilent();
                } else if (withinTolerance(est.midiFloat())) {
                    consecutiveStableFrames++;
                    currentPitchSum += est.midiFloat();
                    currentPitchCount++;
                    currentMaxRms = Math.max(currentMaxRms, est.rms());
                    if (consecutiveStableFrames >= config.minStableFrames) {
                        promoteToHolding(est.tickMs());
                    }
                } else {
                    // Pitch jump while tentative — re-anchor.
                    startTentative(est);
                }
            }
            case HOLDING -> {
                if (!usable) {
                    consecutiveSilenceFrames++;
                    if (consecutiveSilenceFrames >= config.silenceFramesToEnd) {
                        completeNote(est.tickMs());
                    }
                } else if (withinTolerance(est.midiFloat())) {
                    consecutiveSilenceFrames = 0;
                    currentPitchSum += est.midiFloat();
                    currentPitchCount++;
                    currentMaxRms = Math.max(currentMaxRms, est.rms());
                } else {
                    // Pitch jump while holding — close current note, start a new one.
                    completeNote(est.tickMs());
                    startTentative(est);
                }
            }
        }
    }

    /**
     * Flush any in-progress note. Call when the capture stream ends so
     * a held note isn't lost.
     *
     * @param endTickMs ms timestamp to use for the held note's end time
     */
    public void flush(long endTickMs) {
        if (state != State.SILENT) {
            completeNote(endTickMs);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private boolean withinTolerance(double midiFloat) {
        return Math.abs((midiFloat - anchorMidi) * 100.0) <= config.pitchToleranceCents;
    }

    private void startTentative(PitchEstimate est) {
        state = State.TENTATIVE;
        currentStartTickMs = est.tickMs();
        currentPitchSum = est.midiFloat();
        currentPitchCount = 1;
        currentMaxRms = est.rms();
        consecutiveStableFrames = 1;
        consecutiveSilenceFrames = 0;
        anchorMidi = est.midiFloat();
    }

    private void promoteToHolding(long tickMs) {
        state = State.HOLDING;
        int midi = (int) Math.round(currentPitchSum / currentPitchCount);
        int velocity = rmsToVelocity(currentMaxRms);
        listener.onNoteStart(currentStartTickMs, midi, velocity);
    }

    private void completeNote(long endTickMs) {
        if (state != State.HOLDING) {
            // The tentative run never crossed the stability threshold;
            // discard it without emitting.
            resetToSilent();
            return;
        }
        int midi = (int) Math.round(currentPitchSum / currentPitchCount);
        long durationMs = Math.max(1, endTickMs - currentStartTickMs);
        int velocity = rmsToVelocity(currentMaxRms);
        // TODO (post ms→Duration redesign): audio import emits ms-native
        // segments; a proper quantizer would snap these to a chosen
        // musical grid against a reference BPM. For now we project at
        // a fixed 120 bpm (1 quarter = 500 ms → Duration.of(ms, 2000))
        // so callers get a Duration-anchored note. Mark this site for
        // the quantizer follow-up.
        music.notation.duration.Duration at =
                music.notation.duration.Duration.of(currentStartTickMs, 2000);
        music.notation.duration.Duration noteDuration =
                music.notation.duration.Duration.of(durationMs, 2000);
        PitchedNote note = new PitchedNote(at, noteDuration, midi);
        listener.onNoteCompleted(note, velocity);
        resetToSilent();
    }

    private void resetToSilent() {
        state = State.SILENT;
        currentStartTickMs = 0;
        currentPitchSum = 0;
        currentPitchCount = 0;
        currentMaxRms = 0;
        consecutiveStableFrames = 0;
        consecutiveSilenceFrames = 0;
        anchorMidi = 0;
    }

    /**
     * Map RMS amplitude to MIDI velocity. Linear scaling between
     * {@code [minRmsForPitch, 1.0]} → {@code [40, 127]}. Quiet notes
     * still have audible velocity; loud notes get the full range.
     */
    static int rmsToVelocity(double rms) {
        if (rms <= 0) return 40;
        double clamped = Math.max(0.0, Math.min(1.0, rms));
        // Compress with sqrt so the velocity climbs quickly from quiet
        // to mid-range, then tapers — more musically natural than linear.
        double curve = Math.sqrt(clamped);
        int v = 40 + (int) Math.round(curve * (127 - 40));
        return Math.max(1, Math.min(127, v));
    }

    private enum State { SILENT, TENTATIVE, HOLDING }
}
