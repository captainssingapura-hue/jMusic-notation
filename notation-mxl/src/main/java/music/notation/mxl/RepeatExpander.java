package music.notation.mxl;

import music.notation.mxl.RepeatStructure.Direction;
import music.notation.mxl.RepeatStructure.Jump;
import music.notation.mxl.RepeatStructure.JumpKind;
import music.notation.mxl.RepeatStructure.RepeatBar;
import music.notation.mxl.RepeatStructure.Volta;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the structural markings from a part's measures and produces both:
 * <ol>
 *   <li>a {@link RepeatStructure} describing the score's original layout
 *       (for the sidecar / sheet-music reconstruction), and</li>
 *   <li>a <em>playback schedule</em> — a flat list of original-measure
 *       indices in playback order, with measures duplicated as repeats /
 *       D.C. / D.S. dictate, and voltas resolved by pass number.</li>
 * </ol>
 *
 * <p>The schedule is what {@code MusicXmlParser} iterates instead of the
 * raw {@code <measure>} list, so the same per-measure walking code emits
 * notes at fresh cumulative cursor offsets on each pass.</p>
 *
 * <p>Indexing: all measure indices are <strong>0-based document order</strong>
 * within the part — directly usable as a {@code List<Element>} index. The
 * composer-visible {@code <measure number>} attribute is not stored here;
 * downstream tools can recover it from the source XML.</p>
 */
final class RepeatExpander {

    private static final int MAX_SCHEDULE_LENGTH = 100_000;

    private RepeatExpander() {}

    /**
     * Analyse {@code measures} and produce {@link RepeatStructure} +
     * playback schedule.
     */
    static Result analyze(List<Element> measures) {
        Extracted ex = extractStructure(measures);
        List<Integer> schedule = simulateSchedule(measures.size(), ex);
        return new Result(
                new RepeatStructure(ex.bars, ex.voltas, ex.jumps),
                schedule);
    }

    record Result(RepeatStructure structure, List<Integer> schedule) {}

    // ── Step 1: scan XML for repeat / volta / jump markings ──────────────

    private static Extracted extractStructure(List<Element> measures) {
        List<RepeatBar> bars = new ArrayList<>();
        List<Volta> voltas = new ArrayList<>();
        List<Jump> jumps = new ArrayList<>();

        Integer openVoltaStart = null;
        List<Integer> openVoltaNumbers = null;

        for (int m = 0; m < measures.size(); m++) {
            Element measure = measures.get(m);
            for (Node n = measure.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) n;
                String name = localName(el);

                if ("barline".equals(name)) {
                    Element repeat = firstChild(el, "repeat");
                    if (repeat != null) {
                        String dir = repeat.getAttribute("direction");
                        int times = parseTimes(repeat);
                        Direction direction = "forward".equals(dir)
                                ? Direction.FORWARD : Direction.BACKWARD;
                        bars.add(new RepeatBar(m, direction, times));
                    }
                    Element ending = firstChild(el, "ending");
                    if (ending != null) {
                        String type = ending.getAttribute("type");
                        if ("start".equals(type)) {
                            openVoltaStart = m;
                            openVoltaNumbers = parseEndingNumbers(ending.getAttribute("number"));
                        } else if ("stop".equals(type) || "discontinue".equals(type)) {
                            if (openVoltaStart != null) {
                                voltas.add(new Volta(openVoltaStart, m, openVoltaNumbers));
                                openVoltaStart = null;
                                openVoltaNumbers = null;
                            }
                        }
                    }
                } else if ("direction".equals(name)) {
                    Element sound = firstChild(el, "sound");
                    if (sound != null) collectJumps(sound, m, jumps);
                } else if ("sound".equals(name)) {
                    collectJumps(el, m, jumps);
                }
            }
        }

        return new Extracted(bars, voltas, jumps);
    }

    private static void collectJumps(Element sound, int m, List<Jump> jumps) {
        addJumpIfPresent(sound, "segno",    JumpKind.SEGNO,    m, jumps);
        addJumpIfPresent(sound, "coda",     JumpKind.CODA,     m, jumps);
        addJumpIfPresent(sound, "dalsegno", JumpKind.DALSEGNO, m, jumps);
        addJumpIfPresent(sound, "tocoda",   JumpKind.TOCODA,   m, jumps);
        if ("yes".equalsIgnoreCase(sound.getAttribute("dacapo"))) {
            jumps.add(new Jump(m, JumpKind.DACAPO, ""));
        }
        if ("yes".equalsIgnoreCase(sound.getAttribute("fine"))) {
            jumps.add(new Jump(m, JumpKind.FINE, ""));
        }
    }

    private static void addJumpIfPresent(Element sound, String attr, JumpKind kind,
                                          int m, List<Jump> jumps) {
        String label = sound.getAttribute(attr);
        if (label != null && !label.isBlank()) {
            jumps.add(new Jump(m, kind, label));
        }
    }

    private static int parseTimes(Element repeat) {
        String t = repeat.getAttribute("times");
        if (t == null || t.isBlank()) return 2;
        try { return Math.max(1, Integer.parseInt(t.trim())); }
        catch (NumberFormatException e) { return 2; }
    }

    /** Parse a volta {@code number="…"} attribute like {@code "1"} or {@code "1, 3"}. */
    private static List<Integer> parseEndingNumbers(String raw) {
        List<Integer> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) { out.add(1); return out; }
        for (String part : raw.split("[\\s,]+")) {
            if (part.isBlank()) continue;
            try { out.add(Integer.parseInt(part.trim())); }
            catch (NumberFormatException ignored) { /* skip malformed */ }
        }
        if (out.isEmpty()) out.add(1);
        return out;
    }

    private record Extracted(List<RepeatBar> bars, List<Volta> voltas, List<Jump> jumps) {}

    // ── Step 2: simulate the playback head over the structure ────────────

    private static List<Integer> simulateSchedule(int measureCount, Extracted ex) {
        // Lookup tables for fast per-measure questions.
        Map<Integer, RepeatBar> forwardBars = new HashMap<>();
        Map<Integer, RepeatBar> backwardBars = new HashMap<>();
        for (RepeatBar b : ex.bars) {
            (b.direction() == Direction.FORWARD ? forwardBars : backwardBars)
                    .put(b.measureIndex(), b);
        }
        Map<Integer, Volta> voltasByStart = new HashMap<>();
        for (Volta v : ex.voltas) voltasByStart.put(v.startMeasure(), v);

        Map<Integer, List<Jump>> jumpsByMeasure = new HashMap<>();
        for (Jump j : ex.jumps) {
            jumpsByMeasure.computeIfAbsent(j.measureIndex(), k -> new ArrayList<>()).add(j);
        }

        // Label → measure for segno / coda anchors. Take the first
        // occurrence if a label is reused (rare).
        Map<String, Integer> segnoLabel = new HashMap<>();
        Map<String, Integer> codaLabel  = new HashMap<>();
        for (Jump j : ex.jumps) {
            if (j.kind() == JumpKind.SEGNO) segnoLabel.putIfAbsent(j.label(), j.measureIndex());
            if (j.kind() == JumpKind.CODA)  codaLabel.putIfAbsent(j.label(),  j.measureIndex());
        }

        List<Integer> schedule = new ArrayList<>();
        int pos = 0;
        boolean afterJump = false;
        int currentPass = 1;
        int forwardTarget = 0;
        Map<Integer, Integer> backwardPasses = new HashMap<>();
        int safety = 0;

        while (pos >= 0 && pos < measureCount) {
            if (++safety > MAX_SCHEDULE_LENGTH) {
                throw new IllegalStateException(
                        "RepeatExpander: schedule exceeded " + MAX_SCHEDULE_LENGTH +
                        " measures — likely infinite loop in score structure");
            }

            // (a) Volta exclusion: if this measure starts a volta whose pass
            //     numbers don't include the current pass, skip past it.
            Volta voltaHere = voltasByStart.get(pos);
            if (voltaHere != null && !voltaHere.numbers().contains(currentPass)) {
                pos = voltaHere.stopMeasure() + 1;
                continue;
            }

            // (b) Forward repeat at start of this measure marks a new loop target.
            //     Only reset the pass counter on the FIRST natural entry — when
            //     we loop back via a backward-repeat we already incremented
            //     currentPass and must keep that pass active for volta selection.
            if (forwardBars.containsKey(pos) && forwardTarget != pos) {
                forwardTarget = pos;
                currentPass = 1;
            }

            schedule.add(pos);

            // (c) Backward repeat at end of this measure?
            RepeatBar back = backwardBars.get(pos);
            if (back != null) {
                int total = back.times();
                int seen  = backwardPasses.getOrDefault(pos, 0) + 1;
                backwardPasses.put(pos, seen);
                if (seen < total) {
                    currentPass++;
                    pos = forwardTarget;
                    continue;
                } else {
                    currentPass = 1;
                }
            }

            // (d) End-of-measure jumps. D.C./D.S. only fire on the first pass
            //     through the measure that holds them; tocoda and fine fire
            //     only AFTER the D.C./D.S. has happened.
            List<Jump> jumpsHere = jumpsByMeasure.getOrDefault(pos, List.of());
            if (!afterJump) {
                Jump dc = findKind(jumpsHere, JumpKind.DACAPO);
                if (dc != null) {
                    afterJump = true;
                    forwardTarget = 0;
                    currentPass = 1;
                    backwardPasses.clear();
                    pos = 0;
                    continue;
                }
                Jump ds = findKind(jumpsHere, JumpKind.DALSEGNO);
                if (ds != null) {
                    Integer target = segnoLabel.get(ds.label());
                    if (target == null && !segnoLabel.isEmpty()) {
                        target = segnoLabel.values().iterator().next();
                    }
                    if (target != null) {
                        afterJump = true;
                        forwardTarget = target;
                        currentPass = 1;
                        backwardPasses.clear();
                        pos = target;
                        continue;
                    }
                }
            } else {
                if (findKind(jumpsHere, JumpKind.FINE) != null) break;
                Jump tocoda = findKind(jumpsHere, JumpKind.TOCODA);
                if (tocoda != null) {
                    Integer target = codaLabel.get(tocoda.label());
                    if (target == null && !codaLabel.isEmpty()) {
                        target = codaLabel.values().iterator().next();
                    }
                    if (target != null) {
                        pos = target;
                        continue;
                    }
                }
            }

            pos++;
        }

        return schedule;
    }

    private static Jump findKind(List<Jump> list, JumpKind k) {
        for (Jump j : list) if (j.kind() == k) return j;
        return null;
    }

    // ── DOM helpers (mirror MusicXmlParser's) ────────────────────────────

    private static String localName(Element el) {
        String ln = el.getLocalName();
        return ln != null ? ln : el.getTagName();
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
}
