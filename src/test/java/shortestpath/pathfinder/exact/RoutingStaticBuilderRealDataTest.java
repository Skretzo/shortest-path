package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.Destination;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;

/**
 * Real-data coverage for {@link RoutingStaticBuilder}: derivation succeeds on the committed
 * collision map, the plugin's transports and banks, and the committed {@code routing-cuts.bin},
 * and every {@link RoutingStatic} invariant holds.
 */
public class RoutingStaticBuilderRealDataTest
{
	private static CollisionMap collision;
	private static RoutingStaticBuilder.Result result;

	@BeforeClass
	public static void buildOnce() throws Exception
	{
		collision = new CollisionMap(SplitFlagMap.fromResources());
		Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();
		Set<Integer> banks = Destination.loadAllFromResources().get("bank");
		int[] cuts = RoutingCuts.loadFromResources().pairs();
		result = RoutingStaticBuilder.build(collision, transports, banks, cuts);
		System.out.println("real-data build: " + result.diagnostics);
	}

	@Test
	public void buildsSuccessfullyWithPlausibleCounts()
	{
		RoutingStatic mine = result.routingStatic;
		// RoutingStatic.create() already validated every structural invariant inside build().
		assertTrue("search tile count should be substantial", mine.searchTileCount() > 1_000_000);
		assertTrue("site count should be substantial", mine.siteCount() > 1_000);
		assertTrue("at least one reachable bank", mine.reachableBankCount() > 0);
		assertTrue("routing component count should exceed 1", mine.routingComponentCount() > 1);
	}

	@Test
	public void mostCommittedCutsStillSeparateComponents()
	{
		// Cuts may lag behind collision map updates; dropped ones only cost performance.
		assertTrue(result.diagnostics.inputCutCount > 0);
		assertTrue("most cuts should still be valid separating crossings on the current map",
			result.diagnostics.crossingCount > result.diagnostics.inputCutCount / 2);
	}

	@Test
	public void reachableBankIndexesExactlyMatchTheDerivedList()
	{
		RoutingStatic mine = result.routingStatic;
		int expectedNodes = 0;
		for (int i = 0; i < mine.reachableBankCount(); i++)
		{
			int tile = mine.reachableBankTile(i);
			assertTrue(mine.isReachableBankSite(mine.siteIndex(tile)));
			int node = mine.searchIndex(tile);
			if (node >= 0)
			{
				assertTrue(mine.isReachableBankNode(node));
				expectedNodes++;
			}
		}
		int actualNodes = 0;
		for (int node = 0; node < mine.searchTileCount(); node++)
		{
			if (mine.isReachableBankNode(node))
			{
				actualNodes++;
			}
		}
		int actualSites = 0;
		for (int site = 0; site < mine.siteCount(); site++)
		{
			if (mine.isReachableBankSite(site))
			{
				actualSites++;
			}
		}
		assertEquals(expectedNodes, actualNodes);
		assertEquals(mine.reachableBankCount(), actualSites);
	}

	@Test
	public void siteAttachmentsMatchCollisionDerivedAttachments()
	{
		RoutingStatic mine = result.routingStatic;
		int mismatches = 0;
		StringBuilder examples = new StringBuilder();
		for (int site = 0; site < mine.siteCount(); site++)
		{
			int[] expected = mine.siteComponents(site);
			int[] actual = mine.attachments(mine.siteTile(site), collision);
			Arrays.sort(expected);
			Arrays.sort(actual);
			if (!Arrays.equals(expected, actual) && mismatches++ < 3)
			{
				examples.append(" site ").append(site).append(" expected=").append(Arrays.toString(expected))
					.append(" actual=").append(Arrays.toString(actual));
			}
		}
		assertEquals(examples.toString(), 0, mismatches);
	}
}
