package music.notation.ui;

import music.notation.phrase.Phrase;
import music.notation.play.MidiMapper;
import music.notation.play.PlayEvent;
import music.notation.play.PhraseInterpreter;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import java.util.*;

/**
 * Pre-computed visualization data for a {@link PitchScroll}.
 * Decouples the scroll renderer from the music domain model.
 */
record PitchScrollData(
        List<NoteRect> noteRects,
        List<String> trackNames,
        int trackCount,
        int minNote,
        int maxNote,
        long totalTicks,
        long barTickWidth,
        int ticksPerQuarter
) {

    /** Build visualization data from a {@link Piece}. */
    static PitchScrollData fromPiece(Piece piece) {
        var rects = new ArrayList<NoteRect>();
        var names = new ArrayList<String>();
        List<Track> tracks = piece.tracks();

        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            names.add(track.name());

            PhraseInterpreter interpreter = new PhraseInterpreter(0, 80);
            for (Phrase phrase : track.phrases()) {
                interpreter.interpret(phrase);
            }

            var pending = new HashMap<Integer, Long>();
            for (PlayEvent event : interpreter.getEvents()) {
                switch (event) {
                    case PlayEvent.NoteOn on -> pending.put(on.midiNote(), on.tick());
                    case PlayEvent.NoteOff off -> {
                        Long start = pending.remove(off.midiNote());
                        if (start != null) {
                            rects.add(new NoteRect(start, off.tick(), off.midiNote(), i));
                        }
                    }
                    case PlayEvent.ProgramChange ignored -> {}
                }
            }
        }

        rects.sort(Comparator.comparingLong(NoteRect::startTick));

        int min = 127, max = 0;
        for (NoteRect r : rects) {
            if (r.midiNote() < min) min = r.midiNote();
            if (r.midiNote() > max) max = r.midiNote();
        }
        int minNote = Math.max(0, min - 2);
        int maxNote = Math.min(127, max + 2);
        long totalTicks = rects.isEmpty() ? 0 : rects.getLast().endTick();
        long barTickWidth = (long) piece.timeSig().barSixtyFourths() * MidiMapper.TICKS_PER_QUARTER / 16;

        return new PitchScrollData(
                List.copyOf(rects),
                List.copyOf(names),
                tracks.size(),
                minNote, maxNote,
                totalTicks, barTickWidth,
                MidiMapper.TICKS_PER_QUARTER
        );
    }
}
