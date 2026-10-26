package shortestpath.pathfinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;
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
 * routing components joined by transports and crossings it finds the components the player can
 * reach now and the components that could reach the target if the player met every account
 * requirement (settings and world restrictions still apply). Each locked transport from the first
 * set into the second names the capabilities that would open it on their own, such as carrying 20
 * coins for the boat to Aldarin. They are tried cheapest first: first on the component graph, then
 * by rerunning the search with the capability granted. Only those that then reach the target are
 * suggested, and none that a cheaper suggestion already covers.
 */
public final class UnlockSuggestions
{
	/** Up to one game tick, on top of the search that found the target unreachable. */
	static final long BUDGET_NANOS = 600_000_000L;
	/**
	 * No capability starts its rerun with less than this left, as preparing the rerun's graph can't
	 * be interrupted.
	 */
	static final long RERUN_RESERVE_NANOS = 100_000_000L;
	/** A rerun stopped at the deadline takes a moment to return, so it is stopped this much early. */
	private static final long WIND_DOWN_NANOS = 10_000_000L;
	/** How long each of a rerun's two searches may take before the capability is left undecided. */
	static final long CHECK_SLICE_NANOS = 60_000_000L;
	/**
	 * Stretches every limit above. Tests that check what is found, rather than how fast, raise it so
	 * a busy machine can't change the answer.
	 */
	static int timeScale = 1;
	/** The heuristic weight of a rerun's second, greedier search. */
	private static final double QUICK_WEIGHT = 3.0;
	static final int MAX_SUGGESTIONS = 3;

	/** One capability that lets the route reach the target, and a transport it opens on the way. */
	@Getter
	public static final class Suggestion
	{
		private final Capability capability;
		private final Transport transport;
		/** The tier it ranked in, for this player. */
		private final Capability.Effort effort;

		Suggestion(Capability capability, Transport transport, Capability.Effort effort)
		{
			this.capability = capability;
			this.transport = transport;
			this.effort = effort;
		}
	}

	/** What one look found, and how much it weighed up. */
	@Getter
	public static final class Found
	{
		static final Found NONE = new Found(List.of(), 0, 0, 0, false, false);

		private final List<Suggestion> suggestions;
		/** The capabilities that might open the way. */
		private final int candidates;
		/** How many of them were checked by rerunning the search. */
		private final int reruns;
		/** How many reruns settled nothing within their time, so their capability wasn't suggested. */
		private final int inconclusive;
		/** Whether it stopped for the time budget before trying them all. */
		private final boolean outOfTime;
		/**
		 * Whether the component graph already joined the player to the target, so whatever blocks
		 * the route isn't a lock it can see and only the reruns could tell the candidates apart.
		 */
		private final boolean alreadyConnected;

		Found(List<Suggestion> suggestions, int candidates, int reruns, int inconclusive, boolean outOfTime,
			boolean alreadyConnected)
		{
			this.suggestions = List.copyOf(suggestions);
			this.candidates = candidates;
			this.reruns = reruns;
			this.inconclusive = inconclusive;
			this.outOfTime = outOfTime;
			this.alreadyConnected = alreadyConnected;
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
		long deadline = System.nanoTime() + (BUDGET_NANOS - WIND_DOWN_NANOS) * timeScale;
		BooleanSupplier stop = () -> cancelled.getAsBoolean() || System.nanoTime() > deadline;
		int[] targets = goals.tiles().stream().mapToInt(Integer::intValue).toArray();
		ComponentGraph graph = new ComponentGraph(stat, obtainable.get());
		boolean[] startComponents = graph.components(collision, start);
		boolean[] targetComponents = graph.components(collision, targets);
		boolean[] usable = graph.edgesOf(account);
		boolean[] reached = graph.reach(startComponents, usable, false);
		// When the component graph already joins them, what blocks the route is something it can't see,
		// such as a position the route must avoid: every locked way out is a candidate and only the
		// reruns tell them apart.
		boolean connected = graph.any(reached, targetComponents);
		boolean[] leadsToTarget = connected ? graph.everything() : graph.reach(targetComponents, null, true);

		// What would open each locked transport, and the capabilities opening the locked edges out of
		// the reached components into ones that could still lead to the target.
		Map<Transport, List<Capability>> opensWith = new IdentityHashMap<>();
		Map<Capability, PrimitiveIntList> frontier = new LinkedHashMap<>();
		for (int edge = 0; edge < graph.edgeCount(); edge++)
		{
			Transport transport = graph.transport(edge);
			if (transport == null || usable[edge])
			{
				continue;
			}
			List<Capability> capabilities = opensWith.computeIfAbsent(transport, requirements::capabilities);
			if ((graph.from(edge) < 0 || graph.any(reached, graph.from(edge))) && graph.any(leadsToTarget, graph.to(edge)))
			{
				for (Capability capability : capabilities)
				{
					frontier.computeIfAbsent(capability, ignored -> new PrimitiveIntList()).add(edge);
				}
			}
		}

		List<Capability> ranked = new ArrayList<>(frontier.keySet());
		ranked.sort(requirements.cheapestFirst());
		List<Capability> viable = new ArrayList<>();
		for (Capability capability : ranked)
		{
			if (connected || joins(graph, capability, frontier, opensWith, usable, reached, leadsToTarget, targetComponents))
			{
				viable.add(capability);
			}
		}
		List<Suggestion> suggestions = new ArrayList<>();
		int reruns = 0;
		int inconclusive = 0;
		// Granting more never closes a route, so when all of them together don't get there, none does
		// on its own: one rerun settles a hopeless target.
		if (viable.size() > 1)
		{
			Capability all = viable.stream().reduce(Capability::plus).orElseThrow();
			AStarForwardSearch.Result route = rerun(all, stat, collision, targets, start, heuristicWeight, stop,
				restrictions, goals);
			reruns++;
			if (route != null && !route.cancelled() && !route.reached())
			{
				return new Found(suggestions, ranked.size(), reruns, inconclusive, false, connected);
			}
		}
		boolean outOfTime = false;
		for (Capability capability : viable)
		{
			if (suggestions.size() == MAX_SUGGESTIONS || cancelled.getAsBoolean())
			{
				break;
			}
			if (System.nanoTime() > deadline - RERUN_RESERVE_NANOS * timeScale)
			{
				outOfTime = true;
				break;
			}
			// Skip one a cheaper suggestion already covers, or that only opens what one already opens
			// (another variation of the same item, say).
			if (suggestions.stream().anyMatch(suggestion -> capability.covers(suggestion.getCapability())
				|| graph.transports(frontier.get(suggestion.getCapability()))
					.containsAll(graph.transports(frontier.get(capability)))))
			{
				continue;
			}
			AStarForwardSearch.Result route = rerun(capability, stat, collision, targets, start, heuristicWeight, stop,
				restrictions, goals);
			if (route == null || route.cancelled() && stop.getAsBoolean())
			{
				outOfTime = !cancelled.getAsBoolean();
				break;
			}
			reruns++;
			if (route.cancelled())
			{
				inconclusive++;
			}
			else if (route.reached())
			{
				suggestions.add(new Suggestion(capability, onRoute(graph.transports(frontier.get(capability)), route.path()),
					requirements.effort(capability)));
			}
		}
		return new Found(suggestions, ranked.size(), reruns, inconclusive, outOfTime, connected);
	}

	/**
	 * The search rerun with {@code capability} granted, or {@code null} when {@code stop} came first.
	 * It only has to show the target can be reached: the plain search settles most at once, and one
	 * still going after its slice is usually a long way round, which a greedier search finds quickly.
	 * A result still {@link AStarForwardSearch.Result#cancelled() cancelled} settled nothing in time.
	 */
	private AStarForwardSearch.Result rerun(Capability capability, RoutingStatic stat, CollisionMap collision,
		int[] targets, int start, double heuristicWeight, BooleanSupplier stop, SearchRestrictions restrictions,
		GoalStates goals)
	{
		SiteGraph granted = SiteGraph.compile(stat, accountWith.apply(requirements.granting(capability)));
		if (stop.getAsBoolean())
		{
			return null;
		}
		PreparedTarget prepared = PreparedTarget.prepare(granted, collision, targets);
		AStarForwardSearch.Result route = check(prepared, start, heuristicWeight, stop, restrictions, goals);
		if (route.cancelled() && !stop.getAsBoolean())
		{
			route = check(prepared, start, Math.max(heuristicWeight, QUICK_WEIGHT), stop, restrictions, goals);
		}
		return route;
	}

	/**
	 * Whether, on the component graph, the capability joins the reached components to the target's:
	 * through the way out it opens, its own edges and those of every capability it covers.
	 */
	private static boolean joins(ComponentGraph graph, Capability capability, Map<Capability, PrimitiveIntList> frontier,
		Map<Transport, List<Capability>> opensWith, boolean[] usable, boolean[] reached, boolean[] leadsToTarget,
		boolean[] targetComponents)
	{
		PrimitiveIntList exits = new PrimitiveIntList();
		frontier.forEach((other, edges) ->
		{
			if (capability.covers(other))
			{
				for (int i = 0; i < edges.size(); i++)
				{
					exits.add(edges.get(i));
				}
			}
		});
		IntPredicate open = edge -> usable[edge]
			|| opensWith.get(graph.transport(edge)).stream().anyMatch(capability::covers);
		return graph.joins(reached, exits, open, leadsToTarget, targetComponents);
	}

	/** The rerun's search, stopped after {@link #CHECK_SLICE_NANOS} as well as at {@code stop}. */
	private static AStarForwardSearch.Result check(PreparedTarget target, int start, double weight, BooleanSupplier stop,
		SearchRestrictions restrictions, GoalStates goals)
	{
		long sliceEnd = System.nanoTime() + CHECK_SLICE_NANOS * timeScale;
		return target.search(start, () -> stop.getAsBoolean() || System.nanoTime() > sliceEnd, weight, restrictions,
			AStarForwardSearch.NO_PROGRESS, goals::accepts);
	}

	/** The transport the route lands from, else the first one. */
	private static Transport onRoute(List<Transport> transports, List<PathStep> route)
	{
		for (Transport transport : transports)
		{
			for (PathStep step : route)
			{
				if (step.getPackedPosition() == transport.getDestination())
				{
					return transport;
				}
			}
		}
		return transports.get(0);
	}

	/**
	 * Routing components joined by an account's transports, in either bank layer, and by the static
	 * crossings. Walking within a component is free and positional restrictions such as avoiding the
	 * Wilderness are ignored, so reachability here is optimistic; the search rerun has the last word.
	 */
	private static final class ComponentGraph
	{
		private final RoutingStatic stat;
		private final int[][] siteComponents;
		private final int componentCount;
		// Per edge: the sites it joins, -1 for a teleport cast anywhere, and its transport, null for a crossing.
		private final PrimitiveIntList from = new PrimitiveIntList(), to = new PrimitiveIntList();
		private final List<Transport> transports = new ArrayList<>();
		private final List<PrimitiveIntList> outgoing = new ArrayList<>(), incoming = new ArrayList<>();
		private final PrimitiveIntList anywhere = new PrimitiveIntList();

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
				incoming.add(new PrimitiveIntList());
			}
			// ponytail: both bank layers count as one, as if every route could visit a bank; split
			// them if banked-only transports start producing suggestions the rerun rejects.
			for (BankVisitState banked : BankVisitState.values())
			{
				for (int i = 0; i < account.localCount(banked); i++)
				{
					add(stat.siteIndex(account.localOrigin(banked, i)), stat.siteIndex(account.localDestination(banked, i)),
						account.localTransport(banked, i));
				}
				for (int i = 0; i < account.globalCount(banked); i++)
				{
					add(-1, stat.siteIndex(account.globalDestination(banked, i)),
						account.globalTransport(TeleportCapability.ALL, banked, i));
				}
			}
			for (int i = 0; i < stat.crossingCount(); i++)
			{
				add(stat.crossingFromSite(i), stat.crossingToSite(i), null);
				add(stat.crossingToSite(i), stat.crossingFromSite(i), null);
			}
		}

		private void add(int fromSite, int toSite, Transport transport)
		{
			int edge = transports.size();
			from.add(fromSite);
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
			for (int component : siteComponents[toSite])
			{
				incoming.get(component).add(edge);
			}
		}

		int edgeCount()
		{
			return transports.size();
		}

		int from(int edge)
		{
			return from.get(edge);
		}

		int to(int edge)
		{
			return to.get(edge);
		}

		Transport transport(int edge)
		{
			return transports.get(edge);
		}

		List<Transport> transports(PrimitiveIntList edges)
		{
			List<Transport> result = new ArrayList<>(edges.size());
			for (int i = 0; i < edges.size(); i++)
			{
				result.add(transports.get(edges.get(i)));
			}
			return result;
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

		boolean[] everything()
		{
			boolean[] all = new boolean[componentCount];
			Arrays.fill(all, true);
			return all;
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
		 * The components reachable from {@code seeds} over the {@code allowed} edges (all of them when
		 * {@code null}), or with {@code reverse} the components that can reach them.
		 */
		boolean[] reach(boolean[] seeds, boolean[] allowed, boolean reverse)
		{
			boolean[] reached = seeds.clone();
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			for (int component = 0; component < reached.length; component++)
			{
				if (reached[component])
				{
					queue.add(component);
				}
			}
			for (int i = 0; !reverse && i < anywhere.size(); i++)
			{
				visit(anywhere.get(i), allowed, to(anywhere.get(i)), reached, queue);
			}
			while (!queue.isEmpty())
			{
				PrimitiveIntList edges = (reverse ? incoming : outgoing).get(queue.poll());
				for (int i = 0; i < edges.size(); i++)
				{
					int edge = edges.get(i);
					if (reverse && from(edge) < 0 && (allowed == null || allowed[edge]))
					{
						// A teleport cast anywhere lands here, so everywhere reaches it.
						Arrays.fill(reached, true);
						return reached;
					}
					visit(edge, allowed, reverse ? from(edge) : to(edge), reached, queue);
				}
			}
			return reached;
		}

		/**
		 * Whether going on from the {@code reached} components through the {@code exits} and other
		 * edges that are {@code open}, staying {@code within} those components, gets to a
		 * {@code targets} component. Only the newly reached components are walked, not those reached.
		 */
		boolean joins(boolean[] reached, PrimitiveIntList exits, IntPredicate open, boolean[] within, boolean[] targets)
		{
			boolean[] seen = reached.clone();
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			for (int i = 0; i < exits.size(); i++)
			{
				enter(exits.get(i), open, within, seen, queue);
			}
			while (!queue.isEmpty())
			{
				int component = queue.poll();
				if (targets[component])
				{
					return true;
				}
				PrimitiveIntList edges = outgoing.get(component);
				for (int i = 0; i < edges.size(); i++)
				{
					enter(edges.get(i), open, within, seen, queue);
				}
			}
			return false;
		}

		private void enter(int edge, IntPredicate open, boolean[] within, boolean[] seen, ArrayDeque<Integer> queue)
		{
			if (!open.test(edge))
			{
				return;
			}
			for (int component : siteComponents[to(edge)])
			{
				if (within[component] && !seen[component])
				{
					seen[component] = true;
					queue.add(component);
				}
			}
		}

		private void visit(int edge, boolean[] allowed, int site, boolean[] reached, ArrayDeque<Integer> queue)
		{
			if (allowed != null && !allowed[edge])
			{
				return;
			}
			for (int component : siteComponents[site])
			{
				if (!reached[component])
				{
					reached[component] = true;
					queue.add(component);
				}
			}
		}

		boolean any(boolean[] components, int site)
		{
			for (int component : siteComponents[site])
			{
				if (components[component])
				{
					return true;
				}
			}
			return false;
		}

		boolean any(boolean[] components, boolean[] others)
		{
			for (int i = 0; i < components.length; i++)
			{
				if (components[i] && others[i])
				{
					return true;
				}
			}
			return false;
		}
	}
}
