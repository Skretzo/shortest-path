package shortestpath.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

/**
 * Pins the widget-access contract the scrape consumers build on: the
 * ordered-children and self-first text shapes (positions are load-bearing
 * — a first-row sentinel and a keyed row both read index 0), the
 * presence predicate, the loud off-client-thread throw on a live client,
 * and the detached-instance collapse that never throws.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class GameWidgetsTest
{
	private static final int GROUP = 1234;
	private static final int CHILD = 9;
	private static final int PACKED = 5678;

	@Mock
	Client client;

	private GameWidgets widgets;

	@Before
	public void setUp()
	{
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		widgets = new GameWidgets(client);
	}

	private static Widget child(String text)
	{
		Widget child = mock(Widget.class);
		when(child.getText()).thenReturn(text);
		return child;
	}

	private static Widget container(String selfText, Widget... children)
	{
		Widget container = mock(Widget.class);
		when(container.getText()).thenReturn(selfText);
		when(container.getDynamicChildren()).thenReturn(children);
		return container;
	}

	@Test
	public void detachedInstanceCollapsesToEmptyReads()
	{
		GameWidgets detached = GameWidgets.forTesting();

		assertEquals(List.of(), detached.childTexts(GROUP, CHILD));
		assertEquals(List.of(), detached.childTexts(PACKED));
		assertEquals(List.of(), detached.selfAndChildTexts(PACKED));
		assertFalse(detached.isWidgetOpen(PACKED));
	}

	@Test
	public void missingWidgetYieldsEmptyReads()
	{
		when(client.getWidget(GROUP, CHILD)).thenReturn(null);
		when(client.getWidget(PACKED)).thenReturn(null);

		assertEquals(List.of(), widgets.childTexts(GROUP, CHILD));
		assertEquals(List.of(), widgets.childTexts(PACKED));
		assertEquals(List.of(), widgets.selfAndChildTexts(PACKED));
		assertFalse(widgets.isWidgetOpen(PACKED));
	}

	@Test
	public void nullDynamicChildrenYieldsEmptyList()
	{
		Widget container = mock(Widget.class);
		when(container.getDynamicChildren()).thenReturn(null);
		when(client.getWidget(GROUP, CHILD)).thenReturn(container);

		assertEquals(List.of(), widgets.childTexts(GROUP, CHILD));
	}

	@Test
	public void childTextsReturnOrderedChildTexts()
	{
		Widget container = container(null, child("row zero"), child("row one"), child("row two"));
		when(client.getWidget(GROUP, CHILD)).thenReturn(container);
		when(client.getWidget(PACKED)).thenReturn(container);

		// The order is the menu order — a sort or dedup would corrupt the
		// first-row sentinel the consumers identify menus by.
		List<String> expected = List.of("row zero", "row one", "row two");
		assertEquals(expected, widgets.childTexts(GROUP, CHILD));
		assertEquals(expected, widgets.childTexts(PACKED));
	}

	@Test
	public void childTextsKeepPositionsOfNullChildrenAndNullTexts()
	{
		Widget nullText = child(null);
		Widget container = container(null, child("first"), null, nullText);
		when(client.getWidget(GROUP, CHILD)).thenReturn(container);

		// A null child slot or a null text keeps its index as a null
		// element — shifting positions would move the first-row sentinel.
		assertEquals(Arrays.asList("first", null, null), widgets.childTexts(GROUP, CHILD));
	}

	@Test
	public void selfAndChildTextsPutsContainerTextFirst()
	{
		Widget container = container("container row", child("keyed one"), child("keyed two"));
		when(client.getWidget(PACKED)).thenReturn(container);

		List<String> texts = widgets.selfAndChildTexts(PACKED);

		// Index 0 is the container's own text — the keyed-row contract the
		// nexus parse is built on.
		assertEquals(List.of("container row", "keyed one", "keyed two"), texts);
		assertEquals("container row", texts.get(0));
	}

	@Test
	public void selfAndChildTextsReadsSelfTextWithoutChildren()
	{
		Widget container = mock(Widget.class);
		when(container.getText()).thenReturn("only the container");
		when(container.getDynamicChildren()).thenReturn(null);
		when(client.getWidget(PACKED)).thenReturn(container);

		assertEquals(List.of("only the container"), widgets.selfAndChildTexts(PACKED));
	}

	@Test
	public void isWidgetOpenReflectsWidgetPresence()
	{
		Widget container = mock(Widget.class);
		when(client.getWidget(PACKED)).thenReturn(container);

		assertTrue(widgets.isWidgetOpen(PACKED));

		when(client.getWidget(PACKED)).thenReturn(null);

		assertFalse(widgets.isWidgetOpen(PACKED));
	}

	@Test
	public void offClientThreadThrowsImmediately()
	{
		// Any thread other than the current one stands in for the client
		// thread — every primitive must fail loudly, not race the client.
		when(client.getClientThread()).thenReturn(new Thread());

		assertThrows(IllegalStateException.class, () -> widgets.childTexts(GROUP, CHILD));
		assertThrows(IllegalStateException.class, () -> widgets.childTexts(PACKED));
		assertThrows(IllegalStateException.class, () -> widgets.selfAndChildTexts(PACKED));
		assertThrows(IllegalStateException.class, () -> widgets.isWidgetOpen(PACKED));
	}
}
