package music.notation.songs;

import music.notation.chord.Chord;
import music.notation.duration.BaseValue;
import music.notation.duration.Duration;
import music.notation.event.ChordEvent;
import music.notation.event.Instrument;
import music.notation.event.Ornament;
import music.notation.event.PercussionSound;
import music.notation.phrase.*;
import music.notation.pitch.Accidental;
import music.notation.pitch.NoteName;
import music.notation.pitch.Pitch;
import music.notation.structure.MelodicTrack;

import java.util.ArrayList;
import java.util.List;

import static music.notation.phrase.PhraseConnection.*;

public final class PieceHelper {

    private PieceHelper() {}

    public static PitchNode n(NoteName name, int oct, BaseValue dur) {
        return PitchNode.of(Pitch.of(name, oct), Duration.of(dur));
    }

    public static PitchNode orn(NoteName name, int oct, BaseValue dur, Ornament o) {
        return PitchNode.ornamented(Pitch.of(name, oct), Duration.of(dur), o);
    }

    public static PitchNode nd(NoteName name, int oct, BaseValue dur) {
        return PitchNode.of(Pitch.of(name, oct), Duration.dotted(dur));
    }

    public static ChordEvent chord(BaseValue dur, Pitch... pitches) {
        return new ChordEvent(List.of(pitches), Duration.of(dur), List.of());
    }

    public static ChordEvent chord(BaseValue dur, Chord chord) {
        return new ChordEvent(chord.pitches(), Duration.of(dur), List.of());
    }

    public static ChordEvent dchord(BaseValue dur, Pitch... pitches) {
        return new ChordEvent(List.of(pitches), Duration.dotted(dur), List.of());
    }

    public static ChordEvent dchord(BaseValue dur, Chord chord) {
        return new ChordEvent(chord.pitches(), Duration.dotted(dur), List.of());
    }

    public static PercussionNote d(PercussionSound sound, BaseValue dur) {
        return new PercussionNote(sound, Duration.of(dur));
    }

    public static PitchNode ns(NoteName name, int oct, BaseValue dur) {
        return PitchNode.of(Pitch.of(name, Accidental.SHARP, oct), Duration.of(dur));
    }

    public static Pitch p(NoteName name, int oct) {
        return Pitch.of(name, oct);
    }

    public static Pitch ps(NoteName name, int oct) {
        return Pitch.of(name, Accidental.SHARP, oct);
    }

    public static PhraseMarking breath()  { return new PhraseMarking(BREATH, true); }
    public static PhraseMarking attacca() { return new PhraseMarking(ATTACCA, true); }
    public static PhraseMarking elision() { return new PhraseMarking(ELISION, true); }
    public static PhraseMarking end()     { return new PhraseMarking(CAESURA, true); }

    /**
     * Phase 4c.2 migration helper: flatten a list of melodic phrases
     * into a single {@link MelodicTrack} by extracting each phrase's
     * bars (resolving {@link LayeredPhrase} where present) and
     * concatenating them.
     *
     * <p><b>Lossy</b>: phrase markings (BREATH/CAESURA/ATTACCA/ELISION)
     * and voice overlays are dropped — the bar-list shape on
     * {@code MelodicTrack} doesn't carry them. Pickup bars and trailing
     * padding survive verbatim because they're encoded inside the
     * {@link Bar}s themselves.</p>
     *
     * <p>Use {@link #joinMelodicPhrases} instead when phrase boundaries
     * carry {@code ELISION} markings that should overlap pickups.</p>
     */
    public static MelodicTrack flattenMelodic(String name, Instrument inst,
                                              List<? extends Phrase> phrases) {
        var bars = new ArrayList<Bar>();
        for (Phrase phrase : phrases) {
            bars.addAll(toLeafBars(phrase, name, inst));
        }
        return new MelodicTrack(name, inst, BarPhrase.of(bars), List.of());
    }

    /**
     * Phase 4d.2: flatten a list of melodic phrases into a single
     * {@link MelodicTrack}, honouring per-phrase {@code ELISION} markings
     * via {@link JoinedPhrase}. Each phrase's marking decides the
     * {@link ConnectingMode} between it and the next phrase
     * ({@code ELISION} → {@link ConnectingMode#ELIDED}; everything else
     * → {@link ConnectingMode#ATTACCA}).
     *
     * <p>Result: the pickup bars that previously sat orphaned at section
     * boundaries get absorbed into the preceding phrase's trailing pad,
     * matching the old {@code PhraseInterpreter.applyBoundaryGap(ELISION)}
     * timing.</p>
     */
    public static MelodicTrack joinMelodicPhrases(String name, Instrument inst,
                                                  List<? extends Phrase> phrases) {
        if (phrases.isEmpty()) {
            return new MelodicTrack(name, inst, BarPhrase.of(), List.of());
        }

        // Left-fold pairwise: acc starts as LeafPhrase(phrase[0].bars()),
        // then for each subsequent phrase X join(modeBetween, acc, X).
        BarPhrase acc = BarPhrase.of(toLeafBars(phrases.get(0), name, inst));
        for (int i = 1; i < phrases.size(); i++) {
            ConnectingMode mode = modeAfter(phrases.get(i - 1));
            BarPhrase next = BarPhrase.of(toLeafBars(phrases.get(i), name, inst));
            acc = BarPhrase.join(mode, acc, next);
        }
        // Store the BarPhrase TREE directly — no eager .bars() flatten.
        // Track.bars() resolves it lazily on each call.
        return new MelodicTrack(name, inst, acc, List.of());
    }

    /** Extract bars from any supported phrase type. */
    private static List<Bar> toLeafBars(Phrase phrase, String name, Instrument inst) {
        MelodicPhrase mp = switch (phrase) {
            case MelodicPhrase melodic   -> melodic;
            case LayeredPhrase layered   -> layered.resolve();
            default -> throw new IllegalArgumentException(
                    "toLeafBars: unsupported phrase type "
                            + phrase.getClass().getSimpleName());
        };
        return mp.toMelodicTrack(name, inst).bars();
    }

    /** Connecting mode between {@code phrase} and the next based on its marking. */
    private static ConnectingMode modeAfter(Phrase phrase) {
        PhraseMarking marking = switch (phrase) {
            case MelodicPhrase mp -> mp.marking();
            case LayeredPhrase lp -> lp.marking();
            default -> null;
        };
        if (marking == null) return ConnectingMode.ATTACCA;
        return marking.connection() == PhraseConnection.ELISION
                ? ConnectingMode.ELIDED
                : ConnectingMode.ATTACCA;
    }
}
