package music.notation.mixer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.io.File;

/**
 * A single track in the mixer session: a loaded WAV plus its observable
 * mix-time controls (gain in dB, mute, future: time offset).
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Created when the user adds a WAV via the file picker. The WAV is
 * streamed once at add time into a {@link WaveformData} overview (for
 * display) and otherwise referenced only by {@link #file()} (for the
 * eventual mix and playback). The data is never held fully in memory.</p>
 *
 * <h2>Gain semantics</h2>
 *
 * <p>Gain is stored as a linear multiplier in {@code [0, 4]}. Mute is a
 * boolean overlay: when true, the mixer treats the track's gain as zero
 * regardless of the slider value.</p>
 */
public final class MixTrack {

    private final File file;
    private final WaveformData waveform;
    private final DoubleProperty gain  = new SimpleDoubleProperty(1.0);
    private final BooleanProperty muted = new SimpleBooleanProperty(false);

    public MixTrack(File file, WaveformData waveform) {
        this.file = file;
        this.waveform = waveform;
    }

    public File         file()              { return file; }
    public WaveformData waveform()          { return waveform; }
    public DoubleProperty gainProperty()    { return gain; }
    public BooleanProperty mutedProperty()  { return muted; }

    /** Effective gain for the mixer: zero when muted, otherwise the slider value. */
    public float effectiveGain() {
        return muted.get() ? 0f : (float) gain.get();
    }

    public String displayName() {
        return file.getName();
    }
}
