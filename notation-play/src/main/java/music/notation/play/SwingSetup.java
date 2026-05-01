package music.notation.play;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;

/**
 * Swing rhythmic feel: shifts off-beat 8th-notes later within each
 * beat. Pure value object — applies as a tick-position rewrite on a
 * {@link Sequence}.
 *
 * <p>{@code ratio} is the fraction of the beat occupied by the
 * on-beat 8th. {@code 0.5} = straight (identity); {@code 2/3} ≈
 * triplet swing (jazz). Values outside [0.5, 0.85] are clamped.</p>
 *
 * <p>Beat boundaries are unchanged — only positions strictly inside
 * a beat are remapped — so bar/beat-aligned events (tempo changes,
 * downbeat note-ons) keep their place.</p>
 */
public record SwingSetup(double ratio) {

    public static final SwingSetup OFF     = new SwingSetup(0.5);
    public static final SwingSetup LIGHT   = new SwingSetup(0.55);
    public static final SwingSetup MEDIUM  = new SwingSetup(0.60);
    public static final SwingSetup TRIPLET = new SwingSetup(2.0 / 3.0);

    public SwingSetup {
        if (Double.isNaN(ratio)) ratio = 0.5;
        if (ratio < 0.5) ratio = 0.5;
        if (ratio > 0.85) ratio = 0.85;
    }

    public boolean isOff() {
        return Math.abs(ratio - 0.5) < 1e-9;
    }

    /**
     * Map a raw tick to its swung position, given the sequence's
     * pulses-per-quarter resolution.
     */
    public long mapTick(long tick, int ppq) {
        if (isOff() || tick < 0 || ppq <= 0) return tick;
        long beat = tick / ppq;
        long pos = tick - beat * ppq;
        long half = ppq / 2;
        long beatStart = beat * ppq;
        if (pos < half) {
            // First half stretches from [0, half) to [0, ppq*ratio).
            return beatStart + Math.round(pos * 2.0 * ratio);
        } else {
            // Second half compresses from [half, ppq) to [ppq*ratio, ppq).
            double offset = (pos - half) * 2.0 * (1.0 - ratio);
            return beatStart + Math.round(ppq * ratio + offset);
        }
    }

    /**
     * Apply this swing to every event in a sequence, returning a new
     * {@link Sequence}. Returns the input unchanged when {@link #isOff()}.
     */
    public Sequence apply(Sequence src) throws InvalidMidiDataException {
        if (isOff()) return src;
        int ppq = src.getResolution();
        Sequence out = new Sequence(src.getDivisionType(), ppq);
        for (var inT : src.getTracks()) {
            var outT = out.createTrack();
            for (int i = 0; i < inT.size(); i++) {
                MidiEvent ev = inT.get(i);
                long swung = mapTick(ev.getTick(), ppq);
                outT.add(new MidiEvent(ev.getMessage(), swung));
            }
        }
        return out;
    }
}
