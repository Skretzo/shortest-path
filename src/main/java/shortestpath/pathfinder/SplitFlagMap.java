package shortestpath.pathfinder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import shortestpath.ShortestPathPlugin;

import static net.runelite.api.Constants.REGION_SIZE;
import shortestpath.Util;

public class SplitFlagMap {
    @Getter
    private static RegionExtent regionExtents;

    @Getter
    private final byte[] regionMapPlaneCounts;
    // Size is automatically chosen based on the max extents of the collision data
    private final FlagMap[] regionMaps;
    private final int widthInclusive;

    public SplitFlagMap(Map<Integer, byte[]> compressedRegions) {
        widthInclusive = regionExtents.getWidth() + 1;
        final int heightInclusive = regionExtents.getHeight() + 1;
        regionMaps = new FlagMap[widthInclusive * heightInclusive];
        regionMapPlaneCounts = new byte[regionMaps.length];

        for (Map.Entry<Integer, byte[]> entry : compressedRegions.entrySet()) {
            final int pos = entry.getKey();
            final int x = unpackX(pos);
            final int y = unpackY(pos);
            final int index = getIndex(x, y);
            FlagMap flagMap = new FlagMap(x * REGION_SIZE, y * REGION_SIZE, entry.getValue());
            regionMaps[index] = flagMap;
            regionMapPlaneCounts[index] = flagMap.getPlaneCount();
        }
    }

    public boolean get(int x, int y, int z, int flag) {
        final int index = getIndex(x / REGION_SIZE, y / REGION_SIZE);
        if (index < 0 || index >= regionMaps.length || regionMaps[index] == null) {
            return false;
        }

        return regionMaps[index].get(x, y, z, flag);
    }

    private int getIndex(int regionX, int regionY) {
        return (regionX - regionExtents.getMinX()) + (regionY - regionExtents.getMinY()) * widthInclusive;
    }

    public static int unpackX(int position) {
        return position & 0xFFFF;
    }

    public static int unpackY(int position) {
        return (position >> 16) & 0xFFFF;
    }

    public static int packPosition(int x, int y) {
        return (x & 0xFFFF) | ((y & 0xFFFF) << 16);
    }

    public static SplitFlagMap fromResources() {
        Map<Integer, byte[]> compressedRegions = new HashMap<>();

        try (InputStream dir = ShortestPathPlugin.class.getResourceAsStream("/collision-map")) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;

            BufferedReader lines = new BufferedReader(new InputStreamReader(dir, StandardCharsets.UTF_8));
            String name;
            while ((name = lines.readLine()) != null) {
                InputStream in = ShortestPathPlugin.class.getResourceAsStream("/collision-map/" + name);
                String[] n = name.split("_");
                final int x = Integer.parseInt(n[0]);
                final int y = Integer.parseInt(n[1]);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);

                compressedRegions.put(SplitFlagMap.packPosition(x, y), Util.readAllBytes(in));
            }

            regionExtents = new RegionExtent(minX, minY, maxX, maxY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new SplitFlagMap(compressedRegions);
    }

    @RequiredArgsConstructor
    @Getter
    public static class RegionExtent {
        public final int minX, minY, maxX, maxY;

        public int getWidth() {
            return maxX - minX;
        }

        public int getHeight() {
            return maxY - minY;
        }
    }
}
