package music.notation.transcribe.bench;

import music.notation.transcribe.AudioClip;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.function.DoubleConsumer;

/** Records the default microphone as 44.1 kHz 16-bit mono until stopped. */
final class TakeRecorder {

    static final int RATE = 44_100;

    private TargetDataLine line;
    private Thread worker;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private volatile boolean running;

    /** @param level called from the capture thread with the RMS of each block (0–1) */
    void start(DoubleConsumer level) throws LineUnavailableException {
        var info = new DataLine.Info(TargetDataLine.class, Pcm.format(RATE));
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(Pcm.format(RATE), RATE / 5);
        buffer.reset();
        running = true;
        line.start();
        worker = new Thread(() -> {
            byte[] block = new byte[RATE / 20 * 2];                    // 50 ms
            while (running) {
                int n = line.read(block, 0, block.length);
                if (n <= 0) continue;
                synchronized (buffer) { buffer.write(block, 0, n); }
                float[] x = Pcm.fromBytes(block, n);
                double s = 0;
                for (float v : x) s += v * v;
                level.accept(Math.sqrt(s / Math.max(1, x.length)));
            }
        }, "take-recorder");
        worker.setDaemon(true);
        worker.start();
    }

    boolean isRecording() { return running; }

    double seconds() {
        synchronized (buffer) { return buffer.size() / 2.0 / RATE; }
    }

    AudioClip stop() {
        running = false;
        if (line != null) { line.stop(); line.close(); }
        try { if (worker != null) worker.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        byte[] all;
        synchronized (buffer) { all = buffer.toByteArray(); }
        return new AudioClip(RATE, Pcm.fromBytes(all, all.length));
    }
}
