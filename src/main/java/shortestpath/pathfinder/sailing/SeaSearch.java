package shortestpath.pathfinder.sailing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import net.runelite.api.gameval.ItemID;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Traversal;
import shortestpath.pathfinder.astar.CostArithmetic;
import shortestpath.pathfinder.astar.SideGraph;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.sailing.SeaArea;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * The sea for one query, as the search's {@link SideGraph}: each boat the player can sail on its kind's
 * {@link SeaGraph}. A state at sea is a boat at a node: its launch, a corner, or where the boat aboard is. The route
 * boards a boat at a mooring's land tile, onto the mooring's launch; sails runs between nodes, each timed by the boat's
 * open-water bound and the ticks the graph found sailing it took past that; docks at a mooring's launch, onto its land
 * tile; or ends at a target at sea it reaches.
 * <p>
 * Each different boat (kind, speed and parts) has its own copy of its kind's nodes; boats alike in all three share one,
 * boarding whichever is cheapest at each mooring. A run through a restricted area the boat has no part for isn't
 * taken.
 */
public final class SeaSearch implements SideGraph
{
	// The widest corridor the drawing tries before letting the boat turn any way into a move
	private static final int WIDEST_CORRIDOR = 4 * SeaGraph.CORRIDOR;
	private final CollisionMap map;
	private final SailingWaters waters;
	private final SailingFleet fleet;
	private final SeaGate gate;
	// The moorings the account may use, by id
	private final SailingMooring[] usable;
	private final List<Hull> hulls = new ArrayList<>();
	private final int size;
	private int startNode = -1;
	// Where the boat aboard sets out from, if it isn't at a node
	private double startX, startY;
	// The mooring the boat aboard is moored at, if it is, by id; else -1
	private int startMooring = -1;
	private Hull startHull;
	// The boardings at each land tile: asked of every tile the search takes, so a primitive map
	private final PrimitiveIntHashMap<List<Boarding>> boardings = new PrimitiveIntHashMap<>(64);
	private final List<Boarding> allBoardings = new ArrayList<>();
	// The targets at sea some boat can reach
	private final int[] seaTargets;
	private final boolean[] reachable;
	private long setupNanos;

	/** One boat, or boats alike, on its kind's graph: nodes {@link #offset} on, and one more for a start aboard. */
	private final class Hull
	{
		final SailingVessel vessel;
		final SeaGraph graph;
		final SailingMoves moves;
		final int offset;
		// The areas the boat may not enter, as runs carry them
		final int barred;
		// The boats that are this hull, ashore
		final List<SailingFleet.Boat> boats = new ArrayList<>();
		// The links from the start, if the boat aboard is this hull and not at a node
		final List<double[]> startRuns = new ArrayList<>(), startEnds = new ArrayList<>();
		// The links to the targets from each node that sees one: {target index, ticks}
		final Map<Integer, List<int[]>> ends = new HashMap<>();
		// Docking at each mooring, by id, made on first ask
		final Transport[] docking;

		Hull(SailingVessel vessel, SeaGraph graph, int offset)
		{
			this.vessel = vessel;
			this.graph = graph;
			this.moves = vessel.moves();
			this.offset = offset;
			int fits = 0;
			for (SeaArea area : SeaArea.values()) if (vessel.kind().fits(area)) fits |= 1 << area.ordinal();
			this.barred = fits & ~SeaGraph.mask(vessel.areas());
			this.docking = new Transport[graph.moorings];
		}

		int size()
		{
			return graph.size() + 1;
		}

		int ticks(double dx, double dy)
		{
			return (int) Math.round(moves.openWaterTicks(dx, dy));
		}

		// The ticks of run k from node i: its open-water bound, and what sailing it took past that
		int runTicks(int i, int k)
		{
			int j = graph.runTargets[k];
			return ticks(graph.x[j] - graph.x[i], graph.y[j] - graph.y[i]) + graph.runExtra[k];
		}
	}

	/** Boarding a hull at a mooring's land tile. */
	private static final class Boarding
	{
		final Hull hull;
		final SailingMooring mooring;
		final int coins;
		final Transport transport;

		Boarding(Hull hull, SailingMooring mooring, int coins, Transport transport)
		{
			this.hull = hull;
			this.mooring = mooring;
			this.coins = coins;
			this.transport = transport;
		}
	}

	/**
	 * The sea for a query from {@code start} with {@code capability}, to {@code seaTargets}, the targets that are at sea;
	 * {@code gate} is the walking search's positional gates, asked of each run.
	 */
	public SeaSearch(CollisionMap map, PreparedSailingCapability capability, int start, int[] seaTargets,
		SeaGate gate)
	{
		long started = System.nanoTime();
		this.map = map;
		this.waters = SailingWaters.of(map);
		this.fleet = capability.fleet();
		this.gate = gate;
		this.seaTargets = seaTargets;
		this.reachable = new boolean[seaTargets.length];
		List<SailingMooring> all = SailingMoorings.all();
		usable = new SailingMooring[all.size()];
		for (SailingMooring mooring : capability.moorings()) usable[mooring.id()] = mooring;
		Map<SailingVessel, Hull> byVessel = new LinkedHashMap<>();
		int offset = 0;
		for (SailingFleet.Boat boat : fleet.boats())
		{
			SeaGraph graph = SeaGraph.of(map, boat.vessel().kind());
			if (graph == null) continue;
			Hull hull = byVessel.get(boat.vessel());
			if (hull == null)
			{
				hull = new Hull(boat.vessel(), graph, offset);
				offset += hull.size();
				byVessel.put(boat.vessel(), hull);
				hulls.add(hull);
			}
			hull.boats.add(boat);
		}
		SailingFleet.Aboard aboard = fleet.aboard();
		if (aboard != null && SeaGraph.of(map, aboard.vessel().kind()) != null)
		{
			startHull = new Hull(aboard.vessel(), SeaGraph.of(map, aboard.vessel().kind()), offset);
			offset += startHull.size();
			hulls.add(startHull);
			setStart(start);
		}
		size = offset;
		for (Hull hull : hulls) if (hull != startHull) addBoardings(hull);
		linkTargets();
		setupNanos = System.nanoTime() - started;
	}

	// Boarding each hull at each mooring a boat of it can be boarded at, cheapest boat first
	private void addBoardings(Hull hull)
	{
		for (SailingMooring mooring : usable)
		{
			if (mooring == null || !mooring.fits(hull.vessel) || hull.graph.launch[mooring.id()] < 0) continue;
			int launch = hull.graph.launch[mooring.id()];
			if (!waters.allows(hull.vessel, tileOf(hull.graph.x[launch], hull.graph.y[launch]))) continue;
			int coins = SailingFleet.UNAVAILABLE;
			for (SailingFleet.Boat boat : hull.boats)
			{
				int cost = boat.boardingCost(mooring);
				if (cost != SailingFleet.UNAVAILABLE && (coins == SailingFleet.UNAVAILABLE || cost < coins)) coins = cost;
			}
			if (coins == SailingFleet.UNAVAILABLE) continue;
			Transport boarding = new Transport.TransportBuilder().origin(mooring.landTile())
				.destination(tileOf(hull.graph.x[launch], hull.graph.y[launch])).type(TransportType.SAILING)
				.duration(SailingMooring.BOARD_TICKS)
				.displayInfo("Board " + hull.vessel.kind().name().toLowerCase(Locale.ROOT) + " at " + mooring.name())
				.build();
			Boarding entry = new Boarding(hull, mooring, coins, withRecovery(boarding, coins));
			List<Boarding> here = boardings.get(mooring.landTile());
			if (here == null)
			{
				here = new ArrayList<>();
				boardings.put(mooring.landTile(), here);
			}
			here.add(entry);
			allBoardings.add(entry);
		}
	}

	/**
	 * Where the boat aboard on {@code tile} sets out from: its tile, when the boat can be there; otherwise it is moored
	 * over the dock, and casts off from the nearest mooring in docking range, from its launch; nowhere if neither, and
	 * the route walks.
	 */
	private void setStart(int tile)
	{
		Hull hull = startHull;
		SeaGraph graph = hull.graph;
		startNode = hull.offset + graph.size();
		if (waters.possible(hull.vessel, tile))
		{
			startX = WorldPointUtil.unpackWorldX(tile);
			startY = WorldPointUtil.unpackWorldY(tile);
			linkStart(hull);
			return;
		}
		SailingMooring nearest = null;
		for (SailingMooring mooring : SailingMoorings.all())
		{
			if (!mooring.inDockingRange(tile) || !mooring.fits(hull.vessel) || graph.launch[mooring.id()] < 0) continue;
			if (nearest == null || Tiles.distance(tile, mooring.berthTile()) < Tiles.distance(tile, nearest.berthTile()))
				nearest = mooring;
		}
		if (nearest == null)
		{
			startNode = -1;
			return;
		}
		// From the mooring's launch, as from boarding there
		startMooring = nearest.id();
		startNode = hull.offset + graph.launch[nearest.id()];
	}

	// The runs from the start, which isn't a node
	private void linkStart(Hull hull)
	{
		SeaGraph graph = hull.graph;
		for (int i = 0; i < graph.size(); i++)
		{
			if (!graph.tangent(i, startX, startY) || !graph.sees(startX, startY, graph.x[i], graph.y[i])) continue;
			if (!allowed(hull, startX, startY, graph.x[i], graph.y[i])) continue;
			hull.startRuns.add(new double[] {i, hull.ticks(graph.x[i] - startX, graph.y[i] - startY)});
		}
	}

	// Links each target at sea to the nodes of each hull that see it, and to the start
	private void linkTargets()
	{
		Map<SeaGraph, Map<Integer, List<Integer>>> byGraph = new HashMap<>();
		for (int t = 0; t < seaTargets.length; t++)
		{
			int target = seaTargets[t];
			double tx = WorldPointUtil.unpackWorldX(target), ty = WorldPointUtil.unpackWorldY(target);
			for (Hull hull : hulls)
			{
				SeaGraph graph = hull.graph;
				// Which nodes see it is the kind's; whether the boat may take the run is the hull's
				List<Integer> seen = byGraph.computeIfAbsent(graph, g -> new HashMap<>()).computeIfAbsent(t, k ->
				{
					List<Integer> nodes = new ArrayList<>();
					for (int i = 0; i < graph.size(); i++)
					{
						if (graph.tangent(i, tx, ty) && graph.sees(graph.x[i], graph.y[i], tx, ty)) nodes.add(i);
					}
					return nodes;
				});
				for (int i : seen)
				{
					if (!allowed(hull, graph.x[i], graph.y[i], tx, ty)) continue;
					hull.ends.computeIfAbsent(i, k -> new ArrayList<>()).add(new int[] {t, hull.ticks(tx - graph.x[i], ty - graph.y[i])});
					reachable[t] = true;
				}
				if (hull == startHull && startMooring < 0 && startNode >= 0 && graph.sees(startX, startY, tx, ty)
					&& allowed(hull, startX, startY, tx, ty))
				{
					hull.startEnds.add(new double[] {t, hull.ticks(tx - startX, ty - startY)});
					reachable[t] = true;
				}
			}
		}
	}

	// Whether the hull's boat may sail a run between the points: its parts let it into the areas the run crosses
	private boolean allowed(Hull hull, double x0, double y0, double x1, double y1)
	{
		return hull.barred == 0 || (SeaGraph.areasAlong(waters, x0, y0, x1, y1) & hull.barred) == 0;
	}

	private static int tileOf(double x, double y)
	{
		return WorldPointUtil.packWorldPoint((int) Math.round(x), (int) Math.round(y), 0);
	}

	/** Whether any route can be at sea: some boat can be boarded, or the player is aboard one that can sail. */
	public boolean any()
	{
		return !allBoardings.isEmpty() || startNode >= 0;
	}

	/** Whether target {@code index} of the targets at sea is reached aboard: some boat can sail to it. */
	public boolean reaches(int index)
	{
		return reachable[index];
	}

	/** The land tiles boats are boarded at for the targets at sea, with a bound on boarding and sailing to one. */
	public Map<Integer, Integer> boardingBounds()
	{
		Map<Integer, Integer> least = new LinkedHashMap<>();
		boolean any = false;
		for (boolean r : reachable) any |= r;
		if (!any) return least;
		Map<Hull, double[]> tiles = new HashMap<>();
		for (Boarding boarding : allBoardings)
		{
			Hull hull = boarding.hull;
			double[] distances = tiles.computeIfAbsent(hull, this::distancesToTargets);
			double d = distances[hull.graph.launch[boarding.mooring.id()]];
			if (d == Double.MAX_VALUE) continue;
			int bound = SailingMooring.BOARD_TICKS + (int) Math.floor(d / hull.moves.topSpeed());
			least.merge(boarding.mooring.landTile(), bound, Math::min);
		}
		return least;
	}

	// The straight-line tiles from each of hull's nodes to the nearest target at sea, along runs; every part fitted
	private double[] distancesToTargets(Hull hull)
	{
		SeaGraph graph = hull.graph;
		double[] distance = new double[graph.size()];
		Arrays.fill(distance, Double.MAX_VALUE);
		PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		for (Map.Entry<Integer, List<int[]>> entry : hull.ends.entrySet())
		{
			int i = entry.getKey();
			for (int[] end : entry.getValue())
			{
				int target = seaTargets[end[0]];
				double d = Math.hypot(WorldPointUtil.unpackWorldX(target) - graph.x[i], WorldPointUtil.unpackWorldY(target) - graph.y[i]);
				if (d < distance[i])
				{
					distance[i] = d;
					open.add(new double[] {d, i});
				}
			}
		}
		while (!open.isEmpty())
		{
			double[] top = open.poll();
			int i = (int) top[1];
			if (top[0] > distance[i]) continue;
			for (int k = graph.runOffsets[i]; k < graph.runOffsets[i + 1]; k++)
			{
				int j = graph.runTargets[k];
				double next = distance[i] + Math.hypot(graph.x[j] - graph.x[i], graph.y[j] - graph.y[i]);
				if (next >= distance[j]) continue;
				distance[j] = next;
				open.add(new double[] {next, j});
			}
		}
		return distance;
	}

	/** Time spent setting the sea up for this query, in nanoseconds. */
	public long setupNanos()
	{
		return setupNanos;
	}

	@Override
	public int size()
	{
		return size;
	}

	@Override
	public int startNode()
	{
		return startNode;
	}

	@Override
	public boolean entersAt(int tile)
	{
		return boardings.get(tile) != null;
	}

	@Override
	public void enter(int tile, BankVisitState banked, Edges edges)
	{
		for (Boarding boarding : boardings.getOrDefault(tile, List.of()))
		{
			// The recovery coins are carried, or fetched from a bank
			if (banked != BankVisitState.BANKED && boarding.coins > fleet.carriedCoins()) continue;
			Hull hull = boarding.hull;
			int m = boarding.mooring.id();
			edges.toNode(hull.offset + hull.graph.launch[m], SailingMooring.BOARD_TICKS, boarding.transport);
		}
	}

	private Hull hullOf(int node)
	{
		for (Hull hull : hulls) if (node >= hull.offset && node < hull.offset + hull.size()) return hull;
		throw new IllegalArgumentException("no hull has node " + node);
	}

	@Override
	public void expand(int node, BankVisitState banked, Edges edges)
	{
		Hull hull = hullOf(node);
		SeaGraph graph = hull.graph;
		int i = node - hull.offset;
		if (i == graph.size())
		{
			for (double[] run : hull.startRuns) edges.toNode(hull.offset + (int) run[0], (int) run[1], null);
			for (double[] end : hull.startEnds) edges.toEnd(seaTargets[(int) end[0]], (int) end[1]);
			return;
		}
		double x = graph.x[i], y = graph.y[i];
		boolean gated = gate != SeaGate.NONE;
		for (int k = graph.runOffsets[i]; k < graph.runOffsets[i + 1]; k++)
		{
			if ((graph.runAreas[k] & hull.barred) != 0) continue;
			int j = graph.runTargets[k];
			if (gated && !gateAllows(x, y, graph.x[j], graph.y[j])) continue;
			edges.toNode(hull.offset + j, hull.runTicks(i, k), null);
		}
		// Docking, at a launch
		for (int k = graph.dockOffsets[i]; k < graph.dockOffsets[i + 1]; k++) dock(hull, graph.dockMooring[k], 0, edges);
		List<int[]> ends = hull.ends.get(i);
		if (ends != null) for (int[] end : ends) edges.toEnd(seaTargets[end[0]], end[1]);
	}

	// Docking at mooring, by id, after sailing ticks to within range
	private void dock(Hull hull, int mooringId, int ticks, Edges edges)
	{
		SailingMooring mooring = usable[mooringId];
		if (mooring == null || !mooring.fits(hull.vessel)) return;
		Transport docking = hull.docking[mooringId];
		if (docking == null)
		{
			docking = new Transport.TransportBuilder().origin(mooring.berthTile()).destination(mooring.landTile())
				.type(TransportType.SAILING).duration(SailingMooring.DISEMBARK_TICKS)
				.displayInfo("Dock at " + mooring.name()).build();
			hull.docking[mooringId] = docking;
		}
		edges.toTile(mooring.landTile(), ticks + SailingMooring.DISEMBARK_TICKS, docking);
	}

	private boolean gateAllows(double x0, double y0, double x1, double y1)
	{
		int from = tileOf(x0, y0), to = tileOf(x1, y1), middle = tileOf((x0 + x1) / 2, (y0 + y1) / 2);
		return gate.allowed(from, to) && gate.allowed(from, middle) && gate.allowed(middle, to);
	}

	@Override
	public int estimate(int node, BankVisitState banked, LandEstimate land)
	{
		Hull hull = hullOf(node);
		SeaGraph graph = hull.graph;
		int i = node - hull.offset;
		double x = i == graph.size() ? startX : graph.x[i], y = i == graph.size() ? startY : graph.y[i];
		double reach = SailingMooring.DOCKING_REACH * hull.moves.ticksPerTile();
		int least = CostArithmetic.INF;
		for (SailingMooring mooring : usable)
		{
			if (mooring == null || !mooring.fits(hull.vessel)) continue;
			int ashore = land.at(mooring.landTile(), banked);
			if (ashore == CostArithmetic.INF) continue;
			double sail = hull.moves.openWaterTicks(WorldPointUtil.unpackWorldX(mooring.berthTile()) - x,
				WorldPointUtil.unpackWorldY(mooring.berthTile()) - y) - reach;
			least = Math.min(least, CostArithmetic.add((int) Math.floor(Math.max(0, sail)) + SailingMooring.DISEMBARK_TICKS, ashore));
		}
		for (int t = 0; t < seaTargets.length; t++)
		{
			if (!reachable[t]) continue;
			double sail = hull.moves.openWaterTicks(WorldPointUtil.unpackWorldX(seaTargets[t]) - x,
				WorldPointUtil.unpackWorldY(seaTargets[t]) - y);
			least = Math.min(least, (int) Math.floor(sail));
		}
		return least;
	}

	@Override
	public int tile(int node)
	{
		Hull hull = hullOf(node);
		int i = node - hull.offset;
		return i == hull.graph.size() ? tileOf(startX, startY) : tileOf(hull.graph.x[i], hull.graph.y[i]);
	}

	@Override
	public Traversal traversal()
	{
		return Traversal.SAILING;
	}

	/** A route with its stretches at sea sailed on the tiles, and where each step of the route it was made from went. */
	public static final class Filled
	{
		public final List<PathStep> path;
		/** For each step of the route it was made from, its index in {@link #path}. */
		public final int[] indices;

		public Filled(List<PathStep> path, int[] indices)
		{
			this.path = path;
			this.indices = indices;
		}
	}

	/**
	 * {@code path}, a route this search found, with each stretch at sea sailed again on the tiles to draw it: the boat's
	 * tile at the end of every move, out through a mooring's approach, over the runs, and in through an approach
	 * ({@link #sail}). A stretch from a mooring to a mooring ends with the leg as one transport step, as the overlays and
	 * directions take it.
	 */
	public Filled fill(List<PathStep> path, BooleanSupplier cancelled)
	{
		List<PathStep> out = new ArrayList<>(path.size() + 64);
		int[] indices = new int[path.size()];
		int i = 0;
		while (i < path.size())
		{
			PathStep step = path.get(i);
			if (!step.isSailing())
			{
				indices[i++] = out.size();
				out.add(step);
				continue;
			}
			// A stretch at sea: its nodes, then the step ashore if it docks
			int first = i;
			while (i < path.size() && path.get(i).isSailing()) i++;
			PathStep ashore = i < path.size() ? path.get(i) : null;
			Boarding boarding = boardingBy(path.get(first).getTransport());
			SailingVessel vessel = boarding != null ? boarding.hull.vessel : startHull != null ? startHull.vessel : null;
			SailingMooring docked = ashore == null ? null : SailingMoorings.atLand(ashore.getPackedPosition());
			int[] nodes = new int[i - first];
			for (int k = first; k < i; k++) nodes[k - first] = path.get(k).getPackedPosition();
			BankVisitState banked = path.get(first).getBankVisitState();
			// From the boat aboard, as it is facing; boarded at a mooring's launch, or moored there, it may set out any way
			boolean aboard = boarding == null && first == 0 && fleet.aboard() != null && startMooring < 0;
			int heading = aboard ? fleet.aboard().heading() : -1;
			boolean toTarget = ashore == null && isSeaTarget(nodes[nodes.length - 1]);
			int[] tiles = vessel == null ? nodes : sail(vessel, nodes, heading,
				ashore == null ? -1 : docked == null ? -1 : docked.id(), toTarget, cancelled);
			for (int k = 0; k < tiles.length; k++)
			{
				Transport shown = k == 0 && boarding != null && ashore == null ? boarding.transport : null;
				out.add(PathStep.sailing(tiles[k], banked, shown));
			}
			for (int k = first; k < i; k++) indices[k] = out.size() - 1;
			if (ashore == null) continue;
			indices[i++] = out.size();
			if (boarding != null && docked != null)
			{
				// Moored to moored: the leg as one transport, for the overlays and the directions
				Transport leg = new Transport.TransportBuilder().origin(boarding.mooring.landTile()).destination(docked.landTile())
					.type(TransportType.SAILING).duration(SailingMooring.BOARD_TICKS + SailingMooring.DISEMBARK_TICKS)
					.displayInfo(docked.name()).build();
				out.add(new PathStep(ashore.getPackedPosition(), ashore.getBankVisitState(),
					withRecovery(leg, boarding.coins)));
			}
			else
			{
				// From the boat aboard: stepping off is the stretch's last step
				out.add(PathStep.sailing(ashore.getPackedPosition(), ashore.getBankVisitState()));
			}
		}
		return new Filled(out, indices);
	}

	private boolean isSeaTarget(int tile)
	{
		for (int target : seaTargets) if (target == tile) return true;
		return false;
	}

	private Boarding boardingBy(Transport transport)
	{
		if (transport == null) return null;
		for (Boarding boarding : allBoardings) if (boarding.transport == transport) return boarding;
		return null;
	}


	/**
	 * The boat's tiles sailing a stretch through {@code nodes}: from the first node (a mooring's launch, when boarded or
	 * moored there) holding {@code heading} (-1 any); along the graph's route, or that route pulled
	 * taut, within {@link SeaGraph#CORRIDOR} tiles of one, as the graph sailed its runs; to dock at mooring
	 * {@code dockAt} (by id) if not -1, else to the hull over the target at sea that is the last node if {@code toTarget}.
	 * The boat slows where it must ({@link SailingMoves#withSlow}); of the ways that take the fewest ticks, it takes one
	 * with the fewest turns. Where there is none, as where the graph's smoothed coast or dropped rocks leave no room to
	 * turn, it looks in a wider corridor, up to {@link #WIDEST_CORRIDOR} tiles, and then lets the boat turn any way into a
	 * move, as the graph sailed its runs: so the stretch keeps to water. Only if the drawing runs out of time, or a boat
	 * aboard is where nothing can sail, is it drawn through its nodes.
	 */
	int[] sail(SailingVessel vessel, int[] nodes, int heading, int dockAt, boolean toTarget, BooleanSupplier cancelled)
	{
		int[] from = {nodes[0]};
		int last = nodes[nodes.length - 1];
		SailingMooring docking = dockAt < 0 ? null : SailingMoorings.all().get(dockAt);
		double near = SeaGraph.near(vessel.kind());
		HeadingSearch.End end = docking != null ? (tile, h) -> docking.inDockingRange(tile)
			: toTarget ? (tile, h) -> vessel.hullCovers(last, tile, h) : (tile, h) -> Tiles.distance(tile, last) <= near;
		double reach = docking != null ? SailingMooring.DOCKING_REACH : toTarget ? 0 : near;
		int[] taut = taut(vessel, nodes);
		List<int[]> routes = taut.length == nodes.length ? List.of(nodes) : List.of(nodes, taut);
		SailingMoves moves = vessel.moves().withSlow();
		IntPredicate landing = waters.landing(vessel);
		// Steered, in a wider corridor each time; then unsteered, as the graph sailed each run when it was built
		for (int corridor = SeaGraph.CORRIDOR; corridor <= WIDEST_CORRIDOR; corridor *= 2)
		{
			HeadingSearch.Sailed sailed = HeadingSearch.along(map, moves, vessel.hull(), landing, gate, from, heading, routes,
				end, reach, corridor, true, cancelled);
			if (sailed != null) return sailed.tiles;
			if (cancelled.getAsBoolean()) return nodes;
		}
		HeadingSearch.Sailed sailed = HeadingSearch.along(map, moves, vessel.hull(), landing, gate, from, heading, routes,
			end, reach, WIDEST_CORRIDOR, false, cancelled);
		return sailed != null ? sailed.tiles : nodes;
	}

	/**
	 * {@code nodes}, the graph's route, pulled taut: from each node, straight on to the furthest node after it that it
	 * sees, with the boat's parts letting it into the water between. The open-water bound is a norm, so cutting a corner
	 * never takes longer; but it is a polygon's, so a route bending within one of its facets takes no longer either, and
	 * the search may have chosen one. Of routes that take as long, this is the straightest.
	 */
	private int[] taut(SailingVessel vessel, int[] nodes)
	{
		SeaGraph graph = SeaGraph.of(map, vessel.kind());
		if (graph == null || nodes.length < 3) return nodes;
		int barred = barred(vessel);
		List<Integer> kept = new ArrayList<>();
		kept.add(nodes[0]);
		int at = 0;
		while (at < nodes.length - 1)
		{
			int next = at + 1;
			double ax = WorldPointUtil.unpackWorldX(nodes[at]), ay = WorldPointUtil.unpackWorldY(nodes[at]);
			for (int j = nodes.length - 1; j > at + 1; j--)
			{
				double bx = WorldPointUtil.unpackWorldX(nodes[j]), by = WorldPointUtil.unpackWorldY(nodes[j]);
				if (clear(graph, barred, ax, ay, bx, by))
				{
					next = j;
					break;
				}
			}
			kept.add(nodes[next]);
			at = next;
		}
		return kept.stream().mapToInt(Integer::intValue).toArray();
	}

	// The areas of the boat's kind its parts don't let it into, as a mask
	private static int barred(SailingVessel vessel)
	{
		int fits = 0;
		for (SeaArea area : SeaArea.values()) if (vessel.kind().fits(area)) fits |= 1 << area.ordinal();
		return fits & ~SeaGraph.mask(vessel.areas());
	}

	// Whether a straight run crosses no smoothed edge and no area the boat is barred from
	private boolean clear(SeaGraph graph, int barred, double ax, double ay, double bx, double by)
	{
		return graph.sees(ax, ay, bx, by) && (barred == 0 || (SeaGraph.areasAlong(waters, ax, ay, bx, by) & barred) == 0);
	}

	/**
	 * {@code transport} for a boat that a shipwright must first bring to where it boards, for {@code cost} coins: it
	 * then needs the coins, and says so. Unchanged for a boat moored there ({@code cost} 0).
	 */
	private static Transport withRecovery(Transport transport, int cost)
	{
		if (cost <= 0) return transport;
		return transport.withItemRequirements(TransportItems.merge(
			transport.getItemRequirements(), new TransportItems(List.of(
				new ItemRequirement(new int[]{ItemID.COINS}, null, null, cost)))))
			.withDisplayInfo("Recover boat (" + cost + " gp), then " + transport.getDisplayInfo());
	}
}
