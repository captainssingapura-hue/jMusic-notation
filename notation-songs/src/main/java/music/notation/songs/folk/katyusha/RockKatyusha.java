package music.notation.songs.folk.katyusha;

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
import static music.notation.songs.folk.katyusha.KatyushaTracks.*;

/**
 * Katyusha (Катюша) — Rock arrangement.
 *
 * <p>Seven verses modulating by fourths with escalating dynamics:
 * D→G→C→F, then D→G→C again one dynamic level stronger.
 * Reuses melody, bass, and chord templates from {@link KatyushaTracks},
 * applying sparse overrides for dynamics and {@link ShiftedPhrase} for
 * key/octave transposition.</p>
 */
public final class RockKatyusha implements PieceContentProvider<Katyusha> {

    // ── Verse specifications ─────────────────────────────────────

    private record VerseSpec(KeySignature key, int octaveShift, Dynamic dyn) {}

    private static final KeySignature DM = KEY; // D minor (source key)

    private static final KeySignature GM  = new KeySignature(G, Mode.MINOR);
    private static final KeySignature CM  = new KeySignature(C, Mode.MINOR);
    private static final KeySignature FM  = new KeySignature(F, Mode.MINOR);
    private static final VerseSpec[] VERSES = {
        // Pass 1: D → G → C → F
        new VerseSpec(DM, 0, Dynamic.F),
        new VerseSpec(GM, 0, Dynamic.FF),
        new VerseSpec(CM, 1, Dynamic.FFF),
        new VerseSpec(FM, 0, Dynamic.FF),
        // Pass 2: D → G → C (one level stronger)
        new VerseSpec(DM, 0, Dynamic.FF),
        new VerseSpec(GM, 0, Dynamic.FFF),
        new VerseSpec(CM, 1, Dynamic.FFF),
    };

    /** Coda: bars 5–8 in C minor octave 5, softer. */
    private static final VerseSpec CODA = new VerseSpec(CM, 1, Dynamic.MF);

    // ── Piece assembly ───────────────────────────────────────────

    @Override
    public Piece create() {
        final var id = new Katyusha();
        return new Piece(id.title(), id.composer(),
                KEY, TS,
                new Tempo(110, QUARTER),
                List.of(melody(), chords(), bass(), drums()));
    }

    // ── Melody ───────────────────────────────────────────────────

    private static Track melody() {
        var P = StaffPhraseBuilder.in(KEY, TS, EIGHTH);
        var template = buildVerse(P, attacca());
        var phrases = new ArrayList<Phrase>();

        for (VerseSpec v : VERSES) {
            // Override bar 0 to inject dynamic; all verses attacca (coda follows).
            // Voice overlays ride along on the MelodicPhrase — ShiftedPhrase
            // post-shifts all emitted MIDI notes, so voices shift with the main.
            var overridden = OverlayBuilder.over(template, KEY, TS, EIGHTH)
                    .at(0, b -> b.dyn(v.dyn)
                        .o4(QUARTER.dot(), D).o4(EIGHTH, E)
                        .o5(QUARTER.dot(), F).o4(EIGHTH, D))
                    .build(attacca());
            phrases.add(shift(overridden, v));
        }

        // Coda: bars 5–8 repeated softer in C minor (octave 5), rit. to 80 BPM
        var coda = P
                .bar(QUARTER).dyn(Dynamic.MF).transitionStart()
                    .o5(A).o5(D).o5(C).o5(EIGHTH,D).o5(EIGHTH,C)
                    .aux(HALF).o4(F).o4(F.s())
                .bar(QUARTER).o4(B,G).o4(EIGHTH,A).o4(EIGHTH,G).o4(A).o4(D)
                .bar(EIGHTH).r(EIGHTH).o4(QUARTER,B).o4(G).o4(QUARTER.dot(),A).o4(F)
                .bar(EIGHTH).o4(E).o3(A).o4(F).o4(E).rit(80).o4(QUARTER,D).r(QUARTER)
                .build(end());
        phrases.add(shift(coda, CODA));

        return Track.of("Melody", DISTORTION_GUITAR, phrases);
    }

    // ── Bass ─────────────────────────────────────────────────────

    private static Track bass() {
        var P = StaffPhraseBuilder.in(KEY, TS, QUARTER);
        var template = buildBassVerse(P, attacca());
        var phrases = new ArrayList<Phrase>();

        for (int i = 0; i < VERSES.length; i++) {
            var v = VERSES[i];

            var overridden = OverlayBuilder.over(template, KEY, TS, QUARTER)
                    .at(0, b -> b.dyn(v.dyn).o3(D).o3(A).o3(D).o3(A))
                    .build(attacca());

            phrases.add(shift(overridden, v));
        }

        // Coda: bars 5–8 (Bb, Gm→Dm, Gm→Dm, A→Dm) softer
        var coda = P
                .bar().dyn(Dynamic.MF).o2(B).o3(F).o2(B).o3(F)
                .bar().o3(G).o3(D).o3(D).o3(A)
                .bar().o3(G).o3(D).o3(D).o3(A)
                .bar().o2(A).o3(E).o2(HALF, D)
                .build(end());
        phrases.add(shift(coda, CODA));

        return Track.of("Bass", ELECTRIC_BASS_PICK, phrases);
    }

    // ── Chords ───────────────────────────────────────────────────

    private static Track chords() {
        var P = StaffPhraseBuilder.in(KEY, TS, HALF);
        var template = buildChordVerse(P, attacca());
        var phrases = new ArrayList<Phrase>();

        for (int i = 0; i < VERSES.length; i++) {
            var v = VERSES[i];

            var overridden = OverlayBuilder.over(template, KEY, TS, HALF)
                    .at(0, b -> b.dyn(v.dyn).o4(D, F, A).o4(D, F, A))
                    .build(attacca());

            phrases.add(shift(overridden, v));
        }

        // Coda: bars 5–8 (Bb, Gm→Dm, Gm→Dm, A→Dm) softer
        var coda = P
                .bar().dyn(Dynamic.MF)
                    .o3(B, D.higher(1), F.higher(1)).o3(B, D.higher(1), F.higher(1))
                .bar().o4(G, B, D.higher(1)).o4(D, F, A)
                .bar().o4(G, B, D.higher(1)).o4(D, F, A)
                .bar().o4(A, C.s().higher(1), E.higher(1)).o4(D, F, A)
                .build(end());
        phrases.add(shift(coda, CODA));

        return Track.of("Chords", DISTORTION_GUITAR, phrases);
    }

    // ── Key/octave shift helper ──────────────────────────────────

    private static Phrase shift(Phrase phrase, VerseSpec v) {
        if (v.key.equals(DM) && v.octaveShift == 0) return phrase;
        return new ShiftedPhrase(phrase, DM, v.key, v.octaveShift);
    }

    // ── Drums ────────────────────────────────────────────────────

    private static Track drums() {
        var nodes = new ArrayList<PhraseNode>();

        // ── Pass 1: D → G → C → F ──

        // Verse 1 (D minor, f): standard drive
        nodes.add(new DynamicNode(Dynamic.F));
        for (int i = 0; i < 7; i++) driveBar(nodes);
        fillA(nodes);

        // Verse 2 (G minor, ff): open hi-hat drive
        nodes.add(new DynamicNode(Dynamic.FF));
        for (int i = 0; i < 7; i++) openDriveBar(nodes);
        fillB(nodes);

        // Verse 3 (C minor, fff): double-time
        nodes.add(new DynamicNode(Dynamic.FFF));
        for (int i = 0; i < 7; i++) doubleTimeBar(nodes);
        fillA(nodes);

        // Verse 4 (F minor, ff): half-time breather
        nodes.add(new DynamicNode(Dynamic.FF));
        for (int i = 0; i < 7; i++) halfTimeBar(nodes);
        fillB(nodes);

        // ── Pass 2: D → G → C (one level stronger) ──

        // Verse 5 (D minor, ff): open drive
        nodes.add(new DynamicNode(Dynamic.FF));
        for (int i = 0; i < 7; i++) openDriveBar(nodes);
        fillA(nodes);

        // Verse 6 (G minor, fff): double-time
        nodes.add(new DynamicNode(Dynamic.FFF));
        for (int i = 0; i < 7; i++) doubleTimeBar(nodes);
        fillB(nodes);

        // Verse 7 (C minor, fff): double-time climax → transition fill
        nodes.add(new DynamicNode(Dynamic.FFF));
        for (int i = 0; i < 7; i++) doubleTimeBar(nodes);
        fillA(nodes);

        // Coda: 4 bars half-time, softer → crash ending
        nodes.add(new DynamicNode(Dynamic.MF));
        for (int i = 0; i < 3; i++) halfTimeBar(nodes);
        nodes.add(d(CRASH_CYMBAL, EIGHTH));
        nodes.add(d(BASS_DRUM, EIGHTH));
        nodes.add(d(ACOUSTIC_SNARE, QUARTER));
        nodes.add(new RestNode(HALF));

        return Track.of("Drums", DRUM_KIT, List.of(new DrumPhrase(nodes, end())));
    }

    // ── Drum patterns ────────────────────────────────────────────

    private static void driveBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(CLOSED_HI_HAT, EIGHTH));
    }

    private static void openDriveBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(OPEN_HI_HAT, EIGHTH));
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(OPEN_HI_HAT, EIGHTH));
    }

    private static void halfTimeBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, QUARTER));
        out.add(d(RIDE_CYMBAL, QUARTER));
        out.add(d(ACOUSTIC_SNARE, QUARTER));
        out.add(d(RIDE_CYMBAL, QUARTER));
    }

    private static void doubleTimeBar(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, SIXTEENTH));     out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH)); out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH)); out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH)); out.add(d(BASS_DRUM, SIXTEENTH));
        out.add(d(BASS_DRUM, SIXTEENTH));     out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH)); out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH)); out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(BASS_DRUM, SIXTEENTH));     out.add(d(CLOSED_HI_HAT, SIXTEENTH));
    }

    private static void fillA(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(HIGH_TOM, EIGHTH));     out.add(d(HIGH_MID_TOM, EIGHTH));
        out.add(d(LOW_TOM, EIGHTH));      out.add(d(CRASH_CYMBAL, EIGHTH));
    }

    private static void fillB(List<PhraseNode> out) {
        out.add(d(BASS_DRUM, EIGHTH));    out.add(d(CLOSED_HI_HAT, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, EIGHTH)); out.add(d(ACOUSTIC_SNARE, EIGHTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH)); out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH)); out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(CRASH_CYMBAL, EIGHTH)); out.add(d(BASS_DRUM, EIGHTH));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new RockKatyusha());
    }
}
