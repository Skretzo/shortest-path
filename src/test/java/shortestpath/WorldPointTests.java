package shortestpath;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static shortestpath.WorldPointUtil.distanceBetween;
import static shortestpath.WorldPointUtil.distanceToArea;
import static shortestpath.WorldPointUtil.packWorldPoint;
import static shortestpath.WorldPointUtil.unpackWorldPlane;
import static shortestpath.WorldPointUtil.unpackWorldPoint;
import static shortestpath.WorldPointUtil.unpackWorldX;
import static shortestpath.WorldPointUtil.unpackWorldY;

public class WorldPointTests {
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
            final int packedPoint = packWorldPoint(point);
            final int worldUtilDistance = distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND);
            Assert.assertEquals("Calculating distance to " + point + " failed", areaDistance, worldUtilDistance);
        }
    }

    @Test
    public void testWorldPointPacking() {
        WorldPoint point = new WorldPoint(13, 24685, 1);

        final int packedPoint = packWorldPoint(point);
        Assert.assertEquals(0x7036800D, packedPoint); // Manually verified

        final int unpackedX = unpackWorldX(packedPoint);
        Assert.assertEquals(point.getX(), unpackedX);

        final int unpackedY = unpackWorldY(packedPoint);
        Assert.assertEquals(point.getY(), unpackedY);

        final int unpackedPlane = unpackWorldPlane(packedPoint);
        Assert.assertEquals(point.getPlane(), unpackedPlane);

        WorldPoint unpackedPoint = unpackWorldPoint(packedPoint);
        Assert.assertEquals(point, unpackedPoint);
    }

    @Test
    public void testDistanceBetween() {
        WorldPoint pointA = new WorldPoint(13, 24685, 1);
        WorldPoint pointB = new WorldPoint(29241, 3384, 1);
        WorldPoint pointC = new WorldPoint(292, 3384, 0); // Test point on different plane

        Assert.assertEquals(0, distanceBetween(pointA, pointA));
        Assert.assertEquals(29228, distanceBetween(pointA, pointB));
        Assert.assertEquals(29228, distanceBetween(pointB, pointA));
        Assert.assertEquals(Integer.MAX_VALUE, distanceBetween(pointA, pointC));

        // with diagonal = 2
        Assert.assertEquals(0, distanceBetween(pointA, pointA, 2));
        Assert.assertEquals(50529, distanceBetween(pointA, pointB, 2));
        Assert.assertEquals(50529, distanceBetween(pointB, pointA, 2));
        Assert.assertEquals(Integer.MAX_VALUE, distanceBetween(pointB, pointC, 2));
    }

    @Test
    public void testPackedDistanceBetween() {
        WorldPoint pointA = new WorldPoint(13, 24685, 1);
        WorldPoint pointB = new WorldPoint(29241, 3384, 1);
        WorldPoint pointC = new WorldPoint(292, 3384, 0); // Test point on different plane
        final int packedPointA = packWorldPoint(pointA);
        final int packedPointB = packWorldPoint(pointB);
        final int packedPointC = packWorldPoint(pointC);

        Assert.assertEquals(0, distanceBetween(packedPointA, packedPointA));
        Assert.assertEquals(29228, distanceBetween(packedPointA, packedPointB));
        Assert.assertEquals(29228, distanceBetween(packedPointB, packedPointA));
        Assert.assertEquals(Integer.MAX_VALUE, distanceBetween(packedPointA, packedPointC));

        // with diagonal = 2
        Assert.assertEquals(0, distanceBetween(packedPointA, packedPointA, 2));
        Assert.assertEquals(50529, distanceBetween(packedPointA, packedPointB, 2));
        Assert.assertEquals(50529, distanceBetween(packedPointB, packedPointA, 2));
        Assert.assertEquals(Integer.MAX_VALUE, distanceBetween(packedPointB, packedPointC, 2));
    }

    @Test
    public void testMaxWorldPoint() {
        Assert.assertEquals(packWorldPoint(-1, -1, -1), -1);
        Assert.assertEquals(packWorldPoint(-1, -1, 1), 2147483647);
    }
}
