package shortestpath.pathfinder.exact;

import java.util.Arrays;

/** Primitive duplicate-entry min-heap ordered by (priority, cost, state). */
public final class ExactMinHeap
{
	private int[] priorities, costs, states;
	private int size;
	private int poppedPriority, poppedCost, poppedState;

	public ExactMinHeap(int initialCapacity)
	{
		int capacity = Math.max(1, initialCapacity);
		priorities = new int[capacity];
		costs = new int[capacity];
		states = new int[capacity];
	}

	public int size()
	{
		return size;
	}
	public boolean isEmpty()
	{
		return size == 0;
	}
	public int priority()
	{
		return poppedPriority;
	}
	public int cost()
	{
		return poppedCost;
	}
	public int state()
	{
		return poppedState;
	}

	public void push(int priority, int cost, int state)
	{
		ExactCosts.validate(priority);
		ExactCosts.validate(cost);
		if (priority == ExactCosts.INF || cost == ExactCosts.INF)
			throw new IllegalArgumentException("infinite heap entries are not searchable");
		if (size == states.length)
		{
			int capacity = states.length < 1_073_741_824 ? states.length * 2 : Integer.MAX_VALUE;
			priorities = Arrays.copyOf(priorities, capacity);
			costs = Arrays.copyOf(costs, capacity);
			states = Arrays.copyOf(states, capacity);
		}
		priorities[size] = priority;
		costs[size] = cost;
		states[size] = state;
		siftUp(size++);
	}

	public boolean poll()
	{
		if (size == 0)
			return false;
		poppedPriority = priorities[0];
		poppedCost = costs[0];
		poppedState = states[0];
		--size;
		if (size != 0)
		{
			priorities[0] = priorities[size];
			costs[0] = costs[size];
			states[0] = states[size];
			siftDown(0);
		}
		return true;
	}

	private boolean before(int left, int right)
	{
		int result = Integer.compare(priorities[left], priorities[right]);
		if (result != 0)
			return result < 0;
		result = Integer.compare(costs[left], costs[right]);
		return result != 0 ? result < 0 : Integer.compare(states[left], states[right]) < 0;
	}

	private void siftUp(int index)
	{
		int priority = priorities[index], cost = costs[index], state = states[index];
		while (index > 0)
		{
			int parent = (index - 1) >>> 1;
			if (compare(priority, cost, state, priorities[parent], costs[parent], states[parent]) >= 0)
				break;
			priorities[index] = priorities[parent];
			costs[index] = costs[parent];
			states[index] = states[parent];
			index = parent;
		}
		priorities[index] = priority;
		costs[index] = cost;
		states[index] = state;
	}

	private void siftDown(int index)
	{
		int priority = priorities[index], cost = costs[index], state = states[index];
		int half = size >>> 1;
		while (index < half)
		{
			int child = (index << 1) + 1;
			int right = child + 1;
			if (right < size && before(right, child))
				child = right;
			if (compare(priority, cost, state, priorities[child], costs[child], states[child]) <= 0)
				break;
			priorities[index] = priorities[child];
			costs[index] = costs[child];
			states[index] = states[child];
			index = child;
		}
		priorities[index] = priority;
		costs[index] = cost;
		states[index] = state;
	}

	private static int compare(int p1, int c1, int s1, int p2, int c2, int s2)
	{
		int result = Integer.compare(p1, p2);
		if (result != 0)
			return result;
		result = Integer.compare(c1, c2);
		return result != 0 ? result : Integer.compare(s1, s2);
	}
}
