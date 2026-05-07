package music.notation.performance;

import music.notation.structure.KeySignature;
import music.notation.structure.TimeSignature;

import java.util.Optional;

/**
 * Common shape of an externally-loaded piece — the data the UI needs to
 * route it through {@link PerformanceImporter} and present it for playback.
 *
 * <p>Implemented by {@link MidiImport} (MIDI bytes), {@code MxlImport}
 * (compressed MusicXML, in {@code notation-mxl}), and any future format
 * (JSON-folder reload, live capture, …). The downstream consumer treats
 * all sources uniformly: same display fields, same conversion path to
 * {@link music.notation.structure.Piece} via
 * {@link PerformanceImporter#toPiece(Performance, TimeSignature, KeySignature, int, String, int, PerformanceImporter.SplitMode)}.</p>
 */
public interface MusicalImport {

    /** Human-friendly title surfaced in the UI (e.g. file basename). */
    String displayName();

    /** Concrete-notes performance — the playback substrate. */
    Performance performance();

    /** Score's initial time signature (mid-piece changes deferred). */
    TimeSignature timeSig();

    /** Score's initial key signature (mid-piece changes deferred). */
    KeySignature key();

    /** First tempo from {@link #performance()}, else 120 bpm. */
    int initialBpm();

    /** Provenance hint (file path, URL, …) when available. */
    default Optional<String> source() {
        return Optional.empty();
    }
}
