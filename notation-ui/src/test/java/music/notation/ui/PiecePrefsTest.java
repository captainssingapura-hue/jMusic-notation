package music.notation.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip + edge-case checks for {@link PiecePrefs}. Uses an isolated
 * {@link Preferences} sub-node so the test never touches the real app's
 * stored settings. The node is cleared in {@code setUp} and {@code tearDown}
 * so each test starts and leaves the prefs surface clean.
 */
class PiecePrefsTest {

    /** Dedicated prefs sub-tree for tests, swept clean before and after each test. */
    private static final String TEST_ROOT = "test-piece-prefs";

    private Preferences testRoot;
    private Preferences sampleNode;

    @BeforeEach
    void setUp() throws Exception {
        testRoot = Preferences.userNodeForPackage(PiecePrefsTest.class).node(TEST_ROOT);
        testRoot.removeNode();   // clear any leftover state
        testRoot = Preferences.userNodeForPackage(PiecePrefsTest.class).node(TEST_ROOT);
        sampleNode = testRoot.node("sample");
    }

    @AfterEach
    void tearDown() throws Exception {
        // Recreate the parent fresh in case a test left grandchild nodes.
        Preferences.userNodeForPackage(PiecePrefsTest.class).node(TEST_ROOT).removeNode();
    }

    // ── nodeNameFor ───────────────────────────────────────────────────

    @Test
    void nodeNameForIsStableAcrossCalls() {
        String key1 = PiecePrefs.nodeNameFor("Sonata in C minor");
        String key2 = PiecePrefs.nodeNameFor("Sonata in C minor");
        assertEquals(key1, key2, "same input must yield the same node name");
    }

    @Test
    void nodeNameForDistinguishesDifferentTitles() {
        // Two unrelated titles should hash to different node names.
        // (Hash collision is theoretically possible but vanishingly rare.)
        String a = PiecePrefs.nodeNameFor("Sonata in C minor");
        String b = PiecePrefs.nodeNameFor("Etude in D major");
        assertNotEquals(a, b);
    }

    @Test
    void nodeNameForHandlesNull() {
        assertNotNull(PiecePrefs.nodeNameFor(null),
                "null input must yield a valid fallback node name");
    }

    @Test
    void nodeNameForIsPrefsSafe() {
        // Even titles with awkward characters must produce a node name made
        // entirely of [a-z0-9_-] — Preferences will reject anything else.
        String key = PiecePrefs.nodeNameFor("piece/with: bad\\chars\nand spaces");
        assertTrue(key.matches("[a-zA-Z0-9_-]+"),
                "node name must contain only prefs-safe chars: " + key);
    }

    // ── Pedal map round-trip ──────────────────────────────────────────

    @Test
    void pedalMap_emptyOnFreshNode() {
        Map<String, ControlsPanel.PedalMode> got = PiecePrefs.readPedalMap(sampleNode);
        assertTrue(got.isEmpty());
    }

    @Test
    void pedalMap_roundTripsThreeEntries() {
        Map<String, ControlsPanel.PedalMode> original = new LinkedHashMap<>();
        original.put("Lead",    ControlsPanel.PedalMode.AUTO);
        original.put("Harmony", ControlsPanel.PedalMode.SOURCE);
        original.put("Bass",    ControlsPanel.PedalMode.OFF);

        PiecePrefs.writePedalMap(sampleNode, original);
        Map<String, ControlsPanel.PedalMode> back = PiecePrefs.readPedalMap(sampleNode);

        assertEquals(original, back);
    }

    @Test
    void pedalMap_emptyMapRemovesKey() {
        PiecePrefs.writePedalMap(sampleNode, Map.of("Lead", ControlsPanel.PedalMode.AUTO));
        assertFalse(PiecePrefs.readPedalMap(sampleNode).isEmpty(),
                "precondition: map written non-empty");

        PiecePrefs.writePedalMap(sampleNode, Map.of());
        Map<String, ControlsPanel.PedalMode> back = PiecePrefs.readPedalMap(sampleNode);
        assertTrue(back.isEmpty(),
                "writing an empty map must remove the stored entry");
    }

    @Test
    void pedalMap_nullMapTreatedAsEmpty() {
        PiecePrefs.writePedalMap(sampleNode, Map.of("X", ControlsPanel.PedalMode.AUTO));
        PiecePrefs.writePedalMap(sampleNode, null);
        assertTrue(PiecePrefs.readPedalMap(sampleNode).isEmpty());
    }

    @Test
    void pedalMap_skipStaleEnumNames() throws Exception {
        // Write a raw value that includes a stale (non-enum) mode name;
        // the reader must skip just that line, not abort.
        sampleNode.put(PiecePrefs.KEY_PEDAL,
                "Lead=AUTO\nHarmony=DESTROYED\nBass=OFF");

        Map<String, ControlsPanel.PedalMode> back = PiecePrefs.readPedalMap(sampleNode);
        assertEquals(2, back.size(), "Harmony's stale value must be silently dropped");
        assertEquals(ControlsPanel.PedalMode.AUTO, back.get("Lead"));
        assertEquals(ControlsPanel.PedalMode.OFF,  back.get("Bass"));
        assertNull(back.get("Harmony"));
    }

    @Test
    void pedalMap_skipsMalformedLines() throws Exception {
        sampleNode.put(PiecePrefs.KEY_PEDAL,
                "Lead=AUTO\nno-equals-here\n=missing-name\nname-missing-mode=\nBass=OFF");

        Map<String, ControlsPanel.PedalMode> back = PiecePrefs.readPedalMap(sampleNode);
        assertEquals(2, back.size());
        assertEquals(ControlsPanel.PedalMode.AUTO, back.get("Lead"));
        assertEquals(ControlsPanel.PedalMode.OFF,  back.get("Bass"));
    }

    // ── Exclude set round-trip ────────────────────────────────────────

    @Test
    void excludeSet_emptyOnFreshNode() {
        assertTrue(PiecePrefs.readExcludeSet(sampleNode).isEmpty());
    }

    @Test
    void excludeSet_roundTripsTwoEntries() {
        Set<String> original = new LinkedHashSet<>();
        original.add("Drums");
        original.add("Bass");

        PiecePrefs.writeExcludeSet(sampleNode, original);
        Set<String> back = PiecePrefs.readExcludeSet(sampleNode);

        assertEquals(original, back);
    }

    @Test
    void excludeSet_emptySetRemovesKey() {
        PiecePrefs.writeExcludeSet(sampleNode, Set.of("Drums"));
        assertFalse(PiecePrefs.readExcludeSet(sampleNode).isEmpty());

        PiecePrefs.writeExcludeSet(sampleNode, Set.of());
        assertTrue(PiecePrefs.readExcludeSet(sampleNode).isEmpty());
    }

    // ── Title sidecar ─────────────────────────────────────────────────

    @Test
    void title_writesWhenPresent() {
        PiecePrefs.writeTitle(sampleNode, "Sonata in C minor");
        assertEquals("Sonata in C minor", sampleNode.get(PiecePrefs.KEY_TITLE, ""));
    }

    @Test
    void title_silentOnNullOrBlank() {
        PiecePrefs.writeTitle(sampleNode, null);
        assertEquals("", sampleNode.get(PiecePrefs.KEY_TITLE, ""));

        PiecePrefs.writeTitle(sampleNode, "   ");
        assertEquals("", sampleNode.get(PiecePrefs.KEY_TITLE, ""));
    }

    // ── Cross-piece isolation ─────────────────────────────────────────

    @Test
    void differentPieceNodes_keepChoicesSeparate() {
        Preferences pieceA = testRoot.node(PiecePrefs.nodeNameFor("Piece A"));
        Preferences pieceB = testRoot.node(PiecePrefs.nodeNameFor("Piece B"));

        PiecePrefs.writePedalMap(pieceA, Map.of("Lead", ControlsPanel.PedalMode.AUTO));
        PiecePrefs.writePedalMap(pieceB, Map.of("Lead", ControlsPanel.PedalMode.OFF));
        PiecePrefs.writeExcludeSet(pieceA, Set.of("Drums"));
        PiecePrefs.writeExcludeSet(pieceB, Set.of());

        assertEquals(ControlsPanel.PedalMode.AUTO,
                PiecePrefs.readPedalMap(pieceA).get("Lead"));
        assertEquals(ControlsPanel.PedalMode.OFF,
                PiecePrefs.readPedalMap(pieceB).get("Lead"));
        assertEquals(Set.of("Drums"), PiecePrefs.readExcludeSet(pieceA));
        assertTrue(PiecePrefs.readExcludeSet(pieceB).isEmpty());
    }
}
