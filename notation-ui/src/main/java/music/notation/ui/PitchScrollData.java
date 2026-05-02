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
import music.notation.structure.MelodicTrack;
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
        long pickupOffsetTicks,
        List<TempoSegment> tempoSegments
) {

    /** Backwards-compat constructor: defaults tempo segments to empty. */
    public PitchScrollData(List<NoteRect> noteRects, List<LyricRect> lyricRects,
                           List<String> trackNames, int trackCount, int minNote, int maxNote,
                           long totalTicks, long barTickWidth, int ticksPerQuarter,
                           long pickupOffsetTicks) {
        this(noteRects, lyricRects, trackNames, trackCount, minNote, maxNote,
                totalTicks, barTickWidth, ticksPerQuarter, pickupOffsetTicks, List.of());
    }

    /** Build visualization data from a {@link Piece}. */
    static PitchScrollData fromPiece(Piece piece) {
        var rects = new ArrayList<NoteRect>();
        var names = new ArrayList<String>();

        for (Track track : piece.tracks()) {
            names.add(track.name());
            extractNoteRects(track.bars(), track.name(), rects);
            // Aux voices share the parent's lane.
            if (track instanceof MelodicTrack mt) {
                for (var auxBars : mt.auxBars().values()) {
                    extractNoteRects(auxBars, track.name(), rects);
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

        long pickupOffset = MidiPlayer.computeLeadingPaddingTicks(piece);

        // Authored Pieces carry a single piece-wide bpm today; produce one
        // segment spanning the whole timeline so the legend has a base entry.
        long endTick = Math.max(totalTicks, barTickWidth);
        List<TempoSegment> segments = List.of(new TempoSegment(0, endTick, piece.tempo().bpm()));

        return new PitchScrollData(
                List.copyOf(rects),
                List.of(),  // lyrics dropped with legacy phrase family
                List.copyOf(names),
                piece.tracks().size(),
                minNote, maxNote,
                totalTicks, barTickWidth,
                MidiMapper.TICKS_PER_QUARTER,
                pickupOffset,
                segments
        );
    }

    /**
     * Build visualisation data from an imported MIDI {@link music.notation.performance.MidiImport}.
     * Notes are projected ms → musical-quarters → display-ticks via the
     * imported {@link music.notation.performance.TempoTrack}, so tempo
     * changes are honoured: bars line up with the actual downbeats no
     * matter how many tempo events the source MIDI carries.
     */
    static PitchScrollData fromImport(music.notation.performance.MidiImport imp) {
        var rects = new ArrayList<NoteRect>();
        var names = new ArrayList<String>();

        // Build cumulative-quarters segments from the imported tempo timeline.
        // Each segment records (msAtStart, quartersAtStart, bpm); converting a
        // ms reading walks linearly inside its segment.
        var tempoChanges = imp.performance().tempo().changes();
        var segments = new ArrayList<double[]>();
        if (tempoChanges.isEmpty()) {
            segments.add(new double[] { 0.0, 0.0, 120.0 });
        } else {
            double cumQuarters = 0;
            double prevMs = 0;
            int prevBpm = tempoChanges.get(0).bpm();
            for (int i = 0; i < tempoChanges.size(); i++) {
                var tc = tempoChanges.get(i);
                if (i > 0) {
                    double segMs = tc.tickMs() - prevMs;
                    cumQuarters += segMs * prevBpm / 60_000.0;
                }
                segments.add(new double[] { tc.tickMs(), cumQuarters, tc.bpm() });
                prevMs = tc.tickMs();
                prevBpm = tc.bpm();
            }
            // If the timeline doesn't start at 0, prepend a 120 bpm segment so
            // pre-first-event content still projects sensibly.
            if (segments.get(0)[0] > 0) {
                segments.add(0, new double[] { 0.0, 0.0, 120.0 });
                // Re-stitch quarters offsets after the prepend.
                double cum = 0;
                double prev = 0;
                int pBpm = 120;
                for (int i = 0; i < segments.size(); i++) {
                    var s = segments.get(i);
                    if (i > 0) cum += (s[0] - prev) * pBpm / 60_000.0;
                    segments.set(i, new double[] { s[0], cum, s[2] });
                    prev = s[0];
                    pBpm = (int) s[2];
                }
            }
        }
        var segs = segments;  // effectively-final reference for the lambda

        java.util.function.DoubleUnaryOperator msToQuarters = ms -> {
            double[] seg = segs.get(0);
            for (var s : segs) {
                if (s[0] <= ms) seg = s;
                else break;
            }
            double extra = (ms - seg[0]) * seg[2] / 60_000.0;
            return seg[1] + extra;
        };

        for (var track : imp.performance().score().tracks()) {
            String name = track.id().name();
            names.add(name);
            for (var note : track.notes()) {
                double qStart = msToQuarters.applyAsDouble(note.tickMs());
                double qEnd = msToQuarters.applyAsDouble(note.tickMs() + note.durationMs());
                long startTick = Math.round(qStart * MidiMapper.TICKS_PER_QUARTER);
                long endTick = Math.round(qEnd * MidiMapper.TICKS_PER_QUARTER);
                int midi = (note instanceof music.notation.performance.PitchedNote pn) ? pn.midi()
                        : (note instanceof music.notation.performance.DrumNote dn) ? dn.piece()
                        : 60;
                rects.add(new NoteRect(startTick, endTick, midi, name, 0));
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
        long barTickWidth = (long) imp.timeSig().barSixtyFourths() * MidiMapper.TICKS_PER_QUARTER / 16;

        // Build tempo segments in display-tick space using the same projector.
        var tempoSegments = new ArrayList<TempoSegment>();
        if (!tempoChanges.isEmpty()) {
            double finalMs = imp.totalMs() > 0 ? imp.totalMs() : tempoChanges.get(tempoChanges.size() - 1).tickMs() + 1;
            long endOfPiece = Math.round(msToQuarters.applyAsDouble(finalMs) * MidiMapper.TICKS_PER_QUARTER);
            for (int i = 0; i < tempoChanges.size(); i++) {
                var tc = tempoChanges.get(i);
                long segStart = Math.round(msToQuarters.applyAsDouble(tc.tickMs()) * MidiMapper.TICKS_PER_QUARTER);
                long segEnd = (i + 1 < tempoChanges.size())
                        ? Math.round(msToQuarters.applyAsDouble(tempoChanges.get(i + 1).tickMs()) * MidiMapper.TICKS_PER_QUARTER)
                        : Math.max(endOfPiece, segStart + 1);
                tempoSegments.add(new TempoSegment(segStart, segEnd, tc.bpm()));
            }
        } else {
            // No tempo events — single 120 bpm segment spanning the piece.
            tempoSegments.add(new TempoSegment(0, Math.max(totalTicks, barTickWidth), 120));
        }

        return new PitchScrollData(
                List.copyOf(rects),
                List.of(),
                List.copyOf(names),
                imp.performance().score().tracks().size(),
                minNote, maxNote,
                totalTicks, barTickWidth,
                MidiMapper.TICKS_PER_QUARTER,
                0L,
                List.copyOf(tempoSegments)
        );
    }

    // ── Tick-space note-rect walker ────────────────────────────────────

    private static void extractNoteRects(List<Bar> bars, String trackKey, List<NoteRect> out) {
        long tick = 0;
        for (Bar bar : bars) {
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
