# Auto Drum — Strategies

> **Reading order**: start at [auto-drum-overview.md](auto-drum-overview.md);
> this doc is the SPI reference + extension guide + current catalogue.

## The SPI in one screen

```java
public interface DrumStrategy {
    String id();             // stable kebab-case id
    String displayName();    // shown in picker
    String description();    // tooltip

    /** Default rule: don't overlay if the source already has drums. */
    default boolean appliesTo(Piece source) {
        return source.tracks().stream().noneMatch(t -> t instanceof DrumTrack);
    }

    /** Primary generator. */
    Optional<DrumTrack> generate(Piece source, Energy energy);

    /** Convenience: defaults to MEDIUM. */
    default Optional<DrumTrack> generate(Piece source) {
        return generate(source, Energy.MEDIUM);
    }
}
```

Strategies are stateless singletons. Register your instance in
[DrumStrategies.AVAILABLE](../notation-autodrum/src/main/java/music/notation/autodrum/DrumStrategies.java)
and it shows up in the UI picker automatically.

## The path most strategies take

Hand-rolling the bar-walk is rarely needed. Two helpers bring most
strategies down to a single resolver method:

```java
import music.notation.autodrum.PatternResolver;
import music.notation.autodrum.PatternSpec;
import music.notation.autodrum.Patterns;
// …

@Override
public Optional<DrumTrack> generate(Piece source, Energy energy) {
    if (!appliesTo(source)) return Optional.empty();
    return Patterns.generateTrack("Auto Drum", source, energy,
            (PatternResolver) this::resolvePattern);
}

private PatternSpec resolvePattern(BarDuration bd, Energy energy,
                                    BarFeatures features, int barIndex) {
    // Return null → graceful fallback bar (kick on 1).
    // Return a PatternSpec → it's repeated to fill the bar.
}
```

`Patterns.generateTrack` does the bar-walking, the per-bar
`SourceAnalysis` lookup, and the fallback substitution. Your job is the
`(BarDuration, Energy, BarFeatures, barIndex) → PatternSpec` decision.

### `PatternSpec` — the per-bar declaration

```java
new PatternSpec(BaseValue unit, PercussionSound[] sequence)
```

- `unit` — subdivision: `QUARTER`, `EIGHTH`, `SIXTEENTH`. Same unit for
  every slot in the sequence.
- `sequence` — what hits at each slot. `null` = rest, otherwise a
  `PercussionSound` (`BASS_DRUM`, `ACOUSTIC_SNARE`, `CLOSED_HI_HAT`,
  `OPEN_HI_HAT`, `RIDE_CYMBAL`, `SIDE_STICK`, `CRASH_CYMBAL`, …).
- The sequence's total duration must equal the bar's duration. Patterns
  helper validates this; mismatches throw with a clear message.

Examples:

```java
// Rock 8ths in 4/4 — 8 eighth slots
new PatternSpec(BaseValue.EIGHTH, new PercussionSound[] {
    BASS_DRUM,      CLOSED_HI_HAT,
    ACOUSTIC_SNARE, CLOSED_HI_HAT,
    BASS_DRUM,      CLOSED_HI_HAT,
    ACOUSTIC_SNARE, CLOSED_HI_HAT });

// Funk 16ths with rests
new PatternSpec(BaseValue.SIXTEENTH, new PercussionSound[] {
    BASS_DRUM, null, ACOUSTIC_SNARE, null,
    null,      null, ACOUSTIC_SNARE, null,
    BASS_DRUM, null, BASS_DRUM,      null,
    ACOUSTIC_SNARE, null, ACOUSTIC_SNARE, null });
```

## Density-aware variants

`BarFeatures` is the third resolver argument. Use `features.bucket()`
to switch per source-bar density:

```java
private PatternSpec resolvePattern(BarDuration bd, Energy energy,
                                    BarFeatures features, int barIndex) {
    if (!FOUR_FOUR.equals(bd)) return null;        // graceful fallback
    return switch (features.bucket()) {
        case EMPTY    -> null;                     // helper inserts kick-on-1
        case SPARSE   -> standardFor(energy);
        case STANDARD -> standardFor(energy);
        case DENSE    -> THINNED;                  // give the melody air
    };
}
```

`barIndex` is the 0-based bar position — useful for periodic embellishments
(e.g. `RockBeatStrategy` adds a 16th-hat fill every 4th SPARSE bar by
checking `barIndex % 4 == 3`).

`BarFeatures` exposes:

- `density()` — non-rest notes per beat, averaged across melody tracks
- `activeRatio()` — fraction of the bar covered by note durations (0..1)
- `silent()` — convenience for "every track in this bar is rests"

`bucket()` derives:

| Bucket | Range | Meaning |
|---|---|---|
| `EMPTY` | density == 0 || silent | rest bar in the source |
| `SPARSE` | density ≤ 1.0 | half-notes / dotted-quarters territory |
| `STANDARD` | 1.0 < density ≤ 3.0 | eighth-note territory |
| `DENSE` | density > 3.0 | sixteenth runs, ornaments |

## Current catalogue

All built-ins live in `notation-autodrum.strategies/`. Each is a single
~80-line file. Their density-awareness is summarised here; details in
the source.

| Strategy | Active for | LOW | MEDIUM | HIGH | DENSE response |
|---|---|---|---|---|---|
| **NoStrategy** | nothing | empty | empty | empty | — |
| **GentleClassical** | 2/4, 3/4, 4/4, 3/8, 6/8, 12/8 | quarter K/S | quarter K/S/S | quarter K/S/S/S | none — uses its own quiet pattern on EMPTY bars |
| **Rock 8ths** | 4/4 | quarter K/S | 8ths K/H/S/H | + crash on 1 | thinned to quarters; SPARSE-every-4th adds 16th-hat fill |
| **Disco** | 4/4 | 4 kicks | + closed-hat 8ths | + open-hat + snare | bare four-on-floor kicks |
| **Shuffle** | 6/8, 9/8, 12/8 | dotted-quarter K/S | + closed hat | + ride | none |
| **Funk** | 4/4 | syncopated 8ths | + hi-hat | 16ths with ghost-snares | thinned to quarter K/S |
| **Jazz** | 4/4 | quarter ride | ride + chick straight 8ths | + sparse kick + ride | ride-only quarters |
| **Metal** | 4/4 | quarter K/S | 8th kicks | 16th double-bass + crash | thinned to quarter K/S |

## Adding a new strategy — step by step

1. **Decide on time-signature scope.** 4/4 only is the easy case;
   compound-time (6/8 / 12/8) requires dotted-quarter beats which the
   `PatternSpec` model handles indirectly (see `GentleClassical`'s
   compound-time helper for an example of bypassing `Patterns.buildBar`).
2. **Sketch the pattern table.** One row per `(Energy, DensityBucket)`
   you care about. Most strategies define LOW/MEDIUM/HIGH × STANDARD
   and add a thinned DENSE variant.
3. **Implement `DrumStrategy`.** Single file in
   `notation-autodrum/src/main/java/music/notation/autodrum/strategies/`.
   Keep cached `PatternSpec` instances as `private static final`
   constants — they're value-typed and immutable.
4. **Register.** Add a public constant to
   [DrumStrategies](../notation-autodrum/src/main/java/music/notation/autodrum/DrumStrategies.java)
   and append to `AVAILABLE`.
5. **Test.** Smoke-test in `StrategyCatalogTest`'s loop (it iterates
   every registered strategy at every energy on a 4/4 fixture).
   Strategy-specific assertions go in a per-strategy test file beside
   `GentleClassicalStrategyTest`.

## Things to keep in mind

- **Drum bars are sequential.** `PatternSpec` reflects that — you
  declare a single sound per slot. To approximate a kick + hat layered
  hit you alternate them in adjacent subdivisions.
- **Velocity isn't yet in the model.** When the velocity story lands
  (see realism plan), strategies may declare per-slot velocities too.
  Until then, dynamic shaping happens at the Humaniser layer (timing
  jitter only) or by sound substitution (closed → open hat).
- **`appliesTo(Piece)` is your veto.** Override if your strategy
  shouldn't apply to certain pieces (e.g. only valid for source pieces
  that have a bass-line track once accent-matching lands).
- **`Patterns.fallbackBar(BarDuration)`** is your friend. Returning
  `null` from the resolver triggers it; you don't have to handle
  unsupported meters explicitly.

## Where the work would land for Tier 2 strategies

The realism plan ([auto-drum-realism-plan.md](auto-drum-realism-plan.md))
covers source-aware accent matching and structural fills. Both extend
the strategy contract through the existing `PatternResolver` shape —
no SPI break expected. New strategies that depend on those features
will request additional fields on `BarFeatures` (e.g. `accentSlots`,
`isPhraseStart`) once those features exist.
