# Arrangement Cookbook — Building Style-Based Songs

How to take a melody (children's song, folk tune, public-domain melody)
and dress it up as a full band arrangement (rock ballad, grunge, soul
techno, U2 stadium-rock, etc.) inside this Java music-notation system.

This is the playbook learned from `XuWeiXiaoHongMao`,
`GrungeInternationale`, `U2RockTianHeiHei`, and
`SoulTechnoFurElise`. Read it once before writing the next one — most
of the time-wasters are documented gotchas now.

---

## 0. The 5-step workflow

1. **Extract** the source melody (MIDI in, text out).
2. **Choose** a style and decide section structure.
3. **Identify** the role for each instrument lane.
4. **Write** the provider class, one section at a time.
5. **Register, test, listen.**

Each step is below.

---

## 1. Extract the melody

If your source is a MIDI file, run `MidiExtractor` to get a
copy-paste-ready text dump:

```
mvn -pl notation-play exec:java \
  -Dexec.mainClass=music.notation.play.MidiExtractor \
  -Dexec.args='"<path>/melody.mid" "<out-dir>"'
```

The output looks like:
```
| 1
o5 C EIGHTH
o5 D EIGHTH
o5 G QUARTER
| 2
…
```

Each `| N` is a bar marker; the lines underneath translate directly
into `b().bar().o5(EIGHTH, C).o5(EIGHTH, D).o5(QUARTER, G).done()`.
Time signature, key, and tempo show in the header.

**Tip**: if the source is in 2/4 or 3/4 and your target arrangement
will be 4/4, fold pairs/triplets of source bars into one target bar
*now* — easier than after you've started writing accompaniment.

---

## 2. Choose style + section structure

A "regular pop song" is roughly **3:00–3:45**. At a target tempo of
80–95 BPM in 4/4, that's **70–90 bars**. Standard scaffolding:

```
Intro (8) · Verse (12) · Chorus (8) · Interlude (4) ·
Verse (12) · Chorus (8) · Bridge (8–16) · Chorus (8) · Outro (4)
```

Total: 72–80 bars. Encode the section lengths as `static final int`
constants at the top of the file so a single change reflows
everywhere:

```java
private static final int INTRO    = 8;
private static final int VERSE    = 12;
private static final int CHORUS   = 8;
private static final int INTERLUDE= 4;
private static final int BRIDGE   = 16;
private static final int OUTRO    = 4;
private static final int TOTAL    = INTRO + VERSE + CHORUS + INTERLUDE
                                  + VERSE + CHORUS + BRIDGE + CHORUS + OUTRO;
```

`TOTAL` is the bar count every "always-on" track (Edge, Acoustic,
Bass, Pad, Drums) needs to fill.

### Style → tempo + chord-progression starting points

| Style | BPM | Key area | Chord cycle |
|---|---:|---|---|
| 许巍 rock ballad | 84–92 | C major / G major | C – Am – F – G |
| U2 stadium rock | 110–122 | E / A major | I – V – vi – IV |
| Grunge | 88–100 | minor (Am, Em, Dm) | i – ♭VI – ♭III – ♭VII |
| Soul techno | 116–124 | Am / Em | Am – F – C – G |
| Bach-style classical | 60–76 | C / G major | varied per phrase |

These are *starting* points. The song's own melody will pull you
toward specific changes; trust the ear.

---

## 3. Lane roles — the 7-track template

Almost every arrangement we've written uses some subset of these
seven lanes. The *role* is more important than the exact GM patch.

| Lane | Role | Default GM patch | Per-style override |
|---|---|---|---|
| **Vocal** | Carries the literal melody. Stand-in for a singer. | `ACOUSTIC_GRAND_PIANO` | `CHOIR_AAHS` for ballad, `LEAD_5_CHARANG` for synth-pop |
| **Lead Guitar** | Fills + sustains + harmonies + solos. Never doubles the melody during verses. | `OVERDRIVEN_GUITAR` | `DISTORTION_GUITAR` for grunge, `SYNTH_LEAD_SAWTOOTH` for techno |
| **Edge / Rhythm Guitar** | Repeating arpeggios that establish harmony. | `ELECTRIC_GUITAR_CLEAN` | `OVERDRIVEN_GUITAR` for grunge |
| **Acoustic** | Whole-note chord voicings underneath. | `ACOUSTIC_GUITAR_STEEL` | `ACOUSTIC_GUITAR_NYLON` for nylon-string ballad |
| **Bass** | Roots, fifths, walking patterns. | `ELECTRIC_BASS_PICK` | `ELECTRIC_BASS_FINGER` for rock, `SLAP_BASS` for funk/techno |
| **Pad** | Sustained chord wash. | `SYNTH_PAD_WARM` | `STRING_ENSEMBLE` for orchestral |
| **Drums** | Backbeat + fills. | (drum kit on channel 9) | — |

**The single most important principle** (learned the hard way on
`XuWeiXiaoHongMao`):

> **Vocal carries the melody. Lead Guitar plays around it.**
>
> If your "Lead Guitar" track is just rendering the literal melody
> note-for-note, you have a *backing track playing the vocal line*
> — the listener can't tell where the singer would go. Move the
> melody to a piano-like Vocal lane and use the lead guitar for
> fills, sustained color, harmonies, and solos.

---

## 4. Pattern catalogue — what each lane plays per section

### 4.1 Lead Guitar — three patterns by intensity

| Pattern | Where to use | What it sounds like |
|---|---|---|
| **A — sparse fills** | Verses, intro tail | Silent for 3 bars, then a 1-bar pickup lick at the end of each 4-bar phrase |
| **B — sustained color** | First chorus | Quarter-note "punch" then dotted-half held color tone (7th, 9th, or high octave) |
| **C — harmonised line** | Repeat choruses, final chorus | Same rhythm as the vocal but a diatonic third above (E for C, F for D, G for E…) — sounds like an octave-apart vocal harmony |
| **Solo** | Bridge | Free melodic line referencing the song theme; build from low/medium register to a high climax (~C7) and resolve back |

### 4.2 Edge / Rhythm Guitar — repeating arpeggios

One eighth-note arpeggio per chord, cycling through the
progression. For a C–Am–F–G cycle:

```java
case 0 -> bb.bar(EIGHTH).o4(C).o4(E).o4(G).o5(C).o5(E).o5(C).o4(G).o4(E).done(); // C
case 1 -> bb.bar(EIGHTH).o4(A).o5(C).o5(E).o5(A).o5(E).o5(C).o4(A).o5(C).done(); // Am
case 2 -> bb.bar(EIGHTH).o4(F).o4(A).o5(C).o5(F).o5(C).o4(A).o5(F).o5(C).done(); // F
case 3 -> bb.bar(EIGHTH).o4(G).o4(B).o5(D).o5(G).o5(D).o4(B).o5(G).o5(D).done(); // G
```

This pattern works for U2/Edge (clean), Xu Wei (clean+delay), and
many ballad styles. For grunge or harder rock, swap the arpeggio
for power-chord eighths.

### 4.3 Acoustic / Pad — whole-note chord voicings

Same chord cycle as Edge. Each bar holds one whole-note voicing:

```java
case 0 -> bb.bar().o3(WHOLE, C, G, C.higher(1), E.higher(1)).done();  // C
case 1 -> bb.bar().o3(WHOLE, A, E.higher(1), A.higher(1), C.higher(2)).done(); // Am
case 2 -> bb.bar().o3(WHOLE, F, C.higher(1), F.higher(1), A.higher(1)).done(); // F
case 3 -> bb.bar().o3(WHOLE, G, D.higher(1), G.higher(1), B.higher(1)).done(); // G
```

Use `o3 + .higher(1)` or `.higher(2)` to span octaves cleanly.
Acoustic and Pad can share the same voicings — they layer.

### 4.4 Bass — two-tier complexity

Sparser in intro/outro (whole-note roots), denser in body (quarter
roots + fifths):

```java
if (bar < INTRO) {
    switch (bar % 4) {
        case 0 -> bb.bar().o3(WHOLE, C).done();     // C root
        case 1 -> bb.bar().o2(WHOLE, A).done();     // Am root
        case 2 -> bb.bar().o3(WHOLE, F).done();     // F root
        case 3 -> bb.bar().o2(WHOLE, G).done();     // G root
    }
} else {
    switch (bar % 4) {
        case 0 -> bb.bar(QUARTER).o3(C).o3(G).o3(C).o3(E).done();   // C
        case 1 -> bb.bar(QUARTER).o2(A).o3(E).o2(A).o3(C).done();   // Am
        case 2 -> bb.bar(QUARTER).o3(F).o3(C).o3(F).o3(A).done();   // F
        case 3 -> bb.bar(QUARTER).o2(G).o3(D).o2(G).o3(B).done();   // G
    }
}
```

### 4.5 Drum bar primitives

Library of single-bar `Bar` factories you can compose into a full
drum track. All produce `Bar.of(64, …)` (4/4 = 64 sf):

| Helper | Use for |
|---|---|
| `silentBar()` | Intro before drums enter, outro fade |
| `hatTickBar()` | Pre-drum buildup (4 quarter-note hi-hats) |
| `halfTimeBar()` | Verse — kick on 1, snare on 3, open-hat eighths |
| `backbeatBar()` | Chorus — kick 1+3, snare 2+4, closed-hat eighths |
| `crashBackbeatBar()` | Chorus downbeat — replaces beat-1 hat with crash |
| `rideHalfTimeBar()` | Bridge / verse 2 build — half-time but on ride cymbal |
| `snareFillBar()` | End of section — snare-tom-cascade into crash |
| `finalCrashBar()` | Last bar — crash + kick + snare + half rest |

Composing a typical drum track:

```java
private List<Bar> buildDrumBars() {
    var bars = new ArrayList<Bar>();
    // Intro 8 — silent until bar 7 ticks, bar 8 fill
    for (int i = 0; i < 6; i++) bars.add(silentBar());
    bars.add(hatTickBar());
    bars.add(snareFillBar());
    // Verse 12 — half-time
    for (int i = 0; i < 12; i++) bars.add(halfTimeBar());
    // Chorus 8 — crash on bar 1 then backbeat
    bars.add(crashBackbeatBar());
    for (int i = 0; i < 6; i++) bars.add(backbeatBar());
    bars.add(snareFillBar());
    // … etc
    return bars;
}
```

---

## 5. The class skeleton

Boilerplate for every new arrangement. Save this as a starting
point.

```java
package music.notation.songs.<category>.<song>;

import music.notation.duration.Duration;
import music.notation.event.PercussionSound;
import music.notation.phrase.*;
import music.notation.play.PlayPiece;
import music.notation.structure.*;
import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

public final class <Style><Song> implements PieceContentProvider<<Song>> {

    static final KeySignature KEY = new KeySignature(C, Mode.MAJOR);
    static final TimeSignature TS  = new TimeSignature(4, 4);
    private static final int BAR_SF = 64;

    private static final int INTRO=8, VERSE=12, CHORUS=8, INTERLUDE=4,
                             BRIDGE=16, OUTRO=4;
    private static final int TOTAL = INTRO + VERSE + CHORUS + INTERLUDE
                                   + VERSE + CHORUS + BRIDGE + CHORUS + OUTRO;

    private StaffPhraseBuilderTyped b() {
        return StaffPhraseBuilderTyped.in(KEY, TS, QUARTER);
    }

    @Override public String subtitle() { return "<style description>"; }

    @Override
    public Piece create() {
        var id = new <Song>();
        var vocal    = joinMelodicPhrases("Vocal",       ACOUSTIC_GRAND_PIANO,  vocalPhrases());
        var lead     = joinMelodicPhrases("Lead Guitar", OVERDRIVEN_GUITAR,     leadPhrases());
        var edge     = joinMelodicPhrases("Edge Guitar", ELECTRIC_GUITAR_CLEAN, edgePhrases());
        var acoustic = joinMelodicPhrases("Acoustic",    ACOUSTIC_GUITAR_STEEL, acousticPhrases());
        var bass     = joinMelodicPhrases("Bass",        ELECTRIC_BASS_PICK,    bassPhrases());
        var pad      = joinMelodicPhrases("Synth Pad",   SYNTH_PAD_WARM,        padPhrases());
        var drums    = new DrumTrack("Drums", Phrase.of(buildDrumBars()));

        return Piece.ofTrackKinds(id.title(), id.composer(),
                KEY, TS, new Tempo(<bpm>, QUARTER),
                List.of(vocal, lead, edge, acoustic, bass, pad),
                List.of(drums));
    }

    // … phrase factories per track …

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new <Style><Song>());
    }
}
```

Each track exposes a `…Phrases()` method returning
`List<AuthorPhrase>` — one entry per section. `joinMelodicPhrases`
flattens them into one `Phrase` with the right elision.

---

## 6. Bar arithmetic — the discipline

**Every `Bar.of(BAR_SF, …)` call is validated**: if the contained
nodes' total sixty-fourths don't equal `BAR_SF` (64 for 4/4, 48 for
3/4, 24 for 3/8), construction throws `IllegalArgumentException`.

Memorise the conversions:

| Duration | Sixty-fourths |
|---|---:|
| `WHOLE` | 64 |
| `HALF.dot()` | 48 |
| `HALF` | 32 |
| `QUARTER.dot()` | 24 |
| `QUARTER` | 16 |
| `EIGHTH.dot()` | 12 |
| `EIGHTH` | 8 |
| `SIXTEENTH.dot()` | 6 |
| `SIXTEENTH` | 4 |
| `THIRTY_SECOND.dot()` | 3 |
| `THIRTY_SECOND` | 2 |
| `SIXTY_FOURTH` | 1 |

Common 4/4 (64-sf) bar shapes:
- `4 × QUARTER` = 64
- `2 × HALF` = 64
- `8 × EIGHTH` = 64
- `1 QUARTER + 1 HALF.dot()` = 16 + 48 = 64
- `1 QUARTER + 2 EIGHTHs + 1 HALF` = 16 + 16 + 32 = 64
- `1 EIGHTH.dot() + 1 SIXTEENTH + 1 HALF + 1 QUARTER` = 12 + 4 + 32 + 16 = 64

When in doubt, **do the addition before hitting save**. The PieceTestBase
test will catch you eventually but the error message points at the
top-level `create()` call, not the offending bar.

---

## 7. Wire it up

After writing the provider:

1. **Add the identity record** if it doesn't exist:
   ```java
   public record <Song>() implements MusicalPiece {
       @Override public String title()    { return "<title>"; }
       @Override public String composer() { return "<composer>"; }
   }
   ```

2. **Register in `DefaultCollection`**:
   ```java
   Entry.of(new <Song>(), new <Style1Song>(), new <Style2Song>())
   ```
   The first provider is the default; subsequent ones appear as
   variations in the picker.

3. **Add a test** that exercises `provider().create()`:
   ```java
   class <Style><Song>Test extends PieceTestBase {
       @Override
       protected PieceContentProvider<?> provider() {
           return new <Style><Song>();
       }
   }
   ```
   This catches every bar-arithmetic error at `mvn test` time —
   without it, the provider only validates when the user clicks Play.

4. **Run the reactor**:
   ```
   mvn -q test -T 1C
   ```
   Should be green. If not: the stack trace tells you which method
   threw; usually a duration sum is off.

5. **Render**:
   - GUI: `mvn -pl notation-ui javafx:run` → pick from the library.
   - Direct: `mvn -pl notation-songs exec:java -Dexec.mainClass=<full.class.name>`
     (the `main` method calls `PlayPiece.play(...)`).

---

## 8. The 5 most-common pitfalls

1. **Lead Guitar doubles the vocal.** Sounds like a karaoke MIDI.
   Always split: piano carries melody, guitar fills around it.

2. **Bar duration off by 4 sf.** Usually a hidden `pad(EIGHTH)` or
   confusing a `QUARTER.dot()` (24) with `HALF` (32). Re-add by hand.

3. **Forgetting `static import` for `PercussionSound.*`** when
   writing drum bars. Compiler error is clear (`cannot find symbol:
   variable BASS_DRUM`).

4. **Using `BASS_DRUM_1` / `CRASH_CYMBAL_1`** — those don't exist.
   The unsuffixed `BASS_DRUM` and `CRASH_CYMBAL` are the GM names
   in our enum.

5. **Hard-coded section bar counts in `buildDrumBars`** that don't
   match the `INTRO/VERSE/…` constants. When you change a section
   length, the drum lane silently drifts out of sync. Always loop
   `for (int i = 0; i < SECTION; i++)` rather than literal counts.

---

## 9. Per-style cheat sheet

### 许巍 rock ballad
- Tempo 84–92 BPM
- Vocal: ACOUSTIC_GRAND_PIANO
- Lead: OVERDRIVEN_GUITAR (李延亮 style — sparse fills + bridge solo)
- Edge: ELECTRIC_GUITAR_CLEAN with delay-feel arpeggios
- Acoustic: ACOUSTIC_GUITAR_STEEL whole-note chords
- Bass: ELECTRIC_BASS_PICK
- Pad: SYNTH_PAD_WARM
- Chord cycle: C – Am – F – G

### U2 stadium rock
- Tempo 110–122 BPM
- Vocal: ACOUSTIC_GRAND_PIANO or LEAD_5_CHARANG
- Lead: OVERDRIVEN_GUITAR
- Edge: ELECTRIC_GUITAR_CLEAN with dotted-quarter delay arpeggios
- Acoustic: ACOUSTIC_GUITAR_STEEL
- Bass: ELECTRIC_BASS_PICK driving eighths
- Organ: ROCK_ORGAN sustained
- Drums: half-time verse → anthemic chorus with crash on every downbeat
- Chord cycle: I – V – vi – IV (in any major key)

### Grunge
- Tempo 88–100 BPM
- Lead Guitar: DISTORTION_GUITAR
- Harmony Guitar: OVERDRIVEN_GUITAR
- Power Chords: DISTORTION_GUITAR
- Pad: SYNTH_PAD_WARM (atmospheric, not bright)
- Bass: ELECTRIC_BASS_FINGER
- Drums: rock pattern with crash every 8 bars
- Key: minor (Am, Em, Dm)

### Soul Techno
- Tempo 116–124 BPM (lift from any source)
- Lead Synth: SYNTH_LEAD_SAWTOOTH (carries melody)
- Rhodes: ELECTRIC_PIANO_1 (comping)
- Slap Bass: SLAP_BASS (groove)
- Pad: SYNTH_PAD_WARM (one chord per bar)
- Drums: 16th-note techno pattern (kick, hat, clap, hat)
- Key: Am (or whatever the source is in)
- Chord cycle: Am – F – C – G

---

## 10. References — actual implementations

| Provider | Showcases |
|---|---|
| `nursery/xiaohongmao/XuWeiXiaoHongMao.java` | Vocal/Lead split, Pattern A/B/C, 16-bar bridge solo |
| `anthem/internationale/GrungeInternationale.java` | Style template applied to existing song; multi-guitar layering |
| `folk/tianheihei/U2RockTianHeiHei.java` | Edge dotted-quarter arpeggios; section-marked phrases with elision |
| `classical/furelise/SoulTechnoFurElise.java` | 3/8 time signature; slap-bass groove; techno drums |
| `anthem/internationale/ManualInternationale.java` | Marching-band brass arrangement (different lane mix) |

When in doubt, **read the closest existing arrangement** and copy
its skeleton. They've all been polished against the same constraints
this guide describes.
