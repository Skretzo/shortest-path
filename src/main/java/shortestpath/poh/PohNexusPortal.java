package shortestpath.poh;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.experimental.Accessors;
import shortestpath.Util;

public enum PohNexusPortal
{
	APE_ATOLL_DUNGEON,
	ANNAKARL,
	ARCEUUS_LIBRARY,
	ARDOUGNE,
	BARBARIAN_OUTPOST,
	BARROWS,
	BATTLEFRONT,
	CAMELOT,
	CARRALLANGER,
	CATHERBY,
	CEMETERY,
	CIVITAS_ILLA_FORTIS,
	DAREEYAK,
	DRAYNOR_MANOR,
	FALADOR,
	FENKENSTRAINS_CASTLE,
	FISHING_GUILD,
	GHORROCK,
	HARMONY_ISLAND,
	ICE_PLATEAU,
	KHARYRLL,
	KOUREND,
	LASSAR,
	LUMBRIDGE,
	LUNAR_ISLE,
	MARIM,
	MIND_ALTAR,
	OURANIA,
	PADDEWWA,
	PORT_KHAZARD,
	RESPAWN,
	SALVE_GRAVEYARD,
	SENNTISTEN,
	TROLLHEIM,
	TROLL_STRONGHOLD,
	VARROCK,
	WATERBIRTH_ISLAND,
	WATCHTOWER,
	WEISS,
	WEST_ARDOUGNE;

	/**
	 * Spellbook buckets the config panel drills the Portal Nexus list down
	 * into — display-only grouping for the sidebar; {@code PohService}
	 * consumption and the {@code pohNexusPortals} config set are unchanged.
	 * {@link #OTHER} holds the basalt teleports that come from items rather
	 * than spells (Troll Stronghold, Weiss).
	 */
	public enum PortalGroup
	{
		STANDARD,
		ANCIENT,
		LUNAR,
		ARCEUUS,
		OTHER
	}

	private static final Map<String, PohNexusPortal> BY_DISPLAY_INFO;
	private final String label;
	@Getter @Accessors(fluent = true) private final PortalGroup group;
	private final List<String> displayInfos;

	static
	{
		Map<String, PohNexusPortal> lookup = new HashMap<>();
		for (PohNexusPortal portal : values())
		{
			for (String displayInfo : portal.displayInfos)
			{
				if (lookup.put(displayInfo, portal) != null)
				{
					throw new IllegalStateException("Duplicate POH portal display info: " + displayInfo);
				}
			}
		}
		BY_DISPLAY_INFO = Collections.unmodifiableMap(lookup);
		// The constructor fails loudly on a constant with no TSV row; this
		// pass fails loudly on a TSV row whose name matches no constant.
		for (String name : PortalRows.BY_NAME.keySet())
		{
			valueOf(name);
		}
	}

	PohNexusPortal()
	{
		String[] cols = PortalRows.BY_NAME.get(name());
		if (cols == null)
		{
			throw new IllegalStateException("no poh-nexus-portals.tsv row for " + name());
		}
		this.label = cols[1];
		this.group = PortalGroup.valueOf(cols[2]);
		this.displayInfos = List.of(cols[3].split(","));
	}

	public static PohNexusPortal fromDisplayInfo(String displayInfo)
	{
		return BY_DISPLAY_INFO.get(displayInfo);
	}

	@Override
	public String toString()
	{
		return label;
	}

	/**
	 * Row table loaded from the bundled {@code poh-nexus-portals.tsv} — a
	 * nested holder so the load happens on first constant construction; the
	 * enum's own static initializers would run too late (after every
	 * constant is already built).
	 */
	private static final class PortalRows
	{
		private static final Map<String, String[]> BY_NAME = load();

		private static Map<String, String[]> load()
		{
			Map<String, String[]> rows = new HashMap<>();
			try
			{
				Util.each("/poh-nexus-portals.tsv", (line, cols) ->
				{
					if (cols.length < 4)
					{
						throw new IllegalStateException("malformed row: " + line);
					}
					rows.put(cols[0], cols);
				});
			}
			catch (Exception e)
			{
				throw new IllegalStateException("failed to load poh-nexus-portals.tsv", e);
			}
			return Collections.unmodifiableMap(rows);
		}
	}
}
