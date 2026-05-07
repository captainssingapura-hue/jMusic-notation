package music.notation.play;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Microtiming humaniser — adds Gaussian-distributed jitter to drum-channel
 * NOTE_ON / NOTE_OFF tick positions so a metronomic drum track feels less
 * mechanical. Pure value object — applies as a tick-position rewrite on a
 * {@link Sequence}, mirroring {@link SwingSetup}'s shape and lifecycle.
 *
 * <p>{@code maxJitterMs} is interpreted at 120 bpm; at other tempi the
 * sounding jitter scales with the playback tempo (PPQ is fixed but real
 * tick-to-ms varies with BPM). For V1 we accept this approximation rather
 * than threading the live tempo through.</p>
 *
 * <p>NOTE_ON and its matching NOTE_OFF (same channel + note number) shift
 * by the same offset, so note durations are preserved. Non-drum events
 * pass through unchanged when {@code drumsOnly} is true. Tempo metas, CC,
 * program changes — all untouched.</p>
 */
public record HumanizerSetup(int maxJitterMs, boolean drumsOnly, long seed) {

    public static final HumanizerSetup OFF    = new HumanizerSetup(0,  true, 0);
    public static final HumanizerSetup LIGHT  = new HumanizerSetup(5,  true, 0);
    public static final HumanizerSetup MEDIUM = new HumanizerSetup(10, true, 0);
    public static final HumanizerSetup LOOSE  = new HumanizerSetup(20, true, 0);

    /** MIDI channel that drum tracks always use (0-indexed → channel 9). */
    private static final int DRUM_CHANNEL = 9;

    public HumanizerSetup {
        if (maxJitterMs < 0)   maxJitterMs = 0;
        if (maxJitterMs > 200) maxJitterMs = 200;
    }

    public boolean isOff() { return maxJitterMs <= 0; }

    /**
     * Apply this humaniser to every event in {@code src}, returning a new
     * {@link Sequence}. Returns the input unchanged when {@link #isOff()}.
     */
    public Sequence apply(Sequence src) throws InvalidMidiDataException {
        if (isOff()) return src;
        int ppq = src.getResolution();
        Sequence out = new Sequence(src.getDivisionType(), ppq);
        Random rng = (seed == 0) ? new Random() : new Random(seed);

        // Approximation: at 120 bpm, one quarter = 500 ms = ppq ticks
        // ⇒ ticks/ms = ppq / 500. Real playback tempo can differ; jitter
        // scales correspondingly (a piece at 60 bpm hears 2× the jitter).
        // Acceptable for V1.
        double ticksPerMs = ppq / 500.0;
        double sigmaTicks = (maxJitterMs / 3.0) * ticksPerMs;

        for (var inT : src.getTracks()) {
            var outT = out.createTrack();
            // Pending NOTE_ON offsets keyed by (channel, note) so the
            // matching NOTE_OFF picks up the same shift and preserves the
            // note's duration. Outer key is the channel; inner key the note.
            Map<Integer, Map<Integer, Long>> pending = new HashMap<>();
            for (int i = 0; i < inT.size(); i++) {
                MidiEvent ev = inT.get(i);
                long newTick = ev.getTick();
                if (ev.getMessage() instanceof ShortMessage sm) {
                    int channel = sm.getChannel();
                    int command = sm.getCommand();
                    if (shouldJitter(command, channel)) {
                        int note = sm.getData1();
                        if (isNoteOn(sm)) {
                            long offset = Math.round(rng.nextGaussian() * sigmaTicks);
                            pending.computeIfAbsent(channel, k -> new HashMap<>()).put(note, offset);
                            newTick = Math.max(0, ev.getTick() + offset);
                        } else if (isNoteOff(sm)) {
                            Map<Integer, Long> chMap = pending.get(channel);
                            Long offset = chMap == null ? null : chMap.remove(note);
                            if (offset != null) {
                                newTick = Math.max(0, ev.getTick() + offset);
                            }
                        }
                    }
                }
                outT.add(new MidiEvent(ev.getMessage(), newTick));
            }
        }
        return out;
    }

    private boolean shouldJitter(int command, int channel) {
        boolean isNote = command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF;
        if (!isNote) return false;
        if (drumsOnly && channel != DRUM_CHANNEL) return false;
        return true;
    }

    private static boolean isNoteOn(ShortMessage sm) {
        // NOTE_ON with velocity 0 is conventionally treated as NOTE_OFF.
        return sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0;
    }

    private static boolean isNoteOff(ShortMessage sm) {
        return sm.getCommand() == ShortMessage.NOTE_OFF
                || (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() == 0);
    }
}
