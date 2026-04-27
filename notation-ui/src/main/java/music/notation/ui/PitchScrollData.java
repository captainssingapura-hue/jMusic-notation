package music.notation.ui;

import music.notation.duration.Duration;
import music.notation.event.ChordEvent;
import music.notation.phrase.*;
import music.notation.pitch.Pitch;
import music.notation.play.MidiMapper;
import music.notation.play.MidiPlayer;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import java.util.*;

/**
 * Pre-computed visualization data for a {@link PitchScroll}.
 * Decouples the scroll renderer from the music domain model.
 *
 * <p>Walks the abstract phrase tree directly in MIDI-tick space,
 * emitting {@link NoteRect} entries for the piano-roll renderer. The
 * walker mirrors the previous {@code PhraseInterpreter}-driven extraction
 * (voice overlays preserved, grace notes emitted, ornaments dropped, slur
 * legato extension already gone). It does not produce playable MIDI —
 * that's the {@link MidiPlayer} pipeline's job.</p>
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

        // Partition tracks: control tracks are hidden entirely (they carry
        // tempo/articulation markers, no pitches); lyrics tracks feed the
        // lyric timeline; everything else is an audio track on the piano roll.
        var audioTracks = new ArrayList<Track>();
        for (Track track : piece.tracks()) {
            if (piece.isControlTrack(track.name())) {
                continue;
            }
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
            // Voice overlays are walked inside extractNoteRects with their
            // own voice index; one pass over the track is enough.
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

    // ── Tick-space note-rect walker ────────────────────────────────────

    /**
     * Walk a track's phrases and emit {@link NoteRect}s for visualization.
     * Voice 0 = main line; 1..N = voice overlay slots. Drum / chord /
     * percussion phrases are all handled. Tempo, dynamic, and other
     * zero-duration markers are ignored (visualization doesn't need
     * them).
     */
    private static void extractNoteRects(Track track, String trackKey, List<NoteRect> out) {
        long tick = 0;
        for (Phrase phrase : track.phrases()) {
            tick = walkPhrase(phrase, tick, trackKey, /*voice=*/ 0, out);
        }
    }

    /** Walk a phrase starting at {@code startTick}; return the tick after the phrase. */
    private static long walkPhrase(Phrase phrase, long startTick, String trackKey,
                                    int voice, List<NoteRect> out) {
        long tick = startTick;
        switch (phrase) {
            case MelodicPhrase mp -> {
                for (PhraseNode node : mp.nodes()) {
                    tick = walkNode(node, tick, trackKey, voice, out);
                }
                long endTick = tick;

                // Voice overlays: rewind to startTick, walk each overlay
                // with currentVoice = vi+1, then restore to endTick.
                for (int vi = 0; vi < mp.voices().size(); vi++) {
                    VoiceOverlay overlay = mp.voices().get(vi);
                    long subTick = startTick;
                    for (int i = 0; i < overlay.size(); i++) {
                        Optional<Bar> slot = overlay.at(i);
                        if (slot.isEmpty()) {
                            int barSize = mp.bars().get(i).expectedSixtyFourths();
                            subTick += MidiMapper.toTicks(Duration.ofSixtyFourths(barSize));
                        } else {
                            for (PhraseNode node : slot.get().nodes()) {
                                subTick = walkNode(node, subTick, trackKey, vi + 1, out);
                            }
                        }
                    }
                }

                tick = endTick + boundaryGapTicks(mp.marking());
            }
            case RestPhrase rp -> tick += MidiMapper.toTicks(rp.duration())
                    + boundaryGapTicks(rp.marking());
            case VoidPhrase vp -> tick += MidiMapper.toTicks(vp.duration())
                    + boundaryGapTicks(vp.marking());
            case ChordPhrase cp -> {
                for (ChordEvent chord : cp.chords()) {
                    long dur = MidiMapper.toTicks(chord.duration());
                    for (Pitch p : chord.pitches()) {
                        int midi = MidiMapper.toMidiNote(p);
                        out.add(new NoteRect(tick, tick + dur, midi, trackKey, voice));
                    }
                    tick += dur;
                }
                tick += boundaryGapTicks(cp.marking());
            }
            case DrumPhrase dp -> {
                for (PhraseNode node : dp.nodes()) {
                    tick = walkNode(node, tick, trackKey, voice, out);
                }
                tick += boundaryGapTicks(dp.marking());
            }
            case LyricPhrase lp -> {
                // Lyrics produce no note rects; just advance the tick cursor.
                for (LyricEvent e : lp.syllables()) {
                    tick += MidiMapper.toTicks(e.duration());
                }
                tick += boundaryGapTicks(lp.marking());
            }
            case LayeredPhrase lp -> tick = walkPhrase(lp.resolve(), startTick, trackKey, voice, out);
            case ShiftedPhrase sp -> {
                // Walk the source then post-shift the emitted rects.
                int before = out.size();
                tick = walkPhrase(sp.source(), startTick, trackKey, voice, out);
                for (int i = before; i < out.size(); i++) {
                    NoteRect r = out.get(i);
                    out.set(i, new NoteRect(r.startTick(), r.endTick(),
                            sp.shiftMidiNote(r.midiNote()), r.trackKey(), r.voice()));
                }
            }
        }
        return tick;
    }

    /** Walk a single phrase node; return the tick after it. */
    private static long walkNode(PhraseNode node, long tick, String trackKey,
                                  int voice, List<NoteRect> out) {
        switch (node) {
            case PitchNode pn -> {
                long dur = MidiMapper.toTicks(pn.duration());
                long mainDur = dur;

                // Grace notes precede the main note. In equal-division
                // (tuplet) mode each grace and the main each take
                // dur / (graceCount + 1); otherwise each grace plays briefly
                // and the main keeps the remainder.
                if (!pn.graceNotes().isEmpty()) {
                    int slots = pn.graceNotes().size() + 1;
                    long graceDur = pn.equalDivision() ? dur / slots : MidiMapper.GRACE_NOTE_TICK;
                    long graceTotal = 0;
                    for (GraceNote g : pn.graceNotes()) {
                        int gMidi = MidiMapper.toMidiNote(g.pitch());
                        out.add(new NoteRect(tick, tick + graceDur, gMidi, trackKey, voice));
                        tick += graceDur;
                        graceTotal += graceDur;
                    }
                    mainDur = Math.max(dur - graceTotal, MidiMapper.GRACE_NOTE_TICK);
                }
                for (Pitch p : pn.pitches()) {
                    int midi = MidiMapper.toMidiNote(p);
                    out.add(new NoteRect(tick, tick + mainDur, midi, trackKey, voice));
                }
                tick += mainDur;
            }
            case RestNode r -> tick += MidiMapper.toTicks(r.duration());
            case PaddingNode p -> tick += MidiMapper.toTicks(p.duration());
            case PercussionNote pn -> {
                int midi = pn.sound().midiNote();
                long dur = MidiMapper.toTicks(pn.duration());
                out.add(new NoteRect(tick, tick + dur, midi, trackKey, voice));
                tick += dur;
            }
            case DynamicNode d -> {} // visualization ignores dynamics
            case TempoChangeNode t -> {} // visualization ignores tempo
            case TempoTransitionStartNode t -> {}
            case TempoTransitionEndNode t -> {}
            case SubPhrase sp -> tick = walkPhrase(sp.phrase(), tick, trackKey, voice, out);
        }
        return tick;
    }

    // ── Lyric extractor (unchanged from earlier; PhraseInterpreter never used here) ──

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
            // Visualization simplification: treat ELISION as ATTACCA. The
            // playback engine handles elision overlap precisely; the
            // piano-roll rendering is approximate at elision boundaries.
            case ELISION -> 0;
        };
    }
}
