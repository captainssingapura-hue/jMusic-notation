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
            // Voice overlays are already carried on each MelodicPhrase and
            // emitted by the interpreter with a non-zero `voice` index —
            // one pass over the track is all that's needed.
            extractNoteRects(track, track.name(), rects);
            // Legacy auxTracks (cross-instrument parallel voices) — still
            // rendered at voice 0 under the parent's track key.
            for (Track auxTrack : track.auxTracks()) {
                extractNoteRects(auxTrack, track.name(), rects);
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

    /**
     * Extract note rectangles from a track into the given list. Each emitted
     * event carries its own {@code voice} index (0 = main line, 1..N = voice
     * overlays); the rect inherits it directly.
     */
    private static void extractNoteRects(Track track, String trackKey, List<NoteRect> out) {
        PhraseInterpreter interpreter = new PhraseInterpreter(0, 80, 120);
        for (Phrase phrase : track.phrases()) {
            interpreter.interpret(phrase);
        }
        // Pending key = (midi << 8) | voice so concurrent notes on the same
        // pitch but different voices don't collide.
        var pending = new HashMap<Integer, Long>();
        for (PlayEvent event : interpreter.getEvents()) {
            switch (event) {
                case PlayEvent.NoteOn on ->
                        pending.put((on.midiNote() << 8) | (on.voice() & 0xFF), on.tick());
                case PlayEvent.NoteOff off -> {
                    int key = (off.midiNote() << 8) | (off.voice() & 0xFF);
                    Long start = pending.remove(key);
                    if (start != null) {
                        out.add(new NoteRect(start, off.tick(), off.midiNote(),
                                trackKey, off.voice()));
                    }
                }
                case PlayEvent.ProgramChange ignored -> {}
                case PlayEvent.TempoChange ignored -> {}
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
