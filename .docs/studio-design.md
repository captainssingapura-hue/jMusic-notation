# Notation Studios — UI / Workflow Design

> Companion to `studio-plan.md`. Two mini-apps — **Import Studio**
> and **Capture Studio** — sharing a small core (`notation-studio-core`).
> Each app is sized to one focused task; neither tries to be a DAW
> or a piece library. Authored pieces live as Java in collection
> modules (see [`collections-architecture.md`](collections-architecture.md));
> the studios are bridges from MIDI input to that Java code.

---

## Why two apps

The import problem and the capture problem look superficially
similar — "MIDI in, `Piece` out, audition, emit Java" — but the
pressure points differ:

- **Import** wrestles with *interpretation*: many tracks, real
  tempo maps, time-signature changes, which profile per track,
  whether to voice-split. The user spends most of the time
  staring at a multi-track piano roll.
- **Capture** wrestles with the *act of recording*: device
  selection, click track, count-in, retake, rename takes, latency.
  The user spends most of the time playing the keyboard, not
  fiddling with parameters.

Combined, the parameter rail filled up with controls that were
irrelevant in one mode or the other. Split, each app's UI fits
on a small screen and its purpose is obvious from the layout.

---

## Shared core (`notation-studio-core`)

Reusable pieces both apps embed:

- **`PieceAuditionPanel`** — read-only piano-roll view of a `Piece`,
  one row per track, plus play / stop and a status strip.
- **`ParameterPanel` primitives** — profile dropdown, split-mode
  dropdown, BPM-override field. Each app composes the subset it
  needs.
- **`JavaEmitter`** — walks a `Piece` and produces source files
  (identity record + provider) in cookbook style. Backs both apps'
  "Copy as Java" and "Save to folder…" actions.

No app-specific UX in core.

---

## App 1 — Import Studio

### Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ Import Studio   [Open MIDI…] [▶ Audition] [⏹] [Copy as Java ▾] │ ← Top toolbar
├──────────────────────────────────────────┬──────────────────────┤
│ ┌─[Turkish March]──────────────────────┐ │ GLOBAL PARAMETERS    │
│ │ Track 1: Vocal       [profile ▾]     │ │ ──────────           │
│ │ ▰▰▰  ▰  ▰▰▰  ▰▰▰▰▰▰  ▰▰▰▰  ▰  ▰▰▰    │ │ Default profile:     │
│ │                                       │ │  [With triplets ▾]   │
│ │ Track 2: Lead        [profile ▾]     │ │                      │
│ │ ────  ▰▰  ──────────  ▰▰▰▰▰  ──  ▰    │ │ Voice split:         │
│ │                                       │ │  [Auto (T1+T2) ▾]    │
│ │ Track 3: Bass        [profile ▾]     │ │                      │
│ │ ▰  ▰  ▰  ▰  ▰  ▰  ▰  ▰  ▰  ▰  ▰  ▰    │ │ BPM override:        │
│ └───────────────────────────────────────┘ │  [120  ] (auto)      │
├───────────────────────────────────────────┴──────────────────────┤
│ Status: Turkish March · 56 bars · 120 BPM · 4/4 · 3 tracks       │
└──────────────────────────────────────────────────────────────────┘
```

- **Top toolbar**: open `.mid`, transport, **Copy as Java** split-
  button (clipboard / save-to-folder).
- **Center**: `PieceAuditionPanel` showing per-track piano rolls.
  Each track row carries a per-track profile override (Phase B1) —
  drum tracks settle on STANDARD while a melody uses
  WITH_TRIPLETS without forcing the whole piece either way.
- **Right**: global parameters that drive the *next* re-derive —
  default profile (used by tracks that don't override), split
  mode, BPM override.
- **Status bar**: piece title, bar count, BPM, meter, track count.

### Workflow

1. **Open MIDI…** → pick `.mid`.
2. Studio runs `PerformanceImporter.toPiece` with current global
   parameters; per-track overrides start blank (= "use global").
3. **▶ Audition**. Listen.
4. Hear triplets misread on a single track? Set that row's
   profile dropdown to **With triplets**. View + audition update.
5. Lead and bass collapsed in one source track? Switch global
   split mode to **Auto (T1+T2)**. The newly split tracks appear,
   each with its own per-row profile dropdown.
6. **Copy as Java** → **Save to folder…** → pick the target
   collection module's piece sub-package.
7. IDE: register the new piece in the module's `Collection`,
   refine, commit.

### Polish (Phase D1) — small additive items

- Pickup-beat detection nudge: if importer guesses an anacrusis,
  show a one-line banner with "accept / shift to bar 1".
- Per-bar tempo override (table editor) — exposes
  `tempo-arrangement-plan.md` once that lands.
- Profile A/B compare: hold the current `Piece` in memory, let
  the user audition the same range under a different profile
  side-by-side.

---

## App 2 — Capture Studio

### Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ Capture Studio   [● Record] [⏹] [▶ Audition] [Copy as Java ▾]   │ ← Top toolbar
├──────────────┬──────────────────────────────────┬───────────────┤
│ TAKES        │ ┌─[Take 4 — 12 bars]──────────┐  │ DEVICE        │
│ ──────       │ │ Track 1                      │  │ [USB-MIDI ▾]  │
│ Take 1  3 b  │ │ ▰  ▰▰  ▰  ▰▰▰  ▰▰  ▰  ▰▰▰▰   │  │               │
│ Take 2  8 b  │ │                              │  │ TEMPO         │
│ Take 3  ✗    │ │                              │  │ [80 BPM]      │
│ ▶ Take 4 12b │ │                              │  │               │
│              │ │                              │  │ COUNT-IN      │
│ [+ New take] │ │                              │  │ [☑ 1 bar]     │
│              │ │                              │  │ [☑ Click on]  │
│              │ │                              │  │               │
│              │ │                              │  │ INTERPRETATION│
│              │ │                              │  │ Profile:      │
│              │ │                              │  │ [Improv ▾]    │
│              │ └──────────────────────────────┘  │               │
├──────────────┴──────────────────────────────────┴───────────────┤
│ Status: Take 4 · 12 bars · 80 BPM · 4/4 · 1 track · raw saved   │
└─────────────────────────────────────────────────────────────────┘
```

- **Top toolbar**: record / stop / audition / **Copy as Java**.
- **Left rail (Takes)**: every recording becomes a numbered take.
  Click to switch the audition view to that take. Per-take menu:
  rename, save raw `.mid`, delete.
- **Center**: `PieceAuditionPanel` for the currently selected
  take's `Piece`.
- **Right rail**: capture-only controls on top (device / tempo /
  count-in / click), plus a small interpretation block underneath
  (just the profile — split-mode is moot for a single-stream
  capture by default).
- **Status bar**: take name, bar count, tempo, meter, track count,
  whether the raw is saved.

### Workflow

1. Plug in a MIDI keyboard. Pick it from the **Device** dropdown.
2. Set **Tempo** and toggle **Count-in** + **Click** as you like.
3. **● Record** (or hit space). Metronome plays the count-in,
   then recording starts on the first key.
4. Play. **⏹** when done. The take appears in the Takes rail and
   is auto-selected.
5. Studio runs `PerformanceImporter.toPiece` with `IMPROV` and the
   imposed click tempo, displays the result.
6. Like the take? **Copy as Java** → **Save to folder…** → pick a
   personal collection module
   (e.g. `notation-music-personal/.../riff_in_a_minor/`).
7. Don't like it? Click **+ New take** and play again. Old takes
   stay until you delete them.
8. Optional per take: **Save raw `.mid`** for re-analysis later
   under a different profile or BPM.

### Polish (Phase D2) — small additive items

- **Tempo detection** for free-time takes (no click): infer BPM
  from inter-onset stats and snap.
- **Punch-in / out**: re-record a range of an existing take
  without losing the surrounding bars.
- **Layered capture**: record an additional track over an
  existing take (the take's BPM/meter establishes the click);
  produces a multi-track `Piece`.

---

## Java emitter — what both apps produce

Two files per piece, matching `arrangement-cookbook.md`
conventions:

**`TurkishMarch.java`** (identity record):

```java
package music.notation.collections.mozart.turkish_march;

import music.notation.song.MusicalPiece;

public record TurkishMarch() implements MusicalPiece {
    @Override public String title() { return "Turkish March"; }
}
```

**`ManualTurkishMarch.java`** (content provider):

```java
package music.notation.collections.mozart.turkish_march;

public final class ManualTurkishMarch
        implements PieceContentProvider<TurkishMarch> {

    @Override public Piece content(TurkishMarch id) {
        return Piece.of(
            tempo(120), key(A_MINOR), meter(4, 4),
            melody(), bass(), drums()
        );
    }

    private MelodicTrack melody() {
        return MelodicTrack.of("Vocal", PIANO,
            bar4(o5(SIXTEENTH, B, A, GS, A), o5(EIGHTH, C, E)),
            bar4(o5(QUARTER, B), o5(EIGHTH, A, GS), …),
            // …
        );
    }
    // bass(), drums(), …
}
```

Plain Java. The studios don't track ownership beyond emission.

---

## Data flow summary

Both apps converge on the same shape; they differ only on the left.

```
                 .mid file                  live MIDI input
                     ↓                              ↓
              MidiCodec                       Receiver + take buffer
                     ↓                              ↓
                  Performance ───────────────── Performance
                                  ↓
              PerformanceImporter.toPiece(profile, splitMode)  ← parameter rail
                                  ↓
                                Piece
                  ┌───────────────┼───────────────────┐
                  ↓               ↓                   ↓
        PieceConcretizer    JavaEmitter          MidiCodec.toMidi
                  ↓               ↓                   ↓
              MidiPlayer    clipboard / .java    .mid (capture only)
              (audition)    files (deliverable)
```

There is no on-disk piece library owned by either studio.
Everything left of the arrows is transient except the explicitly
saved Java files (and, for Capture Studio, the optional raw take).

---

## Open questions to settle when implementing

1. **Where does shared chrome live — `notation-studio-core` as a
   library, or a thin `BaseStudioApp` class each app extends?**
   Lean: library of components, no inheritance. Each app builds
   its own scene graph from the parts it wants.
2. **Default capture save location?** Lean: `studio-captures/`
   under the user's home, date-stamped filenames.
3. **Re-derive on every parameter change vs explicit "Apply"?**
   Lean: re-derive on change (cancel any in-flight derive when a
   new change arrives). Imports are fast enough; capture takes
   are short.
4. **Per-track profile override granularity in Import Studio —
   per track only, or per bar range too?** Lean: per track in
   Phase B1; per-bar range deferred to D1 polish if it actually
   comes up.
5. **One process or two?** Lean: separate `main` per app; users
   typically want one open at a time. Both can launch to the same
   collection-module target directory if invoked with `--target=…`.

---

## Status

- Design saved. No code yet.
- Pairs with `studio-plan.md`. Recommended start: build
  `notation-studio-core` (especially `JavaEmitter`) first against
  an existing authored piece, then ship Import Studio A1+C1, then
  Capture Studio A2+C2.
