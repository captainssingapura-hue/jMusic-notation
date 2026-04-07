# Music Notation

A typed music notation system in Java using sealed interfaces (sum types) and records (product types), with phrasing as the primary organizational unit. Includes a JavaFX player with MIDI playback.

## Architecture

The project is a Maven multi-module build targeting Java 21.

```
notation-parent
 +-- notation-core     Core ADT: Piece, Track, Phrase, Pitch, Chord, Duration, ...
 +-- notation-play     MIDI mapping, playback, and PlayPiece utility
 +-- notation-songs    Song library, Collection registry, built-in pieces
 +-- notation-ui       JavaFX desktop player with piano-roll visualization
```

### Core ADT

The central abstraction is a **phrase-centric** decomposition:

```
Piece (title, composer, key, time signature, tempo)
  +-- Track* (named channel with instrument assignment)
        +-- Phrase* (sealed: MelodicPhrase | RestPhrase | ChordPhrase)
              +-- PhraseNode* (sealed: NoteNode | RestNode | SubPhrase | DynamicNode)
```

- **Sealed interfaces** model sum types (`Phrase`, `PhraseNode`, `Chord`)
- **Records** model product types (`Pitch`, `Duration`, `NoteNode`, `MajorTriad`, ...)
- **Phrases are recursive** via `SubPhrase`, enabling hierarchical musical structure
- **Dynamics are positional** -- `DynamicNode` markers flow inline among notes

### Chord ADT

A sealed `Chord` interface with records for common chord qualities:

| Type | Intervals |
|------|-----------|
| `MajorTriad` | root, M3, P5 |
| `MinorTriad` | root, m3, P5 |
| `DiminishedTriad` | root, m3, dim5 |
| `AugmentedTriad` | root, M3, aug5 |
| `DominantSeventh` | root, M3, P5, m7 |
| `MajorSeventh` | root, M3, P5, M7 |
| `MinorSeventh` | root, m3, P5, m7 |

Chord records compute correctly-spelled pitches via interval arithmetic (letter steps + semitone distance), so `MinorTriad(F#)` produces F#-A-C# rather than F#-G##-Db.

### Song Library

Songs are modelled with three interfaces:

- **`MusicalPiece`** -- identity record (title, composer)
- **`PieceContentProvider<P extends MusicalPiece>`** -- creates a `Piece` for that identity
- **`Collection`** -- groups pieces with their providers for registration

Multiple providers can exist for the same piece (e.g. different arrangements). `PieceLibrary` loads collections from an external JSON config file, making the registry fully extensible without recompilation.

## Building

```bash
mvn clean package
```

This produces an uber-jar at `notation-ui/target/notation-ui-1.0-SNAPSHOT.jar` containing all dependencies including JavaFX.

## Running

### From Maven (development)

```bash
mvn javafx:run -pl notation-ui
```

### From the uber-jar

```bash
java -Dmusic.collections=collections.json -jar notation-ui/target/notation-ui-1.0-SNAPSHOT.jar
```

The player opens with a list of available pieces. Select one to see a piano-roll visualization and control playback (play/pause, seek by dragging).

### Quick-play a single piece (no UI)

Any content provider can include a `main` method for quick testing:

```java
public static void main(String[] args) throws Exception {
    PlayPiece.play(new DefaultTwinkleStar());
}
```

Run it directly from your IDE -- no collections config or UI needed.

## Collections Config

The `-Dmusic.collections` system property points to a JSON file that maps collection names to fully-qualified class names:

```json
{
    "Built-in Songs": "music.notation.songs.DefaultCollection"
}
```

### Adding custom songs

1. Create your `MusicalPiece` identity record, `PieceContentProvider`, and `Collection` in a separate jar.
2. Add the collection to a JSON config file:
   ```json
   {
       "Built-in Songs": "music.notation.songs.DefaultCollection",
       "My Songs": "com.example.MyCollection"
   }
   ```
3. Run with both jars on the classpath:
   ```bash
   java -Dmusic.collections=my-collections.json \
        -cp notation-ui-1.0-SNAPSHOT.jar:my-songs.jar \
        music.notation.ui.Launcher
   ```

## Included Pieces

| Piece | Composer |
|-------|----------|
| Twinkle, Twinkle, Little Star | Traditional |
| Ode to Joy | Beethoven |
| Mary Had a Little Lamb | Traditional |
| The Ants Go Marching | Traditional |
| Canon in D | Pachelbel |
| Blue Lotus | Xu Wei |
| Two Tigers | Traditional |
| Two Tigers (Canon) | Traditional |
| We Will Rock You | Queen |
| Invention No. 13 in A Minor (BWV 784) | J.S. Bach |
| The Internationale | Pierre De Geyter |

## Design

See [DESIGN.md](DESIGN.md) for the full ADT specification, dependency graph, and a worked example (Twinkle, Twinkle, Little Star).
