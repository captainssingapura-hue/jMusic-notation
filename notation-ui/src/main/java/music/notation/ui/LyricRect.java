package music.notation.ui;

/** A single timed syllable for the lyrics display. */
record LyricRect(long startTick, long endTick, String syllable) {}
