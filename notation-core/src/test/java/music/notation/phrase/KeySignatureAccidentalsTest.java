package music.notation.phrase;

import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.StaffPitch;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import org.junit.jupiter.api.Test;

import static music.notation.duration.BaseValue.QUARTER;
import static music.notation.pitch.NoteName.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the accidental resolution for each key signature.
 *
 * <p>Historically broken case: F major treated a bare "A" as A# because the
 * old {@code MAJOR_SHARPS} lookup table conflated F major (1 flat) with F#
 * major (6 sharps). This regression test suite prevents that class of bug
 * by exercising the full matrix of (tonic × accidental × mode) combinations
 * that real pieces use.</p>
 */
class KeySignatureAccidentalsTest {

    private static final TimeSignature TS = new TimeSignature(4, 4);

    // ── Natural-tonic major keys ─────────────────────────────────────

    @Test void cMajor_allNaturalByDefault() {
        var key = new KeySignature(C, Mode.MAJOR);
        assertAcc(key, C, Accidental.NATURAL);
        assertAcc(key, F, Accidental.NATURAL);
        assertAcc(key, B, Accidental.NATURAL);
    }

    @Test void gMajor_onlyFisSharp() {
        var key = new KeySignature(G, Mode.MAJOR);
        assertAcc(key, F, Accidental.SHARP);
        assertAcc(key, C, Accidental.NATURAL);
        assertAcc(key, G, Accidental.NATURAL);
    }

    @Test void dMajor_fAndCareSharp() {
        var key = new KeySignature(D, Mode.MAJOR);
        assertAcc(key, F, Accidental.SHARP);
        assertAcc(key, C, Accidental.SHARP);
        assertAcc(key, G, Accidental.NATURAL);
        assertAcc(key, B, Accidental.NATURAL);
    }

    @Test void fMajor_onlyBisFlat_andAisNatural() {
        // THE BUG: previously F major resolved A to A#. It should be A natural.
        var key = new KeySignature(F, Mode.MAJOR);
        assertAcc(key, A, Accidental.NATURAL);   // regression guard
        assertAcc(key, B, Accidental.FLAT);
        assertAcc(key, F, Accidental.NATURAL);
        assertAcc(key, C, Accidental.NATURAL);
        assertAcc(key, D, Accidental.NATURAL);
        assertAcc(key, E, Accidental.NATURAL);
        assertAcc(key, G, Accidental.NATURAL);
    }

    // ── Sharp-tonic major keys ───────────────────────────────────────

    @Test void fSharpMajor_sixSharps() {
        var key = new KeySignature(F, Accidental.SHARP, Mode.MAJOR);
        assertAcc(key, F, Accidental.SHARP);
        assertAcc(key, C, Accidental.SHARP);
        assertAcc(key, G, Accidental.SHARP);
        assertAcc(key, D, Accidental.SHARP);
        assertAcc(key, A, Accidental.SHARP);
        assertAcc(key, E, Accidental.SHARP);
        assertAcc(key, B, Accidental.NATURAL);   // 7th sharp would be B#, not in 6-sharp key
    }

    // ── Flat-tonic major keys ────────────────────────────────────────

    @Test void bFlatMajor_bAndEareFlat() {
        var key = new KeySignature(B, Accidental.FLAT, Mode.MAJOR);
        assertAcc(key, B, Accidental.FLAT);
        assertAcc(key, E, Accidental.FLAT);
        assertAcc(key, A, Accidental.NATURAL);
        assertAcc(key, F, Accidental.NATURAL);
    }

    @Test void eFlatMajor_threeFlats() {
        var key = new KeySignature(E, Accidental.FLAT, Mode.MAJOR);
        assertAcc(key, B, Accidental.FLAT);
        assertAcc(key, E, Accidental.FLAT);
        assertAcc(key, A, Accidental.FLAT);
        assertAcc(key, D, Accidental.NATURAL);
    }

    // ── Minor keys — natural tonic ───────────────────────────────────

    @Test void aMinor_noAccidentals() {
        var key = new KeySignature(A, Mode.MINOR);
        assertAcc(key, A, Accidental.NATURAL);
        assertAcc(key, B, Accidental.NATURAL);
        assertAcc(key, F, Accidental.NATURAL);
    }

    @Test void dMinor_onlyBisFlat() {
        var key = new KeySignature(D, Mode.MINOR);
        assertAcc(key, B, Accidental.FLAT);
        assertAcc(key, E, Accidental.NATURAL);
        assertAcc(key, F, Accidental.NATURAL);
    }

    @Test void gMinor_twoFlats() {
        // Previously buggy: old MINOR_TO_MAJOR.get(G) → B (B major, 5 sharps!).
        // Correct: G minor's relative major is Bb major → 2 flats (Bb, Eb).
        var key = new KeySignature(G, Mode.MINOR);
        assertAcc(key, B, Accidental.FLAT);
        assertAcc(key, E, Accidental.FLAT);
        assertAcc(key, F, Accidental.NATURAL);   // previously wrongly sharpened
        assertAcc(key, C, Accidental.NATURAL);
    }

    @Test void cMinor_threeFlats() {
        // Previously buggy: old MINOR_TO_MAJOR.get(C) → E (E major, 4 sharps!).
        // Correct: C minor's relative major is Eb major → 3 flats (Bb, Eb, Ab).
        var key = new KeySignature(C, Mode.MINOR);
        assertAcc(key, B, Accidental.FLAT);
        assertAcc(key, E, Accidental.FLAT);
        assertAcc(key, A, Accidental.FLAT);
        assertAcc(key, F, Accidental.NATURAL);   // previously wrongly sharpened
        assertAcc(key, D, Accidental.NATURAL);   // previously wrongly sharpened
    }

    @Test void fMinor_fourFlats() {
        // Previously buggy: old MINOR_TO_MAJOR.get(F) → A (A major, 3 sharps!).
        // Correct: F minor's relative major is Ab major → 4 flats (Bb, Eb, Ab, Db).
        var key = new KeySignature(F, Mode.MINOR);
        assertAcc(key, B, Accidental.FLAT);
        assertAcc(key, E, Accidental.FLAT);
        assertAcc(key, A, Accidental.FLAT);
        assertAcc(key, D, Accidental.FLAT);
        assertAcc(key, G, Accidental.NATURAL);
    }

    @Test void eMinor_onlyFisSharp() {
        var key = new KeySignature(E, Mode.MINOR);
        assertAcc(key, F, Accidental.SHARP);
        assertAcc(key, C, Accidental.NATURAL);
    }

    @Test void bMinor_fAndCareSharp() {
        var key = new KeySignature(B, Mode.MINOR);
        assertAcc(key, F, Accidental.SHARP);
        assertAcc(key, C, Accidental.SHARP);
        assertAcc(key, G, Accidental.NATURAL);
    }

    // ── Sharp-tonic minor keys ───────────────────────────────────────

    @Test void fSharpMinor_threeSharps() {
        var key = new KeySignature(F, Accidental.SHARP, Mode.MINOR);
        assertAcc(key, F, Accidental.SHARP);
        assertAcc(key, C, Accidental.SHARP);
        assertAcc(key, G, Accidental.SHARP);
        assertAcc(key, D, Accidental.NATURAL);
    }

    @Test void cSharpMinor_fourSharps() {
        var key = new KeySignature(C, Accidental.SHARP, Mode.MINOR);
        assertAcc(key, F, Accidental.SHARP);
        assertAcc(key, C, Accidental.SHARP);
        assertAcc(key, G, Accidental.SHARP);
        assertAcc(key, D, Accidental.SHARP);
        assertAcc(key, A, Accidental.NATURAL);
    }

    // ── Aeolian alias ─────────────────────────────────────────────────

    @Test void aeolianBehavesLikeMinor() {
        var minor   = new KeySignature(D, Mode.MINOR);
        var aeolian = new KeySignature(D, Mode.AEOLIAN);
        for (NoteName n : NoteName.values()) {
            assertEquals(resolve(minor, n).accidental(), resolve(aeolian, n).accidental(),
                    "Aeolian and Minor should resolve identically for " + n);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Build a bare-note phrase in the given key and assert the accidental on the note. */
    private static void assertAcc(KeySignature key, NoteName name, Accidental expected) {
        var pitch = resolve(key, name);
        assertEquals(expected, pitch.accidental(),
                "In key " + key + ", bare '" + name + "' should resolve to accidental "
                        + expected + " but got " + pitch.accidental());
    }

    /** Resolve a bare NoteName through the builder's key-signature logic. */
    private static StaffPitch resolve(KeySignature key, NoteName name) {
        var P = StaffPhraseBuilderTyped.in(key, TS, QUARTER);
        // QUARTER note + 3 QUARTER rests = 64sf, one 4/4 bar.
        var phrase = P.bar().o4(name).r(QUARTER).r(QUARTER).r(QUARTER).done()
                .build(new PhraseMarking(PhraseConnection.ATTACCA, true));
        return phrase.nodes().stream()
                .filter(n -> n instanceof NoteNode)
                .map(n -> (NoteNode) n)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no NoteNode emitted"))
                .pitch() instanceof StaffPitch sp
                ? sp
                : null;
    }
}
