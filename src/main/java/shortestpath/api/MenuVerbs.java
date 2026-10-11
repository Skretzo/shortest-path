package shortestpath.api;

import java.awt.Shape;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.widgets.WidgetGeometry;

/**
 * Owns the plugin's menu-verb surface: the colour-tagged {@code Set Target}/
 * {@code Set Start}/{@code Clear Path}/{@code Find closest} entries offered on
 * shift-right-click and on the world map, and the click handler that turns a
 * chosen verb into scheduler target/start updates. The verb constants live on
 * this one type so the builder and the click matcher compare the same
 * instances. Selected-tile and minimap reads flow through the widget-geometry
 * seam; destination lookups flow through the annotator's config facade.
 *
 * <p>This package is a leaf: nothing here may reference the plugin type.
 */
@Singleton
public class MenuVerbs
{
	private static final String CLEAR = "Clear";
	private static final String PATH = ColorUtil.wrapWithColorTag("Path", JagexColors.MENU_TARGET);
	private static final String SET = "Set";
	private static final String FIND_CLOSEST = "Find closest";
	private static final String FLASH_ICONS = "Flash icons";
	private static final String START = ColorUtil.wrapWithColorTag("Start", JagexColors.MENU_TARGET);
	private static final String TARGET = ColorUtil.wrapWithColorTag("Target", JagexColors.MENU_TARGET);

	private final Client client;
	private final PathScheduler scheduler;
	private final PathAnnotator annotator;
	private final WidgetGeometry geometry;

	@Inject
	public MenuVerbs(Client client, PathScheduler scheduler, PathAnnotator annotator,
		WidgetGeometry geometry)
	{
		this.client = client;
		this.scheduler = scheduler;
		this.annotator = annotator;
		this.geometry = geometry;
	}

	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (client.isKeyPressed(KeyCode.KC_SHIFT)
			&& event.getType() == MenuAction.WALK.getId())
		{
			addMenuEntry(event, SET, TARGET, 1);
			ActiveSearch pathfinder = scheduler.getActiveSearch();
			if (pathfinder != null)
			{
				if (!pathfinder.getTargets().isEmpty())
				{
					addMenuEntry(event, SET, TARGET + ColorUtil.wrapWithColorTag(" " +
						(pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET), 1);
				}
				for (int target : pathfinder.getTargets())
				{
					if (target != WorldPointUtil.UNDEFINED)
					{
						addMenuEntry(event, SET, START, 1);
						break;
					}
				}
				int selectedTile = geometry.getSelectedWorldPoint();
				List<PathStep> path;
				if ((path = pathfinder.getPath()) != null)
				{
					for (PathStep pathStep : path)
					{
						if (pathStep.getPackedPosition() == selectedTile)
						{
							addMenuEntry(event, CLEAR, PATH, 1);
							break;
						}
					}
				}
			}
		}

		final Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);

		if (map != null)
		{
			if (map.getBounds().contains(
				client.getMouseCanvasPosition().getX(),
				client.getMouseCanvasPosition().getY()))
			{
				addMenuEntry(event, SET, TARGET, 0);
				ActiveSearch pathfinder = scheduler.getActiveSearch();
				if (pathfinder != null)
				{
					if (!pathfinder.getTargets().isEmpty())
					{
						addMenuEntry(event, SET, TARGET + ColorUtil.wrapWithColorTag(" " +
							(pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET), 0);
					}
					for (int target : pathfinder.getTargets())
					{
						if (target != WorldPointUtil.UNDEFINED)
						{
							addMenuEntry(event, SET, START, 0);
							addMenuEntry(event, CLEAR, PATH, 0);
						}
					}
				}
			}
			if (event.getOption().equals(FLASH_ICONS) && annotator.hasDestination(simplify(event.getTarget())))
			{
				addMenuEntry(event, FIND_CLOSEST, event.getTarget(), 1);
			}
		}

		final Shape minimap = geometry.getMinimapClipArea();

		if (minimap != null && scheduler.getActiveSearch() != null
			&& minimap.contains(
			client.getMouseCanvasPosition().getX(),
			client.getMouseCanvasPosition().getY()))
		{
			addMenuEntry(event, CLEAR, PATH, 0);
		}

		if (minimap != null && scheduler.getActiveSearch() != null
			&& ("Floating World Map".equals(Text.removeTags(event.getOption()))
			|| "Close Floating panel".equals(Text.removeTags(event.getOption()))))
		{
			addMenuEntry(event, CLEAR, PATH, 1);
		}
	}

	private String simplify(String text)
	{
		return Text.removeTags(text).toLowerCase()
			.replaceAll("[^a-zA-Z ]", "")
			.replace(" ", "_")
			.replace("__", "_");
	}

	private void onMenuOptionClicked(MenuEntry entry)
	{
		ActiveSearch pathfinder = scheduler.getActiveSearch();
		if (entry.getOption().equals(SET) && entry.getTarget().equals(TARGET))
		{
			scheduler.setTarget(geometry.getSelectedWorldPoint());
		}
		else if (entry.getOption().equals(SET) && pathfinder != null && entry.getTarget().equals(TARGET +
			ColorUtil.wrapWithColorTag(" " + (pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET)))
		{
			scheduler.setTarget(geometry.getSelectedWorldPoint(), true);
		}
		else if (entry.getOption().equals(SET) && entry.getTarget().equals(START))
		{
			scheduler.setStart(geometry.getSelectedWorldPoint());
		}
		else if (entry.getOption().equals(CLEAR) && entry.getTarget().equals(PATH))
		{
			scheduler.setTarget(WorldPointUtil.UNDEFINED);
		}
		else if (entry.getOption().equals(FIND_CLOSEST))
		{
			scheduler.setTargets(annotator.getDestinations(simplify(entry.getTarget())), true);
		}
	}

	private void addMenuEntry(MenuEntryAdded event, String option, String target, int position)
	{
		List<MenuEntry> entries = new LinkedList<>(Arrays.asList(client.getMenu().getMenuEntries()));

		if (entries.stream().anyMatch(e -> e.getOption().equals(option) && e.getTarget().equals(target)))
		{
			return;
		}

		client.getMenu().createMenuEntry(position)
			.setOption(option)
			.setTarget(target)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setIdentifier(event.getIdentifier())
			.setType(MenuAction.RUNELITE)
			.onClick(this::onMenuOptionClicked);
	}
}
