package music.notation.guitar;

import music.notation.duration.Duration;
import music.notation.performance.PitchedNote;

import java.util.ArrayList;
import java.util.List;

/**
 * Places every note of a passage on the neck so that the whole passage
 * is as easy as possible to play.
 *
 * <h2>Model</h2>
 * <p>Notes that start together form one <em>event</em> (a chord, or a
 * single note). Each event has a finite set of <em>assignments</em> —
 * one {@link FretPosition} per note, on distinct strings, with the
 * fretted notes no wider apart than {@link HandConstraints#maxStretch()}.
 * A <em>state</em> is an assignment plus a hand position: the fret the
 * index finger sits at, chosen so the assignment lies inside the hand's
 * {@link HandConstraints#span()}.</p>
 *
 * <p>The cost of a fingering is the sum of a <em>local</em> cost per
 * state (high frets, open strings, chord stretch) and a <em>transition</em>
 * cost between consecutive states (moving the hand, skipping strings),
 * scaled up when the two events are close in time. Tied notes must stay
 * on the same string and fret. The minimum-cost path through the states
 * is found by dynamic programming (Viterbi), so the result is optimal
 * under the {@link CostWeights} and deterministic.</p>
 */
public final class FingeringSolver {

    private static final double TIE_BREAK = 1e6;

    private final Tuning tuning;
    private final HandConstraints hand;
    private final CostWeights w;
    private final Meter meter;

    public FingeringSolver(Tuning tuning, HandConstraints hand, CostWeights weights, Meter meter) {
        this.tuning = tuning;
        this.hand = hand;
        this.w = weights;
        this.meter = meter;
    }

    // ── model ────────────────────────────────────────────────────────

    /** Notes starting at the same instant, highest pitch first. */
    private record Event(Duration at, List<PitchedNote> notes, boolean[] folded) {}

    private record State(FretPosition[] positions, int handPosition, double local, double meanString) {}

    // ── solve ────────────────────────────────────────────────────────

    public Fingering solve(List<PitchedNote> input) {
        if (input.isEmpty()) return Fingering.empty();
        List<Event> events = events(input);

        List<List<State>> states = new ArrayList<>(events.size());
        for (Event e : events) states.add(statesFor(e));

        // DP over events.
        int n = events.size();
        double[][] cost = new double[n][];
        int[][] back = new int[n][];
        for (int i = 0; i < n; i++) {
            List<State> cur = states.get(i);
            cost[i] = new double[cur.size()];
            back[i] = new int[cur.size()];
            for (int s = 0; s < cur.size(); s++) {
                State st = cur.get(s);
                if (i == 0) { cost[i][s] = st.local; back[i][s] = -1; continue; }
                List<State> prev = states.get(i - 1);
                double speed = w.speedFactor(meter.toBeats(events.get(i).at.minus(events.get(i - 1).at)));
                double best = Double.POSITIVE_INFINITY; int bestPrev = -1;
                for (int p = 0; p < prev.size(); p++) {
                    double c = cost[i - 1][p] + transition(events.get(i - 1), prev.get(p), events.get(i), st, speed);
                    if (c < best) { best = c; bestPrev = p; }
                }
                cost[i][s] = best + st.local;
                back[i][s] = bestPrev;
            }
        }

        // Best final state, then walk back.
        int last = n - 1, bestS = 0;
        for (int s = 1; s < cost[last].length; s++) if (cost[last][s] < cost[last][bestS]) bestS = s;
        double total = cost[last][bestS];
        int[] chosen = new int[n];
        for (int i = last, s = bestS; i >= 0; i--) { chosen[i] = s; s = back[i][s]; }

        List<FingeredNote> out = new ArrayList<>(input.size());
        for (int i = 0; i < n; i++) {
            Event e = events.get(i);
            State st = states.get(i).get(chosen[i]);
            for (int k = 0; k < e.notes.size(); k++) {
                PitchedNote pn = e.notes.get(k);
                int bar0 = meter.barOf(pn.at());
                out.add(new FingeredNote(pn.at(), pn.duration(), pn.midi(), st.positions[k],
                        st.handPosition, bar0 + 1, meter.beatInBar(pn.at()) + 1.0, e.folded[k]));
            }
        }
        return new Fingering(out, total);
    }

    // ── events ───────────────────────────────────────────────────────

    private List<Event> events(List<PitchedNote> input) {
        List<PitchedNote> sorted = new ArrayList<>(input);
        sorted.sort((a, b) -> {
            int c = a.at().compareDuration(b.at());
            return c != 0 ? c : Integer.compare(b.midi(), a.midi());
        });

        int lo = tuning.lowestPitch(), hi = tuning.highestPitch(hand.maxFret());
        List<PitchedNote> unplayable = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        List<PitchedNote> group = new ArrayList<>();
        List<Boolean> groupFolded = new ArrayList<>();
        Duration groupAt = null;
        for (PitchedNote pn : sorted) {
            boolean folded = false;
            int midi = pn.midi();
            if (midi < lo || midi > hi) {
                if (!hand.foldOutOfRange()) { unplayable.add(pn); continue; }
                while (midi < lo) midi += 12;
                while (midi > hi) midi -= 12;
                if (midi < lo) { unplayable.add(pn); continue; }   // range narrower than an octave
                pn = new PitchedNote(pn.at(), pn.duration(), midi, pn.tiedToNext());
                folded = true;
            }
            if (groupAt != null && !groupAt.equalsDuration(pn.at())) {
                events.add(new Event(groupAt, List.copyOf(group), toArray(groupFolded)));
                group.clear(); groupFolded.clear();
            }
            groupAt = pn.at();
            group.add(pn); groupFolded.add(folded);
        }
        if (groupAt != null) events.add(new Event(groupAt, List.copyOf(group), toArray(groupFolded)));
        if (!unplayable.isEmpty()) {
            throw new UnplayableNotesException(unplayable.size() + " note(s) outside " + lo + "–" + hi
                    + " (first at " + unplayable.get(0).at() + ", midi " + unplayable.get(0).midi() + ")", unplayable);
        }
        for (Event e : events) {
            if (e.notes.size() > tuning.stringCount()) {
                throw new UnplayableNotesException(e.notes.size() + " simultaneous notes at " + e.at
                        + " on a " + tuning.stringCount() + "-string guitar", e.notes);
            }
        }
        return events;
    }

    private static boolean[] toArray(List<Boolean> list) {
        boolean[] a = new boolean[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    // ── states ───────────────────────────────────────────────────────

    private List<FretPosition> candidates(int midi) {
        List<FretPosition> out = new ArrayList<>();
        for (int s = 1; s <= tuning.stringCount(); s++) {
            int fret = midi - tuning.pitchAt(s, 0);
            if (fret < 0 || fret > hand.maxFret()) continue;
            if (fret == 0 && !hand.allowOpenStrings()) continue;
            out.add(new FretPosition(s, fret));
        }
        return out;
    }

    private List<State> statesFor(Event e) {
        List<List<FretPosition>> perNote = new ArrayList<>();
        for (PitchedNote pn : e.notes) perNote.add(candidates(pn.midi()));
        List<State> states = new ArrayList<>();
        assign(perNote, 0, new FretPosition[e.notes.size()], states);
        if (states.isEmpty()) {
            throw new UnplayableNotesException("no fingering for the notes at " + e.at
                    + " (need " + e.notes.size() + " distinct strings within " + hand.maxStretch() + " frets)", e.notes);
        }
        return states;
    }

    private void assign(List<List<FretPosition>> perNote, int k, FretPosition[] acc, List<State> out) {
        if (k == acc.length) { addStates(acc.clone(), out); return; }
        for (FretPosition p : perNote.get(k)) {
            boolean clash = false;
            for (int j = 0; j < k; j++) if (acc[j].string() == p.string()) { clash = true; break; }
            if (clash) continue;
            acc[k] = p;
            if (frettedSpan(acc, k + 1) > hand.maxStretch()) continue;
            assign(perNote, k + 1, acc, out);
        }
    }

    private static int frettedSpan(FretPosition[] ps, int count) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            int f = ps[i].fret();
            if (f == 0) continue;
            lo = Math.min(lo, f); hi = Math.max(hi, f);
        }
        return lo == Integer.MAX_VALUE ? 0 : hi - lo;
    }

    private void addStates(FretPosition[] ps, List<State> out) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        double local = 0, strings = 0;
        for (FretPosition p : ps) {
            strings += p.string();
            if (p.fret() == 0) { local += w.openString(); continue; }
            lo = Math.min(lo, p.fret()); hi = Math.max(hi, p.fret());
            local += w.highFret() * Math.max(0, p.fret() - hand.preferredMaxFret());
        }
        double meanString = strings / ps.length;
        int topHand = hand.maxFret() - hand.span() + 1;
        if (lo == Integer.MAX_VALUE) {
            // All open: the hand may rest anywhere; the DP will keep it where it was.
            for (int h = 1; h <= topHand; h++) out.add(new State(ps, h, local, meanString));
            return;
        }
        local += w.stretch() * Math.max(0, (hi - lo) - (hand.span() - 1));
        int from = Math.max(1, hi - hand.span() + 1), to = Math.min(lo, topHand);
        if (from > to) { out.add(new State(ps, Math.min(lo, topHand), local, meanString)); return; }   // stretched chord
        for (int h = from; h <= to; h++) out.add(new State(ps, h, local, meanString));
    }

    // ── transition ───────────────────────────────────────────────────

    private double transition(Event prevEvent, State prev, Event curEvent, State cur, double speed) {
        int dh = Math.abs(cur.handPosition - prev.handPosition);
        double move = dh == 0 ? 0 : w.positionChange() + w.shift() * dh;
        double skip = w.stringSkip() * Math.max(0, Math.abs(cur.meanString - prev.meanString) - 1);
        double c = (move + skip) * speed;
        // A tie must not change position.
        for (int i = 0; i < prevEvent.notes.size(); i++) {
            PitchedNote a = prevEvent.notes.get(i);
            if (!a.tiedToNext()) continue;
            for (int j = 0; j < curEvent.notes.size(); j++) {
                if (curEvent.notes.get(j).midi() == a.midi() && !cur.positions[j].equals(prev.positions[i])) c += TIE_BREAK;
            }
        }
        return c;
    }
}
