package shortestpath.pathfinder.exact;

import shortestpath.ShortestPathPlugin;
import shortestpath.WorldPointUtil;

/** Tiny valid static routing data for account/site-graph tests. */
public final class RoutingStaticTestFixture
{
	public static final int A = WorldPointUtil.packWorldPoint(1000, 1000, 0);
	public static final int BANK = WorldPointUtil.packWorldPoint(1001, 1000, 0);
	public static final int C = WorldPointUtil.packWorldPoint(1002, 1000, 0);
	public static final int D = WorldPointUtil.packWorldPoint(1003, 1000, 0);
	public static final int POH_ORIGIN = WorldPointUtil.packWorldPoint(1900, 7048, 0);
	public static final int POH_LANDING = WorldPointUtil.packWorldPoint(ShortestPathPlugin.POH_LANDING_X, ShortestPathPlugin.POH_LANDING_Y, 0);
	public static final int T3_A = WorldPointUtil.packWorldPoint(1000, 1000, 0);
	public static final int T3_B = WorldPointUtil.packWorldPoint(1002, 1000, 0);
	public static final int T3_E = WorldPointUtil.packWorldPoint(1004, 1000, 0);
	public static final int T3_F = WorldPointUtil.packWorldPoint(1006, 1000, 0);
	public static final int T3_C = WorldPointUtil.packWorldPoint(1000, 1002, 0);
	public static final int T3_D = WorldPointUtil.packWorldPoint(1000, 1000, 1);
	public static final int T3_TARGET = WorldPointUtil.packWorldPoint(1001, 1001, 0);
	public static final int T3_SEARCH_0 = WorldPointUtil.packWorldPoint(1001, 1000, 0);
	public static final int T3_SEARCH_1 = WorldPointUtil.packWorldPoint(1001, 1002, 0);

	private RoutingStaticTestFixture()
	{
	}

	public static RoutingStatic create() throws Exception
	{
		int[] sites = {A, BANK, C, D, POH_ORIGIN, POH_LANDING};
		return RoutingStatic.create(new int[0], new int[0], new byte[0], new int[0], new int[0], sites,
			new int[] {0, 1, 2, 3, 4, 5, 6}, new int[] {0, 0, 0, 0, 0, 0}, 1, new int[] {0, 6},
			new int[] {0, 1, 2, 3, 4, 5}, new int[] {BANK}, new int[] {0}, new int[] {2}, new int[] {1}, 6, 6, 0, 0, 0,
			new int[] {0, 0, 0, 0, 0, 0, 0}, new int[0], new int[0]);
	}

	public static RoutingStatic createT3() throws Exception
	{
		int[] sites = {T3_A, T3_B, T3_E, T3_F, T3_C, T3_D};
		return RoutingStatic.create(new int[] {T3_SEARCH_0, T3_SEARCH_1}, new int[] {0, 1}, new byte[] {0, 0},
			new int[] {-1, -1}, new int[] {-1, -1}, sites, new int[] {0, 1, 3, 4, 5, 6, 7},
			new int[] {0, 1, 2, 1, 2, 0, 0}, 3, new int[] {0, 3, 5, 7}, new int[] {0, 4, 5, 1, 2, 1, 3},
			new int[] {T3_B}, new int[0], new int[0], new int[0], 6, 6, 0, 0, 0, new int[] {0, 0, 0, 0, 0, 0, 0},
			new int[0], new int[0]);
	}

	public static RoutingStatic createT4Walking() throws Exception
	{
		int[] sites = {A, BANK, C, D};
		return RoutingStatic.create(new int[] {A, BANK, C}, new int[] {0, 0, 0},
			new byte[] {4, 68, 64}, new int[] {-1, -1, -1}, new int[] {-1, -1, -1}, sites,
			new int[] {0, 1, 2, 3, 4}, new int[] {0, 0, 0, 0}, 1, new int[] {0, 4},
			new int[] {0, 1, 2, 3}, new int[] {BANK}, new int[0], new int[0], new int[0], 4, 4, 0, 0, 0,
			new int[] {0, 0, 0, 0, 0}, new int[0], new int[0]);
	}
}
