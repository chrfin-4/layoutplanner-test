package se.ltu.kitting.test.etfa21;

import java.util.List;
import java.util.ArrayList;
import se.ltu.kitting.model.Layout;
import se.ltu.kitting.model.Kit;
import se.ltu.kitting.model.WagonHint;
import se.ltu.kitting.test.Benchmark;
import se.ltu.kitting.api.PlanningRequest;
import se.ltu.kitting.api.json.JsonIO;
import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Solves randomly generated problems to produce data for the ETFA 2021 paper.
 * Writes results to "results_[date]_[time].csv" with fields
 * time,init,hard,soft,config.
 * @author Christoffer Fink
 */
public class GenerateData {

    public static void showResults(Benchmark.ResultStats stats) {
        String s = formatStats(stats);
        System.out.println(s);
    }

    public static String formatStats(Benchmark.ResultStats stats) {
        final double time = stats.time.getAverage();
        final var score = stats.avgScore();
        String fmt = "avg time (ms): %.2f, avg score: (%3d/%3d/%4d)";
        return String.format(fmt, time, score.getInitScore(), score.getHardScore(), score.getSoftScore());
    }

    public static void run(String config, List<Layout> layouts, Consumer<String> log) {
        System.out.println("Config: " + config);
        Benchmark.Builder builder = Benchmark.builder().config(config);
        for (int i = 0; i < layouts.size(); i++) {
            final Layout layout = layouts.get(i);
            builder = builder.test(clearLayout(layout), "random " + (i+1));
        }
        Benchmark benchmark = builder.build();
        System.out.println("Running benchmark " + benchmark.name() + " ...");
        Benchmark.ResultStats stats = benchmark.run()
            .peek(r -> log.accept(formatResultCsv(r)))
            .collect(Benchmark.Result.collector());
        showResults(stats);
    }

    public static void runExperiment(final int count, final long seed) throws IOException {
        List<String> configs = List.of(
            "etfa21/configs/conf-A.xml",
            "etfa21/configs/conf-B.xml",
            "etfa21/configs/conf-C.xml",
            "etfa21/configs/conf-D.xml",
            "etfa21/configs/conf-E.xml"
        );
        final var filePrinter = new PrintWriter(new FileWriter(generateResultsFilename()), true);
        final var layouts = layouts(count, seed);
        configs.forEach(c -> run(c, layouts, filePrinter::println));
    }

    public static void main(String[] args) throws Exception {
        final int count = 200;
        final long seed = 123;
        //dumpProblems(count, seed);    // Export the problems to JSON files.
        runExperiment(count, seed);
    }

    /**
     * Exports generated problems as JSON planning requests.
     * Generates Layouts and converts them to PlanningRequests.
     */
    public static void dumpProblems(int count, long seed) throws IOException {
        final ProblemGenerator gen = new ProblemGenerator(seed);
        for (int i = 0; i < count; i++) {
            final String kitId = seed + "-" + i;
            final String chassisId = kitId;
            final var layout = gen.randomLayout();
            final var kit = new Kit(kitId, chassisId);
            final var wagonHint = new WagonHint(layout.getWagon(), 10); // 10 = mandatory
            final var request = new PlanningRequest(kit, layout.getParts(), wagonHint);
            final String json = JsonIO.toJson(request);
            final String filename = kitId + ".json";
            Files.writeString(Paths.get(filename), json);
        }
    }

    public static String requestToJson(final PlanningRequest request) {
        final String json = JsonIO.toJson(request);
        return json;
    }

    public static List<Layout> layouts(int count, long seed) {
        ProblemGenerator gen = new ProblemGenerator(seed);
        List<Layout> layouts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            layouts.add(gen.randomLayout());
        }
        return layouts;
    }

    private static String formatResult(Benchmark.Result result) {
        long time = result.time();
        String score = result.score().toString();
        String name = result.test().name();
        return String.format("time (ms): %5d, score: (%s) [%s]", time, score, name);
    }

    private static String formatResultCsv(Benchmark.Result result) {
        long time = result.time();
        final var score = result.score();
        String name = result.test().name();
        return String.format("%d,%d,%d,%d,%s", time, score.getInitScore(), score.getHardScore(), score.getSoftScore(), name);
    }

    private static Layout clearLayout(final Layout layout) {
        for (final var part : layout.getParts()) {
            part.setRotation(null);
            part.setSideDown(null);
            part.setPosition(null);
        }
        return layout;
    }

    // Does not check that the file already exists, which should be highly
    // unlikely given that seconds are included.
    private static String generateResultsFilename() {
        final var now = LocalDateTime.now();
        final var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_kk:mm:ss");
        return "results_" + now.format(formatter) + ".csv";
    }

}
