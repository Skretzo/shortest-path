package shortestpath.pathfinder;

import shortestpath.PrimitiveIntList;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

public class CollisionMap
{
	// Enum.values() makes copies every time which hurts performance in the hotpath
	private static final OrdinalDirection[] ORDINAL_VALUES = OrdinalDirection.values();

	private final SplitFlagMap collisionData;
	// This is only safe if pathfinding is single-threaded. Holds the ids of the neighbour nodes
	// appended to the NodeGraph during the most recent getNeighbors call.
	private final PrimitiveIntList neighbors = new PrimitiveIntList(16);
	private final boolean[] traversable = new boolean[8];

	public CollisionMap(SplitFlagMap collisionData)
	{
		this.collisionData = collisionData;
	}

	private static int packedPointFromOrdinal(int startPacked, OrdinalDirection direction)
	{
		final int x = WorldPointUtil.unpackWorldX(startPacked);
		final int y = WorldPointUtil.unpackWorldY(startPacked);
		final int plane = WorldPointUtil.unpackWorldPlane(startPacked);
		return WorldPointUtil.packWorldPoint(x + direction.x, y + direction.y, plane);
	}

	public byte getRegionPlaneCounts(int regionIndex)
	{
		return collisionData.getRegionPlaneCounts(regionIndex);
	}

	private boolean get(int x, int y, int z, int flag)
	{
		return collisionData.get(x, y, z, flag);
	}

	public boolean n(int x, int y, int z)
	{
		return get(x, y, z, 0);
	}

	public boolean s(int x, int y, int z)
	{
		return n(x, y - 1, z);
	}

	public boolean e(int x, int y, int z)
	{
		return get(x, y, z, 1);
	}

	public boolean w(int x, int y, int z)
	{
		return e(x - 1, y, z);
	}

	private boolean ne(int x, int y, int z)
	{
		return n(x, y, z) && e(x, y + 1, z) && e(x, y, z) && n(x + 1, y, z);
	}

	private boolean nw(int x, int y, int z)
	{
		return n(x, y, z) && w(x, y + 1, z) && w(x, y, z) && n(x - 1, y, z);
	}

	private boolean se(int x, int y, int z)
	{
		return s(x, y, z) && e(x, y - 1, z) && e(x, y, z) && s(x + 1, y, z);
	}

	private boolean sw(int x, int y, int z)
	{
		return s(x, y, z) && w(x, y - 1, z) && w(x, y, z) && s(x - 1, y, z);
	}

	public boolean isBlocked(int x, int y, int z)
	{
		return !n(x, y, z) && !s(x, y, z) && !e(x, y, z) && !w(x, y, z);
	}

	public PrimitiveIntList getNeighbors(int node, VisitedTiles visited, PathfinderConfig config, int wildernessLevel, boolean targetInWilderness, NodeGraph graph)
	{
		if (graph.isTile(node))
		{
			return getTileNeighbors(node, visited, config, wildernessLevel, graph);
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
	private PrimitiveIntList getTileNeighbors(int node, VisitedTiles visited, PathfinderConfig config, int wildernessLevel, NodeGraph graph)
	{
		final int packedPosition = graph.packedPosition(node);
		final int x = WorldPointUtil.unpackWorldX(packedPosition);
		final int y = WorldPointUtil.unpackWorldY(packedPosition);
		final int z = WorldPointUtil.unpackWorldPlane(packedPosition);

		neighbors.clear();

		// Either we have already visited a bank, if the current tile is a bank switch into the bankVisited state for the
		// rest of the path.
		boolean pathBankVisited = graph.bankVisited(node)
			|| (config.isBankPathEnabled() && config.bankAccessible(packedPosition));

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
			int bankSurcharge = bankPickupCost(config, pathBankVisited, packedPosition, transport);
			// Bank-surcharged transports are enqueued as delayed visits: their cost no longer matches
			// their creation order, so claiming the destination at enqueue (which for banked nodes also
			// claims the unbanked plane, see VisitedTiles#set) could block a cheaper plain-walking path.
			// Delayed visits are deduplicated at dequeue instead, in cost order.
			boolean delayedVisit = transport.getType().sharesDestinationsWith() != null || bankSurcharge > 0;
			// Do not consider a transport if we have already visited its target tile.
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
				config.getAdditionalTransportCost(transport) + chainPenalty + bankSurcharge,
				pathBankVisited,
				delayedVisit,
				delayedVisit ? config.getDifferentialCost(transport) : 0));
		}

		// Global teleports are only considered from an abstract node, so each
		// wilderness/bank state expands them once.
		AbstractNodeKind abstractKind = AbstractNodeKind.fromWildernessLevel(wildernessLevel);
		if (!visited.getAbstract(abstractKind, pathBankVisited))
		{
			neighbors.add(graph.createAbstract(abstractKind, node, pathBankVisited));
		}

		// Then add tiles which we can walk to, which go into the FIFO boundary queue.
		if (isBlocked(x, y, z))
		{
			boolean westBlocked = isBlocked(x - 1, y, z);
			boolean eastBlocked = isBlocked(x + 1, y, z);
			boolean southBlocked = isBlocked(x, y - 1, z);
			boolean northBlocked = isBlocked(x, y + 1, z);
			boolean southWestBlocked = isBlocked(x - 1, y - 1, z);
			boolean southEastBlocked = isBlocked(x + 1, y - 1, z);
			boolean northWestBlocked = isBlocked(x - 1, y + 1, z);
			boolean northEastBlocked = isBlocked(x + 1, y + 1, z);
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
			traversable[0] = w(x, y, z);
			traversable[1] = e(x, y, z);
			traversable[2] = s(x, y, z);
			traversable[3] = n(x, y, z);
			traversable[4] = sw(x, y, z);
			traversable[5] = se(x, y, z);
			traversable[6] = nw(x, y, z);
			traversable[7] = ne(x, y, z);
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
			else if (Math.abs(d.x + d.y) == 1 && isBlocked(x + d.x, y + d.y, z))
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
		boolean bankVisited = graph.bankVisited(node);
		int maxWildernessLevel = graph.abstractKind(node).maxWildernessLevel();
		for (Transport transport : config.getUsableTeleports(bankVisited))
		{
			int bankSurcharge = bankPickupCost(config, bankVisited, Transport.UNDEFINED_ORIGIN, transport);
			// Bank-surcharged teleports are delayed visits (see getTileNeighbors): claiming their
			// destination at enqueue would also claim the unbanked plane and could block a cheaper
			// plain-walking path; dedup happens at dequeue instead, in cost order.
			boolean delayedVisit = transport.getType().sharesDestinationsWith() != null || bankSurcharge > 0;
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
				config.getAdditionalTransportCost(transport) + bankSurcharge,
				bankVisited,
				delayedVisit,
				differentialCost));
		}
		return neighbors;
	}

	/**
	 * The bank pick-up surcharge for taking {@code transport} in the given bank state: the configured
	 * cost when the transport is only available because the path visited a bank (i.e. it is absent from
	 * the unbanked availability), otherwise zero. Charged on the transport edge itself — transport nodes
	 * are ordered by the cost min-heap, so the surcharge steers the search without disturbing the FIFO
	 * walking frontier, whose ordering assumes uniform step costs.
	 */
	private static int bankPickupCost(PathfinderConfig config, boolean bankVisited, int origin, Transport transport)
	{
		final int cost = config.getBankPickupCost();
		if (!bankVisited || cost == 0)
		{
			return 0;
		}
		return availableWithoutBank(config, origin, transport) ? 0 : cost;
	}

	/**
	 * Whether this transport is also in the unbanked availability (same identity): global teleports
	 * (undefined origin) are matched in the unbanked teleport list, fixed-origin transports in the
	 * unbanked per-tile transport map.
	 */
	private static boolean availableWithoutBank(PathfinderConfig config, int origin, Transport transport)
	{
		if (transport.getOrigin() == Transport.UNDEFINED_ORIGIN)
		{
			for (Transport candidate : config.getUsableTeleports(false))
			{
				if (candidate == transport)
				{
					return true;
				}
			}
			return false;
		}
		for (Transport candidate : config.getTransportsPacked(false).getOrDefault(origin, TransportAvailability.EMPTY_TRANSPORTS))
		{
			if (candidate == transport)
			{
				return true;
			}
		}
		return false;
	}
}
