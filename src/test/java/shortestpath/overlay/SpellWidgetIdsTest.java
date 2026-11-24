package shortestpath.overlay;

import java.io.IOException;
import java.util.*;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.Util;

/**
 * Every widget id in {@code spell-widget-ids.tsv} is the {@link InterfaceID.MagicSpellbook}
 * gameval for that spell. The table below names each spell's gameval, so a RuneLite update that
 * renames or removes one stops this test compiling, and a TSV id pointing at the wrong spell fails
 * it. Which spells need a row is checked against the transports in
 * {@code TransportDataLintTest.testSpellbookOverlayMatchesSpellTransports}.
 */
public class SpellWidgetIdsTest
{
	private static final Map<String, Integer> GAMEVALS = Map.ofEntries(
		// Standard
		Map.entry("Lumbridge Home Teleport", InterfaceID.MagicSpellbook.TELEPORT_HOME_STANDARD),
		Map.entry("Varrock Teleport", InterfaceID.MagicSpellbook.VARROCK_TELEPORT),
		Map.entry("Varrock Teleport: Grand Exchange", InterfaceID.MagicSpellbook.VARROCK_TELEPORT),
		Map.entry("Lumbridge Teleport", InterfaceID.MagicSpellbook.LUMBRIDGE_TELEPORT),
		Map.entry("Falador Teleport", InterfaceID.MagicSpellbook.FALADOR_TELEPORT),
		Map.entry("Teleport to House", InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE),
		Map.entry("Teleport to House (Inside)", InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE),
		Map.entry("Teleport to House (Outside)", InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE),
		Map.entry("Camelot Teleport", InterfaceID.MagicSpellbook.CAMELOT_TELEPORT),
		Map.entry("Camelot Teleport: Seers'", InterfaceID.MagicSpellbook.CAMELOT_TELEPORT),
		Map.entry("Kourend Castle Teleport", InterfaceID.MagicSpellbook.KOUREND_TELEPORT),
		Map.entry("Ardougne Teleport", InterfaceID.MagicSpellbook.ARDOUGNE_TELEPORT),
		Map.entry("Civitas illa Fortis Teleport", InterfaceID.MagicSpellbook.FORTIS_TELEPORT),
		Map.entry("Watchtower Teleport", InterfaceID.MagicSpellbook.WATCHTOWER_TELEPORT),
		Map.entry("Watchtower Teleport: Yanille", InterfaceID.MagicSpellbook.WATCHTOWER_TELEPORT),
		Map.entry("Trollheim Teleport", InterfaceID.MagicSpellbook.TROLLHEIM_TELEPORT),
		Map.entry("Ape Atoll Teleport", InterfaceID.MagicSpellbook.APE_TELEPORT),
		// Ancient
		Map.entry("Edgeville Home Teleport", InterfaceID.MagicSpellbook.TELEPORT_HOME_ZAROS),
		Map.entry("Paddewwa Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT1),
		Map.entry("Senntisten Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT2),
		Map.entry("Kharyrll Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT3),
		Map.entry("Lassar Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT4),
		Map.entry("Dareeyak Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT5),
		Map.entry("Carrallanger Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT6),
		Map.entry("Annakarl Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT7),
		Map.entry("Ghorrock Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT8),
		// Lunar
		Map.entry("Lunar Home Teleport", InterfaceID.MagicSpellbook.TELEPORT_HOME_LUNAR),
		Map.entry("Moonclan Teleport", InterfaceID.MagicSpellbook.TELE_MOONCLAN),
		Map.entry("Ourania Teleport", InterfaceID.MagicSpellbook.OURANIA_TELEPORT),
		Map.entry("Waterbirth Teleport", InterfaceID.MagicSpellbook.TELE_WATERBIRTH),
		Map.entry("Barbarian Teleport", InterfaceID.MagicSpellbook.TELE_BARB_OUT),
		Map.entry("Khazard Teleport", InterfaceID.MagicSpellbook.TELE_KHAZARD),
		Map.entry("Fishing Guild Teleport", InterfaceID.MagicSpellbook.TELE_FISH),
		Map.entry("Catherby Teleport", InterfaceID.MagicSpellbook.TELE_CATHER),
		Map.entry("Ice Plateau Teleport", InterfaceID.MagicSpellbook.TELE_GHORROCK),
		Map.entry("Spellbook Swap", InterfaceID.MagicSpellbook.SPELLBOOK_SWAP),
		// Arceuus
		Map.entry("Arceuus Home Teleport", InterfaceID.MagicSpellbook.TELEPORT_HOME_ARCEUUS),
		Map.entry("Arceuus Library Teleport", InterfaceID.MagicSpellbook.TELEPORT_ARCEUUS_LIBRARY),
		Map.entry("Draynor Manor Teleport", InterfaceID.MagicSpellbook.TELEPORT_DRAYNOR_MANOR),
		Map.entry("Battlefront Teleport", InterfaceID.MagicSpellbook.TELEPORT_BATTLEFRONT),
		Map.entry("Mind Altar Teleport", InterfaceID.MagicSpellbook.TELEPORT_MIND_ALTAR),
		Map.entry("Respawn Teleport", InterfaceID.MagicSpellbook.TELEPORT_RESPAWN),
		Map.entry("Salve Graveyard Teleport", InterfaceID.MagicSpellbook.TELEPORT_SALVE_GRAVEYARD),
		Map.entry("Fenkenstrain's Castle Teleport", InterfaceID.MagicSpellbook.TELEPORT_FENKENSTRAIN_CASTLE),
		Map.entry("West Ardougne Teleport", InterfaceID.MagicSpellbook.TELEPORT_WEST_ARDOUGNE),
		Map.entry("Harmony Island Teleport", InterfaceID.MagicSpellbook.TELEPORT_HARMONY_ISLAND),
		Map.entry("Cemetery Teleport", InterfaceID.MagicSpellbook.TELEPORT_CEMETERY),
		Map.entry("Barrows Teleport", InterfaceID.MagicSpellbook.TELEPORT_BARROWS));

	@Test
	public void everyWidgetIdIsItsSpellsGameval() throws IOException
	{
		Map<String, Integer> tsv = new TreeMap<>();
		Set<String> duplicates = new TreeSet<>();
		Util.each("/spell-widget-ids.tsv", (line, c) ->
		{
			if (tsv.put(c[0], Integer.valueOf(c[1])) != null)
			{
				duplicates.add(c[0]);
			}
		});
		Assert.assertTrue("spell-widget-ids.tsv names listed more than once: " + duplicates,
			duplicates.isEmpty());

		List<String> wrongIds = new ArrayList<>();
		for (Map.Entry<String, Integer> row : tsv.entrySet())
		{
			Integer gameval = GAMEVALS.get(row.getKey());
			if (gameval != null && !gameval.equals(row.getValue()))
			{
				wrongIds.add(row.getKey() + " is " + row.getValue() + ", gameval is " + gameval);
			}
		}
		Assert.assertTrue("spell-widget-ids.tsv ids that differ from InterfaceID.MagicSpellbook: "
			+ wrongIds, wrongIds.isEmpty());

		Set<String> missingFromTable = new TreeSet<>(tsv.keySet());
		missingFromTable.removeAll(GAMEVALS.keySet());
		Assert.assertTrue("spell-widget-ids.tsv rows with no gameval in SpellWidgetIdsTest: "
			+ missingFromTable, missingFromTable.isEmpty());

		Set<String> missingFromTsv = new TreeSet<>(GAMEVALS.keySet());
		missingFromTsv.removeAll(tsv.keySet());
		Assert.assertTrue("SpellWidgetIdsTest gamevals with no row in spell-widget-ids.tsv: "
			+ missingFromTsv, missingFromTsv.isEmpty());
	}
}
