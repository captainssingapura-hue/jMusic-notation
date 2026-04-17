package music.notation.songs.folk.katyusha;

import music.notation.phrase.*;
import music.notation.structure.*;

import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

/**
 * Shared track content for Katyusha (Катюша) — Matvei Blanter (1938).
 *
 * <p>Key of D minor, 4/4 time. The melody is one verse (8 bars)
 * repeated for each verse. Call {@link #melody(int)} to get the
 * desired number of repetitions.</p>
 */
final class KatyushaTracks {

    static final KeySignature KEY = new KeySignature(D, Mode.MINOR);
    static final TimeSignature TS = new TimeSignature(4, 4);

    private KatyushaTracks() {}

    // ── Melody (one verse = 8 bars) ───────────────────────────────

    /**
     * Build one verse of the Katyusha melody.
     *
     * <p>Structure (8 bars, 4 lines):</p>
     * <ol>
     *   <li>Bars 1–2: "Расцветали яблони и груши" — dotted rhythm, ascending</li>
     *   <li>Bars 3–4: "Поплыли туманы над рекой" — dotted rhythm, higher reach</li>
     *   <li>Bars 5–6: "Выходила на берег Катюша" — even quarters, descending</li>
     *   <li>Bars 7–8: "На высокий берег на крутой" — dotted rhythm return, cadence</li>
     * </ol>
     */
    static MelodicPhrase buildVerse(StaffPhraseBuilder P, PhraseMarking ending) {
        return P
                // Line 1 (bars 1–2): A. B C. A | C B A B E
                .bar().o4(QUARTER.dot(), D).o4(EIGHTH, E).o5(QUARTER.dot(), F).o4(EIGHTH, D)
                .bar(QUARTER).o5(F).o4(EIGHTH,E).o4(EIGHTH,D).o4(E).o3(A)
                // Line 2 (bars 3–4): B. C D. B | D D B C A
                .bar().o4(QUARTER.dot(), E).o5(EIGHTH, F).o5(QUARTER.dot(), G).o4(EIGHTH, E)
                .bar(EIGHTH).o5(G).o5(G).o4(F).slur().o5(E).o4(QUARTER,D).r(QUARTER)
                // Line 3 (bars 5–6): E' A' G' A' G F' E' D.~ | ~E rest
                .bar(QUARTER).o5(A).o5(D).o5(C).o5(EIGHTH,D).o5(EIGHTH,C)
                    .aux(HALF).o4(F).o4(F.s())
                .bar(QUARTER).o4(B,G).o4(EIGHTH,A).o4(EIGHTH,G).o4(A).o4(D)
                // Line 4 (bars 7–8): A F D E C B E C B A
                .bar(EIGHTH).r(EIGHTH).o4(QUARTER,B).o4(G).o4(QUARTER.dot(),A).o4(F)
                .bar(EIGHTH).o4(E).o3(A).o4(F).o4(E).o4(QUARTER,D).r(QUARTER)
                .build(ending);
    }

    /**
     * Build a melody track with the given number of verses.
     *
     * @param verses number of verse repetitions (typically 2–4)
     */
    static Track melody(int verses) {
        var P = StaffPhraseBuilder.in(KEY, TS, EIGHTH);
        var phrases = new java.util.ArrayList<Phrase>();
        var auxPhrases = new java.util.ArrayList<Phrase>();

        for (int v = 0; v < verses; v++) {
            var ending = (v < verses - 1) ? attacca() : end();
            phrases.add(buildVerse(P, ending));
            auxPhrases.addAll(P.auxPhrases());
        }

        if (auxPhrases.isEmpty()) {
            return Track.of("Melody", ACCORDION, phrases);
        }
        var auxTracks = List.of(Track.of("Melody Aux", ACCORDION, auxPhrases));
        return new Track("Melody", ACCORDION, phrases, auxTracks);
    }

    // ── Bass line (one verse) ─────────────────────────────────────

    static MelodicPhrase buildBassVerse(StaffPhraseBuilder P, PhraseMarking ending) {
        return P
                // Dm              | Dm → A
                .bar().o3(D).o3(A).o3(D).o3(A)
                .bar().o3(D).o3(A).o2(A).o3(E)
                // Gm              | Gm → Dm
                .bar().o3(G).o3(D).o3(G).o3(D)
                .bar().o3(G).o3(D).o3(D).o3(A)
                // Bb              | Gm → Dm
                .bar().o2(B).o3(F).o2(B).o3(F)
                .bar().o3(G).o3(D).o3(D).o3(A)
                // Gm → Dm         | A → Dm
                .bar().o3(G).o3(D).o3(D).o3(A)
                .bar().o2(A).o3(E).o2(HALF, D)
                .build(ending);
    }

    static Track bass(int verses) {
        var P = StaffPhraseBuilder.in(KEY, TS, QUARTER);
        var phrases = new java.util.ArrayList<Phrase>();
        for (int v = 0; v < verses; v++) {
            phrases.add(buildBassVerse(P, (v < verses - 1) ? attacca() : end()));
        }
        return Track.of("Bass", ACOUSTIC_BASS, phrases);
    }

    // ── Chord accompaniment ───────────────────────────────────────

    static MelodicPhrase buildChordVerse(StaffPhraseBuilder P, PhraseMarking ending) {
        // Dm=D,F,A  Gm=G,Bb,D  A=A,C#,E  Bb=Bb,D,F  C=C,E,G
        // Key sig provides Bb automatically
        return P
                // Dm | Dm → A
                .bar().o4(D, F, A).o4(D, F, A)
                .bar().o4(D, F, A).o4(A, C.s().higher(1), E.higher(1))
                // Gm | Gm → Dm
                .bar().o4(G, B, D.higher(1)).o4(G, B, D.higher(1))
                .bar().o4(G, B, D.higher(1)).o4(D, F, A)
                // Bb | Gm → Dm
                .bar().o3(B, D.higher(1), F.higher(1)).o3(B, D.higher(1), F.higher(1))
                .bar().o4(G, B, D.higher(1)).o4(D, F, A)
                // Gm → Dm | A → Dm
                .bar().o4(G, B, D.higher(1)).o4(D, F, A)
                .bar().o4(A, C.s().higher(1), E.higher(1)).o4(D, F, A)
                .build(ending);
    }

    static Track chords(int verses) {
        var P = StaffPhraseBuilder.in(KEY, TS, HALF);
        var phrases = new java.util.ArrayList<Phrase>();
        for (int v = 0; v < verses; v++) {
            phrases.add(buildChordVerse(P, (v < verses - 1) ? attacca() : end()));
        }
        return Track.of("Chords", ACOUSTIC_GUITAR_NYLON, phrases);
    }
}
