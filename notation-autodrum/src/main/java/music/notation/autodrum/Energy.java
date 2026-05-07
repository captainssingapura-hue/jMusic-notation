package music.notation.autodrum;

/**
 * Coarse energy level passed to a {@link DrumStrategy} when generating
 * accompaniment. Higher energy generally means denser subdivisions, more
 * embellishments (crash, ride, double bass, etc.), and louder hits when
 * velocity rendering ships.
 *
 * <p>Three levels keeps the UI picker simple while giving strategies
 * enough room to vary. Strategies that don't differentiate energy can
 * ignore the parameter.</p>
 */
public enum Energy {
    LOW,
    MEDIUM,
    HIGH
}
