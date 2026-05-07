package music.notation.play;

import music.notation.expressivity.PedalChange;
import music.notation.expressivity.PedalControl;
import music.notation.expressivity.PedalState;
import music.notation.expressivity.Pedaling;
import music.notation.performance.TempoConversion;
import music.notation.performance.TempoTrack;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import java.util.HashSet;
import java.util.Set;

/**
 * Post-processes a MIDI {@link Sequence} by injecting CC #64 (Damper /
 * Sustain Pedal) events from a {@link Pedaling} side-channel.
 *
 * <p>The Piece-based playback path (used by {@code NotationApp}) builds
 * a Sequence from the {@code Piece} and loses {@link Pedaling}, since
 * {@code Piece} doesn't carry pedal info today. This utility re-injects
 * the pedal events on every non-drum channel present in the sequence,
 * so the synth honours the source's pedaling.</p>
 *
 * <p>Time mapping uses {@link TempoConversion} against the supplied
 * {@link TempoTrack}, so accelerandi / ritardandi / rubato are honoured
 * — pedal events land at the correct ticks even when bpm varies through
 * the piece. Empty tempo track falls back to a constant 120 bpm.</p>
 */
public final class PedalInjector {

    /** Drum channel — never injected with sustain pedal. */
    private static final int DRUM_CHANNEL = 9;

    private PedalInjector() {}

    /**
     * Returns a new {@link Sequence} with CC #64 events injected. If
     * {@code pedaling} is empty or null, returns the input unchanged.
     *
     * @param tempos tempo timeline used to convert pedal-event ms
     *               positions to ticks; null is treated as empty
     */
    public static Sequence inject(Sequence src, Pedaling pedaling, TempoTrack tempos)
            throws InvalidMidiDataException {
        if (src == null) return null;
        if (pedaling == null || pedaling.byTrack().isEmpty()) return src;

        int ppq = src.getResolution();
        TempoTrack tt = (tempos == null) ? TempoTrack.empty() : tempos;

        // Build a new Sequence preserving the original event layout, then
        // append CC #64 events to the first non-drum track (Track.add
        // re-sorts by tick).
        Sequence out = new Sequence(src.getDivisionType(), ppq);
        Set<Integer> channelsInUse = new HashSet<>();
        Track injectionTrack = null;
        for (Track inT : src.getTracks()) {
            Track outT = out.createTrack();
            boolean trackHasNonDrum = false;
            for (int i = 0; i < inT.size(); i++) {
                MidiEvent ev = inT.get(i);
                outT.add(new MidiEvent(ev.getMessage(), ev.getTick()));
                if (ev.getMessage() instanceof ShortMessage sm
                        && sm.getCommand() == ShortMessage.NOTE_ON) {
                    int ch = sm.getChannel();
                    channelsInUse.add(ch);
                    if (ch != DRUM_CHANNEL) trackHasNonDrum = true;
                }
            }
            // Pick the first track carrying any non-drum NOTE_ON as the
            // injection target. CC events are channel-scoped, so a single
            // track is enough — we'll emit one CC #64 per non-drum
            // channel on that track.
            if (injectionTrack == null && trackHasNonDrum) injectionTrack = outT;
        }
        if (injectionTrack == null || channelsInUse.isEmpty()) return out;

        // Collapse all per-track pedal changes into one timeline (the
        // pedal is a per-instrument concept; on a piano part with
        // multiple voice tracks we'd have the same timeline duplicated,
        // so de-dup by (tickMs, state)).
        Set<Long> emittedKeys = new HashSet<>();
        for (PedalControl pc : pedaling.byTrack().values()) {
            for (PedalChange change : pc.changes()) {
                long key = (change.tickMs() << 2) | change.state().ordinal();
                if (!emittedKeys.add(key)) continue;
                long tick = TempoConversion.msToTicks(tt, change.tickMs(), ppq);
                emit(injectionTrack, tick, change.state(), channelsInUse);
            }
        }
        return out;
    }

    /**
     * Backwards-compat wrapper accepting a constant bpm — converts to a
     * {@link TempoTrack#constant(int) constant TempoTrack} before
     * delegating. Prefer the {@link TempoTrack} overload when a true
     * tempo timeline is available.
     *
     * @deprecated use {@link #inject(Sequence, Pedaling, TempoTrack)}
     */
    @Deprecated
    public static Sequence inject(Sequence src, Pedaling pedaling, int referenceBpm)
            throws InvalidMidiDataException {
        TempoTrack tt = (referenceBpm > 0)
                ? TempoTrack.constant(referenceBpm)
                : TempoTrack.empty();
        return inject(src, pedaling, tt);
    }

    private static void emit(Track track, long tick, PedalState state, Set<Integer> channels)
            throws InvalidMidiDataException {
        for (int channel : channels) {
            if (channel == DRUM_CHANNEL) continue;
            if (state == PedalState.CHANGE) {
                track.add(controlChange(channel, tick, 0));
                track.add(controlChange(channel, tick + 1, 127));
            } else {
                int value = (state == PedalState.DOWN) ? 127 : 0;
                track.add(controlChange(channel, tick, value));
            }
        }
    }

    private static MidiEvent controlChange(int channel, long tick, int value)
            throws InvalidMidiDataException {
        ShortMessage msg = new ShortMessage();
        msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, /*CC #64=*/ 64, value);
        return new MidiEvent(msg, tick);
    }
}
