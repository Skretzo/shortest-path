package shortestpath.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class DashboardBundlePublisher {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new Gson();
    public static final String OUTPUT_ROOT_PROPERTY = "dashboard.outputRoot";
    public static final String BUNDLE_NAME_PROPERTY = "dashboard.bundleName";
    public static final Path DEFAULT_OUTPUT_ROOT = Paths.get("build", "reports", "pathfinder-dashboard");

    private final PathfinderDashboardAssetWriter assetWriter = new PathfinderDashboardAssetWriter();

    public Path getOutputRoot() {
        String configured = System.getProperty(OUTPUT_ROOT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_OUTPUT_ROOT;
        }
        return Paths.get(configured);
    }

    /**
     * Write a single run's heatmap data to disk immediately, freeing the in-memory tiles.
     * Call this during the pathfinding loop so only one heatmap is ever resident in memory.
     * Sets {@code run.heatmapFile} and nulls {@code run.tileHeatmap}.
     */
    public void externalizeRunHeatmap(String bundleName, int runIndex,
            PathfinderDashboardModels.RunRecord run) throws IOException {
        if (run.tileHeatmap == null || run.tileHeatmap.tiles == null || run.tileHeatmap.tiles.isEmpty()) {
            return;
        }
        Path heatmapDir = getOutputRoot().resolve("bundles").resolve(bundleName).resolve("heatmaps");
        Files.createDirectories(heatmapDir);
        String heatmapFileName = runIndex + ".json";
        writeCompactHeatmap(heatmapDir.resolve(heatmapFileName), run.tileHeatmap.tiles);
        run.heatmapFile = "heatmaps/" + heatmapFileName;
        run.tileHeatmap = null;
    }

    /**
     * Publish a named bundle. Writes:
     *   bundles/{name}/report.json     (runs without inline heatmap data)
     *   bundles/{name}/heatmaps/N.json (one per run whose heatmap was not already externalised)
     *   bundles/index.json             (updated registry)
     *
     * Heatmaps that were already written via {@link #externalizeRunHeatmap} are skipped.
     */
    public synchronized Path publishBundle(
        String bundleName,
        PathfinderDashboardModels.Report report
    ) throws IOException {
        Path outputRoot = getOutputRoot();
        assetWriter.writeAssets(outputRoot);

        Path bundleDir = outputRoot.resolve("bundles").resolve(bundleName);
        Path heatmapDir = bundleDir.resolve("heatmaps");
        Files.createDirectories(bundleDir);

        // Externalise any remaining heatmap data (already-externalised runs have tileHeatmap == null)
        List<PathfinderDashboardModels.RunRecord> runs = report.runs;
        if (runs != null) {
            for (int i = 0; i < runs.size(); i++) {
                PathfinderDashboardModels.RunRecord run = runs.get(i);
                if (run.tileHeatmap != null && run.tileHeatmap.tiles != null && !run.tileHeatmap.tiles.isEmpty()) {
                    Files.createDirectories(heatmapDir);
                    String heatmapFileName = i + ".json";
                    writeCompactHeatmap(heatmapDir.resolve(heatmapFileName), run.tileHeatmap.tiles);
                    run.heatmapFile = "heatmaps/" + heatmapFileName;
                    run.tileHeatmap = null;
                }
            }
        }

        Path reportPath = bundleDir.resolve("report.json");
        writeJson(reportPath, report);

        updateBundleIndex(outputRoot, bundleName, report);
        return reportPath;
    }

    private void updateBundleIndex(Path outputRoot, String bundleName,
            PathfinderDashboardModels.Report report) throws IOException {
        Path indexPath = outputRoot.resolve("bundles").resolve("index.json");
        PathfinderDashboardModels.BundleIndex index;
        if (Files.exists(indexPath)) {
            try (Reader reader = Files.newBufferedReader(indexPath)) {
                index = GSON.fromJson(reader, PathfinderDashboardModels.BundleIndex.class);
            }
            if (index == null || index.bundles == null) {
                index = new PathfinderDashboardModels.BundleIndex();
                index.bundles = new ArrayList<>();
            }
        } else {
            index = new PathfinderDashboardModels.BundleIndex();
            index.bundles = new ArrayList<>();
        }

        // Replace existing entry or add new
        index.bundles.removeIf(e -> bundleName.equals(e.name));
        PathfinderDashboardModels.BundleEntry entry = new PathfinderDashboardModels.BundleEntry();
        entry.name = bundleName;
        entry.title = report.title != null ? report.title : bundleName;
        entry.generatedAt = report.generatedAt;
        entry.reportPath = bundleName + "/report.json";
        index.bundles.add(entry);

        writeJson(indexPath, index);
    }

    /** Write a flat interleaved array [x1,y1,c1, x2,y2,c2, ...] without pretty-printing. */
    private void writeCompactHeatmap(Path outputPath, List<PathfinderDashboardModels.TileVisit> tiles) throws IOException {
        Files.createDirectories(outputPath.getParent());
        int[] flat = new int[tiles.size() * 3];
        for (int i = 0; i < tiles.size(); i++) {
            PathfinderDashboardModels.TileVisit tv = tiles.get(i);
            flat[i * 3] = tv.x;
            flat[i * 3 + 1] = tv.y;
            flat[i * 3 + 2] = tv.count;
        }
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            GSON_COMPACT.toJson(flat, writer);
        }
    }

    private void writeJson(Path outputPath, Object value) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            GSON.toJson(value, writer);
        }
    }
}
