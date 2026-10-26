package shortestpath.api;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Targets;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.widgets.WidgetGeometry;

/**
 * Characterization pin for the menu-verb surface: the exact (option, target,
 * position) sequence {@link MenuVerbs#onMenuEntryAdded} emits for each arm —
 * shift-right-click, world map, Flash icons, minimap, and the addMenuEntry
 * dedup. The {@link Menu} seam records every {@code createMenuEntry(position)}
 * call plus the fluent setters the builder chains onto it, so the fold that
 * parameterizes the two surface blocks must reproduce this log verbatim.
 * Color tags are stripped in the recorded strings — the wrap is a pure
 * function of the JagexColors constants and what matters here is which verb
 * text landed at which position in which order.
 */
public class MenuVerbsTest
{
	private Client client;
	private Menu menu;
	private PathScheduler scheduler;
	private PathAnnotator annotator;
	private WidgetGeometry geometry;
	private MenuVerbs verbs;

	private final List<MenuEntry> menuEntries = new ArrayList<>();
	private final List<Emitted> emitted = new ArrayList<>();

	private static final class Emitted
	{
		private final int position;
		private String option;
		private String target;

		Emitted(int position)
		{
			this.position = position;
		}

		@Override
		public String toString()
		{
			return position + ":" + Text.removeTags(option) + "|" + Text.removeTags(target);
		}
	}

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		menu = mock(Menu.class);
		scheduler = mock(PathScheduler.class);
		annotator = mock(PathAnnotator.class);
		geometry = mock(WidgetGeometry.class);
		verbs = new MenuVerbs(client, scheduler, annotator, geometry);

		when(client.getMenu()).thenReturn(menu);
		// The menu reads the live entry list for dedup and records each created
		// entry with the option/target its fluent setters receive.
		when(menu.getMenuEntries()).thenAnswer(inv -> menuEntries.toArray(new MenuEntry[0]));
		when(menu.createMenuEntry(anyInt())).thenAnswer(invocation ->
		{
			int position = invocation.getArgument(0);
			Emitted rec = new Emitted(position);
			MenuEntry entry = mock(MenuEntry.class, Mockito.RETURNS_SELF);
			doAnswer(a ->
			{
				rec.option = a.getArgument(0);
				return entry;
			}).when(entry).setOption(anyString());
			doAnswer(a ->
			{
				rec.target = a.getArgument(0);
				return entry;
			}).when(entry).setTarget(anyString());
			when(entry.getOption()).thenAnswer(a -> rec.option);
			when(entry.getTarget()).thenAnswer(a -> rec.target);
			emitted.add(rec);
			menuEntries.add(Math.max(0, Math.min(position, menuEntries.size())), entry);
			return entry;
		});
		// The mouse defaults to an off-canvas spot; tests move it inside the
		// bounds they want to arm.
		when(client.getMouseCanvasPosition()).thenReturn(new Point(-1, -1));
	}

	private static int packed(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}

	private static MenuEntryAdded entryAdded(String option, String target)
	{
		MenuEntry source = mock(MenuEntry.class);
		when(source.getOption()).thenReturn(option);
		when(source.getTarget()).thenReturn(target);
		when(source.getType()).thenReturn(MenuAction.WALK);
		return new MenuEntryAdded(source);
	}

	private ActiveSearch activeSearch(Targets targets, List<PathStep> path)
	{
		ActiveSearch search = mock(ActiveSearch.class);
		when(search.getTargets()).thenReturn(targets);
		when(search.getPath()).thenReturn(path);
		when(scheduler.getActiveSearch()).thenReturn(search);
		return search;
	}

	private void worldMapOpen(Rectangle bounds)
	{
		Widget map = mock(Widget.class);
		when(map.getBounds()).thenReturn(bounds);
		when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(map);
	}

	private void mouseAt(int x, int y)
	{
		when(client.getMouseCanvasPosition()).thenReturn(new Point(x, y));
	}

	private void assertEmissions(String... expected)
	{
		List<String> actual = new ArrayList<>();
		for (Emitted e : emitted)
		{
			actual.add(e.toString());
		}
		assertEquals(List.of(expected), actual);
	}

	@Test
	public void shiftWalkEmitsStartAndClearWhenSelectedTileOnPath()
	{
		when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
		int selected = packed(3200, 3200);
		activeSearch(Targets.at(List.of(packed(3300, 3300), packed(3400, 3400))),
			List.of(new PathStep(packed(1, 1), null), new PathStep(selected, null)));
		when(geometry.getSelectedWorldPoint()).thenReturn(selected);

		verbs.onMenuEntryAdded(entryAdded("Walk here", ""));

		assertEmissions("1:Set|Target", "1:Set|Target 3", "1:Set|Start", "1:Clear|Path");
	}

	@Test
	public void shiftWalkOmitsClearWhenSelectedTileOffPath()
	{
		when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
		activeSearch(Targets.at(List.of(packed(3300, 3300), packed(3400, 3400))),
			List.of(new PathStep(packed(1, 1), null)));
		when(geometry.getSelectedWorldPoint()).thenReturn(packed(9, 9));

		verbs.onMenuEntryAdded(entryAdded("Walk here", ""));

		assertEmissions("1:Set|Target", "1:Set|Target 3", "1:Set|Start");
	}

	@Test
	public void shiftWalkOmitsClearWhenSearchHasNoPath()
	{
		when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
		activeSearch(Targets.at(List.of(packed(3300, 3300))), null);
		when(geometry.getSelectedWorldPoint()).thenReturn(packed(1, 1));

		verbs.onMenuEntryAdded(entryAdded("Walk here", ""));

		assertEmissions("1:Set|Target", "1:Set|Target 2", "1:Set|Start");
	}

	@Test
	public void shiftWalkSkipsUndefinedTargets()
	{
		when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
		activeSearch(Targets.at(List.of(WorldPointUtil.UNDEFINED, packed(3300, 3300))),
			List.of(new PathStep(packed(1, 1), null)));
		when(geometry.getSelectedWorldPoint()).thenReturn(packed(9, 9));

		verbs.onMenuEntryAdded(entryAdded("Walk here", ""));

		assertEmissions("1:Set|Target", "1:Set|Target 3", "1:Set|Start");
	}

	@Test
	public void shiftWalkWithoutTargetsEmitsOnlySetTarget()
	{
		when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
		activeSearch(Targets.NONE, null);

		verbs.onMenuEntryAdded(entryAdded("Walk here", ""));

		assertEmissions("1:Set|Target");
	}

	@Test
	public void worldMapEmitsStartAndClearPerDefinedTarget()
	{
		worldMapOpen(new Rectangle(0, 0, 100, 100));
		mouseAt(50, 50);
		activeSearch(Targets.at(List.of(packed(3300, 3300), packed(3400, 3400))), null);

		verbs.onMenuEntryAdded(entryAdded("Walk here", ""));

		assertEmissions("0:Set|Target", "0:Set|Target 3", "0:Set|Start", "0:Clear|Path");
	}

	@Test
	public void worldMapSkipsUndefinedTargets()
	{
		worldMapOpen(new Rectangle(0, 0, 100, 100));
		mouseAt(50, 50);
		activeSearch(Targets.at(List.of(WorldPointUtil.UNDEFINED, packed(3300, 3300))), null);

		verbs.onMenuEntryAdded(entryAdded("Walk here", ""));

		assertEmissions("0:Set|Target", "0:Set|Target 3", "0:Set|Start", "0:Clear|Path");
	}

	@Test
	public void worldMapWithoutTargetsEmitsOnlySetTarget()
	{
		worldMapOpen(new Rectangle(0, 0, 100, 100));
		mouseAt(50, 50);
		activeSearch(Targets.NONE, null);

		verbs.onMenuEntryAdded(entryAdded("Walk here", ""));

		assertEmissions("0:Set|Target");
	}

	@Test
	public void flashIconsOffersFindClosest()
	{
		worldMapOpen(new Rectangle(0, 0, 100, 100));
		when(annotator.hasDestination("bank")).thenReturn(true);

		verbs.onMenuEntryAdded(entryAdded("Flash icons", "Bank"));

		assertEmissions("1:Find closest|Bank");
	}

	@Test
	public void minimapInsideEmitsClearPath()
	{
		when(geometry.getMinimapClipArea()).thenReturn(new Rectangle(0, 0, 100, 100));
		mouseAt(50, 50);
		activeSearch(Targets.NONE, null);

		verbs.onMenuEntryAdded(entryAdded("Walk here", ""));

		assertEmissions("0:Clear|Path");
	}

	@Test
	public void floatingMapOptionEmitsClearPath()
	{
		when(geometry.getMinimapClipArea()).thenReturn(new Rectangle(0, 0, 100, 100));
		activeSearch(Targets.NONE, null);

		verbs.onMenuEntryAdded(entryAdded("Floating World Map", ""));

		assertEmissions("1:Clear|Path");
	}

	@Test
	public void duplicateVerbsAreNotReAdded()
	{
		when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
		int selected = packed(3200, 3200);
		activeSearch(Targets.at(List.of(packed(3300, 3300))),
			List.of(new PathStep(selected, null)));
		when(geometry.getSelectedWorldPoint()).thenReturn(selected);

		MenuEntryAdded event = entryAdded("Walk here", "");
		verbs.onMenuEntryAdded(event);
		verbs.onMenuEntryAdded(event);

		assertEmissions("1:Set|Target", "1:Set|Target 2", "1:Set|Start", "1:Clear|Path");
	}
}
