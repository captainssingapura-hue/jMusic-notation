package music.notation.structure;

import music.notation.event.Instrument;
import music.notation.phrase.Bar;
import music.notation.phrase.DrumPhrase;
import music.notation.phrase.MelodicPhrase;
import music.notation.phrase.Phrase;
import music.notation.phrase.PhraseConnection;
import music.notation.phrase.PhraseMarking;
import music.notation.phrase.PhraseNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A complete musical piece — metadata, a flat list of tracks, and
 * (optionally) the piece-level track declarations that produced them.
 *
 * <p>Two ways to construct:</p>
 * <ul>
 *   <li><b>Flat</b> — {@code new Piece(title, composer, key, timeSig, tempo, tracks)}.
 *       Existing songs. {@link #trackDecls} is empty; all tracks are
 *       treated as music tracks.</li>
 *   <li><b>Sectional</b> — {@code new Piece(title, composer, key, timeSig,
 *       tempo, trackDecls, sections)}. Declares the track lineup via
 *       {@link TrackDecl}, supplies homogeneous {@link Section}s, and
 *       joins matching-named tracks across sections. {@link #trackDecls}
 *       is populated and enables downstream control-track awareness.</li>
 * </ul>
 *
 * <p>Downstream consumers (MIDI renderer, piano roll, tab display) use
 * {@link #isControlTrack(String)} to distinguish control tracks from
 * music tracks. For flat-constructed pieces the method always returns
 * {@code false} — backward-compatible with every existing song.</p>
 */
public record Piece(
        String title,
        String composer,
        KeySignature key,
        TimeSignature timeSig,
        Tempo tempo,
        List<Track> tracks,
        List<TrackDecl> trackDecls
) {
    public Piece {
        tracks = List.copyOf(tracks);
        trackDecls = List.copyOf(trackDecls);
    }

    /**
     * Flat constructor — backward-compatible with every existing song.
     * No track declarations; {@link #isControlTrack(String)} returns
     * {@code false} for all tracks.
     */
    public Piece(String title, String composer, KeySignature key, TimeSignature timeSig,
                 Tempo tempo, List<Track> tracks) {
        this(title, composer, key, timeSig, tempo, tracks, List.of());
    }

    /**
     * Sectional factory. Declares the piece's track lineup via
     * {@link TrackDecl} values, then assembles the piece by joining each
     * named track's content across sections in order.
     *
     * <p>Expressed as a static factory (not a constructor) because both
     * factory forms and the flat constructor collide on type-erasure —
     * two {@code List<...>} parameters erase to the same signature.</p>
     *
     * <p>Validates:</p>
     * <ul>
     *   <li>Every section's {@code tracks} key-set matches the declared
     *       track names exactly — no missing, no extra.</li>
     *   <li>Track names are unique within the piece.</li>
     * </ul>
     *
     * <p>{@link TrackDecl.ControlTrackDecl}-backed tracks resolve to
     * {@link Track}s with a placeholder instrument
     * ({@link Instrument#ACOUSTIC_GRAND_PIANO}); their control-track
     * status is preserved via {@link #trackDecls} and consulted by the
     * MIDI renderer / piano roll through {@link #isControlTrack(String)}.</p>
     */
    public static Piece ofSections(String title, String composer,
                                   KeySignature key, TimeSignature timeSig, Tempo tempo,
                                   List<TrackDecl> trackDecls, List<Section> sections) {
        return new Piece(title, composer, key, timeSig, tempo,
                         joinSections(trackDecls, sections), trackDecls);
    }

    // ── Control-track lookup ──────────────────────────────────────────

    /**
     * The {@link TrackDecl} for the named track, if this piece was
     * built via the sectional constructor. Empty for flat-constructed
     * pieces and for names that don't match any declaration.
     */
    public Optional<TrackDecl> declFor(String trackName) {
        for (TrackDecl decl : trackDecls) {
            if (decl.name().equals(trackName)) return Optional.of(decl);
        }
        return Optional.empty();
    }

    /**
     * True iff the named track was declared as a
     * {@link TrackDecl.ControlTrackDecl}. Always false for
     * flat-constructed pieces (no declarations to inspect).
     */
    public boolean isControlTrack(String trackName) {
        return declFor(trackName)
                .filter(d -> d instanceof TrackDecl.ControlTrackDecl)
                .isPresent();
    }

    // ── Internals ─────────────────────────────────────────────────────

    /**
     * Join named tracks across sections into a single flat
     * {@code List<Track>}, in declared {@code trackDecls} order.
     */
    private static List<Track> joinSections(List<TrackDecl> trackDecls, List<Section> sections) {
        validateTrackDeclsUnique(trackDecls);
        validateSectionsHomogeneous(trackDecls, sections);

        List<Track> resolved = new ArrayList<>(trackDecls.size());
        for (TrackDecl decl : trackDecls) {
            String name = decl.name();
            List<Phrase> combinedPhrases = new ArrayList<>();
            // Gather aux tracks by slot index across sections.
            List<List<Phrase>> auxPhrasesPerSlot = new ArrayList<>();

            for (Section section : sections) {
                SectionTrack st = section.tracks().get(name);
                combinedPhrases.addAll(st.phrases());
                while (auxPhrasesPerSlot.size() < st.auxTracks().size()) {
                    auxPhrasesPerSlot.add(new ArrayList<>());
                }
                for (int i = 0; i < st.auxTracks().size(); i++) {
                    auxPhrasesPerSlot.get(i).addAll(st.auxTracks().get(i).phrases());
                }
            }

            Instrument instrument = switch (decl) {
                case TrackDecl.MusicTrackDecl m -> m.defaultInstrument();
                case TrackDecl.ControlTrackDecl c -> Instrument.ACOUSTIC_GRAND_PIANO;
            };

            List<Track> resolvedAux = new ArrayList<>(auxPhrasesPerSlot.size());
            for (int i = 0; i < auxPhrasesPerSlot.size(); i++) {
                resolvedAux.add(new Track(
                        name + " Aux " + (i + 1),
                        instrument,
                        auxPhrasesPerSlot.get(i),
                        List.of()));
            }

            resolved.add(new Track(name, instrument, combinedPhrases, resolvedAux));
        }
        return resolved;
    }

    private static void validateTrackDeclsUnique(List<TrackDecl> trackDecls) {
        Set<String> seen = new HashSet<>();
        for (TrackDecl decl : trackDecls) {
            if (!seen.add(decl.name())) {
                throw new IllegalArgumentException(
                        "Duplicate track name in piece declaration: '" + decl.name() + "'");
            }
        }
    }

    private static void validateSectionsHomogeneous(List<TrackDecl> trackDecls, List<Section> sections) {
        Set<String> declaredNames = new LinkedHashSet<>();
        for (TrackDecl decl : trackDecls) {
            declaredNames.add(decl.name());
        }
        for (Section section : sections) {
            Set<String> actual = section.tracks().keySet();
            if (!actual.equals(declaredNames)) {
                Set<String> missing = new LinkedHashSet<>(declaredNames);
                missing.removeAll(actual);
                Set<String> extra = new LinkedHashSet<>(actual);
                extra.removeAll(declaredNames);
                throw new IllegalArgumentException(String.format(
                        "Section '%s' track set does not match piece declaration. Missing: %s. Extra: %s.",
                        section.name(), missing, extra));
            }
        }
    }

    // ── Phase 4c: factory accepting the new typed track shapes ────────

    /**
     * Phase 4c factory: build a {@link Piece} directly from the new
     * {@link MelodicTrack} and {@link DrumTrack} shapes. Internally
     * each new track is converted into a legacy {@link Track} (one
     * per new track, wrapping its bars in a single phrase) so the
     * resulting {@link Piece} fits the current type system unchanged.
     *
     * <p>Authoring-side benefits: no need to construct
     * {@link MelodicPhrase} / {@link DrumPhrase} explicitly, no
     * {@link PhraseMarking} ceremony, bars sit directly on the track.
     * Caveats matching {@link MelodicTrack#auxTracks()} semantics:
     * aux tracks are converted recursively. Phrase-boundary markings
     * (BREATH/CAESURA) are not represented by the new types and so
     * are rendered as {@code ATTACCA} (no inter-phrase gap) by the
     * resulting Piece — same as if the legacy phrases had been
     * authored with attacca markings.</p>
     *
     * <p>This factory is the migration on-ramp: songs that don't
     * depend on the lost markings can author with the new types
     * today; songs that do can stay on the legacy {@link Piece}
     * constructors until the markings get a new home in a later
     * phase.</p>
     */
    public static Piece ofTrackKinds(String title, String composer,
                                     KeySignature key, TimeSignature timeSig, Tempo tempo,
                                     List<MelodicTrack> melodicTracks,
                                     List<DrumTrack> drumTracks) {
        var legacyTracks = new ArrayList<Track>(melodicTracks.size() + drumTracks.size());
        for (MelodicTrack mt : melodicTracks) {
            legacyTracks.add(toLegacyTrack(mt));
        }
        for (DrumTrack dt : drumTracks) {
            legacyTracks.add(toLegacyTrack(dt));
        }
        return new Piece(title, composer, key, timeSig, tempo, legacyTracks);
    }

    private static final PhraseMarking ATTACCA_MARKING =
            new PhraseMarking(PhraseConnection.ATTACCA, false);

    private static Track toLegacyTrack(MelodicTrack mt) {
        var nodes = new ArrayList<PhraseNode>();
        for (Bar b : mt.bars()) {
            nodes.addAll(b.nodes());
        }
        var phrase = new MelodicPhrase(nodes, mt.bars(), ATTACCA_MARKING);
        var auxLegacy = new ArrayList<Track>(mt.auxTracks().size());
        for (MelodicTrack aux : mt.auxTracks()) {
            auxLegacy.add(toLegacyTrack(aux));
        }
        return new Track(mt.name(), mt.defaultInstrument(),
                List.of(phrase), auxLegacy);
    }

    private static Track toLegacyTrack(DrumTrack dt) {
        var nodes = new ArrayList<PhraseNode>();
        for (Bar b : dt.bars()) {
            nodes.addAll(b.nodes());
        }
        Phrase phrase = nodes.isEmpty()
                // Empty drum track: single attacca phrase carrying nothing.
                // DrumPhrase requires a non-empty node list, so we use
                // MelodicPhrase here as a backwards-compat carrier.
                ? new MelodicPhrase(List.of(new music.notation.phrase.PaddingNode(
                        music.notation.duration.Duration.ofSixtyFourths(0))),
                        dt.bars(), ATTACCA_MARKING)
                : new DrumPhrase(nodes, ATTACCA_MARKING);
        var auxLegacy = new ArrayList<Track>(dt.auxTracks().size());
        for (DrumTrack aux : dt.auxTracks()) {
            auxLegacy.add(toLegacyTrack(aux));
        }
        return new Track(dt.name(), Instrument.DRUM_KIT,
                List.of(phrase), auxLegacy);
    }
}
