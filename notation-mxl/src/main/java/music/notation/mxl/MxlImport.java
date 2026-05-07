package music.notation.mxl;

import music.notation.performance.MusicalImport;
import music.notation.performance.Performance;
import music.notation.performance.PerformanceImporter;
import music.notation.performance.PerformanceImporter.SplitMode;
import music.notation.structure.KeySignature;
import music.notation.structure.Piece;
import music.notation.structure.TimeSignature;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of reading a compressed MusicXML (.mxl) file: the canonical
 * {@link Performance} plus the time-signature and key-signature meta
 * extracted from the score, and the raw decompressed XML for inspection
 * and round-trip comparison.
 *
 * <p>Mirrors {@code MidiImport} in {@code notation-performance} — imports
 * are session-ephemeral and carry no model-level structure ({@code Piece},
 * {@code Phrase}). They are consumed by playback and visualisation paths
 * directly.</p>
 *
 * <p>{@code sourceXml} holds the decompressed root MusicXML document as a
 * UTF-8 string. Useful for diffing against re-emitted XML and for the
 * "MXL_*.xml" sidecar that {@code MxlProject} writes to disk.</p>
 */
public record MxlImport(
        String displayName,
        Performance performance,
        TimeSignature timeSig,
        KeySignature key,
        String sourceXml,
        RepeatStructure repeatStructure,
        Transpositions transpositions
) implements MusicalImport {
    public MxlImport {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(performance, "performance");
        Objects.requireNonNull(timeSig, "timeSig");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(sourceXml, "sourceXml");
        Objects.requireNonNull(repeatStructure, "repeatStructure");
        Objects.requireNonNull(transpositions, "transpositions");
    }

    /** Backwards-compat ctor: defaults sidecars to empty. */
    public MxlImport(String displayName, Performance performance,
                     TimeSignature timeSig, KeySignature key, String sourceXml) {
        this(displayName, performance, timeSig, key, sourceXml,
                RepeatStructure.empty(), Transpositions.empty());
    }

    /** Backwards-compat ctor: defaults transpositions to empty. */
    public MxlImport(String displayName, Performance performance,
                     TimeSignature timeSig, KeySignature key, String sourceXml,
                     RepeatStructure repeatStructure) {
        this(displayName, performance, timeSig, key, sourceXml,
                repeatStructure, Transpositions.empty());
    }

    public Optional<String> source() {
        return Optional.of(displayName);
    }

    /** First tempo in the imported tempo track, else 120 bpm. */
    public int initialBpm() {
        return performance.tempo().changes().isEmpty()
                ? 120
                : performance.tempo().changes().get(0).bpm();
    }

    /**
     * Convert this import to a {@link Piece} for GUI / playback paths that
     * consume the abstract structure model (e.g. {@code NotationApp}).
     * Defaults to {@link SplitMode#PRESERVE} since the MXL parser already
     * emits one {@code Track} per voice/staff — no further voice splitting is
     * needed.
     */
    public Piece toPiece() {
        return toPiece(SplitMode.PRESERVE);
    }

    /** {@link #toPiece()} with an explicit split mode for callers that want SPLIT. */
    public Piece toPiece(SplitMode mode) {
        return PerformanceImporter.toPiece(
                performance,
                timeSig,
                key,
                initialBpm(),
                displayName,
                PerformanceImporter.DEFAULT_CUTOFF_MIDI,
                mode);
    }
}
