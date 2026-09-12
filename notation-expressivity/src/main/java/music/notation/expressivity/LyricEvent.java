package music.notation.expressivity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import music.notation.duration.Duration;

import java.util.Objects;

/**
 * A single per-note lyric attribution anchored at a musical position
 * from the start of the owning track. The codePoint is one Unicode
 * scalar value; a value equal to {@code '_'}
 * ({@link #CONTINUATION_CODE_POINT}) marks a <b>continuation</b> —
 * the glyph from the previous lyric event in the line is being held
 * across another melodic note.
 *
 * <p>Stored as an {@code int} so non-BMP glyphs (emoji, rare CJK)
 * survive round-tripping cleanly. Decoders display via
 * {@code new String(Character.toChars(codePoint))}.</p>
 */
public record LyricEvent(Duration at, int codePoint) {

    /**
     * Reserved continuation marker — mirrors
     * {@code LyricNode.CONTINUATION_CODE_POINT} so authoring and
     * performance representations share a single convention.
     */
    public static final int CONTINUATION_CODE_POINT = '_';

    public LyricEvent {
        Objects.requireNonNull(at, "at");
        if (at.compareDuration(Duration.zero()) < 0) {
            throw new IllegalArgumentException("at must be >= 0: " + at);
        }
        if (!Character.isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException(
                    "codePoint is not a valid Unicode code point: " + codePoint);
        }
    }

    /** True when the glyph at this event is the {@code '_'} continuation marker. */
    @JsonIgnore
    public boolean isContinuation() {
        return codePoint == CONTINUATION_CODE_POINT;
    }

    /**
     * The glyph as a {@link String}; handles surrogate pairs correctly.
     * Prefer this over {@code (char) codePoint()} for any display path.
     *
     * <p>Annotated {@code @JsonIgnore} so it doesn't fight the
     * record-component serialization: the wire format uses
     * {@link #codePoint} only; {@code character} is a derived view.</p>
     */
    @JsonIgnore
    public String character() {
        return new String(Character.toChars(codePoint));
    }

    /** Convenience factory for a continuation marker at a given position. */
    public static LyricEvent continuation(Duration at) {
        return new LyricEvent(at, CONTINUATION_CODE_POINT);
    }
}
