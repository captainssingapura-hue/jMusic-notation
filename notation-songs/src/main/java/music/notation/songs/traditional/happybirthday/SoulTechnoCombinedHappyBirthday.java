package music.notation.songs.traditional.happybirthday;

import music.notation.event.Dynamic;
import music.notation.phrase.DrumPhrase;
import music.notation.phrase.DynamicNode;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseNode;
import music.notation.phrase.StaffPhraseBuilder;
import music.notation.play.PlayPiece;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * "Happy Birthday" — Soul Techno arrangement of the Combined tour.
 *
 * <p>Reuses {@link CombinedHappyBirthday}'s melody (Lead Synth) and
 * accompaniment (Rhodes) tracks as-is — same phrases, just re-instrumented.
 * Adds three rhythm-section tracks that play throughout all 10 sections
 * (5 styles × 2 passes):
 * <ul>
 *   <li><b>Slap Bass</b> — walking six-eighths-per-bar pattern through the
 *       C / C / G7 / G7 / C / C / C / C / F progression.</li>
 *   <li><b>Warm Pad</b> — sustained dotted-half chord per bar following the
 *       same harmony, filling out the spectrum.</li>
 *   <li><b>Drums</b> — 3/4 techno-soul pattern: kick → hat → clap → hat →
 *       kick → open-hat per bar.</li>
 * </ul>
 * The rhythm section plays a steady 9 bars per section (matching the
 * elision-shortened playback length of the RH/LH). Because the <b>first</b>
 * track (Lead Synth = RH) contains all the {@code TempoChangeNode} markers,
 * every track automatically follows the per-style tempo arc
 * (112 → 132 → 76 → 120 → 88 → repeat) via MIDI global tempo.</p>
 */
public final class SoulTechnoCombinedHappyBirthday implements PieceContentProvider<HappyBirthday> {

    private static final KeySignature KEY = DefaultHappyBirthday.KEY;   // C major
    private static final TimeSignature TS = DefaultHappyBirthday.TS;    // 3/4
    private static final int SECTIONS = 10;            // 5 styles × 2 passes

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "Soul Techno Tour"; }

    @Override
    public Piece create() {
        final var id = new HappyBirthday();

        // Reuse the combined tour's Melody + Accompaniment tracks verbatim.
        // Just re-instrument them as Lead Synth and Rhodes; phrases are shared.
        final Piece combined = new CombinedHappyBirthday().create();
        final Track leadSynth = new Track(
                "Lead Synth", SYNTH_LEAD_SAWTOOTH,
                combined.tracks().get(0).phrases(), List.of());
        final Track rhodes = new Track(
                "Rhodes", ELECTRIC_PIANO_1,
                combined.tracks().get(1).phrases(), List.of());

        return new Piece(id.title(), id.composer(), KEY, TS,
                combined.tempo(),  // 112 — immediately overridden by RH's tempo markers
                List.of(leadSynth, rhodes, slapBassTrack(), warmPadTrack(), drumsTrack()));
    }

    // ════════════════════════════════════════════════════════════════
    //  Slap Bass — 6-eighth walking bass per bar
    // ════════════════════════════════════════════════════════════════

    private Track slapBassTrack() {
        final List<Phrase> phrases = new ArrayList<>();
        for (int s = 0; s < SECTIONS; s++) phrases.add(bassSection());
        return Track.of("Slap Bass", SLAP_BASS, phrases);
    }

    /** 9-bar bass line matching LH's trimmed progression: C C G7 | G7 C C | C C F. */
    private MelodicPhrase bassSection() {
        var bb = b();
        bassC(bb);  bassC(bb);  bassG7(bb);       // bars 1–3 (line 1)
        bassG7(bb); bassC(bb);  bassC(bb);        // bars 4–6 (line 2)
        bassC(bb);  bassC(bb);  bassF(bb);        // bars 7–9 (line 3)
        return bb.build(end());
    }

    /** C bar walking bass: C E G C G E (6 eighths = 48sf). */
    private static void bassC(StaffPhraseBuilder bb) {
        bb.bar(EIGHTH).o2(C).o3(E).o3(G).o4(C).o3(G).o3(E);
    }

    /** G7 bar walking bass: G B D F D B (root-3-5-♭7-5-3). */
    private static void bassG7(StaffPhraseBuilder bb) {
        bb.bar(EIGHTH).o2(G).o2(B).o3(D).o3(F).o3(D).o2(B);
    }

    /** F bar walking bass: F A C F C A. */
    private static void bassF(StaffPhraseBuilder bb) {
        bb.bar(EIGHTH).o2(F).o2(A).o3(C).o3(F).o3(C).o2(A);
    }

    // ════════════════════════════════════════════════════════════════
    //  Warm Pad — one sustained triad per bar
    // ════════════════════════════════════════════════════════════════

    private Track warmPadTrack() {
        final List<Phrase> phrases = new ArrayList<>();
        for (int s = 0; s < SECTIONS; s++) phrases.add(padSection());
        return Track.of("Warm Pad", SYNTH_PAD_WARM, phrases);
    }

    private MelodicPhrase padSection() {
        var bb = b();
        padC(bb);  padC(bb);  padG7(bb);
        padG7(bb); padC(bb);  padC(bb);
        padC(bb);  padC(bb);  padF(bb);
        return bb.build(end());
    }

    private static void padC(StaffPhraseBuilder bb) {
        bb.bar().o3(HALF.dot(), C, E, G);                    // C major triad
    }

    private static void padG7(StaffPhraseBuilder bb) {
        bb.bar().o3(HALF.dot(), G, B, F);                    // G7 (no 5th) — root, 3rd, ♭7
    }

    private static void padF(StaffPhraseBuilder bb) {
        bb.bar().o3(HALF.dot(), F, A, C.higher(1));          // F major (spread)
    }

    // ════════════════════════════════════════════════════════════════
    //  Drums — 3/4 techno-soul pattern per bar
    // ════════════════════════════════════════════════════════════════

    private Track drumsTrack() {
        final List<Phrase> phrases = new ArrayList<>();
        for (int s = 0; s < SECTIONS; s++) phrases.add(drumsSection());
        return Track.of("Drums", DRUM_KIT, phrases);
    }

    /**
     * 9 bars of 6-eighth techno pattern per bar:
     *   [1]   kick    [1&]  hat
     *   [2]   clap    [2&]  hat     ← soul backbeat
     *   [3]   kick    [3&]  open-hat
     */
    private DrumPhrase drumsSection() {
        final var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MF));
        for (int bar = 0; bar < 9; bar++) {
            n.add(d(BASS_DRUM,     EIGHTH));
            n.add(d(CLOSED_HI_HAT, EIGHTH));
            n.add(d(HAND_CLAP,     EIGHTH));
            n.add(d(CLOSED_HI_HAT, EIGHTH));
            n.add(d(BASS_DRUM,     EIGHTH));
            n.add(d(OPEN_HI_HAT,   EIGHTH));
        }
        return new DrumPhrase(n, end());
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new SoulTechnoCombinedHappyBirthday());
    }
}
