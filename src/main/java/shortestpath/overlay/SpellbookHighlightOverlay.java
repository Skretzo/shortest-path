package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import shortestpath.ShortestPathPlugin;
import shortestpath.pathfinder.PathStep;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class SpellbookHighlightOverlay extends AbstractHighlightOverlay
{
	private static final Map<String, Integer> SPELL_WIDGET_IDS = new HashMap<>();

	static
	{
		// Standard spellbook
		SPELL_WIDGET_IDS.put("Lumbridge Home Teleport", InterfaceID.MagicSpellbook.TELEPORT_HOME_STANDARD);
		SPELL_WIDGET_IDS.put("Varrock Teleport", InterfaceID.MagicSpellbook.VARROCK_TELEPORT);
		SPELL_WIDGET_IDS.put("Varrock Teleport: GE", InterfaceID.MagicSpellbook.VARROCK_TELEPORT);
		SPELL_WIDGET_IDS.put("Lumbridge Teleport", InterfaceID.MagicSpellbook.LUMBRIDGE_TELEPORT);
		SPELL_WIDGET_IDS.put("Falador Teleport", InterfaceID.MagicSpellbook.FALADOR_TELEPORT);
		SPELL_WIDGET_IDS.put("Teleport to House", InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE);
		SPELL_WIDGET_IDS.put("Teleport to House (Outside)", InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE);
		SPELL_WIDGET_IDS.put("Camelot Teleport", InterfaceID.MagicSpellbook.CAMELOT_TELEPORT);
		SPELL_WIDGET_IDS.put("Camelot Teleport: Seers'", InterfaceID.MagicSpellbook.CAMELOT_TELEPORT);
		SPELL_WIDGET_IDS.put("Kourend Castle Teleport", InterfaceID.MagicSpellbook.KOUREND_TELEPORT);
		SPELL_WIDGET_IDS.put("Ardougne Teleport", InterfaceID.MagicSpellbook.ARDOUGNE_TELEPORT);
		SPELL_WIDGET_IDS.put("Civitas illa Fortis Teleport", InterfaceID.MagicSpellbook.FORTIS_TELEPORT);
		SPELL_WIDGET_IDS.put("Watchtower Teleport", InterfaceID.MagicSpellbook.WATCHTOWER_TELEPORT);
		SPELL_WIDGET_IDS.put("Watchtower Teleport: Yanille", InterfaceID.MagicSpellbook.WATCHTOWER_TELEPORT);
		SPELL_WIDGET_IDS.put("Trollheim Teleport", InterfaceID.MagicSpellbook.TROLLHEIM_TELEPORT);
		SPELL_WIDGET_IDS.put("Ape Atoll Teleport", InterfaceID.MagicSpellbook.APE_TELEPORT);
		// Ancient (Zaros) spellbook
		SPELL_WIDGET_IDS.put("Edgeville Home Teleport", InterfaceID.MagicSpellbook.TELEPORT_HOME_ZAROS);
		SPELL_WIDGET_IDS.put("Paddewwa Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT1);
		SPELL_WIDGET_IDS.put("Senntisten Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT2);
		SPELL_WIDGET_IDS.put("Kharyrll Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT3);
		SPELL_WIDGET_IDS.put("Lassar Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT4);
		SPELL_WIDGET_IDS.put("Dareeyak Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT5);
		SPELL_WIDGET_IDS.put("Carrallanger Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT6);
		SPELL_WIDGET_IDS.put("Annakarl Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT7);
		SPELL_WIDGET_IDS.put("Ghorrock Teleport", InterfaceID.MagicSpellbook.ZAROSTELEPORT8);
		// Lunar spellbook
		SPELL_WIDGET_IDS.put("Lunar Home Teleport", InterfaceID.MagicSpellbook.TELEPORT_HOME_LUNAR);
		SPELL_WIDGET_IDS.put("Moonclan Teleport", InterfaceID.MagicSpellbook.TELE_MOONCLAN);
		SPELL_WIDGET_IDS.put("Waterbirth Teleport", InterfaceID.MagicSpellbook.TELE_WATERBIRTH);
		SPELL_WIDGET_IDS.put("Barbarian Teleport", InterfaceID.MagicSpellbook.TELE_BARB_OUT);
		SPELL_WIDGET_IDS.put("Khazard Teleport", InterfaceID.MagicSpellbook.TELE_KHAZARD);
		SPELL_WIDGET_IDS.put("Fishing Guild Teleport", InterfaceID.MagicSpellbook.TELE_FISH);
		SPELL_WIDGET_IDS.put("Catherby Teleport", InterfaceID.MagicSpellbook.TELE_CATHER);
		SPELL_WIDGET_IDS.put("Ice Plateau Teleport", InterfaceID.MagicSpellbook.TELE_GHORROCK);
		SPELL_WIDGET_IDS.put("Ourania Teleport", InterfaceID.MagicSpellbook.OURANIA_TELEPORT);
		// Arceuus spellbook
		SPELL_WIDGET_IDS.put("Arceuus Home Teleport", InterfaceID.MagicSpellbook.TELEPORT_HOME_ARCEUUS);
		SPELL_WIDGET_IDS.put("Arceuus Library Teleport", InterfaceID.MagicSpellbook.TELEPORT_ARCEUUS_LIBRARY);
		SPELL_WIDGET_IDS.put("Draynor Manor Teleport", InterfaceID.MagicSpellbook.TELEPORT_DRAYNOR_MANOR);
		SPELL_WIDGET_IDS.put("Mind Altar Teleport", InterfaceID.MagicSpellbook.TELEPORT_MIND_ALTAR);
		SPELL_WIDGET_IDS.put("Salve Graveyard Teleport", InterfaceID.MagicSpellbook.TELEPORT_SALVE_GRAVEYARD);
		SPELL_WIDGET_IDS.put("Fenkenstrain's Castle Teleport", InterfaceID.MagicSpellbook.TELEPORT_FENKENSTRAIN_CASTLE);
		SPELL_WIDGET_IDS.put("West Ardougne Teleport", InterfaceID.MagicSpellbook.TELEPORT_WEST_ARDOUGNE);
		SPELL_WIDGET_IDS.put("Harmony Island Teleport", InterfaceID.MagicSpellbook.TELEPORT_HARMONY_ISLAND);
		SPELL_WIDGET_IDS.put("Cemetery Teleport", InterfaceID.MagicSpellbook.TELEPORT_CEMETERY);
		SPELL_WIDGET_IDS.put("Barrows Teleport", InterfaceID.MagicSpellbook.TELEPORT_BARROWS);
		SPELL_WIDGET_IDS.put("Battlefront Teleport", InterfaceID.MagicSpellbook.TELEPORT_BATTLEFRONT);
	}

	@Inject
	public SpellbookHighlightOverlay(Client client, ShortestPathPlugin plugin)
	{
		super(client, plugin);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.highlightSpellbookSpells)
		{
			return null;
		}

		if (plugin.getPathfinder() == null)
		{
			return null;
		}

		List<PathStep> path = plugin.getPathfinder().getPath();
		if (path == null || path.isEmpty())
		{
			return null;
		}

		Widget spellLayer = client.getWidget(InterfaceID.MagicSpellbook.SPELLLAYER);
		if (spellLayer == null || spellLayer.isHidden())
		{
			return null;
		}

		int pathIndex = getPlayerPathIndex(path);
		if (pathIndex < 0 || pathIndex + 1 >= path.size())
		{
			return null;
		}

		Set<Transport> transports = plugin.transportsForEdge(path.get(pathIndex), path.get(pathIndex + 1));
		Color highlight = plugin.colourBankPickupHighlight;

		for (Transport transport : transports)
		{
			if (!TransportType.TELEPORTATION_SPELL.equals(transport.getType()))
			{
				continue;
			}
			String displayInfo = transport.getDisplayInfo();
			if (displayInfo == null)
			{
				continue;
			}
			Integer widgetId = SPELL_WIDGET_IDS.get(displayInfo);
			if (widgetId == null)
			{
				continue;
			}
			Widget spellWidget = client.getWidget(widgetId);
			if (spellWidget == null || spellWidget.isHidden())
			{
				continue;
			}
			Rectangle bounds = spellWidget.getBounds();
			Stroke oldStroke = graphics.getStroke();
			graphics.setStroke(new BasicStroke(2));
			graphics.setColor(highlight);
			graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
			graphics.setStroke(oldStroke);
		}

		return null;
	}
}
