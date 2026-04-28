package music.notation.play;

import music.notation.phrase.Bar;
import music.notation.phrase.GraceNote;
import music.notation.phrase.PaddingNode;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.PolyPitchNode;
import music.notation.phrase.RestNode;
import music.notation.phrase.SimplePitchNode;
import music.notation.pitch.Pitch;
import music.notation.structure.DrumTrack;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Headless text-grid renderer over a {@link Piece}'s
 * {@link Track#bars()} value object. Mirrors what
 * {@link music.notation.play.PieceConcretizer} consumes for audio
 * and {@code PitchScrollData} consumes for the JavaFX piano roll —
 * by construction the three views agree on note positions because
 * they read identical bar lists.
 *
 * <p>Testable without UI. Each {@link Hit} carries
 * {@code (track, sf, durSf, midi)} where {@code sf} is sixty-fourths
 * from the start of the piece. The formatted string places one
 * character per sixty-fourth: {@code '.'} silence, {@code 'o'} onset,
 * {@code '#'} sustain, {@code '|'} bar boundary.</p>
 */
public final class TUIPianoRoll {

    private TUIPianoRoll() {}

    /** A single emitted note in 64th-units. */
    public record Hit(String trackName, int sf, int durSf, int midi) {}

    /** Rendering result over a whole piece. */
    public record Roll(List<Hit> hits, int totalSixtyFourths,
                       Map<String, Integer> barSizesByTrack) {
        /** Hits filtered to a single track, sorted by (sf, midi). */
        public List<Hit> hitsForTrack(String trackName) {
            return hits.stream()
                    .filter(h -> h.trackName().equals(trackName))
                    .sorted((a, b) -> {
                        if (a.sf() != b.sf()) return Integer.compare(a.sf(), b.sf());
                        return Integer.compare(a.midi(), b.midi());
                    })
                    .toList();
        }

        /**
         * Format one track as a text grid. Rows are MIDI notes
         * (descending), columns are sixty-fourths from 0 to
         * {@link #totalSixtyFourths}. Bar boundaries inserted as
         * {@code '|'} based on {@link #barSizesByTrack}.
         */
        public String formatTrack(String trackName) {
            List<Hit> trackHits = hitsForTrack(trackName);
            if (trackHits.isEmpty()) return trackName + ": (empty)\n";

            int minMidi = trackHits.stream().mapToInt(Hit::midi).min().getAsInt();
            int maxMidi = trackHits.stream().mapToInt(Hit::midi).max().getAsInt();
            int barSize = barSizesByTrack.getOrDefault(trackName, 64);

            // Build per-pitch row of length totalSixtyFourths.
            var rows = new TreeMap<Integer, char[]>();
            for (int m = minMidi; m <= maxMidi; m++) {
                char[] row = new char[totalSixtyFourths];
                java.util.Arrays.fill(row, '.');
                rows.put(m, row);
            }
            for (Hit h : trackHits) {
                char[] row = rows.get(h.midi());
                if (row == null) continue;
                int start = h.sf();
                int end = Math.min(start + h.durSf(), row.length);
                if (start < row.length) row[start] = 'o';
                for (int i = start + 1; i < end; i++) row[i] = '#';
            }

            var sb = new StringBuilder();
            sb.append(trackName).append('\n');
            // Print rows from highest pitch to lowest (piano-roll convention).
            for (int m = maxMidi; m >= minMidi; m--) {
                sb.append(String.format("%3d ", m));
                char[] row = rows.get(m);
                for (int i = 0; i < row.length; i++) {
                    if (i > 0 && i % barSize == 0) sb.append('|');
                    sb.append(row[i]);
                }
                sb.append('\n');
            }
            return sb.toString();
        }

        /** Format every track. */
        public String format() {
            var sb = new StringBuilder();
            for (String name : barSizesByTrack.keySet()) {
                sb.append(formatTrack(name)).append('\n');
            }
            return sb.toString();
        }
    }

    // ── Renderer ────────────────────────────────────────────────────

    public static Roll render(Piece piece) {
        var hits = new ArrayList<Hit>();
        var barSizes = new LinkedHashMap<String, Integer>();
        int total = 0;

        for (Track track : piece.tracks()) {
            int trackTotal = walkTrack(track, track.name(), hits);
            int barSize = track.bars().isEmpty() ? piece.timeSig().barSixtyFourths()
                    : track.bars().get(0).expectedSixtyFourths();
            barSizes.put(track.name(), barSize);
            if (trackTotal > total) total = trackTotal;
            for (Track aux : track.auxTracks()) {
                walkTrack(aux, track.name() + " Aux", hits);
            }
        }
        return new Roll(List.copyOf(hits), total, barSizes);
    }

    private static int walkTrack(Track track, String trackName, List<Hit> out) {
        int sf = 0;
        for (Bar bar : track.bars()) {
            for (PhraseNode node : bar.nodes()) {
                sf = walkNode(node, sf, trackName, out, /*drumKit*/ track instanceof DrumTrack);
            }
        }
        return sf;
    }

    private static int walkNode(PhraseNode node, int sf, String trackName,
                                List<Hit> out, boolean drumKit) {
        switch (node) {
            case SimplePitchNode pn -> {
                int dur = pn.duration().sixtyFourths();
                int mainDur = dur;
                if (!pn.graceNotes().isEmpty()) {
                    int slots = pn.graceNotes().size() + 1;
                    int graceDur = pn.equalDivision() ? Math.max(1, dur / slots) : 1;
                    int graceTotal = 0;
                    for (GraceNote g : pn.graceNotes()) {
                        out.add(new Hit(trackName, sf, graceDur, midiOf(g.pitch())));
                        sf += graceDur;
                        graceTotal += graceDur;
                    }
                    mainDur = Math.max(dur - graceTotal, 1);
                }
                out.add(new Hit(trackName, sf, mainDur, midiOf(pn.pitch())));
                sf += mainDur;
            }
            case PolyPitchNode pn -> {
                int dur = pn.duration().sixtyFourths();
                for (Pitch p : pn.pitches()) {
                    out.add(new Hit(trackName, sf, dur, midiOf(p)));
                }
                sf += dur;
            }
            case PercussionNote pn -> {
                int dur = pn.duration().sixtyFourths();
                out.add(new Hit(trackName, sf, dur, pn.sound().midiNote()));
                sf += dur;
            }
            case RestNode r -> sf += r.duration().sixtyFourths();
            case PaddingNode p -> sf += p.duration().sixtyFourths();
            default -> {} // dynamics, tempo markers, sub phrases — no rendering
        }
        return sf;
    }

    private static int midiOf(Pitch p) {
        return MidiMapper.toMidiNote(p);
    }
}
