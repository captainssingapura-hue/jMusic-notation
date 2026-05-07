package music.notation.play;

import music.notation.performance.MidiImport;
import music.notation.performance.PerformanceImporter;
import music.notation.structure.MusicalPiece;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;

/**
 * Static factories that build {@link LoadedPiece} instances from
 * each supported origin.
 *
 * <p>Single source of truth for the "piece + sidecar performance"
 * pairing. Callers anywhere in the UI / playback pipeline should
 * obtain LoadedPieces here rather than constructing the pair
 * themselves; this guarantees the invariant that {@code piece} and
 * {@code performance} describe the same content.</p>
 */
public final class LoadedPieces {

    private LoadedPieces() {}

    /**
     * From a Java-authored {@link PieceContentProvider}: instantiate
     * the {@link Piece}, then concretise it to a {@link
     * music.notation.performance.Performance} for the sidecar.
     *
     * @param provider     the content provider (e.g. a song class).
     * @param providerIndex which variant of the provider was selected
     *                      (0 for the default provider).
     */
    public static LoadedPiece fromProvider(
            PieceContentProvider<? extends MusicalPiece> provider, int providerIndex) {
        Piece piece = provider.create();
        var perf    = PieceConcretizer.concretize(piece);
        return new LoadedPiece(piece, perf,
                new LoadedPiece.Origin.Provider(provider, providerIndex));
    }

    /** Convenience overload defaulting providerIndex to 0. */
    public static LoadedPiece fromProvider(
            PieceContentProvider<? extends MusicalPiece> provider) {
        return fromProvider(provider, 0);
    }

    /**
     * From a freshly-loaded MIDI {@link MidiImport}: derive the
     * structural {@link Piece} via {@link PerformanceImporter}, and
     * keep the original {@code Performance} as the sidecar so
     * playback/export are exactly faithful to the source bytes.
     *
     * <p>Defaults to {@link PerformanceImporter.SplitMode#PRESERVE}:
     * 1 input track → 1 output {@link Piece} track. Use
     * {@link #fromImport(MidiImport, PerformanceImporter.SplitMode)}
     * to opt into voice splitting.</p>
     */
    public static LoadedPiece fromImport(MidiImport imp) {
        return fromImport(imp, PerformanceImporter.SplitMode.PRESERVE);
    }

    /** Variant with explicit {@link PerformanceImporter.SplitMode}. */
    public static LoadedPiece fromImport(MidiImport imp,
                                         PerformanceImporter.SplitMode mode) {
        return fromImport(imp, mode,
                music.notation.performance.QuantizerProfile.STANDARD);
    }

    /**
     * Full-control variant with both {@link PerformanceImporter.SplitMode}
     * and {@link music.notation.performance.QuantizerProfile}. Profile
     * controls which durations the quantizer recognises (e.g. add
     * triplets / quintuplets for tuplet-heavy material).
     */
    public static LoadedPiece fromImport(MidiImport imp,
                                         PerformanceImporter.SplitMode mode,
                                         music.notation.performance.QuantizerProfile profile) {
        Piece piece = PerformanceImporter.toPiece(imp, mode, profile);
        return new LoadedPiece(piece, imp.performance(),
                new LoadedPiece.Origin.Import(imp));
    }
}
