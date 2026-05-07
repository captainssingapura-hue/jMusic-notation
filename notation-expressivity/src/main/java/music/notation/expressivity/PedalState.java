package music.notation.expressivity;

/**
 * Sustain-pedal state at a {@link PedalChange} event.
 *
 * <ul>
 *   <li>{@link #DOWN}   — pedal pressed; dampers released so notes ring on</li>
 *   <li>{@link #UP}     — pedal lifted; dampers re-engage and notes decay</li>
 *   <li>{@link #CHANGE} — rapid release-and-re-press (clears the held
 *       resonance, then resumes sustain). Common between chord changes
 *       in legato playing.</li>
 * </ul>
 *
 * <p>Maps to MIDI CC #64 on emit: {@code DOWN} → 127, {@code UP} → 0,
 * {@code CHANGE} → 0 then 127 separated by 1 ms.</p>
 */
public enum PedalState { DOWN, UP, CHANGE }
