/**
 * Performance side-channels — per-track timelines for the dimensions
 * of how a piece is <em>played</em> rather than how it's <em>written</em>.
 *
 * <h2>The doctrine</h2>
 *
 * <p>The abstract music model (notation-core / notation-performance's
 * {@code Score}, {@code Track}, note records) stays small and stable.
 * Performance dimensions — volume, attack shape, sustain pedal,
 * velocity, and eventually pitch bend / modulation / aftertouch —
 * layer on top as side-channels keyed by {@link
 * music.notation.expressivity.TrackId}.</p>
 *
 * <p>Every side-channel in this package follows the same shape:</p>
 * <ul>
 *   <li>{@code FooChange(long tickMs, ...)} — a single set-point</li>
 *   <li>{@code FooControl(List<FooChange> changes)} — per-track
 *       sparse timeline; sorted + deduped at construction</li>
 *   <li>{@code Foos(Map<TrackId, FooControl> byTrack)} — top-level
 *       side-channel, dropping empty controls at construction</li>
 * </ul>
 *
 * <p>Currently shipped:</p>
 * <ul>
 *   <li>{@link music.notation.expressivity.Volume} — per-track CC #7</li>
 *   <li>{@link music.notation.expressivity.Articulations} — per-onset
 *       attack-shape hints</li>
 *   <li>{@link music.notation.expressivity.Pedaling} — sustain pedal CC #64
 *       (DOWN / UP / CHANGE states)</li>
 * </ul>
 *
 * <p>Future shape (see {@code .docs/velocity-model-plan.md}):</p>
 * <ul>
 *   <li>{@code Velocities} — per-note attack strength</li>
 *   <li>{@code Sostenuto} / {@code UnaCorda} — piano CC #66 / #67,
 *       mirror {@link music.notation.expressivity.Pedaling}</li>
 *   <li>{@code PitchBend} — guitar / synth / MPE pitch-wheel</li>
 *   <li>{@code Modulation} / {@code Expression} — CC #1 / CC #11</li>
 * </ul>
 *
 * <h2>Why side-channel, not record fields</h2>
 *
 * <p>Velocity, dynamics, etc. are interpretation, not notation. The
 * composer writes "snare hit"; the performance layer decides whether
 * that's vel 50 (ghost) or vel 110 (accent). Same way a score's "f"
 * marking is read separately from the noteheads themselves.</p>
 *
 * <p>The side-channel pattern keeps the abstract note records
 * immutable across feature additions. Adding a new performance
 * dimension means adding a new side-channel to this package — never
 * extending {@code PitchedNote} or {@code DrumNote}.</p>
 *
 * <h2>Composition at emission time</h2>
 *
 * <p>{@code MidiCodec.toMidi} (in notation-performance) reads each
 * side-channel from the {@code Performance}, converts it to the
 * appropriate MIDI bytes, and merges by tick:</p>
 * <pre>
 *   Volume       → CC #7  events
 *   Pedaling     → CC #64 events
 *   Articulations→ note attack shape (codec-internal)
 *   Velocities   → NOTE_ON velocity byte (when shipped)
 * </pre>
 *
 * <p>Each side-channel can be empty independently — a piece with
 * pedaling but no per-track volumes still works. The codec composes
 * whatever's present.</p>
 */
package music.notation.expressivity;
