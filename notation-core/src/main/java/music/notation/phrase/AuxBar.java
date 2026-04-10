package music.notation.phrase;

import java.util.List;

/**
 * Auxiliary notes that play simultaneously with the main bar content.
 *
 * <p>An {@code AuxBar} holds secondary-voice content for a single bar.
 * It does not advance the main timeline. At playback time, aux bars are
 * collected into aux tracks on the parent {@link music.notation.structure.Track},
 * each interpreted as a separate MIDI track sharing the parent's instrument.</p>
 *
 * <p>Use this for secondary voices, counter-melodies, or short ornamental
 * fragments that appear alongside the main melody in the same staff,
 * without creating a separate track full of rests.</p>
 */
public record AuxBar(List<PhraseNode> nodes) {
    public AuxBar {
        nodes = List.copyOf(nodes);
    }
}
