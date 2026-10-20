package shortestpath;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Item-name to item-id variation table, loaded from a bundled resource.
 *
 * <p>The table lives in {@code item-variations.tsv}: name, the item ids the
 * name covers, the staff alternatives for rune names, and the offhand (tome)
 * alternatives. Columns after the first two may be empty.
 */
public final class ItemVariations
{
	private static final Map<String, ItemVariations> BY_NAME = new HashMap<>();
	private static final Map<String, int[]> STAVES = new HashMap<>();
	private static final Map<String, int[]> OFFHANDS = new HashMap<>();

	static
	{
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			ItemVariations.class.getResourceAsStream("/item-variations.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] cols = line.split("\t", -1);
				ItemVariations v = new ItemVariations(cols[0], parseIds(cols[1]));
				BY_NAME.put(v.name, v);
				if (cols.length > 2 && !cols[2].isEmpty())
				{
					STAVES.put(v.name, parseIds(cols[2]));
				}
				if (cols.length > 3 && !cols[3].isEmpty())
				{
					OFFHANDS.put(v.name, parseIds(cols[3]));
				}
			}
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load item-variations.tsv", e);
		}
	}

	private static int[] parseIds(String col)
	{
		String[] parts = col.split(",");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			out[i] = Integer.parseInt(parts[i].trim());
		}
		return out;
	}

	private final String name;
	private final int[] ids;

	private ItemVariations(String name, int[] ids)
	{
		this.name = name;
		this.ids = ids;
	}

	public String name()
	{
		return name;
	}

	public int[] getIds()
	{
		return ids;
	}

	public static int[] staves(ItemVariations itemVariation)
	{
		return itemVariation == null ? null : STAVES.get(itemVariation.name);
	}

	public static int[] offhands(ItemVariations itemVariation)
	{
		return itemVariation == null ? null : OFFHANDS.get(itemVariation.name);
	}

	public static ItemVariations fromName(String name)
	{
		return BY_NAME.get(name);
	}

	public static final ItemVariations DRAMEN_STAFF = BY_NAME.get("DRAMEN_STAFF");
}
