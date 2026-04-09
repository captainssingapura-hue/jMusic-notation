package music.notation.ui;

import music.notation.duration.Duration;
import music.notation.phrase.*;
import music.notation.play.MidiMapper;
import music.notation.play.MidiPlayer;
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
        List<LyricRect> lyricRects,
        List<String> trackNames,
        int trackCount,
        int minNote,
        int maxNote,
        long totalTicks,
        long barTickWidth,
        int ticksPerQuarter,
        long pickupOffsetTicks
) {

    /** Build visualization data from a {@link Piece}. */
    static PitchScrollData fromPiece(Piece piece) {
        var rects = new ArrayList<NoteRect>();
        var lyrics = new ArrayList<LyricRect>();
        var names = new ArrayList<String>();

        // Separate lyrics tracks from audio tracks
        var audioTracks = new ArrayList<Track>();
        for (Track track : piece.tracks()) {
            boolean hasLyrics = track.phrases().stream()
                    .anyMatch(p -> p instanceof LyricPhrase);
            if (hasLyrics) {
                extractLyrics(track, lyrics);
            } else {
                audioTracks.add(track);
            }
        }

        for (int i = 0; i < audioTracks.size(); i++) {
            Track track = audioTracks.get(i);
            names.add(track.name());
            extractNoteRects(track, i, false, rects);
            // Aux tracks share the parent's lane index
            for (Track auxTrack : track.auxTracks()) {
                extractNoteRects(auxTrack, i, true, rects);
            }
        }

        rects.sort(Comparator.comparingLong(NoteRect::startTick));
        lyrics.sort(Comparator.comparingLong(LyricRect::startTick));

        int min = 127, max = 0;
        for (NoteRect r : rects) {
            if (r.midiNote() < min) min = r.midiNote();
            if (r.midiNote() > max) max = r.midiNote();
        }
        int minNote = Math.max(0, min - 2);
        int maxNote = Math.min(127, max + 2);
        long totalTicks = rects.isEmpty() ? 0 : rects.getLast().endTick();
        long barTickWidth = (long) piece.timeSig().barSixtyFourths() * MidiMapper.TICKS_PER_QUARTER / 16;

        long pickupOffset = MidiPlayer.computeLeadingPaddingTicks(piece);

        return new PitchScrollData(
                List.copyOf(rects),
                List.copyOf(lyrics),
                List.copyOf(names),
                audioTracks.size(),
                minNote, maxNote,
                totalTicks, barTickWidth,
                MidiMapper.TICKS_PER_QUARTER,
                pickupOffset
        );
    }

    /** Extract note rectangles from a track into the given list. */
    private static void extractNoteRects(Track track, int trackIndex, boolean aux, List<NoteRect> out) {
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
                        out.add(new NoteRect(start, off.tick(), off.midiNote(), trackIndex, aux));
                    }
                }
                case PlayEvent.ProgramChange ignored -> {}
            }
        }
    }

    /** Extract timed syllables from a lyrics track. */
    private static void extractLyrics(Track track, List<LyricRect> out) {
        long tick = 0;
        for (Phrase phrase : track.phrases()) {
            if (phrase instanceof LyricPhrase lp) {
                for (LyricEvent event : lp.syllables()) {
                    long dur = MidiMapper.toTicks(event.duration());
                    if (!event.syllable().isEmpty()) {
                        out.add(new LyricRect(tick, tick + dur, event.syllable()));
                    }
                    tick += dur;
                }
                tick += boundaryGapTicks(lp.marking());
            } else {
                tick += MidiMapper.toTicks(
                        Duration.ofSixtyFourths(Bar.phraseSixtyFourths(phrase)));
                tick += boundaryGapTicks(phrase.marking());
            }
        }
    }

    private static long boundaryGapTicks(PhraseMarking marking) {
        return switch (marking.connection()) {
            case BREATH  -> MidiMapper.TICKS_PER_QUARTER / 4;
            case CAESURA -> MidiMapper.TICKS_PER_QUARTER;
            case ATTACCA -> 0;
            case ELISION -> 0;
        };
    }
}
