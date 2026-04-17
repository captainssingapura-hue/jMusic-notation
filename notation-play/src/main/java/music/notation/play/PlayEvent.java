package music.notation.play;

public sealed interface PlayEvent permits PlayEvent.NoteOn, PlayEvent.NoteOff, PlayEvent.ProgramChange, PlayEvent.TempoChange {

    long tick();

    record NoteOn(long tick, int midiNote, int velocity) implements PlayEvent {}

    record NoteOff(long tick, int midiNote) implements PlayEvent {}

    record ProgramChange(long tick, int program) implements PlayEvent {}

    record TempoChange(long tick, int bpm) implements PlayEvent {}
}
