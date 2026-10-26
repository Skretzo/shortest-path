package shortestpath.poh;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.experimental.Accessors;

public enum PohNexusPortal
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
	}

	PohNexusPortal(String label, PortalGroup group, String... displayInfos)
	{
		this.label = label;
		this.group = group;
		this.displayInfos = List.of(displayInfos);
	}

	public List<String> getDisplayInfos()
	{
		return displayInfos;
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
}
