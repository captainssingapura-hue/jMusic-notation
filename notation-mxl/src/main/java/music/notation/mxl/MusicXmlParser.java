package music.notation.mxl;

import music.notation.expressivity.Articulation;
import music.notation.expressivity.ArticulationChange;
import music.notation.expressivity.ArticulationControl;
import music.notation.expressivity.Articulations;
import music.notation.performance.ConcreteNote;
import music.notation.performance.DrumNote;
import music.notation.performance.InstrumentChange;
import music.notation.performance.InstrumentControl;
import music.notation.performance.Instrumentation;
import music.notation.performance.KeySignatureChange;
import music.notation.performance.KeySignatureTrack;
import music.notation.performance.TimeSignatureChange;
import music.notation.performance.TimeSignatureTrack;
import music.notation.expressivity.Direction;
import music.notation.expressivity.HairpinSpan;
import music.notation.expressivity.PedalChange;
import music.notation.expressivity.PedalControl;
import music.notation.expressivity.PedalState;
import music.notation.expressivity.Pedaling;
import music.notation.performance.Performance;
import music.notation.performance.PitchedNote;
import music.notation.performance.Score;
import music.notation.performance.TempoTrack;
import music.notation.performance.Track;
import music.notation.expressivity.TrackId;
import music.notation.performance.TrackKind;
import music.notation.expressivity.Velocities;
import music.notation.expressivity.VelocityChange;
import music.notation.expressivity.VelocityControl;
import music.notation.expressivity.Volume;
import music.notation.expressivity.VolumeChange;
import music.notation.expressivity.VolumeControl;
import music.notation.pitch.NoteName;
import music.notation.structure.KeySignature;
import music.notation.structure.Mode;
import music.notation.structure.TimeSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates a decompressed MusicXML document into the concrete-notes
 * {@link Performance} model plus the score's initial time/key signatures.
 *
 * <p>Pass 1 scope: notes (pitch + duration), chords ({@code <chord/>}),
 * ties (intrinsic on {@link PitchedNote}), tuplets (handled implicitly —
 * MusicXML's {@code <duration>} already carries the post-tuplet division
 * count), {@code <backup>}/{@code <forward>} for multi-voice parts,
 * voices/staves mapped to separate {@link Track}s, multi-tempo via
 * {@link TempoTimeline}, dynamics → {@link Volume} side-channel, and
 * per-note articulations + slurs → {@link Articulations}.</p>
 *
 * <p>Out of scope for Pass 1 (see {@code .docs/mxl-import-plan.md}):
 * mid-piece time/key changes, transposing instruments, unpitched
 * percussion, grace notes.</p>
 */
public final class MusicXmlParser {

    private static final Logger LOG = LoggerFactory.getLogger(MusicXmlParser.class);

    /** MusicXML default when {@code <time>} is absent. */
    public static final TimeSignature DEFAULT_TIME = new TimeSignature(4, 4);

    /** MusicXML default when {@code <key>} is absent (no sharps/flats, major). */
    public static final KeySignature DEFAULT_KEY =
            new KeySignature(NoteName.C, Mode.MAJOR);

    /** Fallback tempo when no {@code <sound tempo>} is present. */
    public static final int DEFAULT_BPM = 120;

    private MusicXmlParser() {}

    /**
     * Parse a decompressed MusicXML document.
     *
     * @param xml UTF-8 MusicXML (partwise) as a string
     * @return parser result holding the concrete-notes performance plus extracted meta
     */
    public static Result parse(String xml) {
        Document doc = parseXml(xml);
        Element root = doc.getDocumentElement();
        if (!"score-partwise".equals(root.getLocalName() != null ? root.getLocalName() : root.getTagName())) {
            throw new UnsupportedOperationException(
                    "only score-partwise is supported; got <" + root.getTagName() + ">");
        }

        ScoreMeta meta = extractScoreMeta(root);

        // Build playback schedule + structural sidecar from the first part.
        // Score convention: all parts share the same measure structure, so
        // the first part's repeat markings drive the schedule for every part.
        Element firstPart = firstChild(root, "part");
        RepeatExpander.Result repeatResult;
        if (firstPart == null) {
            repeatResult = new RepeatExpander.Result(RepeatStructure.empty(), List.of());
        } else {
            repeatResult = RepeatExpander.analyze(children(firstPart, "measure"));
        }
        List<Integer> schedule = repeatResult.schedule();

        TempoTimeline tempos = buildTempoTimeline(root, meta, schedule);

        // Pre-scan <part-list> for percussion instrument MIDI assignments
        // (used to resolve <unpitched> notes to GM percussion keys).
        Map<String, Map<String, Integer>> partInstruments = scanPartList(root);

        // Parallel pre-scan for melodic <midi-program> and static <volume>.
        // Drum-only parts (no <midi-program>) are absent from this map and
        // their tracks fall through to the synth default at concretization.
        Map<String, PartMidi> partMidi = scanPartMidi(root);

        Map<TrackKey, TrackBucket> buckets = new LinkedHashMap<>();
        List<DynamicsEvent> dynamicsEvents = new ArrayList<>();
        List<PedalEvent> pedalEvents = new ArrayList<>();
        List<WedgeEvent> wedgeEvents = new ArrayList<>();
        Map<String, Transpose> partTranspose = new LinkedHashMap<>();
        List<DrumNote> drumNotes = new ArrayList<>();
        // Score-wide signature changes. Multiple parts in a typical
        // MusicXML score declare the same time/key at the same measure,
        // so we dedup by tickMs using TreeMap's first-write-wins put.
        // The static initial values from {@code meta} seed the change at
        // tick 0; subsequent mid-piece changes appear later in the map.
        // Post ms→Duration: signature changes are anchored at musical
        // positions, keyed by Duration with a value-based comparator
        // (so 1/4 and 2/8 collide as the same position).
        java.util.Comparator<music.notation.duration.Duration> byPos =
                (a, b) -> a.compareDuration(b);
        java.util.TreeMap<music.notation.duration.Duration, TimeSignature> timeSigByAt =
                new java.util.TreeMap<>(byPos);
        java.util.TreeMap<music.notation.duration.Duration, KeySignature> keyByAt =
                new java.util.TreeMap<>(byPos);
        timeSigByAt.put(music.notation.duration.Duration.zero(), meta.timeSig);
        keyByAt.put(music.notation.duration.Duration.zero(), meta.key);

        for (Element partEl : children(root, "part")) {
            walkPart(partEl, tempos, schedule, buckets, dynamicsEvents, pedalEvents,
                    wedgeEvents,
                    partInstruments, partTranspose, drumNotes,
                    timeSigByAt, keyByAt);
        }

        return assemble(meta, tempos, buckets, dynamicsEvents, pedalEvents,
                wedgeEvents,
                repeatResult.structure(), partTranspose, drumNotes, partMidi,
                timeSigByAt, keyByAt);
    }

    /** Build the {@link Performance} + side-channels from the per-part walk results. */
    private static Result assemble(ScoreMeta meta, TempoTimeline tempos,
                                    Map<TrackKey, TrackBucket> buckets,
                                    List<DynamicsEvent> dynamicsEvents,
                                    List<PedalEvent> pedalEvents,
                                    List<WedgeEvent> wedgeEvents,
                                    RepeatStructure repeatStructure,
                                    Map<String, Transpose> partTranspose,
                                    List<DrumNote> drumNotes,
                                    Map<String, PartMidi> partMidi,
                                    java.util.TreeMap<music.notation.duration.Duration, TimeSignature> timeSigByAt,
                                    java.util.TreeMap<music.notation.duration.Duration, KeySignature> keyByAt) {
        // Pair wedge starts with stops by (partId, number) into HairpinSpans.
        // Stops that don't match an open start, and starts with no matching
        // stop, are logged and skipped. Sort by start position so the
        // matching algorithm is deterministic when nested numbers reuse
        // the same id.
        Map<String, List<HairpinSpan>> hairpinsByPart =
                pairWedgesIntoSpans(wedgeEvents, tempos);
        // Order tracks by descending average MIDI pitch so the highest-
        // sounding voice (typically the melody) lands on top in the UI's
        // pitch-roll lanes. Ties broken by document insertion order.
        List<java.util.Map.Entry<TrackKey, TrackBucket>> ordered =
                new ArrayList<>(buckets.entrySet());
        ordered.removeIf(e -> e.getValue().notes.isEmpty());
        ordered.sort(java.util.Comparator
                .comparingDouble((java.util.Map.Entry<TrackKey, TrackBucket> e)
                        -> averagePitch(e.getValue()))
                .reversed());

        List<Track> tracks = new ArrayList<>(ordered.size() + 1);
        Map<TrackId, InstrumentControl> instrumentMap = new LinkedHashMap<>();
        Map<TrackId, VolumeControl> volumeMap = new LinkedHashMap<>();
        Map<TrackId, VelocityControl> velocityMap = new LinkedHashMap<>();
        Map<TrackId, ArticulationControl> articMap = new LinkedHashMap<>();
        Map<TrackId, Transpose> transposeMap = new LinkedHashMap<>();
        Map<TrackId, PedalControl> pedalingMap = new LinkedHashMap<>();
        Map<TrackId, music.notation.expressivity.HairpinControl> hairpinsMap = new LinkedHashMap<>();

        for (var entry : ordered) {
            TrackKey key = entry.getKey();
            TrackBucket bucket = entry.getValue();
            tracks.add(new Track(bucket.id, TrackKind.PITCHED, bucket.notes));

            // <part-list><midi-instrument><midi-program> — sets the
            // per-track instrument so MXL imports of strings/brass/etc.
            // don't render as piano. MXL is 1-indexed; subtract 1 for GM.
            PartMidi pm = partMidi.get(key.partId);
            if (pm != null && pm.program != null) {
                int gm = clamp(pm.program - 1, 0, 127);
                instrumentMap.put(bucket.id,
                        new InstrumentControl(List.of(new InstrumentChange(
                                music.notation.duration.Duration.zero(), gm))));
            }

            List<VolumeChange> volChanges = new ArrayList<>();
            List<VelocityChange> velChanges = new ArrayList<>();
            for (DynamicsEvent ev : dynamicsEvents) {
                if (!ev.partId().equals(key.partId)) continue;
                // <staff> in a <direction> is *visual anchoring* (which
                // staff the marking is drawn beside) — NOT musical scope.
                // Dynamics apply to the whole part, so we fan out to
                // every track in the part regardless of the staff
                // attribute. (For genuinely staff-specific dynamics —
                // e.g., one hand loud, the other quiet — MusicXML offers
                // no clean encoding; conventional practice is that both
                // hands take the same dynamic and the engraver shows it
                // once between staves.)
                music.notation.duration.Duration at = tempos.divToDuration(ev.div());
                // Same authored dynamic drives both channel volume and
                // per-note attack velocity. The Loudness payload preserves
                // whether the source was symbolic (Named) or numeric (Raw)
                // so engravers can re-emit the glyph and the codec just
                // calls level() at the MIDI boundary. The codec floors
                // velocity to MIDI 1 on emit (NOTE_OFF-vel-0 is illegal).
                volChanges.add(new VolumeChange(at, ev.loudness()));
                velChanges.add(new VelocityChange(at, ev.loudness()));
            }
            // <part-list><midi-instrument><volume> — when no <dynamics>
            // events override, seed a single VolumeChange/VelocityChange at
            // t=0 so the part-default volume is honoured. Authored
            // dynamics always take precedence: when present, they're the
            // user's explicit shaping intent and the static <volume>
            // becomes redundant.
            if (volChanges.isEmpty() && pm != null && pm.volume != null) {
                // pm.volume is already a normalised level in [0.0, 1.0]
                // (see scanPartMidi).
                double level = pm.volume;
                volChanges.add(new VolumeChange(music.notation.duration.Duration.zero(), level));
                velChanges.add(new VelocityChange(music.notation.duration.Duration.zero(),
                        level));
            }
            if (!volChanges.isEmpty()) {
                volumeMap.put(bucket.id, new VolumeControl(volChanges));
            }
            if (!velChanges.isEmpty()) {
                velocityMap.put(bucket.id, new VelocityControl(velChanges));
            }

            if (!bucket.articChanges.isEmpty()) {
                articMap.put(bucket.id, new ArticulationControl(bucket.articChanges));
            }

            Transpose t = partTranspose.get(key.partId);
            if (t != null && !t.isIdentity()) {
                transposeMap.put(bucket.id, t);
            }

            // Pedal applies to the whole part (the damper affects every
            // string regardless of staff/voice), so every track of the
            // part receives the same pedal timeline.
            List<PedalChange> pedalChanges = new ArrayList<>();
            for (PedalEvent ev : pedalEvents) {
                if (!ev.partId().equals(key.partId)) continue;
                pedalChanges.add(new PedalChange(tempos.divToDuration(ev.div()), ev.state()));
            }
            if (!pedalChanges.isEmpty()) {
                pedalingMap.put(bucket.id, new PedalControl(pedalChanges));
            }

            // Hairpins are part-level (same rule as dynamics): every
            // track of the part shares the spans. Skipped when the part
            // has no hairpins.
            List<HairpinSpan> partSpans = hairpinsByPart.get(key.partId);
            if (partSpans != null && !partSpans.isEmpty()) {
                hairpinsMap.put(bucket.id,
                        new music.notation.expressivity.HairpinControl(partSpans));
            }
        }

        // Drums: collapse all parts' percussion into a single DRUM track
        // (Score allows at most one). When at least one drum hit landed,
        // append a Track at the end — Score's constructor canonicalises it.
        if (!drumNotes.isEmpty()) {
            List<ConcreteNote> drumConcrete = new ArrayList<>(drumNotes);
            tracks.add(new Track(new TrackId("Drums"), TrackKind.DRUM, drumConcrete));
        }

        Instrumentation instrumentation = instrumentMap.isEmpty()
                ? Instrumentation.empty()
                : new Instrumentation(instrumentMap);
        // Fold the score-wide signature changes into their tracks.
        // The track records canonicalise (sort + dedup adjacent same-value),
        // so duplicate first entries collapse harmlessly.
        List<TimeSignatureChange> timeSigChangeList = new ArrayList<>();
        for (var e : timeSigByAt.entrySet()) {
            timeSigChangeList.add(new TimeSignatureChange(e.getKey(), e.getValue()));
        }
        List<KeySignatureChange> keyChangeList = new ArrayList<>();
        for (var e : keyByAt.entrySet()) {
            keyChangeList.add(new KeySignatureChange(e.getKey(), e.getValue()));
        }

        Performance perf = new Performance(
                new Score(tracks),
                tempos.toTempoTrack(),
                instrumentation,
                new Volume(volumeMap),
                new Articulations(articMap),
                new Pedaling(pedalingMap),
                new Velocities(velocityMap),
                new music.notation.expressivity.Hairpins(hairpinsMap),
                music.notation.expressivity.Lyrics.empty(),
                new TimeSignatureTrack(timeSigChangeList),
                new KeySignatureTrack(keyChangeList));
        return new Result(perf, meta.timeSig, meta.key, repeatStructure,
                new Transpositions(transposeMap));
    }

    // ── Part-list pre-scan (percussion instrument resolution) ───────────

    /**
     * Scan {@code <part-list><score-part><midi-instrument><midi-unpitched>}
     * to build {@code partId → instrumentId → MIDI percussion key}.
     * MusicXML stores the GM percussion key as 1..128; MIDI value = N - 1.
     */
    private static Map<String, Map<String, Integer>> scanPartList(Element root) {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        Element partList = firstChild(root, "part-list");
        if (partList == null) return out;
        for (Element scorePart : children(partList, "score-part")) {
            String partId = scorePart.getAttribute("id");
            if (partId == null || partId.isBlank()) continue;
            Map<String, Integer> instrMap = new LinkedHashMap<>();
            for (Element mi : children(scorePart, "midi-instrument")) {
                String iid = mi.getAttribute("id");
                if (iid == null || iid.isBlank()) continue;
                String unpitched = textOf(firstChild(mi, "midi-unpitched"));
                if (unpitched == null || unpitched.isBlank()) continue;
                try {
                    int gm = Integer.parseInt(unpitched.trim());
                    int midi = clamp(gm - 1, 0, 127);
                    instrMap.put(iid, midi);
                } catch (NumberFormatException ignored) {
                    LOG.warn("ignored non-integer <midi-unpitched>: {}", unpitched);
                }
            }
            if (!instrMap.isEmpty()) out.put(partId, instrMap);
        }
        return out;
    }

    /**
     * Per-part MIDI instrument metadata captured from {@code <part-list>}.
     * Mirrors the small handful of {@code <midi-instrument>} fields we
     * actually consume: melodic program (GM 1..128 in MXL → 0..127 in
     * MIDI; we keep the MXL form here and convert at use site) and
     * static initial volume.
     *
     * <p>Drum-only parts may have only {@code <midi-unpitched>} — for
     * those, no entry is recorded in the {@code partMidi} map and the
     * tracks fall through to the synth default.</p>
     */
    /** {@code volume} is a synth-agnostic level in [0.0, 1.0], or null when absent. */
    private record PartMidi(Integer program, Double volume) {}

    /**
     * Map every {@code <part-list><score-part><midi-instrument id="…">
     * <midi-program>N</midi-program></midi-instrument>} into
     * {@code partId → instrumentId → GM-0-based program}. Used by the
     * note-walker to translate a per-note
     * {@code <instrument id="…"/>} reference into a runtime program
     * change — i.e. mid-piece instrument changes.
     *
     * <p>Mirrors {@link #scanPartList} (which builds the analogous map
     * for percussion <code>&lt;midi-unpitched&gt;</code>); both could
     * be folded into one pass, but the current pair stays explicit so
     * each is independently traceable.</p>
     */
    private static Map<String, Map<String, Integer>> scanPartInstrumentPrograms(Element root) {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        Element partList = firstChild(root, "part-list");
        if (partList == null) return out;
        for (Element scorePart : children(partList, "score-part")) {
            String partId = scorePart.getAttribute("id");
            if (partId == null || partId.isBlank()) continue;
            Map<String, Integer> instrMap = new LinkedHashMap<>();
            for (Element mi : children(scorePart, "midi-instrument")) {
                String iid = mi.getAttribute("id");
                if (iid == null || iid.isBlank()) continue;
                // Skip drum entries (already in the percussion map).
                if (firstChild(mi, "midi-unpitched") != null) continue;
                String prog = textOf(firstChild(mi, "midi-program"));
                if (prog == null || prog.isBlank()) continue;
                try {
                    int gm1 = Integer.parseInt(prog.trim());
                    // MusicXML <midi-program> is 1-indexed (1..128); GM is 0..127.
                    instrMap.put(iid, clamp(gm1 - 1, 0, 127));
                } catch (NumberFormatException ignored) {
                    LOG.warn("ignored non-integer <midi-program> for {}/{}: {}",
                            partId, iid, prog);
                }
            }
            if (!instrMap.isEmpty()) out.put(partId, instrMap);
        }
        return out;
    }

    /**
     * Scan {@code <part-list><score-part><midi-instrument>} for melodic
     * program + static volume. Multi-{@code <midi-instrument>} parts (rare,
     * e.g. one drum entry plus one melodic) take the first entry without
     * {@code <midi-unpitched>} as the part-level program.
     */
    private static Map<String, PartMidi> scanPartMidi(Element root) {
        Map<String, PartMidi> out = new LinkedHashMap<>();
        Element partList = firstChild(root, "part-list");
        if (partList == null) return out;
        for (Element scorePart : children(partList, "score-part")) {
            String partId = scorePart.getAttribute("id");
            if (partId == null || partId.isBlank()) continue;
            Integer program = null;
            Double volume = null;
            for (Element mi : children(scorePart, "midi-instrument")) {
                // Skip drum-only entries — those carry <midi-unpitched>
                // and are handled by scanPartList. A part can have both
                // a drum instrument and a melodic one; the melodic entry
                // is what we want here.
                if (firstChild(mi, "midi-unpitched") != null) continue;
                String prog = textOf(firstChild(mi, "midi-program"));
                if (prog != null && !prog.isBlank() && program == null) {
                    try {
                        program = Integer.parseInt(prog.trim());
                    } catch (NumberFormatException ignored) {
                        LOG.warn("ignored non-integer <midi-program>: {}", prog);
                    }
                }
                String vol = textOf(firstChild(mi, "volume"));
                if (vol != null && !vol.isBlank() && volume == null) {
                    try {
                        // <volume> is sometimes a fraction (0.0..1.0) or a
                        // percentage 0..100 in MusicXML 3.x. Normalise to a
                        // synth-agnostic level in [0.0, 1.0]; the codec maps
                        // level -> CC #7 at the MIDI boundary.
                        double parsed = Double.parseDouble(vol.trim());
                        double level = parsed <= 1.0 ? parsed : parsed / 100.0;
                        volume = Math.max(0.0, Math.min(1.0, level));
                    } catch (NumberFormatException ignored) {
                        LOG.warn("ignored non-numeric <volume>: {}", vol);
                    }
                }
            }
            if (program != null || volume != null) {
                out.put(partId, new PartMidi(program, volume));
            }
        }
        return out;
    }

    // ── Tempo timeline (pre-walk) ───────────────────────────────────────

    /**
     * Pre-scan the first part along the playback {@code schedule} to collect
     * every {@code <sound tempo>} change with its accumulated divisions-from-
     * piece-start. Tempo is a global property of the score, so the first
     * part is authoritative — subsequent parts share the same timeline.
     *
     * <p>Iterating the schedule (rather than raw measures) means tempo
     * changes inside a repeated section are emitted on every pass, at the
     * correct cumulative div, so playback respects them through the
     * expansion.</p>
     */
    private static TempoTimeline buildTempoTimeline(Element root, ScoreMeta meta,
                                                     List<Integer> schedule) {
        Element firstPart = firstChild(root, "part");
        if (firstPart == null) return TempoTimeline.constant(meta.divisions, meta.bpm);

        List<Element> measures = children(firstPart, "measure");
        List<TempoTimeline.TempoEvent> events = new ArrayList<>();
        long currentDiv = 0;
        for (int idx : schedule) {
            if (idx < 0 || idx >= measures.size()) continue;
            Element measure = measures.get(idx);
            for (Node n = measure.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) n;
                switch (localName(el)) {
                    case "note" -> {
                        if (firstChild(el, "chord") == null && firstChild(el, "grace") == null) {
                            currentDiv += readDuration(el);
                        }
                    }
                    case "backup"  -> currentDiv -= readDuration(el);
                    case "forward" -> currentDiv += readDuration(el);
                    case "direction" -> {
                        Integer bpm = soundTempoOf(el);
                        if (bpm != null) events.add(new TempoTimeline.TempoEvent(currentDiv, bpm));
                    }
                    case "sound" -> {
                        Integer bpm = parseTempoAttr(el);
                        if (bpm != null) events.add(new TempoTimeline.TempoEvent(currentDiv, bpm));
                    }
                    default -> { /* ignore */ }
                }
            }
        }
        return TempoTimeline.from(events, meta.divisions, meta.bpm);
    }

    private static Integer soundTempoOf(Element direction) {
        for (Element s : children(direction, "sound")) {
            Integer bpm = parseTempoAttr(s);
            if (bpm != null) return bpm;
        }
        return null;
    }

    private static Integer parseTempoAttr(Element sound) {
        String t = sound.getAttribute("tempo");
        if (t.isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(t));
        } catch (NumberFormatException ex) {
            LOG.warn("ignored non-numeric <sound tempo>: '{}'", t);
            return null;
        }
    }

    // ── Score-level meta extraction ─────────────────────────────────────

    private static ScoreMeta extractScoreMeta(Element root) {
        int divisions = 480;
        TimeSignature timeSig = DEFAULT_TIME;
        KeySignature key = DEFAULT_KEY;
        int bpm = DEFAULT_BPM;

        Element firstPart = firstChild(root, "part");
        if (firstPart == null) return new ScoreMeta(divisions, timeSig, key, bpm);

        Element firstMeasure = firstChild(firstPart, "measure");
        if (firstMeasure == null) return new ScoreMeta(divisions, timeSig, key, bpm);

        Element attributes = firstChild(firstMeasure, "attributes");
        if (attributes != null) {
            String divs = textOf(firstChild(attributes, "divisions"));
            if (divs != null) divisions = Integer.parseInt(divs.trim());

            Element timeEl = firstChild(attributes, "time");
            if (timeEl != null) {
                TimeSignature parsed = parseTimeSignature(timeEl);
                if (parsed != null) timeSig = parsed;
            }

            Element keyEl = firstChild(attributes, "key");
            if (keyEl != null) {
                key = parseKey(keyEl);
            }
        }

        Integer firstTempo = findFirstSoundTempo(firstMeasure);
        if (firstTempo != null) bpm = firstTempo;

        return new ScoreMeta(divisions, timeSig, key, bpm);
    }

    private static Integer findFirstSoundTempo(Element measure) {
        for (Element direction : children(measure, "direction")) {
            Element sound = firstChild(direction, "sound");
            if (sound != null) {
                Integer bpm = parseTempoAttr(sound);
                if (bpm != null) return bpm;
            }
        }
        Element soundDirect = firstChild(measure, "sound");
        if (soundDirect != null) {
            return parseTempoAttr(soundDirect);
        }
        return null;
    }

    /**
     * Parse a {@code <time>} element into a {@link TimeSignature}.
     * Returns {@code null} if the required {@code <beats>}/{@code <beat-type>}
     * children are missing or non-numeric — caller falls back to the
     * static default.
     */
    private static TimeSignature parseTimeSignature(Element timeEl) {
        String beatsTxt = textOf(firstChild(timeEl, "beats"));
        String beatTypeTxt = textOf(firstChild(timeEl, "beat-type"));
        if (beatsTxt == null || beatTypeTxt == null) return null;
        try {
            int beats = Integer.parseInt(beatsTxt.trim());
            int beatType = Integer.parseInt(beatTypeTxt.trim());
            return new TimeSignature(beats, beatType);
        } catch (NumberFormatException ex) {
            LOG.warn("ignored non-numeric <time>: beats='{}' beat-type='{}'", beatsTxt, beatTypeTxt);
            return null;
        }
    }

    private static KeySignature parseKey(Element keyEl) {
        String fifthsStr = textOf(firstChild(keyEl, "fifths"));
        String modeStr   = textOf(firstChild(keyEl, "mode"));
        Mode mode;
        if (modeStr == null || modeStr.isBlank()) {
            // Source didn't declare a mode — stay honest with Mode.NONE
            // rather than fabricating a major/minor label. Tonic still
            // resolves via the relative-major table (sounds correct;
            // labelling reflects what the source actually said).
            mode = Mode.NONE;
        } else {
            mode = modeStr.trim().equalsIgnoreCase("minor") ? Mode.MINOR : Mode.MAJOR;
        }
        if (fifthsStr == null) return new KeySignature(NoteName.C, mode);
        int fifths;
        try {
            fifths = Integer.parseInt(fifthsStr.trim());
        } catch (NumberFormatException ex) {
            LOG.warn("<fifths> is not an integer: {} — falling back to 0", fifthsStr);
            fifths = 0;
        }
        return KeyFromFifths.of(fifths, mode);
    }

    // ── Part walker ────────────────────────────────────────────────────

    private static void walkPart(Element partEl, TempoTimeline tempos,
                                  List<Integer> schedule,
                                  Map<TrackKey, TrackBucket> buckets,
                                  List<DynamicsEvent> dynamicsEvents,
                                  List<PedalEvent> pedalEvents,
                                  List<WedgeEvent> wedgeEvents,
                                  Map<String, Map<String, Integer>> partInstruments,
                                  Map<String, Transpose> partTranspose,
                                  List<DrumNote> drumNotes,
                                  java.util.TreeMap<music.notation.duration.Duration, TimeSignature> timeSigByAt,
                                  java.util.TreeMap<music.notation.duration.Duration, KeySignature> keyByAt) {
        String partId = partEl.getAttribute("id");
        PartCursor cursor = new PartCursor(tempos);
        Map<String, Integer> instrumentMap = partInstruments.getOrDefault(partId, Map.of());
        List<Element> measures = children(partEl, "measure");

        for (int idx : schedule) {
            if (idx < 0 || idx >= measures.size()) continue;
            walkMeasure(measures.get(idx), partId, cursor, buckets, dynamicsEvents,
                    pedalEvents, wedgeEvents, instrumentMap, partTranspose, drumNotes,
                    timeSigByAt, keyByAt);
        }

        // End-of-part: flush any orphan grace notes (graces with no
        // following main note). Emit them at the cursor's current ms so
        // they still play; better than silent loss.
        if (!cursor.pendingGraces.isEmpty()) {
            flushPendingGraces(cursor.divToDuration(cursor.cursorDiv), cursor,
                    partId, buckets, partTranspose);
        }

        // Per-part diagnostic summary: surface counts of skipped/dropped notes
        // so the user knows what was lost without cluttering the per-note log.
        if (cursor.graceNotesEmitted > 0) {
            LOG.info("Part {} — emitted {} grace note(s) as pre-beat acciaccaturas",
                    partId, cursor.graceNotesEmitted);
        }
        if (cursor.unmappedPercussionDropped > 0) {
            LOG.warn("Part {} — dropped {} unpitched note(s) due to missing instrument map",
                    partId, cursor.unmappedPercussionDropped);
        }
    }

    private static void walkMeasure(Element measureEl, String partId, PartCursor cursor,
                                     Map<TrackKey, TrackBucket> buckets,
                                     List<DynamicsEvent> dynamicsEvents,
                                     List<PedalEvent> pedalEvents,
                                     List<WedgeEvent> wedgeEvents,
                                     Map<String, Integer> instrumentMap,
                                     Map<String, Transpose> partTranspose,
                                     List<DrumNote> drumNotes,
                                     java.util.TreeMap<music.notation.duration.Duration, TimeSignature> timeSigByAt,
                                     java.util.TreeMap<music.notation.duration.Duration, KeySignature> keyByAt) {
        for (Node n = measureEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            switch (localName(el)) {
                case "attributes" -> applyAttributes(el, partId, cursor, partTranspose,
                        timeSigByAt, keyByAt);
                case "note"       -> emitNote(el, partId, cursor, buckets,
                                                instrumentMap, partTranspose, drumNotes);
                case "backup"     -> cursor.cursorDiv -= readDuration(el);
                case "forward"    -> cursor.cursorDiv += readDuration(el);
                case "direction"  -> handleDirection(el, partId, cursor,
                                                     dynamicsEvents, pedalEvents, wedgeEvents);
                default           -> { /* barline, print, top-level sound (already in tempo pre-pass), … */ }
            }
        }
    }

    /**
     * Extract dynamics + pedal events from a {@code <direction>} element.
     * One direction can carry both (e.g. a {@code <words>p</words>} alongside
     * a {@code <pedal type="start"/>}); we examine each independently.
     */
    private static void handleDirection(Element direction, String partId, PartCursor cursor,
                                         List<DynamicsEvent> events,
                                         List<PedalEvent> pedalEvents,
                                         List<WedgeEvent> wedgeEvents) {
        // Pedal: <direction-type><pedal type="start|stop|change|continue|discontinue"/>
        Element dt = firstChild(direction, "direction-type");
        if (dt != null) {
            Element pedal = firstChild(dt, "pedal");
            if (pedal != null) {
                PedalState state = mapPedalType(pedal.getAttribute("type"));
                if (state != null) {
                    pedalEvents.add(new PedalEvent(cursor.cursorDiv, partId, state));
                }
            }

            // Wedge: <direction-type><wedge type="crescendo|diminuendo|stop|continue" number="N"/>
            // Starts (crescendo/diminuendo) and stops are paired by `number`
            // in assemble() to form HairpinSpans. `continue` is a visual
            // continuation across system breaks — no state change.
            Element wedge = firstChild(dt, "wedge");
            if (wedge != null) {
                WedgeKind kind = mapWedgeType(wedge.getAttribute("type"));
                if (kind != null) {
                    Integer number = parseWedgeNumber(wedge.getAttribute("number"));
                    wedgeEvents.add(new WedgeEvent(cursor.cursorDiv, partId, number, kind));
                }
            }
        }

        // Dynamics — original handler logic preserved below.
        addDynamicsFromDirection(direction, partId, cursor, events);
    }

    /**
     * Pair wedge starts with their stops by {@code (partId, number)} into
     * {@link HairpinSpan}s. The MusicXML convention: a {@code crescendo}
     * or {@code diminuendo} starts a wedge identified by {@code number};
     * a later {@code stop} with the same number ends it. Walks events in
     * document order and maintains one open start per
     * {@code (partId, number)} key.
     *
     * <p>Unmatched events (a stop with no open start, or a start with no
     * later stop) are logged at warn-level and discarded — the model
     * stays consistent rather than carrying half-spans.</p>
     */
    private static Map<String, List<HairpinSpan>> pairWedgesIntoSpans(
            List<WedgeEvent> wedges, TempoTimeline tempos) {
        Map<String, List<HairpinSpan>> byPart = new LinkedHashMap<>();
        record OpenKey(String partId, Integer number) {}
        Map<OpenKey, WedgeEvent> open = new LinkedHashMap<>();
        for (WedgeEvent ev : wedges) {
            OpenKey k = new OpenKey(ev.partId(), ev.number());
            if (ev.kind() == WedgeKind.STOP) {
                WedgeEvent start = open.remove(k);
                if (start == null) {
                    LOG.warn("wedge stop with no open start: part={} number={} div={}",
                            ev.partId(), ev.number(), ev.div());
                    continue;
                }
                Direction direction = (start.kind() == WedgeKind.CRESCENDO)
                        ? Direction.CRESCENDO : Direction.DECRESCENDO;
                music.notation.duration.Duration from = tempos.divToDuration(start.div());
                music.notation.duration.Duration to   = tempos.divToDuration(ev.div());
                if (to.compareDuration(from) <= 0) {
                    LOG.warn("wedge span has non-positive length: part={} from={} to={} — skipped",
                            ev.partId(), from, to);
                    continue;
                }
                byPart.computeIfAbsent(ev.partId(), p -> new ArrayList<>())
                        .add(new HairpinSpan(from, to, direction));
            } else {
                WedgeEvent prior = open.put(k, ev);
                if (prior != null) {
                    LOG.warn("wedge start replacing un-stopped earlier start: part={} number={} prior-div={} new-div={}",
                            ev.partId(), ev.number(), prior.div(), ev.div());
                }
            }
        }
        for (var e : open.entrySet()) {
            LOG.warn("wedge start with no matching stop: part={} number={} div={} — skipped",
                    e.getKey().partId(), e.getKey().number(), e.getValue().div());
        }
        return byPart;
    }

    /** Map a MusicXML {@code <wedge type>} to {@link WedgeKind}, or null for visual-only. */
    private static WedgeKind mapWedgeType(String type) {
        if (type == null) return null;
        return switch (type) {
            case "crescendo"  -> WedgeKind.CRESCENDO;
            case "diminuendo" -> WedgeKind.DECRESCENDO;
            case "stop"       -> WedgeKind.STOP;
            // "continue" — visual continuation across system breaks, no
            // state change. Skip.
            default           -> null;
        };
    }

    /** Parse the {@code number} attribute on a {@code <wedge>}; defaults to 1 when missing/blank. */
    private static Integer parseWedgeNumber(String s) {
        if (s == null || s.isBlank()) return 1;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException ex) { return 1; }
    }

    /** Map a MusicXML {@code <pedal type>} attribute to {@link PedalState}, or {@code null} for visual-only. */
    private static PedalState mapPedalType(String type) {
        if (type == null) return null;
        return switch (type) {
            case "start"        -> PedalState.DOWN;
            case "stop"         -> PedalState.UP;
            case "discontinue"  -> PedalState.UP;
            case "change"       -> PedalState.CHANGE;
            // "continue" — visual continuation, no state change. Skip.
            default             -> null;
        };
    }

    /**
     * Extract a dynamics set-point from a {@code <direction>}. The
     * authored shape is preserved:
     * <ul>
     *   <li>{@code <direction-type><dynamics><f/></dynamics>} →
     *       {@link music.notation.expressivity.Loudness.Named} carrying
     *       {@link music.notation.event.Dynamic#F} — the glyph survives
     *       end-to-end and an engraver can re-emit it verbatim.</li>
     *   <li>{@code <sound dynamics="54.44"/>} (a numeric percentage)
     *       → {@link music.notation.expressivity.Loudness.Raw} with the
     *       level in {@code [0.0, 1.0]}. No symbol is fabricated.</li>
     * </ul>
     * Special markings ({@code sf}, {@code fp}, …) without a clean
     * named-Dynamic equivalent collapse to {@code Named(F)} — they're
     * forte-class accents and the codec treats them as such; future
     * work can introduce a distinct accent side-channel.
     */
    private static void addDynamicsFromDirection(Element direction, String partId,
                                                   PartCursor cursor,
                                                   List<DynamicsEvent> events) {
        Integer staff = childIntOrNull(direction, "staff");

        music.notation.expressivity.Loudness loudness = null;

        // Prefer the symbolic mark when present — it carries authorial intent.
        Element dt = firstChild(direction, "direction-type");
        if (dt != null) {
            Element dynEl = firstChild(dt, "dynamics");
            if (dynEl != null) {
                loudness = symbolicDynamic(dynEl);
            }
        }

        // Fall back to numeric <sound dynamics="..."> percentage.
        if (loudness == null) {
            Element sound = firstChild(direction, "sound");
            if (sound != null) {
                String dyn = sound.getAttribute("dynamics");
                if (!dyn.isBlank()) {
                    try {
                        // MusicXML <sound dynamics> is a percentage where
                        // 100 ≈ forte. Treat it as percent-of-max and clamp
                        // to a synth-agnostic level [0.0, 1.0].
                        double pct = Double.parseDouble(dyn);
                        double level = Math.max(0.0, Math.min(1.0, pct / 100.0));
                        loudness = music.notation.expressivity.Loudness.of(level);
                    } catch (NumberFormatException ex) {
                        LOG.warn("ignored non-numeric <sound dynamics>: '{}'", dyn);
                    }
                }
            }
        }

        if (loudness != null) {
            events.add(new DynamicsEvent(cursor.cursorDiv, partId, staff, loudness));
        }
    }

    /** First known symbolic-dynamic child of {@code <dynamics>} → {@link Loudness.Named}, else null. */
    private static music.notation.expressivity.Loudness symbolicDynamic(Element dynamicsEl) {
        for (Node n = dynamicsEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            music.notation.event.Dynamic mark = SYMBOLIC_DYNAMICS.get(localName((Element) n));
            if (mark != null) return music.notation.expressivity.Loudness.of(mark);
        }
        return null;
    }

    /**
     * MusicXML symbolic-dynamic tag → {@link music.notation.event.Dynamic} enum value.
     * Authored marks survive verbatim through the model. Special markings
     * (sf*, fp, …) collapse to {@link music.notation.event.Dynamic#F}
     * pending a dedicated accent side-channel.
     */
    private static final Map<String, music.notation.event.Dynamic> SYMBOLIC_DYNAMICS = Map.ofEntries(
            Map.entry("pppp", music.notation.event.Dynamic.PPPP),
            Map.entry("ppp",  music.notation.event.Dynamic.PPP),
            Map.entry("pp",   music.notation.event.Dynamic.PP),
            Map.entry("p",    music.notation.event.Dynamic.P),
            Map.entry("mp",   music.notation.event.Dynamic.MP),
            Map.entry("mf",   music.notation.event.Dynamic.MF),
            Map.entry("f",    music.notation.event.Dynamic.F),
            Map.entry("ff",   music.notation.event.Dynamic.FF),
            Map.entry("fff",  music.notation.event.Dynamic.FFF),
            Map.entry("ffff", music.notation.event.Dynamic.FFFF),
            Map.entry("sf",   music.notation.event.Dynamic.F),
            Map.entry("sfp",  music.notation.event.Dynamic.F),
            Map.entry("sfz",  music.notation.event.Dynamic.F),
            Map.entry("fp",   music.notation.event.Dynamic.F),
            Map.entry("fz",   music.notation.event.Dynamic.F),
            Map.entry("rf",   music.notation.event.Dynamic.F),
            Map.entry("rfz",  music.notation.event.Dynamic.F)
    );

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static Integer childIntOrNull(Element parent, String childName) {
        String s = textOf(firstChild(parent, childName));
        return (s == null || s.isBlank()) ? null : Integer.parseInt(s.trim());
    }

    private static void applyAttributes(Element attributes, String partId, PartCursor cursor,
                                         Map<String, Transpose> partTranspose,
                                         java.util.TreeMap<music.notation.duration.Duration, TimeSignature> timeSigByAt,
                                         java.util.TreeMap<music.notation.duration.Duration, KeySignature> keyByAt) {
        // <divisions> changes still warn — the TempoTimeline locks in
        // the first <divisions> for the part. <time> and <key>
        // changes are now captured into the score-wide signature
        // tracks instead of being dropped.
        if (firstChild(attributes, "divisions") != null) {
            if (cursor.seenInitialDivisions) {
                LOG.warn("mid-piece <divisions> change in part {} ignored "
                        + "(single-divisions limitation)", partId);
            } else {
                cursor.seenInitialDivisions = true;
            }
        }
        Element timeEl = firstChild(attributes, "time");
        if (timeEl != null) {
            TimeSignature ts = parseTimeSignature(timeEl);
            if (ts != null) {
                music.notation.duration.Duration at = cursor.divToDuration(cursor.cursorDiv);
                // Multiple parts in a typical score declare the same
                // signature at the same measure; first part wins.
                timeSigByAt.putIfAbsent(at, ts);
            }
            cursor.seenInitialTime = true;
        }
        Element keyEl = firstChild(attributes, "key");
        if (keyEl != null) {
            KeySignature ks = parseKey(keyEl);
            if (ks != null) {
                music.notation.duration.Duration at = cursor.divToDuration(cursor.cursorDiv);
                keyByAt.putIfAbsent(at, ks);
            }
            cursor.seenInitialKey = true;
        }
        Element transpose = firstChild(attributes, "transpose");
        if (transpose != null) {
            Transpose t = parseTranspose(transpose);
            Transpose existing = partTranspose.get(partId);
            if (existing == null) {
                partTranspose.put(partId, t);
            } else if (!existing.equals(t)) {
                LOG.warn("mid-piece <transpose> change in part {} ignored: {} -> {}",
                        partId, existing, t);
            }
        }
    }

    private static Transpose parseTranspose(Element el) {
        int chromatic = intText(el, "chromatic", 0);
        int octave    = intText(el, "octave-change", 0);
        int diatonic  = intText(el, "diatonic", 0);
        return new Transpose(chromatic, octave, diatonic);
    }

    private static long readDuration(Element el) {
        String s = textOf(firstChild(el, "duration"));
        return s == null ? 0 : Long.parseLong(s.trim());
    }

    private static void emitNote(Element noteEl, String partId, PartCursor cursor,
                                  Map<TrackKey, TrackBucket> buckets,
                                  Map<String, Integer> instrumentMap,
                                  Map<String, Transpose> partTranspose,
                                  List<DrumNote> drumNotes) {
        if (firstChild(noteEl, "grace") != null) {
            // Buffer the grace note. It will be emitted just before the
            // next non-grace note's onset, with a short fixed duration —
            // the standard pre-beat acciaccatura interpretation.
            bufferGraceNote(noteEl, cursor);
            return;
        }

        boolean isChord = firstChild(noteEl, "chord") != null;
        boolean isRest  = firstChild(noteEl, "rest") != null;
        boolean isUnpitched = firstChild(noteEl, "unpitched") != null;
        long durationDiv = readDuration(noteEl);

        long onsetDiv;
        if (isChord) {
            onsetDiv = cursor.prevOnsetDiv;
        } else {
            onsetDiv = cursor.cursorDiv;
            cursor.prevOnsetDiv = cursor.cursorDiv;
            cursor.cursorDiv += durationDiv;
        }

        music.notation.duration.Duration onsetAt = cursor.divToDuration(onsetDiv);

        // Flush any pending grace notes onto the lead-in of THIS note
        // (chord-members re-use the previous onset so we only flush on
        // the first member to avoid duplicate grace runs).
        if (!isChord && !cursor.pendingGraces.isEmpty()) {
            flushPendingGraces(onsetAt, cursor, partId, buckets, partTranspose);
        }

        if (isRest) return;

        // Note duration: end-div minus start-div, both translated to Duration.
        music.notation.duration.Duration endAt = cursor.divToDuration(onsetDiv + durationDiv);
        music.notation.duration.Duration noteDuration = endAt.minus(onsetAt);
        if (noteDuration.compareDuration(music.notation.duration.Duration.zero()) <= 0) {
            // Floor: a zero-duration note isn't legal in PitchedNote;
            // give it the smallest sensible Duration (a 128th note).
            noteDuration = music.notation.duration.Duration.of(1, 128);
        }

        if (isUnpitched) {
            emitDrumNote(noteEl, partId, onsetAt, noteDuration, instrumentMap, drumNotes, cursor);
            return;
        }

        Element pitch = firstChild(noteEl, "pitch");
        if (pitch == null) return;
        Spelling sp = spellingFromPitch(pitch);
        Transpose t = partTranspose.getOrDefault(partId, Transpose.NONE);
        // Preserve authored spelling when the part isn't transposing —
        // covers piano, voice, strings (non-transposing). For
        // transposing instruments (clarinets, horns, ...) the shifted
        // pitch has no canonical re-spelling without key-aware logic,
        // so fall back to RawMidi. A future key-aware speller can lift
        // RawMidi → Spelled when context is available.
        music.notation.performance.Pitch pitchValue;
        if (t.totalSemitones() == 0) {
            pitchValue = music.notation.performance.Pitch.of(sp.step, sp.alter, sp.octave);
        } else {
            int writtenMidi = (sp.octave + 1) * 12 + sp.step.semitoneFromC() + sp.alter;
            int shifted = clamp(writtenMidi + t.totalSemitones(), 0, 127);
            pitchValue = music.notation.performance.Pitch.of(shifted);
        }

        boolean tiedToNext = hasTie(noteEl, "start");
        int staff = intText(noteEl, "staff", 1);
        int voice = intText(noteEl, "voice", 1);

        TrackKey key = new TrackKey(partId, staff, voice);
        TrackBucket bucket = buckets.computeIfAbsent(key,
                k -> new TrackBucket(new TrackId(trackName(k))));
        bucket.notes.add(new PitchedNote(onsetAt, noteDuration, pitchValue, tiedToNext));

        if (!isChord) updateArticulationState(bucket, noteEl, onsetAt);
    }

    /**
     * Per-grace acciaccatura duration as a musical {@link
     * music.notation.duration.Duration}. A 32nd note — short enough to
     * read as ornamental, long enough to be audible. Three or four
     * stacked graces will collide with the main note; the flush logic
     * clamps the start to the available pre-beat window.
     */
    private static final music.notation.duration.Duration GRACE_PER_NOTE =
            music.notation.duration.Duration.of(1, 32);

    /**
     * Floor on emitted grace-note duration. A 128th note — protects
     * against zero-length notes when graces stack against a very early
     * main note.
     */
    private static final music.notation.duration.Duration GRACE_MIN_DURATION =
            music.notation.duration.Duration.of(1, 128);

    /**
     * Buffer a {@code <grace>} note into the cursor. Multiple consecutive
     * graces queue up; the next non-grace note triggers a flush that
     * places them as pre-beat acciaccaturas. Chord graces (marked with
     * {@code <chord/>} inside the grace block) are grouped so they
     * sound simultaneously rather than stacking sequentially.
     */
    private static void bufferGraceNote(Element noteEl, PartCursor cursor) {
        Element pitch = firstChild(noteEl, "pitch");
        if (pitch == null) return;       // tied / rest graces — ignore for now
        Spelling sp = spellingFromPitch(pitch);
        Element grace = firstChild(noteEl, "grace");
        // <grace slash="yes"/> marks an acciaccatura — visually crossed
        // through. We don't currently model the slash distinction in
        // playback (both flavours play as pre-beat) but record it on
        // the buffered entry so future renderers can differentiate.
        boolean accented = grace != null
                && "yes".equalsIgnoreCase(grace.getAttribute("slash"));
        int staff = intText(noteEl, "staff", 1);
        int voice = intText(noteEl, "voice", 1);
        boolean chordedWithPrev = firstChild(noteEl, "chord") != null;
        cursor.pendingGraces.add(new PendingGrace(
                sp, accented, staff, voice, chordedWithPrev));
    }

    /**
     * Emit the cursor's buffered graces as {@link PitchedNote}s just
     * before {@code mainOnsetAt}. Sequential graces stack pre-beat;
     * chord graces play simultaneously within a slot. Each emitted
     * note's length is {@link #GRACE_PER_NOTE} clamped against the
     * available pre-beat window.
     */
    private static void flushPendingGraces(music.notation.duration.Duration mainOnsetAt,
                                            PartCursor cursor,
                                            String partId,
                                            Map<TrackKey, TrackBucket> buckets,
                                            Map<String, Transpose> partTranspose) {
        if (cursor.pendingGraces.isEmpty()) return;

        // Group consecutive chord-graces (sound at the same slot).
        java.util.List<java.util.List<PendingGrace>> slots = new java.util.ArrayList<>();
        for (PendingGrace pg : cursor.pendingGraces) {
            if (pg.chordedWithPrev() && !slots.isEmpty()) {
                slots.get(slots.size() - 1).add(pg);
            } else {
                java.util.List<PendingGrace> slot = new java.util.ArrayList<>();
                slot.add(pg);
                slots.add(slot);
            }
        }

        int slotCount = slots.size();
        music.notation.duration.Duration totalLead = GRACE_PER_NOTE.times(slotCount);
        music.notation.duration.Duration startAt;
        if (mainOnsetAt.compareDuration(totalLead) >= 0) {
            startAt = mainOnsetAt.minus(totalLead);
        } else {
            startAt = music.notation.duration.Duration.zero();
        }
        music.notation.duration.Duration slotAt = startAt;

        Transpose t = partTranspose.getOrDefault(partId, Transpose.NONE);

        for (java.util.List<PendingGrace> slot : slots) {
            // Available window for this slot — never push past the main note.
            music.notation.duration.Duration slotEnd =
                    slotAt.plus(GRACE_PER_NOTE).compareDuration(mainOnsetAt) <= 0
                            ? slotAt.plus(GRACE_PER_NOTE) : mainOnsetAt;
            music.notation.duration.Duration slotDur = slotEnd.minus(slotAt);
            if (slotDur.compareDuration(GRACE_MIN_DURATION) < 0) slotDur = GRACE_MIN_DURATION;
            for (PendingGrace pg : slot) {
                music.notation.performance.Pitch pitchValue;
                if (t.totalSemitones() == 0) {
                    pitchValue = music.notation.performance.Pitch.of(
                            pg.spelling().step, pg.spelling().alter, pg.spelling().octave);
                } else {
                    int writtenMidi = (pg.spelling().octave + 1) * 12
                            + pg.spelling().step.semitoneFromC()
                            + pg.spelling().alter;
                    pitchValue = music.notation.performance.Pitch.of(
                            clamp(writtenMidi + t.totalSemitones(), 0, 127));
                }
                TrackKey key = new TrackKey(partId, pg.staff(), pg.voice());
                TrackBucket bucket = buckets.computeIfAbsent(key,
                        k -> new TrackBucket(new TrackId(trackName(k))));
                bucket.notes.add(new PitchedNote(slotAt, slotDur, pitchValue, false));
                cursor.graceNotesEmitted++;
            }
            slotAt = slotAt.plus(GRACE_PER_NOTE);
        }
        cursor.pendingGraces.clear();
    }

    /** One buffered grace note awaiting flush onto the next main onset.
     *  Carries authorial {@link Spelling} so spelling survives through
     *  the grace → main-note flush; the codec/render path only cares
     *  about the resolved MIDI, but engravers need the glyph. */
    private record PendingGrace(Spelling spelling, boolean accented,
                                  int staff, int voice, boolean chordedWithPrev) {}

    /**
     * Resolve a {@code <unpitched>} note to a GM percussion {@link DrumNote}
     * via the {@code <part-list><midi-instrument>} reference. Drops the
     * note (counted on the cursor for a summary log) when the instrument
     * id is absent or unmapped.
     */
    private static void emitDrumNote(Element noteEl, String partId,
                                      music.notation.duration.Duration onsetAt,
                                      music.notation.duration.Duration noteDuration,
                                      Map<String, Integer> instrumentMap,
                                      List<DrumNote> drumNotes,
                                      PartCursor cursor) {
        if (instrumentMap.isEmpty()) {
            cursor.unmappedPercussionDropped++;
            return;
        }
        Element instr = firstChild(noteEl, "instrument");
        Integer midi;
        if (instr == null) {
            // Some files omit <instrument> when the part has only one drum sound.
            midi = instrumentMap.values().iterator().next();
        } else {
            String iid = instr.getAttribute("id");
            midi = instrumentMap.get(iid);
            if (midi == null) {
                cursor.unmappedPercussionDropped++;
                return;
            }
        }
        drumNotes.add(new DrumNote(onsetAt, noteDuration, midi));
    }

    /**
     * Maintain a sparse articulation timeline per track. Slurs win over per-note
     * markings — a note inside a slur is LEGATO regardless of any staccato/accent
     * notation. A change is appended only when the kind actually flips, so the
     * resulting {@link ArticulationControl} stays canonical.
     */
    private static void updateArticulationState(TrackBucket bucket, Element noteEl,
                                                 music.notation.duration.Duration onsetAt) {
        boolean slurStartsHere = hasSlurType(noteEl, "start");
        boolean slurEndsHere   = hasSlurType(noteEl, "stop");
        boolean thisNoteInSlur = bucket.inSlur || slurStartsHere;

        Articulation kind = thisNoteInSlur
                ? Articulation.LEGATO
                : articulationFromNotations(noteEl);

        if (kind != bucket.currentArtic) {
            bucket.articChanges.add(new ArticulationChange(onsetAt, kind));
            bucket.currentArtic = kind;
        }

        // After this note, slur is active iff we were in one (or just started) and didn't stop.
        bucket.inSlur = (slurStartsHere || bucket.inSlur) && !slurEndsHere;
    }

    /** First recognised marker under {@code <notations><articulations>}, else NORMAL. */
    private static Articulation articulationFromNotations(Element noteEl) {
        Element notations = firstChild(noteEl, "notations");
        if (notations == null) return Articulation.NORMAL;
        Element articulations = firstChild(notations, "articulations");
        if (articulations == null) return Articulation.NORMAL;
        if (firstChild(articulations, "staccato")      != null) return Articulation.STACCATO;
        if (firstChild(articulations, "accent")        != null) return Articulation.ACCENT;
        if (firstChild(articulations, "tenuto")        != null) return Articulation.TENUTO;
        if (firstChild(articulations, "strong-accent") != null) return Articulation.MARCATO;
        return Articulation.NORMAL;
    }

    private static boolean hasSlurType(Element noteEl, String type) {
        Element notations = firstChild(noteEl, "notations");
        if (notations == null) return false;
        for (Element s : children(notations, "slur")) {
            if (type.equals(s.getAttribute("type"))) return true;
        }
        return false;
    }

    /** Extracted authorial spelling — diatonic step letter, chromatic alter, scientific octave. */
    private record Spelling(music.notation.event.Step step, int alter, int octave) {}

    private static Spelling spellingFromPitch(Element pitch) {
        String stepStr = textOf(firstChild(pitch, "step")).trim();
        music.notation.event.Step step = music.notation.event.Step.valueOf(stepStr);
        Element alterEl = firstChild(pitch, "alter");
        int alter = (alterEl == null) ? 0 : (int) Math.round(Double.parseDouble(textOf(alterEl).trim()));
        int octave = Integer.parseInt(textOf(firstChild(pitch, "octave")).trim());
        return new Spelling(step, alter, octave);
    }

    /** Legacy MIDI-only resolution path used by the chord-grouping code that
     *  still expects an int — kept thin around {@link #spellingFromPitch}. */
    private static int midiFromPitch(Element pitch) {
        Spelling sp = spellingFromPitch(pitch);
        return (sp.octave + 1) * 12 + sp.step.semitoneFromC() + sp.alter;
    }

    private static boolean hasTie(Element noteEl, String type) {
        for (Element t : children(noteEl, "tie")) {
            if (type.equals(t.getAttribute("type"))) return true;
        }
        return false;
    }

    private static int intText(Element parent, String childName, int defaultVal) {
        String s = textOf(firstChild(parent, childName));
        return (s == null || s.isBlank()) ? defaultVal : Integer.parseInt(s.trim());
    }

    private static String trackName(TrackKey k) {
        return k.partId + " · staff " + k.staff + " · v" + k.voice;
    }

    // ── DOM helpers ─────────────────────────────────────────────────────

    private static Document parseXml(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to parse MusicXML", e);
        }
    }

    private static String localName(Element el) {
        String ln = el.getLocalName();
        return ln != null ? ln : el.getTagName();
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && localName.equals(localName((Element) n))) {
                out.add((Element) n);
            }
        }
        return out;
    }

    private static Element firstChild(Element parent, String localName) {
        if (parent == null) return null;
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && localName.equals(localName((Element) n))) {
                return (Element) n;
            }
        }
        return null;
    }

    private static String textOf(Element el) {
        return el == null ? null : el.getTextContent();
    }

    // ── Internal types ─────────────────────────────────────────────────

    /**
     * Parser output: the concrete-notes performance plus extracted score
     * meta and the structural sidecars ({@link RepeatStructure} +
     * {@link Transpositions}).
     */
    public record Result(Performance performance, TimeSignature timeSig,
                         KeySignature key,
                         RepeatStructure repeatStructure,
                         Transpositions transpositions) {}

    private record ScoreMeta(int divisions, TimeSignature timeSig, KeySignature key, int bpm) {}

    private record TrackKey(String partId, int staff, int voice) {}

    /** Dynamic marking emitted into the {@link Volume} side-channel.
     *  {@code loudness} preserves whether the mark was authored as a
     *  symbolic Dynamic ({@link music.notation.expressivity.Loudness.Named})
     *  or a numeric percentage ({@link music.notation.expressivity.Loudness.Raw}). */
    private record DynamicsEvent(long div, String partId, Integer staff,
                                 music.notation.expressivity.Loudness loudness) {}

    /** Sustain-pedal event emitted into the {@link Pedaling} side-channel. */
    private record PedalEvent(long div, String partId, PedalState state) {}

    /** A single wedge (hairpin) endpoint: a {@code <wedge>} element at
     *  some musical position. Starts (CRESCENDO/DECRESCENDO) are paired
     *  with later stops by {@code number} inside the same part. */
    private record WedgeEvent(long div, String partId, Integer number, WedgeKind kind) {}

    private enum WedgeKind { CRESCENDO, DECRESCENDO, STOP }

    /** Mean MIDI pitch of pitched notes in a bucket (0 when empty). */
    private static double averagePitch(TrackBucket bucket) {
        long sum = 0;
        int count = 0;
        for (ConcreteNote n : bucket.notes) {
            if (n instanceof PitchedNote pn) {
                sum += pn.midi();
                count++;
            }
        }
        return count == 0 ? 0.0 : (double) sum / count;
    }

    private static final class TrackBucket {
        final TrackId id;
        final List<ConcreteNote> notes = new ArrayList<>();
        final List<ArticulationChange> articChanges = new ArrayList<>();
        Articulation currentArtic = Articulation.NORMAL;
        boolean inSlur = false;
        TrackBucket(TrackId id) { this.id = id; }
    }

    /**
     * Per-part time cursor (in division units, accumulated from piece start).
     * Defers ms math to a shared {@link TempoTimeline} so mid-piece tempo
     * changes are honoured across all parts.
     *
     * <p>Also tracks "first attribute seen" flags so {@link #applyAttributes}
     * can distinguish the initial time/key/divisions declarations from
     * mid-piece changes (which we currently log + ignore). Grace-note
     * skip count is accumulated here and emitted as a per-part summary
     * at the end of {@link #walkPart}.</p>
     */
    private static final class PartCursor {
        final TempoTimeline tempos;
        long cursorDiv = 0;
        long prevOnsetDiv = 0;

        boolean seenInitialTime = false;
        boolean seenInitialKey = false;
        boolean seenInitialDivisions = false;

        // Grace notes buffered between their <grace> elements and the
        // following non-grace note. The flush logic in
        // {@link MusicXmlParser#flushPendingGraces} consumes this list.
        final java.util.List<PendingGrace> pendingGraces = new java.util.ArrayList<>();
        int graceNotesEmitted = 0;

        int unmappedPercussionDropped = 0;

        PartCursor(TempoTimeline tempos) {
            this.tempos = tempos;
        }

        music.notation.duration.Duration divToDuration(long div) {
            return tempos.divToDuration(div);
        }
    }
}
