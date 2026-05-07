# Rational Duration Storage — Plan & Progress

> **Status: Phases 1–6 done.** Phases 7–8 deferred as polish.
> Triggered into life by the Mozart Turkish March import case.

> **Notation in this doc**: "**sf**" = "sixty-fourths" — the legacy
> integer time unit (whole = 64 sf, quarter = 16 sf, eighth = 8 sf,
> sixty-fourth note = 1 sf). It survives mainly via
> `Duration.sixtyFourths()` and `Bar.expectedSixtyFourths()` for
> backward compatibility with visitors that haven't migrated to
> rational. After the refactor, *the canonical storage is the rational
> `(numerator, denominator)` pair*; sf is a derived view that's lossy
> for tuplets (triplet-eighth = 1/12 ≈ 5.33 sf truncates to 5).

---

## Goal (delivered)

Replace the `int sixtyFourths` storage with an exact rational
representation `(long numerator, long denominator)` where each
{@code Duration} is **a fraction of a whole note**. Every musical
duration in standard notation (powers-of-2, tuplets, dotted variants,
mixed) is now representable exactly. The model is mathematically
*complete* with respect to its problem domain.

---

## Phase status overview

| Phase | What | Status |
|---|---|---|
| **1** | Rational `Duration` ADT (5 variants: `BaseValue`, `Dotted`, `Triplet`, `RawTuplet`, `RawDuration`) + tests | ✅ Done |
| **2** | `Bar` / phrase validation switches to `Duration.plus()` sums | ✅ Done |
| **3** | `MidiMapper.toTicks` uses exact `Duration.ticks(ppq)` | ✅ Done |
| **4** | `QuantizerProfile` extension point + rational `Quantizer.snap` | ✅ Done |
| **5** | `BarBuilder` / `DrumBarBuilder` / `PerformanceImporter` / `LoadedPieces` accept profile (default `STANDARD`) + UI import panel | ✅ Done |
| **6** | `BarBuilder` / `DrumBarBuilder` internal walker fully rational | ✅ Done |
| 7 | Decompose non-named chunks into chains of legal named values for cleaner score rendering | Deferred |
| 8 | Onset snapping through the profile (eliminates the residual sub-quantum gap that the safety check currently absorbs) | Deferred |
| Studio | Group-level beat quantization, manual re-quantize UI, save-to-library | Future project |

---

## What landed (Phase 1–6 details)

### Phase 1 — `Duration` ADT

`notation-core/duration/`:

```java
public sealed interface Duration permits BaseValue, Dotted, Triplet, RawTuplet, RawDuration {
    long numerator();
    long denominator();
    int  sixtyFourths();           // back-compat (lossy for non-pow-2)
    Duration dot();
    default Duration plus(Duration o)         { … }
    default Duration minus(Duration o)        { … }
    default Duration times(long n)            { … }
    default Duration dividedBy(long n)        { … }
    default int      compareDuration(Duration o) { … }
    default boolean  equalsDuration(Duration o)  { … }   // value-aware
    default long     ticks(long ppq)          { … }       // exact MIDI ticks
    default Duration canonical()              { … }       // → RawDuration

    // Factories
    static Duration of(BaseValue base)              { … }
    static Duration of(long num, long den)          { … }
    static Duration ofSixtyFourths(int sf)          { … }
    static Duration dotted(BaseValue base)          { … }
    static Duration triplet(BaseValue base)         { … }
    static Duration tuplet(int count, BaseValue base) { … }
    static Duration tuplet(int actual, int normal, BaseValue base) { … }
}

public enum BaseValue implements Duration {
    WHOLE                 (1, 1),
    HALF                  (1, 2),
    QUARTER               (1, 4),
    EIGHTH                (1, 8),
    SIXTEENTH             (1, 16),
    THIRTY_SECOND         (1, 32),
    SIXTY_FOURTH          (1, 64),
    HUNDRED_TWENTY_EIGHTH (1, 128);    // ← new for fast keyboard runs
}

public record Dotted(BaseValue base, int dotCount) implements Duration { … }
public record Triplet(BaseValue base) implements Duration { … }    // 3-in-time-of-2
public record RawTuplet(int actualCount, int normalCount, BaseValue base) implements Duration { … }
public record RawDuration(long numerator, long denominator) implements Duration { … }   // gcd-normalised
```

Constants on each variant:

- `Dotted.{WHOLE,HALF,QUARTER,EIGHTH,SIXTEENTH,THIRTY_SECOND}` (single-dot)
- `Triplet.{HALF,QUARTER,EIGHTH,SIXTEENTH,THIRTY_SECOND}`

40 tests in `DurationTest`, 31 in `BarDurationTest`. Both green.

### Phase 2 — Bar/Phrase validation rational

`Bar.<init>` sums nodes' rational durations and compares via
`equalsDuration` against `expectedDuration.totalDuration()`.
Triplet bars now validate exactly (3 × `Triplet.EIGHTH` = `1/4` —
no precision loss).

`Bar.nodeDuration(PhraseNode)` and `Bar.phraseDuration(AuthorPhrase)`
return exact `Duration`. The legacy `nodeSixtyFourths` /
`phraseSixtyFourths` getters delegate to these and `.sixtyFourths()`
the result (for visitors that still want int sf).

### Phase 3 — MIDI tick math exact

```java
// Was: (long) duration.sixtyFourths() * TICKS_PER_QUARTER / 16   ← lossy for tuplets
// Now: duration.ticks(TICKS_PER_QUARTER)                          ← exact rational
```

Triplet eighth = 160 ticks (vs old 150). Five quintuplet sixteenths
= 96 ticks each, sum to 480 = one quarter. Septuplet eighth = 137.

### Phase 4 — `QuantizerProfile`

`notation-performance/QuantizerProfile.java`. Sealed-style record
with `Builder` + four presets:

- `STANDARD` — powers-of-2 + dotted (legacy default)
- `WITH_TRIPLETS` — adds triplet at every level (Mozart, Beethoven, pop/rock)
- `FULL` — adds quintuplets + septuplets (Chopin, Liszt, jazz)
- `IMPROV` — same legal set as `FULL`, semantic placeholder for live MIDI keyboard

Custom profiles via `Builder` (`withBaseDurations()`, `withTriplets()`,
`withQuintuplets()`, `withSeptuplets()`, `withVeryFast()`, `add(Duration)`).

`Quantizer.snap(Duration raw, QuantizerProfile)` returns nearest
legal duration by absolute value distance. `Quantizer.floor(Duration max,
QuantizerProfile)` returns the largest legal ≤ max.

Legacy int-based `Quantizer.snap(double sf)`, `floor(int)`, `isLegal(int)`
preserved unchanged.

### Phase 5 — Importer plumbing

`BarBuilder.Config(int barSf, int bpm, QuantizerProfile profile)` — new
field; backward-compat 2-arg constructor defaults to `STANDARD`.
`PerformanceImporter.toPiece(...)` and `LoadedPieces.fromImport(...)`
gain profile overloads with same defaults.

### Phase 6 — Rational walker

`BarBuilder.State` and `DrumBarBuilder.State`:

- `cursor` and `posInBar` are now `Duration` (not `int sf`)
- `barTotal` cached as `Duration`
- `emitRest(Duration gap)` — greedy decomposition via
  `Quantizer.floor(Duration, profile)`
- `emitEvent(pitches, Duration totalDur)` — fully rational tie-chain
  decomposition: each chunk is exactly the rational remainder of the
  current bar; overflow continues to next bar with `tied = true`

Triplet-aware imports now produce bars whose nodes' rational sums
match exactly. Verified by `BarBuilderTest.tripletEighths_*`.

### UI — Import panel

`PiecePickerDialog` got an "Import…" toggle button at the bottom
right that expands a hidden bottom panel containing:

- Quantizer profile dropdown (Standard / With triplets / Full /
  Improvisation)
- Voice-split tracks checkbox
- Load file… button

`PieceChoice.Imported` carries the chosen `QuantizerProfile`. The
default unchanged ("Standard") matches the legacy STANDARD profile —
no surprise behaviour for users not opting in.

---

## Bugs discovered & fixed during Phase 6

Both were specific to the rational walker and didn't show up in
unit tests until live UI import surfaced them.

### Bug A — sub-quantum gap infinite loop

**Symptom**: UI hang on any MIDI import, including simple files.

**Cause**: ms-derived onsets are exact rationals (e.g., `167/2000`
of a whole). Cursor advances by snapped durations only (e.g., `1/12`).
The gap can be smaller than every legal duration in the profile
(e.g., `1/6000` of a whole ≈ 0.32 ms at 120 BPM). `Quantizer.floor`
returns null; inner loop breaks; outer loop's `remaining` never
decreased → infinite spin.

**Fix**: two safety checks in both `emitRest`s:

```java
// Drop sub-quantum gaps upfront
if (gap.compareDuration(profile.smallest()) < 0) return;

// Outer-loop safety: break if inner loop made no progress
if (!madeProgress) break;
```

The first is the cleanest path; the second is defensive.

### Bug B — TieSplitter overshoot when `posInBar` non-sf-clean

**Symptom**: `IllegalArgumentException: Bar: expected 1/1 (64 sf)
but nodes total 193/192` after a triplet-aware import.

**Cause**: Phase 6's first cut kept the int-sf `TieSplitter` path
for bar-overflow cases. When `posInBar` held a rational like `1/12`,
`posInBar.sixtyFourths()` truncated to 5; TieSplitter computed "59 sf
fits in remaining bar" but actual rational `remainingInBar = 11/12 =
176/192`, while 59 sf = 177/192. **Overshoot by 1/192** = the error.

**Fix**: replaced the entire TieSplitter branch in `emitEvent` with
fully rational decomposition:

```java
while (remaining.numerator() > 0) {
    Duration remainingInBar = barTotal.minus(posInBar);
    Duration chunk = remaining.compareDuration(remainingInBar) <= 0
            ? remaining : remainingInBar;
    boolean tied = chunk.compareDuration(remaining) < 0;
    cur.add(makePitchNode(pitches, chunk, tied));
    posInBar = posInBar.plus(chunk);
    cursor   = cursor.plus(chunk);
    remaining = remaining.minus(chunk);
    flushBarIfFull();
}
```

Each chunk is exactly the rational remainder — sums always match.

**Trade-off**: a chunk that bridges a bar boundary may have a
duration like `5/64` (no named note value). Bar validation passes;
audio playback works (exact rational ticks); but visual notation
would need decomposition into `[1/16 tied → 1/64]` for cleaner
display. Phase 7 territory.

---

## What's left

### Phase 7 — Decompose non-named chunks (deferred)

Some bar-spanning emits produce chunks with durations not
representable as a single named note. For audio it's fine; for
visualization we'd want to post-process each bar, splitting weird
chunks into chains of legal named durations connected by ties.

Plan: a `LegalDurationDecomposer` that takes a chunk + `QuantizerProfile`
and returns a list of legal durations summing to the chunk. Use it
inside `emitEvent`'s tie loop. ~80 LOC + tests.

Trigger: when score-style rendering of imported pieces becomes a
priority.

### Phase 8 — Snap onsets through the profile (deferred)

Currently durations are profile-snapped but onsets are computed from
raw ms via `msToFraction` without any snapping. The sub-quantum gaps
the safety check absorbs are residue from this asymmetry. Phase 8 also
snaps onsets, removing the asymmetry and the safety check together.

Plan: in `BarBuilder.build`, after computing `Duration onsetRaw`, snap
to the closest legal grid position via the profile (or via a different
"position grid" derived from the profile). ~30 LOC + tests.

Trigger: when individual note onsets matter visibly (live keyboard
capture where exact beat-relative placement is wanted).

### Studio (separate project)

The work that would make Turkish March's "B-A-G♯-A is supposed to be
4 sixteenths" inference correct lives outside the per-note quantizer.
Group-level beat quantization, manual re-quantize UI, save-to-library —
all studio-tier features. Plan-doc those when starting that work.

---

## Where to look in the codebase

| Concern | File |
|---|---|
| Rational `Duration` ADT | `notation-core/duration/{Duration,BaseValue,Dotted,Triplet,RawTuplet,RawDuration}.java` |
| Bar/phrase rational validation | `notation-core/phrase/Bar.java` |
| MIDI tick math | `notation-play/play/MidiMapper.java` |
| Quantizer + profile | `notation-performance/{Quantizer,QuantizerProfile}.java` |
| Rational import walker | `notation-performance/{BarBuilder,DrumBarBuilder}.java` |
| Importer profile API | `notation-performance/PerformanceImporter.java`, `notation-play/play/LoadedPieces.java` |
| UI import panel | `notation-ui/ui/PiecePickerDialog.java`, `PieceChoice.java`, `NotationApp.java` |

---

## Tests of interest

- `DurationTest` — 40 tests proving the rational arithmetic is exact
- `BarDurationTest` — 31 tests covering meter shapes (4/4, 6/8, 5/4, etc.)
- `BarTest` — including triplet-bars-validate cases
- `MidiMapperTest` — proves tick math is exact for triplets/quintuplets/septuplets
- `QuantizerProfileTest` — covers preset profiles + builder + snap behaviour
- `BarBuilderTest.tripletEighths_*` — end-to-end import-to-bar correctness
