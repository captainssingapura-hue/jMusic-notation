package music.notation.play;

import music.notation.event.Instrument;
import music.notation.structure.DrumTrack;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Piece;
import music.notation.structure.Track;

import javax.sound.midi.Synthesizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-channel program + volume + pan snapshot — the "what to play with"
 * value object, separate from the "what to play" sequence.
 *
 * <p>Phase 5.1: replaces the imperative {@code setProgram} /
 * {@code setVolume} surface on {@link MidiPlayer} with a pure
 * value object that can be applied to a {@link Synthesizer}
 * idempotently. Live changes = build a new {@code ChannelSetup}
 * via {@link #from(Piece, List, List, List)} and call
 * {@link MidiPlayer#applySetup(ChannelSetup)}.</p>
 *
 * <p>Channel allocation mirrors {@code MidiCodec.assignChannels}:
 * drum tracks → channel 9; pitched tracks fill 0–8 then 10–15
 * (skipping 9). Throws {@link IllegalStateException} for &gt;15 pitched
 * tracks.</p>
 *
 * @param programs per-channel program number (0–127). Drum channel
 *                 entries (channel 9) are kept for completeness but
 *                 most synths ignore the program on channel 9
 *                 (kit selection is bank-based, not program-based).
 * @param volumes  per-channel CC #7 level (0–127).
 * @param pans     per-channel CC #10 pan (0=hard-left, 64=center, 127=hard-right).
 */
public record ChannelSetup(
        Map<Integer, Integer> programs,
        Map<Integer, Integer> volumes,
        Map<Integer, Integer> pans
) {

    /** Default pan: dead-centre. Use this when the caller has no opinion. */
    public static final int PAN_CENTER = 64;

    private static final int DRUM_CHANNEL = 9;
    private static final int MAX_CHANNEL = 15;

    public ChannelSetup {
        programs = Map.copyOf(programs);
        volumes = Map.copyOf(volumes);
        pans = Map.copyOf(pans);
    }

    /** Backwards-compat constructor: defaults pan to centre on every channel. */
    public ChannelSetup(Map<Integer, Integer> programs, Map<Integer, Integer> volumes) {
        this(programs, volumes, defaultPansFor(programs.keySet()));
    }

    /**
     * Build a {@code ChannelSetup} from a piece and per-track instrument
     * + volume + pan selections. Pure: no I/O, no synth dependency.
     *
     * <p>Per-track lookups: if {@code instruments[i]} is null or absent,
     * falls back to the track's {@code defaultInstrument()} for melodic
     * tracks (or {@link Instrument#DRUM_KIT} for drum tracks). If
     * {@code volumes[i]} is null or absent, falls back to 100. If
     * {@code pans[i]} is null or absent, falls back to {@link #PAN_CENTER}.</p>
     */
    public static ChannelSetup from(Piece piece,
                                    List<Instrument> instruments,
                                    List<Integer> volumes,
                                    List<Integer> pans) {
        Map<Integer, Integer> programs = new LinkedHashMap<>();
        Map<Integer, Integer> volMap = new LinkedHashMap<>();
        Map<Integer, Integer> panMap = new LinkedHashMap<>();
        int next = 0;
        int pitchedCount = 0;
        List<Track> tracks = piece.tracks();
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            int channel;
            if (track instanceof DrumTrack) {
                channel = DRUM_CHANNEL;
            } else {
                if (next == DRUM_CHANNEL) next++;
                if (next > MAX_CHANNEL) {
                    throw new IllegalStateException(
                            "Too many pitched tracks: max 15, got " + (pitchedCount + 1));
                }
                channel = next++;
                pitchedCount++;
            }
            Instrument ins = (instruments != null && i < instruments.size() && instruments.get(i) != null)
                    ? instruments.get(i)
                    : defaultInstrumentOf(track);
            int vol = (volumes != null && i < volumes.size() && volumes.get(i) != null)
                    ? volumes.get(i)
                    : 100;
            int pan = (pans != null && i < pans.size() && pans.get(i) != null)
                    ? pans.get(i)
                    : PAN_CENTER;
            programs.put(channel, ins.program());
            volMap.put(channel, clamp(vol));
            panMap.put(channel, clamp(pan));
        }
        return new ChannelSetup(programs, volMap, panMap);
    }

    /** Backwards-compat overload: no pan list ⇒ all channels centred. */
    public static ChannelSetup from(Piece piece,
                                    List<Instrument> instruments,
                                    List<Integer> volumes) {
        return from(piece, instruments, volumes, null);
    }

    /**
     * Performance-track overload: build a setup directly from the
     * concrete {@link music.notation.performance.Track}s of an imported
     * {@link music.notation.performance.Performance}. Channel allocation
     * mirrors {@link #from(Piece, List, List, List)} — drum tracks → 9,
     * pitched fill 0–8 then 10–15.
     */
    public static ChannelSetup fromPerformanceTracks(
            List<music.notation.performance.Track> tracks,
            List<Instrument> instruments,
            List<Integer> volumes,
            List<Integer> pans) {
        Map<Integer, Integer> programs = new LinkedHashMap<>();
        Map<Integer, Integer> volMap = new LinkedHashMap<>();
        Map<Integer, Integer> panMap = new LinkedHashMap<>();
        int next = 0;
        int pitchedCount = 0;
        for (int i = 0; i < tracks.size(); i++) {
            var track = tracks.get(i);
            int channel;
            if (track.kind() == music.notation.performance.TrackKind.DRUM) {
                channel = DRUM_CHANNEL;
            } else {
                if (next == DRUM_CHANNEL) next++;
                if (next > MAX_CHANNEL) {
                    throw new IllegalStateException(
                            "Too many pitched tracks: max 15, got " + (pitchedCount + 1));
                }
                channel = next++;
                pitchedCount++;
            }
            Instrument ins = (instruments != null && i < instruments.size() && instruments.get(i) != null)
                    ? instruments.get(i)
                    : (track.kind() == music.notation.performance.TrackKind.DRUM
                            ? Instrument.DRUM_KIT
                            : Instrument.ACOUSTIC_GRAND_PIANO);
            int vol = (volumes != null && i < volumes.size() && volumes.get(i) != null)
                    ? volumes.get(i) : 100;
            int pan = (pans != null && i < pans.size() && pans.get(i) != null)
                    ? pans.get(i) : PAN_CENTER;
            programs.put(channel, ins.program());
            volMap.put(channel, clamp(vol));
            panMap.put(channel, clamp(pan));
        }
        return new ChannelSetup(programs, volMap, panMap);
    }

    /**
     * Apply this setup to a synthesizer's channels. Idempotent —
     * call any time. No-op when {@code synth} is null.
     */
    public void apply(Synthesizer synth) {
        if (synth == null) return;
        var channels = synth.getChannels();
        if (channels == null) return;
        for (var e : programs.entrySet()) {
            int ch = e.getKey();
            if (ch < 0 || ch >= channels.length || channels[ch] == null) continue;
            channels[ch].programChange(e.getValue());
        }
        for (var e : volumes.entrySet()) {
            int ch = e.getKey();
            if (ch < 0 || ch >= channels.length || channels[ch] == null) continue;
            channels[ch].controlChange(/*CC #7=*/ 7, e.getValue());
        }
        for (var e : pans.entrySet()) {
            int ch = e.getKey();
            if (ch < 0 || ch >= channels.length || channels[ch] == null) continue;
            channels[ch].controlChange(/*CC #10=*/ 10, e.getValue());
        }
    }

    private static Map<Integer, Integer> defaultPansFor(Iterable<Integer> channels) {
        var out = new LinkedHashMap<Integer, Integer>();
        for (Integer ch : channels) out.put(ch, PAN_CENTER);
        return out;
    }

    private static Instrument defaultInstrumentOf(Track track) {
        return switch (track) {
            case MelodicTrack mt -> mt.defaultInstrument();
            case DrumTrack dt -> Instrument.DRUM_KIT;
        };
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(127, v));
    }
}
