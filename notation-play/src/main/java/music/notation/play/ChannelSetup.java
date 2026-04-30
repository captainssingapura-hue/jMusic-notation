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
 * Per-channel program + volume snapshot — the "what to play with"
 * value object, separate from the "what to play" sequence.
 *
 * <p>Phase 5.1: replaces the imperative {@code setProgram} /
 * {@code setVolume} surface on {@link MidiPlayer} with a pure
 * value object that can be applied to a {@link Synthesizer}
 * idempotently. Live changes = build a new {@code ChannelSetup}
 * via {@link #from(Piece, List, List)} and call
 * {@link MidiPlayer#applySetup(ChannelSetup)}.</p>
 *
 * <p>Channel allocation mirrors {@code MidiCodec.assignChannels}:
 * drum tracks → channel 9; pitched tracks fill 0–8 then 10–15
 * (skipping 9). Throws {@link IllegalStateException} for >15 pitched
 * tracks.</p>
 *
 * @param programs per-channel program number (0–127). Drum channel
 *                 entries (channel 9) are kept for completeness but
 *                 most synths ignore the program on channel 9
 *                 (kit selection is bank-based, not program-based).
 * @param volumes  per-channel CC #7 level (0–127).
 */
public record ChannelSetup(
        Map<Integer, Integer> programs,
        Map<Integer, Integer> volumes
) {

    private static final int DRUM_CHANNEL = 9;
    private static final int MAX_CHANNEL = 15;

    public ChannelSetup {
        programs = Map.copyOf(programs);
        volumes = Map.copyOf(volumes);
    }

    /**
     * Build a {@code ChannelSetup} from a piece and per-track instrument
     * + volume selections. Pure: no I/O, no synth dependency.
     *
     * <p>Per-track lookups: if {@code instruments[i]} is null or absent,
     * falls back to the track's {@code defaultInstrument()} for melodic
     * tracks (or {@link Instrument#DRUM_KIT} for drum tracks). If
     * {@code volumes[i]} is null or absent, falls back to 100.</p>
     */
    public static ChannelSetup from(Piece piece,
                                    List<Instrument> instruments,
                                    List<Integer> volumes) {
        Map<Integer, Integer> programs = new LinkedHashMap<>();
        Map<Integer, Integer> volMap = new LinkedHashMap<>();
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
            programs.put(channel, ins.program());
            volMap.put(channel, clampVolume(vol));
        }
        return new ChannelSetup(programs, volMap);
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
    }

    private static Instrument defaultInstrumentOf(Track track) {
        return switch (track) {
            case MelodicTrack mt -> mt.defaultInstrument();
            case DrumTrack dt -> Instrument.DRUM_KIT;
        };
    }

    private static int clampVolume(int v) {
        return Math.max(0, Math.min(127, v));
    }
}
