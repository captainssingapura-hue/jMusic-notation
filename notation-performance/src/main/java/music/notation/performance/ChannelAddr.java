package music.notation.performance;

/**
 * A two-dimensional MIDI channel address: which {@link javax.sound.midi.Synthesizer
 * Synthesizer} instance, and which of its 16 channels.
 *
 * <p>Today (Phase 1 of the multi-synth fan-out) {@code synth} is always 0 and
 * the codec emits one byte[] containing all channels. From Phase 2 onward,
 * {@link MidiCodec#toMidiSplit(Performance)} returns one byte[] per synth and
 * {@code synth} indexes into the resulting list.</p>
 *
 * <p>Slot semantics (Phase 2):</p>
 * <table>
 *   <caption>synth-index → role</caption>
 *   <tr><th>synth</th><th>role</th></tr>
 *   <tr><td>0</td><td>SOURCE_PRIMARY — source pitched + source drum</td></tr>
 *   <tr><td>1</td><td>AUTO — auto-X tracks (auto-drum, auto-harmony)</td></tr>
 *   <tr><td>2</td><td>SOURCE_OVERFLOW — source pitched 16th onward</td></tr>
 * </table>
 *
 * <p>Validation rules:</p>
 * <ul>
 *   <li>{@code synth} ≥ 0</li>
 *   <li>{@code channel} in [0, 15]</li>
 * </ul>
 *
 * <p>Note: {@code channel == 9} is the GM rhythm channel by convention; the
 * record itself doesn't enforce that drum tracks land there — that's the
 * channel allocator's job.</p>
 */
public record ChannelAddr(int synth, int channel) {
    public ChannelAddr {
        if (synth < 0) {
            throw new IllegalArgumentException("synth must be >= 0: " + synth);
        }
        if (channel < 0 || channel > 15) {
            throw new IllegalArgumentException("channel must be in [0, 15]: " + channel);
        }
    }

    /** Convenience: address on synth 0. Used through Phase 1 while every track is on the same synth. */
    public static ChannelAddr onSynthZero(int channel) {
        return new ChannelAddr(0, channel);
    }
}
