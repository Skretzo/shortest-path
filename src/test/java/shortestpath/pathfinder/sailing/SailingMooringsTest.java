package shortestpath.pathfinder.sailing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;
import shortestpath.sailing.BoatSpeed;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.TestWorldData;

/** The mooring data and the sea it opens onto. */
public class SailingMooringsTest
{
	@Test
	public void everyLandTileIsWalkable()
	{
		// Boarding and docking are transports, and obey their rule: a route walks onto the land tile to board, and off it
		// once docked, so it is never blocked
		CollisionMap map = new CollisionMap(TestWorldData.map());
		List<String> blocked = new java.util.ArrayList<>();
		for (SailingMooring mooring : SailingMoorings.all())
		{
			int tile = mooring.landTile();
			if (map.isBlocked(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile), WorldPointUtil.unpackWorldPlane(tile)))
				blocked.add(mooring.name() + " " + WorldPointUtil.unpackWorldX(tile) + " " + WorldPointUtil.unpackWorldY(tile));
		}
		assertEquals("blocked land tiles: " + blocked, List.of(), blocked);
	}

	@Test
	public void everyMooringParsesWithItsRequirements()
	{
		List<SailingMooring> moorings = SailingMoorings.all();
		assertEquals(60, moorings.size());
		SailingMooring sarim = byName(moorings, "Port Sarim");
		assertEquals(1, sarim.requirements().getSkillLevels().get(Skill.SAILING));
		SailingMooring summerShore = byName(moorings, "The Summer Shore");
		assertEquals(45, summerShore.requirements().getSkillLevels().get(Skill.SAILING));
		assertTrue(summerShore.questsStarted().contains(Quest.TROUBLED_TORTUGANS));
		assertTrue(byName(moorings, "Corsair Cove").requirements().getQuests().contains(Quest.THE_CORSAIR_CURSE));
		assertTrue(moorings.stream().noneMatch(m -> m.name().equals("Wyrmscraig Cavern")));
		for (int i = 0; i < moorings.size(); i++) assertEquals(i, moorings.get(i).id());
	}

	@Test
	public void everyLandTileIsWalkableAndEveryBerthOpensOntoTheSea()
	{
		CollisionMap map = new CollisionMap(TestWorldData.map());
		List<SailingMooring> moorings = SailingMoorings.all();
		SailingWaters sea = SailingWaters.of(map);
		for (SailingMooring mooring : moorings)
		{
			int land = mooring.landTile();
			assertFalse(mooring + " land is blocked",
				map.isBlocked(WorldPointUtil.unpackWorldX(land), WorldPointUtil.unpackWorldY(land), 0));
			assertFalse(mooring + " land is not sea", sea.contains(land));
			int berth = mooring.berthTile();
			boolean opens = false;
			for (int dy = -3; dy <= 3; dy++)
				for (int dx = -3; dx <= 3; dx++)
					opens |= sea.contains(WorldPointUtil.packWorldPoint(WorldPointUtil.unpackWorldX(berth) + dx,
						WorldPointUtil.unpackWorldY(berth) + dy, 0));
			assertTrue(mooring + " berth", opens);
		}
	}

	@Test
	public void theSeaIsWaterNotLand()
	{
		CollisionMap map = new CollisionMap(TestWorldData.map());
		SailingWaters sea = SailingWaters.of(map);
		assertTrue(sea.contains(point(3045, 3180)));
		assertTrue("the Mudskipper Point dive site", sea.contains(point(2979, 3085)));
		assertTrue("past the Elid Delta islands", sea.contains(point(3273, 2738)));
		assertFalse("Lumbridge", sea.contains(point(3222, 3218)));
		assertFalse("a clue on the Great Conch", sea.contains(point(3143, 2423)));
		assertFalse("Dognose Island's mooring", sea.contains(point(3060, 2638)));
		int[] seaTiles = {0};
		sea.forEachTile(tile -> seaTiles[0]++);
		assertTrue(seaTiles[0] > 1_000_000);
		List<SailingMooring> moorings = SailingMoorings.all();
		SailingVessel raft = SailingVessel.of(SailingVessel.Kind.RAFT, BoatSpeed.ESTIMATED)
			.withAreas(shortestpath.sailing.SeaArea.ALL);
		int[] sarim = sea.castOff(byName(moorings, "Port Sarim"), raft);
		assertTrue("Port Sarim and Dognose Island are on the same sea", sea.connects(raft, sarim,
			byName(moorings, "Dognose Island").dockingTiles()));
		assertTrue(sea.connects(raft, sarim, Tiles.within(point(2979, 3085), 1)));
	}

	@Test(expected = IllegalStateException.class)
	public void anUnknownQuestFailsTheLoad()
	{
		SailingMoorings.parse("# Name\tLand\tBerth\tSkills\tQuests\tQuests started\tVessels\n"
			+ "Nowhere\t1 1 0\t2 2 0\t1 Sailing\tNot A Quest\t\t\n");
	}

	private static SailingMooring byName(List<SailingMooring> moorings, String name)
	{
		return moorings.stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
	}

	private static int point(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}
}
