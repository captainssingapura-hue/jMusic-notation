package music.notation.transcribe.bench;

import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import music.notation.input.PitchEstimate;
import music.notation.transcribe.Keys;
import music.notation.transcribe.PitchTrack;
import music.notation.transcribe.QuantizedNote;
import music.notation.transcribe.SmoothingEdit;
import music.notation.transcribe.TranscribedNote;
import music.notation.transcribe.Transcription;

import java.util.Arrays;
import java.util.function.DoubleConsumer;

/**
 * Piano-roll view of one transcription, in file time:
 * <ul>
 *   <li>dots — the detector's pitch, frame by frame</li>
 *   <li>filled boxes — notes as segmented (real time)</li>
 *   <li>outlined boxes — the same notes after snapping to the grid</li>
 *   <li>vertical lines — bars and beats from the tempo and downbeat (red: the downbeat)</li>
 *   <li>bottom strip — loudness per frame</li>
 * </ul>
 * Wheel scrolls, Ctrl+wheel zooms, click sets the play position,
 * double-click sets the downbeat.
 */
final class PitchView extends BorderPane {

    private static final double GUTTER = 44, RULER = 18, LANE = 36;
    private static final Color BG = Color.web("#fbfbfd"), BLACK_KEY = Color.web("#eef0f5"),
            BEAT = Color.web("#e2e5ee"), BAR = Color.web("#a3abbd"), DOWNBEAT = Color.web("#d04646"),
            NOTE_FILL = Color.web("#3a6fd8", 0.22), NOTE_EDGE = Color.web("#3a6fd8"),
            GRID_NOTE = Color.web("#1f3f8f"), CURVE = Color.web("#d0582a"), LOUD = Color.web("#9aa3b5"),
            SEEK = Color.web("#2e8b57"), PLAY = Color.web("#e08a00"), TEXT = Color.web("#444a57"),
            EDIT = Color.web("#8e44ad");

    private final Canvas canvas = new Canvas();
    private final ScrollBar scroll = new ScrollBar();

    private Transcription t;
    private double durationSec = 10, originSec = 0;
    private double pxPerSec = 120, viewStart = 0;
    private double seekSec = Double.NaN, playSec = Double.NaN;
    private int lo = 55, hi = 72;
    private DoubleConsumer onSeek = s -> {}, onDownbeat = s -> {};

    PitchView() {
        Pane holder = new Pane(canvas);
        holder.setMinSize(200, 200);
        canvas.widthProperty().bind(holder.widthProperty());
        canvas.heightProperty().bind(holder.heightProperty());
        canvas.widthProperty().addListener(o -> { updateScroll(); redraw(); });
        canvas.heightProperty().addListener(o -> redraw());
        scroll.setOrientation(Orientation.HORIZONTAL);
        scroll.valueProperty().addListener((o, a, b) -> { viewStart = b.doubleValue(); redraw(); });
        setCenter(holder);
        setBottom(scroll);

        canvas.setOnScroll(e -> {
            if (e.isControlDown()) {
                double at = timeAt(e.getX());
                zoom(e.getDeltaY() > 0 ? 1.25 : 0.8);
                setViewStart(at - (e.getX() - GUTTER) / pxPerSec);
            } else {
                setViewStart(viewStart - e.getDeltaY() / pxPerSec);
            }
            e.consume();
        });
        canvas.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY || e.getX() < GUTTER) return;
            double sec = timeAt(e.getX());
            if (e.getClickCount() == 2) onDownbeat.accept(sec);
            else { seekSec = sec; onSeek.accept(sec); redraw(); }
        });
    }

    void setOnSeek(DoubleConsumer c) { onSeek = c; }
    void setOnDownbeat(DoubleConsumer c) { onDownbeat = c; }

    /** The audio's extent in file time. */
    void setAudioExtent(double originSec, double durationSec) {
        this.originSec = originSec;
        this.durationSec = Math.max(0.5, durationSec);
        updateScroll();
        setViewStart(originSec);
    }

    void setTranscription(Transcription t) {
        this.t = t;
        computeRange();
        redraw();
    }

    void setPlayPosition(double sec) { playSec = sec; redraw(); }

    double seekSec() { return seekSec; }

    void zoom(double factor) {
        pxPerSec = Math.max(10, Math.min(2000, pxPerSec * factor));
        updateScroll();
        setViewStart(viewStart);
    }

    void fit() {
        pxPerSec = Math.max(10, (canvas.getWidth() - GUTTER) / durationSec);
        updateScroll();
        setViewStart(originSec);
    }

    /** Keep {@code sec} on screen while playing. */
    void follow(double sec) {
        double visible = visibleSec();
        if (sec < viewStart || sec > viewStart + visible * 0.9) setViewStart(sec - visible * 0.1);
    }

    private double visibleSec() { return Math.max(0.1, (canvas.getWidth() - GUTTER) / pxPerSec); }

    private void setViewStart(double s) {
        double max = Math.max(originSec, originSec + durationSec - visibleSec());
        viewStart = Math.max(originSec, Math.min(max, s));
        scroll.setValue(viewStart);
        redraw();
    }

    private void updateScroll() {
        double visible = visibleSec();
        scroll.setMin(originSec);
        scroll.setMax(Math.max(originSec, originSec + durationSec - visible));
        scroll.setVisibleAmount(Math.min(durationSec, visible));
        scroll.setUnitIncrement(visible / 20);
        scroll.setBlockIncrement(visible * 0.8);
    }

    private double timeAt(double x) { return viewStart + (x - GUTTER) / pxPerSec; }
    private double xOf(double sec) { return GUTTER + (sec - viewStart) * pxPerSec; }
    private double rollBottom() { return canvas.getHeight() - LANE; }
    private double rowH() { return (rollBottom() - RULER) / (hi - lo + 1); }
    /** y of the top edge of the row for fractional MIDI {@code m} (row centre at m). */
    private double yOf(double m) { return RULER + (hi + 0.5 - m) * rowH(); }

    private void computeRange() {
        if (t == null) return;
        double[] v = t.pitchTrack() == null ? new double[0] : t.pitchTrack().frames().stream()
                .filter(PitchEstimate::hasPitch).mapToDouble(PitchEstimate::midiFloat).sorted().toArray();
        int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE;
        for (TranscribedNote n : t.notes()) { a = Math.min(a, n.midi()); b = Math.max(b, n.midi()); }
        for (SmoothingEdit e : t.smoothing()) { a = Math.min(a, e.fromMidi()); b = Math.max(b, e.fromMidi()); }
        if (v.length > 0) {
            a = Math.min(a, (int) Math.floor(v[(int) (v.length * 0.02)]));
            b = Math.max(b, (int) Math.ceil(v[(int) (v.length * 0.98)]));
        }
        if (a > b) { a = 55; b = 72; }
        a -= 2; b += 2;
        while (b - a < 14) { a--; b++; }
        lo = Math.max(0, a); hi = Math.min(127, b);
    }

    private void redraw() {
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(BG);
        g.fillRect(0, 0, w, h);
        g.setFont(Font.font("System", 10));

        double rh = rowH();
        for (int m = lo; m <= hi; m++) {
            double y = yOf(m + 0.5);
            if (isBlack(m)) { g.setFill(BLACK_KEY); g.fillRect(GUTTER, y, w - GUTTER, rh); }
        }
        if (t != null) drawGrid(g, w);
        if (t != null) drawNotes(g);
        if (t != null && t.pitchTrack() != null) drawCurve(g, t.pitchTrack(), h);

        // Gutter labels drawn last so boxes never cover them.
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, GUTTER, h);
        g.setFill(TEXT);
        g.setTextAlign(TextAlignment.RIGHT);
        g.setTextBaseline(VPos.CENTER);
        for (int m = lo; m <= hi; m++) {
            if (rh >= 11 || m % 12 == 0) g.fillText(Keys.noteName(m, t == null ? null : t.key()), GUTTER - 4, yOf(m + 0.5) + rh / 2);
        }
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText("loud", 4, rollBottom() + LANE / 2);

        drawLine(g, seekSec, SEEK, h);
        drawLine(g, playSec, PLAY, h);
    }

    private void drawGrid(GraphicsContext g, double w) {
        BarClock clock = BarClock.of(t);
        int beats = t.request().timing().timeSignature().beats();
        int firstBar = (int) Math.floor((viewStart - clock.barOneSec()) / clock.barSec()) + 1;
        g.setTextBaseline(VPos.TOP);
        g.setTextAlign(TextAlignment.LEFT);
        for (int bar = firstBar; ; bar++) {
            double bx = xOf(clock.barStartSec(bar));
            if (bx > w) break;
            for (int beat = 1; beat < beats; beat++) {
                double x = xOf(clock.barStartSec(bar) + beat * clock.beatSec());
                g.setStroke(BEAT); g.setLineWidth(1);
                g.strokeLine(x, RULER, x, rollBottom());
            }
            if (bx >= GUTTER) {
                g.setStroke(BAR); g.setLineWidth(1);
                g.strokeLine(bx, RULER, bx, rollBottom());
                if (bar >= 1) { g.setFill(TEXT); g.fillText(Integer.toString(bar), bx + 3, 3); }
            }
        }
        double dx = xOf(t.downbeatSec());
        if (dx >= GUTTER) {
            g.setStroke(DOWNBEAT); g.setLineWidth(1.5);
            g.strokeLine(dx, RULER, dx, rollBottom());
        }
    }

    private void drawNotes(GraphicsContext g) {
        double rh = rowH();
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.LEFT);
        for (TranscribedNote n : t.notes()) {
            double x0 = xOf(n.startMs() / 1000), x1 = xOf(n.endMs() / 1000), y = yOf(n.midi() + 0.5);
            g.setFill(NOTE_FILL); g.fillRect(x0, y, x1 - x0, rh);
            g.setStroke(NOTE_EDGE); g.setLineWidth(1); g.strokeRect(x0, y, x1 - x0, rh);
        }
        drawEdits(g, rh);
        BarClock clock = BarClock.of(t);
        g.setLineDashes(4, 3);
        for (QuantizedNote q : t.quantized()) {
            double x0 = xOf(clock.fileSec(q.at())), x1 = xOf(clock.fileSec(q.at().plus(q.duration())));
            double y = yOf(q.midi() + 0.5);
            g.setStroke(GRID_NOTE); g.setLineWidth(1.5);
            g.strokeRect(x0 + 1, y + 1, Math.max(1, x1 - x0 - 2), Math.max(1, rh - 2));
            if (x1 - x0 > 34 && rh >= 10) {
                g.setFill(GRID_NOTE);
                g.fillText(String.format("%s %+.0f", Keys.noteName(q.midi(), t.key()), q.cents()), x0 + 4, y + rh / 2);
            }
        }
        g.setLineDashes();
    }

    /** Where smoothing moved a note: a dotted ghost at the sung pitch, an arrow to where it went. */
    private void drawEdits(GraphicsContext g, double rh) {
        g.setStroke(EDIT); g.setFill(EDIT); g.setLineWidth(1.2);
        for (SmoothingEdit e : t.smoothing()) {
            double x0 = xOf(e.startMs() / 1000), x1 = xOf(e.endMs() / 1000);
            double yFrom = yOf(e.fromMidi() + 0.5), yTo = yOf(e.toMidi() + 0.5);
            g.setLineDashes(2, 2);
            g.strokeRect(x0, yFrom, Math.max(2, x1 - x0), rh);
            g.setLineDashes();
            double xm = (x0 + x1) / 2, ya = yFrom + rh / 2, yb = yTo + rh / 2;
            g.strokeLine(xm, ya, xm, yb);
            double tip = Math.signum(yb - ya) * Math.min(5, Math.abs(yb - ya));
            g.fillPolygon(new double[]{xm - 3, xm + 3, xm}, new double[]{yb - tip, yb - tip, yb}, 3);
            if (rh >= 10) {
                g.fillText(e.kind().name().toLowerCase(java.util.Locale.ROOT), x1 + 3, ya);
            }
        }
    }

    private void drawCurve(GraphicsContext g, PitchTrack track, double h) {
        double maxRms = 0;
        for (PitchEstimate f : track.frames()) maxRms = Math.max(maxRms, f.rms());
        double laneTop = rollBottom();
        g.setStroke(Color.web("#d5d9e3")); g.setLineWidth(1);
        g.strokeLine(GUTTER, laneTop, canvas.getWidth(), laneTop);
        double from = viewStart - 0.05, to = viewStart + visibleSec() + 0.05;
        double barW = Math.max(1, track.hopMs() / 1000 * pxPerSec);
        for (PitchEstimate f : track.frames()) {
            double sec = f.tickMs() / 1000.0;
            if (sec < from || sec > to) continue;
            double x = xOf(sec);
            if (maxRms > 0) {
                double hgt = (LANE - 6) * Math.sqrt(f.rms() / maxRms);
                g.setFill(LOUD);
                g.fillRect(x - barW / 2, h - 3 - hgt, barW, hgt);
            }
            if (f.hasPitch() && f.midiFloat() >= lo - 0.5 && f.midiFloat() <= hi + 0.5) {
                double y = yOf(f.midiFloat());
                g.setFill(CURVE);
                g.fillOval(x - 1.5, y - 1.5, 3, 3);
            }
        }
    }

    private void drawLine(GraphicsContext g, double sec, Color c, double h) {
        if (Double.isNaN(sec)) return;
        double x = xOf(sec);
        if (x < GUTTER || x > canvas.getWidth()) return;
        g.setStroke(c); g.setLineWidth(1.5);
        g.strokeLine(x, 0, x, h);
    }

    private static boolean isBlack(int midi) {
        return Arrays.binarySearch(new int[]{1, 3, 6, 8, 10}, Math.floorMod(midi, 12)) >= 0;
    }
}
