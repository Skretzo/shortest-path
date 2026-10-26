package shortestpath.pathfinder;

import java.util.List;
import shortestpath.WorldPointUtil;
import shortestpath.requirement.model.TransportItems;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * A tile a search may end on, with the items the player must hold when they arrive there: a spade
 * for a dig spot, an emote clue's outfit. A target the player picks has no required items; other
 * plugins can add them. Immutable.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public final class Target
{
	/** The required items of a target that needs none. */
	public static final TransportItems NO_ITEMS = new TransportItems(List.of());

	@Getter private final int point;
	@Getter private final TransportItems requiredItems;

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


	/** Every item group the player must hold on arrival; {@link #NO_ITEMS} when none. */


	public boolean hasRequiredItems()
	{
		return requiredItems.size() > 0;
	}

	@Override
	public String toString()
	{
		return WorldPointUtil.unpackWorldPoint(point) + (hasRequiredItems() ? " requiring " + requiredItems.size()
			+ " item group(s)" : "");
	}
}
