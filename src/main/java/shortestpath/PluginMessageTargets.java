package shortestpath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import shortestpath.pathfinder.Target;
import shortestpath.pathfinder.Targets;
import shortestpath.transport.requirement.ItemRequirement;
import shortestpath.transport.requirement.TransportItems;

/**
 * Converts between {@link Targets} and the values other plugins put in the {@code target} of a
 * {@code path} or {@code query} message, using only JDK and RuneLite types, since plugins can't
 * share classes.
 *
 * <p>A target is a {@link WorldPoint}, a packed {@link Integer}, or a target map
 * {@code {"point": WorldPoint|Integer, "requirements": requirement}}; a message's {@code target} is
 * one target or a {@link Collection} of them. A requirement is:
 * <ul>
 *     <li>an {@code Integer} item id: one of that item;</li>
 *     <li>a {@code Map<Integer, Integer>} of item id to quantity: any one of those items, at
 *     least that many;</li>
 *     <li>any other {@code Collection} of item ids: any one of them;</li>
 *     <li>a {@code List} of the above: all of them. Inside it, a {@code List} is an "any of"
 *     group like any other collection.</li>
 * </ul>
 * Item ids are matched exactly; no item variations are added.
 */
final class PluginMessageTargets
{
	static final String POINT = "point";
	static final String REQUIREMENTS = "requirements";

	private static final int[] NONE = new int[0];

	private PluginMessageTargets()
	{
	}

	/**
	 * The targets in a message's {@code target} value; {@link Targets#NONE} for {@code null}.
	 * Throws {@link IllegalArgumentException} for anything malformed.
	 */
	static Targets decode(Object value)
	{
		if (value == null)
		{
			return Targets.NONE;
		}
		List<Target> targets = new ArrayList<>();
		if (value instanceof Collection<?>)
		{
			for (Object element : (Collection<?>) value)
			{
				targets.add(decodeTarget(element));
			}
		}
		else
		{
			targets.add(decodeTarget(value));
		}
		return Targets.of(targets);
	}

	private static Target decodeTarget(Object value)
	{
		if (!(value instanceof Map<?, ?>))
		{
			return Target.at(decodePoint(value));
		}
		Map<?, ?> map = (Map<?, ?>) value;
		int point = decodePoint(map.get(POINT));
		Object requirements = map.get(REQUIREMENTS);
		return requirements == null ? Target.at(point) : Target.requiring(point, decodeRequirements(requirements));
	}

	private static int decodePoint(Object value)
	{
		int packed = WorldPointUtil.UNDEFINED;
		if (value instanceof WorldPoint)
		{
			packed = WorldPointUtil.packWorldPoint((WorldPoint) value);
		}
		else if (value instanceof Integer)
		{
			packed = (Integer) value;
		}
		if (packed == WorldPointUtil.UNDEFINED)
		{
			throw new IllegalArgumentException("not a target point: " + value);
		}
		return packed;
	}

	/** One requirement value; see the class documentation for the accepted shapes. */
	static TransportItems decodeRequirements(Object value)
	{
		List<ItemRequirement> groups = new ArrayList<>();
		if (value instanceof List<?>)
		{
			List<?> all = (List<?>) value;
			if (all.isEmpty())
			{
				throw new IllegalArgumentException("a requirement list needs at least one item");
			}
			for (Object element : all)
			{
				groups.add(decodeGroup(element));
			}
		}
		else
		{
			groups.add(decodeGroup(value));
		}
		return new TransportItems(groups);
	}

	/** One "any of" group: an item id, a collection of item ids, or a map of item id to quantity. */
	private static ItemRequirement decodeGroup(Object value)
	{
		List<ItemRequirement.Branch> branches = new ArrayList<>();
		if (value instanceof Integer)
		{
			branches.add(branch(value, 1));
		}
		else if (value instanceof Map<?, ?>)
		{
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
			{
				Object quantity = entry.getValue();
				if (!(quantity instanceof Integer) || (Integer) quantity < 1)
				{
					throw new IllegalArgumentException("not an item quantity: " + quantity);
				}
				branches.add(branch(entry.getKey(), (Integer) quantity));
			}
		}
		else if (value instanceof Collection<?>)
		{
			for (Object id : (Collection<?>) value)
			{
				branches.add(branch(id, 1));
			}
		}
		else
		{
			throw new IllegalArgumentException("not an item requirement: " + value);
		}
		if (branches.isEmpty())
		{
			throw new IllegalArgumentException("an item group needs at least one item");
		}
		return new ItemRequirement(branches);
	}

	private static ItemRequirement.Branch branch(Object id, int quantity)
	{
		if (!(id instanceof Integer) || (Integer) id < 0)
		{
			throw new IllegalArgumentException("not an item id: " + id);
		}
		return new ItemRequirement.Branch(new int[] {(Integer) id}, NONE, NONE, quantity);
	}

	/**
	 * {@code target} as a message value: a {@link WorldPoint}, or a target map when it has required
	 * items. The requirements take their canonical shape, a {@code List} with one
	 * {@code Map<Integer, Integer>} of item id to quantity per group, which decodes to the same
	 * target.
	 */
	static Object encode(Target target)
	{
		WorldPoint point = WorldPointUtil.unpackWorldPoint(target.getPoint());
		if (!target.hasRequiredItems())
		{
			return point;
		}
		List<Map<Integer, Integer>> groups = new ArrayList<>();
		for (ItemRequirement group : target.getRequiredItems().getRequirements())
		{
			Map<Integer, Integer> alternatives = new LinkedHashMap<>();
			for (ItemRequirement.Branch branch : group.getBranches())
			{
				for (int id : branch.getItemIds())
				{
					alternatives.put(id, branch.getQuantity());
				}
			}
			groups.add(alternatives);
		}
		Map<String, Object> map = new HashMap<>();
		map.put(POINT, point);
		map.put(REQUIREMENTS, groups);
		return map;
	}

	/** The item ids of each group, for reporting the groups a target is missing. */
	static List<List<Integer>> itemIds(List<ItemRequirement> groups)
	{
		List<List<Integer>> ids = new ArrayList<>(groups.size());
		for (ItemRequirement group : groups)
		{
			List<Integer> groupIds = new ArrayList<>();
			for (ItemRequirement.Branch branch : group.getBranches())
			{
				for (int id : branch.getItemIds())
				{
					groupIds.add(id);
				}
			}
			ids.add(groupIds);
		}
		return ids;
	}
}
