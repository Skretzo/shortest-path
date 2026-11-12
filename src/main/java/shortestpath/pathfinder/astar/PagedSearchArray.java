package shortestpath.pathfinder.astar;

import java.util.Arrays;

/** Per-search integers, allocating storage only for pages that receive a write. */
final class PagedSearchArray
{
	private static final int PAGE_BITS = 10;
	private static final int PAGE_SIZE = 1 << PAGE_BITS;
	private static final int PAGE_MASK = PAGE_SIZE - 1;
	private final int[][] pages;
	private final int defaultValue;

	PagedSearchArray(int size, int defaultValue)
	{
		pages = new int[(size + PAGE_MASK) >>> PAGE_BITS][];
		this.defaultValue = defaultValue;
	}

	int get(int index)
	{
		int[] page = pages[index >>> PAGE_BITS];
		return page == null ? defaultValue : page[index & PAGE_MASK];
	}

	void set(int index, int value)
	{
		int pageIndex = index >>> PAGE_BITS;
		int[] page = pages[pageIndex];
		if (page == null)
		{
			page = new int[PAGE_SIZE];
			if (defaultValue != 0) Arrays.fill(page, defaultValue);
			pages[pageIndex] = page;
		}
		page[index & PAGE_MASK] = value;
	}
}
