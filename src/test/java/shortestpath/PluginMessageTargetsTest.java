package shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.pathfinder.Target;
import shortestpath.pathfinder.Targets;
import shortestpath.transport.requirement.ItemRequirement;
import shortestpath.transport.requirement.TransportItems;

/** Converting the {@code target} of {@code path} and {@code query} messages to and from {@link Targets}. */
public class PluginMessageTargetsTest
{
	private static final WorldPoint A = new WorldPoint(3240, 3225, 0);
	private static final int PACKED_A = WorldPointUtil.packWorldPoint(A);
	private static final WorldPoint B = new WorldPoint(3260, 3230, 0);

	private static Map<String, Object> target(Object point, Object requirements)
	{
		Map<String, Object> target = new HashMap<>();
		target.put("point", point);
		target.put("requirements", requirements);
		return target;
	}

	/** The groups as item id to quantity, one map per group that must all be met. */
	private static List<Map<Integer, Integer>> groups(TransportItems items)
	{
		List<Map<Integer, Integer>> groups = new java.util.ArrayList<>();
		for (ItemRequirement group : items.getRequirements())
		{
			Map<Integer, Integer> alternatives = new LinkedHashMap<>();
			for (ItemRequirement.Branch branch : group.getBranches())
			{
				assertEquals("one exact id per alternative, no variations", 1, branch.getItemIds().length);
				alternatives.put(branch.getItemIds()[0], branch.getQuantity());
			}
			groups.add(alternatives);
		}
		return groups;
	}

	@Test
	public void nullIsNoTargets()
	{
		assertSame(Targets.NONE, PluginMessageTargets.decode(null));
	}

	@Test
	public void plainPointsNeedNoItems()
	{
		Targets targets = PluginMessageTargets.decode(Set.of(A, WorldPointUtil.packWorldPoint(B)));
		assertEquals(Set.of(PACKED_A, WorldPointUtil.packWorldPoint(B)), targets.points());
		assertEquals(false, targets.hasRequiredItems());
	}

	@Test
	public void requirementShapes()
	{
		assertEquals(List.of(Map.of(ItemID.SPADE, 1)), groups(PluginMessageTargets.decodeRequirements(ItemID.SPADE)));
		assertEquals(List.of(Map.of(ItemID.SPADE, 1, ItemID.ROPE, 1)),
			groups(PluginMessageTargets.decodeRequirements(Set.of(ItemID.SPADE, ItemID.ROPE))));
		assertEquals(List.of(Map.of(ItemID.COINS, 500)),
			groups(PluginMessageTargets.decodeRequirements(Map.of(ItemID.COINS, 500))));
		assertEquals(List.of(Map.of(ItemID.SPADE, 1), Map.of(ItemID.ROPE, 1, ItemID.KNIFE, 1), Map.of(ItemID.COINS, 2)),
			groups(PluginMessageTargets.decodeRequirements(List.of(ItemID.SPADE, List.of(ItemID.ROPE, ItemID.KNIFE),
				Map.of(ItemID.COINS, 2)))));
	}

	@Test
	public void encodingDecodesToTheSameTarget()
	{
		Target spadeAndCoins = PluginMessageTargets.decode(
			target(A, List.of(ItemID.SPADE, Set.of(ItemID.ROPE, ItemID.KNIFE), Map.of(ItemID.COINS, 50)))).get(PACKED_A);
		Object encoded = PluginMessageTargets.encode(spadeAndCoins);
		assertEquals(Targets.of(List.of(spadeAndCoins)), PluginMessageTargets.decode(encoded));
		assertEquals(A, PluginMessageTargets.encode(Target.at(PACKED_A)));
	}

	@Test
	public void unknownKeysInATargetMapAreIgnored()
	{
		Map<String, Object> target = target(A, ItemID.SPADE);
		target.put("label", "Dig here");
		assertEquals(true, PluginMessageTargets.decode(target).hasRequiredItems());
	}

	@Test
	public void missingItemsAreReportedAsTheirIds()
	{
		TransportItems items = PluginMessageTargets.decodeRequirements(
			List.of(List.of(ItemID.SPADE, ItemID.ROPE), ItemID.KNIFE));
		assertEquals(List.of(List.of(ItemID.SPADE, ItemID.ROPE), List.of(ItemID.KNIFE)),
			PluginMessageTargets.itemIds(items.getRequirements()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void undefinedPointIsInvalid()
	{
		PluginMessageTargets.decode(WorldPointUtil.UNDEFINED);
	}

	@Test(expected = IllegalArgumentException.class)
	public void nullRequirementElementIsInvalid()
	{
		PluginMessageTargets.decodeRequirements(java.util.Arrays.asList(ItemID.SPADE, null));
	}
}
