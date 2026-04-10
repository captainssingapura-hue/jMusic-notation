package music.notation.songs.rock.bluelotus;

import music.notation.chord.MajorTriad;
import music.notation.chord.MinorTriad;
import music.notation.event.Dynamic;
import music.notation.phrase.*;
import music.notation.structure.*;

import java.util.ArrayList;
import java.util.List;

import static music.notation.duration.BaseValue.*;
import static music.notation.event.Instrument.*;
import static music.notation.event.Ornament.*;
import static music.notation.event.PercussionSound.*;
import static music.notation.phrase.Deg.*;
import static music.notation.pitch.Accidental.*;
import static music.notation.pitch.NoteName.*;
import static music.notation.songs.PieceHelper.*;

public final class DefaultBlueLotus implements PieceContentProvider<BlueLotus> {

    private static final TimeSignature TS = new TimeSignature(4, 4);

    @Override
    public Piece create() {
        final var id = new BlueLotus();

        // 蓝莲花 (Blue Lotus) by Xu Wei — 1=♭E, 4/4, ♩=90
        // Progression: E♭ – B♭ – Cm – A♭  (I – V – vi – IV)
        // All tracks: 32 bars = 2048 sixty-fourths

        var P  = NumberedPhraseBuilder.in(E, FLAT, 4, TS);
        var bP = NumberedPhraseBuilder.in(E, FLAT, 3, TS);

        // Shared 4-bar rest (for guitar intro, bass intro, drums intro)
        var rest4 = P.bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE).bar().r(WHOLE)
                .build(attacca());

        // =====================================================================
        //  LEAD — overdriven guitar replacing vocal (1=♭E)
        //
        //  Pickups live at the END of the preceding section's last bar,
        //  so all tracks stay aligned at 32 bars total.
        // =====================================================================

        // -- Intro (4 bars): 3 rests + pickup into verse 1 --
        var leadIntro = P
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().r(WHOLE)
                .bar().mf().r(HALF).r(QUARTER).l(_5).l(_6)           // pickup → verse1
                .build(attacca());

        // -- Verse 1 (4 bars): 没有什么能够阻挡 你对自由的向往 --
        var verseLine1 = P
                .bar().n(_5,EIGHTH).n(_6,EIGHTH).n(_1,EIGHTH).n(_2,EIGHTH).n(_3,EIGHTH).n(_2,QUARTER).n(_1,EIGHTH)                    // 没有什么能够阻
                                           //
                .bar().l(_6,HALF).r(QUARTER).r(QUARTER)              // 挡 →mid-pickup
                .bar().l(_5,EIGHTH).l(_6,EIGHTH).n(_1,EIGHTH).n(_2,EIGHTH)    // 你对自由的向往
                      .n(_3,EIGHTH).n(_5,QUARTER).n(_2,EIGHTH).slurStart()
                .bar().n(_2,HALF).slurEnd().r(HALF)              //  →pickup
                .build(attacca());

        // -- Verse 2 (4 bars): 天马行空的生涯 你的心了无牵挂 --
        var verseLine2 = P
                .bar().r(EIGHTH).n(_2,QUARTER).l(_5,EIGHTH)            // 天马行空的生
                      .n(_2,EIGHTH).n(_2,EIGHTH).n(_3,EIGHTH).n(_2,EIGHTH)
                .bar().n(_1,HALF).r(HALF)                             // 涯 →mid-pickup
                .bar().r(EIGHTH).n(_2,EIGHTH).n(_2,EIGHTH).n(_2,EIGHTH)                    // 你的心了
                      .l(_5,EIGHTH).l(_6,EIGHTH).n(_1,EIGHTH).l(_6, EIGHTH).slurStart()
                .bar().l(_6,QUARTER).slurEnd().r(QUARTER).r(HALF)              // 无牵挂 →pickup
                .build(breath());

        // -- Chorus 1 (4 bars): 穿过幽暗的岁月 也曾感到彷徨 --
        var chorusLine1 = P
                .bar().f()
                      .r(EIGHTH).n(_4,QUARTER).n(_4,EIGHTH)                // 穿过幽暗
                      .n(_3,EIGHTH).n(_2,EIGHTH).n(_1,EIGHTH).n(_3,SIXTEENTH).slurStart().n(_2,SIXTEENTH).slurEnd()                      //   的岁月
                .bar().n(_2,HALF).r(HALF)              // →mid-pickup
                .bar().r(EIGHTH).n(_4,QUARTER).n(_6,EIGHTH)                    // 也曾感到
                      .n(_5,EIGHTH).n(_4,EIGHTH).n(_3,EIGHTH).n(_2,EIGHTH).slurStart()
                .bar().n(_2,QUARTER).slurEnd().r(QUARTER).r(HALF)              // 彷徨 →pickup
                .build(attacca());

        // -- Chorus 2 (4 bars): 当你低头的瞬间 才发觉脚下的路 --
        var chorusLine2 = P
                .bar().r(EIGHTH).n(_2,QUARTER).l(_5,EIGHTH).n(_2,EIGHTH).n(_2,EIGHTH)                    // 当你低头
                      .n(_3,EIGHTH).n(_2,EIGHTH)                      //   的瞬间
                .bar().n(_1,HALF).r(HALF)              // →mid-pickup
                .bar().r(EIGHTH).n(_2,EIGHTH).n(_2,EIGHTH).n(_2,EIGHTH)                    // 才发觉脚下
                      .l(_5,EIGHTH).l(_6,EIGHTH).n(_1,EIGHTH).l(_6,EIGHTH).slurStart()
                .bar().l(_6,HALF).slurEnd().r(HALF)              // 的路 →pickup
                .build(breath());

        // -- Chorus 3 (4 bars): 心中那自由的世界 如此的清澈高远 --
        var chorusLine3 = P
                .bar().f().r(EIGHTH).n(_5,EIGHTH).n(_5,EIGHTH).n(_5,EIGHTH).n(_5,EIGHTH).n(_5,EIGHTH).n(_5,EIGHTH)                     // 心中那自由
                      .n(_5,SIXTEENTH).n(_6,SIXTEENTH).slurStart()                      //   的
                .bar().n(_6,HALF).slurEnd().r(HALF)           // 世界 如此的
                .bar().r(EIGHTH).n(_6,EIGHTH).n(_6,EIGHTH).n(_6,EIGHTH)                    // 清澈高远
                      .h(_1,EIGHTH).n(_6,EIGHTH).n(_6,EIGHTH).n(_5,EIGHTH).slurStart()
                .bar().n(_5,HALF).slurEnd().r(HALF)              // →pickup
                .build(attacca());

        // -- Chorus 4 (4 bars): 盛开着永不凋零 蓝莲花 --
        var chorusLine4 = P
                .bar().r(EIGHTH).n(_5,EIGHTH).n(_5, EIGHTH).n(_5, EIGHTH).n(_5,EIGHTH).n(_6,EIGHTH)                    // 盛开着永
                      .h(_1,EIGHTH).n(_6,EIGHTH)                      //   不
                .bar().n(_6,HALF).r(QUARTER).n(_5,EIGHTH).n(_6,EIGHTH)           // 凋零
                .bar().h(_3,WHOLE)                                    // 蓝莲花
                .bar().h(_2,WHOLE)               // ___
                .build(breath());

        // -- Outro (4 bars) --
        var outro = P
                .bar().mp().n(_5,HALF).n(_3,HALF)
                .bar().n(_2,HALF).l(_7,HALF)
                .bar().n(_1,HALF).r(HALF)
                .bar().n(_1, WHOLE, MORDENT)
                .build(end());

        var lead = Track.of("Lead", OVERDRIVEN_GUITAR, List.of(
                leadIntro, verseLine1, verseLine2,
                chorusLine1, chorusLine2, chorusLine3, chorusLine4,
                outro));

        // =====================================================================
        //  GUITAR — clean arpeggios (verse) / chords (chorus) in E♭ major
        //  8 × 4 bars = 32 bars
        // =====================================================================

        var arpIntro = P
                .bar().mp().l(_1).l(_5).n(_1).n(_3).n(_5).n(_3).n(_1).l(_5)
                .bar().l(_5).n(_2).n(_5).n(_7).h(_2).n(_7).n(_5).n(_2)
                .bar().l(_6).n(_3).n(_6).h(_1).h(_3).h(_1).n(_6).n(_3)
                .bar().l(_4).n(_1).n(_4).n(_6).h(_1).n(_6).n(_4).n(_1)
                .build(attacca());

        var arpCycle = P
                .bar().l(_1).l(_5).n(_1).n(_3).n(_5).n(_3).n(_1).l(_5)
                .bar().l(_5).n(_2).n(_5).n(_7).h(_2).n(_7).n(_5).n(_2)
                .bar().l(_6).n(_3).n(_6).h(_1).h(_3).h(_1).n(_6).n(_3)
                .bar().l(_4).n(_1).n(_4).n(_6).h(_1).n(_6).n(_4).n(_1)
                .build(attacca());

        var arpOutro = P
                .bar().mp().l(_1).l(_5).n(_1).n(_3).n(_5).n(_3).n(_1).l(_5)
                .bar().l(_5).n(_2).n(_5).n(_7).h(_2).n(_7).n(_5).n(_2)
                .bar().l(_6).n(_3).n(_6).h(_1).h(_3).h(_1).n(_6).n(_3)
                .bar().l(_4).n(_1).n(_4).n(_6).h(_1).n(_6).n(_4).n(_1)
                .build(end());

        final var ebMaj = new MajorTriad(E, FLAT, 3);
        final var bbMaj = new MajorTriad(B, FLAT, 2);
        final var cMin  = new MinorTriad(C, 3);
        final var abMaj = new MajorTriad(A, FLAT, 2);
        var EbChord = chord(WHOLE, ebMaj);
        var BbChord = chord(WHOLE, bbMaj);
        var CmChord = chord(WHOLE, cMin);
        var AbChord = chord(WHOLE, abMaj);
        var cm = new PhraseMarking(PhraseConnection.ATTACCA, false);

        var guitar = Track.of("Guitar", ELECTRIC_GUITAR_CLEAN, List.of(
                arpIntro, arpCycle, arpCycle,
                new ChordPhrase(List.of(EbChord, BbChord, CmChord, AbChord,
                        EbChord, BbChord, CmChord, AbChord), cm),
                new ChordPhrase(List.of(EbChord, BbChord, CmChord, AbChord,
                        EbChord, BbChord, CmChord, AbChord), cm),
                arpOutro));

        // =====================================================================
        //  BASS — root notes in E♭ major (8 × 4 bars = 32 bars)
        // =====================================================================

        var bassMf = bP.bar().dyn(Dynamic.MF).l(_1,WHOLE)
                .bar().l(_5,WHOLE).bar().l(_6,WHOLE).bar().l(_4,WHOLE)
                .build(attacca());
        var bassCycle = bP.bar().l(_1,WHOLE)
                .bar().l(_5,WHOLE).bar().l(_6,WHOLE).bar().l(_4,WHOLE)
                .build(attacca());
        var bassF = bP.bar().dyn(Dynamic.F).l(_1,WHOLE)
                .bar().l(_5,WHOLE).bar().l(_6,WHOLE).bar().l(_4,WHOLE)
                .build(attacca());
        var bassMp = bP.bar().dyn(Dynamic.MP).l(_1,WHOLE)
                .bar().l(_5,WHOLE).bar().l(_6,WHOLE).bar().l(_4,WHOLE)
                .build(end());

        var bass = Track.of("Bass", ELECTRIC_BASS_FINGER, List.of(
                rest4,
                bassMf, bassCycle,
                bassF, bassCycle,
                bassCycle, bassCycle,
                bassMp));

        // =====================================================================
        //  DRUMS (4 + 8 + 8 + 8 + 4 = 32 bars)
        // =====================================================================

        var drumBar = Bar.of(TS.barSixtyFourths(),
                d(BASS_DRUM, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(CLOSED_HI_HAT, QUARTER));
        var drumBarCrash = Bar.of(TS.barSixtyFourths(),
                d(CRASH_CYMBAL, QUARTER), d(CLOSED_HI_HAT, QUARTER),
                d(ACOUSTIC_SNARE, QUARTER), d(OPEN_HI_HAT, QUARTER));

        var drums = Track.of("Drums", DRUM_KIT, List.of(
                rest4,
                drumPhrase(Dynamic.MP, drumBar, 8, attacca()),
                drumPhrase(Dynamic.MF, drumBarCrash, drumBar, 8, attacca()),
                drumPhrase(Dynamic.F, drumBarCrash, drumBar, 8, attacca()),
                drumPhrase(Dynamic.MP, drumBar, 4, end())));

        return new Piece(id.title(), id.composer(),
                new KeySignature(E, Mode.MAJOR),
                TS, new Tempo(90, QUARTER), List.of(lead, guitar, bass, drums));
    }

    private static DrumPhrase drumPhrase(Dynamic dyn, Bar bar, int bars,
                                         PhraseMarking marking) {
        var nodes = new ArrayList<PhraseNode>();
        nodes.add(new DynamicNode(dyn));
        for (int i = 0; i < bars; i++) nodes.addAll(bar.nodes());
        return new DrumPhrase(nodes, marking);
    }

    private static DrumPhrase drumPhrase(Dynamic dyn, Bar crashBar,
                                         Bar normalBar, int bars,
                                         PhraseMarking marking) {
        var nodes = new ArrayList<PhraseNode>();
        nodes.add(new DynamicNode(dyn));
        nodes.addAll(crashBar.nodes());
        for (int i = 1; i < bars; i++) nodes.addAll(normalBar.nodes());
        return new DrumPhrase(nodes, marking);
    }
}
