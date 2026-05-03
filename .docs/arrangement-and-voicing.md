# Arrangement & Voicing — Design Note (Future Reference)

## Context

Today every track on a `Piece` carries a single `Instrument` (GM enum)
as its `defaultInstrument`. The user can override per-track via the
instrument-picker dialog and the per-track UI button. Selections live
ephemerally in `NotationApp.selectedInstruments` and friends; no
higher-level grouping, no reusable "this is a string-quartet setup"
preset.

We want a **higher-level data model** that:

- Lets a piece declare what kind of ensemble it is for (SATB, jazz
  trio, string quartet, …) — **optional, auxiliary** metadata.
- Defines a stable set of **roles** for each ensemble type.
- Lets users pick a **voicing** that maps roles → instruments
  (potentially soundbank-specific patches), reusable across any piece
  of the matching ensemble type.
- Falls back gracefully to per-track authored defaults whenever an
  arrangement/voicing isn't applicable.

## Layered model

```
notation-core
├── ArrangementType        (open interface, extensible)
│   └── Role               (marker interface, nested)
│
├── Arrangement            (record: type + track-name → role)
│   record Arrangement(ArrangementType type, Map<String, Role> trackToRole)
│
└── MusicalPiece
    └── default Optional<Arrangement> arrangement() = Optional.empty()
```

```
notation-play
└── Voicing                (record: forType + role.name() → PatchRef)
    record Voicing(String name, ArrangementType forType,
                   Map<String, PatchRef> rolePatchesByName,
                   List<File> requiredSoundbanks)
```

```
notation-songs (or any third-party jar)
├── SATB        implements ArrangementType
│     └── SatbRole sealed interface permits Soprano, Alto, Tenor, Bass
├── JazzTrio    implements ArrangementType
│     └── JazzRole enum (LEAD, COMP, BASS, DRUMS) implementing Role
└── … plug-in arrangement types
```

## Open ArrangementType, sealed Role-per-impl

`ArrangementType` is an **open** interface — new types can be added in
any module or third-party jar. Each implementation declares its
**closed** set of roles via a sealed interface (or enum) that extends
`ArrangementType.Role`.

```java
public interface ArrangementType {
    String name();
    List<? extends Role> roles();

    /** Marker — every concrete role implements this. */
    interface Role {
        String name();
    }
}

public final class SATB implements ArrangementType {
    public sealed interface SatbRole extends ArrangementType.Role
            permits Soprano, Alto, Tenor, Bass { }
    public record Soprano() implements SatbRole { public String name() { return "Soprano"; } }
    public record Alto()    implements SatbRole { public String name() { return "Alto"; } }
    public record Tenor()   implements SatbRole { public String name() { return "Tenor"; } }
    public record Bass()    implements SatbRole { public String name() { return "Bass"; } }

    public String name()                 { return "SATB"; }
    public List<? extends Role> roles()  {
        return List.of(new Soprano(), new Alto(), new Tenor(), new Bass());
    }
}
```

For arrangements where roles carry no per-role state, a sealed enum
implementing `Role` is equally valid.

## Arrangement is auxiliary; defaults always work

```java
public interface MusicalPiece {
    String title();
    String composer();

    /** Auxiliary metadata. Default: no arrangement declared. */
    default Optional<Arrangement> arrangement() {
        return Optional.empty();
    }
}
```

- Pieces that don't override `arrangement()` work exactly as today.
- Per-track `defaultInstrument` is the **always-present fallback**.
- Voicings only apply to pieces that explicitly opt in.

Example opt-in:

```java
public record Traumerei() implements MusicalPiece {
    public String title()    { return "Träumerei (Op. 15 No. 7)"; }
    public String composer() { return "Robert Schumann"; }

    private static final ArrangementType TYPE = new SATB();
    private static final Arrangement ARR = new Arrangement(TYPE, Map.of(
            "Soprano", new SATB.Soprano(),
            "Alto",    new SATB.Alto(),
            "Tenor",   new SATB.Tenor(),
            "Bass",    new SATB.Bass()));

    public Optional<Arrangement> arrangement() { return Optional.of(ARR); }
}
```

## Voicing — keys-by-name

```java
public record Voicing(
        String name,                                // "Default SATB", "Pipe Organ Choir"
        ArrangementType forType,
        Map<String, PatchRef> rolePatchesByName,    // role.name() → patch
        List<File> requiredSoundbanks               // optional, for documentation
) { }
```

Why **keys by `role.name()`** instead of `Map<Role, PatchRef>`:
- JSON serialization out of the box.
- No `Role`-equality landmines across modules / class loaders.
- Lookup is trivial: `voicing.rolePatchesByName().get(role.name())`.

## Resolver — explicit fallback chain

```java
public final class VoicingResolver {

    /** Resolve per-track patches with explicit fallback to the track's default instrument. */
    public static Map<String, PatchRef> resolve(Piece piece, Voicing voicing) {
        var out = new LinkedHashMap<String, PatchRef>();
        Optional<Arrangement> arr = piece.arrangement();
        boolean compatible = voicing != null && arr.isPresent()
                && arr.get().type().name().equals(voicing.forType().name());

        for (Track track : piece.tracks()) {
            String name = track.name();
            PatchRef patch = null;
            if (compatible) {
                Role role = arr.get().trackToRole().get(name);
                if (role != null) {
                    patch = voicing.rolePatchesByName().get(role.name());
                }
            }
            if (patch == null) {
                // Fallback: per-track authored default instrument.
                patch = PatchRef.gm(defaultInstrumentOf(track));
            }
            out.put(name, patch);
        }
        return out;
    }
}
```

Fallback paths:
- No `arrangement()` on the piece → all tracks use per-track defaults
  (current behaviour).
- Arrangement present but no voicing selected → all tracks use per-track
  defaults.
- Voicing selected, but a track has no role / role has no patch → that
  track uses its default; other tracks honour the voicing.

`ChannelSetup.fromVoicing(piece, voicing, volumes, pans)` builds on the
resolver, producing the same `ChannelSetup` shape used today.

## Decisions pinned

1. **`ArrangementType` is open**; concrete types live anywhere
   (notation-songs or third-party jars).
2. **`Role` is sealed per implementation** — each `ArrangementType`
   declares its closed role set.
3. **`Arrangement` is auxiliary metadata on `MusicalPiece`** —
   `Optional<Arrangement> arrangement() default empty()`. Pieces that
   don't override are unaffected.
4. **Per-track `defaultInstrument` is the always-present fallback**.
5. **Voicing keys are role names** (strings), not `Role` instances —
   serialization-friendly, cross-module-safe.
6. **`Voicing.forType` matched by `name()`** (not class identity) for
   class-loader resilience.
7. **Voicing storage v1**: bundled in code. JSON-file discovery and
   user-saved via `Preferences` come in a follow-up phase.
8. **Imports (MIDI files)** never have an arrangement; per-track GM
   defaults drive playback. Heuristic guessing is out of scope.

## Validation rules

At `Arrangement` construction:
- All values in `trackToRole` must be members of `type.roles()`
  (verified by `name()` equality against the type's role list).
- Duplicate role mappings to multiple tracks are allowed (polyphonic
  voices on a single role are valid).

At resolution time:
- Track names referenced in `trackToRole` that don't exist on the
  piece → silently skipped; those tracks fall back to defaults.
- Roles in `voicing.rolePatchesByName` that don't exist in the
  arrangement type → ignored.

Both errors surface as informational status messages in the UI, not
exceptions — the playback path always produces *something*.

## UI surface (deferred to a later phase)

- New **Voicing combo** in the Controls drawer, between "Arrangement"
  (provider) and "Scale".
- Filtered to voicings whose `forType.name()` matches the current
  piece's arrangement type. Disabled when piece has no arrangement.
- Selecting a voicing applies the resolved per-track patches; per-track
  buttons re-label automatically.
- Per-track button still permits one-off overrides on top of a voicing.
- "Save current as Voicing…" menu item captures the current per-track
  state as a new user voicing (Preferences-backed).

## Implementation phases

| Phase | Scope | Lines |
|-------|-------|-------|
| 1 | `ArrangementType` + `Role` + `Arrangement` in notation-core; `MusicalPiece.arrangement()` default | ~80 |
| 2 | `SATB` concrete impl in notation-songs + tests | ~80 |
| 3 | `Voicing` record + `VoicingResolver` + `ChannelSetup.fromVoicing` + tests | ~120 |
| 4 | Träumerei opts in + one bundled "Default SATB" voicing | ~50 |
| 5 | UI: Voicing combo in Controls drawer; listener wiring | ~100 |
| 6 (later) | ServiceLoader discovery + JSON voicing files + Preferences-backed user voicings | ~150 |

Phases 1–4 deliver an **end-to-end demo** without any UI changes
(testable via TUI / unit tests). Phase 5 adds the user-facing combo.
Phase 6 is the polish for community-extensible voicings.

## Migration impact

- **Zero behaviour change** for any existing song that doesn't opt in.
- **One song demo** (Träumerei) adds an `arrangement()` override.
- **One bundled voicing** ("Default SATB") provides the demo's role →
  GM-instrument mapping.
- **No breaking changes** to `Piece`, `MelodicTrack`, `ChannelSetup`.
  New code paths layer on top of the existing playback chain.

## Out of scope (this design)

- Heuristic arrangement detection for imported MIDI files.
- Per-section arrangement changes (a piece using SATB for verses and
  unison for a coda).
- Voicing inheritance / composition (e.g. "Pipe Organ Choir" extends
  "Default SATB" with overrides).
- Voicing marketplace / soundpack distribution unit.
- Cross-arrangement-type role mapping ("apply this jazz voicing to an
  SATB piece by aliasing JAZZ.LEAD ↔ SATB.SOPRANO"). Almost certainly
  unwanted.
