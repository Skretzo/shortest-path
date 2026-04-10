package shortestpath.dashboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DashboardBundlePublisher {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // Overrides the root directory where the shared dashboard site and bundle registry are written.
    public static final String OUTPUT_ROOT_PROPERTY = "dashboard.outputRoot";
    public static final Path DEFAULT_OUTPUT_ROOT = Paths.get("build", "reports", "pathfinder-dashboard");
    private static final String BUNDLES_DIRECTORY = "bundles";
    private static final String INDEX_FILENAME = "index.json";

    private final PathfinderDashboardAssetWriter assetWriter = new PathfinderDashboardAssetWriter();

    public Path getOutputRoot() {
        String configured = System.getProperty(OUTPUT_ROOT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_OUTPUT_ROOT;
        }
        return Paths.get(configured);
    }

    public synchronized Path publishBundle(
        String bundleId,
        String title,
        String subtitle,
        PathfinderDashboardModels.Report report
    ) throws IOException {
        // Each producer writes into the same shared site root. A "bundle" is just one report payload plus
        // one entry in the shared bundle index that tells the frontend where to find it.
        Path outputRoot = getOutputRoot();
        Path bundleDirectory = outputRoot.resolve(BUNDLES_DIRECTORY).resolve(bundleId);
        Path reportPath = bundleDirectory.resolve("report.json");

        // The site shell is shared across all bundles, so every publish ensures the static assets exist before
        // updating the bundle-specific payload and registry entry.
        assetWriter.writeAssets(outputRoot);
        Files.createDirectories(bundleDirectory);
        writeJson(reportPath, report);
        updateBundleIndex(outputRoot, bundleId, title, subtitle, reportPath);
        return reportPath;
    }

    private void updateBundleIndex(
        Path outputRoot,
        String bundleId,
        String title,
        String subtitle,
        Path reportPath
    ) throws IOException {
        Path indexPath = outputRoot.resolve(BUNDLES_DIRECTORY).resolve(INDEX_FILENAME);
        PathfinderDashboardModels.BundleIndex index = readIndex(indexPath);
        String relativeReportPath = outputRoot.relativize(reportPath).toString().replace('\\', '/');
        String generatedAt = Instant.now().toString();

        PathfinderDashboardModels.BundleManifest replacement = new PathfinderDashboardModels.BundleManifest();
        replacement.id = bundleId;
        replacement.title = title;
        replacement.subtitle = subtitle;
        replacement.reportPath = relativeReportPath;
        replacement.generatedAt = generatedAt;

        // Publishing is replace-or-insert by bundle id so repeated runs refresh one bundle without disturbing the
        // others already present in the shared site.
        List<PathfinderDashboardModels.BundleManifest> bundles = new ArrayList<>();
        boolean replaced = false;
        for (PathfinderDashboardModels.BundleManifest bundle : index.bundles) {
            if (bundleId.equals(bundle.id)) {
                bundles.add(replacement);
                replaced = true;
            } else {
                bundles.add(bundle);
            }
        }
        if (!replaced) {
            bundles.add(replacement);
        }
        bundles.sort(Comparator.comparing(bundle -> bundle.title));
        index.bundles = bundles;
        writeJson(indexPath, index);
    }

    private PathfinderDashboardModels.BundleIndex readIndex(Path indexPath) throws IOException {
        if (!Files.exists(indexPath)) {
            PathfinderDashboardModels.BundleIndex index = new PathfinderDashboardModels.BundleIndex();
            index.bundles = new ArrayList<>();
            return index;
        }
        return GSON.fromJson(Files.readString(indexPath), PathfinderDashboardModels.BundleIndex.class);
    }

    private void writeJson(Path outputPath, Object value) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            GSON.toJson(value, writer);
        }
    }
}
