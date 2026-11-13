package music.notation.transcribe;

/**
 * A note found in the pitch curve, before quantisation — real time, not
 * musical time.
 *
 * @param startMs    onset, file time
 * @param endMs      release, file time
 * @param midi       nearest MIDI pitch
 * @param cents      how far the sung pitch sat from {@code midi}
 *                   (−50 … +50): negative = flat
 * @param confidence mean detector confidence over the note
 * @param peakRms    loudest frame of the note (0–1 full scale)
 */
public record TranscribedNote(double startMs, double endMs, int midi, double cents,
                              double confidence, double peakRms) {

    public TranscribedNote {
        if (!(endMs > startMs)) throw new IllegalArgumentException("note must have positive length: " + startMs + "–" + endMs);
        if (midi < 0 || midi > 127) throw new IllegalArgumentException("midi out of range: " + midi);
    }

    public double durationMs() { return endMs - startMs; }
}
