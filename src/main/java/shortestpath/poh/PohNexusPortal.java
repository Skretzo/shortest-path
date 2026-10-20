package shortestpath.poh;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * POH nexus portal labels and their panel display strings, loaded from a
 * bundled resource ({@code poh-nexus-portals.tsv}: name, label, then the
 * '|' separated displayInfo strings the portal menu reports).
 */
public final class PohNexusPortal
{
	private static final Map<String, PohNexusPortal> BY_NAME = new LinkedHashMap<>();
	private static final Map<String, PohNexusPortal> BY_DISPLAY_INFO = new HashMap<>();

	static
	{
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			PohNexusPortal.class.getResourceAsStream("/poh-nexus-portals.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				PohNexusPortal portal = new PohNexusPortal(c[0], c[1], List.of(c[2].split("\\|")));
				BY_NAME.put(c[0], portal);
				for (String displayInfo : portal.displayInfos)
				{
					if (BY_DISPLAY_INFO.put(displayInfo, portal) != null)
					{
						throw new IllegalStateException("Duplicate POH portal display info: " + displayInfo);
					}
				}
			}
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load poh-nexus-portals.tsv", e);
		}
	}

	@Getter @Accessors(fluent = true) private final String name;
	private final String label;
	@Getter private final List<String> displayInfos;

	private PohNexusPortal(String name, String label, List<String> displayInfos)
	{
		this.name = name;
		this.label = label;
		this.displayInfos = displayInfos;
	}





	public static Collection<PohNexusPortal> values()
	{
		return Collections.unmodifiableCollection(BY_NAME.values());
	}

	public static PohNexusPortal fromName(String name)
	{
		return BY_NAME.get(name);
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
	public static final PohNexusPortal APE_ATOLL_DUNGEON = fromName("APE_ATOLL_DUNGEON");
	public static final PohNexusPortal ANNAKARL = fromName("ANNAKARL");
	public static final PohNexusPortal ARCEUUS_LIBRARY = fromName("ARCEUUS_LIBRARY");
	public static final PohNexusPortal ARDOUGNE = fromName("ARDOUGNE");
	public static final PohNexusPortal BARBARIAN_OUTPOST = fromName("BARBARIAN_OUTPOST");
	public static final PohNexusPortal BARROWS = fromName("BARROWS");
	public static final PohNexusPortal BATTLEFRONT = fromName("BATTLEFRONT");
	public static final PohNexusPortal CAMELOT = fromName("CAMELOT");
	public static final PohNexusPortal CARRALLANGER = fromName("CARRALLANGER");
	public static final PohNexusPortal CATHERBY = fromName("CATHERBY");
	public static final PohNexusPortal CEMETERY = fromName("CEMETERY");
	public static final PohNexusPortal CIVITAS_ILLA_FORTIS = fromName("CIVITAS_ILLA_FORTIS");
	public static final PohNexusPortal DAREEYAK = fromName("DAREEYAK");
	public static final PohNexusPortal DRAYNOR_MANOR = fromName("DRAYNOR_MANOR");
	public static final PohNexusPortal FALADOR = fromName("FALADOR");
	public static final PohNexusPortal FENKENSTRAINS_CASTLE = fromName("FENKENSTRAINS_CASTLE");
	public static final PohNexusPortal FISHING_GUILD = fromName("FISHING_GUILD");
	public static final PohNexusPortal GHORROCK = fromName("GHORROCK");
	public static final PohNexusPortal HARMONY_ISLAND = fromName("HARMONY_ISLAND");
	public static final PohNexusPortal ICE_PLATEAU = fromName("ICE_PLATEAU");
	public static final PohNexusPortal KHARYRLL = fromName("KHARYRLL");
	public static final PohNexusPortal KOUREND = fromName("KOUREND");
	public static final PohNexusPortal LASSAR = fromName("LASSAR");
	public static final PohNexusPortal LUMBRIDGE = fromName("LUMBRIDGE");
	public static final PohNexusPortal LUNAR_ISLE = fromName("LUNAR_ISLE");
	public static final PohNexusPortal MARIM = fromName("MARIM");
	public static final PohNexusPortal MIND_ALTAR = fromName("MIND_ALTAR");
	public static final PohNexusPortal OURANIA = fromName("OURANIA");
	public static final PohNexusPortal PADDEWWA = fromName("PADDEWWA");
	public static final PohNexusPortal PORT_KHAZARD = fromName("PORT_KHAZARD");
	public static final PohNexusPortal RESPAWN = fromName("RESPAWN");
	public static final PohNexusPortal SALVE_GRAVEYARD = fromName("SALVE_GRAVEYARD");
	public static final PohNexusPortal SENNTISTEN = fromName("SENNTISTEN");
	public static final PohNexusPortal TROLLHEIM = fromName("TROLLHEIM");
	public static final PohNexusPortal TROLL_STRONGHOLD = fromName("TROLL_STRONGHOLD");
	public static final PohNexusPortal VARROCK = fromName("VARROCK");
	public static final PohNexusPortal WATERBIRTH_ISLAND = fromName("WATERBIRTH_ISLAND");
	public static final PohNexusPortal WATCHTOWER = fromName("WATCHTOWER");
	public static final PohNexusPortal WEISS = fromName("WEISS");
	public static final PohNexusPortal WEST_ARDOUGNE = fromName("WEST_ARDOUGNE");
}
