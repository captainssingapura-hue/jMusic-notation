package music.notation.ui;

import music.notation.phrase.Bar;
import music.notation.phrase.GraceNote;
import music.notation.phrase.PaddingNode;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.PolyPitchNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.SimplePitchNode;
import music.notation.pitch.Pitch;
import music.notation.play.MidiMapper;
import music.notation.play.MidiPlayer;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pre-computed visualization data for a {@link PitchScroll}.
 *
 * <p>Phase 4d cutover: walks the sealed {@link Track}'s bar list directly
 * — the same value object the {@link music.notation.play.PieceConcretizer}
 * consumes for audio. UI and audio agree on note positions by construction:
 * they read identical bars from the same {@link music.notation.phrase.Phrase}
 * tree resolution.</p>
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
        var names = new ArrayList<String>();

        for (Track track : piece.tracks()) {
            names.add(track.name());
            extractNoteRects(track, track.name(), rects);
            for (Track auxTrack : track.auxTracks()) {
                extractNoteRects(auxTrack, track.name(), rects);
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

        long pickupOffset = MidiPlayer.computeLeadingPaddingTicks(piece);

        return new PitchScrollData(
                List.copyOf(rects),
                List.of(),  // lyrics dropped with legacy phrase family
                List.copyOf(names),
                piece.tracks().size(),
                minNote, maxNote,
                totalTicks, barTickWidth,
                MidiMapper.TICKS_PER_QUARTER,
                pickupOffset
        );
    }

    // ── Tick-space note-rect walker ────────────────────────────────────

    private static void extractNoteRects(Track track, String trackKey, List<NoteRect> out) {
        long tick = 0;
        for (Bar bar : track.bars()) {
            for (PhraseNode node : bar.nodes()) {
                tick = walkNode(node, tick, trackKey, out);
            }
        }
    }

    /** Walk a single phrase node; return the tick after it. */
    private static long walkNode(PhraseNode node, long tick, String trackKey,
                                 List<NoteRect> out) {
        switch (node) {
            case SimplePitchNode pn -> tick = emitPitch(pn, tick, trackKey, out);
            case PolyPitchNode pn -> tick = emitPoly(pn, tick, trackKey, out);
            case RestNode r -> tick += MidiMapper.toTicks(r.duration());
            case PaddingNode p -> tick += MidiMapper.toTicks(p.duration());
            case PercussionNote pn -> {
                int midi = pn.sound().midiNote();
                long dur = MidiMapper.toTicks(pn.duration());
                out.add(new NoteRect(tick, tick + dur, midi, trackKey, 0));
                tick += dur;
            }
            // Zero-duration markers — no advance, no rect.
            case music.notation.phrase.DynamicNode d -> {}
            case music.notation.phrase.TempoChangeNode t -> {}
            case music.notation.phrase.TempoTransitionStartNode t -> {}
            case music.notation.phrase.TempoTransitionEndNode t -> {}
            case music.notation.phrase.SubPhrase sp -> { /* legacy nesting dropped */ }
        }
        return tick;
    }

    private static long emitPitch(SimplePitchNode pn, long tick, String trackKey, List<NoteRect> out) {
        long dur = MidiMapper.toTicks(pn.duration());
        long mainDur = dur;
        if (!pn.graceNotes().isEmpty()) {
            int slots = pn.graceNotes().size() + 1;
            long graceDur = pn.equalDivision() ? dur / slots : MidiMapper.GRACE_NOTE_TICK;
            long graceTotal = 0;
            for (GraceNote g : pn.graceNotes()) {
                int gMidi = MidiMapper.toMidiNote(g.pitch());
                out.add(new NoteRect(tick, tick + graceDur, gMidi, trackKey, 0));
                tick += graceDur;
                graceTotal += graceDur;
            }
            mainDur = Math.max(dur - graceTotal, MidiMapper.GRACE_NOTE_TICK);
        }
        int midi = MidiMapper.toMidiNote(pn.pitch());
        out.add(new NoteRect(tick, tick + mainDur, midi, trackKey, 0));
        return tick + mainDur;
    }

    private static long emitPoly(PolyPitchNode pn, long tick, String trackKey, List<NoteRect> out) {
        long dur = MidiMapper.toTicks(pn.duration());
        long mainDur = dur;
        if (!pn.graceNotes().isEmpty()) {
            int slots = pn.graceNotes().size() + 1;
            long graceDur = pn.equalDivision() ? dur / slots : MidiMapper.GRACE_NOTE_TICK;
            long graceTotal = 0;
            for (GraceNote g : pn.graceNotes()) {
                int gMidi = MidiMapper.toMidiNote(g.pitch());
                out.add(new NoteRect(tick, tick + graceDur, gMidi, trackKey, 0));
                tick += graceDur;
                graceTotal += graceDur;
            }
            mainDur = Math.max(dur - graceTotal, MidiMapper.GRACE_NOTE_TICK);
        }
        for (Pitch p : pn.pitches()) {
            int midi = MidiMapper.toMidiNote(p);
            out.add(new NoteRect(tick, tick + mainDur, midi, trackKey, 0));
        }
        return tick + mainDur;
    }
}
