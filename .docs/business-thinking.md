# Business Thinking — Where the Infra Could Actually Earn Its Keep

> Captured from a strategy discussion. Not a plan, not a commitment.
> A snapshot of candid thinking about whether what we've been
> building has commercial legs, and where.

---

## What's actually distinctive in what we've built

Most of the infra (MIDI playback, quantization, piano roll) is
table-stakes — every DAW does it. The genuinely unusual pieces:

1. **Rational, tuplet-aware durations end-to-end.** Most software
   grid-snaps to PPQ ticks and accepts the lossy round-trip. The
   5-variant `Duration` ADT preserves the difference between
   "dotted sixteenth" and "triplet eighth" through analysis,
   manipulation, and emission. Matters when output quality is
   judged symbolically (sheet music, MusicXML, AI training
   corpora) rather than just "does it sound right."
2. **Voice-separation pipeline as composable tiers.** Tier-0
   chord coalesce → pitch-band → overlap-voice → role classifier.
   Most importers treat this as a single black box.
3. **"Music as Java code" with type safety.** Sealed ADTs, IDE
   refactoring, Git diffs, code review. A real workflow for a
   tiny audience.
4. **Pluggable collections via plain Maven modules.** Composer-as-
   library is a clean idea — most music software thinks in files,
   not packages.
5. **Cookbook + arrangement templates.** Apply U2 / Grunge /
   Soul Techno / Xu Wei to any melody. Beyond chord-loop
   playback that competitors do.

Items 1–2 are *quality* differentiators. Items 3–4 are *workflow*
differentiators with a small but real audience. Item 5 is the
*product* differentiator — the one that demos in 30 seconds.

---

## Business angles considered, ranked

### 1. Smart practice / performance accompanist (B2C prosumer)

Target: jazz/rock musicians using iReal Pro or Band-in-a-Box and
finding them musically thin. Differentiator: real cookbook
arrangements, not chord loops. Distribution: mobile app, iOS
first.

iReal Pro proves the market exists and pays (~$20, ~millions of
users). Band-in-a-Box has been a viable business for decades at
$129+. Our edge is musical quality, not feature breadth.

**Headwinds**: latency on JVM is dead — would need native audio
(Kotlin Multiplatform symbolic core, AVAudioEngine / Oboe
playback). Beat-following from audio input is hard; symbolic
MIDI input (USB/BLE keyboard) sidesteps it for v1 but narrows
audience to keyboardists. Consumer distribution is brutal.

### 2. Live-venue operations: "powered by [us]" small livehouse

The angle that surprised on the upside. Doesn't make existing-
format livehouses easier to open (rent + permits + PA dominate)
but plausibly enables a **new format** of micro-venue (40–80 cap)
whose unit economics only work because tech replaces staff:

- Sound engineer → song-aware scene recall on existing consoles
  (X32, SQ-5); one engineer covers 2–3 rooms remotely
- House band → cookbook engine plays for walk-up performers
- Lights / visuals → structure-aware DMX / Resolume drive
- Recording → auto-stem-separated clip per set, walks out as
  TikTok content

Reduced staffing model: 1 floor + remote engineer covering
multiple rooms. Enables 7-nights-a-week programming (open mic,
songwriter circle, jazz jam, livehouse karaoke) where traditional
venues are dark Mon–Wed.

**Two flavors**:

- **A. SaaS to venue operators**. $200–$500/mo per venue, need
  ~1000 venues, slow ground game, lifestyle business.
- **B. Operate venues yourself, prove the model, then franchise**.
  Venture-scale. WeWork-for-livehouse / KTV-2.0 thesis. Tech is
  the moat. Much riskier, much higher ceiling.

Cultural fit with the existing cookbook is striking — Xu Wei,
grunge, U2 stadium rock are exactly livehouse-genre staples.

**Headwinds**: cultural reception varies (mainland China post-
乐队的夏天 likely yes for amateur nights; Tokyo livehouse purists
no). Live audio reliability bar is high. Hardware-integration
drudgery (console scene recall, DMX, click, in-ears) is months
of plumbing. Rent and permits still gate startup cost.

**Cheap test**: partner with one struggling small venue in
Chengdu/Shanghai for a 3-month "powered by us" weekday-
programming pilot. If Mon–Wed door receipts go from ¥0 to
¥1500/night with 1.5 staff, the thesis is alive.

### 3. B2B symbolic-music ingest for ML / MIR companies

Suno/Udio/Riffusion live in audio, but a growing tier of startups
(symbolic generation, music-theory copilots, transcription)
struggle with clean MIDI → symbolic data. Licensed library that
does tuplet-correct, voice-separated import would solve a real
pain.

Strong technical fit. Demo-resistant — no consumer hook, technical
buyer. Best-margin business but worst beachhead.

**Headwinds**: ML world speaks Python, not Java. Need JNI / REST
or a thin Python facade.

### 4. Adaptive music engine for indie game devs

Music-theory-aware transitions across game states — modulate
keys, shift modes, change textures without hard cuts. FMOD/Wwise
own this but are clunky for indies. JVM/Kotlin/libGDX integration
is normal in indie game world. Smaller market, easier sale.

### 5. Transcription / MusicXML pipeline

For publishers, educators, arrangers. Triplet-aware import +
chunk decomposition is exactly what they need. Niche but pays
well per seat.

### 6. Music theory / education tooling

Type-safe music makes voice-leading checkers, harmony exercises,
counterpoint validators easy to build correctly. Fragmented
market; side bet.

### 7. Humanoid integration — force multiplier, not standalone

Wild card. The humanoid wave is real and disproportionately
Chinese (Unitree G1 ~$16–30K, UBTech, Xiaomi CyberOne, Fourier
GR-1, plus Figure / Optimus in the US). Useful to think about
because it intersects two of our existing angles.

**Where our infra meets a humanoid**:

- **Robot plays real instruments** (drums, shakers, percussion;
  piano years out). Existing humanoid music demos are
  embarrassingly metronomic because robot teams have no music-
  intelligence stack — they're solving balance and grasping. Our
  cookbook + role-aware patterns + tempo arrangement is exactly
  the missing layer. Shape: middleware/content licensing to
  humanoid companies.
- **Structure-aware movement** (dancing, conducting, gestures).
  Every viral robot-dancing video is beat-matched, none are
  *structure-aware*. A robot that anticipates the chorus drop
  because it parsed the song looks dramatically more intelligent
  than one that bobs to the beat. Highest-leverage piece of our
  stack for this context.
- **Livehouse fixture**: humanoid as the virtual-band drummer
  made *physical*. Same role as the cookbook arrangement engine,
  but with a real kit on stage. Addresses the "audience won't
  accept machine band" headwind directly — there's a visible
  performer up there even if robotic. The venue brand becomes
  "the room with the robot drummer." Makes the livehouse thesis
  genuinely venture-pitchable.

**Dismissed**:

- Music tutor / practice partner — beautiful, but Yousician at
  $15/mo kills the unit economics on hardware
- Embodied AI musician (improv on a real instrument) — marketing
  asset, not a business

### Strongest concrete bet — the $50K self-built demo

Two-step play, feasible inside 12 months:

**Step 1**: buy a single Unitree G1 (~$30K), 3D-print drumstick
end-effectors, build the drummer integration ourselves. Total
under $50K. Open APIs and active dev community make this a
solo-engineer-quarter project, not a research program.

This single demo serves three businesses simultaneously:

- Marketing asset for the smart-accompanist consumer product
  (people who'd never click a backing-track app share a robot-
  drummer video)
- MVP for the livehouse-fixture business (run it in one venue
  for a quarter, measure the weekday door pop)
- Proof point for licensing conversations with humanoid
  companies (walk into Unitree with a working integration, not
  a slide deck)

**Step 2**: let the demo's reception decide which thread to
prioritise. Venues call → livehouse-augmented chain. Unitree
calls → middleware licensing. Neither → the demo still earned
free marketing for the consumer accompanist.

**Headwinds**:

- Mechanical reliability is brutal — a dropped stick mid-set
  destroys a venue's reputation. Real engineering, not a
  hackathon.
- Latency through actuation chain (~50–100ms sensed-to-strike).
  Solution: robot leads tempo (click track), humans follow.
  Same compromise as the software accompanist. Acceptable v1.
- Humanoid dexterity today supports drums / shakers / cajón;
  piano and guitar are years out. Stay in the achievable lane.
- Social acceptance varies. China is fastest-accepting for
  visible commercial robotics, also where the cheap good
  hardware comes from — lucky alignment.
- Hardware margins are not software margins. Repairs, shipping,
  customs, on-site service.

**Net**: humanoids don't replace any primary business angle but
are a force multiplier on smart-accompanist (as marketing) and
livehouse (as venture thesis), and unlock a music-IQ middleware
licensing category that didn't exist before. The $50K demo is
the **single highest-ROI marketing investment** we could make
across all the directions in this document.

### Considered and dismissed

- **DAW competitor** — closed market, don't try
- **Notation editor** — Sibelius/Dorico/MuseScore own it
- **GitHub for composers** — romantic but tiny audience
- **Worship live tooling** — entrenched (ProPresenter, PCO)
- **Touring band rig** — Ableton owns it
- **Loop pedals** — Boss/Loopy own it

---

## The pivotal product decision (whichever direction we pick)

**Input modality**: symbolic-MIDI-only vs. audio-input.

- Symbolic-MIDI ships in months, demos perfectly to a keyboardist
  niche, reuses everything we have.
- Audio-input opens the real market (singers, guitarists, horn
  players) but the MIR layer (beat tracking, polyphonic pitch
  detection on noisy stage signal) is a multi-year problem that
  doesn't reuse much of what we've built.

This is the call to make before any line of product code.

---

## Strategic synthesis

The cookbook + arrangement-template direction (the deferred
"Phase H" of the old studio plan) is the **single highest-value
asset** across all these business angles. It powers:

- Smart accompanist (the consumer wedge)
- Livehouse virtual-band (the venue wedge)
- Indie game adaptive music (the dev wedge)
- Even feeds the ML-ingest play with cleaner training data

If we wanted to make exactly one bet that opens the most doors,
**finish the cookbook → arrangement-template engine**, then pick
which market to launch into based on which conversation lights up
first.

Smart accompanist is the lowest-risk launch (proven market,
known willingness-to-pay, no real estate). Livehouse is the
highest-ceiling launch (new category creation, possible venture
play). Both share the same engine.

A separate, cheap, asymmetric bet sits alongside both: the
**self-built robot-drummer demo** (#7). $50K, ~one quarter, and
serves as a marketing asset for the accompanist, an MVP for the
livehouse fixture, and a door-opener for humanoid licensing
conversations. Highest marketing-ROI investment available no
matter which primary business we end up pursuing.

---

## What we are *not* committing to here

Nothing. This document captures a thinking session. The current
roadmap (rational durations done; studio split into Import +
Capture mini-apps; cookbook gradually expanding) continues
unchanged unless we deliberately decide to redirect.

Worth re-reading before any conversation about what to build
next, or before any pitch to a third party who asks
"what's this for?"

---

## Status

- Discussion captured. Not a plan.
- Companion to `studio-plan.md`, `studio-design.md`,
  `arrangement-cookbook.md`, `collections-architecture.md`.
- Re-read when product direction comes up.
