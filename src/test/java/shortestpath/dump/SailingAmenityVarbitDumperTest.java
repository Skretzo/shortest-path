package shortestpath.dump;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Finds the varbits that gate the Sailing-island construction-built bank
 * chests.
 *
 * The wiki's Bank chest (amenity) infobox names two object IDs:
 *   58662 — "Bank chest space" (unbuilt)
 *   58663 — "Bank chest" (built)
 *
 * Both definitions carry no varbit and are never placed in the landscape
 * directly. Each physical chest is a multi-loc parent object whose
 * {@code configChangeDest} transforms between 58662 and 58663 based on its
 * varbit. This dumper:
 *   1. finds every parent object whose transforms reference 58662/58663,
 *   2. prints that parent's varbit,
 *   3. looks up the parent's one and only world placement.
 *
 * Run with:
 *   ./gradlew sailingAmenityVarbitDump \
 *     -PsailingAmenityCacheDir=$PWD/cache \
 *     -PsailingAmenityXteaPath=$PWD/keys.json
 */
public class SailingAmenityVarbitDumperTest {
    private static final int[] SEED_IDS = {58662, 58663};

    @Test
    public void dumpSailingAmenityVarbits() throws Exception {
        Assume.assumeTrue(
            "Enable with -Dsailing.amenity.dump=true and supply -Dsailing.amenity.cacheDir / -Dsailing.amenity.xteaPath",
            Boolean.getBoolean("sailing.amenity.dump"));

        String cacheDir = requiredProperty("sailing.amenity.cacheDir");
        String xteaPath = requiredProperty("sailing.amenity.xteaPath");

        XteaKeyManager xteaKeyManager = new XteaKeyManager();
        try (FileInputStream fin = new FileInputStream(xteaPath)) {
            xteaKeyManager.loadKeys(fin);
        }

        try (Store store = new Store(new File(cacheDir))) {
            store.load();

            ObjectManager objectManager = new ObjectManager(store);
            objectManager.load();

            Set<Integer> seedSet = new HashSet<>();
            for (int id : SEED_IDS) seedSet.add(id);

            // 1. Parent multi-loc objects that transform into the seed IDs.
            List<ObjectDefinition> parents = new ArrayList<>();
            for (ObjectDefinition def : objectManager.getObjects()) {
                int[] dest = def.getConfigChangeDest();
                if (dest == null) continue;
                for (int d : dest) {
                    if (seedSet.contains(d)) { parents.add(def); break; }
                }
            }
            parents.sort((a, b) -> Integer.compare(a.getId(), b.getId()));

            // 2. World placements of those parents.
            RegionLoader regionLoader = new RegionLoader(store, xteaKeyManager);
            regionLoader.loadRegions();
            regionLoader.calculateBounds();

            Set<Integer> parentIds = new HashSet<>();
            for (ObjectDefinition p : parents) parentIds.add(p.getId());

            Map<Integer, int[]> placement = new HashMap<>();
            for (Region region : regionLoader.getRegions()) {
                for (Location loc : region.getLocations()) {
                    if (!parentIds.contains(loc.getId())) continue;
                    Position pos = loc.getPosition();
                    placement.put(loc.getId(), new int[]{pos.getX(), pos.getY(), pos.getZ()});
                }
            }

            // 3. Report.
            System.out.println("id\tvarbitId\tvarpId\ttransforms\tx\ty\tplane");
            for (ObjectDefinition p : parents) {
                int[] pos = placement.get(p.getId());
                String coord = pos == null ? "?\t?\t?" : pos[0] + "\t" + pos[1] + "\t" + pos[2];
                System.out.println(p.getId()
                    + "\t" + p.getVarbitID()
                    + "\t" + p.getVarpID()
                    + "\t" + Arrays.toString(p.getConfigChangeDest())
                    + "\t" + coord);
            }
        }
    }

    private static String requiredProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing required system property -D" + key + "=...");
        }
        return value;
    }
}
