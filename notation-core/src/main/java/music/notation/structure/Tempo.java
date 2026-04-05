package music.notation.structure;

import music.notation.duration.BaseValue;

public record Tempo(int bpm, BaseValue beatUnit) {
    public Tempo {
        if (bpm < 1) {
            throw new IllegalArgumentException("BPM must be positive: " + bpm);
        }
    }
}
