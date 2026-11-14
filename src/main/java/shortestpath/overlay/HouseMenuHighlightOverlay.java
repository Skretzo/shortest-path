package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.poh.*;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.*;
import shortestpath.transport.*;

/**
 * Outlines, in an open house teleport menu, the line the route needs: the
 * destination in the jewellery box menu or the Portal Nexus teleport menu.
 * The same setting as the house teleport highlight turns it on.
 */
@Slf4j
public class HouseMenuHighlightOverlay extends Overlay
{
	/** The jewellery box menu's column per jewellery type. */
	private static final int[] BOX_COLUMNS = {
		InterfaceID.PohJewelleryBox.DUELING,
		InterfaceID.PohJewelleryBox.GAMING,
		InterfaceID.PohJewelleryBox.COMBAT,
		InterfaceID.PohJewelleryBox.SKILLS,
		InterfaceID.PohJewelleryBox.WEALTH,
		InterfaceID.PohJewelleryBox.GLORY,
	};
	private static final int[] NEXUS_LINES = {
		InterfaceID.TelenexusTeleport.TEXT1,
		InterfaceID.TelenexusTeleport.EXTRAS,
	};

	private final Client client;
	private final PathScheduler scheduler;
	private final PathAnnotator annotator;
	private final Settings settings;
	private final HouseRouteProgress progress;
	/** The destination last logged as having no hotkey, so it is logged once. */
	private String lastUnkeyed;
	private Font keyFont;
	// The lines matched for the route's exit, worked out once per tick.
	private int matchTick = -1;
	private Transport matchExit;
	private boolean matchBox;
	private boolean matchNexus;
	private List<Widget> boxLines = List.of();
	private List<Widget> nexusLines = List.of();
	private String nexusKey;
	private String nexusName;

	@Inject
	public HouseMenuHighlightOverlay(Client client, PathScheduler scheduler, PathAnnotator annotator, Settings settings,
		HouseRouteProgress progress)
	{
		this.progress = progress;
		this.client = client;
		this.scheduler = scheduler;
		this.annotator = annotator;
		this.settings = settings;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!settings.bool(ConfigKey.HIGHLIGHT_POH_OBJECT))
		{
			return null;
		}
		boolean box = isOpen(InterfaceID.PohJewelleryBox.UNIVERSE);
		boolean nexus = isOpen(InterfaceID.TelenexusTeleport.UNIVERSE);
		if (!box && !nexus)
		{
			return null;
		}
		int tick = client.getTickCount();
		Transport exit = exit();
		if (tick != matchTick || exit != matchExit || box != matchBox || nexus != matchNexus)
		{
			// The menus' text changes only when they are built, so the lines are matched once per tick.
			match(exit, box, nexus);
			matchTick = tick;
			matchExit = exit;
			matchBox = box;
			matchNexus = nexus;
		}
		if (exit == null)
		{
			return null;
		}
		Color color = settings.get(ConfigKey.COLOUR_BANK_PICKUP_HIGHLIGHT);
		for (Widget line : boxLines)
		{
			outline(graphics, line, color);
		}
		if (!nexusLines.isEmpty())
		{
			// The list scrolls: a line scrolled out of view is outlined only where it shows, if at all.
			Widget scroll = client.getWidget(InterfaceID.TelenexusTeleport.SCROLLING1);
			Rectangle view = scroll == null ? null : scroll.getBounds();
			if (view != null)
			{
				Shape oldClip = graphics.getClip();
				graphics.clip(view);
				for (Widget line : nexusLines)
				{
					if (view.intersects(line.getBounds()))
					{
						outline(graphics, line, color);
					}
				}
				graphics.setClip(oldClip);
			}
			// The key works wherever the line is scrolled to, so it is shown in the empty space above the nexus.
			drawKey(graphics, nexusKey, nexusName, color);
		}
		return null;
	}

	/**
	 * Finds the open menus' lines for the route's house exit. A nexus line
	 * must name the exact destination (the Grand Exchange, not Varrock, which
	 * share one setting); a line naming only the shared setting counts when
	 * no exact one is listed.
	 */
	private void match(Transport exit, boolean box, boolean nexus)
	{
		boxLines = List.of();
		nexusLines = List.of();
		nexusKey = null;
		nexusName = null;
		if (exit == null)
		{
			return;
		}
		if (box && TransportType.TELEPORTATION_BOX.equals(exit.getType()))
		{
			List<Widget> found = new ArrayList<>();
			for (int column : BOX_COLUMNS)
			{
				for (Widget line : textWidgets(client.getWidget(column)))
				{
					if (HouseScan.isBoxLine(exit.getDisplayInfo(), line.getText()))
					{
						found.add(line);
					}
				}
			}
			boxLines = found;
		}
		PohNexusPortal want = PohNexusPortal.fromDisplayInfo(exit.getDisplayInfo());
		if (!nexus || want == null)
		{
			return;
		}
		List<Widget> exact = new ArrayList<>();
		List<Widget> shared = new ArrayList<>();
		for (int id : NEXUS_LINES)
		{
			for (Widget line : textWidgets(client.getWidget(id)))
			{
				if (HouseScan.isNexusLineFor(exit.getDisplayInfo(), line.getText()))
				{
					exact.add(line);
				}
				else if (want.equals(HouseScan.nexusLine(line.getText())))
				{
					shared.add(line);
				}
			}
		}
		nexusLines = exact.isEmpty() ? shared : exact;
		for (Widget line : nexusLines)
		{
			nexusKey = HouseScan.lineKey(line.getText());
			if (nexusKey != null)
			{
				break;
			}
		}
		nexusName = PohService.stripKeyPrefix(net.runelite.client.util.Text.removeTags(nexusLines.isEmpty()
			? "" : nexusLines.get(0).getText()).trim());
	}

	/**
	 * Writes "Press <key>" and the destination in the empty space at the top
	 * of the menu's right side, above the nexus picture: centered over the
	 * Teleport / Scry mode box, level with the top of the list. Without a
	 * hotkey on the line it names the line to click instead.
	 */
	private void drawKey(Graphics2D graphics, String key, String destination, Color color)
	{
		Widget list = client.getWidget(InterfaceID.TelenexusTeleport.SCROLLING1);
		Widget modes = client.getWidget(InterfaceID.TelenexusTeleport.OPTIONS_FRAME);
		if (list == null || modes == null || modes.isHidden())
		{
			return;
		}
		Rectangle listBounds = list.getBounds();
		Rectangle modeBounds = modes.getBounds();
		int centerX = modeBounds.x + modeBounds.width / 2;
		int baseline = listBounds.y + 36;
		if (keyFont == null)
		{
			keyFont = FontManager.getRunescapeBoldFont().deriveFont(24f);
		}
		graphics.setFont(keyFont);
		drawCentered(graphics, keyPrompt(key, destination), centerX, baseline, color);
		if (key != null)
		{
			graphics.setFont(FontManager.getRunescapeFont());
			drawCentered(graphics, destination, centerX, baseline + 24, color);
		}
		if (key == null && !destination.equals(lastUnkeyed))
		{
			lastUnkeyed = destination;
			log.debug("nexus line for {} shows no hotkey", destination);
		}
	}

	/** "Press 6" for a keyed line, else "Click Barrows". */
	static String keyPrompt(String key, String destination)
	{
		return key != null ? "Press " + key : "Click " + destination;
	}

	private static void drawCentered(Graphics2D graphics, String text, int centerX, int baseline, Color color)
	{
		int x = centerX - graphics.getFontMetrics().stringWidth(text) / 2;
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, baseline + 1);
		graphics.setColor(color);
		graphics.drawString(text, x, baseline);
	}

	/** The transport the route leaves the house by, or null. */
	private Transport exit()
	{
		ActiveSearch search = scheduler.getActiveSearch();
		List<PathStep> path = search == null ? null : search.getPath();
		if (path == null || path.isEmpty())
		{
			return null;
		}
		int entry = progress.houseVisit(path);
		return entry < 0 ? null : annotator.getPohExitTransport(path.get(entry).getPackedPosition(), path, entry - 1);
	}

	private boolean isOpen(int id)
	{
		Widget widget = client.getWidget(id);
		return widget != null && !widget.isHidden();
	}

	/** The widget and every visible descendant that shows text. */
	private static List<Widget> textWidgets(Widget root)
	{
		List<Widget> found = new ArrayList<>();
		collect(root, found);
		return found;
	}

	private static void collect(Widget widget, List<Widget> found)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}
		String text = widget.getText();
		if (text != null && !text.isEmpty())
		{
			found.add(widget);
		}
		for (Widget[] children : new Widget[][]{widget.getStaticChildren(), widget.getDynamicChildren(),
			widget.getNestedChildren()})
		{
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				collect(child, found);
			}
		}
	}

	private static void outline(Graphics2D graphics, Widget widget, Color color)
	{
		Rectangle bounds = widget.getBounds();
		if (bounds == null)
		{
			return;
		}
		bounds.grow(2, 2);
		Stroke old = graphics.getStroke();
		graphics.setStroke(new BasicStroke(2));
		graphics.setColor(color);
		graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
		graphics.setStroke(old);
	}
}
