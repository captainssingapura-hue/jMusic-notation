package music.notation.performance;

import music.notation.event.Instrument;
import music.notation.performance.OnsetGrouper.GroupedEvent;
import music.notation.performance.OverlapVoiceSplitter.SplitResult;
import music.notation.phrase.Phrase;
import music.notation.structure.DrumTrack;
import music.notation.structure.KeySignature;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Mode;
import music.notation.structure.Piece;
import music.notation.structure.Tempo;
import music.notation.structure.TimeSignature;
import music.notation.duration.BaseValue;
import music.notation.pitch.NoteName;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates conversion of a {@link Performance} into a {@link Piece}.
 *
 * <p><b>Pitched tracks</b> are processed in one of two
 * {@link SplitMode}s:</p>
 * <ul>
 *   <li>{@link SplitMode#PRESERVE} (default) — each input pitched track
 *       becomes one {@link MelodicTrack}, with no register or voice
 *       splitting. Right answer for properly-arranged multi-track
 *       MIDIs where each source track is already one logical voice.</li>
 *   <li>{@link SplitMode#SPLIT} — runs the full
 *       {@link PitchBandSplitter} → {@link OverlapVoiceSplitter}
 *       pipeline, producing one {@code MelodicTrack} per detected
 *       voice (named {@code "<source> · RH/LH · v<i>"}). Right answer
 *       for piano-blob MIDIs whose composer collapsed multiple voices
 *       into a single track.</li>
 * </ul>
 *
 * <p><b>Drum tracks</b>: each input drum source produces one
 * {@link DrumTrack} carrying every percussion piece that source plays.
 * Same-quantum collisions (kick + crash on beat 1) are sequenced
 * 1 sf apart by {@link DrumBarBuilder}.</p>
 *
 * <p>See {@code .docs/drum-track-model.md}.</p>
 *
 * <p>Stateless and thread-safe.</p>
 */
public final class PerformanceImporter {

    private PerformanceImporter() {}

    /** Default split cutoff = MIDI 60 (middle C). */
    public static final int DEFAULT_CUTOFF_MIDI = 60;

    /** Whether to break each pitched input track down into voices. */
    public enum SplitMode {
        /** 1 input track → 1 output {@link MelodicTrack} (no splitting). */
        PRESERVE,
        /** Run the full Tier-1 + Tier-2 voice split per input track. */
        SPLIT
    }

    /** Convert a {@link MidiImport} using the default {@link SplitMode#PRESERVE}
     *  and {@link QuantizerProfile#STANDARD} (powers-of-2 + dotted variants). */
    public static Piece toPiece(MidiImport imp) {
        return toPiece(imp, SplitMode.PRESERVE);
    }

    public static Piece toPiece(MidiImport imp, SplitMode mode) {
        return toPiece(imp, mode, QuantizerProfile.STANDARD);
    }

    /** Mode + profile overload — the most common shape from the UI. */
    public static Piece toPiece(MidiImport imp, SplitMode mode, QuantizerProfile profile) {
        return toPiece(imp.performance(), imp.timeSig(), imp.key(),
                imp.initialBpm(), imp.displayName(), DEFAULT_CUTOFF_MIDI, mode, profile);
    }

    /** Backwards-compat 6-arg overload — defaults to {@link SplitMode#SPLIT}, STANDARD profile. */
    public static Piece toPiece(Performance perf, TimeSignature ts, KeySignature key,
                                int bpm, String displayName, int cutoffMidi) {
        return toPiece(perf, ts, key, bpm, displayName, cutoffMidi,
                SplitMode.SPLIT, QuantizerProfile.STANDARD);
    }

    /** 7-arg overload — defaults to STANDARD profile. */
    public static Piece toPiece(Performance perf, TimeSignature ts, KeySignature key,
                                int bpm, String displayName, int cutoffMidi, SplitMode mode) {
        return toPiece(perf, ts, key, bpm, displayName, cutoffMidi, mode,
                QuantizerProfile.STANDARD);
    }

    /** Full-control overload. */
    public static Piece toPiece(Performance perf, TimeSignature ts, KeySignature key,
                                int bpm, String displayName, int cutoffMidi,
                                SplitMode mode, QuantizerProfile profile) {
        int barSf = ts.beats() * (64 / ts.beatValue());
        var cfg   = new BarBuilder.Config(barSf, bpm, profile);

        var melodic = new ArrayList<MelodicTrack>();
        var drums   = new ArrayList<DrumTrack>();
        var srcTracks = perf.score().tracks();
        for (int idx = 0; idx < srcTracks.size(); idx++) {
            Track src = srcTracks.get(idx);
            if (src.kind() == TrackKind.DRUM) {
                drums.addAll(buildDrumTracks(src, idx, cfg));
            } else {
                melodic.addAll(buildMelodicTracks(src, perf, idx, cfg, cutoffMidi, mode));
            }
        }

        return Piece.ofTrackKinds(displayName, "Imported MIDI",
                key != null ? key : new KeySignature(NoteName.C, Mode.MAJOR),
                ts != null ? ts : new TimeSignature(4, 4),
                new Tempo(bpm, BaseValue.QUARTER),
                melodic, drums);
    }

    // ── Pitched-track pipeline ──────────────────────────────────────

    private static List<MelodicTrack> buildMelodicTracks(
            Track src, Performance perf, int srcIdx,
            BarBuilder.Config cfg, int cutoffMidi, SplitMode mode) {
        var pitched = new ArrayList<PitchedNote>();
        for (var n : src.notes()) {
            if (n instanceof PitchedNote pn) pitched.add(pn);
        }
        if (pitched.isEmpty()) return List.of();

        var events = OnsetGrouper.group(pitched);
        Instrument inst = defaultInstrumentFor(perf, src);
        String baseName = friendlyName(src, srcIdx);

        if (mode == SplitMode.PRESERVE) {
            // 1:1 — let BarBuilder handle each chord/event sequentially.
            var bars = BarBuilder.build(events, cfg);
            if (bars.isEmpty()) return List.of();
            return List.of(new MelodicTrack(baseName, inst, Phrase.of(bars)));
        }

        // SPLIT: full pipeline.
        var bands = PitchBandSplitter.split(events, cutoffMidi);
        var out = new ArrayList<MelodicTrack>();
        boolean usingBoth = bands.hasHigh() && bands.hasLow();
        if (bands.hasHigh()) appendVoices(out, bands.high(), cfg, inst,
                usingBoth ? baseName + " · RH" : baseName);
        if (bands.hasLow())  appendVoices(out, bands.low(),  cfg, inst,
                usingBoth ? baseName + " · LH" : baseName);
        return out;
    }

    private static void appendVoices(List<MelodicTrack> out,
                                     List<GroupedEvent> bandEvents,
                                     BarBuilder.Config cfg, Instrument inst,
                                     String prefix) {
        SplitResult voices = OverlapVoiceSplitter.split(bandEvents);
        boolean multi = voices.size() > 1;
        for (int v = 0; v < voices.size(); v++) {
            var bars = BarBuilder.build(voices.voices().get(v), cfg);
            if (bars.isEmpty()) continue;
            String name = multi ? prefix + " · v" + v : prefix;
            out.add(new MelodicTrack(name, inst, Phrase.of(bars)));
        }
    }

    // ── Drum-track pipeline ─────────────────────────────────────────

    /**
     * Convert a drum source track into a single {@link DrumTrack}.
     * All percussion pieces stay on one lane; same-quantum collisions
     * are micro-staggered 1 sf apart by {@link DrumBarBuilder}.
     */
    private static List<DrumTrack> buildDrumTracks(Track src, int srcIdx,
                                                   BarBuilder.Config cfg) {
        var hits = new ArrayList<DrumBarBuilder.Hit>();
        for (var n : src.notes()) {
            if (!(n instanceof DrumNote dn)) continue;
            // Unmapped MIDI percussion notes are silently dropped.
            PercussionMap.forNote(dn.piece()).ifPresent(sound ->
                hits.add(new DrumBarBuilder.Hit(dn.tickMs(), dn.durationMs(), sound)));
        }
        if (hits.isEmpty()) return List.of();
        var bars = DrumBarBuilder.build(hits, cfg);
        if (bars.isEmpty()) return List.of();
        return List.of(new DrumTrack(friendlyName(src, srcIdx), Phrase.of(bars)));
    }

    // ── helpers ──────────────────────────────────────────────────────

    private static Instrument defaultInstrumentFor(Performance perf, Track src) {
        var control = perf.instruments().byTrack().get(src.id());
        if (control == null || control.changes().isEmpty()) {
            return Instrument.ACOUSTIC_GRAND_PIANO;
        }
        int program = control.changes().get(0).program();
        return InstrumentMap.forProgramOrDefault(program);
    }

    private static String friendlyName(Track src, int idx) {
        String n = src.id().name();
        if (n == null || n.isBlank()) return "Track " + idx;
        return n;
    }
}
