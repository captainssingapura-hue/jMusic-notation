package music.notation.songs.game.contra;

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
 * Contra — Base Theme (Stage 1).
 *
 * <p>Faithful NES arrangement: two synth leads (melody + harmony),
 * slap bass, and drums. The piece is in C minor at 146 BPM.
 * Structure: intro (1 bar) + main body (31 bars) repeated twice,
 * ending with a 5-bar fade on the main theme.</p>
 */
public final class DefaultContraBase implements PieceContentProvider<ContraBase> {

    private static final KeySignature KEY = new KeySignature(C, Mode.MINOR);
    private static final TimeSignature TS = new TimeSignature(4, 4);

    /** Builder defaulting to SIXTEENTH notes. */
    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, SIXTEENTH);
    }

    @Override public String subtitle() { return "NES Original"; }

    @Override
    public Piece create() {
        var id = new ContraBase();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(146, QUARTER),
                List.of(melody1(), melody2(), bass(), drums()));
    }

    // ════════════════════════════════════════════════════════════════
    //  MELODY 1 — main lead (SYNTH_LEAD_SAWTOOTH)
    // ════════════════════════════════════════════════════════════════

    private Track melody1() {
        return Track.of("Melody 1", SYNTH_LEAD_SAWTOOTH, List.of(
                m1Intro(),
                m1ThemeA(), m1ThemeB(), m1Transition(), m1Bridge(),
                m1ThemeC(), m1ThemeD(), m1Turnaround(),
                m1ThemeA(), m1ThemeB(), m1Transition(), m1Bridge(),
                m1ThemeC(), m1ThemeD(), m1Turnaround(),
                m1Ending()));
    }

    // ── Intro (bar 1): iconic descending run ─────────────────────
    private MelodicPhrase m1Intro() {
        return b()
                .bar().o5(C).o4(A.s()).o4(G).o4(F).o4(G).o4(F).o4(D.s()).o4(D).o4(D.s()).o4(D).o4(C).o3(A.s()).o3(A.s()).o3(F).o3(G).o3(A.s())
                .build(attacca());
    }

    // ── Theme A (bars 2-9): sustained notes + pickup figures ─────
    private MelodicPhrase m1ThemeA() {
        return b()
                // bar 2: C held, pickup → G#
                .bar().o4(HALF, C).r(EIGHTH.dot()).o4(G).o4(F).o4(EIGHTH, G).o4(G.s())
                // bar 3: A# whole
                .bar().o4(WHOLE, A.s())
                // bar 4: C held, pickup → A
                .bar().o4(HALF, C).r(EIGHTH.dot()).o4(G).o4(F).o4(EIGHTH, G).o4(A)
                // bar 5: D# whole
                .bar().o4(WHOLE, D.s())
                // bar 6 = bar 2
                .bar().o4(HALF, C).r(EIGHTH.dot()).o4(G).o4(F).o4(EIGHTH, G).o4(G.s())
                // bar 7: A# whole
                .bar().o4(WHOLE, A.s())
                // bar 8 = bar 4
                .bar().o4(HALF, C).r(EIGHTH.dot()).o4(G).o4(F).o4(EIGHTH, G).o4(A)
                // bar 9: D# whole
                .bar().o4(WHOLE, D.s())
                .build(attacca());
    }

    // ── Theme B (bars 10-13): syncopated riff ────────────────────
    private MelodicPhrase m1ThemeB() {
        return b()
                // bar 10
                .bar().o4(G).r().o4(EIGHTH.dot(), G).o4(EIGHTH.dot(), A).o4(A.s()).o4(A.s()).o4(G).o4(A.s()).o5(C).ending()
                // bar 11
                .bar().o4(A).r().o4(EIGHTH.dot(), A).o4(EIGHTH.dot(), A.s()).o5(C).o5(C).o4(A.s()).o5(EIGHTH.dot(), C).ending()
                // bar 12 = bar 10
                .bar().o4(G).r().o4(EIGHTH.dot(), G).o4(EIGHTH.dot(), A).o4(A.s()).o4(A.s()).o4(G).o4(A.s()).o5(C).ending()
                // bar 13: resolves into sustained notes
                .bar().o4(A).r().o4(EIGHTH.dot(), A).o4(EIGHTH.dot(), A.s()).o5(QUARTER, C).o5(QUARTER, F)
                .build(attacca());
    }

    // ── Transition (bar 14): fast descending run ─────────────────
    private MelodicPhrase m1Transition() {
        return b()
                .bar().o5(G).o5(F).o5(G).o5(F).o6(D).o6(C).o5(A.s()).o5(A).o5(G).o5(F).o5(G).o5(F).o5(D).o5(C).o5(D).o5(C)
                .build(attacca());
    }

    // ── Bridge (bars 15-16) ──────────────────────────────────────
    private MelodicPhrase m1Bridge() {
        return b()
                // bar 15
                .bar().o3(EIGHTH.dot(), G).r().o3(G).o4(EIGHTH, D).o4(D.s()).o4(G).o4(F).o4(D.s()).o4(EIGHTH, D).o3(A.s()).ending()
                // bar 16
                .bar().o3(C).o3(C).o3(C).r().o3(C).o3(EIGHTH.dot(), D).o3(EIGHTH.dot(), D.s()).o3(C).o3(F).o3(EIGHTH.dot(), G)
                .build(attacca());
    }

    // ── Theme C (bars 17-22): ascending melody ───────────────────
    private MelodicPhrase m1ThemeC() {
        return b()
                // bar 17
                .bar().o3(QUARTER, F).o3(G).o3(EIGHTH.dot(), A).o3(QUARTER, A.s()).o4(C).o4(EIGHTH.dot(), D)
                // bar 18
                .bar().o4(QUARTER, D.s()).o4(D.s()).o4(EIGHTH, F).o4(G).o4(QUARTER, A.s()).o4(A).o4(C).o4(D.s()).o4(F)
                // bar 19
                .bar().o4(G).o4(F).o4(G).o4(C).o4(C).o4(D.s()).o4(F).o4(EIGHTH, G).o4(A.s()).o4(A).o4(A.s()).o4(EIGHTH, A).o4(F).ending()
                // bar 20
                .bar().o4(G).o4(F).o4(G).o4(C).o4(C).o4(D.s()).o4(F).o4(EIGHTH, G).o4(A.s()).o4(A).o4(A.s()).o5(EIGHTH, C).o5(D).ending()
                // bar 21 = bar 17
                .bar().o3(QUARTER, F).o3(G).o3(EIGHTH.dot(), A).o3(QUARTER, A.s()).o4(C).o4(EIGHTH.dot(), D)
                // bar 22
                .bar().o4(D.s()).o4(D.s()).o4(D.s()).r().o4(D.s()).o4(EIGHTH, F).o4(G).o4(QUARTER, A.s()).o4(QUARTER, A)
                .build(attacca());
    }

    // ── Theme D (bars 23-30): driving repeated notes ─────────────
    private MelodicPhrase m1ThemeD() {
        return b()
                // bar 23: G pattern
                .bar().o4(G).o4(G).o4(G).o4(G).r(EIGHTH).o4(G).o4(G).o4(G).o4(G).r(EIGHTH).o4(G).o4(EIGHTH.dot(), F)
                // bar 24: D# pattern
                .bar().o4(D.s()).o4(D.s()).o4(D.s()).o4(D.s()).r(EIGHTH).o4(D.s()).o4(D.s()).o4(D.s()).o4(D.s()).r(EIGHTH).o4(D.s()).o4(EIGHTH.dot(), F)
                // bar 25: G pattern
                .bar().o4(G).o4(G).o4(G).o4(G).r(EIGHTH).o4(G).o4(G).o4(G).o4(G).r(EIGHTH).o4(G).o4(EIGHTH.dot(), F)
                // bar 26: D# ascending
                .bar().o4(D.s()).o4(D.s()).o4(D.s()).o4(D.s()).r(EIGHTH.dot()).o4(D.s()).o4(F).o4(G).o4(A).o4(A.s()).o4(A).o4(EIGHTH.dot(), F)
                // bar 27: A# pattern
                .bar().o4(A.s()).o4(A.s()).o4(A.s()).o4(A.s()).r(EIGHTH).o4(A.s()).o4(A.s()).o4(A.s()).o4(A.s()).r(EIGHTH).o4(A.s()).o4(EIGHTH.dot(), A)
                // bar 28: G pattern
                .bar().o4(G).o4(G).o4(G).o4(G).r(EIGHTH).o4(G).o4(G).o4(G).o4(G).r(EIGHTH).o4(G).o4(EIGHTH.dot(), A)
                // bar 29: A# pattern
                .bar().o4(A.s()).o4(A.s()).o4(A.s()).o4(A.s()).r(EIGHTH).o4(A.s()).o4(A.s()).o4(A.s()).o4(A.s()).r(EIGHTH).o4(A).o4(EIGHTH.dot(), A.s())
                // bar 30: C pattern → connects to turnaround
                .bar().o5(C).o5(C).o5(C).o5(C).r(EIGHTH).o5(C).o5(C).o5(C).o5(C).r(EIGHTH).o3(G).o4(C).o4(D).ending()
                .build(attacca());
    }

    // ── Turnaround (bars 31-32) ──────────────────────────────────
    private MelodicPhrase m1Turnaround() {
        return b()
                // bar 31
                .bar().o4(D.s()).o4(D).o4(D.s()).o3(EIGHTH, G).o3(G).o4(C).o4(D).o4(D.s()).o4(D).o4(D.s()).o4(EIGHTH, G).o4(G).o4(F).o4(G).ending()
                // bar 32
                .bar().o4(D.s()).o4(D).o4(D.s()).o3(EIGHTH, G).o3(G).o4(C).o4(D).o4(D.s()).r(EIGHTH.dot()).o4(F).o4(EIGHTH.dot(), G)
                .build(attacca());
    }

    // ── Ending (bars 64-68): theme A fading out ──────────────────
    private MelodicPhrase m1Ending() {
        return b()
                .bar().o4(HALF, C).r(EIGHTH.dot()).o4(G).o4(F).o4(EIGHTH, G).o4(G.s())
                .bar().o4(WHOLE, A.s())
                .bar().o4(HALF, C).r(EIGHTH.dot()).o4(G).o4(F).o4(EIGHTH, G).o4(A)
                .bar().o4(WHOLE, D.s())
                .bar().o4(HALF, C).ending()
                .build(end());
    }

    // ════════════════════════════════════════════════════════════════
    //  MELODY 2 — harmony lead (SYNTH_LEAD_SAWTOOTH)
    // ════════════════════════════════════════════════════════════════

    private Track melody2() {
        return Track.of("Melody 2", SYNTH_LEAD_SAWTOOTH, List.of(
                m2Intro(),
                m2ThemeA(), m2ThemeB(), m2Transition(), m2Bridge(),
                m2ThemeC(), m2ThemeD(), m2Turnaround(),
                m2ThemeA(), m2ThemeB(), m2Transition(), m2Bridge(),
                m2ThemeC(), m2ThemeD(), m2Turnaround(),
                m2Ending()));
    }

    private MelodicPhrase m2Intro() {
        return b()
                .bar().o5(F).o5(D.s()).o5(C).o4(A.s()).o5(C).o4(A.s()).o4(G.s()).o4(G).o4(G.s()).o4(G).o4(F).o4(D.s()).o4(F).o3(A.s()).o4(C).o4(D.s())
                .build(attacca());
    }

    private MelodicPhrase m2ThemeA() {
        return b()
                // bar 2: F held, pickup → D
                .bar().o4(HALF, F).r(EIGHTH.dot()).o5(C).o4(A.s()).o5(EIGHTH, C).o5(D)
                // bar 3: D# whole
                .bar().o5(WHOLE, D.s())
                // bar 4: F held, pickup → D
                .bar().o4(HALF, F).r(EIGHTH.dot()).o5(C).o4(A.s()).o5(EIGHTH, C).o5(D)
                // bar 5: G# whole
                .bar().o4(WHOLE, G.s())
                // bar 6 = bar 2
                .bar().o4(HALF, F).r(EIGHTH.dot()).o5(C).o4(A.s()).o5(EIGHTH, C).o5(D)
                // bar 7: D# whole
                .bar().o5(WHOLE, D.s())
                // bar 8 = bar 4
                .bar().o4(HALF, F).r(EIGHTH.dot()).o5(C).o4(A.s()).o5(EIGHTH, C).o5(D)
                // bar 9: G# whole
                .bar().o4(WHOLE, G.s())
                .build(attacca());
    }

    private MelodicPhrase m2ThemeB() {
        return b()
                // bar 10
                .bar().o5(C).r().o5(EIGHTH.dot(), C).o5(EIGHTH.dot(), D).o5(D.s()).o5(D.s()).ending()
                // bar 11
                .bar().o5(D).r().o5(EIGHTH.dot(), D).o5(EIGHTH.dot(), D.s()).o5(F).o5(F).o5(D.s()).o5(EIGHTH.dot(), F).ending()
                // bar 12
                .bar().o5(C).r().o5(EIGHTH.dot(), C).o5(EIGHTH.dot(), D).o5(D.s()).o5(D.s()).ending()
                // bar 13
                .bar().o5(D).r().o5(EIGHTH.dot(), D).o5(EIGHTH.dot(), D.s()).o5(QUARTER, F).o5(QUARTER, A.s())
                .build(attacca());
    }

    private MelodicPhrase m2Transition() {
        return b()
                .bar().o5(G).o5(F).o6(D).o6(C).o5(A.s()).o5(A).o5(G).o5(F).o5(G).o5(F).o5(D).o5(C).o5(D).o5(C).o4(A.s()).o4(A)
                .build(attacca());
    }

    private MelodicPhrase m2Bridge() {
        return b()
                // bar 15
                .bar().o4(EIGHTH.dot(), C).r().o4(C).o4(EIGHTH, D).o4(D.s()).o4(G).o4(F).o4(D.s()).o4(EIGHTH, D).o3(A.s()).ending()
                // bar 16: C whole
                .bar().o4(WHOLE, C)
                .build(attacca());
    }

    private MelodicPhrase m2ThemeC() {
        return b()
                // bar 17
                .bar().o3(QUARTER, A).o3(A.s()).o4(EIGHTH.dot(), C).o4(QUARTER, D).o4(D.s()).o4(EIGHTH.dot(), F)
                // bar 18
                .bar().o4(QUARTER, G).o4(A).o4(EIGHTH, A.s()).o5(C).o5(QUARTER, D).o5(C).o4(C).o4(D.s()).o4(F)
                // bar 19
                .bar().o4(G).o4(F).o4(G).o4(C).o4(C).o4(D.s()).o4(F).o4(EIGHTH, G).o4(A.s()).o4(A).o4(A.s()).o4(EIGHTH, A).o4(F).ending()
                // bar 20: G whole
                .bar().o4(WHOLE, G)
                // bar 21 = bar 17
                .bar().o3(QUARTER, A).o3(A.s()).o4(EIGHTH.dot(), C).o4(QUARTER, D).o4(D.s()).o4(EIGHTH.dot(), F)
                // bar 22
                .bar().o4(G).o4(G).o4(G).r().o4(G).o4(EIGHTH, A).o4(A.s()).o5(QUARTER, D).o5(QUARTER, C)
                .build(attacca());
    }

    private MelodicPhrase m2ThemeD() {
        return b()
                // bar 23: A# pattern
                .bar().o4(A.s()).o4(A.s()).o4(A.s()).o4(A.s()).r(EIGHTH).o4(A.s()).o4(A.s()).o4(A.s()).o4(A.s()).r(EIGHTH).o4(A.s()).o4(EIGHTH.dot(), A)
                // bar 24: G pattern
                .bar().o4(G).o4(G).o4(G).o4(G).r(EIGHTH).o4(G).o4(G).o4(G).o4(G).r(EIGHTH).o4(G).o4(EIGHTH.dot(), A)
                // bar 25: A# pattern
                .bar().o4(A.s()).o4(A.s()).o4(A.s()).o4(A.s()).r(EIGHTH).o4(A.s()).o4(A.s()).o4(A.s()).o4(A.s()).r(EIGHTH).o4(A.s()).o4(EIGHTH.dot(), A)
                // bar 26: G ascending
                .bar().o4(G).o4(G).o4(G).o4(G).r(EIGHTH.dot()).o4(G).o4(A).o4(A.s()).o5(C).o5(D).o5(C).o4(EIGHTH.dot(), A)
                // bar 27: D pattern
                .bar().o5(D).o5(D).o5(D).o5(D).r(EIGHTH).o5(D).o5(D).o5(D).o5(D).r(EIGHTH).o5(D).o5(EIGHTH.dot(), C)
                // bar 28: A# pattern
                .bar().o4(A.s()).o4(A.s()).o4(A.s()).o4(A.s()).r(EIGHTH).o4(A.s()).o4(A.s()).o4(A.s()).o4(A.s()).r(EIGHTH).o4(A.s()).o5(EIGHTH.dot(), C)
                // bar 29: D pattern
                .bar().o5(D).o5(D).o5(D).o5(D).r(EIGHTH).o5(D).o5(D).o5(D).o5(D).r(EIGHTH).o5(D).o5(EIGHTH.dot(), D.s())
                // bar 30: F pattern
                .bar().o5(F).o5(F).o5(F).o5(F).r(EIGHTH).o5(F).o5(F).o5(F).o5(F).r(EIGHTH).o4(C).o4(D.s()).o4(F).ending()
                .build(attacca());
    }

    private MelodicPhrase m2Turnaround() {
        return b()
                // bar 31
                .bar().o4(G).o4(F).o4(G).o4(EIGHTH, C).o4(C).o4(D.s()).o4(F).o4(G).o4(F).o4(G).o4(EIGHTH, A.s()).o4(A.s()).o4(A).o4(A.s()).ending()
                // bar 32
                .bar().o4(G).o4(F).o4(G).o4(EIGHTH, C).o4(C).o4(D.s()).o4(F).o4(G).r(EIGHTH.dot()).o4(A).o4(EIGHTH.dot(), A.s())
                .build(attacca());
    }

    private MelodicPhrase m2Ending() {
        return b()
                .bar().o4(HALF, F).r(EIGHTH.dot()).o5(C).o4(A.s()).o5(EIGHTH, C).o5(D)
                .bar().o5(WHOLE, D.s())
                .bar().o4(HALF, F).r(EIGHTH.dot()).o5(C).o4(A.s()).o5(EIGHTH, C).o5(D)
                .bar().o4(WHOLE, G.s())
                .bar().o4(HALF, F).ending()
                .build(end());
    }

    // ════════════════════════════════════════════════════════════════
    //  BASS (SLAP_BASS)
    // ════════════════════════════════════════════════════════════════

    private Track bass() {
        return Track.of("Bass", SLAP_BASS, List.of(
                bassIntro(),
                bassThemeA(), bassThemeB(), bassTransition(), bassBridge(),
                bassThemeC(), bassThemeD(), bassTurnaround(),
                bassThemeA(), bassThemeB(), bassTransition(), bassBridge(),
                bassThemeC(), bassThemeD(), bassTurnaround(),
                bassEnding()));
    }

    private MelodicPhrase bassIntro() {
        return b()
                .bar().o3(F).o3(D.s()).o3(C).o2(A.s()).o3(C).o2(A.s()).o2(G.s()).o2(G).o2(G.s()).o2(G).o2(F).o2(D.s()).o2(F).o1(A.s()).o2(C).o2(D.s())
                .build(attacca());
    }

    // Bass theme A: driving eighth-note bass pattern (alternating o1/o2)
    private MelodicPhrase bassThemeA() {
        return b()
                // bar 2: Cm riff
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o1(A.s()).o2(C).o2(D.s()).o2(D.s()).o2(D).o2(D.s()).ending()
                // bar 3: Cm riff variant
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o2(D.s()).o2(C).o2(D.s()).o2(D).r(EIGHTH.dot()).o1(A.s())
                // bar 4 = bar 2
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o1(A.s()).o2(C).o2(D.s()).o2(D.s()).o2(D).o2(D.s()).ending()
                // bar 5 = bar 3
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o2(D.s()).o2(C).o2(D.s()).o2(D).r(EIGHTH.dot()).o1(A.s())
                // bar 6 = bar 2
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o1(A.s()).o2(C).o2(D.s()).o2(D.s()).o2(D).o2(D.s()).ending()
                // bar 7 = bar 3
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o2(D.s()).o2(C).o2(D.s()).o2(D).r(EIGHTH.dot()).o1(A.s())
                // bar 8 = bar 2
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o1(A.s()).o2(C).o2(D.s()).o2(D.s()).o2(D).o2(D.s()).ending()
                // bar 9: variant ending
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o1(A.s()).o2(C).o2(D.s()).o2(D.s()).o2(F).o2(G).ending()
                .build(attacca());
    }

    private MelodicPhrase bassThemeB() {
        return b()
                // bar 10
                .bar().o2(C).r().o2(C).r().o2(D).r(EIGHTH).o2(D.s()).o2(C).o2(F).o2(G).o2(A.s()).o3(C).ending()
                // bar 11
                .bar().o2(D).r().o2(D).r().o2(D.s()).r(EIGHTH).o2(F).o2(F).o2(D.s()).o2(EIGHTH.dot(), F).ending()
                // bar 12
                .bar().o2(C).r().o2(C).r().o2(D).r(EIGHTH).o2(D.s()).o2(C).o2(F).o2(G).o2(A.s()).o3(C).ending()
                // bar 13
                .bar().o2(D).r().o2(D).r().o2(D.s()).r(EIGHTH.dot()).o2(QUARTER, F).o2(QUARTER, A.s())
                .build(attacca());
    }

    private MelodicPhrase bassTransition() {
        return b()
                .bar().o3(G).o3(F).o4(D).o4(C).o3(A.s()).o3(A).o3(G).o3(F).o3(G).o3(F).o3(D).o3(C).o3(D).o3(C).o2(A.s()).o2(A)
                .build(attacca());
    }

    private MelodicPhrase bassBridge() {
        return b()
                // bar 15
                .bar().o2(C).r().o2(C).r().o2(C).o2(D).o2(D.s()).o2(C).o2(D).o2(D.s()).o1(G).o1(A.s()).o1(B).ending()
                // bar 16
                .bar().o2(C).o2(C).o2(C).r().o2(C).o2(D).r(EIGHTH).o2(D.s()).r(EIGHTH).o2(C).o2(F).o2(EIGHTH.dot(), G)
                .build(attacca());
    }

    private MelodicPhrase bassThemeC() {
        return b()
                // bar 17
                .bar().o2(F).r().o2(F).o2(F).o2(F).r(EIGHTH).o2(F).o2(F).o1(A.s()).o1(A.s()).o1(A.s()).o1(A.s()).o1(A.s()).o1(A.s()).ending()
                // bar 18
                .bar().o2(D.s()).r().o2(D.s()).o2(D.s()).o2(D.s()).r(EIGHTH).o2(D.s()).o2(D.s()).o1(G).o1(G).o1(G).o1(F).o1(G).o1(A).o1(A.s()).ending()
                // bar 19
                .bar().o2(C).r().o2(C).o2(C).o2(C).r(EIGHTH).o2(C).o2(C).o2(C).o2(C).o2(C).o2(C).o2(C).o2(C).ending()
                // bar 20
                .bar().o2(D.s()).r().o2(D.s()).o2(D.s()).o2(D.s()).r(EIGHTH).o2(D.s()).o2(D.s()).o2(D.s()).o2(D.s()).o2(D.s()).o2(D.s()).o2(D.s()).o2(D.s()).ending()
                // bar 21
                .bar().o2(F).r().o2(F).o2(F).o2(F).r(EIGHTH).o2(F).o2(F).o1(A.s()).o1(A.s()).o1(A.s()).o1(A.s()).o1(A.s()).o1(A.s()).ending()
                // bar 22
                .bar().o2(D.s()).o2(D.s()).o2(D.s()).r().o2(D.s()).o2(F).o2(D.s()).o2(G).r(QUARTER).o2(F).ending()
                .build(attacca());
    }

    private MelodicPhrase bassThemeD() {
        return b()
                // bar 23: G pattern
                .bar().o2(G).o2(G).o2(G).o2(G).r(EIGHTH).o2(G).o2(G).o2(G).o2(G).r(EIGHTH).o2(G).o2(F).ending()
                // bar 24: D# pattern
                .bar().o2(D.s()).o2(D.s()).o2(D.s()).o2(D.s()).r(EIGHTH).o2(D.s()).o2(D.s()).o2(D.s()).o2(D.s()).r(EIGHTH).o2(D.s()).o2(F).ending()
                // bar 25: G pattern
                .bar().o2(G).o2(G).o2(G).o2(G).r(EIGHTH).o2(G).o2(G).o2(G).o2(G).r(EIGHTH).o2(G).o2(F).ending()
                // bar 26: D# ascending
                .bar().o2(D.s()).o2(D.s()).o2(D.s()).o2(D.s()).r(EIGHTH).o2(D.s()).o2(F).o2(G).o2(A).o2(A.s()).o2(A).o2(F).ending()
                // bar 27: G pattern with walk
                .bar().o2(G).o2(G).o2(G).o2(G).r(EIGHTH).o2(G).o2(G).o2(G).o2(G).r(EIGHTH).o2(G).o2(F).o2(G).o2(A).ending()
                // bar 28: D# pattern
                .bar().o2(D.s()).o2(D.s()).o2(D.s()).o2(D.s()).r(EIGHTH).o2(D.s()).o2(D.s()).o2(D.s()).o2(D.s()).r(EIGHTH).o2(D.s()).o2(D.s()).o2(D.s()).o2(F).ending()
                // bar 29: G pattern
                .bar().o2(G).o2(G).o2(G).o2(G).r(EIGHTH).o2(G).o2(G).o2(G).o2(G).r(EIGHTH).o2(G).o2(D.s()).o2(F).o2(G).ending()
                // bar 30: F pattern
                .bar().o2(F).o2(F).o2(F).o2(F).r(EIGHTH).o2(F).o2(F).o2(F).o2(F).ending()
                .build(attacca());
    }

    private MelodicPhrase bassTurnaround() {
        return b()
                // bar 31: rest
                .bar().r(WHOLE)
                // bar 32: pickup
                .bar().r(HALF.dot()).r().o1(G).o1(A).o1(A.s())
                .build(attacca());
    }

    private MelodicPhrase bassEnding() {
        return b()
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o1(A.s()).o2(C).o2(D.s()).o2(D.s()).o2(D).o2(D.s()).ending()
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o2(D.s()).o2(C).o2(D.s()).o2(D).r(EIGHTH.dot()).o1(A.s())
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o1(A.s()).o2(C).o2(D.s()).o2(D.s()).o2(D).o2(D.s()).ending()
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).o2(C).o2(D.s()).o2(C).o2(D.s()).o2(D).r(EIGHTH.dot()).o1(A.s())
                .bar().o2(C).o1(A.s()).o2(C).o1(F).o1(F).o1(G.s()).o1(A.s()).ending()
                .build(end());
    }

    // ════════════════════════════════════════════════════════════════
    //  DRUMS (DRUM_KIT)
    // ════════════════════════════════════════════════════════════════

    private Track drums() {
        return Track.of("Drums", DRUM_KIT, List.of(
                drumsIntro(),
                drumsMain(), drumsMain(),
                drumsEnding()));
    }

    // ── Intro (1 bar): hi-hat buildup ────────────────────────────
    private DrumPhrase drumsIntro() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MF));
        // 12 hi-hats then snare-hihat alternation
        for (int i = 0; i < 12; i++) n.add(d(CLOSED_HI_HAT, SIXTEENTH));
        n.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        n.add(d(CLOSED_HI_HAT, SIXTEENTH));
        n.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        n.add(d(CLOSED_HI_HAT, SIXTEENTH));
        return new DrumPhrase(n, attacca());
    }

    // ── Main drum loop (31 bars) ─────────────────────────────────
    private DrumPhrase drumsMain() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.F));
        // 31 bars of driving NES drums
        for (int i = 0; i < 31; i++) contraBar(n);
        return new DrumPhrase(n, attacca());
    }

    // ── Ending (5 bars) ──────────────────────────────────────────
    private DrumPhrase drumsEnding() {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.F));
        for (int i = 0; i < 4; i++) contraBar(n);
        // final bar: half bar then silence
        n.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        n.add(d(CLOSED_HI_HAT, SIXTEENTH));
        n.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        n.add(d(CLOSED_HI_HAT, SIXTEENTH));
        n.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        n.add(d(CLOSED_HI_HAT, SIXTEENTH));
        n.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        n.add(d(CLOSED_HI_HAT, SIXTEENTH));
        n.add(new RestNode(HALF));
        return new DrumPhrase(n, end());
    }

    // ── Drum patterns ────────────────────────────────────────────

    /** Standard Contra drum bar: kick/hihat alternating 16ths with snare hits. */
    private static void contraBar(List<PhraseNode> out) {
        // Beat 1: kick-hat-kick-hat
        out.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        // Beat 2: kick-hat-kick-hat
        out.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        // Beat 3: snare-hat-kick-hat
        out.add(d(ACOUSTIC_SNARE, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        // Beat 4: kick-hat-kick-hat
        out.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
        out.add(d(ACOUSTIC_BASS_DRUM, SIXTEENTH));
        out.add(d(CLOSED_HI_HAT, SIXTEENTH));
    }

    public static void main(final String[] args) throws Exception {
        PlayPiece.play(new DefaultContraBase());
    }
}
