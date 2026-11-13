package music.notation.transcribe;

import music.notation.expressivity.Articulations;
import music.notation.expressivity.Pedaling;
import music.notation.expressivity.TrackId;
import music.notation.expressivity.Velocities;
import music.notation.expressivity.VelocityChange;
import music.notation.expressivity.VelocityControl;
import music.notation.expressivity.Volume;
import music.notation.mxl.MxlImport;
import music.notation.performance.ConcreteNote;
import music.notation.performance.Instrumentation;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.TempoTrack;
import music.notation.performance.Track;
import music.notation.performance.TrackKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A {@link Transcription} as a one-track piece — the same shape the
 * recorder saves, so the split-JSON folder opens in the notation apps.
 */
public final class TranscriptionPiece {

    public static final TrackId TRACK = new TrackId("vocal");

    private TranscriptionPiece() {}

    public static Performance performance(Transcription t) {
        var notes = new ArrayList<ConcreteNote>();
        var velocity = new ArrayList<VelocityChange>();
        for (QuantizedNote q : t.quantized()) {
            notes.add(new PitchedNote(q.at(), q.duration(), q.midi()));
            velocity.add(new VelocityChange(q.at(), q.velocity() / 127.0));
        }
        Track track = new Track(TRACK, TrackKind.PITCHED, notes);
        Velocities velocities = velocity.isEmpty() ? new Velocities(Map.of())
                : new Velocities(Map.of(TRACK, new VelocityControl(velocity)));
        return new Performance(new Score(List.of(track)), TempoTrack.constant(t.request().timing().bpm()),
                Instrumentation.empty(), Volume.empty(), Articulations.empty(), Pedaling.empty(), velocities);
    }

    public static MxlImport toImport(Transcription t) {
        return new MxlImport(t.title(), performance(t), t.request().timing().timeSignature(), t.key(), "");
    }
}
