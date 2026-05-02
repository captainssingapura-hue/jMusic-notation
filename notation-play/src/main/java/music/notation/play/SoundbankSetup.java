package music.notation.play;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import java.io.File;
import java.util.List;

/**
 * Per-app SoundFont layering. Each file is loaded in order onto the
 * synthesizer's instrument table; later files override earlier ones at
 * conflicting {@code (bank, program)} tuples. Java's default soundbank
 * stays loaded as the fallback for any patch the layered files don't
 * supply.
 *
 * <p>Empty list = pure Java default soundbank. Missing or unsupported
 * files are silently skipped so a stale stored path doesn't break
 * playback.</p>
 */
public record SoundbankSetup(List<File> files) {

    public SoundbankSetup {
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static SoundbankSetup empty() { return new SoundbankSetup(List.of()); }

    public static SoundbankSetup of(File... files) { return new SoundbankSetup(List.of(files)); }

    /** Number of files that actually loaded successfully. */
    public int apply(Synthesizer synth) {
        if (synth == null || files.isEmpty()) return 0;
        int loaded = 0;
        for (File f : files) {
            if (f == null || !f.isFile()) continue;
            try {
                Soundbank sb = MidiSystem.getSoundbank(f);
                if (synth.isSoundbankSupported(sb)) {
                    synth.loadAllInstruments(sb);
                    loaded++;
                }
            } catch (Exception ignored) {
                // Skip any file that fails to parse; the previous load order remains effective.
            }
        }
        return loaded;
    }
}
