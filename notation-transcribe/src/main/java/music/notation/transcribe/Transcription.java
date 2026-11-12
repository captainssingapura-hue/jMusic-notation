package music.notation.transcribe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import music.notation.structure.KeySignature;

import java.util.List;

/**
 * The result of a run: the notes as sung (real time) and as written
 * (on the grid), with the context needed to read them.
 *
 * @param request        what was asked for (with defaults filled in)
 * @param fromSec        start of the analysed audio, file time
 * @param toSec          end of the analysed audio, file time
 * @param downbeatSec    file time used as beat 1 of the first full bar
 * @param key            key written into the piece
 * @param keyEstimated   true when {@code key} came from {@link KeyEstimator}
 * @param voicedFraction share of frames the detector found a pitch in
 * @param notes          segmented notes, real time
 * @param quantized      the same notes on the bar grid
 * @param pickupBars     bars added before the downbeat for early notes
 * @param dropped        notes lost to onset collisions on the grid
 * @param pitchTrack     frame-by-frame detector output (not in the JSON;
 *                       written separately as CSV)
 */
public record Transcription(TranscriptionRequest request, double fromSec, double toSec, double downbeatSec,
                            KeySignature key, boolean keyEstimated, double voicedFraction,
                            List<TranscribedNote> notes, List<QuantizedNote> quantized,
                            int pickupBars, int dropped,
                            @JsonIgnore PitchTrack pitchTrack) {

    public Transcription {
        notes = List.copyOf(notes);
        quantized = List.copyOf(quantized);
    }

    public String title() { return request.title(); }
}
