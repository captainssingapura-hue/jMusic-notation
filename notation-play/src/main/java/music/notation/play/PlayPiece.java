package music.notation.play;

import music.notation.duration.Duration;
import music.notation.phrase.*;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import music.notation.structure.Track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Simple utility to play a {@link Piece} from a {@link PieceContentProvider}
 * via MIDI, blocking until playback finishes.
 *
 * <p>If any track contains {@link LyricPhrase}s, syllables are printed
 * to the console in real time — a basic karaoke display.</p>
 *
 * <p>Intended for quick testing from a provider's own {@code main} method:
 * <pre>{@code
 *     public static void main(String[] args) throws Exception {
 *         PlayPiece.play(new DefaultTwinkleStar());
 *     }
 * }</pre>
 */
public final class PlayPiece {

    private PlayPiece() {}

    public static void play(final PieceContentProvider<?> provider) throws Exception {
        final Piece piece = provider.create();

        System.out.println("Playing: " + piece.title() + " by " + piece.composer());
        System.out.println("  Key: " + piece.key().tonic() + " " + piece.key().mode()
                + "  |  Time: " + piece.timeSig().beats() + "/" + piece.timeSig().beatValue()
                + "  |  Tempo: " + piece.tempo().bpm() + " BPM");
        System.out.println("  Tracks: " + piece.tracks().stream()
                .map(t -> t.name() + " (" + t.defaultInstrument() + ")")
                .toList());
        System.out.println();

        var timeline = buildLyricTimeline(piece);
        final var player = new MidiPlayer();
        player.start(piece);

        int idx = 0;
        while (player.isPlaying()) {
            long tick = player.getTickPosition();
            while (idx < timeline.size() && timeline.get(idx).tick() <= tick) {
                var entry = timeline.get(idx);
                if (entry.newLine() && idx > 0) {
                    System.out.println();
                }
                System.out.print(entry.syllable());
                System.out.flush();
                idx++;
            }
            Thread.sleep(50);
        }

        // Print any remaining syllables (in case playback ended slightly early)
        while (idx < timeline.size()) {
            var entry = timeline.get(idx);
            if (entry.newLine() && idx > 0) System.out.println();
            System.out.print(entry.syllable());
            idx++;
        }
        if (!timeline.isEmpty()) System.out.println();

        Thread.sleep(500);
        player.stop();
    }

    // ── Lyric timeline ────────────────────────────────────────────────

    private record LyricTick(long tick, String syllable, boolean newLine) {}

    /**
     * Walk all tracks, extract {@link LyricPhrase}s, and build a sorted
     * timeline of (tick, syllable, newLine) entries.
     */
    private static List<LyricTick> buildLyricTimeline(Piece piece) {
        long barTicks = MidiMapper.toTicks(
                Duration.ofSixtyFourths(piece.timeSig().barSixtyFourths()));
        var timeline = new ArrayList<LyricTick>();

        for (Track track : piece.tracks()) {
            long tick = 0;
            boolean firstLyricPhrase = true;

            for (Phrase phrase : track.phrases()) {
                if (phrase instanceof LyricPhrase lp) {
                    long phraseStart = tick;
                    for (LyricEvent event : lp.syllables()) {
                        if (!event.syllable().isEmpty()) {
                            // New line at phrase boundaries, and every 2 bars within a phrase
                            boolean newLine;
                            if (tick == phraseStart && !firstLyricPhrase) {
                                newLine = true;
                            } else {
                                long barsIntoPhrase = (tick - phraseStart) / barTicks;
                                long prevBars = barsIntoPhrase > 0
                                        ? (tick - phraseStart - 1) / barTicks : -1;
                                newLine = tick > phraseStart
                                        && barsIntoPhrase > 0
                                        && barsIntoPhrase % 2 == 0
                                        && barsIntoPhrase != prevBars;
                            }
                            timeline.add(new LyricTick(tick, event.syllable(), newLine));
                        }
                        tick += MidiMapper.toTicks(event.duration());
                    }
                    tick += boundaryGapTicks(lp.marking());
                    firstLyricPhrase = false;
                } else {
                    tick += phraseDurationTicks(phrase);
                    tick += boundaryGapTicks(phrase.marking());
                }
            }
        }

        timeline.sort(Comparator.comparingLong(LyricTick::tick));
        return timeline;
    }

    private static long phraseDurationTicks(Phrase phrase) {
        final int sf = Bar.phraseSixtyFourths(phrase);
        // Zero-duration phrases (e.g. tempo-marker phrases containing only a
        // TempoChangeNode) advance the timeline by 0 ticks.
        return sf > 0 ? MidiMapper.toTicks(Duration.ofSixtyFourths(sf)) : 0L;
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
