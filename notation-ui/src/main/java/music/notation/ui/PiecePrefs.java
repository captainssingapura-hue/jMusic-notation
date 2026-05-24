package music.notation.ui;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Per-piece persistence helper for the user's per-track choices —
 * pedal-source mode per track and export-exclusion set.
 *
 * <h2>What this owns</h2>
 *
 * <p>Pure serialisation logic for two maps into a {@link Preferences}
 * sub-node. Holds no state of its own; the caller provides the node and
 * the maps to read/write. Stays free of NotationApp coupling so the
 * round-trip can be unit-tested against an isolated Preferences instance.</p>
 *
 * <h2>Format</h2>
 *
 * <ul>
 *   <li>{@code pedal} → {@code "trackA=AUTO\ntrackB=SOURCE\ntrackC=OFF"}.
 *       Stale enum values are silently skipped on read.</li>
 *   <li>{@code export.exclude} → {@code "trackA\ntrackB"}.</li>
 *   <li>{@code title} → optional human-readable sidecar so a future
 *       cleanup tool can list which pieces have settings.</li>
 * </ul>
 *
 * <p>Both keys are removed when their corresponding map is empty so the
 * stored shape stays clean — no orphan empty strings.</p>
 *
 * <h2>Why per piece</h2>
 *
 * <p>Different pieces have different track rosters; a global per-track
 * setting would be meaningless (the names don't match). The user's
 * "drums off for export" choice for piece A should not bleed into
 * piece B's export dialog.</p>
 */
final class PiecePrefs {

    static final String KEY_PEDAL          = "pedal";
    static final String KEY_EXPORT_EXCLUDE = "export.exclude";
    static final String KEY_TITLE          = "title";

    private PiecePrefs() {}

    /**
     * Build a stable, prefs-safe node-name segment from a piece's display
     * name / title. Uses a non-cryptographic hash so the key is
     * length-bounded and free of characters Preferences node names can't
     * carry. Collisions are theoretically possible but vanishingly rare
     * for any one user's library.
     */
    static String nodeNameFor(String pieceDisplayName) {
        if (pieceDisplayName == null) return "p_unknown";
        return "p_" + Integer.toHexString(pieceDisplayName.hashCode());
    }

    /**
     * Read pedal-mode-per-track for the piece at {@code pieceNode}.
     * Returns an empty map when no entries are stored or all stored
     * values are stale enums.
     */
    static Map<String, ControlsPanel.PedalMode> readPedalMap(Preferences pieceNode) {
        Map<String, ControlsPanel.PedalMode> out = new LinkedHashMap<>();
        String raw = pieceNode.get(KEY_PEDAL, "");
        if (raw.isBlank()) return out;
        for (String line : raw.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0 || eq >= line.length() - 1) continue;
            String name = line.substring(0, eq);
            String modeName = line.substring(eq + 1);
            try {
                out.put(name, ControlsPanel.PedalMode.valueOf(modeName));
            } catch (IllegalArgumentException ignored) {
                // Stale value (e.g. a renamed enum constant) — silently skip.
            }
        }
        return out;
    }

    /**
     * Read export-exclusion track names for the piece at {@code pieceNode}.
     * Returns an empty set when no entries are stored.
     */
    static Set<String> readExcludeSet(Preferences pieceNode) {
        Set<String> out = new LinkedHashSet<>();
        String raw = pieceNode.get(KEY_EXPORT_EXCLUDE, "");
        if (raw.isBlank()) return out;
        for (String line : raw.split("\n")) {
            if (!line.isBlank()) out.add(line.trim());
        }
        return out;
    }

    /**
     * Write the per-track pedal map for the piece at {@code pieceNode}.
     * An empty map removes the key entirely, leaving the stored shape
     * clean.
     */
    static void writePedalMap(Preferences pieceNode,
                              Map<String, ControlsPanel.PedalMode> map) {
        if (map == null || map.isEmpty()) {
            pieceNode.remove(KEY_PEDAL);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : map.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(entry.getKey()).append('=').append(entry.getValue().name());
        }
        pieceNode.put(KEY_PEDAL, sb.toString());
    }

    /**
     * Write the export-exclusion set for the piece at {@code pieceNode}.
     * An empty set removes the key entirely.
     */
    static void writeExcludeSet(Preferences pieceNode, Set<String> excluded) {
        if (excluded == null || excluded.isEmpty()) {
            pieceNode.remove(KEY_EXPORT_EXCLUDE);
            return;
        }
        pieceNode.put(KEY_EXPORT_EXCLUDE, String.join("\n", excluded));
    }

    /**
     * Set the optional human-readable title sidecar so future cleanup
     * tooling can show which pieces have settings.
     */
    static void writeTitle(Preferences pieceNode, String title) {
        if (title == null || title.isBlank()) return;
        pieceNode.put(KEY_TITLE, title);
    }
}
