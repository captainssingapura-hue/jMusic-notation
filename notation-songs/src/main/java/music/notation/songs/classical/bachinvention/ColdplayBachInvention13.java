package music.notation.songs.classical.bachinvention;

import music.notation.event.Dynamic;
import music.notation.phrase.*;
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
 * Invention No. 13 in A Minor (BWV 784) — Coldplay-style rock arrangement.
 *
 * <p>Transforms Bach's two-part counterpoint into an anthemic rock setting:
 * the right-hand melody becomes an overdriven guitar lead, the left hand
 * is reimagined as rock organ chords, with shimmering clean guitar arpeggios,
 * driving bass, atmospheric synth pad, and building drums.</p>
 */
public final class ColdplayBachInvention13 implements PieceContentProvider<BachInvention13> {

    private static final KeySignature KEY = ManualBachInvention13.KEY;
    private static final TimeSignature TS = ManualBachInvention13.TS;
    private final ManualBachInvention13 bach = new ManualBachInvention13();

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, QUARTER);
    }

    private StaffPhraseBuilder b16() {
        return StaffPhraseBuilder.in(KEY, TS, SIXTEENTH);
    }

    @Override public String subtitle() { return "Coldplay Rock"; }

    @Override
    public Piece create() {
        var id = new BachInvention13();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(100, QUARTER),
                List.of(leadGuitar(), counterGuitar(), rhythmGuitar(), organ(), bass(), synthPad(), drums()));
    }

    // ── Lead Guitar: Bach's RH melody on overdriven guitar ──────────

    private Track leadGuitar() {
        return Track.of("Lead Guitar", OVERDRIVEN_GUITAR, List.of(
                bach.buildRhSection1(), bach.buildRhSection2(), bach.buildRhSection3()));
    }

    // ── Counter Guitar: Bach's LH counterpoint on clean guitar ────

    private Track counterGuitar() {
        return Track.of("Counter Guitar", ELECTRIC_GUITAR_CLEAN, List.of(
                bach.buildLhSection1(), bach.buildLhSection2(), bach.buildLhSection3()));
    }

    // ── Rhythm Guitar: shimmering Coldplay clean arpeggios ──────────

    private Track rhythmGuitar() {
        return Track.of("Rhythm Guitar", ELECTRIC_GUITAR_CLEAN, List.of(
                guitarSection1(), guitarSection2(), guitarSection3()));
    }

    // Section 1 (bars 1-6): sparse entry, dotted arpeggios
    private MelodicPhrase guitarSection1() {
        return b()
                .bar().r(HALF).o4(QUARTER.dot(), E).o4(EIGHTH, A)
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, G.s()).o4(QUARTER.dot(), B).o4(EIGHTH, E)
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, F).o4(QUARTER.dot(), A).o4(EIGHTH, C)
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), B).o5(EIGHTH, C)
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o4(QUARTER.dot(), F).o4(EIGHTH, A)
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(HALF, C)
                .build(attacca());
    }

    // Section 2 (bars 7-13): full arpeggios
    private MelodicPhrase guitarSection2() {
        return b()
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), G).o5(EIGHTH, C)
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), A).o5(EIGHTH, C)
                .bar().o4(QUARTER.dot(), A).o4(EIGHTH, C).o4(QUARTER.dot(), D).o4(EIGHTH, F.s())
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G).o4(QUARTER.dot(), E).o4(EIGHTH, G)
                .bar().o4(QUARTER.dot(), D.s()).o4(EIGHTH, F.s()).o4(QUARTER.dot(), B).o5(EIGHTH, E)
                .bar().o4(QUARTER.dot(), A).o4(EIGHTH, C).o4(QUARTER.dot(), D).o4(EIGHTH, F.s())
                .bar().o4(HALF, E, B).o4(HALF, E, B)
                .build(attacca());
    }

    // Section 3 (bars 14-25): climactic sequences then resolve
    private MelodicPhrase guitarSection3() {
        return b()
                .bar().o4(QUARTER.dot(), C.s()).o4(EIGHTH, E).o4(QUARTER.dot(), A).o5(EIGHTH, C.s())
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, F).o4(QUARTER.dot(), B).o5(EIGHTH, D)
                .bar().o4(QUARTER.dot(), C).o4(EIGHTH, E).o4(QUARTER.dot(), A).o5(EIGHTH, C)
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, G.s()).o4(QUARTER.dot(), B).o5(EIGHTH, D)
                .bar().o4(QUARTER.dot(), E).o4(EIGHTH, A).o4(QUARTER.dot(), C).o4(EIGHTH, E)
                .bar().o4(QUARTER.dot(), A).o4(EIGHTH, C).o4(QUARTER.dot(), F.s()).o4(EIGHTH, A)
                .bar().o4(QUARTER.dot(), G.s()).o4(EIGHTH, B).o4(QUARTER.dot(), D).o4(EIGHTH, G.s())
                .bar().o4(QUARTER.dot(), A).o4(EIGHTH, C).o4(QUARTER.dot(), D.s()).o4(EIGHTH, F.s())
                .bar().o4(QUARTER.dot(), G.s()).o4(EIGHTH, B).o4(QUARTER.dot(), E).o4(EIGHTH, A)
                .bar().o4(QUARTER.dot(), A).o4(EIGHTH, C).o4(QUARTER.dot(), D).o4(EIGHTH, F)
                .bar().o4(QUARTER.dot(), G.s()).o4(EIGHTH, B).o5(QUARTER.dot(), D).o5(EIGHTH, F)
                .bar().o4(HALF, A, E.higher(1)).o3(HALF, A, E)
                .build(attacca());
    }

    // ── Rock Organ: sustained chords derived from Bach's LH ─────────

    private Track organ() {
        return Track.of("Organ", ROCK_ORGAN, List.of(
                organSection1(), organSection2(), organSection3()));
    }

    // Section 1 (bars 1-6): Am | Am/E | F→Dm | G→C | Am→Dm→G | F→G→Am
    private MelodicPhrase organSection1() {
        return b()
                .bar().r(WHOLE)
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, F, A, C.higher(1)).o3(HALF, D, F, A)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, C, E, G)
                .bar().o3(QUARTER, A, C.higher(1), E.higher(1)).o3(QUARTER, F, A, D.higher(1)).o3(QUARTER, G, B, D.higher(1)).o3(QUARTER, E, G, B)
                .bar().o3(HALF, F, A, D.higher(1)).o3(QUARTER, G, B, D.higher(1)).o3(QUARTER, C, E, G)
                .build(attacca());
    }

    // Section 2 (bars 7-13): C | C→Am | Am→D | G→Em | B7→Em | Am→D | Em
    private MelodicPhrase organSection2() {
        return b()
                .bar().o3(WHOLE, C, E, G)
                .bar().o3(HALF, C, E, G).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, D, F.s(), A)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, E, G, B)
                .bar().o3(HALF, B, D.s(), F.s()).o3(HALF, E, G, B)
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, D, F.s(), A)
                .bar().o3(WHOLE, E, G, B)
                .build(attacca());
    }

    // Section 3 (bars 14-25): modulation through to final cadence
    private MelodicPhrase organSection3() {
        return b()
                .bar().o3(HALF, A, C.s(), E).o3(HALF, D, F, A)
                .bar().o3(HALF, D, F, A).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, C, E, G).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, G.s(), B, D).o3(HALF, E, G.s(), B)
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, F.s(), A, C.higher(1))
                .bar().o3(HALF, E, G.s(), B).o3(HALF, G.s(), B, D.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, D.s(), F.s(), A)
                .bar().o3(HALF, E, G.s(), B).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, D, F, A)
                .bar().o3(HALF, E, G.s(), B).o3(HALF, D, F, G.s())
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .build(attacca());
    }

    // ── Bass: driving rock bass ─────────────────────────────────────

    private Track bass() {
        return Track.of("Bass", ELECTRIC_BASS_PICK, List.of(
                bassSection1(), bassSection2(), bassSection3()));
    }

    // Section 1 (bars 1-6): sparse entry building to eighths
    private MelodicPhrase bassSection1() {
        return b()
                .bar().r(WHOLE)
                .bar().o2(HALF, A).o2(HALF, E)
                .bar(EIGHTH).o2(F).o2(F).o3(C).o3(C).o2(A).o2(A).o3(E).o3(E)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o3(C).o3(C).o3(G).o3(G)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(D).o2(D).o3(A).o3(A)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o3(C).o3(C).o3(G).o3(G)
                .build(attacca());
    }

    // Section 2 (bars 7-13): full driving eighths
    private MelodicPhrase bassSection2() {
        return b()
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o3(C).o3(C).o3(G).o3(C)
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o2(A).o2(A).o3(E).o3(E)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(D).o2(D).o3(A).o3(A)
                .bar(EIGHTH).o2(G).o2(G).o3(D).o3(D).o2(E).o2(E).o2(B).o2(B)
                .bar(EIGHTH).o2(B).o2(B).o2(F.s()).o2(F.s()).o2(E).o2(E).o2(B).o2(B)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(D).o2(D).o3(A).o3(A)
                .bar(EIGHTH).o2(E).o2(E).o2(B).o2(B).o2(E).o2(E).o2(B).o2(B)
                .build(attacca());
    }

    // Section 3 (bars 14-25): driving through recapitulation
    private MelodicPhrase bassSection3() {
        return b()
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(D).o2(D).o3(A).o3(A)
                .bar(EIGHTH).o2(D).o2(D).o3(A).o3(A).o2(G).o2(G).o3(D).o3(D)
                .bar(EIGHTH).o3(C).o3(C).o3(G).o3(G).o2(A).o2(A).o3(E).o3(E)
                .bar(EIGHTH).o2(E).o2(E).o2(B).o2(B).o2(G.s()).o2(G.s()).o3(D).o3(D)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(A).o2(A).o3(E).o3(E)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(F.s()).o2(F.s()).o3(C).o3(C)
                .bar(EIGHTH).o2(E).o2(E).o2(B).o2(B).o2(E).o2(E).o2(B).o2(B)
                .bar(EIGHTH).o2(A).o2(A).o3(E).o3(E).o2(D.s()).o2(D.s()).o2(A).o2(A)
                .bar(EIGHTH).o2(E).o2(E).o2(B).o2(B).o2(A).o2(A).o3(E).o3(E)
                .bar(EIGHTH).o2(A).o2(A).o2(D).o2(D).o2(E).o2(E).o2(B).o2(B)
                .bar(EIGHTH).o2(G.s()).o2(A).o2(D).o2(E).o2(F).o2(D.s()).o2(E).o3(E)
                .bar().o2(HALF, A).o2(HALF, A)
                .build(attacca());
    }

    // ── Synth Pad: atmospheric sustained chords ─────────────────────

    private Track synthPad() {
        return Track.of("Synth Pad", SYNTH_PAD_WARM, List.of(
                padSection1(), padSection2(), padSection3()));
    }

    // Section 1 (bars 1-6): building from silence
    private MelodicPhrase padSection1() {
        return b()
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().o3(WHOLE, F, A, C.higher(1))
                .bar().o3(WHOLE, G, B, D.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, D, F, A)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, C, E, G)
                .build(attacca());
    }

    // Section 2 (bars 7-13): full atmospheric presence
    private MelodicPhrase padSection2() {
        return b()
                .bar().o3(WHOLE, C, E, G)
                .bar().o3(HALF, C, E, G).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, D, F.s(), A)
                .bar().o3(HALF, G, B, D.higher(1)).o3(HALF, E, G, B)
                .bar().o3(HALF, B, D.s(), F.s()).o3(HALF, E, G, B)
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, D, F.s(), A)
                .bar().o3(WHOLE, E, G, B)
                .build(attacca());
    }

    // Section 3 (bars 14-25): modulation colors then resolve
    private MelodicPhrase padSection3() {
        return b()
                .bar().o3(HALF, A, C.s(), E).o3(HALF, D, F, A)
                .bar().o3(HALF, D, F, A).o3(HALF, G, B, D.higher(1))
                .bar().o3(HALF, C, E, G).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, G.s(), B, D).o3(HALF, E, G.s(), B)
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, F.s(), A, C.higher(1))
                .bar().o3(HALF, G.s(), B, D.higher(1)).o3(HALF, E, G.s(), B)
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, D.s(), F.s(), A)
                .bar().o3(HALF, E, G.s(), B).o3(HALF, A, C.higher(1), E.higher(1))
                .bar().o3(HALF, A, C.higher(1), E.higher(1)).o3(HALF, D, F, A)
                .bar().o3(HALF, E, G.s(), B).o3(HALF, D, F, G.s())
                .bar().o3(WHOLE, A, C.higher(1), E.higher(1))
                .build(attacca());
    }

    // ── Drums: Coldplay anthemic build ───────────────────────────────

    private Track drums() {
        return Track.of("Drums", DRUM_KIT, List.of(
                drumsSection1(), drumsSection2(), drumsSection3()));
    }

    // Section 1 (bars 1-6): sparse — hi-hat ticks building to half-time
    private DrumPhrase drumsSection1() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.P));
        // bars 1-2: just hi-hat ticks
        for (int i = 0; i < 2; i++) tickBar(n);
        // bars 3-4: add kick on 1
        n.add(new DynamicNode(Dynamic.MP));
        for (int i = 0; i < 2; i++) kickTickBar(n);
        // bars 5-6: half-time feel
        for (int i = 0; i < 2; i++) halfTimeBar(n);
        return new DrumPhrase(n, attacca());
    }

    // Section 2 (bars 7-13): full Coldplay backbeat
    private DrumPhrase drumsSection2() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 6; i++) coldplayBar(n);
        // bar 13: fill into section 3
        coldplayFill(n);
        return new DrumPhrase(n, attacca());
    }

    // Section 3 (bars 14-25): climactic build and resolution
    private DrumPhrase drumsSection3() {
        var n = new ArrayList<PhraseNode>();
        // bars 14-17: half-time with ride — pulling back
        n.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 4; i++) rideHalfTime(n);
        // bars 18-21: rebuilding — standard Coldplay
        n.add(new DynamicNode(Dynamic.F));
        for (int i = 0; i < 4; i++) coldplayBar(n);
        // bars 22-24: anthemic — crashing
        for (int i = 0; i < 2; i++) anthemBar(n);
        coldplayFill(n);
        // bar 25: final — crash and sustain
        n.add(d(CRASH_CYMBAL, EIGHTH));
        n.add(d(BASS_DRUM, EIGHTH));
        n.add(d(ACOUSTIC_SNARE, QUARTER));
        n.add(new RestNode(HALF));
        return new DrumPhrase(n, end());
    }

    // ── Drum patterns ───────────────────────────────────────────────

    private static void tickBar(List<PhraseNode> out) {
        for (int i = 0; i < 4; i++) out.add(d(CLOSED_HI_HAT, QUARTER));
    }

    private static void kickTickBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, QUARTER));
        for (int i = 0; i < 3; i++) out.add(d(CLOSED_HI_HAT, QUARTER));
    }

    private static void halfTimeBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));  out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(OPEN_HI_HAT, EIGHTH));  out.add(d(OPEN_HI_HAT, EIGHTH));
    }

    private static void coldplayBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));      out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));      out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(CLOSED_HI_HAT, EIGHTH));
    }

    private static void rideHalfTime(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));      out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));    out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(RIDE_CYMBAL, EIGHTH));
        out.add(d(RIDE_CYMBAL, EIGHTH));    out.add(d(RIDE_CYMBAL, EIGHTH));
    }

    private static void anthemBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));      out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));      out.add(d(CRASH_CYMBAL, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    private static void coldplayFill(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));      out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(HIGH_TOM, EIGHTH));       out.add(d(HIGH_MID_TOM, EIGHTH));
        out.add(d(LOW_TOM, EIGHTH));        out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new ColdplayBachInvention13());
    }
}
