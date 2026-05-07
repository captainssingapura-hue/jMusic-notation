package music.notation.play;

import music.notation.performance.MidiImport;
import music.notation.performance.Performance;
import music.notation.structure.MusicalPiece;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;

import java.util.Objects;

/**
 * Bundles the structural reading of a piece ({@link Piece} — bar
 * lists, voice-split tracks) with the playback-fidelity reading
 * ({@link Performance} — raw note timings) and provenance
 * ({@link Origin}).
 *
 * <p>Used by the UI to collapse the two historical "Piece path" and
 * "Imported MIDI path" branches into one. Visualisation reads
 * {@code piece}; audio playback and MIDI export read
 * {@code performance}; the {@code origin} tag tells us where it came
 * from (Java provider vs MIDI import vs future JSON).</p>
 *
 * <p>Invariant: for any {@code LoadedPiece},
 * {@code piece} and {@code performance} should describe the
 * <em>same musical content</em>. For Java-authored pieces this is
 * enforced by deriving {@code performance} via
 * {@link PieceConcretizer#concretize(Piece)}. For MIDI imports,
 * {@code performance} is the original MIDI-derived value and
 * {@code piece} is its structural transcription; they are
 * <em>approximately</em> equivalent (lossy in the import direction).</p>
 */
public record LoadedPiece(Piece piece, Performance performance, Origin origin) {

    public LoadedPiece {
        Objects.requireNonNull(piece,       "piece");
        Objects.requireNonNull(performance, "performance");
        Objects.requireNonNull(origin,      "origin");
    }

    /** Provenance — what produced this {@code LoadedPiece}. */
    public sealed interface Origin {

        /** Java-authored: a {@link PieceContentProvider} (with index for variants). */
        record Provider(PieceContentProvider<? extends MusicalPiece> provider, int providerIndex)
                implements Origin {
            public Provider {
                Objects.requireNonNull(provider, "provider");
                if (providerIndex < 0)
                    throw new IllegalArgumentException("providerIndex must be >= 0");
            }
        }

        /** Loaded from a MIDI file via {@link music.notation.performance.MidiCodec}. */
        record Import(MidiImport raw) implements Origin {
            public Import {
                Objects.requireNonNull(raw, "raw");
            }
        }
    }

    /** Convenience: human-readable display label for the piece. */
    public String displayName() {
        return piece.title();
    }

    /** Whether this LoadedPiece came from a MIDI import (vs a Java provider). */
    public boolean isImport() {
        return origin instanceof Origin.Import;
    }
}
