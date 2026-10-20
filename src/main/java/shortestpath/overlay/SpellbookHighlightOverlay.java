package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.BasicStroke;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			SpellbookHighlightOverlay.class.getResourceAsStream("/spell-widget-ids.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				SPELL_WIDGET_IDS.put(c[0], Integer.valueOf(c[1]));
			}
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load spell-widget-ids.tsv", e);
		}
	}

	private final Settings settings;

	@Inject
	public SpellbookHighlightOverlay(Client client, Settings settings,
		PathScheduler scheduler, PathAnnotator annotator)
	{
		super(client, scheduler, annotator, settings);
		this.settings = settings;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!settings.display().highlightSpellbookSpells())
		{
			return null;
		}

		ActiveSearch search = scheduler.getActiveSearch();
		if (search == null)
		{
			return null;
		}

		List<PathStep> path = search.getPath();
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
