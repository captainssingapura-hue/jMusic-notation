package music.notation.mixer;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-track audio preview for the mixer. One {@link MediaPlayer} per
 * loaded track, all started at the same time so the user can audibly
 * verify alignment alongside the visual waveform check.
 *
 * <h2>Synchronisation</h2>
 *
 * <p>JavaFX {@link MediaPlayer} is single-stream, so we run N of them in
 * parallel. They're started in one tight loop after their {@code READY}
 * state, which keeps the start offsets well under one frame at typical
 * sample rates. Per-track gain is reflected by binding each
 * {@code MediaPlayer.volume} to the track's gain slider.</p>
 *
 * <h2>Cursor</h2>
 *
 * <p>A vertical line sweeps across the waveform area. Its x-position is
 * derived each frame from the first player's {@code currentTime} mapped
 * through the {@link TimeAxis}. The cursor canvas spans the full overlay;
 * we paint only the vertical line and clear the rest.</p>
 *
 * <h2>Not a mixer at playback time</h2>
 *
 * <p>Multiple {@code MediaPlayer}s output to the system mixer
 * independently — the OS audio engine sums them. That's good enough for
 * preview but is not bit-identical to the file mixer
 * ({@link music.notation.play.AudioMixer}), which does its own integer
 * sum-and-clip. For deliverable audio, always go through the export path.</p>
 */
public final class PlaybackController {

    /** Pixel offset from the left edge of the cursor canvas where the
     *  waveform area begins (matches TrackRow.CONTROLS_WIDTH). */
    private static final double WAVEFORM_LEFT_OFFSET = 280.0;
    private static final Color  CURSOR_COLOR = Color.web("#8b3a3a");

    private final List<MixTrack> tracks;
    private final TimeAxis axis;
    private final Canvas cursorCanvas;
    private final List<MediaPlayer> players = new ArrayList<>();
    private final AnimationTimer cursorTimer;
    private boolean playing = false;

    public PlaybackController(List<MixTrack> tracks, TimeAxis axis, Canvas cursorCanvas) {
        this.tracks = List.copyOf(tracks);
        this.axis = axis;
        this.cursorCanvas = cursorCanvas;

        for (MixTrack t : this.tracks) {
            Media media = new Media(t.file().toURI().toString());
            MediaPlayer mp = new MediaPlayer(media);
            mp.setVolume(currentVolume(t));
            // Re-bind volume whenever gain or muted changes (handles slider drag during playback).
            t.gainProperty().addListener((obs, oldV, newV) -> mp.setVolume(currentVolume(t)));
            t.mutedProperty().addListener((obs, oldV, newV) -> mp.setVolume(currentVolume(t)));
            players.add(mp);
        }

        cursorTimer = new AnimationTimer() {
            @Override public void handle(long now) { repaintCursor(); }
        };
    }

    /** Re-apply current gain/mute values to each MediaPlayer; called before each play. */
    public void refreshGainBindings() {
        for (int i = 0; i < tracks.size(); i++) {
            players.get(i).setVolume(currentVolume(tracks.get(i)));
        }
    }

    public void play() {
        if (playing) return;
        playing = true;
        // Wait for all players to reach READY before starting them in a tight loop.
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(players.size());
        for (MediaPlayer mp : players) {
            if (mp.getStatus() == MediaPlayer.Status.READY
                    || mp.getStatus() == MediaPlayer.Status.PAUSED
                    || mp.getStatus() == MediaPlayer.Status.STOPPED
                    || mp.getStatus() == MediaPlayer.Status.PLAYING) {
                if (remaining.decrementAndGet() == 0) startAll();
            } else {
                mp.setOnReady(() -> {
                    if (remaining.decrementAndGet() == 0) startAll();
                });
            }
        }
    }

    private void startAll() {
        for (MediaPlayer mp : players) {
            mp.seek(Duration.ZERO);
            mp.play();
        }
        cursorTimer.start();
    }

    public void seek(long micros) {
        Duration d = Duration.millis(micros / 1000.0);
        for (MediaPlayer mp : players) {
            mp.seek(d);
        }
        repaintCursor();
    }

    public void stop() {
        if (!playing) return;
        playing = false;
        for (MediaPlayer mp : players) {
            mp.stop();
        }
        cursorTimer.stop();
        clearCursor();
    }

    public void dispose() {
        stop();
        for (MediaPlayer mp : players) {
            try { mp.dispose(); } catch (Exception ignored) {}
        }
    }

    // ── Cursor painting ───────────────────────────────────────────────

    private void repaintCursor() {
        GraphicsContext g = cursorCanvas.getGraphicsContext2D();
        double w = cursorCanvas.getWidth();
        double h = cursorCanvas.getHeight();
        if (w <= 0 || h <= 0) return;
        g.clearRect(0, 0, w, h);

        if (players.isEmpty()) return;
        // Use the first non-stopped player as the time source.
        MediaPlayer src = players.get(0);
        Duration t = src.getCurrentTime();
        if (t == null) return;
        long micros = (long) (t.toMillis() * 1000.0);
        double waveAreaWidth = w - WAVEFORM_LEFT_OFFSET;
        if (waveAreaWidth <= 0) return;

        // The TimeAxis spans the waveform area; we add the control-panel offset.
        double pixelInArea = axis.microsToPixel(micros);
        double x = WAVEFORM_LEFT_OFFSET + pixelInArea;
        if (x < WAVEFORM_LEFT_OFFSET || x > w) return;

        g.setStroke(CURSOR_COLOR);
        g.setLineWidth(1.5);
        g.strokeLine(x, 0, x, h);
    }

    private void clearCursor() {
        GraphicsContext g = cursorCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, cursorCanvas.getWidth(), cursorCanvas.getHeight());
    }

    private static double currentVolume(MixTrack t) {
        // MediaPlayer volume is [0, 1], with our gain in [0, 2].
        // Clamp to [0, 1] for the preview; AudioMixer respects the full
        // [0, 4] gain range for the actual exported mix.
        return Math.max(0.0, Math.min(1.0, t.gainProperty().get())) * (t.mutedProperty().get() ? 0.0 : 1.0);
    }
}
