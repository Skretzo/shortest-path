package shortestpath;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.pathfinder.WildernessChecker;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class WildernessCheckerTest {

    private static final int WILDERNESS_GREEN_DRAGONS = WorldPointUtil.packWorldPoint(3339, 3696, 0);
    private static final int WILDERNESS_EDGEVILLE_DUNGEON = WorldPointUtil.packWorldPoint(3089, 9957, 0);
    private static final int WILDERNESS_LAVA_MAZE_DUNGEON = WorldPointUtil.packWorldPoint(3039, 10260, 0);
    private static final int WILDERNESS_DARK_WARRIORS_FORTRESS = WorldPointUtil.packWorldPoint(3023, 3626, 1);
    private static final int WILDERNESS_REVENANT_CAVES = WorldPointUtil.packWorldPoint(3198, 10074, 0);
    private static final int WILDERNESS_SLAYER_CAVE = WorldPointUtil.packWorldPoint(3406, 10144, 0);
    private static final int WILDERNESS_GOD_WARS_DUNGEON = WorldPointUtil.packWorldPoint(3044, 10134, 0);
    private static final int WILDERNESS_GOD_WARS_DUNGEON_UPSTAIRS = WorldPointUtil.packWorldPoint(3065, 10155, 3);

    private static final int WILDERNESS_FEROX_ENCLAVE = WorldPointUtil.packWorldPoint(3134, 3629, 0);
    private static final int NON_WILDERNESS_PENINSULA = WorldPointUtil.packWorldPoint(3009, 3531, 0);

    private static final int WILDERNESS_BANDIT_CAMP = WorldPointUtil.packWorldPoint(3037, 3700, 0);

    @Test
    public void testIsInWilderness() {
        assertTrue(WildernessChecker.isInWilderness(WILDERNESS_GREEN_DRAGONS));
        assertTrue(WildernessChecker.isInWilderness(WILDERNESS_EDGEVILLE_DUNGEON));
        assertTrue(WildernessChecker.isInWilderness(WILDERNESS_LAVA_MAZE_DUNGEON));
        assertTrue(WildernessChecker.isInWilderness(WILDERNESS_DARK_WARRIORS_FORTRESS));
        assertTrue(WildernessChecker.isInWilderness(WILDERNESS_REVENANT_CAVES));
        assertTrue(WildernessChecker.isInWilderness(WILDERNESS_SLAYER_CAVE));
        assertTrue(WildernessChecker.isInWilderness(WILDERNESS_GOD_WARS_DUNGEON));
        assertTrue(WildernessChecker.isInWilderness(WILDERNESS_GOD_WARS_DUNGEON_UPSTAIRS));

        assertFalse(WildernessChecker.isInWilderness(NON_WILDERNESS_PENINSULA));
        assertFalse(WildernessChecker.isInWilderness(WILDERNESS_FEROX_ENCLAVE));
    }

    @Test
    public void testisInWildernessSet() {
        Set<Integer> packedPoints = new HashSet<>();

        // All wilderness areas
        packedPoints.add(WILDERNESS_DARK_WARRIORS_FORTRESS);
        packedPoints.add(WILDERNESS_EDGEVILLE_DUNGEON);
        assertTrue(WildernessChecker.isInWilderness(packedPoints));
        packedPoints.clear();

        // All non-wilderness areas
        packedPoints.add(WILDERNESS_FEROX_ENCLAVE);
        packedPoints.add(NON_WILDERNESS_PENINSULA);
        assertFalse(WildernessChecker.isInWilderness(packedPoints));
        packedPoints.clear();

        // Mixed areas
        packedPoints.add(WILDERNESS_GREEN_DRAGONS);
        packedPoints.add(NON_WILDERNESS_PENINSULA);
        assertTrue(WildernessChecker.isInWilderness(packedPoints));
    }

    @Test
    public void testInLevel20Wilderness() {
        assertTrue(WildernessChecker.isInLevel20Wilderness(WILDERNESS_BANDIT_CAMP));
        assertTrue(WildernessChecker.isInLevel20Wilderness(WILDERNESS_GOD_WARS_DUNGEON));
        assertFalse(WildernessChecker.isInLevel20Wilderness(WILDERNESS_SLAYER_CAVE));
    }

    @Test
    public void testInLevel30Wilderness() {
        assertTrue(WildernessChecker.isInLevel30Wilderness(WILDERNESS_LAVA_MAZE_DUNGEON));
    }
}
