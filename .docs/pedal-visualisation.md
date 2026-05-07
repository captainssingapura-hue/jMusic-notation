# Pedal Visualisation — Design

> **Status: Designed. Implementation incoming.**
> Companion to the audio-side sustain-pedal feature
> (see `MidiCodec.toMidi`'s CC #64 emission + `PedalInjector` post-process).

## Goal

Make the GUI pitch-roll visibly reflect the sustain-pedal regions of the
loaded piece, so it's obvious at a glance:

- where the pedal is currently held,
- when one held region ends and the next begins (especially across
  `<pedal type="change"/>` boundaries — release-and-re-press happens in
  ~1 ms, easy to miss),
- when different tracks of a part have *different* pedaling (rare today,
  but the model already supports it — see `Pedaling.byTrack`).

## Approach — per-lane background tint with cycling palette

Each pitch-roll lane reads its own `PedalControl` from
`Pedaling.byTrack`. For every consecutive pedal-down region in that
lane, the lane background fills with a **translucent warm tint**, where
the **tint colour cycles through a small palette** so adjacent regions
are always different colours.

```
┌───────────┬──────────────────────────────────────────────────┐
│ Treble    │ AAAAAAAAAA  ♩ ♩ BBBBBBB  CCCCCCCCC  AAAAAAAA   │ ← lane 1
├───────────┼──────────────────────────────────────────────────┤
│ Bass      │ AAAAAAAAAA  ♩    BBBBBBB  CCCCCCCCC  AAAAAAAA   │ ← same pedaling
├───────────┼──────────────────────────────────────────────────┤
│ Auto Drum │  (no tint — drum tracks don't pedal)            │
└───────────┴──────────────────────────────────────────────────┘

  A · pale amber       B · pale rose       C · pale sage      D · pale lavender
```

When all tracks of a part share their pedaling (typical piano case), the
lane tints **align vertically** — visually reads as one continuous band
across the lanes, with clean colour boundaries between consecutive
regions. When pedaling differs per track, lanes evolve independently.

## Palette

Four muted, low-alpha tints — warm-leaning so the aesthetic stays
coherent against the dark theme (`#1e1e2e`):

```
A · pale amber       rgba(245, 194, 124, 0.12)
B · pale rose        rgba(245, 154, 162, 0.12)
C · pale sage        rgba(166, 218, 149, 0.10)
D · pale lavender    rgba(180, 168, 245, 0.11)
```

All α ≤ 0.12 — notes stay readable through the tint.

## Cycling rule

A **per-lane counter** rotates the palette as we walk the lane's
`PedalControl.changes()`:

```java
int region = 0;
PedalState last = PedalState.UP;
long downAt = -1;
for (PedalChange ch : control.changes()) {
    if (ch.state() == PedalState.DOWN) {
        downAt = ch.tickMs();
    } else if (ch.state() == PedalState.UP && downAt >= 0) {
        emitTint(downAt, ch.tickMs(), palette[region % palette.length]);
        region++;
        downAt = -1;
    } else if (ch.state() == PedalState.CHANGE && downAt >= 0) {
        // Close current region; open the next one with a fresh colour.
        emitTint(downAt, ch.tickMs(), palette[region % palette.length]);
        region++;
        downAt = ch.tickMs();
    }
}
// Trailing DOWN with no closing UP → tint to end of lane.
if (downAt >= 0) emitTint(downAt, laneEndMs, palette[region % palette.length]);
```

Properties:

- **Adjacent regions always differ** — `region` increments on every close.
- **Deterministic** — the same source produces the same colour sequence
  every time. No flicker on play/pause/restart.
- **Per-lane** — each lane has its own counter; lanes that share a
  pedaling timeline tile vertically into matching colours.

## Edge cases

| Case | Behaviour |
|---|---|
| Source has no `<pedal>` markings | `Pedaling.byTrack` is empty → no tint anywhere. Lanes look default. |
| Lane's `TrackId` not in `Pedaling.byTrack` | No tint on that lane (e.g. auto-drum lane never tints). |
| Pedal toggle disabled in controls | No tint at all (visual matches audio). Toggle on → tint reappears. |
| `CHANGE` event | Closes the current region (with its colour) and opens the next (with the next colour). Visible as a colour flip even when the gap is 1 ms. |
| Trailing `DOWN` with no `UP` | Tint extends to the last note's `tickMs` on that lane. |
| Live pedal toggle while playing | Sequence rebuild silences CC + tint clears (or appears) on the next render frame. |

## Rendering integration

Hooks in `PitchScroll`:

- `setPedaling(Pedaling pedaling, Map<TrackId, Integer> trackToLaneIndex)`
  — called by `NotationApp` whenever `currentPiece` changes.
- `setPedalEnabled(boolean)` — gates the tint same way the audio path
  is gated.
- The lane painter draws the tint background **before** the notes for
  that lane, so notes always render on top.

About 80 lines net change (+ ~30 lines of palette + cycler in a small
helper class). No new types in the model — uses the existing
`Pedaling` / `PedalControl` / `PedalChange` directly.

## Why per-track + cycling, not the alternatives

| Alternative | Trade-off |
|---|---|
| **Single global tint** | Hides per-track differences. Adjacent regions blur together. |
| **Single colour, varying alpha** | Adjacency still hard to see. Three brightnesses isn't very distinct. |
| **Time-axis "Ped." / "*" markers** | Traditional but tiny; no continuous indicator. We considered it as Option 1; the tint is the user's pick. |
| **Dedicated pedal lane below** | Useful but takes vertical space and can't show per-track differences without multiple lanes. |
| **This: per-lane cycling tint** | Continuous, ambient, per-track, adjacent-region-distinguishing. Costs 80 lines of canvas drawing. |

## Future hooks (not V1)

- **Per-region tooltip** — hover a tint region → show "Pedal · bar 14
  · 2.3s". Cheap to add once the region geometry is computed.
- **CHANGE marker** — small `▽` glyph at change ticks for the
  score-reading aesthetic, layered on top of the tint flip.
- **Sostenuto / una corda** (CC #66 / #67) — same data shape, would
  warrant a second cycling palette in a different hue family (e.g.
  cool-blues) so the visualisations don't collide.
