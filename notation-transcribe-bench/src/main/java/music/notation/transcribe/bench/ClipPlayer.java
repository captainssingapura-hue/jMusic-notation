package music.notation.transcribe.bench;

import music.notation.transcribe.AudioClip;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

/** Plays an {@link AudioClip} (or part of it) and reports the file time being heard. */
final class ClipPlayer {

    private Clip clip;
    private double startSec;

    /** Open the audio from {@code fromSec} (file time) to the end, ready to {@link #start}. */
    void prepare(AudioClip audio, double fromSec) throws LineUnavailableException {
        stop();
        int from = (int) Math.max(0, Math.min(audio.length(), Math.round((fromSec - audio.offsetSec()) * audio.sampleRate())));
        byte[] data = Pcm.toBytes(audio.samples(), from, audio.length());
        clip = AudioSystem.getClip();
        clip.open(Pcm.format(audio.sampleRate()), data, 0, data.length);
        startSec = audio.offsetSec() + (double) from / audio.sampleRate();
    }

    void start() { if (clip != null) clip.start(); }

    boolean isPlaying() { return clip != null && clip.isRunning(); }

    /** File time currently playing, or {@code NaN} when idle. */
    double positionSec() {
        return clip == null ? Double.NaN : startSec + clip.getMicrosecondPosition() / 1e6;
    }

    void stop() {
        if (clip != null) { clip.stop(); clip.close(); clip = null; }
    }
}
