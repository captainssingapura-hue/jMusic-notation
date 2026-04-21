package music.notation.structure;

import music.notation.event.Instrument;

/**
 * A piece-level declaration of a named track.
 *
 * <p>Two kinds:</p>
 * <ul>
 *   <li>{@link MusicTrackDecl} — a pitched-content track with an instrument
 *       (melody, bass, drums, …). Its content is played through the declared
 *       instrument.</li>
 *   <li>{@link ControlTrackDecl} — a lane carrying only structural markers
 *       (tempo changes, rehearsal annotations, articulation hints, …).
 *       No pitches, no instrument. Keeps music-track phrases clean of
 *       markers that apply piece-wide.</li>
 * </ul>
 *
 * <p>Used by the sectional {@link Piece} constructor: each section must
 * supply a {@link SectionTrack} for every declared name, and the piece
 * joins matching names across sections.</p>
 */
public sealed interface TrackDecl {

    /** The track's name — must be unique within a piece. */
    String name();

    /** A pitched-content track with an instrument. */
    record MusicTrackDecl(String name, Instrument defaultInstrument) implements TrackDecl {
        public MusicTrackDecl {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("MusicTrackDecl name must be non-blank");
            }
            if (defaultInstrument == null) {
                throw new IllegalArgumentException(
                        "MusicTrackDecl '" + name + "': defaultInstrument must not be null");
            }
        }
    }

    /**
     * A structural / marker-only track — carries tempo changes,
     * articulation hints, rehearsal metadata, etc. No pitches, no
     * instrument. The resolved {@link Track} exists to keep timing
     * alignment but never emits note events.
     */
    record ControlTrackDecl(String name) implements TrackDecl {
        public ControlTrackDecl {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("ControlTrackDecl name must be non-blank");
            }
        }
    }
}
