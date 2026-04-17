package music.notation.songs.classical.furelise;

import music.notation.event.Dynamic;
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

/**
 * Für Elise — Soul Techno arrangement.
 *
 * <p>Keeps the full RH and LH piano parts from {@link ManualFurElise}
 * (synth lead + Rhodes comping) and adds a techno-soul rhythm section:
 * slap bass groove, warm analog pad, and a driving 3/8 drum pattern.
 * Tempo bumped from 76 → 120 BPM for the techno energy.</p>
 */
public final class SoulTechnoFurElise implements PieceContentProvider<FurElise> {

    private static final KeySignature KEY = new KeySignature(A, Mode.MINOR);
    private static final TimeSignature TS = new TimeSignature(3, 8);
    private static final int BAR_SIXTY_FOURTHS = 24; // 3/8 = 3 × 8th = 24 × 64th

    private final ManualFurElise manual = new ManualFurElise();

    private StaffPhraseBuilder b() {
        return StaffPhraseBuilder.in(KEY, TS, EIGHTH);
    }

    @Override public String subtitle() { return "Soul Techno"; }

    @Override
    public Piece create() {
        // Reuse the full Manual arrangement for the melody (RH) and LH arpeggios
        final Piece manualPiece = manual.create();
        final Track manualRh = manualPiece.tracks().get(0);
        final Track manualLh = manualPiece.tracks().get(1);

        // Align accompaniment to RH total duration (bars)
        final int totalBars = totalBars(manualRh);

        final var id = new FurElise();
        return new Piece(id.title(), id.composer(), KEY, TS,
                new Tempo(120, QUARTER),
                List.of(
                        Track.of("Lead Synth", SYNTH_LEAD_SAWTOOTH, manualRh.phrases()),
                        Track.of("Rhodes",     ELECTRIC_PIANO_1,    manualLh.phrases()),
                        Track.of("Slap Bass",  SLAP_BASS,           List.of(bassGroove(totalBars))),
                        Track.of("Warm Pad",   SYNTH_PAD_WARM,      List.of(padLayer(totalBars))),
                        Track.of("Drums",      DRUM_KIT,            List.of(drumsTrack(totalBars)))
                ));
    }

    private static int totalBars(Track track) {
        int sf = track.phrases().stream().mapToInt(Bar::phraseSixtyFourths).sum();
        return sf / BAR_SIXTY_FOURTHS;
    }

    // ════════════════════════════════════════════════════════════════
    //  SLAP BASS — 3/8 walking groove, Am ↔ E with soulful bVI flavor
    // ════════════════════════════════════════════════════════════════

    /** Repeats an 8-bar groove to fill {@code totalBars} bars. */
    private MelodicPhrase bassGroove(int totalBars) {
        var bb = b();
        for (int bar = 0; bar < totalBars; bar++) {
            switch (bar % 8) {
                case 0, 1, 2 -> bb.bar(EIGHTH).o2(A).o3(E).o2(A);       // Am
                case 3       -> bb.bar(EIGHTH).o2(E).o2(B).o3(D);       // E7 (dominant)
                case 4       -> bb.bar(EIGHTH).o2(A).o3(E).o2(A);       // Am
                case 5       -> bb.bar(EIGHTH).o2(A).o3(E).o3(G);       // Am with passing tone
                case 6       -> bb.bar(EIGHTH).o2(F).o3(C).o2(F);       // bVI (F) soul shift
                case 7       -> bb.bar(EIGHTH).o2(E).o2(B).o2(E);       // E leading back
            }
        }
        return bb.build(end());
    }

    // ════════════════════════════════════════════════════════════════
    //  WARM PAD — one sustained poly chord per bar
    // ════════════════════════════════════════════════════════════════

    private MelodicPhrase padLayer(int totalBars) {
        var bb = b();
        for (int bar = 0; bar < totalBars; bar++) {
            switch (bar % 8) {
                case 0, 1, 2 -> bb.bar().o4(QUARTER.dot(), A, C.higher(1), E.higher(1)); // Am
                case 3       -> bb.bar().o4(QUARTER.dot(), E, G.s(), B);                 // E
                case 4, 5    -> bb.bar().o4(QUARTER.dot(), A, C.higher(1), E.higher(1)); // Am
                case 6       -> bb.bar().o4(QUARTER.dot(), F, A, C.higher(1));           // F (bVI)
                case 7       -> bb.bar().o4(QUARTER.dot(), E, G.s(), B);                 // E
            }
        }
        return bb.build(end());
    }

    // ════════════════════════════════════════════════════════════════
    //  DRUMS — 3/8 techno pattern with soulful clap backbeat
    // ════════════════════════════════════════════════════════════════

    /**
     * Each 3/8 bar = 6 sixteenths, one drum per 16th:
     *   [1]   kick          (downbeat)
     *   [1&]  closed-hat
     *   [2]   clap          (soul backbeat)
     *   [2&]  closed-hat
     *   [3]   kick          (techno drive)
     *   [3&]  open-hat      (offbeat accent)
     *
     * Dynamics swell across sections for a "drop" feel.
     */
    private DrumPhrase drumsTrack(int totalBars) {
        var n = new ArrayList<PhraseNode>();
        n.add(new DynamicNode(Dynamic.MP));

        // Dynamics timeline (fractions of totalBars)
        int drop1 = totalBars / 6;      // first drop after ~16% in
        int drop2 = totalBars / 2;      // bigger drop at midpoint
        int fade  = (totalBars * 5) / 6; // soft fade near the end

        for (int bar = 0; bar < totalBars; bar++) {
            if (bar == drop1) n.add(new DynamicNode(Dynamic.MF));
            if (bar == drop2) n.add(new DynamicNode(Dynamic.F));
            if (bar == fade)  n.add(new DynamicNode(Dynamic.MP));

            n.add(d(BASS_DRUM,     SIXTEENTH));
            n.add(d(CLOSED_HI_HAT, SIXTEENTH));
            n.add(d(HAND_CLAP,     SIXTEENTH));
            n.add(d(CLOSED_HI_HAT, SIXTEENTH));
            n.add(d(BASS_DRUM,     SIXTEENTH));
            n.add(d(OPEN_HI_HAT,   SIXTEENTH));
        }
        return new DrumPhrase(n, end());
    }

    public static void main(String[] args) throws Exception {
        PlayPiece.play(new SoulTechnoFurElise());
    }
}
