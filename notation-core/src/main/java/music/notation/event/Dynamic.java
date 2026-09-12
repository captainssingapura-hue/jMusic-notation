package music.notation.event;

/**
 * Discrete musical loudness mark — the set of named dynamics that
 * appear in scores. Each mark carries an abstract <b>level</b> in
 * <code>[0.0, 1.0]</code> via {@link #level()} — a synth-agnostic
 * intensity. Output formats convert at their own boundary:
 *
 * <ul>
 *   <li>MIDI: {@code midiByte = round(level × 127)}.</li>
 *   <li>Score rendering: looks up the named mark directly.</li>
 *   <li>Physical-modelling / sampler synths: scale level into
 *       their own attack range.</li>
 * </ul>
 *
 * <p>Hairpin spans (cresc./decresc.) are not loudness values and live
 * on the {@code Hairpins} side-channel as
 * {@code HairpinSpan(Duration from, Duration to, Direction)}; they
 * read their endpoint levels from the discrete {@code VelocityChange}
 * / {@code VolumeChange} timeline at codec/render time.</p>
 *
 * <p>The named-mark levels follow the conventional MusicXML
 * recommendations rounded to 2dp:</p>
 * <pre>
 *   pppp 0.13  ppp 0.19  pp 0.26  p 0.39
 *   mp   0.50  mf  0.63  f  0.76  ff 0.85
 *   fff  0.94  ffff 1.00
 * </pre>
 */
public enum Dynamic {
    PPPP(0.13),
    PPP (0.19),
    PP  (0.26),
    P   (0.39),
    MP  (0.50),
    MF  (0.63),
    F   (0.76),
    FF  (0.85),
    FFF (0.94),
    FFFF(1.00);

    private final double level;

    Dynamic(double level) { this.level = level; }

    /** Synth-agnostic loudness in {@code [0.0, 1.0]}. */
    public double level() { return level; }
}
