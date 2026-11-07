package shortestpath.pathfinder.sailing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.pathfinder.astar.EstimateEdges;
import shortestpath.sailing.BoatSpeed;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.TestWorldData;
import shortestpath.sailing.SeaArea;

/** The generated sailing data: current with what it comes from, and what the search's estimate knows from it. */
public class SailingDataTest
{
	@Test
	public void theSailingLayerIsCurrent()
	{
		String expected = SailingDataFingerprint.of(SailingDataFingerprint.resource("/collision-map.zip"), SailingMoorings.all());
		assertEquals("the collision map's sailing layer is stale: run shortest-path-tooling's ./gradlew sailingData", expected,
			SailingWaters.of(new CollisionMap(TestWorldData.map())).inputs());
	}

	@Test
	public void theRaftReachesFurtherThanTheSloop()
	{
		// Lum Lagoon, behind a channel the sloop's hull can't get through
		int lagoon = shortestpath.WorldPointUtil.packWorldPoint(3277, 3135, 0);
		CollisionMap map = new CollisionMap(TestWorldData.map());
		SailingWaters waters = SailingWaters.of(map);
		SailingMooring sarim = mooring("Port Sarim");
		for (SailingVessel.Kind kind : new SailingVessel.Kind[] {SailingVessel.Kind.RAFT, SailingVessel.Kind.SLOOP})
		{
			SailingVessel vessel = SailingVessel.of(kind, BoatSpeed.ESTIMATED).withAreas(SeaArea.ALL);
			assertEquals(kind.toString(), kind == SailingVessel.Kind.RAFT, waters.connects(vessel,
				waters.castOff(sarim, vessel), Tiles.within(lagoon, 3)));
		}
	}

	@Test
	public void aKindHasAnEstimateOnlyWhereItsGraphJoins()
	{
		// The raft has no launch at moorings in water it can't sail: it has no estimate from or to them
		CollisionMap map = new CollisionMap(TestWorldData.map());
		SailingWaters waters = SailingWaters.of(map);
		Map<SailingVessel.Kind, SeaGraph> graphs = SeaGraph.all(map);
		List<SailingMooring> moorings = SailingMoorings.all();
		int pairs = 0, joined = 0;
		for (SailingVessel.Kind kind : SailingVessel.Kind.values())
		{
			SailingFleet fleet = SailingFleet.everywhere(List.of(SailingVessel.of(kind, 384).fullyFitted()));
			Map<Long, Integer> edges = edges(SailingEstimates.edges(fleet, moorings, waters, graphs));
			for (SailingMooring from : moorings)
			{
				for (SailingMooring to : moorings)
				{
					if (from == to) continue;
					pairs++;
					boolean joins = graphs.get(kind).joins(from, to);
					assertEquals(kind + " " + from.name() + " to " + to.name(), joins,
						edges.containsKey(key(from.landTile(), to.landTile())));
					if (joins) joined++;
				}
			}
		}
		assertTrue(joined > 1000);
		assertTrue(joined < pairs);
	}

	@Test
	public void anEstimateBoundsTheBoatsOwnLeg()
	{
		// The estimate sees across the sea by these: each must be no more than the boat's leg on its kind's graph,
		// which the search sails, with or without its parts
		CollisionMap map = new CollisionMap(TestWorldData.map());
		SailingWaters waters = SailingWaters.of(map);
		Map<SailingVessel.Kind, SeaGraph> graphs = SeaGraph.all(map);
		List<SailingMooring> moorings = SailingMoorings.all();
		int sailed = 0;
		for (SailingVessel.Kind kind : SailingVessel.Kind.values())
		{
			for (SailingVessel vessel : List.of(SailingVessel.of(kind, 192), SailingVessel.of(kind, 384).fullyFitted()))
			{
				Map<Long, Integer> edges = edges(SailingEstimates.edges(SailingFleet.everywhere(List.of(vessel)), moorings,
					waters, graphs));
				for (int i = 0; i < moorings.size(); i += 7)
				{
					for (int j = 3; j < moorings.size(); j += 11)
					{
						SailingMooring from = moorings.get(i), to = moorings.get(j);
						Integer estimate = edges.get(key(from.landTile(), to.landTile()));
						if (estimate == null) continue;
						double onGraph = SeaGraphTest.ticks(graphs.get(kind), vessel.moves(), from, to);
						if (onGraph == Double.MAX_VALUE) continue;
						sailed++;
						double ticks = SailingMooring.BOARD_TICKS + onGraph + SailingMooring.DISEMBARK_TICKS;
						assertTrue(vessel + " " + from.name() + " to " + to.name() + ": " + estimate + " <= " + ticks,
							estimate <= ticks);
					}
				}
			}
		}
		assertTrue(sailed > 10);
	}

	@Test
	public void aBoatABankVisitAwayIsOnlyInTheBankedLayer()
	{
		CollisionMap map = new CollisionMap(TestWorldData.map());
		SailingMooring sarim = mooring("Port Sarim");
		SailingVessel vessel = SailingVessel.of(SailingVessel.Kind.SKIFF, 256);
		// Moored at the Pandemonium, brought to Port Sarim for 500 coins with 100 carried
		SailingFleet fleet = new SailingFleet(List.of(SailingFleet.Boat.at(vessel, mooring("The Pandemonium").dock(), 500)),
			null, 100);
		EstimateEdges edges = SailingEstimates.edges(fleet, SailingMoorings.all(), SailingWaters.of(map), SeaGraph.all(map));
		assertTrue(edges.count(BankVisitState.BANKED) > 0);
		for (int i = 0; i < edges.count(BankVisitState.CARRIED); i++)
			assertTrue(edges.origin(BankVisitState.CARRIED, i) != sarim.landTile());
	}

	@Test
	public void aBoatCastsOffWhereItCouldDock()
	{
		CollisionMap map = new CollisionMap(TestWorldData.map());
		SailingWaters waters = SailingWaters.of(map);
		for (SailingMooring mooring : SailingMoorings.all())
		{
			for (SailingVessel.Kind kind : SailingVessel.Kind.values())
			{
				SailingVessel vessel = SailingVessel.of(kind, BoatSpeed.ESTIMATED).withAreas(SeaArea.ALL);
				if (!mooring.fits(vessel)) continue;
				BoatHull hull = vessel.hull();
				for (int tile : waters.castOff(mooring, vessel))
				{
					// Never the berth: a boat state, in docking range, its hull clear facing some heading
					assertTrue(mooring.inDockingRange(tile));
					assertTrue(waters.allows(vessel, tile));
					assertTrue(hull.fits(map, shortestpath.WorldPointUtil.unpackWorldX(tile),
						shortestpath.WorldPointUtil.unpackWorldY(tile), shortestpath.WorldPointUtil.unpackWorldPlane(tile)));
				}
			}
		}
	}

	@Test
	public void theSeaLayerIsNotKeptAtRuntime()
	{
		SplitFlagMap runtime = SplitFlagMap.fromResources();
		assertTrue(runtime.layers("sea").isEmpty());
		assertFalse(runtime.layers(SailingWaters.LAYER).isEmpty());
	}

	static SailingMooring mooring(String name)
	{
		return SailingMoorings.all().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
	}

	private static long key(int origin, int destination)
	{
		return ((long) origin << 32) | (destination & 0xFFFFFFFFL);
	}

	// The banked layer's edges, which every boat the fleet can board is in, by origin and destination
	private static Map<Long, Integer> edges(EstimateEdges edges)
	{
		Map<Long, Integer> found = new HashMap<>();
		for (int i = 0; i < edges.count(BankVisitState.BANKED); i++)
			found.put(key(edges.origin(BankVisitState.BANKED, i), edges.destination(BankVisitState.BANKED, i)),
				edges.cost(BankVisitState.BANKED, i));
		return found;
	}
}
