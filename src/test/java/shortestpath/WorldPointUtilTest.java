package shortestpath;

import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.api.Constants.CHUNK_SIZE;
import static net.runelite.api.Perspective.LOCAL_COORD_BITS;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link WorldPointUtil}.
 */
public class WorldPointUtilTest {

    @Test
    public void packAndUnpackRoundTrip() {
        WorldPoint p = new WorldPoint(1234, 5678, 2);
        int packed = WorldPointUtil.packWorldPoint(p);
        assertEquals(1234, WorldPointUtil.unpackWorldX(packed));
        assertEquals(5678, WorldPointUtil.unpackWorldY(packed));
        assertEquals(2, WorldPointUtil.unpackWorldPlane(packed));

        WorldPoint unpacked = WorldPointUtil.unpackWorldPoint(packed);
        assertEquals(p, unpacked);
    }

    @Test
    public void packMasksHighBits() {
        int x = 40000; // exceeds 15 bits
        int y = 50000; // exceeds 15 bits
        int plane = 7; // exceeds 2 bits
        int packed = WorldPointUtil.packWorldPoint(x, y, plane);
        assertEquals(x & 0x7FFF, WorldPointUtil.unpackWorldX(packed));
        assertEquals(y & 0x7FFF, WorldPointUtil.unpackWorldY(packed));
        assertEquals(plane & 0x3, WorldPointUtil.unpackWorldPlane(packed));
    }

    @Test
    public void dxdyOffsetsCorrectly() {
        int base = WorldPointUtil.packWorldPoint(3200, 3200, 1);
        int moved = WorldPointUtil.dxdy(base, 5, -7);
        assertEquals(3200 + 5, WorldPointUtil.unpackWorldX(moved));
        assertEquals(3200 - 7, WorldPointUtil.unpackWorldY(moved));
        assertEquals(1, WorldPointUtil.unpackWorldPlane(moved));
    }

    @Test
    public void distanceChebyshevAndManhattan() {
        int a = WorldPointUtil.packWorldPoint(10, 10, 0);
        int b = WorldPointUtil.packWorldPoint(15, 13, 0); // dx=5 dy=3
        assertEquals(5, WorldPointUtil.distanceBetween(a, b)); // Chebyshev
        assertEquals(5, WorldPointUtil.distanceBetween2D(a, b)); // Chebyshev 2D
        assertEquals(5 + 3, WorldPointUtil.distanceBetween(a, b, 2)); // Manhattan
        assertEquals(Integer.MAX_VALUE, WorldPointUtil.distanceBetween(a, b, 99));
    }

    @Test
    public void distanceDifferentPlanesReturnsMax() {
        int a = WorldPointUtil.packWorldPoint(10, 10, 0);
        int b = WorldPointUtil.packWorldPoint(11, 11, 1);
        assertEquals(Integer.MAX_VALUE, WorldPointUtil.distanceBetween(a, b));
    }

    @Test
    public void distanceWorldPointOverloads() {
        WorldPoint a = new WorldPoint(100, 150, 0);
        WorldPoint b = new WorldPoint(103, 160, 0); // dx=3 dy=10
        assertEquals(10, WorldPointUtil.distanceBetween(a, b)); // Chebyshev
        assertEquals(13, WorldPointUtil.distanceBetween(a, b, 2)); // Manhattan
    }

    @Test
    public void distanceToAreaInsideAndOutside() {
        WorldArea area = new WorldArea(3000, 4000, 5, 4, 0); // x:[3000..3004], y:[4000..4003]
        int inside = WorldPointUtil.packWorldPoint(3002, 4001, 0);
        int leftOutside = WorldPointUtil.packWorldPoint(2995, 4002, 0);
        int diagOutside = WorldPointUtil.packWorldPoint(2990, 3990, 0);
        int otherPlane = WorldPointUtil.packWorldPoint(3002, 4001, 1);

        assertEquals(0, WorldPointUtil.distanceToArea(inside, area));
        assertEquals(0, WorldPointUtil.distanceToArea2D(inside, area));
        // leftOutside: dx = 3000-2995=5, dy=0 => Chebyshev 5
        assertEquals(5, WorldPointUtil.distanceToArea(leftOutside, area));
        assertEquals(5, WorldPointUtil.distanceToArea2D(leftOutside, area));
        // diagOutside: dx=3000-2990=10, dy=4000-3990=10 => Chebyshev 10
        assertEquals(10, WorldPointUtil.distanceToArea(diagOutside, area));
        assertEquals(10, WorldPointUtil.distanceToArea2D(diagOutside, area));
        // plane mismatch
        assertEquals(Integer.MAX_VALUE, WorldPointUtil.distanceToArea(otherPlane, area));
    }

    @Test
    public void toLocalPointWithinScene() {
        Client client = Mockito.mock(Client.class);
        WorldView worldView = Mockito.mock(WorldView.class);
        Mockito.when(client.getTopLevelWorldView()).thenReturn(worldView);
        Mockito.when(worldView.getPlane()).thenReturn(0);
        Mockito.when(worldView.getBaseX()).thenReturn(3200);
        Mockito.when(worldView.getBaseY()).thenReturn(3200);
        Mockito.when(worldView.getSizeX()).thenReturn(104); // typical scene size
        Mockito.when(worldView.getSizeY()).thenReturn(104);
        Mockito.when(worldView.getId()).thenReturn(0);

        int packed = WorldPointUtil.packWorldPoint(3210, 3220, 0);
        LocalPoint lp = WorldPointUtil.toLocalPoint(client, packed);
        assertNotNull(lp);
        int expectedLocalX = ((3210 - 3200) << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
        int expectedLocalY = ((3220 - 3200) << LOCAL_COORD_BITS) + (1 << (LOCAL_COORD_BITS - 1));
        assertEquals(expectedLocalX, lp.getX());
        assertEquals(expectedLocalY, lp.getY());
    }

    @Test
    public void toLocalPointOutOfSceneOrPlane() {
        Client client = Mockito.mock(Client.class);
        WorldView worldView = Mockito.mock(WorldView.class);
        Mockito.when(client.getTopLevelWorldView()).thenReturn(worldView);
        Mockito.when(worldView.getPlane()).thenReturn(0);
        Mockito.when(worldView.getBaseX()).thenReturn(3200);
        Mockito.when(worldView.getBaseY()).thenReturn(3200);
        Mockito.when(worldView.getSizeX()).thenReturn(104);
        Mockito.when(worldView.getSizeY()).thenReturn(104);
        Mockito.when(worldView.getId()).thenReturn(0);

        // Plane mismatch
        int packedWrongPlane = WorldPointUtil.packWorldPoint(3210, 3210, 1);
        assertNull(WorldPointUtil.toLocalPoint(client, packedWrongPlane));

        // Outside scene (x less than baseX)
        int packedOutside = WorldPointUtil.packWorldPoint(3199, 3210, 0);
        assertNull(WorldPointUtil.toLocalPoint(client, packedOutside));
    }

    @Test
    public void toLocalInstanceNonInstanceWorld() {
        Client client = Mockito.mock(Client.class);
        WorldView top = Mockito.mock(WorldView.class);
        Mockito.when(client.getTopLevelWorldView()).thenReturn(top);
        Mockito.when(top.isInstance()).thenReturn(false);
        int packed = WorldPointUtil.packWorldPoint(4000, 5000, 2);
        PrimitiveIntList list = WorldPointUtil.toLocalInstance(client, packed);
        assertEquals(1, list.size());
        assertEquals(packed, list.get(0));
    }

    @Test
    public void fromLocalInstanceNonInstanceWorld() {
        Client client = Mockito.mock(Client.class);
        WorldView view = Mockito.mock(WorldView.class);
        Mockito.when(client.getWorldView(0)).thenReturn(view);
        Mockito.when(view.getPlane()).thenReturn(0);
        Mockito.when(view.isInstance()).thenReturn(false);
        Mockito.when(view.getBaseX()).thenReturn(3200);
        Mockito.when(view.getBaseY()).thenReturn(3200);

        int tileX = 3210;
        int tileY = 3222;
        // LocalPoint stores scene (local) coordinates relative to base, so subtract base before shifting
        int localTileX = tileX - 3200;
        int localTileY = tileY - 3200;
        LocalPoint lp = new LocalPoint(localTileX << LOCAL_COORD_BITS, localTileY << LOCAL_COORD_BITS, 0);
        int packed = WorldPointUtil.fromLocalInstance(client, lp);
        assertEquals(tileX, WorldPointUtil.unpackWorldX(packed));
        assertEquals(tileY, WorldPointUtil.unpackWorldY(packed));
        assertEquals(0, WorldPointUtil.unpackWorldPlane(packed));
    }

    @Test
    public void toLocalInstanceSimpleInstanceMatch() {
        // Build a minimal instance template where a single chunk maps to a template containing our point
        Client client = Mockito.mock(Client.class);
        WorldView top = Mockito.mock(WorldView.class);
        Mockito.when(client.getTopLevelWorldView()).thenReturn(top);
        Mockito.when(top.isInstance()).thenReturn(true);
        Mockito.when(top.getBaseX()).thenReturn(0);
        Mockito.when(top.getBaseY()).thenReturn(0);

        // one plane, 2x2 chunks
        int planes = 1;
        int size = 2; // chunk dimensions in each axis
        int[][][] chunks = new int[planes][size][size];

        // Target world point inside template chunk at chunk indices (1,1)
        int worldX = 100; // arbitrary
        int worldY = 200;
        int templateChunkX = (worldX & ~(CHUNK_SIZE - 1));
        int templateChunkY = (worldY & ~(CHUNK_SIZE - 1));
        int chunkIndexX = 1;
        int chunkIndexY = 1;
        int rotation = 0;
        int plane = 0;
        int chunkData = (plane << 24) | ((templateChunkX / CHUNK_SIZE) << 14) | ((templateChunkY / CHUNK_SIZE) << 3) | (rotation << 1);
        chunks[0][chunkIndexX][chunkIndexY] = chunkData;
        Mockito.when(top.getInstanceTemplateChunks()).thenReturn(chunks);
        Mockito.when(top.getPlane()).thenReturn(0);

        int packed = WorldPointUtil.packWorldPoint(worldX, worldY, 0);
        PrimitiveIntList list = WorldPointUtil.toLocalInstance(client, packed);
        assertTrue("Expected at least one mapping", list.size() >= 1);
        // The produced local instance point should be within the instance chunk bounds
        int mapped = list.get(0);
        int mx = WorldPointUtil.unpackWorldX(mapped);
        int my = WorldPointUtil.unpackWorldY(mapped);
        assertTrue(mx >= chunkIndexX * CHUNK_SIZE && mx < (chunkIndexX + 1) * CHUNK_SIZE + CHUNK_SIZE); // loose bound
        assertTrue(my >= chunkIndexY * CHUNK_SIZE && my < (chunkIndexY + 1) * CHUNK_SIZE + CHUNK_SIZE);
    }

    // --- Tests merged from WorldPointTests.java ---

    private static final WorldArea WILDERNESS_ABOVE_GROUND = new WorldArea(2944, 3523, 448, 448, 0);

    @Test
    public void testDistanceToArea() {
        List<WorldPoint> testPoints = new ArrayList<>(10);
        testPoints.add(new WorldPoint(2900, 3500, 0));
        testPoints.add(new WorldPoint(3000, 3500, 0));
        testPoints.add(new WorldPoint(3600, 3500, 0));
        testPoints.add(new WorldPoint(2900, 3622, 0));
        testPoints.add(new WorldPoint(3000, 3622, 0));
        testPoints.add(new WorldPoint(3600, 3622, 0));
        testPoints.add(new WorldPoint(2900, 4300, 0));
        testPoints.add(new WorldPoint(3000, 4300, 0));
        testPoints.add(new WorldPoint(3600, 4300, 0));
        testPoints.add(new WorldPoint(3600, 4200, 1));

        for (WorldPoint point : testPoints) {
            final int areaDistance = WILDERNESS_ABOVE_GROUND.distanceTo(point);
            final int packedPoint = WorldPointUtil.packWorldPoint(point);
            final int worldUtilDistance = WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND);
            assertEquals("Calculating distance to " + point + " failed", areaDistance, worldUtilDistance);
        }
    }

    @Test
    public void testWorldPointPacking() {
        WorldPoint point = new WorldPoint(13, 24685, 1);

        final int packedPoint = WorldPointUtil.packWorldPoint(point);
        assertEquals((1 << 30) | (24685 << 15) | 13, packedPoint);
        final int unpackedX = WorldPointUtil.unpackWorldX(packedPoint);
        assertEquals(point.getX(), unpackedX);

        final int unpackedY = WorldPointUtil.unpackWorldY(packedPoint);
        assertEquals(point.getY(), unpackedY);

        final int unpackedPlane = WorldPointUtil.unpackWorldPlane(packedPoint);
        assertEquals(point.getPlane(), unpackedPlane);

        WorldPoint unpackedPoint = WorldPointUtil.unpackWorldPoint(packedPoint);
        assertEquals(point, unpackedPoint);
    }

    @Test
    public void testDistanceBetween() {
        WorldPoint pointA = new WorldPoint(13, 24685, 1);
        WorldPoint pointB = new WorldPoint(29241, 3384, 1);
        WorldPoint pointC = new WorldPoint(292, 3384, 0); // Test point on different plane

        assertEquals(0, WorldPointUtil.distanceBetween(pointA, pointA));
        assertEquals(29228, WorldPointUtil.distanceBetween(pointA, pointB));
        assertEquals(29228, WorldPointUtil.distanceBetween(pointB, pointA));
        assertEquals(Integer.MAX_VALUE, WorldPointUtil.distanceBetween(pointA, pointC));

        // with diagonal = 2
        assertEquals(0, WorldPointUtil.distanceBetween(pointA, pointA, 2));
        assertEquals(50529, WorldPointUtil.distanceBetween(pointA, pointB, 2));
        assertEquals(50529, WorldPointUtil.distanceBetween(pointB, pointA, 2));
        assertEquals(Integer.MAX_VALUE, WorldPointUtil.distanceBetween(pointB, pointC, 2));
    }

    @Test
    public void testPackedDistanceBetween() {
        WorldPoint pointA = new WorldPoint(13, 24685, 1);
        WorldPoint pointB = new WorldPoint(29241, 3384, 1);
        WorldPoint pointC = new WorldPoint(292, 3384, 0); // Test point on different plane
        final int packedPointA = WorldPointUtil.packWorldPoint(pointA);
        final int packedPointB = WorldPointUtil.packWorldPoint(pointB);
        final int packedPointC = WorldPointUtil.packWorldPoint(pointC);

        assertEquals(0, WorldPointUtil.distanceBetween(packedPointA, packedPointA));
        assertEquals(29228, WorldPointUtil.distanceBetween(packedPointA, packedPointB));
        assertEquals(29228, WorldPointUtil.distanceBetween(packedPointB, packedPointA));
        assertEquals(Integer.MAX_VALUE, WorldPointUtil.distanceBetween(packedPointA, packedPointC));

        // with diagonal = 2
        assertEquals(0, WorldPointUtil.distanceBetween(packedPointA, packedPointA, 2));
        assertEquals(50529, WorldPointUtil.distanceBetween(packedPointA, packedPointB, 2));
        assertEquals(50529, WorldPointUtil.distanceBetween(packedPointB, packedPointA, 2));
        assertEquals(Integer.MAX_VALUE, WorldPointUtil.distanceBetween(packedPointB, packedPointC, 2));
    }

    @Test
    public void testMaxWorldPoint() {
        assertEquals(WorldPointUtil.packWorldPoint(-1, -1, -1), -1);
        assertEquals(WorldPointUtil.packWorldPoint(-1, -1, 1), Integer.MAX_VALUE);
    }
}
