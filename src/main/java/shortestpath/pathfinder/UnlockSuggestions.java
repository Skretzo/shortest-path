package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.Getter;
import shortestpath.PrimitiveIntList;
import shortestpath.pathfinder.astar.AStarForwardSearch;
import shortestpath.pathfinder.astar.PreparedRoutingAccount;
import shortestpath.pathfinder.astar.PreparedTarget;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.pathfinder.astar.SearchRestrictions;
import shortestpath.pathfinder.astar.SiteGraph;
import shortestpath.pathfinder.astar.TeleportCapability;
import shortestpath.requirement.Capability;
import shortestpath.requirement.Requirements;
import shortestpath.transport.Transport;

/**
 * What the player could unlock to reach a target the A* search found unreachable. On the graph of
 * routing components joined by every transport no setting or world restriction closes, and by the
 * crossings, one Dijkstra search from the player to the target weighs each locked transport by the
 * effort of what would open it, hardest {@link Capability.Effort} tier first and the journey's
 * duration last, so carrying 20 coins for the boat to Aldarin beats Twilight's Promise for the
 * quetzal however long the boat takes. The locked transports on the cheapest path name the unlocks,
 * each once, and the search is rerun with them granted: only if it then reaches the target are they
 * suggested.
 */
public final class UnlockSuggestions
{
	/** Up to one game tick, on top of the search that found the target unreachable. */
	static final long BUDGET_NANOS = 600_000_000L;
	/** A rerun stopped at the deadline takes a moment to return, so it is stopped this much early. */
	private static final long WIND_DOWN_NANOS = 10_000_000L;
	/**
	 * Stretches the budget. Tests that check what is found, rather than how fast, raise it so a busy
	 * machine can't change the answer.
	 */
	static int timeScale = 1;
	/** The heuristic weight of the rerun's second, greedier search. */
	private static final double QUICK_WEIGHT = 3.0;

	/** The unlocks that let the route reach the target, and the locked transports they open on the way. */
	@Getter
	public static final class Suggestion
	{
		private final Capability capability;
		/** The locked transports on the way, in the order the route takes them. */
		private final List<Transport> transports;
		/** The tier of its hardest part, for this player. */
		private final Capability.Effort effort;

		Suggestion(Capability capability, List<Transport> transports, Capability.Effort effort)
		{
			this.capability = capability;
			this.transports = List.copyOf(transports);
			this.effort = effort;
		}
	}

	/** Why the look ended as it did. */
	public enum Outcome
	{
		/** Not looked for. */
		NONE,
		/** No path at all, even with every unlock: a setting or world restriction closes the way. */
		CLOSED,
		/**
		 * The component graph joins the player to the target without unlocks, so what blocks the
		 * route is something it can't see, such as a position the route must avoid.
		 */
		ALREADY_CONNECTED,
		/** The rerun with the unlocks granted reached the target: they are suggested. */
		CONFIRMED,
		/** The rerun with the unlocks granted settled that the target is still unreachable. */
		REJECTED,
		/** The rerun didn't settle it within the budget. */
		INCONCLUSIVE,
		CANCELLED
	}

	/** What one look found, and how much it weighed up. */
	@Getter
	public static final class Found
	{
		static final Found NONE = new Found(Outcome.NONE, null, 0, 0);

		private final Outcome outcome;
		/** The unlocks the cheapest path names, confirmed or not; {@code null} when there are none. */
		private final Suggestion explanation;
		/** Time spent on the component graph, and on the rerun. */
		private final long graphNanos, rerunNanos;

		Found(Outcome outcome, Suggestion explanation, long graphNanos, long rerunNanos)
		{
			this.outcome = outcome;
			this.explanation = explanation;
			this.graphNanos = graphNanos;
			this.rerunNanos = rerunNanos;
		}

		/** The explanation once the rerun confirmed it, else nothing. */
		public List<Suggestion> getSuggestions()
		{
			return outcome == Outcome.CONFIRMED ? List.of(explanation) : List.of();
		}
	}

	private final Requirements requirements;
	private final Function<Requirements, PreparedRoutingAccount> accountWith;
	private final Supplier<PreparedRoutingAccount> obtainable;

	/**
	 * @param accountWith the routing account the player would have under other requirements
	 * @param obtainable the routing account with every transport no setting or world restriction closes
	 */
	UnlockSuggestions(Requirements requirements, Function<Requirements, PreparedRoutingAccount> accountWith,
		Supplier<PreparedRoutingAccount> obtainable)
	{
		this.requirements = requirements;
		this.accountWith = accountWith;
		this.obtainable = obtainable;
	}

	Found find(RoutingStatic stat, CollisionMap collision, PreparedRoutingAccount account, int start,
		GoalStates goals, SearchRestrictions restrictions, double heuristicWeight, BooleanSupplier cancelled)
	{
		long started = System.nanoTime();
		long deadline = started + (BUDGET_NANOS - WIND_DOWN_NANOS) * timeScale;
		BooleanSupplier stop = () -> cancelled.getAsBoolean() || System.nanoTime() > deadline;
		int[] targets = goals.tiles().stream().mapToInt(Integer::intValue).toArray();
		ComponentGraph graph = new ComponentGraph(stat, obtainable.get());
		long[] cost = new long[graph.edgeCount()];
		Capability[] opens = new Capability[graph.edgeCount()];
		weigh(graph, graph.edgesOf(account), cost, opens);
		int[] path = graph.cheapest(graph.components(collision, start), graph.components(collision, targets), cost);
		long graphNanos = System.nanoTime() - started;
		if (path == null)
		{
			return new Found(Outcome.CLOSED, null, graphNanos, 0);
		}

		// The unlocks of the locked transports on the path, each once, in the order the route takes them.
		Capability unlocks = null;
		List<Transport> locked = new ArrayList<>();
		for (int edge : path)
		{
			if (opens[edge] != null)
			{
				unlocks = unlocks == null ? opens[edge] : unlocks.union(opens[edge]);
				locked.add(graph.transport(edge));
			}
		}
		if (unlocks == null)
		{
			return new Found(Outcome.ALREADY_CONNECTED, null, graphNanos, 0);
		}
		Suggestion explanation = new Suggestion(unlocks, locked, requirements.effort(unlocks));
		long rerunStarted = System.nanoTime();
		AStarForwardSearch.Result route = rerun(unlocks, stat, collision, targets, start, heuristicWeight, stop,
			deadline, restrictions, goals);
		long rerunNanos = System.nanoTime() - rerunStarted;
		Outcome outcome = cancelled.getAsBoolean() ? Outcome.CANCELLED
			: route == null || route.cancelled() ? Outcome.INCONCLUSIVE
			: route.reached() ? Outcome.CONFIRMED : Outcome.REJECTED;
		return new Found(outcome, explanation, graphNanos, rerunNanos);
	}

	/**
	 * Fills each edge's {@code cost} and, for a locked transport, what it {@code opens} with: the
	 * cheapest of the capabilities that would open it on its own. A locked transport no capability
	 * opens, such as one behind a declared unlock, costs {@link ComponentGraph#CLOSED}.
	 */
	private void weigh(ComponentGraph graph, boolean[] usable, long[] cost, Capability[] opens)
	{
		Map<Transport, Capability> cheapest = new IdentityHashMap<>();
		Map<Transport, Long> lockCost = new IdentityHashMap<>();
		for (int edge = 0; edge < cost.length; edge++)
		{
			Transport transport = graph.transport(edge);
			long duration = graph.duration(edge);
			if (usable[edge])
			{
				cost[edge] = duration;
				continue;
			}
			if (!lockCost.containsKey(transport))
			{
				long best = ComponentGraph.CLOSED;
				for (Capability capability : requirements.capabilities(transport))
				{
					long weight = Cost.of(requirements.effortUnits(capability));
					if (weight < best)
					{
						best = weight;
						cheapest.put(transport, capability);
					}
				}
				lockCost.put(transport, best);
			}
			long weight = lockCost.get(transport);
			cost[edge] = weight == ComponentGraph.CLOSED ? weight : weight + duration;
			opens[edge] = cheapest.get(transport);
		}
	}

	/**
	 * An edge's cost packed into one long so that comparing two compares them lexicographically:
	 * the units of each effort tier, hardest first, above the duration in ticks. Repeated uses of a
	 * locked transport add up, keeping the costs additive for Dijkstra; units overflowing their field
	 * carry into the harder tier above, which only makes a pathological path dearer.
	 */
	static final class Cost
	{
		static final int TIER_BITS = 9;
		static final int DURATION_BITS = 63 - TIER_BITS * Capability.Effort.values().length;
		private static final int TIER_MAX = (1 << TIER_BITS) - 1;

		private Cost()
		{
		}

		/** The cost of opening with these {@link Requirements#effortUnits units}, without duration. */
		static long of(int[] units)
		{
			long packed = 0;
			for (int tier = units.length - 1; tier >= 0; tier--)
			{
				packed = (packed << TIER_BITS) | Math.min(units[tier], TIER_MAX);
			}
			return packed << DURATION_BITS;
		}
	}

	/**
	 * The search rerun with {@code capability} granted, or {@code null} when {@code stop} came first.
	 * It only has to show the target can be reached: the plain search gets half the time left, and
	 * one still going after that is usually a long way round, which a greedier search finds quickly.
	 * A result still {@link AStarForwardSearch.Result#cancelled() cancelled} settled nothing in time.
	 */
	private AStarForwardSearch.Result rerun(Capability capability, RoutingStatic stat, CollisionMap collision,
		int[] targets, int start, double heuristicWeight, BooleanSupplier stop, long deadline,
		SearchRestrictions restrictions, GoalStates goals)
	{
		SiteGraph granted = SiteGraph.compile(stat, accountWith.apply(requirements.granting(capability)));
		if (stop.getAsBoolean())
		{
			return null;
		}
		PreparedTarget prepared = PreparedTarget.prepare(granted, collision, targets);
		long sliceEnd = System.nanoTime() + (deadline - System.nanoTime()) / 2;
		AStarForwardSearch.Result route = prepared.search(start, () -> stop.getAsBoolean() || System.nanoTime() > sliceEnd,
			heuristicWeight, restrictions, AStarForwardSearch.NO_PROGRESS, goals::accepts);
		if (route.cancelled() && !stop.getAsBoolean())
		{
			route = prepared.search(start, stop, Math.max(heuristicWeight, QUICK_WEIGHT), restrictions,
				AStarForwardSearch.NO_PROGRESS, goals::accepts);
		}
		return route;
	}

	/**
	 * Routing components joined by an account's transports, in either bank layer, and by the static
	 * crossings. Walking within a component is free and positional restrictions such as avoiding the
	 * Wilderness are ignored, so reachability here is optimistic; the search rerun has the last word.
	 * ponytail: free walking leaves only the transports' durations to break ties between unlocks of
	 * equal effort; weigh walks between a component's sites if those ties pick badly.
	 */
	private static final class ComponentGraph
	{
		/** The cost of an edge nothing opens. */
		static final long CLOSED = Long.MAX_VALUE;

		private final RoutingStatic stat;
		private final int[][] siteComponents;
		private final int componentCount;
		// Per edge: the site it lands on, its transport (null for a crossing) and its duration. The
		// edges leaving a component are in outgoing; those cast anywhere are in anywhere.
		private final PrimitiveIntList to = new PrimitiveIntList();
		private final List<Transport> transports = new ArrayList<>();
		private final List<PrimitiveIntList> outgoing = new ArrayList<>();
		private final PrimitiveIntList anywhere = new PrimitiveIntList();
		private final PrimitiveIntList durations = new PrimitiveIntList();

		ComponentGraph(RoutingStatic stat, PreparedRoutingAccount account)
		{
			this.stat = stat;
			siteComponents = new int[stat.siteCount()][];
			int count = stat.routingComponentCount();
			for (int site = 0; site < siteComponents.length; site++)
			{
				siteComponents[site] = stat.siteComponents(site);
				// A site off the walkable tiles, such as a landing that is only the start of another
				// transport, is a component of its own, so the transports through it still join.
				if (siteComponents[site].length == 0)
				{
					siteComponents[site] = new int[] {count++};
				}
			}
			componentCount = count;
			for (int component = 0; component < componentCount; component++)
			{
				outgoing.add(new PrimitiveIntList());
			}
			// ponytail: both bank layers count as one, as if every route could visit a bank; split
			// them if banked-only transports start producing suggestions the rerun rejects.
			for (BankVisitState banked : BankVisitState.values())
			{
				for (int i = 0; i < account.localCount(banked); i++)
				{
					add(stat.siteIndex(account.localOrigin(banked, i)), stat.siteIndex(account.localDestination(banked, i)),
						account.localTransport(banked, i), account.localTransport(banked, i).getDuration());
				}
				for (int i = 0; i < account.globalCount(banked); i++)
				{
					add(-1, stat.siteIndex(account.globalDestination(banked, i)),
						account.globalTransport(TeleportCapability.ALL, banked, i),
						account.globalTransport(TeleportCapability.ALL, banked, i).getDuration());
				}
			}
			for (int i = 0; i < stat.crossingCount(); i++)
			{
				add(stat.crossingFromSite(i), stat.crossingToSite(i), null, stat.crossingCost(i));
				add(stat.crossingToSite(i), stat.crossingFromSite(i), null, stat.crossingCost(i));
			}
		}

		private void add(int fromSite, int toSite, Transport transport, int duration)
		{
			int edge = transports.size();
			durations.add(Math.max(0, duration));
			to.add(toSite);
			transports.add(transport);
			if (fromSite < 0)
			{
				anywhere.add(edge);
			}
			else
			{
				for (int component : siteComponents[fromSite])
				{
					outgoing.get(component).add(edge);
				}
			}
		}

		int edgeCount()
		{
			return transports.size();
		}

		int to(int edge)
		{
			return to.get(edge);
		}

		/** The ticks the edge takes. */
		int duration(int edge)
		{
			return durations.get(edge);
		}

		Transport transport(int edge)
		{
			return transports.get(edge);
		}

		/** The edges whose transports {@code account} may use, and every crossing. */
		boolean[] edgesOf(PreparedRoutingAccount account)
		{
			Set<Transport> usable = Collections.newSetFromMap(new IdentityHashMap<>());
			for (BankVisitState banked : BankVisitState.values())
			{
				for (int i = 0; i < account.localCount(banked); i++)
				{
					usable.add(account.localTransport(banked, i));
				}
				for (int i = 0; i < account.globalCount(banked); i++)
				{
					usable.add(account.globalTransport(TeleportCapability.ALL, banked, i));
				}
			}
			boolean[] edges = new boolean[edgeCount()];
			for (int edge = 0; edge < edges.length; edge++)
			{
				edges[edge] = transports.get(edge) == null || usable.contains(transports.get(edge));
			}
			return edges;
		}

		boolean[] components(CollisionMap collision, int... tiles)
		{
			boolean[] components = new boolean[componentCount];
			for (int tile : tiles)
			{
				int[] attached = stat.attachments(tile, collision);
				int site = stat.siteIndex(tile);
				for (int component : attached.length == 0 && site >= 0 ? siteComponents[site] : attached)
				{
					components[component] = true;
				}
			}
			return components;
		}

		/**
		 * The edges of the cheapest path by {@code cost} from a {@code starts} component, or from
		 * anywhere through a teleport, to a {@code targets} component, in order; {@code null} when
		 * every way there is {@link #CLOSED}.
		 */
		int[] cheapest(boolean[] starts, boolean[] targets, long[] cost)
		{
			long[] distance = new long[componentCount];
			Arrays.fill(distance, Long.MAX_VALUE);
			// The edge each component was reached by, and the component it left: -1 at the start.
			int[] via = new int[componentCount], previous = new int[componentCount];
			Arrays.fill(via, -1);
			Arrays.fill(previous, -1);
			PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
			for (int component = 0; component < componentCount; component++)
			{
				if (starts[component])
				{
					distance[component] = 0;
					queue.add(new long[] {0, component});
				}
			}
			for (int i = 0; i < anywhere.size(); i++)
			{
				relax(anywhere.get(i), -1, 0, cost, distance, via, previous, queue);
			}
			while (!queue.isEmpty())
			{
				long[] entry = queue.poll();
				int component = (int) entry[1];
				if (entry[0] != distance[component])
				{
					continue;
				}
				if (targets[component])
				{
					PrimitiveIntList edges = new PrimitiveIntList();
					for (int at = component; via[at] >= 0; at = previous[at])
					{
						edges.add(via[at]);
						if (previous[at] < 0)
						{
							break;
						}
					}
					int[] path = new int[edges.size()];
					for (int i = 0; i < path.length; i++)
					{
						path[i] = edges.get(path.length - 1 - i);
					}
					return path;
				}
				PrimitiveIntList edges = outgoing.get(component);
				for (int i = 0; i < edges.size(); i++)
				{
					relax(edges.get(i), component, entry[0], cost, distance, via, previous, queue);
				}
			}
			return null;
		}

		private void relax(int edge, int fromComponent, long at, long[] cost, long[] distance, int[] via,
			int[] previous, PriorityQueue<long[]> queue)
		{
			if (cost[edge] == CLOSED)
			{
				return;
			}
			long reached = at + cost[edge];
			if (reached < 0)
			{
				reached = CLOSED - 1;
			}
			for (int component : siteComponents[to(edge)])
			{
				if (reached < distance[component])
				{
					distance[component] = reached;
					via[component] = edge;
					previous[component] = fromComponent;
					queue.add(new long[] {reached, component});
				}
			}
		}
	}
}
