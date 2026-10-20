package shortestpath;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.experimental.Accessors;

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

	@Getter @Accessors(fluent = true) private final String name;
	@Getter private final int[] ids;

	private ItemVariations(String name, int[] ids)
	{
		this.name = name;
		this.ids = ids;
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

	public static final ItemVariations AIR_RUNE = fromName("AIR_RUNE");
	public static final ItemVariations ARDOUGNE_CLOAK = fromName("ARDOUGNE_CLOAK");
	public static final ItemVariations ASTRAL_RUNE = fromName("ASTRAL_RUNE");
	public static final ItemVariations AXE = fromName("AXE");
	public static final ItemVariations BANANA = fromName("BANANA");
	public static final ItemVariations BLOOD_RUNE = fromName("BLOOD_RUNE");
	public static final ItemVariations BROWN_APRON = fromName("BROWN_APRON");
	public static final ItemVariations BRYOPHYTAS_STAFF = fromName("BRYOPHYTAS_STAFF");
	public static final ItemVariations CAPESLOT = fromName("CAPESLOT");
	public static final ItemVariations CLIMBING_BOOTS = fromName("CLIMBING_BOOTS");
	public static final ItemVariations COINS = fromName("COINS");
	public static final ItemVariations CROSSBOW = fromName("CROSSBOW");
	public static final ItemVariations DRAMEN_STAFF = fromName("DRAMEN_STAFF");
	public static final ItemVariations DUST_BATTLESTAFF = fromName("DUST_BATTLESTAFF");
	public static final ItemVariations DUST_RUNE = fromName("DUST_RUNE");
	public static final ItemVariations DUSTY_KEY = fromName("DUSTY_KEY");
	public static final ItemVariations EARTH_RUNE = fromName("EARTH_RUNE");
	public static final ItemVariations ECTO_TOKEN = fromName("ECTO_TOKEN");
	public static final ItemVariations FIRE_RUNE = fromName("FIRE_RUNE");
	public static final ItemVariations GAMES_NECKLACE = fromName("GAMES_NECKLACE");
	public static final ItemVariations GLOWING_FUNGUS = fromName("GLOWING_FUNGUS");
	public static final ItemVariations HEADSLOT = fromName("HEADSLOT");
	public static final ItemVariations LAVA_BATTLESTAFF = fromName("LAVA_BATTLESTAFF");
	public static final ItemVariations LAVA_RUNE = fromName("LAVA_RUNE");
	public static final ItemVariations LAW_RUNE = fromName("LAW_RUNE");
	public static final ItemVariations MACHETE = fromName("MACHETE");
	public static final ItemVariations MAX_CAPE = fromName("MAX_CAPE");
	public static final ItemVariations MAX_HOOD = fromName("MAX_HOOD");
	public static final ItemVariations MAZE_KEY = fromName("MAZE_KEY");
	public static final ItemVariations MIND_RUNE = fromName("MIND_RUNE");
	public static final ItemVariations MIST_BATTLESTAFF = fromName("MIST_BATTLESTAFF");
	public static final ItemVariations MIST_RUNE = fromName("MIST_RUNE");
	public static final ItemVariations MITH_GRAPPLE = fromName("MITH_GRAPPLE");
	public static final ItemVariations MUD_BATTLESTAFF = fromName("MUD_BATTLESTAFF");
	public static final ItemVariations MUD_RUNE = fromName("MUD_RUNE");
	public static final ItemVariations MYSTIC_DUST_STAFF = fromName("MYSTIC_DUST_STAFF");
	public static final ItemVariations MYSTIC_LAVA_STAFF = fromName("MYSTIC_LAVA_STAFF");
	public static final ItemVariations MYSTIC_MIST_STAFF = fromName("MYSTIC_MIST_STAFF");
	public static final ItemVariations MYSTIC_MUD_STAFF = fromName("MYSTIC_MUD_STAFF");
	public static final ItemVariations MYSTIC_SMOKE_STAFF = fromName("MYSTIC_SMOKE_STAFF");
	public static final ItemVariations MYSTIC_STEAM_STAFF = fromName("MYSTIC_STEAM_STAFF");
	public static final ItemVariations NATURE_RUNE = fromName("NATURE_RUNE");
	public static final ItemVariations PICKAXE = fromName("PICKAXE");
	public static final ItemVariations ROPE = fromName("ROPE");
	public static final ItemVariations SHANTAY_PASS = fromName("SHANTAY_PASS");
	public static final ItemVariations SKAVID_MAP = fromName("SKAVID_MAP");
	public static final ItemVariations SLASH = fromName("SLASH");
	public static final ItemVariations SMOKE_BATTLESTAFF = fromName("SMOKE_BATTLESTAFF");
	public static final ItemVariations SMOKE_RUNE = fromName("SMOKE_RUNE");
	public static final ItemVariations SOUL_RUNE = fromName("SOUL_RUNE");
	public static final ItemVariations STAFF_OF_AIR = fromName("STAFF_OF_AIR");
	public static final ItemVariations STAFF_OF_EARTH = fromName("STAFF_OF_EARTH");
	public static final ItemVariations STAFF_OF_FIRE = fromName("STAFF_OF_FIRE");
	public static final ItemVariations STAFF_OF_WATER = fromName("STAFF_OF_WATER");
	public static final ItemVariations STEAM_BATTLESTAFF = fromName("STEAM_BATTLESTAFF");
	public static final ItemVariations STEAM_RUNE = fromName("STEAM_RUNE");
	public static final ItemVariations TOME_OF_EARTH = fromName("TOME_OF_EARTH");
	public static final ItemVariations TOME_OF_FIRE = fromName("TOME_OF_FIRE");
	public static final ItemVariations TOME_OF_WATER = fromName("TOME_OF_WATER");
	public static final ItemVariations WATER_RUNE = fromName("WATER_RUNE");
}