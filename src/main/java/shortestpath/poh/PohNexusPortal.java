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
	APE_ATOLL_DUNGEON("Ape Atoll Dungeon", PortalGroup.ARCEUUS, "Ape Atoll Dungeon Portal"),
	ANNAKARL("Annakarl", PortalGroup.ANCIENT, "Annakarl Portal"),
	ARCEUUS_LIBRARY("Arceuus Library", PortalGroup.ARCEUUS, "Arceuus Library Portal"),
	ARDOUGNE("Ardougne", PortalGroup.STANDARD, "Ardougne Portal"),
	BARBARIAN_OUTPOST("Barbarian Outpost", PortalGroup.LUNAR, "Barbarian Outpost Portal"),
	BARROWS("Barrows", PortalGroup.ARCEUUS, "Barrows Portal"),
	BATTLEFRONT("Battlefront", PortalGroup.ARCEUUS, "Battlefront Portal"),
	CAMELOT("Camelot / Seers' Village", PortalGroup.STANDARD, "Camelot Portal", "Seers' Village Portal"),
	CARRALLANGER("Carrallanger", PortalGroup.ANCIENT, "Carrallanger Portal"),
	CATHERBY("Catherby", PortalGroup.LUNAR, "Catherby Portal"),
	CEMETERY("Cemetery", PortalGroup.ARCEUUS, "Cemetery Portal"),
	CIVITAS_ILLA_FORTIS("Civitas illa Fortis", PortalGroup.STANDARD, "Civitas illa Fortis Portal"),
	DAREEYAK("Dareeyak", PortalGroup.ANCIENT, "Dareeyak Portal"),
	DRAYNOR_MANOR("Draynor Manor", PortalGroup.ARCEUUS, "Draynor Manor Portal"),
	FALADOR("Falador", PortalGroup.STANDARD, "Falador Portal"),
	FENKENSTRAINS_CASTLE("Fenkenstrain's Castle", PortalGroup.ARCEUUS, "Fenkenstrain's Castle Portal"),
	FISHING_GUILD("Fishing Guild", PortalGroup.LUNAR, "Fishing Guild Portal"),
	GHORROCK("Ghorrock", PortalGroup.ANCIENT, "Ghorrock Portal"),
	HARMONY_ISLAND("Harmony Island", PortalGroup.ARCEUUS, "Harmony Island Portal"),
	ICE_PLATEAU("Ice Plateau", PortalGroup.LUNAR, "Ice Plateau Portal"),
	KHARYRLL("Kharyrll", PortalGroup.ANCIENT, "Kharyrll Portal"),
	KOUREND("Kourend", PortalGroup.STANDARD, "Kourend Portal"),
	LASSAR("Lassar", PortalGroup.ANCIENT, "Lassar Portal"),
	LUMBRIDGE("Lumbridge", PortalGroup.STANDARD, "Lumbridge Portal"),
	LUNAR_ISLE("Lunar Isle", PortalGroup.LUNAR, "Lunar Isle Portal"),
	MARIM("Marim", PortalGroup.STANDARD, "Marim Portal"),
	MIND_ALTAR("Mind Altar", PortalGroup.ARCEUUS, "Mind Altar Portal"),
	OURANIA("Ourania", PortalGroup.LUNAR, "Ourania Portal"),
	PADDEWWA("Paddewwa", PortalGroup.ANCIENT, "Paddewwa Portal"),
	PORT_KHAZARD("Port Khazard", PortalGroup.LUNAR, "Port Khazard Portal"),
	RESPAWN(
		"Respawn",
		PortalGroup.ARCEUUS,
		"Respawn Portal (Lumbridge)",
		"Respawn Portal (Falador)",
		"Respawn Portal (Camelot)",
		"Respawn Portal (Edgeville)",
		"Respawn Portal (Prifddinas)",
		"Respawn Portal (Ferox Enclave)",
		"Respawn Portal (Kourend Castle)",
		"Respawn Portal (Civitas illa Fortis)"
	),
	SALVE_GRAVEYARD("Salve Graveyard", PortalGroup.ARCEUUS, "Salve Graveyard Portal"),
	SENNTISTEN("Senntisten", PortalGroup.ANCIENT, "Senntisten Portal"),
	TROLLHEIM("Trollheim", PortalGroup.STANDARD, "Trollheim Portal"),
	TROLL_STRONGHOLD("Troll Stronghold", PortalGroup.OTHER, "Troll Stronghold Portal"),
	VARROCK("Varrock / Grand Exchange", PortalGroup.STANDARD, "Varrock Portal", "Grand Exchange Portal"),
	WATERBIRTH_ISLAND("Waterbirth Island", PortalGroup.LUNAR, "Waterbirth Island Portal"),
	WATCHTOWER("Watchtower / Yanille", PortalGroup.STANDARD, "Watchtower Portal", "Yanille Portal"),
	WEISS("Weiss", PortalGroup.OTHER, "Weiss Portal"),
	WEST_ARDOUGNE("West Ardougne", PortalGroup.ARCEUUS, "West Ardougne Portal");

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
	private final PortalGroup group;
	private final List<String> displayInfos;

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

	PohNexusPortal(String label, PortalGroup group, String... displayInfos)
	{
		this.name = name;
		this.label = label;
		this.group = group;
		this.displayInfos = List.of(displayInfos);
	}

	public PortalGroup group()
	{
		return group;
	}

	public List<String> getDisplayInfos()
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
