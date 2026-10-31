package shortestpath.pathfinder;

import shortestpath.PrimitiveIntList;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

/**
 * The neighbour-generation half of the legacy pathfinder, lifted out of
 * {@link CollisionMap} so the vendored {@link Pathfinder} oracle keeps emitting
 * identical neighbours while the production collision map only carries the
 * walking surface the remaining engine needs.
 */
public class LegacyNeighborGen
{
	// Enum.values() makes copies every time which hurts performance in the hotpath
	private static final OrdinalDirection[] ORDINAL_VALUES = OrdinalDirection.values();

	private final CollisionMap map;
	// This is only safe if pathfinding is single-threaded. Holds the ids of the neighbour nodes
	// appended to the NodeGraph during the most recent getNeighbors call.
	private final PrimitiveIntList neighbors = new PrimitiveIntList(16);
	private final boolean[] traversable = new boolean[8];

	public LegacyNeighborGen(CollisionMap map)
	{
		this.map = map;
	}

	private static int packedPointFromOrdinal(int startPacked, OrdinalDirection direction)
	{
		final int x = WorldPointUtil.unpackWorldX(startPacked);
		final int y = WorldPointUtil.unpackWorldY(startPacked);
		final int plane = WorldPointUtil.unpackWorldPlane(startPacked);
		return WorldPointUtil.packWorldPoint(x + direction.x, y + direction.y, plane);
	}

	/**
	 * {@code bankPathEnabled} is whether this search may visit a bank: the setting, or a search
	 * whose target needs items from the bank.
	 */
	public PrimitiveIntList getNeighbors(int node, VisitedTiles visited, PathfinderConfig config, int wildernessLevel,
		boolean targetInWilderness, NodeGraph graph, boolean bankPathEnabled)
	{
		if (graph.isTile(node))
		{
			return getTileNeighbors(node, visited, config, wildernessLevel, graph, bankPathEnabled);
		}
		else
		{
			return getAbstractNodeNeighbors(node, visited, config, targetInWilderness, graph);
		}
	}

	// Get neighbours for a walkable tile:
	//      * Neighbouring tiles we can walk to
	//      * A transition into banked state, if the current tile is a bank.
	//      * Transition into abstract global teleport nodes, if we haven't tried that yet.
	private PrimitiveIntList getTileNeighbors(int node, VisitedTiles visited, PathfinderConfig config, int wildernessLevel, NodeGraph graph,
		boolean bankPathEnabled)
	{
		final int packedPosition = graph.packedPosition(node);
		final int x = WorldPointUtil.unpackWorldX(packedPosition);
		final int y = WorldPointUtil.unpackWorldY(packedPosition);
		final int z = WorldPointUtil.unpackWorldPlane(packedPosition);

		neighbors.clear();

		// The banked state is only entered through an explicit, costed transition: standing on a
		// bank-accessible tile emits a bank-visit edge carrying the configured penalty, so the
		// charge sits on the decision to bank rather than on the edges leaving the bank tile.
		// The edge is flagged as a transport so Pathfinder queues it on the cost-ordered pending
		// heap — the FIFO tile boundary queue is only ordered for unit-cost walking edges.
		BankVisitState pathBankVisited = graph.bankVisited(node);
		if (pathBankVisited != BankVisitState.BANKED && bankPathEnabled && config.bankAccessible(packedPosition)
			&& !visited.get(packedPosition, BankVisitState.BANKED))
		{
			neighbors.add(graph.createBankVisit(packedPosition, node, config.getBankVisitCost()));
		}

		// Firstly check if there are any transports or teleports which are applicable from the current tile.
		Transport[] transports = config.getTransportsPacked(pathBankVisited).getOrDefault(packedPosition, TransportAvailability.EMPTY_TRANSPORTS);
		// If this tile was itself reached via a delayed-visit teleport (e.g. QUETZAL_WHISTLE), propagate its
		// differential cost to any competing delayed-visit transports emitted from here. This prevents the
		// pathfinder from choosing a chain (e.g. whistle → landing site A → fly to B) over a direct teleport
		// to B, because the chain inherits the teleport's penalty and is therefore always more expensive.
		int inheritedDifferential = (graph.isTransport(node) && graph.isDelayedVisit(node))
			? graph.differentialCost(node)
			: 0;
		for (Transport transport : transports)
		{
			boolean delayedVisit = transport.getType().sharesDestinationsWith() != null;
			// Do not consider a transport if its target tile is already settled —
			// dequeued from pending via a cheaper-or-equal route.
			// For transports that share destinations with a teleport, skip this check
			// so both can compete in the priority queue (delayed visit).
			if (!delayedVisit && visited.get(transport.getDestination(), pathBankVisited))
			{
				continue;
			}
			// Inherit the parent teleport's differential as a real cost on chained shared-destination transports,
			// so that chaining (e.g. fly to landing site A then use station to B) is always more expensive than
			// a direct teleport to B.
			int chainPenalty = (delayedVisit && inheritedDifferential > 0) ? inheritedDifferential : 0;
			// NB: Do not need to check for wilderness level for transports, since transports have specific origin tile.
			neighbors.add(graph.createTransport(
				transport.getDestination(),
				node,
				transport.getDuration(),
				config.getAdditionalTransportCost(transport) + chainPenalty,
				pathBankVisited,
				delayedVisit,
				delayedVisit ? config.getDifferentialCost(transport) : 0,
				transport));
		}

		// Global teleports are only considered from an abstract node, so each
		// wilderness/bank state expands them once.
		AbstractNodeKind abstractKind = AbstractNodeKind.fromWildernessLevel(wildernessLevel);
		if (!visited.getAbstract(abstractKind, pathBankVisited))
		{
			neighbors.add(graph.createAbstract(abstractKind, node, pathBankVisited));
		}

		// Then add tiles which we can walk to, which go into the FIFO boundary queue.
		if (map.isBlocked(x, y, z))
		{
			boolean westBlocked = map.isBlocked(x - 1, y, z);
			boolean eastBlocked = map.isBlocked(x + 1, y, z);
			boolean southBlocked = map.isBlocked(x, y - 1, z);
			boolean northBlocked = map.isBlocked(x, y + 1, z);
			boolean southWestBlocked = map.isBlocked(x - 1, y - 1, z);
			boolean southEastBlocked = map.isBlocked(x + 1, y - 1, z);
			boolean northWestBlocked = map.isBlocked(x - 1, y + 1, z);
			boolean northEastBlocked = map.isBlocked(x + 1, y + 1, z);
			traversable[0] = !westBlocked;
			traversable[1] = !eastBlocked;
			traversable[2] = !southBlocked;
			traversable[3] = !northBlocked;
			traversable[4] = !southWestBlocked && !westBlocked && !southBlocked;
			traversable[5] = !southEastBlocked && !eastBlocked && !southBlocked;
			traversable[6] = !northWestBlocked && !westBlocked && !northBlocked;
			traversable[7] = !northEastBlocked && !eastBlocked && !northBlocked;
		}
		else
		{
			traversable[0] = map.w(x, y, z);
			traversable[1] = map.e(x, y, z);
			traversable[2] = map.s(x, y, z);
			traversable[3] = map.n(x, y, z);
			// Diagonals: same logic as CollisionMap's private sw/se/nw/ne methods
			traversable[4] = map.s(x, y, z) && map.w(x, y - 1, z) && map.w(x, y, z) && map.s(x - 1, y, z);
			traversable[5] = map.s(x, y, z) && map.e(x, y - 1, z) && map.e(x, y, z) && map.s(x + 1, y, z);
			traversable[6] = map.n(x, y, z) && map.w(x, y + 1, z) && map.w(x, y, z) && map.n(x - 1, y, z);
			traversable[7] = map.n(x, y, z) && map.e(x, y + 1, z) && map.e(x, y, z) && map.n(x + 1, y, z);
		}

		for (int i = 0; i < traversable.length; i++)
		{
			OrdinalDirection d = ORDINAL_VALUES[i];
			int neighborPacked = packedPointFromOrdinal(packedPosition, d);
			if (visited.get(neighborPacked, pathBankVisited))
			{
				continue;
			}

			if (traversable[i])
			{
				neighbors.add(graph.createTile(neighborPacked, node, pathBankVisited));
			}
			else if (Math.abs(d.x + d.y) == 1 && map.isBlocked(x + d.x, y + d.y, z))
			{
				// The transport starts from a blocked adjacent tile, e.g. fairy ring
				// Only checks non-teleport transports (includes portals and levers, but not
				// items and spells)
				Transport[] neighborTransports = config.getTransportsPacked(pathBankVisited).getOrDefault(neighborPacked,
					TransportAvailability.EMPTY_TRANSPORTS);
				for (Transport transport : neighborTransports)
				{
					if (transport.getOrigin() == Transport.UNDEFINED_ORIGIN
						|| !(transport.isUsableAtWildernessLevel(wildernessLevel))
						|| visited.get(transport.getOrigin(), pathBankVisited))
					{
						continue;
					}
					neighbors.add(graph.createTile(transport.getOrigin(), node, pathBankVisited));
				}
			}
		}

		return neighbors;
	}

	// The only abstract nodes are currently for global teleports
	private PrimitiveIntList getAbstractNodeNeighbors(int node, VisitedTiles visited, PathfinderConfig config,
		boolean targetInWilderness, NodeGraph graph)
	{
		neighbors.clear();
		int sourceTile = graph.getClosestTilePosition(node);
		BankVisitState bankVisited = graph.bankVisited(node);
		int maxWildernessLevel = graph.abstractKind(node).maxWildernessLevel();
		for (Transport transport : config.getUsableTeleports(bankVisited))
		{
			boolean delayedVisit = transport.getType().sharesDestinationsWith() != null;
			if (!delayedVisit && visited.get(transport.getDestination(), bankVisited))
			{
				continue;
			}
			if (!transport.isUsableAtWildernessLevel(maxWildernessLevel))
			{
				continue;
			}
			if (config.avoidWilderness(sourceTile, transport.getDestination(), targetInWilderness))
			{
				continue;
			}
			// The differential cost is only used for priority-queue ordering (compareCost), not
			// propagated as real cost, so applying it unconditionally is safe: a nearby partner
			// station can still win the dequeue race, and a far-away whistle still resolves as the
			// cheapest path because no competitor has a lower real cost to the same destination.
			int differentialCost = delayedVisit ? config.getDifferentialCost(transport) : 0;
			neighbors.add(graph.createTransport(
				transport.getDestination(),
				node,
				transport.getDuration(),
				config.getAdditionalTransportCost(transport),
				bankVisited,
				delayedVisit,
				differentialCost,
				transport));
		}
		return neighbors;
	}
}
