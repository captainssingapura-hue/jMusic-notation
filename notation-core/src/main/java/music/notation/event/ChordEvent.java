package music.notation.event;

import music.notation.duration.Duration;
import music.notation.pitch.Pitch;

import java.util.List;

public record ChordEvent(List<Pitch> pitches, Duration duration, List<Articulation> articulations) {
    public ChordEvent {
        if (pitches.size() < 2) {
            throw new IllegalArgumentException("Chord requires at least 2 pitches");
        }
        pitches = List.copyOf(pitches);
        articulations = List.copyOf(articulations);
    }
}
