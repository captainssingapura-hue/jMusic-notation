package music.notation.guitar;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Where the music comes from.
 *
 * <pre>{@code
 *   { "type": "library", "title": "春晓 / 静夜思 (Spring Dawn / Quiet Night Thoughts)", "variant": "Super Blue in B♭" }
 *   { "type": "folder",  "path": "R:/Music_works/MusicXml/VA/json/Some_Piece" }
 * }</pre>
 *
 * <p>{@code variant} names a provider by its subtitle, or by 0-based
 * index when it parses as an integer; empty means the first.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PieceSource.Library.class, name = "library"),
        @JsonSubTypes.Type(value = PieceSource.Folder.class,  name = "folder")
})
public sealed interface PieceSource {

    /** A piece from {@code music.notation.songs.PieceLibrary}, by title. */
    record Library(String title, String variant) implements PieceSource {
        public Library {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
            variant = variant == null ? "" : variant;
        }
    }

    /** A split-JSON piece folder as written by the importer and the other apps. */
    record Folder(String path) implements PieceSource {
        public Folder {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("path required");
        }
    }
}
