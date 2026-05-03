package music.notation.songs.anthem.internationale;

import music.notation.duration.Duration;
import music.notation.event.PercussionSound;
import music.notation.phrase.Bar;
import music.notation.phrase.PercussionNote;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.PitchNode;
import music.notation.phrase.RestNode;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.play.PlayPiece;
import music.notation.structure.DrumTrack;
import music.notation.structure.MelodicTrack;
import music.notation.structure.Piece;
import music.notation.structure.PieceContentProvider;
import music.notation.structure.Tempo;
import music.notation.structure.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.songs.PieceHelper.flattenMelodic;
import static music.notation.songs.anthem.internationale.InternationaleTracks.*;

/**
 * Grunge-rock arrangement of The Internationale, played 3× in a row.
 *
 * <p>Same melodic / harmony / chord content as {@link ManualInternationale}
 * but routed onto a rock-band instrument set with a kick/snare drum
 * pulse and a driving electric-bass riff for full-bodied feel. Tempo
 * dropped to 92 bpm for a sludgier groove. Suitable as a contrast
 * arrangement alongside the marching brass version.</p>
 */
public final class GrungeInternationale implements PieceContentProvider<Internationale> {

    private static final int REPEATS = 3;
    private static final int BAR_SIXTYFOURTHS = 64;   // 4/4

    @Override public String subtitle() { return "Grunge rock × 3"; }

    @Override
    public Piece create() {
        final var id = new Internationale();

        // Each melodic track runs the source phrase three times back-to-back.
        var leadPhrases    = repeat(InternationaleTracks::melodyPhrase,  REPEATS);
        var harmonyPhrases = repeat(InternationaleTracks::harmonyPhrase, REPEATS);
        var chordPhrases   = repeat(InternationaleTracks::chordsPhrase,  REPEATS);

        var lead    = flattenMelodic("Lead Guitar",    DISTORTION_GUITAR,   leadPhrases);
        var harmony = flattenMelodic("Harmony Guitar", OVERDRIVEN_GUITAR,   harmonyPhrases);
        var rhythm  = flattenMelodic("Power Chords",   DISTORTION_GUITAR,   chordPhrases);
        var pad     = flattenMelodic("Synth Pad",      SYNTH_PAD_WARM,      chordPhrases);

        // Bass + drum patterns sized to the full repeated length.
        int totalBars = REPEATS * BAR_COUNT;
        var bass  = new MelodicTrack("Bass", ELECTRIC_BASS_FINGER,
                Phrase.of(buildBassBars(totalBars)));
        var drums = new DrumTrack("Drums",
                Phrase.of(buildDrumBars(totalBars)));

        List<Track> tracks = List.of(lead, harmony, rhythm, pad, bass, drums);

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS,
                new Tempo(92, QUARTER),
                tracks.stream().filter(t -> t instanceof MelodicTrack)
                        .map(t -> (MelodicTrack) t).toList(),
                List.of(drums));
    }

    // ── Phrase helpers ────────────────────────────────────────────────

    private static List<music.notation.phrase.AuthorPhrase> repeat(
            java.util.function.Supplier<music.notation.phrase.AuthorPhrase> factory, int n) {
        var out = new ArrayList<music.notation.phrase.AuthorPhrase>(n);
        for (int i = 0; i < n; i++) out.add(factory.get());
        return out;
    }

    // ── Bass — driving root-note rhythm ───────────────────────────────

    private static List<Bar> buildBassBars(int totalBars) {
        var bars = new ArrayList<Bar>(totalBars);
        bars.add(silentBar());                // pickup bar — bass enters on bar 1
        for (int i = 0; i < totalBars - 1; i++) {
            bars.add((i % 4) == 3 ? bassBarEnd() : bassBarSteady());
        }
        return bars;
    }

    /** Steady A1 quarter-note pulse — the workhorse pattern. */
    private static Bar bassBarSteady() {
        var a1 = PitchNode.of(Pitch.of(NoteName.A, 1), Duration.of(QUARTER));
        return Bar.of(BAR_SIXTYFOURTHS, a1, a1, a1, a1);
    }

    /** A-A-A-E end-of-phrase variation, every 4th bar. */
    private static Bar bassBarEnd() {
        var a1 = PitchNode.of(Pitch.of(NoteName.A, 1), Duration.of(QUARTER));
        var e1 = PitchNode.of(Pitch.of(NoteName.E, 1), Duration.of(QUARTER));
        return Bar.of(BAR_SIXTYFOURTHS, a1, a1, a1, e1);
    }

    // ── Drums — rock pulse with crash on every 8th bar ────────────────

    private static List<Bar> buildDrumBars(int totalBars) {
        var bars = new ArrayList<Bar>(totalBars);
        bars.add(silentBar());
        for (int i = 0; i < totalBars - 1; i++) {
            bars.add((i % 8) == 0 ? drumBarWithCrash() : drumBarStandard());
        }
        return bars;
    }

    /** Sequential rock pattern: K hh S hh K hh S hh (eighth notes). */
    private static Bar drumBarStandard() {
        var k = new PercussionNote(PercussionSound.BASS_DRUM, Duration.of(EIGHTH));
        var h = new PercussionNote(PercussionSound.CLOSED_HI_HAT, Duration.of(EIGHTH));
        var s = new PercussionNote(PercussionSound.ACOUSTIC_SNARE, Duration.of(EIGHTH));
        return Bar.of(BAR_SIXTYFOURTHS, k, h, s, h, k, h, s, h);
    }

    /** Same, but a crash hit replaces beat 1 for section accent. */
    private static Bar drumBarWithCrash() {
        var c = new PercussionNote(PercussionSound.CRASH_CYMBAL, Duration.of(EIGHTH));
        var h = new PercussionNote(PercussionSound.CLOSED_HI_HAT, Duration.of(EIGHTH));
        var s = new PercussionNote(PercussionSound.ACOUSTIC_SNARE, Duration.of(EIGHTH));
        var k = new PercussionNote(PercussionSound.BASS_DRUM, Duration.of(EIGHTH));
        return Bar.of(BAR_SIXTYFOURTHS, c, h, s, h, k, h, s, h);
    }

    // ── Shared ───────────────────────────────────────────────────────

    private static Bar silentBar() {
        return Bar.of(BAR_SIXTYFOURTHS,
                (PhraseNode) new RestNode(Duration.ofSixtyFourths(BAR_SIXTYFOURTHS)));
    }

    /** Convenience: launch this arrangement directly. */
    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new GrungeInternationale());
    }

    @SuppressWarnings("unused")
    private static List<Bar> immutable(List<Bar> bars) { return Collections.unmodifiableList(bars); }
}
