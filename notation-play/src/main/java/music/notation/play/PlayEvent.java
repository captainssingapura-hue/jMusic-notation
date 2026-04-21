package music.notation.play;

public sealed interface PlayEvent permits PlayEvent.NoteOn, PlayEvent.NoteOff, PlayEvent.ProgramChange, PlayEvent.TempoChange {

    long tick();

    /**
     * A note-on. The {@code voice} index identifies which voice within the
     * owning track produced this event: {@code 0} is the main line, {@code 1}
     * is the first {@link music.notation.phrase.VoiceOverlay}, and so on.
     * MIDI output ignores {@code voice} (all voices share a channel); the UI
     * uses it to color/tag overlay notes distinctly.
     */
    record NoteOn(long tick, int midiNote, int velocity, int voice) implements PlayEvent {
        /** Convenience: main-voice note-on (voice = 0). */
        public NoteOn(long tick, int midiNote, int velocity) {
            this(tick, midiNote, velocity, 0);
        }
    }

    record NoteOff(long tick, int midiNote, int voice) implements PlayEvent {
        /** Convenience: main-voice note-off (voice = 0). */
        public NoteOff(long tick, int midiNote) {
            this(tick, midiNote, 0);
        }
    }

    record ProgramChange(long tick, int program) implements PlayEvent {}

    record TempoChange(long tick, int bpm) implements PlayEvent {}
}
