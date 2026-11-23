package shortestpath.requirement;

import java.util.*;
import shortestpath.transport.ResourceLoader;

/**
 * The banks a player prefers, stored by name in the {@code preferredBanks} setting as a
 * comma-separated list. A bank is every {@code bank.tsv} tile sharing an Info name, so a
 * preference survives edits to the tiles. Routing charges a visit to any other bank the
 * {@code costOtherBanks} setting on top of the bank visit cost.
 */
public final class PreferredBanks
{
	private static final String SEPARATOR = ",";

	private PreferredBanks()
	{
	}

	private static final class Names
	{
		static final Map<Integer, String> BY_TILE = Collections.unmodifiableMap(ResourceLoader.bankNames());
		static final List<String> SORTED = List.copyOf(new TreeSet<>(BY_TILE.values()));
	}

	/** Every bank's name, by packed tile. */
	public static Map<Integer, String> namesByTile()
	{
		return Names.BY_TILE;
	}

	/** Every bank's name once, alphabetically. */
	public static List<String> names()
	{
		return Names.SORTED;
	}

	/** The bank names in a stored setting; blank and unset read as none. */
	public static Set<String> parse(Object stored)
	{
		Set<String> names = new TreeSet<>();
		if (stored instanceof String)
		{
			for (String name : ((String) stored).split(SEPARATOR))
			{
				if (!name.isBlank())
				{
					names.add(name.trim());
				}
			}
		}
		return names;
	}

	public static String format(Collection<String> names)
	{
		return String.join(SEPARATOR, new TreeSet<>(names));
	}

	/** The tiles of the named banks in {@code namesByTile}. */
	public static Set<Integer> tiles(Set<String> preferred, Map<Integer, String> namesByTile)
	{
		Set<Integer> tiles = new HashSet<>();
		if (!preferred.isEmpty())
		{
			namesByTile.forEach((tile, name) ->
			{
				if (preferred.contains(name))
				{
					tiles.add(tile);
				}
			});
		}
		return tiles;
	}
}
