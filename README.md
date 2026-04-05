# Music Notation

A typed music notation system in Java using sealed interfaces (sum types) and records (product types), with phrasing as the primary organizational unit. Includes a JavaFX player with MIDI playback.

## Architecture

The project is a Maven multi-module build targeting Java 21.

```
notation-parent
 +-- notation-core     Core ADT: Piece, Track, Phrase, Pitch, Chord, Duration, ...
 +-- notation-play     MIDI mapping and playback (javax.sound.midi)
 +-- notation-songs    Song library with auto-discovery of pieces and providers
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

Songs are modelled with two interfaces:

- **`MusicalPiece`** -- identity record (title, composer)
- **`PieceContentProvider<P extends MusicalPiece>`** -- creates a `Piece` for that identity

Multiple providers can exist for the same piece (e.g. different arrangements). `PieceLibrary` uses Guava `ClassPath` to auto-discover all implementations at runtime.

## Building

```bash
mvn clean package
```

## Running

```bash
mvn javafx:run -pl notation-ui
```

The player opens with a list of available pieces. Select one to see a piano-roll visualization and control playback (play/pause, seek by dragging).

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

