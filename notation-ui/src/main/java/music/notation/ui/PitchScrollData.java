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

    /**
     * Functional transform: produce a copy of this data with every pitched
     * {@link NoteRect}'s {@code midiNote} shifted by {@code semitoneShift}.
     * The {@code originalMidi} field is preserved (or set from the current
     * unshifted {@code midiNote} when no prior shift existed) so the UI's
     * ghost-lane rendering shows both pitches.
     *
     * <p>Out-of-range notes ({@code newMidi < 0 || newMidi > 127}) are
     * dropped — same policy as {@code TransposeTransform.apply} at the
     * Performance layer. The vertical range ({@code minNote}/{@code maxNote})
     * is recomputed to accommodate both the shifted and original positions.</p>
     *
     * <p>Shift of 0 returns the same instance — caller can use it freely
     * without worrying about identity churn.</p>
     */
    PitchScrollData withTransposition(int semitoneShift) {
        if (semitoneShift == 0) return this;
        var shifted = new ArrayList<NoteRect>(noteRects.size());
        for (NoteRect r : noteRects) {
            // Drum NoteRects pass through unchanged — drum "pitch" is a
            // GM kit selector, not a pitch. Shifting a kick (MIDI 36) up
            // 5 would silently re-route to a high-tom slot. Same exemption
            // as TransposeTransform.apply at the Performance layer.
            if (r.isDrum()) {
                shifted.add(r);
                continue;
            }
            int newMidi = r.midiNote() + semitoneShift;
            if (newMidi < 0 || newMidi > 127) continue;     // out of range, drop
            int origMidi = r.originalMidi();   // preserve prior original if already shifted
            shifted.add(new NoteRect(r.startTick(), r.endTick(), newMidi,
                                       origMidi, r.trackKey(), r.voice(),
                                       /*isDrum=*/ false));
        }
        int min = 127, max = 0;
        for (NoteRect r : shifted) {
            int lo = Math.min(r.midiNote(), r.originalMidi());
            int hi = Math.max(r.midiNote(), r.originalMidi());
            if (lo < min) min = lo;
            if (hi > max) max = hi;
        }
        int newMin = shifted.isEmpty() ? minNote : Math.max(0, min - 2);
        int newMax = shifted.isEmpty() ? maxNote : Math.min(127, max + 2);
        return new PitchScrollData(
                List.copyOf(shifted),
                lyricRects, trackNames, trackCount,
                newMin, newMax,
                totalTicks, barTickWidth, ticksPerQuarter, pickupOffsetTicks,
                tempoSegments);
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
     * Notes are projected directly from their musical
     * {@link music.notation.duration.Duration} position to display
     * ticks (PPQ × 4 = ticks per whole note). Tempo plays no role in
     * the layout — bars always line up because positions are
     * intrinsically musical, not wall-clock.
     */
    static PitchScrollData fromImport(music.notation.performance.MidiImport imp) {
        var rects = new ArrayList<NoteRect>();
        var names = new ArrayList<String>();

        final long TICKS_PER_WHOLE = (long) MidiMapper.TICKS_PER_QUARTER * 4L;
        java.util.function.ToLongFunction<music.notation.duration.Duration> durationToTicks =
                d -> Math.multiplyExact(d.numerator(), TICKS_PER_WHOLE) / d.denominator();

        for (var track : imp.performance().score().tracks()) {
            String name = track.id().name();
            names.add(name);
            for (var note : track.notes()) {
                long startTick = durationToTicks.applyAsLong(note.at());
                long endTick = durationToTicks.applyAsLong(note.endAt());
                // ShiftedNote first — it's a PitchedLike, so the broader
                // check below would also match, but we want its originalMidi
                // for ghost rendering.
                int midi;
                int originalMidi;
                boolean isDrum = false;
                if (note instanceof music.notation.performance.ShiftedNote sn) {
                    midi = sn.midi();
                    originalMidi = sn.originalMidi();
                } else if (note instanceof music.notation.performance.PitchedLike pl) {
                    midi = pl.midi();
                    originalMidi = midi;
                } else if (note instanceof music.notation.performance.DrumNote dn) {
                    midi = dn.piece();
                    originalMidi = midi;
                    isDrum = true;
                } else {
                    midi = 60;
                    originalMidi = 60;
                }
                rects.add(new NoteRect(startTick, endTick, midi, originalMidi, name, 0, isDrum));
            }
        }
        rects.sort(Comparator.comparingLong(NoteRect::startTick));

        int min = 127, max = 0;
        for (NoteRect r : rects) {
            // Include both displayed and original midi so the y-axis
            // accommodates the ghost lane.
            int lo = Math.min(r.midiNote(), r.originalMidi());
            int hi = Math.max(r.midiNote(), r.originalMidi());
            if (lo < min) min = lo;
            if (hi > max) max = hi;
        }
        int minNote = Math.max(0, min - 2);
        int maxNote = Math.min(127, max + 2);
        long totalTicks = rects.isEmpty() ? 0 : rects.getLast().endTick();
        long barTickWidth = (long) imp.timeSig().barSixtyFourths() * MidiMapper.TICKS_PER_QUARTER / 16;

        // Build tempo segments in display-tick space using the same projector.
        var tempoChanges = imp.performance().tempo().changes();
        var tempoSegments = new ArrayList<TempoSegment>();
        if (!tempoChanges.isEmpty()) {
            for (int i = 0; i < tempoChanges.size(); i++) {
                var tc = tempoChanges.get(i);
                long segStart = durationToTicks.applyAsLong(tc.at());
                long segEnd = (i + 1 < tempoChanges.size())
                        ? durationToTicks.applyAsLong(tempoChanges.get(i + 1).at())
                        : Math.max(totalTicks, segStart + 1);
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
                // Mark as drum so withTransposition skips it — drum "pitch"
                // is a GM kit selector, not a pitch.
                out.add(new NoteRect(tick, tick + dur, midi, midi, trackKey, 0, true));
                tick += dur;
            }
            // Zero-duration markers — no advance, no rect.
            case music.notation.phrase.DynamicNode d -> {}
            case music.notation.phrase.TempoChangeNode t -> {}
            case music.notation.phrase.TempoTransitionStartNode t -> {}
            case music.notation.phrase.TempoTransitionEndNode t -> {}
            case music.notation.phrase.SubPhrase sp -> { /* legacy nesting dropped */ }
            // Lyrics advance the cursor (so subsequent notes line up) but
            // produce no rect — the piano roll shows pitched / drum notes
            // only. A dedicated lyric strip can render them later.
            case music.notation.phrase.LyricNode l ->
                    tick += MidiMapper.toTicks(l.duration());
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
