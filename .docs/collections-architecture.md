# Music Collection Modules — Multi-project Architecture

> The canonical pattern for organising authored music in this system.
> Each composer / genre / personal collection lives in its own
> Maven module (often its own Git repository), and pieces are
> hand-authored Java code using the typed ADT.

---

## Why multiple projects

The original vision: pieces are **Java code, version-controlled, IDE-edited**.
The IDE + Git is the "library." But putting all music in one giant
`notation-songs` module doesn't scale:

- Mozart's complete works, the Real Book of jazz standards, a
  collection of Chinese folk songs, your personal compositions —
  these have **different lifetimes, owners, and audiences**
- Mixing them in one repo means anyone hacking on one collection
  has to pull, build and test all the others
- A friend wanting to share their collection of Brazilian folk
  arrangements should be able to publish their own JAR; you add it
  as a dependency without entangling sources
- Personal works should be private; public-domain transcriptions
  can be shared

**Each collection = its own Maven module.** Often its own Git
repository. Independently versioned, distributable, optional.

---

## Module shape

A collection module is a small Maven project that depends on the
shared infrastructure (`notation-core`, `notation-performance`,
`notation-play`) and provides a `Collection` implementation.

```
notation-music-mozart/                ← module / repo root
├── pom.xml                           ← depends on notation-core, -performance, -play
├── src/main/java/music/notation/collections/mozart/
│   ├── MozartCollection.java         ← implements Collection
│   ├── turkish_march/
│   │   ├── TurkishMarch.java         ← record TurkishMarch implements MusicalPiece
│   │   └── ManualTurkishMarch.java   ← PieceContentProvider<TurkishMarch>
│   ├── sonata_a_minor/
│   │   ├── SonataA Minor.java
│   │   └── ManualSonataAMinor.java
│   └── …
└── src/test/java/…                   ← PieceTestBase subclasses
```

`MozartCollection` is a one-liner-per-piece registration:

```java
package music.notation.collections.mozart;

import music.notation.collections.mozart.turkish_march.*;
import music.notation.collections.mozart.sonata_a_minor.*;
import music.notation.structure.Collection;
import java.util.List;

public final class MozartCollection implements Collection {
    @Override public String name() { return "Mozart"; }
    @Override public List<Entry<?>> entries() {
        return List.of(
            Entry.of(new TurkishMarch(),    new ManualTurkishMarch()),
            Entry.of(new SonataAMinor(),    new ManualSonataAMinor()),
            // …
        );
    }
}
```

That's it. No reflection magic, no annotations, no SPI files.

---

## Discovery — the `music.collections` JSON

`PieceLibrary` already supports plugging in collections via a system
property:

```bash
-Dmusic.collections=/path/to/collections.json
```

The JSON maps a display name to a fully-qualified `Collection`
class:

```json
{
  "Built-in Examples":  "music.notation.songs.DefaultCollection",
  "Mozart":             "music.notation.collections.mozart.MozartCollection",
  "Chinese Folk":       "music.notation.collections.china_folk.ChinaFolkCollection",
  "Personal":           "com.example.my.notation.MyCollection"
}
```

At startup, `PieceLibrary` reads the JSON, instantiates each class
via reflection (no-arg constructor), and registers all entries.
Order of insertion is preserved — first-wins on duplicate
identities.

The JSON is read once via `Files.readString` of the configured path;
no JSON library dependency.

---

## How a third-party collection gets used

User has Mozart collection JAR (built from `notation-music-mozart`):

1. Add to classpath: either `mvn install` it locally then add as a
   dependency in `notation-ui`'s pom, or drop the JAR into a
   classpath dir.
2. Reference it from `music.collections` JSON (the path the
   `notation-ui` runtime uses).
3. Launch — Mozart entries appear in the `NotationApp` piece
   picker alongside built-ins.

No re-build of the host app required. New collections are pure
add-ons.

---

## Repo layout options

Two valid patterns:

**A. Single repo with one collection module**:

```
notation-music-mozart/
└── pom.xml                  ← single-module Maven
```

Simple; right for "I want to maintain Mozart pieces and nothing else."

**B. Mono-repo with multiple collections + shared utilities**:

```
my-music/
├── pom.xml                  ← reactor parent
├── shared-helpers/          ← chord progressions, drum patterns reused across pieces
├── classical-mozart/
├── jazz-standards/
└── personal/
```

Right for "I have several collections plus shared infrastructure."

The infrastructure modules (`notation-core` etc.) live in a
*separate* repo, are published as Maven artifacts, and every music
project depends on them as ordinary dependencies.

---

## What stays in the main `music-notation` repo

The current `notation-songs` module is repurposed as
**"Built-in Examples"** — small set of demonstrations that show off
the ADT (`GrungeInternationale`, `XuWeiXiaoHongMao` etc.). Useful
for new users learning the system.

Real authoring goes into separate collection modules. The main
repo holds:

- `notation-core`, `notation-performance`, `notation-play`,
  `notation-ui` — the engine
- `notation-songs` — minimal examples + the `DefaultCollection` reference impl
- `notation-experiments` — sandbox / scratch
- (future) `notation-studio` — the focused-editing tool

It does NOT hold curated music libraries. Those go in their own
projects.

---

## Conventions for collection modules

- **Module name**: `notation-music-<topic>` — `notation-music-mozart`,
  `notation-music-china-folk`, `notation-music-jazz-standards`,
  `notation-music-personal`.
- **Package root**: `music.notation.collections.<topic>` — keeps
  identities and providers cleanly namespaced.
- **One sub-package per piece** — `…/turkish_march/`, `…/sonata_a_minor/` —
  contains identity record + 1+ providers + tests for that piece.
- **`Manual<PieceName>`** for the canonical authored arrangement;
  variant providers get descriptive names (`GrungeInternationale`,
  `BluesZaiNaYaoYuan`).
- **Each provider has a corresponding test** extending `PieceTestBase`.
- **Cookbook reference**: link `arrangement-cookbook.md` from each
  module's README so contributors know where to start.

---

## Onboarding a new collection (5-step recipe)

1. `git clone --template music-notation-collection-template my-music`
   (or copy `notation-songs` and rename)
2. Update `pom.xml`'s `<artifactId>` and parent versions
3. Author your first piece following `arrangement-cookbook.md`
4. `mvn install` to put your collection in the local Maven repo
5. Add the collection class to your `music.collections` JSON

Done — your pieces show up in `NotationApp`'s picker.

A `template` repo is worth creating once the pattern stabilises;
it's optional for now.

---

## Practical first concrete step (when ready)

The most useful thing to do *next* in this direction:

- Create a sibling repo `notation-music-mozart` (or
  `notation-music-classical`)
- Move `BluesZaiNaYaoYuan` and friends into a separate
  `notation-music-china-folk` module to demonstrate the pattern
- Leave only one or two reference arrangements in `notation-songs`
- Update `arrangement-cookbook.md` with the "where do I put my
  pieces?" section pointing at this doc

This is a small refactor — the code under `notation-songs` already
follows the right shape. Mostly a matter of moving directories and
splitting the `pom.xml`.

---

## Status

- Pattern works **today** — `Collection` interface and `music.collections`
  JSON discovery are already implemented in `PieceLibrary`.
- This doc captures the conventions. No code changes needed in the
  main repo to support multi-project collections.
- The optional template repo + the move of `BluesZaiNaYaoYuan` etc.
  out of `notation-songs` are clean-up tasks; do them when convenient.
