package shortestpath.pathfinder;

import java.util.List;
import shortestpath.WorldPointUtil;
import shortestpath.transport.requirement.TransportItems;

/**
 * A tile a search may end on, with the items the player must hold when they arrive there: a spade
 * for a dig spot, an emote clue's outfit. A target the player picks has no required items; other
 * plugins can add them. Immutable.
 */
public final class Target
{
	/** The required items of a target that needs none. */
	public static final TransportItems NO_ITEMS = new TransportItems(List.of());

	private final int point;
	private final TransportItems requiredItems;

	private Target(int point, TransportItems requiredItems)
	{
		this.point = point;
		this.requiredItems = requiredItems;
	}

	/** A target at {@code point} that needs no items. */
	public static Target at(int point)
	{
		return new Target(point, NO_ITEMS);
	}

	/** A target at {@code point} that the player must reach holding {@code requiredItems}. */
	public static Target requiring(int point, TransportItems requiredItems)
	{
		if (requiredItems == null)
		{
			throw new NullPointerException("requiredItems");
		}
		return new Target(point, requiredItems);
	}

	/** The packed tile. */
	public int getPoint()
	{
		return point;
	}

	/** Every item group the player must hold on arrival; {@link #NO_ITEMS} when none. */
	public TransportItems getRequiredItems()
	{
		return requiredItems;
	}

	public boolean hasRequiredItems()
	{
		return requiredItems.size() > 0;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Target))
		{
			return false;
		}
		Target that = (Target) o;
		return point == that.point && requiredItems.equals(that.requiredItems);
	}

	@Override
	public int hashCode()
	{
		return 31 * point + requiredItems.hashCode();
	}

	@Override
	public String toString()
	{
		return WorldPointUtil.unpackWorldPoint(point) + (hasRequiredItems() ? " requiring " + requiredItems.size()
			+ " item group(s)" : "");
	}
}
