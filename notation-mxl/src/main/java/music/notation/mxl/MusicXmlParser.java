package music.notation.mxl;

import music.notation.expressivity.Articulation;
import music.notation.expressivity.ArticulationChange;
import music.notation.expressivity.ArticulationControl;
import music.notation.expressivity.Articulations;
import music.notation.performance.ConcreteNote;
import music.notation.performance.DrumNote;
import music.notation.performance.Instrumentation;
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

        Map<TrackKey, TrackBucket> buckets = new LinkedHashMap<>();
        List<DynamicsEvent> dynamicsEvents = new ArrayList<>();
        List<PedalEvent> pedalEvents = new ArrayList<>();
        Map<String, Transpose> partTranspose = new LinkedHashMap<>();
        List<DrumNote> drumNotes = new ArrayList<>();
        for (Element partEl : children(root, "part")) {
            walkPart(partEl, tempos, schedule, buckets, dynamicsEvents, pedalEvents,
                    partInstruments, partTranspose, drumNotes);
        }

        return assemble(meta, tempos, buckets, dynamicsEvents, pedalEvents,
                repeatResult.structure(), partTranspose, drumNotes);
    }

    /** Build the {@link Performance} + side-channels from the per-part walk results. */
    private static Result assemble(ScoreMeta meta, TempoTimeline tempos,
                                    Map<TrackKey, TrackBucket> buckets,
                                    List<DynamicsEvent> dynamicsEvents,
                                    List<PedalEvent> pedalEvents,
                                    RepeatStructure repeatStructure,
                                    Map<String, Transpose> partTranspose,
                                    List<DrumNote> drumNotes) {
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
        Map<TrackId, VolumeControl> volumeMap = new LinkedHashMap<>();
        Map<TrackId, VelocityControl> velocityMap = new LinkedHashMap<>();
        Map<TrackId, ArticulationControl> articMap = new LinkedHashMap<>();
        Map<TrackId, Transpose> transposeMap = new LinkedHashMap<>();
        Map<TrackId, PedalControl> pedalingMap = new LinkedHashMap<>();

        for (var entry : ordered) {
            TrackKey key = entry.getKey();
            TrackBucket bucket = entry.getValue();
            tracks.add(new Track(bucket.id, TrackKind.PITCHED, bucket.notes));

            List<VolumeChange> volChanges = new ArrayList<>();
            List<VelocityChange> velChanges = new ArrayList<>();
            for (DynamicsEvent ev : dynamicsEvents) {
                if (!ev.partId().equals(key.partId)) continue;
                if (ev.staff() != null && ev.staff() != key.staff) continue;
                long ms = tempos.divToMs(ev.div());
                volChanges.add(new VolumeChange(ms, ev.cc7()));
                // Same dynamic value drives both channel volume (CC #7)
                // and per-note attack velocity. Clamp to [1,127] — vel 0
                // is illegal in the model (NOTE_OFF synonym).
                velChanges.add(new VelocityChange(ms, clamp(ev.cc7(), 1, 127)));
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
                pedalChanges.add(new PedalChange(tempos.divToMs(ev.div()), ev.state()));
            }
            if (!pedalChanges.isEmpty()) {
                pedalingMap.put(bucket.id, new PedalControl(pedalChanges));
            }
        }

        // Drums: collapse all parts' percussion into a single DRUM track
        // (Score allows at most one). When at least one drum hit landed,
        // append a Track at the end — Score's constructor canonicalises it.
        if (!drumNotes.isEmpty()) {
            List<ConcreteNote> drumConcrete = new ArrayList<>(drumNotes);
            tracks.add(new Track(new TrackId("Drums"), TrackKind.DRUM, drumConcrete));
        }

        Performance perf = new Performance(
                new Score(tracks),
                tempos.toTempoTrack(),
                Instrumentation.empty(),
                new Volume(volumeMap),
                new Articulations(articMap),
                new Pedaling(pedalingMap),
                new Velocities(velocityMap));
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
                int beats = Integer.parseInt(textOf(firstChild(timeEl, "beats")).trim());
                int beatType = Integer.parseInt(textOf(firstChild(timeEl, "beat-type")).trim());
                timeSig = new TimeSignature(beats, beatType);
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
                                  Map<String, Map<String, Integer>> partInstruments,
                                  Map<String, Transpose> partTranspose,
                                  List<DrumNote> drumNotes) {
        String partId = partEl.getAttribute("id");
        PartCursor cursor = new PartCursor(tempos);
        Map<String, Integer> instrumentMap = partInstruments.getOrDefault(partId, Map.of());
        List<Element> measures = children(partEl, "measure");

        for (int idx : schedule) {
            if (idx < 0 || idx >= measures.size()) continue;
            walkMeasure(measures.get(idx), partId, cursor, buckets, dynamicsEvents,
                    pedalEvents, instrumentMap, partTranspose, drumNotes);
        }

        // Per-part diagnostic summary: surface counts of skipped/dropped notes
        // so the user knows what was lost without cluttering the per-note log.
        if (cursor.graceNotesSkipped > 0) {
            LOG.warn("Part {} — skipped {} grace note(s) (deferred per plan)",
                    partId, cursor.graceNotesSkipped);
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
                                     Map<String, Integer> instrumentMap,
                                     Map<String, Transpose> partTranspose,
                                     List<DrumNote> drumNotes) {
        for (Node n = measureEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) n;
            switch (localName(el)) {
                case "attributes" -> applyAttributes(el, partId, cursor, partTranspose);
                case "note"       -> emitNote(el, partId, cursor, buckets,
                                                instrumentMap, partTranspose, drumNotes);
                case "backup"     -> cursor.cursorDiv -= readDuration(el);
                case "forward"    -> cursor.cursorDiv += readDuration(el);
                case "direction"  -> handleDirection(el, partId, cursor,
                                                     dynamicsEvents, pedalEvents);
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
                                         List<PedalEvent> pedalEvents) {
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
        }

        // Dynamics — original handler logic preserved below.
        addDynamicsFromDirection(direction, partId, cursor, events);
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

    /** Original {@code handleDirection} body — extracted for clarity. */
    private static void addDynamicsFromDirection(Element direction, String partId,
                                                   PartCursor cursor,
                                                   List<DynamicsEvent> events) {
        Integer staff = childIntOrNull(direction, "staff");

        Integer cc7 = null;
        Element sound = firstChild(direction, "sound");
        if (sound != null) {
            String dyn = sound.getAttribute("dynamics");
            if (!dyn.isBlank()) {
                try {
                    double pct = Double.parseDouble(dyn);
                    cc7 = clamp((int) Math.round(pct * 90.0 / 100.0), 1, 127);
                } catch (NumberFormatException ex) {
                    LOG.warn("ignored non-numeric <sound dynamics>: '{}'", dyn);
                }
            }
        }
        if (cc7 == null) {
            Element dt = firstChild(direction, "direction-type");
            if (dt != null) {
                Element dynEl = firstChild(dt, "dynamics");
                if (dynEl != null) {
                    Integer fromSym = symbolicDynamic(dynEl);
                    if (fromSym != null) cc7 = fromSym;
                }
            }
        }
        if (cc7 != null) {
            events.add(new DynamicsEvent(cursor.cursorDiv, partId, staff, cc7));
        }
    }

    /** First known symbolic-dynamic child of {@code <dynamics>} → CC #7 value, else null. */
    private static Integer symbolicDynamic(Element dynamicsEl) {
        for (Node n = dynamicsEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Integer mapped = SYMBOLIC_DYNAMICS.get(localName((Element) n));
            if (mapped != null) return mapped;
        }
        return null;
    }

    /** Standard symbolic → CC #7 mapping. Special markings (sf*, fp, …) collapse to f. */
    private static final Map<String, Integer> SYMBOLIC_DYNAMICS = Map.ofEntries(
            Map.entry("pppp", 16),
            Map.entry("ppp",  24),
            Map.entry("pp",   33),
            Map.entry("p",    49),
            Map.entry("mp",   64),
            Map.entry("mf",   80),
            Map.entry("f",    96),
            Map.entry("ff",   108),
            Map.entry("fff",  120),
            Map.entry("ffff", 127),
            Map.entry("sf",   96),
            Map.entry("sfp",  96),
            Map.entry("sfz",  96),
            Map.entry("fp",   96),
            Map.entry("fz",   96),
            Map.entry("rf",   96),
            Map.entry("rfz",  96)
    );

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static Integer childIntOrNull(Element parent, String childName) {
        String s = textOf(firstChild(parent, childName));
        return (s == null || s.isBlank()) ? null : Integer.parseInt(s.trim());
    }

    private static void applyAttributes(Element attributes, String partId, PartCursor cursor,
                                         Map<String, Transpose> partTranspose) {
        // <divisions>, <time>, <key> changes are not honoured yet — the
        // TempoTimeline + score-level meta lock in the first occurrence.
        // Distinguish the initial declaration (silent) from mid-piece
        // changes (warn) via per-part "seen" flags on the cursor.
        if (firstChild(attributes, "divisions") != null) {
            if (cursor.seenInitialDivisions) {
                LOG.warn("mid-piece <divisions> change in part {} ignored "
                        + "(single-divisions limitation)", partId);
            } else {
                cursor.seenInitialDivisions = true;
            }
        }
        if (firstChild(attributes, "time") != null) {
            if (cursor.seenInitialTime) {
                LOG.warn("mid-piece <time> change in part {} ignored "
                        + "(single-time-sig limitation, see plan doc)", partId);
            } else {
                cursor.seenInitialTime = true;
            }
        }
        if (firstChild(attributes, "key") != null) {
            if (cursor.seenInitialKey) {
                LOG.warn("mid-piece <key> change in part {} ignored "
                        + "(single-key-sig limitation, see plan doc)", partId);
            } else {
                cursor.seenInitialKey = true;
            }
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
            cursor.graceNotesSkipped++;
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

        if (isRest) return;

        long onsetMs    = cursor.divToMs(onsetDiv);
        long durationMs = Math.max(1, cursor.divToMs(onsetDiv + durationDiv) - onsetMs);

        if (isUnpitched) {
            emitDrumNote(noteEl, partId, onsetMs, durationMs, instrumentMap, drumNotes, cursor);
            return;
        }

        Element pitch = firstChild(noteEl, "pitch");
        if (pitch == null) return;
        int writtenMidi = midiFromPitch(pitch);
        Transpose t = partTranspose.getOrDefault(partId, Transpose.NONE);
        int midi = clamp(writtenMidi + t.totalSemitones(), 0, 127);

        boolean tiedToNext = hasTie(noteEl, "start");
        int staff = intText(noteEl, "staff", 1);
        int voice = intText(noteEl, "voice", 1);

        TrackKey key = new TrackKey(partId, staff, voice);
        TrackBucket bucket = buckets.computeIfAbsent(key,
                k -> new TrackBucket(new TrackId(trackName(k))));
        bucket.notes.add(new PitchedNote(onsetMs, durationMs, midi, tiedToNext));

        if (!isChord) updateArticulationState(bucket, noteEl, onsetMs);
    }

    /**
     * Resolve a {@code <unpitched>} note to a GM percussion {@link DrumNote}
     * via the {@code <part-list><midi-instrument>} reference. Drops the
     * note (counted on the cursor for a summary log) when the instrument
     * id is absent or unmapped.
     */
    private static void emitDrumNote(Element noteEl, String partId,
                                      long onsetMs, long durationMs,
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
        drumNotes.add(new DrumNote(onsetMs, durationMs, midi));
    }

    /**
     * Maintain a sparse articulation timeline per track. Slurs win over per-note
     * markings — a note inside a slur is LEGATO regardless of any staccato/accent
     * notation. A change is appended only when the kind actually flips, so the
     * resulting {@link ArticulationControl} stays canonical.
     */
    private static void updateArticulationState(TrackBucket bucket, Element noteEl, long onsetMs) {
        boolean slurStartsHere = hasSlurType(noteEl, "start");
        boolean slurEndsHere   = hasSlurType(noteEl, "stop");
        boolean thisNoteInSlur = bucket.inSlur || slurStartsHere;

        Articulation kind = thisNoteInSlur
                ? Articulation.LEGATO
                : articulationFromNotations(noteEl);

        if (kind != bucket.currentArtic) {
            bucket.articChanges.add(new ArticulationChange(onsetMs, kind));
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

    private static int midiFromPitch(Element pitch) {
        String step = textOf(firstChild(pitch, "step")).trim();
        Element alterEl = firstChild(pitch, "alter");
        int alter = (alterEl == null) ? 0 : (int) Math.round(Double.parseDouble(textOf(alterEl).trim()));
        int octave = Integer.parseInt(textOf(firstChild(pitch, "octave")).trim());
        return PitchMath.toMidi(step, alter, octave);
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

    /** Dynamic marking emitted into the {@link Volume} side-channel. */
    private record DynamicsEvent(long div, String partId, Integer staff, int cc7) {}

    /** Sustain-pedal event emitted into the {@link Pedaling} side-channel. */
    private record PedalEvent(long div, String partId, PedalState state) {}

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
        int graceNotesSkipped = 0;
        int unmappedPercussionDropped = 0;

        PartCursor(TempoTimeline tempos) {
            this.tempos = tempos;
        }

        long divToMs(long div) {
            return tempos.divToMs(div);
        }
    }
}
