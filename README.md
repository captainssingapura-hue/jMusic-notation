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
        +-- auxTracks: Track* (auxiliary voices sharing the same lane)
        +-- Phrase* (sealed: MelodicPhrase | RestPhrase | ChordPhrase)
              +-- PhraseNode* (sealed: NoteNode | RestNode | PaddingNode | SubPhrase | DynamicNode | GraceNote | PercussionNote | SlurStart | SlurEnd)
```

- **Sealed interfaces** model sum types (`Phrase`, `PhraseNode`, `Chord`)
- **Records** model product types (`Pitch`, `Duration`, `NoteNode`, `MajorTriad`, ...)
- **Phrases are recursive** via `SubPhrase`, enabling hierarchical musical structure
- **Dynamics are positional** -- `DynamicNode` markers flow inline among notes
- **Aux tracks** allow multi-voice writing within a single staff (e.g. soprano + alto on the right hand). Aux bars are collected per-bar in the builder and extracted into separate `Track` objects for playback and display

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

### StaffPhraseBuilder

A fluent builder for staff-notation melodic phrases using absolute pitch names. Key features:

- **Key-aware accidentals** -- notes automatically use sharps/flats from the key signature
- **Octave methods** -- `o2()` through `o7()` for concise pitch entry with poly chord support
- **Bar management** -- `bar()`, `pickup()`, `ending()` with automatic bar-duration validation
- **Per-bar default duration** -- `bar(QUARTER)` / `aux(HALF)` set a local default, reducing explicit `Duration` annotations. Plain `bar()` / `aux()` revert to the builder-level default
- **Aux bars** -- `aux()` within a bar starts an auxiliary voice; `buildAuxPhrases()` extracts them into separate phrases for multi-voice tracks
- **Slurs and ties** -- `slur()` / `slurEnd()` for cross-bar ties (auto-merged same-pitch)
- **Grace notes** -- `grace()` / `accentedGrace()` for ornamental notes

```java
var P = StaffPhraseBuilder.in(KEY, TS, EIGHTH);

var melody = P
    .pickup(QUARTER).o4(F)
    .bar(QUARTER).mf().o4(A).o4(C).o5(F).o5(A)
        .aux(HALF).o4(C, F).o4(E)
    .bar().o5(QUARTER.dot(), A).o5(G).o5(F).o5(E)
    .build(breath());
var auxPhrases = P.buildAuxPhrases(breath());
```

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

The player opens with a list of available pieces. Select one to see a piano-roll visualization and control playback (play/pause, seek by dragging). Aux tracks are rendered in the same lane as their parent track with a slightly shifted color for visual distinction.

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
| November Storm | Xu Wei |
| Traumerei (Op. 15 No. 7) | Robert Schumann |

## Design

See [DESIGN.md](DESIGN.md) for the full ADT specification, dependency graph, and a worked example (Twinkle, Twinkle, Little Star).
