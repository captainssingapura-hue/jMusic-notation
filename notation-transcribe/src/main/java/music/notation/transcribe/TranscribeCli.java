package music.notation.transcribe;

import music.notation.duration.Duration;
import music.notation.structure.TimeSignature;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Command line front end.
 *
 * <pre>
 *   transcribe request.json [--out DIR]
 *   transcribe take.wav [--bpm N] [--downbeat SEC] [--key "Bb major"] [--grid 1/16]
 *                       [--from SEC] [--to SEC] [--detector yin|rmvpe] [--model rmvpe.onnx] [--out DIR]
 *   transcribe --example FILE        write an example request
 * </pre>
 *
 * A request's relative {@code audio} path is resolved against the
 * request file's folder. Output defaults to
 * {@code <input folder>/<title>-transcription/}.
 */
public final class TranscribeCli {

    private TranscribeCli() {}

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        try {
            System.exit(run(args, out));
        } catch (RuntimeException e) {
            out.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args, PrintStream out) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            usage(out);
            return args.length == 0 ? 2 : 0;
        }
        if (args[0].equals("--example")) {
            if (args.length < 2) { usage(out); return 2; }
            Path file = Path.of(args[1]);
            TranscriptionJson.write(file, exampleRequest());
            out.println("wrote example request → " + file);
            return 0;
        }

        Path input = Path.of(args[0]).toAbsolutePath();
        Path base = input.getParent();
        TranscriptionRequest request;
        Path outDir = null;
        if (args[0].toLowerCase().endsWith(".json")) {
            request = TranscriptionJson.readRequest(input);
            Path audio = Path.of(request.audio());
            if (!audio.isAbsolute()) request = request.withAudio(base.resolve(audio).normalize().toString());
            for (int i = 1; i < args.length; i++) {
                if (args[i].equals("--out") && i + 1 < args.length) outDir = Path.of(args[++i]);
                else throw new IllegalArgumentException("unknown option " + args[i]);
            }
        } else {
            int bpm = 120; Double downbeat = null; String key = null; Duration grid = null;
            double from = 0, to = 0;
            String detector = "yin", model = null;
            for (int i = 1; i < args.length; i++) {
                String a = args[i];
                if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for " + a);
                String v = args[++i];
                switch (a) {
                    case "--bpm" -> bpm = Integer.parseInt(v);
                    case "--downbeat" -> downbeat = Double.parseDouble(v);
                    case "--key" -> key = v;
                    case "--grid" -> grid = parseFraction(v);
                    case "--from" -> from = Double.parseDouble(v);
                    case "--to" -> to = Double.parseDouble(v);
                    case "--detector" -> detector = v.toLowerCase(java.util.Locale.ROOT);
                    case "--model" -> model = v;
                    case "--out" -> outDir = Path.of(v);
                    default -> throw new IllegalArgumentException("unknown option " + a);
                }
            }
            request = new TranscriptionRequest(input.toString(), new TimeWindow(from, to), detector(detector, model), null,
                    new TimingSpec(bpm, downbeat, null, key, grid), null);
        }
        if (outDir == null) outDir = base.resolve(TranscriptionWriter.slug(request.title()) + "-transcription");

        long t0 = System.nanoTime();
        Transcription t = Transcriber.transcribe(request);
        List<Path> written = TranscriptionWriter.write(t, outDir);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        String report = TranscriptionReport.render(t);
        out.print(report.substring(0, report.indexOf("\n\n") + 1));
        out.printf("Took %d ms. Wrote:%n", ms);
        for (Path p : written) out.println("  " + p);
        return 0;
    }

    static TranscriptionRequest exampleRequest() {
        return new TranscriptionRequest("take-01.wav", new TimeWindow(0, 30), PitchDetectorSpec.Yin.DEFAULT,
                SegmentationSpec.DEFAULT,
                new TimingSpec(92, 1.3, new TimeSignature(4, 4), "auto", Duration.of(1, 16)), "Take 1");
    }

    private static PitchDetectorSpec detector(String name, String model) {
        return switch (name) {
            case "yin" -> {
                if (model != null) throw new IllegalArgumentException("--model only applies to --detector rmvpe");
                yield PitchDetectorSpec.Yin.DEFAULT;
            }
            case "rmvpe" -> {
                var d = PitchDetectorSpec.Rmvpe.DEFAULT;
                yield new PitchDetectorSpec.Rmvpe(model, d.threshold(), d.minHz(), d.maxHz(), d.minRms());
            }
            default -> throw new IllegalArgumentException("--detector must be yin or rmvpe: " + name);
        };
    }

    private static Duration parseFraction(String s) {
        int slash = s.indexOf('/');
        if (slash < 0) throw new IllegalArgumentException("grid must be a fraction like 1/16: " + s);
        return Duration.of(Long.parseLong(s.substring(0, slash).trim()), Long.parseLong(s.substring(slash + 1).trim()));
    }

    private static void usage(PrintStream out) {
        out.println("""
                usage:
                  transcribe request.json [--out DIR]
                  transcribe take.wav [--bpm N] [--downbeat SEC] [--key "Bb major"|auto] [--grid 1/16]
                                      [--from SEC] [--to SEC] [--detector yin|rmvpe] [--model rmvpe.onnx]
                                      [--out DIR]
                  transcribe --example FILE

                rmvpe needs the model weights: --model, -D%s=…, %s, or %s"""
                .formatted(RmvpeModel.PROPERTY, RmvpeModel.ENV, RmvpeModel.DEFAULT_PATH));
    }
}
