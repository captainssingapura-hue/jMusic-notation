# MXL (Compressed MusicXML) Import — Plan & Progress

> **Status: Skeleton landed. Pass 1 parser not started.**
> Triggered by user-supplied `.mxl` corpus (Chopin Nocturne et al.) and
> the desire to import notation-authored scores without going through
> the live-MIDI capture pipeline.

> **Module**: `notation-mxl`, sibling plugin module depending on
> `notation-core` + `notation-performance`. No other module depends on
> it — opt-in import path.

---

## Goal

`.mxl` (and bare `.xml`) → `MxlImport(Performance, TimeSignature, KeySignature, sourceXml)`,
parallel to `MidiImport`. The concrete-notes layer (`Performance`) is
the equivalent of the JSON intermediate — `PerformanceJson` already
provides the JSON face, so no separate JSON layer is needed.

---

## Why MusicXML sidesteps the live-MIDI pipeline

`PerformanceImporter` runs `Quantizer / OnsetGrouper / OverlapVoiceSplitter
/ PitchBandSplitter / TieSplitter` to clean up live-captured MIDI. None
of these are needed for MusicXML, because the format encodes the answers
the splitters reverse-engineer:

| Live-MIDI problem | MusicXML answer |
|---|---|
| Sloppy onsets | Exact rational positions (`<divisions>`) |
| Async chord onsets | Explicit `<chord/>` element |
| Conflated voices | Explicit `<voice>` element |
| Implicit ties | Explicit `<tie type="start/stop"/>` |
| Pitch register splits | Explicit `<staff>` per note |

MXL imports therefore target **`PerformanceImporter.SplitMode.PRESERVE`**
— each MusicXML voice/staff becomes its own `Track` up front, and the
1:1 path through the existing importer takes it the rest of the way to
`Piece`.

---

## Phase status overview

| Phase | What | Status |
|---|---|---|
| **0** | Module skeleton (`pom.xml`, `MxlContainer`, `MxlReader`, `MxlImport`, `MxlProject`, `MusicXmlParser` stub) + Chopin fixture + container smoke tests | ✅ Done |
| **1a** | Single-part walker — notes, chords, ties, `<backup>`/`<forward>`, voices/staves → tracks, initial tempo | ✅ Done |
| **1b** | `TempoTimeline` for mid-piece `<sound tempo>` changes + multi-part | ✅ Done (Chopin's 30 rubato tempo changes flow through) |
| **1c** | Split JSON output (per-piece folder of small files) — see "JSON layout" below | ✅ Done |
| **1d** | Dynamics → `Volume` side-channel; articulations → `Articulations`; slurs → LEGATO span | ✅ Done (Chopin's `<dynamics>` markings + `<sound dynamics>` flow into Volume; staccato/accent/tenuto/marcato + slurs flow into Articulations) |
| **1e** | Repeats expansion + `repeats.json` structural sidecar | ✅ Done (Bach Air: 468 → 921 notes after expansion; structure sidecar round-trips) |
| **1f** | Transposing instruments (`<transpose>` → sounding pitch + `transpositions.json` sidecar) + unpitched percussion (`<unpitched>` → single DRUM track via `<part-list><midi-unpitched>`) | ✅ Done |
| **1g** | Edge cases polish — `<mode>`-absent warning, mid-piece `<time>`/`<key>`/`<divisions>` change warnings (per part), per-part grace-note + dropped-percussion summary log, robust numeric parsing (tempo / fifths / dynamics) | ✅ Done |
| **2** | Mid-piece time/key changes (after `Piece`-meta extension lands) + grace-note policy | Pending upstream work |

---

## Pass 1 scope (full coverage minus deferred)

- Parts → `Track`s; voices/staves → separate `Track`s (preserve mode)
- Chords (`<chord/>`), ties (intrinsic on `PitchedNote`), tuplets (`<time-modification>`)
- Repeats expanded inline: `<repeat>`, voltas (`<ending>`), `<segno>`/`<coda>`/`<dacapo>`/`<dalsegno>`/`<tocoda>`/`<fine>`
- Transposition (`<transpose>`) → store **sounding** pitch
- Unpitched percussion (`<unpitched>`) → `DrumNote` via `PercussionMap`
- Tempo from `<sound tempo="…">`; ignore text-only tempo markings (e.g. bare `<words>Andante</words>`)
- Dynamics (`<dynamics>`) → `Volume` side-channel
- Articulations (`<articulations>`) → `Articulations` side-channel (write-only — see [Articulation.java:7](../notation-performance/src/main/java/music/notation/performance/Articulation.java))
- **Initial** `TimeSignature` + `KeySignature` only (subsequent changes logged)

---

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Voice/staff → Track naming | Flat: `"<part> · staff N · v M"` | Keeps `Score`/`Track` shape unchanged; `PerformanceImporter.PRESERVE` consumes it 1:1. Part grouping, if needed later, is a UI concern. |
| Transposing pitch storage | Sounding (concert) pitch | MIDI playback parity. Written-pitch view is a rendering concern. |
| Repeat handling | Expand at parse time | `Performance` is a flat timeline; repeat structure is a notation concern. |
| Tempo source | Honour `<sound tempo>` only | Text-only marking interpretation is out of scope. |

---

## Deferred gaps (registered)

| Gap | Why deferred | Where it should land |
|---|---|---|
| **Grace notes** | Need consistent treatment across the abstract `AbstractNote` layer (`notation-core/.../core/model/AbstractNote.java`) and the concrete-notes layer. Picking a ms borrow-from-next strategy in MXL only would diverge from any policy the abstract layer eventually adopts. | When the abstract-layer grace-note policy is set, then thread through. |
| **Mid-piece time-signature changes** | `Piece` carries a single `timeSig`; MIDI import has the same single-value limitation. Solving for MXL alone would force a `Performance`-level side-channel that duplicates an extension `Piece` will need anyway. | Piece-meta extension (track-of-time-signatures), then re-parse to capture. |
| **Mid-piece key-signature changes** | Same as above. | Same. |
| **Articulations round-trip** | Known write-only limitation: `MidiCodec` drops them. | When articulations become a first-class round-trip channel. |
| ~~**Score's repeat structure preserved**~~ | Promoted from "deferred" to part of Landing 1e: a `repeats.json` sidecar captures the structure independently of the (expanded) `Performance`. See "Landing 1e design". | — |

---

## Module layout (landed in Phase 0)

```
notation-mxl/
  pom.xml                                # depends on notation-core + notation-performance
                                         # + slf4j-api / log4j-slf4j2-impl / log4j-core (logging)
  src/main/java/music/notation/mxl/
    MxlContainer.java                    # zip + META-INF/container.xml resolution
    MusicXmlParser.java                  # XML → Performance (drives SLF4J logging)
    RepeatExpander.java                  # extracts RepeatStructure + builds playback schedule (1e)
    RepeatStructure.java                 # repeat / volta / jump records (sidecar shape)
    TempoTimeline.java                   # piecewise-constant tempo function over divisions
    PitchMath.java / KeyFromFifths.java  # MusicXML → MIDI pitch / key
    MxlReader.java                       # bytes/Path → MxlImport (pure, no I/O)
    MxlImport.java                       # carries Performance + RepeatStructure + sourceXml
    MxlProject.java                      # folder coordinator (extractXml + importMxl)
    MxlSplitJsonWriter.java              # Performance → per-piece folder of JSON files
    MxlSplitJsonReader.java              # round-trip reader
    MxlPlay.java / MxlPlayJson.java      # CLI smoke players
    MxlBatch.java                        # CLI batch importer (folder → JSON packages)
  src/main/resources/
    log4j2.xml                           # console pattern, INFO default
  src/test/resources/
    Chopin_Nocturne_Op9_No1.mxl
```

### Project-folder convention (`MxlProject`)

```
<project>/
  mxl/    # source .mxl (populated when an import copies an external file in)
  xml/    # decompressed MusicXML, written as MXL_<base>.xml
  json/   # concrete-notes JSON, split per piece into MXL_<base>/...
```

- Default project folder = parent dir of the picked `.mxl` (inferred).
- User may override; sources outside the project are copied into `mxl/`.
- `MXL_` prefix marks files/dirs derived from MXL extraction so hand-edited
  files can sit alongside without collision.

### Landing 1e design — playback expansion + structural sidecar

> **Status**: Designed. Implementation pending — picked up as a session-of-its-own.

**Guiding principle**: the goal of import is to **faithfully reproduce the
actual sequence of notes the piece plays**, not to preserve the score's
sheet-music abbreviations. Repeats and voltas exist on paper to save space;
on a modern pitch-roll display that constraint is gone. So:

- `Performance` is the **expanded playback truth** — every loop is fully
  materialised, voltas are resolved per pass, and D.C. / D.S. / coda /
  fine reroutes are baked into the ms timeline.
- A **`RepeatStructure` sidecar** captures the score's original repeat
  layout, keyed by original-measure index, so the source MusicXML (or
  even sheet music) can be reconstructed if a notation renderer needs it.

This separation also keeps the Java DSL more concise than traditional
sheet music (no repeat machinery in the playback model) while leaving
room for downstream tools that *do* care about the score layout.

#### Data shape

```java
record RepeatStructure(
    List<RepeatBar> repeatBars,   // <repeat direction="forward|backward" times="N"/>
    List<Volta> voltas,           // <ending number="1,2..." type="start|stop"/>
    List<Jump> jumps              // <sound dacapo|dalsegno|tocoda|fine|segno|coda />
) {}

record RepeatBar(int measureIndex, Direction direction, int times) {}
record Volta(int startMeasure, int stopMeasure, List<Integer> numbers) {}
record Jump(int measureIndex, JumpKind kind, String label) {}

enum Direction { FORWARD, BACKWARD }
enum JumpKind  { SEGNO, CODA, DACAPO, DALSEGNO, TOCODA, FINE }
```

All `measureIndex` fields reference the **original** measure list so the
structure can be remapped to the score, independent of expansion.

#### Wiring

1. **Parser pre-pass** walks measures once and produces *both*:
   - a `RepeatStructure` (purely descriptive — what the score says), and
   - a `List<Integer>` **playback schedule** of original-measure indices
     in playback order, by simulating the playback head over the
     structure (forward/backward repeats, voltas by pass number,
     D.C. / D.S. / fine).
2. **`buildTempoTimeline`** and **`walkPart`** iterate the schedule
   instead of `children(part, "measure")`. The existing per-measure
   code is reused unchanged — same measure can be visited multiple times,
   each pass laying notes at a fresh cumulative cursor offset.
3. **`MxlImport`** gains a `RepeatStructure` field (MXL-specific —
   sibling to `sourceXml`, not part of the `MusicalImport` interface;
   `MidiImport` doesn't carry one).
4. **Split JSON**:
   - Writer emits `repeats.json` when structure is non-empty.
   - Reader loads it back when present.
5. **Round-trip contract**: `Performance` (expanded) + `RepeatStructure`
   (annotation) together let downstream tools reconstruct the original
   sheet layout.

#### Synthetic test fixtures

- Forward + backward repeat (basic loop)
- Voltas (1st / 2nd ending) with backward repeat between
- D.C. al fine (play through, return to start, end at fine)
- D.S. al coda (segno + tocoda + coda)
- Real corpus check: Bach Air on the G String — has volta-1/volta-2 +
  two backward repeats; current parser plays volta 1 *and* volta 2
  back-to-back (subtle bug surfaced during planning).

#### Open questions to resolve at start of work

1. **Sidecar filename**: `repeats.json` (focused) vs `score-structure.json`
   (general). Lean `repeats.json` — future structural sidecars get
   their own files.
2. **Measure index source**: prefer the XML `<measure number="N">`
   attribute (matches what composers see); fall back to 0-based
   document order when absent.
3. **Volta semantics on D.C./D.S. passes**: standard playback says the
   D.C./D.S. return is treated as a fresh pass-1 unless additional
   repeats sit inside the returned region. Confirm the corner cases
   against a real fixture before wiring.

### JSON layout (per piece)

Each import gets its own subfolder under `json/` populated with small files
keyed by purpose, instead of one large monolithic document:

```
json/MXL_<base>/
  meta.json                      # { displayName, timeSig, key }
  tempo.json                     # TempoTrack (all changes)
  track-01-<slug>.json           # one file per Track in Score order
  track-02-<slug>.json
  …
  volume.json                    # written when Volume is populated (Landing 1d)
  articulations.json             # written when Articulations is populated (Landing 1d)
  repeats.json                   # written when RepeatStructure is non-empty (Landing 1e)
  transpositions.json            # written when any part transposes (Landing 1f)
```

`<slug>` is a filesystem-safe transform of the track id (e.g. `P1 · staff 1 ·
v1` → `P1-staff-1-v1`). The numeric prefix preserves Score order for at-a-glance
listing. Side-channel files are omitted while their data is empty; they appear
in 1d once dynamics/articulations are populated.
