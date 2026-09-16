package shortestpath.transport;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum PohNexusPortal
{
	ANNAKARL("Annakarl", "Annakarl Portal"),
	ARCEUUS_LIBRARY("Arceuus Library", "Arceuus Library Portal"),
	ARDOUGNE("Ardougne", "Ardougne Portal"),
	BARBARIAN_OUTPOST("Barbarian Outpost", "Barbarian Outpost Portal"),
	BARROWS("Barrows", "Barrows Portal"),
	BATTLEFRONT("Battlefront", "Battlefront Portal"),
	CAMELOT("Camelot / Seers' Village", "Camelot Portal", "Seers' Village Portal"),
	CARRALLANGER("Carrallanger", "Carrallanger Portal"),
	CATHERBY("Catherby", "Catherby Portal"),
	CEMETERY("Cemetery", "Cemetery Portal"),
	CIVITAS_ILLA_FORTIS("Civitas illa Fortis", "Civitas illa Fortis Portal"),
	DAREEYAK("Dareeyak", "Dareeyak Portal"),
	DRAYNOR_MANOR("Draynor Manor", "Draynor Manor Portal"),
	FALADOR("Falador", "Falador Portal"),
	FENKENSTRAINS_CASTLE("Fenkenstrain's Castle", "Fenkenstrain's Castle Portal"),
	FISHING_GUILD("Fishing Guild", "Fishing Guild Portal"),
	GHORROCK("Ghorrock", "Ghorrock Portal"),
	HARMONY_ISLAND("Harmony Island", "Harmony Island Portal"),
	ICE_PLATEAU("Ice Plateau", "Ice Plateau Portal"),
	KHARYRLL("Kharyrll", "Kharyrll Portal"),
	KOUREND("Kourend", "Kourend Portal"),
	LASSAR("Lassar", "Lassar Portal"),
	LUMBRIDGE("Lumbridge", "Lumbridge Portal"),
	LUNAR_ISLE("Lunar Isle", "Lunar Isle Portal"),
	MARIM("Marim", "Marim Portal"),
	MIND_ALTAR("Mind Altar", "Mind Altar Portal"),
	OURANIA("Ourania", "Ourania Portal"),
	PADDEWWA("Paddewwa", "Paddewwa Portal"),
	PORT_KHAZARD("Port Khazard", "Port Khazard Portal"),
	RESPAWN(
		"Respawn",
		"Respawn Portal (Lumbridge)",
		"Respawn Portal (Falador)",
		"Respawn Portal (Camelot)",
		"Respawn Portal (Edgeville)",
		"Respawn Portal (Prifddinas)",
		"Respawn Portal (Ferox Enclave)",
		"Respawn Portal (Kourend Castle)",
		"Respawn Portal (Civitas illa Fortis)"
	),
	SALVE_GRAVEYARD("Salve Graveyard", "Salve Graveyard Portal"),
	SENNTISTEN("Senntisten", "Senntisten Portal"),
	TROLLHEIM("Trollheim", "Trollheim Portal"),
	TROLL_STRONGHOLD("Troll Stronghold", "Troll Stronghold Portal"),
	VARROCK("Varrock / Grand Exchange", "Varrock Portal", "Grand Exchange Portal"),
	WATERBIRTH_ISLAND("Waterbirth Island", "Waterbirth Island Portal"),
	WATCHTOWER("Watchtower / Yanille", "Watchtower Portal", "Yanille Portal"),
	WEISS("Weiss", "Weiss Portal"),
	WEST_ARDOUGNE("West Ardougne", "West Ardougne Portal");

	private static final Map<String, PohNexusPortal> BY_DISPLAY_INFO;
	private final String label;
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

	PohNexusPortal(String label, String... displayInfos)
	{
		this.label = label;
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
