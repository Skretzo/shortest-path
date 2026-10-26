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
import shortestpath.Util;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.Settings;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class SpellbookHighlightOverlay extends AbstractHighlightOverlay
{
	private static final Map<String, Integer> SPELL_WIDGET_IDS = new HashMap<>();

	static
	{
		// spell display name -> InterfaceID.MagicSpellbook widget id
		try
		{
			Util.each("/spell-widget-ids.tsv", (line, c) ->
				SPELL_WIDGET_IDS.put(c[0], Integer.valueOf(c[1])));
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load spell-widget-ids.tsv", e);
		}
	}

	@Inject
	public SpellbookHighlightOverlay(Client client, Settings settings,
		PathScheduler scheduler, PathAnnotator annotator)
	{
		super(client, scheduler, annotator, settings);
	}

	@Override
	protected boolean enabled()
	{
		return settings.display().highlightSpellbookSpells();
	}

	@Override
	protected boolean extraGatesPass()
	{
		Widget spellLayer = client.getWidget(InterfaceID.MagicSpellbook.SPELLLAYER);
		return spellLayer != null && !spellLayer.isHidden();
	}

	@Override
	protected Dimension drawHighlights(Graphics2D graphics, List<PathStep> path, int pathIndex,
		ActiveSearch search)
	{
		Set<Transport> transports = annotator.transportsForEdge(path.get(pathIndex), path.get(pathIndex + 1));
		Color highlight = settings.display().colourBankPickupHighlight();

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
