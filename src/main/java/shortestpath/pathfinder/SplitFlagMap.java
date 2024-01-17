package shortestpath.pathfinder;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ResourceInfo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import shortestpath.ShortestPathPlugin;

import static net.runelite.api.Constants.REGION_SIZE;

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
        try {
            ClassPath classPath = ClassPath.from(ShortestPathPlugin.class.getClassLoader());

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = 0;
            int maxY = 0;

            for (ResourceInfo info : classPath.getResources()) {
                if (info.getResourceName().startsWith("collision-map/")) {
                    String[] n = info.getResourceName().split("/")[1].split("_");
                    final int x = Integer.parseInt(n[0]);
                    final int y = Integer.parseInt(n[1]);
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    compressedRegions.put(SplitFlagMap.packPosition(x, y), info.asByteSource().read());
                }
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
