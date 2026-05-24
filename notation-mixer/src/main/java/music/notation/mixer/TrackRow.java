package music.notation.mixer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * One horizontal track lane in the mixer view. Left side carries the
 * track metadata + controls (filename, mute, gain slider, remove
 * button); right side is the {@link WaveformView} painted against the
 * shared {@link TimeAxis}.
 *
 * <h2>Layout</h2>
 *
 * <pre>
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │ file.wav      ☐ Mute   ─●─── gain   [X]  │  ╱╲╱╲╱╲╱╲╱╲╱╲╱╲ │
 *   └─────────────────────────────────────────────────────────────┘
 *      ◄─ control panel (fixed width) ─►       ◄─ waveform area ─►
 * </pre>
 *
 * <p>The control panel has a fixed width across all rows so waveform
 * areas line up vertically — that's the alignment-verification surface.</p>
 */
public final class TrackRow extends BorderPane {

    private static final double CONTROLS_WIDTH = 280;
    private static final double ROW_HEIGHT     = 96;

    private final MixTrack track;
    private final WaveformView waveformView;

    private final TimeAxis axis;
    private final LongConsumer onSeek;

    public TrackRow(MixTrack track, TimeAxis axis, Color tint,
                    Consumer<MixTrack> onRemove, LongConsumer onSeek) {
        this.track = track;
        this.axis = axis;
        this.onSeek = onSeek;
        this.waveformView = new WaveformView(track.waveform(), axis);
        waveformView.setWaveformColor(tint);
        waveformView.mutedProperty().bind(track.mutedProperty());

        setPrefHeight(ROW_HEIGHT);
        setMinHeight(ROW_HEIGHT);
        setStyle("-fx-border-color: #d4cfc0; -fx-border-width: 0 0 1 0;");

        setLeft(buildControls(onRemove));
        setCenter(buildWaveformPane());
    }

    public MixTrack    track()        { return track; }
    public WaveformView waveformView() { return waveformView; }

    private VBox buildControls(Consumer<MixTrack> onRemove) {
        Label filename = new Label(track.displayName());
        filename.setFont(Font.font("System", FontWeight.BOLD, 12));
        filename.setMaxWidth(CONTROLS_WIDTH - 24);

        Label duration = new Label(formatDuration(track.waveform().durationMicros()));
        duration.setStyle("-fx-text-fill: #777;");
        duration.setFont(Font.font(10));

        CheckBox muteBox = new CheckBox("Mute");
        muteBox.selectedProperty().bindBidirectional(track.mutedProperty());

        Slider gainSlider = new Slider(0.0, 2.0, track.gainProperty().get());
        gainSlider.setMajorTickUnit(0.5);
        gainSlider.setShowTickMarks(true);
        gainSlider.setPrefWidth(CONTROLS_WIDTH - 60);
        gainSlider.valueProperty().bindBidirectional(track.gainProperty());

        Label gainLabel = new Label();
        gainLabel.setFont(Font.font(10));
        gainLabel.setStyle("-fx-text-fill: #555;");
        track.gainProperty().addListener((obs, oldV, newV) ->
                gainLabel.setText(String.format("gain %.2f", newV.doubleValue())));
        gainLabel.setText(String.format("gain %.2f", track.gainProperty().get()));

        Button removeBtn = new Button("✕");
        removeBtn.setStyle("-fx-background-radius: 12; -fx-font-size: 10;");
        removeBtn.setOnAction(e -> onRemove.accept(track));

        HBox titleRow = new HBox(8, filename);
        HBox.setHgrow(filename, Priority.ALWAYS);

        HBox muteRow = new HBox(8, muteBox, spacer(), removeBtn);
        muteRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(4, titleRow, duration, muteRow, gainSlider, gainLabel);
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setPrefWidth(CONTROLS_WIDTH);
        box.setMinWidth(CONTROLS_WIDTH);
        box.setMaxWidth(CONTROLS_WIDTH);
        box.setStyle("-fx-background-color: #f4f1e8; -fx-border-color: #d4cfc0; -fx-border-width: 0 1 0 0;");
        return box;
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private javafx.scene.layout.StackPane buildWaveformPane() {
        // Wrap the canvas in a StackPane so it resizes to fill the available area.
        var pane = new javafx.scene.layout.StackPane(waveformView);
        pane.setStyle("-fx-background-color: #fffef9;");
        pane.widthProperty().addListener((obs, oldV, newV) -> waveformView.setWidth(newV.doubleValue()));
        pane.heightProperty().addListener((obs, oldV, newV) -> waveformView.setHeight(newV.doubleValue()));
        // Click anywhere on the waveform to seek.
        pane.setOnMouseClicked(e -> {
            if (onSeek == null) return;
            double micros = axis.pixelToMicros(e.getX());
            onSeek.accept((long) micros);
        });
        return pane;
    }

    private static String formatDuration(long micros) {
        long totalSeconds = micros / 1_000_000L;
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
