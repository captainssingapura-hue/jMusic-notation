package music.notation.play;

import music.notation.event.Instrument;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Soundbank;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reusable index of {@link SoundBankInstrument}s discovered from a
 * list of SoundFont files, grouped 1-to-many by their GM
 * {@link Instrument} family.
 *
 * <p>The {@link Classifier} is pluggable so future configuration can
 * override the default GM-program-based classification — e.g. mapping
 * a "Steinway Concert" patch under {@link Instrument#BRIGHT_ACOUSTIC_PIANO}
 * instead of its default family. Per-SBI overrides live in
 * {@link #withOverride}.</p>
 */
public final class SoundBankRegistry {

    /** SBI-classification rule. The default classifies by bank/program → GM family. */
    @FunctionalInterface
    public interface Classifier {
        Instrument classify(SoundBankInstrument sbi);

        Classifier DEFAULT = sbi ->
                SoundBankInstrument.classifyToGm(sbi.bank(), sbi.program(), sbi.displayName());
    }

    private final Map<Instrument, List<SoundBankInstrument>> byFamily;
    private final List<SoundBankInstrument> all;

    private SoundBankRegistry(Map<Instrument, List<SoundBankInstrument>> byFamily,
                              List<SoundBankInstrument> all) {
        this.byFamily = byFamily;
        this.all = all;
    }

    public static SoundBankRegistry empty() {
        return new SoundBankRegistry(Map.of(), List.of());
    }

    /** Build from a list of soundbank files using the default classifier. */
    public static SoundBankRegistry build(List<File> files) {
        return build(files, Classifier.DEFAULT);
    }

    public static SoundBankRegistry build(List<File> files, Classifier classifier) {
        if (files == null || files.isEmpty()) return empty();
        var all = new ArrayList<SoundBankInstrument>();
        for (File f : files) {
            if (f == null || !f.isFile()) continue;
            try {
                Soundbank sb = MidiSystem.getSoundbank(f);
                String sbName = (sb.getName() != null && !sb.getName().isBlank())
                        ? sb.getName() : f.getName();
                for (var inst : sb.getInstruments()) {
                    var p = inst.getPatch();
                    var sbi = new SoundBankInstrument(
                            SoundBankInstrument.classifyToGm(p.getBank(), p.getProgram(), inst.getName()),
                            p.getBank(), p.getProgram(), inst.getName(), sbName);
                    all.add(sbi);
                }
            } catch (Exception ignored) {
                // Skip unreadable / unsupported files; the registry is best-effort.
            }
        }
        return groupBy(all, classifier);
    }

    private static SoundBankRegistry groupBy(List<SoundBankInstrument> all, Classifier classifier) {
        var sorted = new ArrayList<>(all);
        sorted.sort(Comparator
                .comparing((SoundBankInstrument s) -> classifier.classify(s).program())
                .thenComparingInt(SoundBankInstrument::bank)
                .thenComparingInt(SoundBankInstrument::program)
                .thenComparing(SoundBankInstrument::displayName));
        var byFamily = new LinkedHashMap<Instrument, List<SoundBankInstrument>>();
        for (var sbi : sorted) {
            // Re-classify each SBI through the active classifier, dropping
            // the default-classified family stored at parse time.
            Instrument family = classifier.classify(sbi);
            var rebound = new SoundBankInstrument(
                    family, sbi.bank(), sbi.program(),
                    sbi.displayName(), sbi.soundbankName());
            byFamily.computeIfAbsent(family, k -> new ArrayList<>()).add(rebound);
        }
        // Defensive copies.
        var frozen = new LinkedHashMap<Instrument, List<SoundBankInstrument>>();
        for (var e : byFamily.entrySet()) frozen.put(e.getKey(), List.copyOf(e.getValue()));
        return new SoundBankRegistry(Map.copyOf(frozen), List.copyOf(sorted));
    }

    /** All SBIs that classified as {@code family}; empty list if none. */
    public List<SoundBankInstrument> variantsFor(Instrument family) {
        return byFamily.getOrDefault(family, List.of());
    }

    /** GM families with at least one SBI variant available. */
    public Set<Instrument> familiesWithVariants() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(byFamily.keySet()));
    }

    /** All discovered SBIs across every loaded soundbank, sorted. */
    public List<SoundBankInstrument> all() {
        return all;
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }

    /**
     * Produce a new registry with one SBI re-classified to a different
     * GM family. The original is unchanged. Foundation for a future
     * "Adjust classification" UI.
     */
    public SoundBankRegistry withOverride(SoundBankInstrument target, Instrument newFamily) {
        Classifier override = sbi -> sameSbi(sbi, target) ? newFamily : Classifier.DEFAULT.classify(sbi);
        return groupBy(all, override);
    }

    private static boolean sameSbi(SoundBankInstrument a, SoundBankInstrument b) {
        return a.bank() == b.bank()
                && a.program() == b.program()
                && a.soundbankName().equals(b.soundbankName());
    }
}
