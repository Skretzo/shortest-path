package shortestpath.pathfinder.astar;

import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.Traversal;
import shortestpath.transport.Transport;

/**
 * States beside the walking ones, in a graph of their own that a route enters from tiles and leaves onto tiles, or ends
 * in: the sea, where the player is aboard a boat. The search knows nothing of boats: it gives each node a state per bank
 * layer, as it does each tile, and asks the graph for the edges out of them. A search without one ({@link #NONE}) pays
 * nothing for it.
 */
public interface SideGraph
{
	/** No side graph: the route only walks. */
	SideGraph NONE = new SideGraph()
	{
		@Override
		public int size()
		{
			return 0;
		}

		@Override
		public int startNode()
		{
			return -1;
		}

		@Override
		public boolean entersAt(int tile)
		{
			return false;
		}

		@Override
		public void enter(int tile, BankVisitState banked, Edges edges)
		{
		}

		@Override
		public void expand(int node, BankVisitState banked, Edges edges)
		{
		}

		@Override
		public int estimate(int node, BankVisitState banked, LandEstimate land)
		{
			return CostArithmetic.INF;
		}

		@Override
		public int tile(int node)
		{
			return -1;
		}

		@Override
		public Traversal traversal()
		{
			return Traversal.WALK;
		}
	};

	/** Where a node's edges go. */
	interface Edges
	{
		/** To {@code node}, for {@code cost}, by {@code transport} (null for none). */
		void toNode(int node, int cost, Transport transport);

		/** Onto {@code tile}, for {@code cost}, by {@code transport} (null for none). */
		void toTile(int tile, int cost, Transport transport);

		/** To the end of the route at {@code tile}, for {@code cost}. */
		void toEnd(int tile, int cost);
	}

	/** The search's estimate from a tile. */
	interface LandEstimate
	{
		/** A lower bound on the cost from {@code tile} to the end of the route, or {@link CostArithmetic#INF}. */
		int at(int tile, BankVisitState banked);
	}

	/** The number of nodes. */
	int size();

	/** The node the route starts on, or -1 if it starts at its start tile. */
	int startNode();

	/** Whether the route may enter the graph from {@code tile}: asked of every tile the search takes, so cheap. */
	boolean entersAt(int tile);

	/** The edges into the graph from {@code tile}, in bank layer {@code banked}. */
	void enter(int tile, BankVisitState banked, Edges edges);

	/** The edges out of {@code node}, in bank layer {@code banked}. */
	void expand(int node, BankVisitState banked, Edges edges);

	/**
	 * A lower bound on the cost from {@code node} to the end of the route, or {@link CostArithmetic#INF} if there is no
	 * way on; {@code land} answers it for the tiles the graph leaves onto.
	 */
	int estimate(int node, BankVisitState banked, LandEstimate land);

	/** The tile a step on {@code node} is shown at. */
	int tile(int node);

	/** How a route's steps on the graph are reached, such as {@link Traversal#SAILING} for the sea. */
	Traversal traversal();
}
