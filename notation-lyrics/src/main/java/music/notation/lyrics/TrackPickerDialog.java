package music.notation.lyrics;

import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import music.notation.expressivity.TrackId;
import music.notation.mxl.MxlImport;
import music.notation.performance.PitchedNote;
import music.notation.performance.Track;
import music.notation.performance.TrackKind;
import music.notation.phrase.Monophony;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modal that asks which pitched track to attach lyrics to. Every
 * pitched track is selectable — the editor uses
 * {@link Monophony#extractTopLine} so even non-monophonic tracks (e.g.
 * vocal-to-MIDI exports with overlapping double-trigger notes, or
 * single-track piano scores with chords) get a usable melody line.
 *
 * <p>Each row shows the kept / dropped note counts so the user can
 * see at a glance whether the track is essentially monophonic (e.g.
 * {@code 142 notes (1 hidden)}) or a chord-heavy track where lyrics
 * will only follow the top line (e.g. {@code 87 notes (215 hidden)}).</p>
 */
public final class TrackPickerDialog {

    private TrackPickerDialog() {}

    private record TrackStat(TrackId id, int kept, int dropped) {
        int total() { return kept + dropped; }
        boolean isClean() { return dropped == 0; }
        String label() {
            if (isClean()) {
                return id.name() + "  ·  " + kept + " note" + (kept == 1 ? "" : "s");
            }
            return id.name() + "  ·  " + kept + " kept, " + dropped + " hidden as inner voice";
        }
    }

    public static Optional<TrackId> show(Window owner, MxlImport imp) {
        Dialog<TrackId> dialog = new Dialog<>();
        dialog.setTitle("Pick a track");
        dialog.setHeaderText("Lyrics attach to the top-line of the selected track.");
        if (owner != null) dialog.initOwner(owner);

        Map<TrackId, TrackStat> stats = new LinkedHashMap<>();
        for (Track t : imp.performance().score().tracks()) {
            if (t.kind() != TrackKind.PITCHED) continue;
            stats.put(t.id(), statOf(t));
        }

        ListView<TrackStat> list = new ListView<>(
                FXCollections.observableArrayList(stats.values()));
        list.setPrefHeight(220);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(TrackStat item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item.label());
                // Visually flag tracks where a lot of notes will be
                // hidden so the user knows their lyric grid won't match
                // the full source. Dim, but still selectable.
                if (!item.isClean() && item.dropped() * 2 > item.kept()) {
                    setStyle("-fx-text-fill: #888;");
                } else {
                    setStyle("");
                }
            }
        });
        // Default-select the first track that has any kept notes.
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i).kept() > 0) {
                list.getSelectionModel().select(i);
                break;
            }
        }

        Label hint = new Label(
                "All pitched tracks are selectable. The lyrics editor takes the\n"
                + "top-most sounding note at each instant as the melody —\n"
                + "lower / inner-voice notes are hidden from the lyric grid\n"
                + "but preserved in the source.");
        hint.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");

        VBox box = new VBox(8, list, hint);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                TrackStat pick = list.getSelectionModel().getSelectedItem();
                return pick != null && pick.kept() > 0 ? pick.id() : null;
            }
            return null;
        });
        return dialog.showAndWait();
    }

    /**
     * Run the top-line extractor in dry-run mode to count kept vs
     * dropped notes for the picker UI. Cheap (single linear scan), so
     * we run it for every pitched track at picker-show time without a
     * background thread.
     */
    private static TrackStat statOf(Track track) {
        List<PitchedNote> notes = new ArrayList<>();
        for (var n : track.notes()) {
            if (n instanceof PitchedNote pn) notes.add(pn);
        }
        Monophony.TopLine<PitchedNote> top = Monophony.extractTopLine(
                notes,
                PitchedNote::midi,
                PitchedNote::tickMs,
                pn -> pn.tickMs() + pn.durationMs());
        return new TrackStat(track.id(), top.melody().size(), top.dropped());
    }
}
