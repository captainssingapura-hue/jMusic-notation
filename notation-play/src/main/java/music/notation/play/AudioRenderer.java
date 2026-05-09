package music.notation.play;

import com.sun.media.sound.AudioSynthesizer;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders a MIDI {@link Sequence} into rendered audio (PCM samples) by
 * driving the JDK's default soft synthesizer (Gervill) in <b>offline</b>
 * mode — faster-than-realtime, no audio output line.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Open a {@link Synthesizer} via {@link MidiSystem#getSynthesizer()}.
 *       On a JDK-default install this is a {@code SoftSynthesizer}, which
 *       implements an {@code openStream(AudioFormat, Map)} method that
 *       returns an {@link AudioInputStream} of rendered PCM samples.</li>
 *   <li>Apply the user's {@link SoundbankSetup} (same SF2 layering the
 *       live playback path uses), so the rendered audio matches what the
 *       user hears.</li>
 *   <li>Walk the Sequence's events in tick order, converting ticks to
 *       microseconds via the embedded tempo meta events, and dispatch
 *       each event to the synth's {@link Receiver} with the computed
 *       timestamp. The synth queues events by timestamp and renders them
 *       as the audio stream is read.</li>
 *   <li>Wrap the synth's "infinite" stream in a frame-bounded
 *       {@link AudioInputStream} sized to the sequence's duration plus a
 *       2-second tail (for reverb / release decay), then write to disk
 *       via {@link AudioSystem#write}.</li>
 * </ol>
 *
 * <h2>Module access note</h2>
 *
 * <p>{@link AudioSynthesizer} lives in {@code com.sun.media.sound}
 * (module {@code java.desktop}), which is not exported by default in
 * Java 9+. The {@code notation-play} pom adds
 * {@code --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED}
 * for compile + test; the {@code notation-ui} javafx-maven-plugin
 * config adds the same for runtime. Any new module that calls into
 * audio rendering needs the same flag.</p>
 *
 * <h2>Same audio path as live playback</h2>
 *
 * <p>The renderer consumes whatever {@link Sequence} the caller hands
 * in. Channel setup, side-channel pedaling, velocities, and the
 * soundbank are all already baked into that sequence by
 * {@link MidiPlayer#freezeForExport} and {@link PedalInjector} — same
 * code paths the .mid file export uses. Audio export is therefore
 * bit-identical-equivalent to playing the .mid through the same SF2
 * in real time.</p>
 */
public final class AudioRenderer {

    /** CD-quality default. Sample rate, bit depth, channels, signed, little-endian. */
    public static final int    DEFAULT_SAMPLE_RATE   = 44100;
    public static final int    DEFAULT_BIT_DEPTH     = 16;
    public static final int    DEFAULT_CHANNELS      = 2;

    /**
     * Tail length appended after the last MIDI event — catches reverb
     * decay, release envelopes, and trailing CC effects (sustain
     * release etc.). Empirically chosen.
     */
    private static final long TAIL_MICROS = 2_000_000L;

    /** Default tempo used before any tempo meta event — 120 bpm = 500 000 μs / quarter. */
    private static final long DEFAULT_TEMPO_MICROS_PER_QUARTER = 500_000L;

    /** MIDI tempo meta event type. */
    private static final int META_TEMPO = 0x51;

    private AudioRenderer() {}

    /**
     * Render {@code sequence} to a 44.1 kHz / 16-bit / stereo WAV file.
     * Synchronous; returns when the file is fully written.
     *
     * @param sequence       a self-contained {@link Sequence} (typically
     *                       from {@link MidiPlayer#freezeForExport})
     * @param soundbankSetup SF2 layering to apply to the offline synth;
     *                       null or empty → JDK default soundbank
     * @param outputFile     destination file
     * @throws Exception     wraps any synth / IO / reflection failure
     */
    public static void renderWav(Sequence sequence,
                                  SoundbankSetup soundbankSetup,
                                  File outputFile) throws Exception {
        AudioFormat format = new AudioFormat(
                DEFAULT_SAMPLE_RATE, DEFAULT_BIT_DEPTH, DEFAULT_CHANNELS,
                /*signed=*/ true, /*bigEndian=*/ false);
        renderWav(sequence, soundbankSetup, outputFile, format);
    }

    /**
     * Variant accepting an explicit {@link AudioFormat} — used by the
     * future FLAC / MP3 phases that may pick higher rates.
     */
    public static void renderWav(Sequence sequence,
                                  SoundbankSetup soundbankSetup,
                                  File outputFile,
                                  AudioFormat format) throws Exception {
        if (sequence == null) throw new IllegalArgumentException("sequence required");
        if (outputFile == null) throw new IllegalArgumentException("outputFile required");

        Synthesizer synth = MidiSystem.getSynthesizer();
        if (!(synth instanceof AudioSynthesizer audioSynth)) {
            throw new UnsupportedOperationException(
                    "Audio rendering requires an AudioSynthesizer (Gervill SoftSynthesizer) — got "
                            + synth.getClass().getName());
        }
        // Note: do NOT call synth.open() before openStream — that opens the
        // synth on the system audio output (real-time mode). openStream
        // both opens the synth and routes its output to the returned
        // AudioInputStream (offline mode).
        AudioInputStream rawStream = audioSynth.openStream(format, /*info=*/ null);
        try {
            if (soundbankSetup != null) soundbankSetup.apply(audioSynth);

            dispatchEvents(sequence, audioSynth.getReceiver());

            long durationMicros = sequence.getMicrosecondLength() + TAIL_MICROS;
            long totalFrames = framesForDuration(durationMicros, format.getSampleRate());

            try (AudioInputStream bounded = new AudioInputStream(
                    rawStream, format, totalFrames)) {
                AudioSystem.write(bounded, AudioFileFormat.Type.WAVE, outputFile);
            }
        } finally {
            try { rawStream.close(); } catch (Exception ignored) {}
            audioSynth.close();
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────

    /**
     * Walk all events in the sequence in tick order, convert each tick
     * to microseconds via the embedded tempo curve, and dispatch to the
     * synth's receiver with that timestamp.
     */
    private static void dispatchEvents(Sequence sequence, Receiver receiver) {
        int ppq = sequence.getResolution();
        if (ppq <= 0) {
            throw new UnsupportedOperationException(
                    "Sequence must use PPQ resolution (got " + ppq + ")");
        }

        List<MidiEvent> all = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                all.add(track.get(i));
            }
        }
        all.sort(Comparator.comparingLong(MidiEvent::getTick));

        long currentMicros = 0L;
        long lastTick = 0L;
        long currentTempo = DEFAULT_TEMPO_MICROS_PER_QUARTER;

        for (MidiEvent ev : all) {
            long tick = ev.getTick();
            long deltaTicks = tick - lastTick;
            if (deltaTicks > 0) {
                currentMicros += deltaTicks * currentTempo / ppq;
                lastTick = tick;
            }
            // Tempo meta events update the rate going forward (after we've
            // accounted for the time up to this tick using the previous tempo).
            if (ev.getMessage() instanceof MetaMessage mm && mm.getType() == META_TEMPO) {
                byte[] d = mm.getData();
                if (d.length >= 3) {
                    currentTempo = ((d[0] & 0xFFL) << 16)
                                 | ((d[1] & 0xFFL) << 8)
                                 |  (d[2] & 0xFFL);
                }
            }
            receiver.send(ev.getMessage(), currentMicros);
        }
    }

    private static long framesForDuration(long micros, float sampleRate) {
        return Math.max(0, Math.round(micros / 1_000_000.0 * sampleRate));
    }
}
