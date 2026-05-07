# Duration Abstraction — Sealing the 64-Base Leak

> **Goal**: stop the 64-sixty-fourth integer base from leaking into
> author-facing APIs. After this work, song-provider code never
> mentions an integer sf count, and the *internal* storage model
> (currently `int sixtyFourths`) becomes a self-contained
> implementation detail that can be refactored later (to 192-base,
> rational, or anything else) with zero impact on song files.

---

## The leak today

Authored songs almost-but-not-quite work in pure abstractions
(`BaseValue` constants like `EIGHTH`, `QUARTER`, `WHOLE`, plus the
`StaffPhraseBuilder` DSL). The `64` shows through in **three** places:

1. **`Bar.of(int expectedSixtyFourths, PhraseNode... nodes)`** —
   used by every hand-built drum bar:
   ```java
   private static final int BAR_SF = 64;
   return Bar.of(BAR_SF, k, h, s, h, k, h, s, h);
   ```

2. **`Duration.ofSixtyFourths(int)`** — used in silent-bar helpers
   and a few one-off durations:
   ```java
   new RestNode(Duration.ofSixtyFourths(BAR_SF));
   ```

3. **The `Bar.expectedSixtyFourths()` accessor** — leaks via
   visualization and converter code, but only via *internal*
   call-sites; no song file currently calls it.

The cookbook's "Bar arithmetic — sixty-fourths conversion table"
section exists *only* because of #1 and #2.

---

## Five-step plan

### Step 1 — introduce `BarDuration` (logical bar size)

A new `notation-core/duration/BarDuration.java` record that
expresses a bar's size as **N copies of a `BaseValue`** — the
musical-notation natural form.

```java
public record BarDuration(int unitCount, BaseValue unit) { … }
```

Examples:

| Time sig | `BarDuration` |
|---|---|
| 4/4 | `BarDuration(4, QUARTER)` |
| 3/4 | `BarDuration(3, QUARTER)` |
| 6/8 | `BarDuration(6, EIGHTH)` |
| 3/8 | `BarDuration(3, EIGHTH)` |
| 5/4 | `BarDuration(5, QUARTER)` |
| 12/8 | `BarDuration(12, EIGHTH)` |
| 2/2 (cut time) | `BarDuration(2, HALF)` |

Note: 6/8 and 3/4 share the same total duration (48 sf = dotted
half) but are *different* `BarDuration` values — the (count, unit)
pair preserves meter character. Useful later for stress patterns,
beaming, even score rendering.

`BarDuration` carries:
- the (count, unit) pair (via the record components)
- a derived `totalDuration()` returning a `Duration`
- a derived `sixtyFourths()` for internal-converter use

`TimeSignature` and `BarDuration` get a bidirectional bridge
(`TimeSignature.barDuration()` and `BarDuration.toTimeSignature()`).

### Step 2 — add `Bar.of(BarDuration, PhraseNode...)` overload

Two-line addition in `Bar.java`:

```java
public static Bar of(BarDuration expected, PhraseNode... nodes) {
    return new Bar(expected.sixtyFourths(), List.of(nodes));
}
```

The existing `Bar.of(int expectedSixtyFourths, …)` stays — gets
`@Deprecated` later. No breaking change in this step.

### Step 3 — migrate song files to use `BarDuration` constants

Pattern change in every song file with hand-built drum bars
(currently 5 files). Before:

```java
private static final int BAR_SF = 64;
…
return Bar.of(BAR_SF, k, h, s, h, k, h, s, h);
return Bar.of(BAR_SF, (PhraseNode) new RestNode(Duration.ofSixtyFourths(BAR_SF)));
```

After:

```java
private static final BarDuration BAR_4_4 =
        new BarDuration(4, QUARTER);
…
return Bar.of(BAR_4_4, k, h, s, h, k, h, s, h);
return Bar.of(BAR_4_4, (PhraseNode) new RestNode(BAR_4_4.totalDuration()));
```

For 3/8 (`SoulTechnoFurElise`):
```java
private static final BarDuration BAR_3_8 = new BarDuration(3, EIGHTH);
```

For 6/4 (`U2RockTianHeiHei`):
```java
private static final BarDuration BAR_6_4 = new BarDuration(6, QUARTER);
```

### Step 4 — update the cookbook

Delete the "Bar arithmetic — sixty-fourths conversion table"
section in `arrangement-cookbook.md`. Replace with a one-liner:

> **You don't think in sixty-fourths.** Hand-built drum bars use
> a `BarDuration` constant matching the piece's time signature
> (e.g. `new BarDuration(4, QUARTER)` for 4/4). Bar size validation
> is automatic.

### Step 5 — deprecate `Bar.of(int, …)` and `Duration.ofSixtyFourths(int)` for author use

Add `@Deprecated(forRemoval = false)` to both. Note in javadoc:
"Internal converters only. Authors: use `BarDuration` /
`BaseValue` constants."

Don't actually remove them — they're still used internally.

---

## What's left dealing in sf after this — and why it's fine

| Code that still uses sf | Why it's appropriate |
|---|---|
| `Quantizer` | Snapping fractional ms→grid is *literally* what a quantizer does; sf is its native unit |
| `BarBuilder` / `DrumBarBuilder` | MIDI→Bar converters; internal arithmetic |
| `PieceConcretizer` | Bar→ms event converter; internal |
| `Bar.expectedSixtyFourths()` (the getter) | Derived value; used only by visualization / MIDI emission / TUI |
| `Duration.ofSixtyFourths(int)` (the factory) | Used by the converters above; still public but deprecated for author use |
| JSON serializers | Storage format; internal |

None of these are author-facing. The cookbook never mentions them.

---

## Optional follow-on: storage-model refactor

Once this leak is sealed, swapping the storage from `int sf` to
something tuplet-friendly becomes a self-contained change inside
`notation-core/duration/`:

- **Option Y — bump base to 192**: triplets become integers
  (triplet-eighth = 16). 5/7-tuplets still don't divide evenly.
  ~250 LOC mechanical, all inside `notation-core`.
- **Option X — rational `Duration(long num, long den)`**: every
  tuplet representable exactly. ~600 LOC, deeper change including
  JSON and visitor sites, but all inside `notation-core` and the
  converter modules. **Song files don't change at all** because
  they only see `BaseValue` constants.

Discussed in detail in the in-message thread that prompted this
plan. Decision deferred until tuplet-feel material becomes a
priority.

---

## Effort summary

| Step | LOC | Status |
|---|---:|---|
| 1. `BarDuration` record + tests | ~80 | ✅ Done |
| 2. `Bar.of(BarDuration, ...)` overload + Bar carries `BarDuration` field + reverse-math `BarDuration.fromSixtyFourths(int)` for backward-compat int factory | ~120 | ✅ Done — went deeper than originally planned: absorbed Mixed-Meter Phase 1 |
| 3. Song-file migration (5 files) | ~50 mechanical | **Optional** — songs work via reverse math from `Bar.of(int, …)` → `BarDuration.fromSixtyFourths(int)`; migrate when convenient |
| 4. Cookbook update | ~30 lines deleted | Pending — lower priority while legacy form still works |
| 5. `@Deprecated` annotations | ~20 | Pending |

---

## Status (current)

- **Step 1 ✅ done**: `BarDuration(unitCount, unit)` record landed
  with full test coverage.
- **Step 2 ✅ done**: `Bar.of(BarDuration, …)` and `Bar.silent(BarDuration)`
  overloads added. **Additionally — and not originally scoped**:
  `Bar`'s record component changed from `int expectedSixtyFourths`
  to `BarDuration expectedDuration`, with a derived
  `expectedSixtyFourths()` getter for backward-compat.
  `BarDuration.fromSixtyFourths(int)` reverse-math factory added so
  the legacy `Bar.of(int, …)` form (and `new Bar(int, list)`) keeps
  working transparently. All ~120 LOC of mechanical migration to
  internal call sites done. Reactor green.
  - **Side effect**: this completes Phase 1 of the
    [mixed-meter plan](mixed-meter-plan.md) for free.
- **Steps 3–5 deferred** as optional cleanup. The legacy
  `Bar.of(int, …)` form decodes via `BarDuration.fromSixtyFourths`,
  so migrating song files to the explicit `BarDuration` constants
  is a polish item, not a correctness requirement. Pick up when:
  - A song author wants explicit meter character (e.g. authoring
    a 6/8 piece — the heuristic defaults to 3/4 on equivalent sf
    totals, and the author can override by constructing
    `BarDuration` directly).
  - Cookbook becomes confusing because of the leftover legacy form.
  - The eventual rational-Duration refactor pulls in the deprecation
    annotations as part of broader cleanup.

## What this enables

After Step 2's deeper change:

- `Bar` *honestly carries its meter* (when constructible from a
  `BarDuration` source). Mixed-meter pieces are structurally
  representable today.
- The future rational-Duration refactor
  ([duration-rational-plan.md](duration-rational-plan.md)) becomes
  even more contained: `Bar`'s storage is already abstracted; only
  `Duration` and `BarDuration` themselves need to change.
- Authors who want exact meter character today can construct
  `new BarDuration(6, EIGHTH)` directly; legacy `BAR_SF = 64`
  pattern still works via reverse math.
