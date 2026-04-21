package shortestpath.dump;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Position;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.XteaKeyManager;
import org.junit.Assume;
import org.junit.Test;

/**
 * Dumps every landscape placement of bank-related objects from an OSRS cache
 * to a TSV. Intended for use by {@code scripts/reconcile_bank_tiles.py} which
 * snaps {@code bank.tsv} rows to exact stand tiles.
 *
 * Enabled by setting {@code -DbankTiles.dump=true} and providing:
 *   -DbankTiles.cacheDir=/path/to/cache
 *   -DbankTiles.xteaPath=/path/to/keys.json
 *   -DbankTiles.output=/path/to/bank_tile_placements.tsv   (optional; defaults to build/)
 *
 * The keys.json file from archive.openrs2.org must be patched so its JSON
 * uses {@code region}/{@code keys} instead of {@code mapsquare}/{@code key};
 * the {@code bankTileDump} Gradle task does this automatically.
 *
 * Run via {@code ./gradlew bankTileDump -PbankTileCacheDir=... -PbankTileXteaPath=...}.
 */
public class BankTileDumperTest {
    private static final String DUMP_PROPERTY = "bankTiles.dump";
    private static final String CACHE_DIR_PROPERTY = "bankTiles.cacheDir";
    private static final String XTEA_PROPERTY = "bankTiles.xteaPath";
    private static final String OUTPUT_PROPERTY = "bankTiles.output";
    private static final String PATTERNS_PROPERTY = "bankTiles.patterns";

    private static final Pattern[] DEFAULT_NAME_PATTERNS = new Pattern[]{
        Pattern.compile("(?i)^bank booth$"),
        Pattern.compile("(?i)^bank chest$"),
        Pattern.compile("(?i)^bank chest-wreck$"),
        Pattern.compile("(?i)^bank deposit box$"),
        Pattern.compile("(?i)^deposit box$"),
        Pattern.compile("(?i)^grand exchange booth$"),
        Pattern.compile("(?i)^clan hall bank chest$"),
    };

    @Test
    public void dumpBankTilePlacements() throws IOException {
        Assume.assumeTrue(
            "Enable with -DbankTiles.dump=true (and provide -DbankTiles.cacheDir / -DbankTiles.xteaPath)",
            Boolean.getBoolean(DUMP_PROPERTY));

        String cacheDir = requiredProperty(CACHE_DIR_PROPERTY);
        String xteaPath = requiredProperty(XTEA_PROPERTY);
        Path output = Paths.get(System.getProperty(OUTPUT_PROPERTY,
            "build/bank-tiles/bank_tile_placements.tsv"));
        Pattern[] patterns = resolvePatterns();

        Files.createDirectories(output.getParent());

        XteaKeyManager xteaKeyManager = new XteaKeyManager();
        try (FileInputStream fin = new FileInputStream(xteaPath)) {
            xteaKeyManager.loadKeys(fin);
        }

        List<Row> rows;
        try (Store store = new Store(new File(cacheDir))) {
            store.load();

            ObjectManager objectManager = new ObjectManager(store);
            objectManager.load();
            RegionLoader regionLoader = new RegionLoader(store, xteaKeyManager);
            regionLoader.loadRegions();
            regionLoader.calculateBounds();

            Set<Integer> matchingIds = collectMatchingIds(objectManager, patterns);
            System.out.println("Matched " + matchingIds.size() + " bank-related object definitions");

            rows = collectRows(regionLoader, objectManager, matchingIds);
        }

        writeTsv(rows, output);
        System.out.println("Wrote " + rows.size() + " placements to " + output.toAbsolutePath());
    }

    private static String requiredProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing required system property -D" + key + "=...");
        }
        return value;
    }

    private static Pattern[] resolvePatterns() {
        String override = System.getProperty(PATTERNS_PROPERTY);
        if (override == null || override.isEmpty()) {
            return DEFAULT_NAME_PATTERNS;
        }
        String[] parts = override.split(";;");
        Pattern[] patterns = new Pattern[parts.length];
        for (int i = 0; i < parts.length; i++) {
            patterns[i] = Pattern.compile(parts[i]);
        }
        return patterns;
    }

    private static Set<Integer> collectMatchingIds(ObjectManager objectManager, Pattern[] patterns) {
        Set<Integer> ids = new HashSet<>();
        for (ObjectDefinition def : objectManager.getObjects()) {
            String name = def.getName();
            if (name == null || "null".equalsIgnoreCase(name)) {
                continue;
            }
            for (Pattern p : patterns) {
                if (p.matcher(name).matches()) {
                    ids.add(def.getId());
                    break;
                }
            }
        }
        return ids;
    }

    private static List<Row> collectRows(RegionLoader regionLoader, ObjectManager objectManager,
                                         Set<Integer> matchingIds) {
        List<Row> rows = new ArrayList<>();
        Collection<Region> regions = regionLoader.getRegions();
        for (Region region : regions) {
            for (Location loc : region.getLocations()) {
                int id = loc.getId();
                if (!matchingIds.contains(id)) {
                    continue;
                }
                ObjectDefinition def = objectManager.getObject(id);
                Position pos = loc.getPosition();
                Row row = new Row();
                row.id = id;
                row.name = def.getName();
                row.x = pos.getX();
                row.y = pos.getY();
                row.plane = pos.getZ();
                row.regionId = region.getRegionID();
                row.localX = pos.getX() - region.getBaseX();
                row.localY = pos.getY() - region.getBaseY();
                row.type = loc.getType();
                row.orientation = loc.getOrientation();
                row.sizeX = def.getSizeX();
                row.sizeY = def.getSizeY();
                rows.add(row);
            }
        }
        Collections.sort(rows, (a, b) -> {
            int c = Integer.compare(a.regionId, b.regionId);
            if (c != 0) return c;
            c = Integer.compare(a.plane, b.plane);
            if (c != 0) return c;
            c = Integer.compare(a.x, b.x);
            if (c != 0) return c;
            return Integer.compare(a.y, b.y);
        });
        return rows;
    }

    private static void writeTsv(List<Row> rows, Path output) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(output.toFile()))) {
            w.write("id\tname\tx\ty\tplane\tregionId\tlocalX\tlocalY\ttype\torientation\tsizeX\tsizeY");
            w.newLine();
            for (Row r : rows) {
                w.write(String.format(Locale.ROOT,
                    "%d\t%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d",
                    r.id, r.name, r.x, r.y, r.plane,
                    r.regionId, r.localX, r.localY,
                    r.type, r.orientation, r.sizeX, r.sizeY));
                w.newLine();
            }
        }
    }

    private static final class Row {
        int id;
        String name;
        int x;
        int y;
        int plane;
        int regionId;
        int localX;
        int localY;
        int type;
        int orientation;
        int sizeX;
        int sizeY;
    }
}
