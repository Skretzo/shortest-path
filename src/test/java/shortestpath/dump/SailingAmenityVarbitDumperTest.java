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
import net.runelite.cache.ItemManager;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ItemDefinition;
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
    // Sailors' Marker scenery object IDs (Port Roberts, Red Rock, Deepfin Point).
    // Interacting with a marker unlocks the Sailors' amulet teleport to that location.
    private static final int[] SAILORS_MARKER_IDS = {59985, 59986, 59988};

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

            // 4. Sailors' Marker dump. Print the marker def's own varbit/varp,
            //    any multi-loc parent that transforms into it, and every
            //    world placement of the marker object id.
            System.out.println();
            System.out.println("=== Sailors' Markers ===");
            System.out.println("id\tname\tvarbitId\tvarpId\ttransforms");
            Set<Integer> markerSet = new HashSet<>();
            for (int id : SAILORS_MARKER_IDS) markerSet.add(id);
            List<ObjectDefinition> markerParents = new ArrayList<>();
            for (ObjectDefinition def : objectManager.getObjects()) {
                if (markerSet.contains(def.getId())) {
                    System.out.println(def.getId()
                        + "\t" + def.getName()
                        + "\t" + def.getVarbitID()
                        + "\t" + def.getVarpID()
                        + "\t" + Arrays.toString(def.getConfigChangeDest()));
                }
                int[] dest = def.getConfigChangeDest();
                if (dest == null) continue;
                for (int d : dest) {
                    if (markerSet.contains(d)) { markerParents.add(def); break; }
                }
            }
            System.out.println();
            System.out.println("=== Sailors' Marker parents (multi-loc) ===");
            System.out.println("id\tname\tvarbitId\tvarpId\ttransforms");
            for (ObjectDefinition p : markerParents) {
                System.out.println(p.getId()
                    + "\t" + p.getName()
                    + "\t" + p.getVarbitID()
                    + "\t" + p.getVarpID()
                    + "\t" + Arrays.toString(p.getConfigChangeDest()));
            }
            System.out.println();
            System.out.println("=== Sailors' Marker placements ===");
            System.out.println("id\tx\ty\tplane");
            Set<Integer> markerPlusParentIds = new HashSet<>(markerSet);
            for (ObjectDefinition p : markerParents) markerPlusParentIds.add(p.getId());
            for (Region region : regionLoader.getRegions()) {
                for (Location loc : region.getLocations()) {
                    if (!markerPlusParentIds.contains(loc.getId())) continue;
                    Position pos = loc.getPosition();
                    System.out.println(loc.getId() + "\t" + pos.getX() + "\t" + pos.getY() + "\t" + pos.getZ());
                }
            }

            // 5. Sailors' amulet item dump. Walk every item named "Sailors' amulet..." and print
            //    inventoryActions (interfaceOptions), subops, params, and countObj/countCo chains.
            //    Teleport-gating varbits on OSRS items are typically stored in the item's params map
            //    (int-keyed) or implied by the subops that a cs2 script toggles.
            System.out.println();
            System.out.println("=== Sailors' amulet items ===");
            ItemManager itemManager = new ItemManager(store);
            itemManager.load();
            List<ItemDefinition> matches = new ArrayList<>();
            for (ItemDefinition item : itemManager.getItems()) {
                String name = item.getName();
                if (name == null) continue;
                if (name.toLowerCase().contains("sailors' amulet") || name.toLowerCase().contains("sailor's amulet")) {
                    matches.add(item);
                }
            }
            matches.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
            for (ItemDefinition item : matches) {
                System.out.println();
                System.out.println("-- id=" + item.getId() + " name=\"" + item.getName() + "\" --");
                System.out.println("  examine:          " + item.getExamine());
                System.out.println("  notedID:          " + item.notedID);
                System.out.println("  placeholderId:    " + item.placeholderId);
                System.out.println("  boughtId:         " + item.boughtId);
                System.out.println("  countCo:          " + Arrays.toString(item.getCountCo()));
                System.out.println("  countObj:         " + Arrays.toString(item.getCountObj()));
                System.out.println("  interfaceOptions: " + Arrays.toString(item.getInterfaceOptions()));
                String[][] subops = item.getSubops();
                if (subops != null) {
                    for (int i = 0; i < subops.length; i++) {
                        System.out.println("  subops[" + i + "]:        " + Arrays.toString(subops[i]));
                    }
                }
                Map<Integer, Object> params = item.params;
                if (params != null && !params.isEmpty()) {
                    // Sort params by key for readability.
                    List<Integer> keys = new ArrayList<>(params.keySet());
                    keys.sort(Integer::compareTo);
                    for (Integer k : keys) {
                        System.out.println("  param[" + k + "] = " + params.get(k));
                    }
                }
            }

            // Note: the cs2 script referenced by param[2257] on item 32399 handles the Teleport
            // submenu gating, but the bundled runelite-cache 1.12.24 ScriptLoader can't
            // reliably decode current-rev scripts, so we don't extract the unlock varbit here.
            // The subop indices (0=Pandemonium, 1=Port Roberts, 4=Deepfin Point) and the marker
            // coordinates from section 4 are enough for a contributor to identify the varbit
            // in-game via RuneLite's varbit inspector.
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
