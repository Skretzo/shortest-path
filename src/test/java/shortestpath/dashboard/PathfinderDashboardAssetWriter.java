package shortestpath.dashboard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import shortestpath.Util;

public class PathfinderDashboardAssetWriter {
    private static final String[] ASSETS = {
        "/reachability-dashboard/index.html",
        "/reachability-dashboard/app.js",
        "/reachability-dashboard/profiler.js",
        "/reachability-dashboard/styles.css"
    };

    public void writeAssets(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        for (String asset : ASSETS) {
            try (InputStream in = PathfinderDashboardAssetWriter.class.getResourceAsStream(asset)) {
                if (in == null) {
                    throw new IOException("Missing asset resource: " + asset);
                }
                Path destination = outputDirectory.resolve(asset.substring(asset.lastIndexOf('/') + 1));
                Files.write(destination, Util.readAllBytes(in));
            }
        }
    }
}
