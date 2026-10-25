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
	static final int MAX_SUGGESTIONS = 3;

	/** One capability that lets the route reach the target, and a transport it opens on the way. */
	@Getter
	public static final class Suggestion
	{
		private final Capability capability;
		private final Transport transport;

		Suggestion(Capability capability, Transport transport)
		{
			this.capability = capability;
			this.transport = transport;
		}
	}

	/** What one look found, and how much it weighed up. */
	@Getter
	public static final class Found
	{
		static final Found NONE = new Found(List.of(), 0, 0, false, false);

		private final List<Suggestion> suggestions;
		/** The capabilities that might open the way. */
		private final int candidates;
		/** How many of them were checked by rerunning the search. */
		private final int reruns;
		/** Whether it stopped for the time budget before trying them all. */
		private final boolean outOfTime;
		/**
		 * Whether the component graph already joined the player to the target, so whatever blocks
		 * the route isn't a lock it can see and nothing was tried.
		 */
		private final boolean alreadyConnected;

		Found(List<Suggestion> suggestions, int candidates, int reruns, boolean outOfTime, boolean alreadyConnected)
		{
			this.suggestions = List.copyOf(suggestions);
			this.candidates = candidates;
			this.reruns = reruns;
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
		long deadline = System.nanoTime() + BUDGET_NANOS - WIND_DOWN_NANOS;
		BooleanSupplier stop = () -> cancelled.getAsBoolean() || System.nanoTime() > deadline;
		int[] targets = goals.tiles().stream().mapToInt(Integer::intValue).toArray();
		ComponentGraph graph = new ComponentGraph(stat, obtainable.get());
		boolean[] startComponents = graph.components(collision, start);
		boolean[] targetComponents = graph.components(collision, targets);
		boolean[] usable = graph.edgesOf(account);
		boolean[] reached = graph.reach(startComponents, usable, false);
		if (graph.any(reached, targetComponents))
		{
			return new Found(List.of(), 0, 0, false, true);
		}
		boolean[] leadsToTarget = graph.reach(targetComponents, null, true);

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
		List<Suggestion> suggestions = new ArrayList<>();
		int reruns = 0;
		boolean outOfTime = false;
		for (Capability capability : ranked)
		{
			if (suggestions.size() == MAX_SUGGESTIONS || cancelled.getAsBoolean())
			{
				break;
			}
			if (System.nanoTime() > deadline - RERUN_RESERVE_NANOS)
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
			// The way out it opens: its own edges and those of every capability it covers.
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
			if (!graph.joins(reached, exits, open, leadsToTarget, targetComponents))
			{
				continue;
			}
			SiteGraph granted = SiteGraph.compile(stat, accountWith.apply(requirements.granting(capability)));
			if (stop.getAsBoolean())
			{
				outOfTime = !cancelled.getAsBoolean();
				break;
			}
			reruns++;
			AStarForwardSearch.Result route = PreparedTarget.prepare(granted, collision, targets)
				.search(start, stop, heuristicWeight, restrictions, AStarForwardSearch.NO_PROGRESS, goals::accepts);
			if (route.cancelled())
			{
				outOfTime = !cancelled.getAsBoolean();
				break;
			}
			if (route.reached())
			{
				suggestions.add(new Suggestion(capability, onRoute(graph.transports(frontier.get(capability)), route.path())));
			}
		}
		return new Found(suggestions, ranked.size(), reruns, outOfTime, false);
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
		// Per edge: the sites it joins, -1 for a teleport cast anywhere, and its transport, null for a crossing.
		private final PrimitiveIntList from = new PrimitiveIntList(), to = new PrimitiveIntList();
		private final List<Transport> transports = new ArrayList<>();
		private final List<PrimitiveIntList> outgoing = new ArrayList<>(), incoming = new ArrayList<>();
		private final PrimitiveIntList anywhere = new PrimitiveIntList();

		ComponentGraph(RoutingStatic stat, PreparedRoutingAccount account)
		{
			this.stat = stat;
			siteComponents = new int[stat.siteCount()][];
			for (int site = 0; site < siteComponents.length; site++)
			{
				siteComponents[site] = stat.siteComponents(site);
			}
			for (int component = 0; component < stat.routingComponentCount(); component++)
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

		boolean[] components(CollisionMap collision, int... tiles)
		{
			boolean[] components = new boolean[stat.routingComponentCount()];
			for (int tile : tiles)
			{
				for (int component : stat.attachments(tile, collision))
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
