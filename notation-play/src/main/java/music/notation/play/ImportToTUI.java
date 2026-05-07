package music.notation.play;

import music.notation.performance.MidiCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI showcase for the MIDI-import pipeline. Reads a .mid file,
 * routes it through {@link music.notation.performance.PerformanceImporter},
 * and dumps the resulting voice-split {@code Piece} via
 * {@link TUIPianoRoll} so you can eyeball the tracks the splitter
 * produced.
 *
 * <p>Usage: {@code java ImportToTUI <file.mid>}</p>
 */
public final class ImportToTUI {

    private ImportToTUI() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java ImportToTUI <file.mid>");
            System.exit(1);
        }
        Path file = Path.of(args[0]);
        byte[] bytes = Files.readAllBytes(file);
        String name = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");

        var imp = MidiCodec.fromMidiWithMeta(bytes, name);
        var loaded = LoadedPieces.fromImport(imp);
        var piece  = loaded.piece();

        System.out.println("════════════════════════════════════════════════════════");
        System.out.printf(" %s%n", piece.title());
        System.out.printf(" key=%s  time=%d/%d  tempo=%d bpm%n",
                piece.key(), piece.timeSig().beats(), piece.timeSig().beatValue(),
                piece.tempo().bpm());
        System.out.printf(" source tracks: %d%n",
                imp.performance().score().tracks().size());
        System.out.printf(" voice-split tracks: %d%n", piece.tracks().size());
        System.out.println("════════════════════════════════════════════════════════");
        for (int i = 0; i < piece.tracks().size(); i++) {
            var t = piece.tracks().get(i);
            System.out.printf("   [%d] %s   (%d bars)%n", i, t.name(), t.bars().size());
        }
        System.out.println("════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println(TUIPianoRoll.render(piece).format());
    }
}
