package music.notation.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import music.notation.expressivity.TrackId;
import music.notation.performance.Track;
import music.notation.performance.TrackKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reusable modal dialog showing a list of tracks with a per-row control
 * widget. The widget shape is supplied by the caller, so the same dialog
 * serves multiple use cases — "include in export?" (boolean), "pedal
 * source?" (enum), and any future per-track configuration.
 *
 * <h2>Design</h2>
 *
 * <p>Mouse-travel minimisation: instead of a permanent Tracks panel that
 * takes screen real estate, this dialog opens on demand near the action
 * that triggered it (the export button, the pedal button, ...). The user
 * confirms their per-track settings, hits OK, and the dialog disappears.</p>
 *
 * <p>The dialog is generic over the per-track value type {@code <T>}.
 * Callers supply a {@link RowFactory} that builds a {@link Row} for each
 * track; the dialog handles layout, the OK/Cancel buttons, and result
 * aggregation. Two pre-built factories — {@link #booleanFactory} and
 * {@link #choiceFactory} — cover the common cases.</p>
 *
 * <h2>Usage example</h2>
 *
 * <pre>{@code
 * Optional<Map<TrackId, Boolean>> result = TrackSelectorDialog.showAndWait(
 *         owner,
 *         "Export tracks",
 *         "Choose which tracks to include in the exported file.",
 *         piece.tracks(),
 *         currentIncludeMap,
 *         TrackSelectorDialog.booleanFactory("Include"));
 * }</pre>
 *
 * <p>For a choice-style row (e.g. pedal mode), pass
 * {@link #choiceFactory(List, Function)} with the available options.</p>
 */
public final class TrackSelectorDialog {

    private TrackSelectorDialog() {}

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Show the modal dialog. Returns the per-track values on OK; empty on
     * Cancel or close.
     *
     * @param owner       parent stage (null is acceptable but the dialog
     *                    won't be modal-blocking against any specific window)
     * @param title       window title
     * @param subtitle    short prose under the title; null hides the line
     * @param tracks      tracks to list, in display order
     * @param initial     starting values keyed by TrackId; missing entries
     *                    fall back to the row factory's default
     * @param rowFactory  builds the per-row control widget
     * @param <T>         per-track value type
     */
    public static <T> Optional<Map<TrackId, T>> showAndWait(
            Stage owner,
            String title,
            String subtitle,
            List<Track> tracks,
            Map<TrackId, T> initial,
            RowFactory<T> rowFactory) {
        Objects.requireNonNull(tracks, "tracks");
        Objects.requireNonNull(rowFactory, "rowFactory");
        Map<TrackId, T> safeInitial = initial == null ? Map.of() : initial;

        Dialog<Map<TrackId, T>> dialog = new Dialog<>();
        dialog.setTitle(title);
        if (owner != null) dialog.initOwner(owner);
        dialog.setResizable(true);

        // ── Header
        Label heading = new Label(title);
        heading.setFont(Font.font("System", FontWeight.BOLD, 14));
        VBox header = new VBox(heading);
        if (subtitle != null && !subtitle.isBlank()) {
            Label sub = new Label(subtitle);
            sub.setStyle("-fx-text-fill: #555; -fx-font-size: 11;");
            sub.setWrapText(true);
            header.getChildren().add(sub);
        }
        header.setSpacing(4);
        header.setPadding(new Insets(0, 0, 10, 0));

        // ── Track rows
        List<TrackRowBinding<T>> rowBindings = new ArrayList<>(tracks.size());
        ListView<Node> listView = new ListView<>();
        listView.setMinHeight(180);
        listView.setPrefHeight(Math.min(420, 32 + tracks.size() * 36));

        for (Track t : tracks) {
            T startValue = safeInitial.getOrDefault(t.id(), rowFactory.defaultValue(t));
            Row<T> row = rowFactory.create(t, startValue);
            Node node = buildRowNode(t, row);
            listView.getItems().add(node);
            rowBindings.add(new TrackRowBinding<>(t.id(), row));
        }

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Node item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty ? null : item);
            }
        });

        BorderPane body = new BorderPane();
        body.setTop(header);
        body.setCenter(listView);
        body.setPadding(new Insets(14, 14, 0, 14));
        body.setPrefWidth(460);

        // ── Optional "set all" affordance
        Node setAllNode = rowFactory.setAllNode(rowBindings.stream().map(TrackRowBinding::row).toList());
        if (setAllNode != null) {
            HBox bar = new HBox(8, setAllNode);
            bar.setAlignment(Pos.CENTER_LEFT);
            bar.setPadding(new Insets(8, 0, 0, 0));
            VBox bottom = new VBox(bar);
            body.setBottom(bottom);
        }

        dialog.getDialogPane().setContent(body);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // ── Result converter
        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            Map<TrackId, T> result = new LinkedHashMap<>();
            for (TrackRowBinding<T> b : rowBindings) {
                result.put(b.id(), b.row().value());
            }
            return result;
        });

        return Optional.ofNullable(dialog.showAndWait().orElse(null));
    }

    // ── Row interface + node building ─────────────────────────────────

    /**
     * One row's control widget plus its read-back value.
     *
     * <p>Implementations should not need to call OK / Cancel buttons or
     * the dialog itself — the dialog reads {@link #value()} only when the
     * user confirms.</p>
     */
    public interface Row<T> {
        /** The control(s) that go on the right side of the row. */
        Node control();

        /** Current value at any time; the dialog reads this on OK. */
        T value();
    }

    /**
     * Factory for building rows. {@code defaultValue} is consulted when
     * the {@code initial} map doesn't carry a value for a given track.
     * {@code setAllNode} returns null when no bulk affordance is wanted.
     */
    public interface RowFactory<T> {
        Row<T> create(Track track, T currentValue);
        T defaultValue(Track track);

        /**
         * Optional "set all" widget shown below the list. Receives every
         * row's mutable handle so it can push a single value across.
         * Default: no bulk widget.
         */
        default Node setAllNode(List<Row<T>> rows) { return null; }
    }

    private static <T> Node buildRowNode(Track track, Row<T> row) {
        Label name = new Label(track.id().name());
        name.setFont(Font.font("System", FontWeight.BOLD, 12));
        name.setMaxWidth(220);

        Label hint = new Label(kindHint(track.kind()));
        hint.setStyle("-fx-text-fill: #888; -fx-font-size: 10;");

        VBox left = new VBox(2, name, hint);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row1 = new HBox(8, left, spacer, row.control());
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPadding(new Insets(6, 6, 6, 6));
        row1.setMinHeight(34);
        return row1;
    }

    private static String kindHint(TrackKind kind) {
        if (kind == null) return "";
        return switch (kind) {
            case PITCHED -> "pitched track";
            case DRUM    -> "drum track";
        };
    }

    private record TrackRowBinding<T>(TrackId id, Row<T> row) {}

    // ══════════════════════════════════════════════════════════════════
    //  Pre-built row factories for common cases
    // ══════════════════════════════════════════════════════════════════

    /**
     * Boolean (checkbox) row factory. The checkbox label is the supplied
     * text (e.g. "Include"); default value is {@code true}.
     *
     * <p>Includes a "Set all" toggle line that flips every row at once.</p>
     */
    public static RowFactory<Boolean> booleanFactory(String label) {
        return new RowFactory<>() {
            @Override
            public Row<Boolean> create(Track track, Boolean currentValue) {
                CheckBox box = new CheckBox(label);
                box.setSelected(currentValue != null && currentValue);
                return new Row<>() {
                    @Override public Node control() { return box; }
                    @Override public Boolean value() { return box.isSelected(); }
                };
            }
            @Override public Boolean defaultValue(Track track) { return true; }
            @Override
            public Node setAllNode(List<Row<Boolean>> rows) {
                Button allOn = new Button("Select all");
                Button allOff = new Button("Clear all");
                allOn.setOnAction(e -> {
                    for (Row<Boolean> r : rows) {
                        if (r.control() instanceof CheckBox cb) cb.setSelected(true);
                    }
                });
                allOff.setOnAction(e -> {
                    for (Row<Boolean> r : rows) {
                        if (r.control() instanceof CheckBox cb) cb.setSelected(false);
                    }
                });
                HBox bar = new HBox(6, allOn, allOff);
                bar.setAlignment(Pos.CENTER_LEFT);
                return bar;
            }
        };
    }

    /**
     * Choice (dropdown) row factory. Builds a {@link ChoiceBox} per row
     * with the given options and a label function for display.
     *
     * <p>{@code disabledFor} lets the caller grey out the row for tracks
     * where the choice isn't applicable (e.g. pedal mode for drum tracks).
     * Pass {@code track -> false} to enable for all tracks.</p>
     */
    public static <T> RowFactory<T> choiceFactory(List<T> options,
                                                   Function<T, String> labelFn,
                                                   T defaultValue,
                                                   Function<Track, Boolean> disabledFor) {
        return new RowFactory<>() {
            @Override
            public Row<T> create(Track track, T currentValue) {
                ChoiceBox<T> box = new ChoiceBox<>();
                box.getItems().addAll(options);
                box.setConverter(new StringConverter<>() {
                    @Override public String toString(T v) { return v == null ? "" : labelFn.apply(v); }
                    @Override public T fromString(String s) { return null; }
                });
                box.setValue(currentValue != null ? currentValue : defaultValue);
                if (disabledFor != null && disabledFor.apply(track)) {
                    box.setDisable(true);
                }
                return new Row<>() {
                    @Override public Node control() { return box; }
                    @Override public T value() { return box.getValue(); }
                };
            }
            @Override public T defaultValue(Track track) { return defaultValue; }
            @Override
            public Node setAllNode(List<Row<T>> rows) {
                ChoiceBox<T> setAll = new ChoiceBox<>();
                setAll.getItems().addAll(options);
                setAll.setConverter(new StringConverter<>() {
                    @Override public String toString(T v) { return v == null ? "Set all to…" : labelFn.apply(v); }
                    @Override public T fromString(String s) { return null; }
                });
                Button apply = new Button("Set all");
                apply.setOnAction(e -> {
                    T pick = setAll.getValue();
                    if (pick == null) return;
                    for (Row<T> r : rows) {
                        if (r.control() instanceof ChoiceBox<?> cb && !cb.isDisabled()) {
                            @SuppressWarnings("unchecked")
                            ChoiceBox<T> typed = (ChoiceBox<T>) cb;
                            typed.setValue(pick);
                        }
                    }
                });
                HBox bar = new HBox(6, setAll, apply);
                bar.setAlignment(Pos.CENTER_LEFT);
                return bar;
            }
        };
    }

    /**
     * Choice (dropdown) factory without per-track disable logic. Convenience
     * overload — equivalent to {@link #choiceFactory(List, Function, Object, Function)}
     * with all tracks enabled.
     */
    public static <T> RowFactory<T> choiceFactory(List<T> options,
                                                   Function<T, String> labelFn,
                                                   T defaultValue) {
        return choiceFactory(options, labelFn, defaultValue, track -> false);
    }
}
