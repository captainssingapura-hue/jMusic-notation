package music.notation.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke + format coverage for {@link AudioRenderer}.
 *
 * <p>Tests run only on a JDK with the default soft synthesizer
 * (Gervill) — i.e. any standard OpenJDK / Oracle distribution. CI
 * matrices that use a stripped JRE without javax.sound implementations
 * may need to skip these.</p>
 */
class AudioRendererTest {

    private static final int PPQ = 480;

    /** Minimal sequence: one piano note, one second long. */
    private static Sequence singleNoteSequence() throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, PPQ);
        Track t = seq.createTrack();
        ShortMessage on = new ShortMessage();
        on.setMessage(ShortMessage.NOTE_ON, /*channel=*/ 0, /*pitch=*/ 60, /*velocity=*/ 100);
        t.add(new MidiEvent(on, 0));
        ShortMessage off = new ShortMessage();
        off.setMessage(ShortMessage.NOTE_OFF, 0, 60, 0);
        t.add(new MidiEvent(off, 2 * PPQ));   // one quarter at 120bpm = 500ms; two = 1s
        return seq;
    }

    /** Empty sequence: just an empty track. */
    private static Sequence emptySequence() throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, PPQ);
        seq.createTrack();
        return seq;
    }

    @Test
    void rendersWavWithCorrectFormat(@TempDir Path tmp) throws Exception {
        File out = tmp.resolve("smoke.wav").toFile();
        AudioRenderer.renderWav(singleNoteSequence(), SoundbankSetup.empty(), out);

        assertTrue(out.exists(), "WAV file should be written");
        assertTrue(out.length() > 0, "WAV file should be non-empty");

        AudioFileFormat info = AudioSystem.getAudioFileFormat(out);
        assertEquals(AudioFileFormat.Type.WAVE, info.getType());
        assertEquals(44100.0f, info.getFormat().getSampleRate(), 0.1f);
        assertEquals(16,       info.getFormat().getSampleSizeInBits());
        assertEquals(2,        info.getFormat().getChannels());
    }

    @Test
    void rendersExpectedFrameCountIncludingTail(@TempDir Path tmp) throws Exception {
        // Sequence ends at tick 2*PPQ = 1 second (at default 120bpm).
        // Renderer adds a 2-second tail → expected ~3 seconds total.
        File out = tmp.resolve("frames.wav").toFile();
        AudioRenderer.renderWav(singleNoteSequence(), SoundbankSetup.empty(), out);

        AudioFileFormat info = AudioSystem.getAudioFileFormat(out);
        long frames = info.getFrameLength();
        // Total ≈ 3 seconds × 44100 frames/sec = ~132 300 frames. Allow ±5%
        // for tail-rounding and any fencepost in tempo conversion.
        long expected = 3 * 44100;
        long tolerance = expected / 20;
        assertTrue(Math.abs(frames - expected) <= tolerance,
                "frame length " + frames + " not within ±5% of expected " + expected);
    }

    @Test
    void emptySequenceProducesSilenceTail(@TempDir Path tmp) throws Exception {
        // Empty sequence → 0 ms of MIDI + 2 s tail = ~2 seconds of silence.
        File out = tmp.resolve("silence.wav").toFile();
        AudioRenderer.renderWav(emptySequence(), SoundbankSetup.empty(), out);

        AudioFileFormat info = AudioSystem.getAudioFileFormat(out);
        long frames = info.getFrameLength();
        long expected = 2 * 44100;   // 2 seconds tail only
        long tolerance = expected / 20;
        assertTrue(Math.abs(frames - expected) <= tolerance,
                "silence-only frame length " + frames + " not within ±5% of expected " + expected);
    }
}
