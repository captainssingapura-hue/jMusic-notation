package music.notation.songs.anthem.internationale;

import music.notation.duration.Duration;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.songs.PieceHelper.*;
import static music.notation.songs.anthem.internationale.InternationaleTracks.*;

/**
 * The Internationale — Rock arrangement.
 *
 * <p>Reuses the shared melody, harmony, and chords tracks from
 * {@link InternationaleTracks}, adding a driving rock drum part
 * with kick/snare/hi-hat patterns and fills.</p>
 */
public final class RockInternationale implements PieceContentProvider<Internationale> {

    @Override
    public Piece create() {
        final var id = new Internationale();

        final var trackDecls = List.<TrackDecl>of(
                new TrackDecl.MusicTrackDecl("Melody",  FRENCH_HORN),
                new TrackDecl.MusicTrackDecl("Harmony", FRENCH_HORN),
                new TrackDecl.MusicTrackDecl("Chords",  STRING_ENSEMBLE_1),
                new TrackDecl.MusicTrackDecl("Drums",   DRUM_KIT)
        );

        final Duration SONG_DURATION = Duration.ofSixtyFourths(BAR_COUNT * TS.barSixtyFourths());

        final var anthem = Section.named("Anthem")
                .duration(SONG_DURATION)
                .timeSignature(TS)
                .track("Melody",  melodyPhrase())
                .track("Harmony", harmonyPhrase())
                .track("Chords",  chordsPhrase())
                .track("Drums",   drumsPhrase())
                .build();

        return Piece.ofSections(id.title(), id.composer(),
                KEY, TS,
                new Tempo(108, QUARTER),
                trackDecls,
                List.of(anthem));
    }

    // ── Drum track ────────────────────────────────────────────────

    private static Phrase drumsPhrase() {
        var nodes = new ArrayList<PhraseNode>();
        nodes.add(new DynamicNode(Dynamic.F));

        // Pickup bar: crash + kick on beat 4 (eighth pickup)
        nodes.add(d(CLOSED_HI_HAT, QUARTER));
        nodes.add(d(CLOSED_HI_HAT, QUARTER));
        nodes.add(d(CLOSED_HI_HAT, QUARTER));
        nodes.add(d(CRASH_CYMBAL, EIGHTH));
        nodes.add(d(BASS_DRUM, EIGHTH));

        // ── Verse (bars 1–8): driving eighth-note hi-hat, kick+snare backbeat ──
        for (int i = 0; i < 7; i++) {
            driveBar(nodes);
        }
        // Bar 8: fill into chorus
        fillA(nodes);

        // ── Chorus (bars 9–12): open hi-hat accent pattern ──
        for (int i = 0; i < 3; i++) {
            openDriveBar(nodes);
        }
        fillB(nodes);

        // ── Bars 13–16: back to drive ──
        for (int i = 0; i < 3; i++) {
            driveBar(nodes);
        }
        fillA(nodes);

        // ── Bars 17–20: verse 2 drive ──
        for (int i = 0; i < 3; i++) {
            driveBar(nodes);
        }
        fillB(nodes);

        // ── Bars 21–24: open drive ──
        for (int i = 0; i < 3; i++) {
            openDriveBar(nodes);
        }
        fillA(nodes);

        // ── Bars 25–28: half-time feel for build-up ──
        for (int i = 0; i < 4; i++) {
            halfTimeBar(nodes);
        }

        // ── Bars 29–31: climax — double-time feel ──
        for (int i = 0; i < 3; i++) {
            doubleTimeBar(nodes);
        }

        // ── Bar 32 (ending): crash and sustain ──
        nodes.add(d(CRASH_CYMBAL, EIGHTH));
        nodes.add(d(BASS_DRUM, EIGHTH));
        nodes.add(d(ACOUSTIC_SNARE, QUARTER));
        nodes.add(new RestNode(HALF));

        return new DrumPhrase(nodes, end());
    }

    // ── Patterns ──────────────────────────────────────────────────

    /** Standard rock drive: kick on 1&3, snare on 2&4, hi-hat eighths throughout. */
    private static void driveBar(List<PhraseNode> out) {
        // Beat 1: kick + hi-hat
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        // Beat 2: snare + hi-hat
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        // Beat 3: kick + hi-hat
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        // Beat 4: snare + hi-hat
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
    }

    /** Drive with open hi-hat on beats 2 and 4 for chorus intensity. */
    private static void openDriveBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));
    }

    /** Half-time: kick on 1, snare on 3, ride quarter notes. */
    private static void halfTimeBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, QUARTER));
        out.add(d(RIDE_CYMBAL, QUARTER));
        out.add(d(ACOUSTIC_SNARE, QUARTER));
        out.add(d(RIDE_CYMBAL, QUARTER));
    }

    /** Double-time feel: sixteenth-note hi-hat with kick/snare. */
    private static void doubleTimeBar(List<PhraseNode> out) {
        // Beat 1
        out.add(d(BASS_DRUM, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        // Beat 2
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(BASS_DRUM, SIXTEENTH));
        // Beat 3
        out.add(d(BASS_DRUM, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        // Beat 4
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(BASS_DRUM, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
    }

    /** Fill A: tom cascade into crash. */
    private static void fillA(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        // Fill on beats 3-4
        out.add(d(HIGH_TOM, EIGHTH));
        out.add(d(HIGH_MID_TOM, EIGHTH));
        out.add(d(LOW_TOM, EIGHTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    /** Fill B: snare flam build into crash. */
    private static void fillB(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));
        out.add(d(CLOSED_HI_HAT, EIGHTH));
        // Snare build
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new RockInternationale());
    }
}
