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
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.Settings;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class ScrollBookHighlightOverlay extends AbstractHighlightOverlay
{
	private static final Map<String, Integer> SCROLL_WIDGET_IDS = new HashMap<>();

	static
	{
		SCROLL_WIDGET_IDS.put("Master scroll book: Nardah teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_NARDAH);
		SCROLL_WIDGET_IDS.put("Master scroll book: Digsite teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_DIGSITE);
		SCROLL_WIDGET_IDS.put("Master scroll book: Feldip hills teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_FELDIP);
		SCROLL_WIDGET_IDS.put("Master scroll book: Lunar isle teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_LUNARISLE);
		SCROLL_WIDGET_IDS.put("Master scroll book: Mort'ton teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_MORTTON);
		SCROLL_WIDGET_IDS.put("Master scroll book: Pest control teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_PESTCONTROL);
		SCROLL_WIDGET_IDS.put("Master scroll book: Piscatoris teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_PISCATORIS);
		SCROLL_WIDGET_IDS.put("Master scroll book: Tai bwo wannai teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_TAIBWO);
		SCROLL_WIDGET_IDS.put("Master scroll book: Iorwerth camp teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_ELF);
		SCROLL_WIDGET_IDS.put("Master scroll book: Mos le'harmless teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_MOSLES);
		SCROLL_WIDGET_IDS.put("Master scroll book: Lumberyard teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_LUMBERYARD);
		SCROLL_WIDGET_IDS.put("Master scroll book: Zul-andra teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_ZULANDRA);
		SCROLL_WIDGET_IDS.put("Master scroll book: Key master teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_CERBERUS);
		SCROLL_WIDGET_IDS.put("Master scroll book: Revenant cave teleport (level 17 entrance)", InterfaceID.Bookofscrolls.TELEPORTSCROLL_REVENANTS);
		SCROLL_WIDGET_IDS.put("Master scroll book: Revenant cave teleport (level 26 entrance)", InterfaceID.Bookofscrolls.TELEPORTSCROLL_REVENANTS);
		SCROLL_WIDGET_IDS.put("Master scroll book: Revenant cave teleport (level 40 entrance)", InterfaceID.Bookofscrolls.TELEPORTSCROLL_REVENANTS);
		SCROLL_WIDGET_IDS.put("Master scroll book: Watson teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_WATSON);
		SCROLL_WIDGET_IDS.put("Master scroll book: Guthixian temple teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_GUTHIXIAN_TEMPLE);
		SCROLL_WIDGET_IDS.put("Master scroll book: Spider cave teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_SPIDERCAVE);
		SCROLL_WIDGET_IDS.put("Master scroll book: Colossal wyrm teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_COLOSSAL_WYRM);
		SCROLL_WIDGET_IDS.put("Master scroll book: Chasm of fire teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_CHASMOFFIRE);
		SCROLL_WIDGET_IDS.put("Master scroll book: Arde-aglais teleport", InterfaceID.Bookofscrolls.TELEPORTSCROLL_ARDEAGLAIS);
	}

	@Inject
	public ScrollBookHighlightOverlay(Client client, Settings settings, PathScheduler scheduler, PathAnnotator annotator)
	{
		super(client, scheduler, annotator, settings);
	}

	@Override
	protected boolean enabled()
	{
		return settings.display().highlightInventoryItems();
	}

	@Override
	protected boolean extraGatesPass()
	{
		Widget scrollBook = client.getWidget(InterfaceID.Bookofscrolls.CONTENTS);
		return scrollBook != null && !scrollBook.isHidden();
	}

	@Override
	protected Dimension drawHighlights(Graphics2D graphics, List<PathStep> path,
		int pathIndex, ActiveSearch search)
	{
		Set<Transport> transports = annotator.transportsForEdge(path.get(pathIndex), path.get(pathIndex + 1));
		Color highlight = settings.display().colourBankPickupHighlight();

		for (Transport transport : transports)
		{
			if (!TransportType.TELEPORTATION_ITEM.equals(transport.getType()))
			{
				continue;
			}
			String displayInfo = transport.getDisplayInfo();
			if (displayInfo == null)
			{
				continue;
			}
			Integer widgetId = SCROLL_WIDGET_IDS.get(displayInfo);
			if (widgetId == null)
			{
				continue;
			}
			Widget scrollWidget = client.getWidget(widgetId);
			if (scrollWidget == null || scrollWidget.isHidden())
			{
				continue;
			}
			Rectangle bounds = scrollWidget.getBounds();
			bounds.grow(2, 2);
			Stroke oldStroke = graphics.getStroke();
			graphics.setStroke(new BasicStroke(2));
			graphics.setColor(highlight);
			graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
			graphics.setStroke(oldStroke);
		}

		return null;
	}
}
