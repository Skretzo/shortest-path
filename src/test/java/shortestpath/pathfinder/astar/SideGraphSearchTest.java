package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Traversal;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/** How the search takes a side graph, on a corridor of tiles 10 to 40 whose walk costs a tick a tile. */
public class SideGraphSearchTest
{
	private static final int TARGET = tile(40), AT_SEA = tile(100);

	/** A side graph from tables: nodes at tiles 100 on, entered and left at given tiles, with given edges. */
	private static final class Scripted implements SideGraph
	{
		final int size;
		int start = -1;
		// tile -> {node, cost}
		final Map<Integer, List<int[]>> entries = new HashMap<>();
		// node -> {kind (0 node, 1 tile, 2 end), to, cost}
		final Map<Integer, List<int[]>> edges = new HashMap<>();
		final List<Integer> expanded = new ArrayList<>();
		boolean bankedOnly;

		Scripted(int size)
		{
			this.size = size;
		}

		Scripted entry(int tile, int node, int cost)
		{
			entries.computeIfAbsent(tile, k -> new ArrayList<>()).add(new int[] {node, cost});
			return this;
		}

		Scripted edge(int from, int kind, int to, int cost)
		{
			edges.computeIfAbsent(from, k -> new ArrayList<>()).add(new int[] {kind, to, cost});
			return this;
		}

		@Override
		public int size()
		{
			return size;
		}

		@Override
		public int startNode()
		{
			return start;
		}

		@Override
		public boolean entersAt(int tile)
		{
			return entries.containsKey(tile);
		}

		@Override
		public void enter(int tile, BankVisitState banked, Edges out)
		{
			if (bankedOnly && banked != BankVisitState.BANKED) return;
			for (int[] entry : entries.get(tile)) out.toNode(entry[0], entry[1], null);
		}

		@Override
		public void expand(int node, BankVisitState banked, Edges out)
		{
			expanded.add(node);
			for (int[] edge : edges.getOrDefault(node, List.of()))
			{
				if (edge[0] == 0) out.toNode(edge[1], edge[2], null);
				else if (edge[0] == 1) out.toTile(edge[1], edge[2], null);
				else out.toEnd(edge[1], edge[2]);
			}
		}

		@Override
		public int estimate(int node, BankVisitState banked, LandEstimate land)
		{
			return 0;
		}

		@Override
		public Traversal traversal()
		{
			return Traversal.SAILING;
		}

		@Override
		public int tile(int node)
		{
			return SideGraphSearchTest.tile(100 + node);
		}

	}

	private static AStarForwardSearch.Result search(int start, SideGraph side, Transport... transports) throws Exception
	{
		return search(start, side, EstimateEdges.NONE, transports);
	}

	private static AStarForwardSearch.Result search(int start, SideGraph side, EstimateEdges estimate,
		Transport... transports) throws Exception
	{
		Set<Integer> walkable = new java.util.HashSet<>();
		List<int[]> steps = new ArrayList<>();
		for (int x = 10; x <= 40; x++)
		{
			walkable.add(tile(x));
			if (x > 10) steps.add(new int[] {tile(x - 1), tile(x)});
		}
		SyntheticCollisionMap collision = new SyntheticCollisionMap(walkable,
			SyntheticCollisionMap.symmetricAdjacency(steps.toArray(new int[0][])), 1);
		Map<Integer, Set<Transport>> byOrigin = new HashMap<>();
		for (Transport transport : transports) byOrigin.put(transport.getOrigin(), Set.of(transport));
		RoutingStatic stat = RoutingStaticBuilder.build(collision, byOrigin, Set.of(tile(15), tile(35)), Set.of(),
			new int[0], tile(10)).routingStatic;
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(transports),
			TransportAvailabilityFixture.of(transports), false, Set.of(), 0, true, ignored -> 0)
			.withEstimateEdges(estimate);
		return PreparedTarget.prepare(new SiteGraph(stat, account), collision, TARGET).search(start, () -> false, 1,
			SearchRestrictions.none(), AStarForwardSearch.NO_PROGRESS, (tile, banked) -> true, side);
	}

	@Test
	public void aWayAcrossTheSideGraphCheaperThanWalkingIsTaken() throws Exception
	{
		Scripted sea = new Scripted(2).entry(tile(15), 0, 2).edge(0, 0, 1, 3).edge(1, 1, tile(35), 2);

		AStarForwardSearch.Result result = search(tile(10), sea);

		assertTrue(result.reached());
		assertEquals("5 walked, 2 + 3 + 2 at sea, 5 walked", 17, result.cost());
		List<PathStep> path = result.route().steps();
		List<Integer> tiles = new ArrayList<>();
		for (PathStep step : path) tiles.add(step.getPackedPosition());
		int boarded = tiles.indexOf(tile(15));
		assertEquals(tile(100), (int) tiles.get(boarded + 1));
		assertEquals(tile(101), (int) tiles.get(boarded + 2));
		assertEquals(tile(35), (int) tiles.get(boarded + 3));
		assertEquals(Traversal.SAILING, path.get(boarded + 1).getTraversal());
		assertEquals(Traversal.SAILING, path.get(boarded + 2).getTraversal());
		assertEquals(12, result.route().cost(boarded + 3));
		assertEquals(WorldPointUtil.UNDEFINED, result.sideEnd());
	}

	@Test
	public void aWayAcrossDearerThanWalkingIsNot() throws Exception
	{
		Scripted sea = new Scripted(1).entry(tile(15), 0, 2).edge(0, 1, tile(35), 40);

		AStarForwardSearch.Result result = search(tile(10), sea);

		assertEquals(30, result.cost());
		assertTrue(result.route().steps().stream().noneMatch(step -> step.getTraversal() == Traversal.SAILING));
	}

	@Test
	public void aRouteCanEndOnTheSideGraph() throws Exception
	{
		Scripted sea = new Scripted(1).entry(tile(15), 0, 2).edge(0, 2, AT_SEA, 1);

		AStarForwardSearch.Result result = search(tile(10), sea);

		assertTrue(result.reached());
		assertEquals(8, result.cost());
		assertEquals(AT_SEA, result.sideEnd());
		List<PathStep> path = result.route().steps();
		assertEquals(AT_SEA, path.get(path.size() - 1).getPackedPosition());
		assertEquals(8, result.route().cost(path.size() - 1));
	}

	@Test
	public void anEndDearerThanWalkingToTheTargetLosesToIt() throws Exception
	{
		Scripted sea = new Scripted(1).entry(tile(15), 0, 2).edge(0, 2, AT_SEA, 60);

		AStarForwardSearch.Result result = search(tile(10), sea);

		assertEquals(30, result.cost());
		assertEquals(WorldPointUtil.UNDEFINED, result.sideEnd());
	}

	@Test
	public void aRouteCanStartOnTheSideGraph() throws Exception
	{
		Scripted sea = new Scripted(2).edge(0, 1, tile(30), 50).edge(0, 0, 1, 1).edge(1, 1, tile(20), 4);
		sea.start = 0;

		AStarForwardSearch.Result result = search(tile(100), sea);

		assertTrue(result.reached());
		assertEquals("5 at sea, then 20 on foot", 25, result.cost());
		List<PathStep> path = result.route().steps();
		assertEquals(tile(100), path.get(0).getPackedPosition());
		assertEquals(tile(101), path.get(1).getPackedPosition());
		assertEquals(tile(20), path.get(2).getPackedPosition());
		assertEquals(TARGET, path.get(path.size() - 1).getPackedPosition());
	}

	@Test
	public void aRouteStartingOnTheSideGraphTeleportsOnceAshore() throws Exception
	{
		Transport teleport = new Transport.TransportBuilder().destination(tile(39)).type(TransportType.TELEPORTATION_ITEM)
			.duration(2).maxWildernessLevel(0).build();
		Scripted sea = new Scripted(1).edge(0, 1, tile(20), 4);
		sea.start = 0;

		AStarForwardSearch.Result result = search(tile(100), sea, teleport);

		assertEquals("4 ashore, 2 teleporting, 1 walked", 7, result.cost());
		assertTrue(result.route().steps().stream().anyMatch(step -> step.getTransport() == teleport));
	}

	@Test
	public void aRouteStartingOnTheSideGraphWithNoWayOffIsUnreachable() throws Exception
	{
		Scripted sea = new Scripted(1);
		sea.start = 0;

		assertFalse(search(tile(100), sea).reached());
	}

	@Test
	public void entriesFollowTheBankLayer() throws Exception
	{
		Scripted sea = new Scripted(1).entry(tile(15), 0, 2).edge(0, 2, AT_SEA, 1);
		sea.bankedOnly = true;

		assertEquals("no bank on the corridor, so never boarded", 30, search(tile(10), sea).cost());
	}

	@Test
	public void anEstimateEdgeIsNeverTaken() throws Exception
	{
		EstimateEdges across = new EstimateEdges.Builder().add(BankVisitState.CARRIED, tile(15), tile(35), 1).build();
		Scripted sea = new Scripted(1).entry(tile(15), 0, 2).edge(0, 1, tile(35), 10);

		AStarForwardSearch.Result result = search(tile(10), sea, across);

		assertEquals("5 walked, 12 by sea, 5 walked", 22, result.cost());
		List<PathStep> steps = result.route().steps();
		for (int i = 1; i < steps.size(); i++)
		{
			assertFalse("no step from 15 straight to 35", steps.get(i - 1).getPackedPosition() == tile(15)
				&& steps.get(i).getPackedPosition() == tile(35));
		}
		assertEquals("without the side graph, walked", 30, search(tile(10), SideGraph.NONE, across).cost());
	}

	@Test
	public void withoutASideGraphATransportIsTaken() throws Exception
	{
		Transport hop = new Transport.TransportBuilder().origin(tile(15)).destination(tile(35))
			.type(TransportType.TRANSPORT).duration(1).build();

		assertEquals(11, search(tile(10), SideGraph.NONE, hop).cost());
	}

	private static int tile(int x)
	{
		return WorldPointUtil.packWorldPoint(x, 10, 0);
	}
}
