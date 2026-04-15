package shortestpath.pathfinder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import shortestpath.WorldPointUtil;

public class VisitedTilesTest {
    @Test
    public void settingBankedTileAlsoMarksUnbankedTileVisited() {
        VisitedTiles visited = new VisitedTiles(collisionMap());
        int tile = WorldPointUtil.packWorldPoint(3200, 3200, 0);

        assertTrue(visited.set(tile, true));

        assertTrue(visited.get(tile, true));
        assertTrue(visited.get(tile, false));
        assertFalse(visited.set(tile, false));
    }

    @Test
    public void settingBankedAbstractNodeAlsoMarksUnbankedAbstractNodeVisited() {
        VisitedTiles visited = new VisitedTiles(collisionMap());
        Node banked = Node.abstractNode(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, null, true);
        Node unbanked = Node.abstractNode(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, null, false);

        assertTrue(visited.set(banked));

        assertTrue(visited.get(banked));
        assertTrue(visited.get(unbanked));
        assertFalse(visited.set(unbanked));
    }

    @Test
    public void settingUnbankedNodeDoesNotMarkBankedNodeVisited() {
        VisitedTiles visited = new VisitedTiles(collisionMap());
        int tile = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Node unbanked = Node.abstractNode(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, null, false);
        Node banked = Node.abstractNode(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, null, true);

        assertTrue(visited.set(tile, false));
        assertTrue(visited.get(tile, false));
        assertFalse(visited.get(tile, true));

        assertTrue(visited.set(unbanked));
        assertTrue(visited.get(unbanked));
        assertFalse(visited.get(banked));
    }

    private static CollisionMap collisionMap() {
        return new CollisionMap(SplitFlagMap.fromResources());
    }
}
