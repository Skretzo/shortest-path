package shortestpath.requirement;

import java.util.*;
import java.util.stream.Collectors;
import shortestpath.transport.ResourceLoader;

/**
 * The banks a player prefers, stored by name in the {@code preferredBanks} setting as a
 * comma-separated list. A bank is every {@code bank.tsv} tile sharing an Info name, so a
 * preference survives edits to the tiles. Routing charges a visit to any other bank the
 * {@code costOtherBanks} setting on top of the bank visit cost.
 */
public final class PreferredBanks
{
	private static final Map<Integer, String> NAMES_BY_TILE = ResourceLoader.bankNames();
	/** Every bank's name once, alphabetically. */
	public static final List<String> NAMES = List.copyOf(new TreeSet<>(NAMES_BY_TILE.values()));

	private PreferredBanks()
	{
	}

	/** The bank names in a stored setting; blank and unset read as none. */
	public static Set<String> parse(Object stored)
	{
		return stored instanceof String ? Arrays.stream(((String) stored).split(","))
			.map(String::trim).filter(name -> !name.isEmpty())
			.collect(Collectors.toCollection(TreeSet::new)) : new TreeSet<>();
	}

	public static String format(Collection<String> names)
	{
		return String.join(",", new TreeSet<>(names));
	}

	/** The tiles of the named banks. */
	public static Set<Integer> tiles(Set<String> preferred)
	{
		return NAMES_BY_TILE.entrySet().stream().filter(entry -> preferred.contains(entry.getValue()))
			.map(Map.Entry::getKey).collect(Collectors.toSet());
	}
}
