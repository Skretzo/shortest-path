package shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.Test;

import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import shortestpath.settings.Settings;

/**
 * Headless structure tests pinning the tile drill-down layout contract:
 * the six-tile home rooted in the muxer, drill/back stack mechanics, every
 * scroll view pinned to the 225px viewport (so the scrollbar never covers
 * content or the master toggles), a real left inset on every scroll
 * surface, live tile statuses, and the grouped search results + filtered
 * deep-link seam.
 *
 * <p>The harness wraps {@link TestShortestPathConfig} through the
 * {@link Settings#wrap} seam — reads and listeners work, writes are inert.
 * {@code panel.onActivate()} runs once after construction because
 * {@code MultiplexingPluginPanel} only dispatches child activation while
 * its own active flag is set (the sidebar sets it in production); without
 * it pushes swap the visible card but never run the deep-link
 * {@code applyQuery} path under test.</p>
 */
public class ShortestPathPanelLayoutTest
{
	private TestShortestPathConfig config;
	private Settings settings;
	private ShortestPathPanel panel;

	private ShortestPathPanel newPanel()
	{
		config = new TestShortestPathConfig();
		settings = Settings.wrap(config);
		Client client = mock(Client.class);
		ClientThread clientThread = mock(ClientThread.class);
		ItemManager itemManager = mock(ItemManager.class);
		when(itemManager.getImage(anyInt())).thenAnswer(
			i -> new AsyncBufferedImage(clientThread, 16, 16, BufferedImage.TYPE_INT_ARGB));
		panel = new ShortestPathPanel(settings, client, clientThread, itemManager);
		panel.onActivate();
		return panel;
	}

	private static void flushEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	private static ConfigChanged event(String key)
	{
		ConfigChanged changed = new ConfigChanged();
		changed.setGroup(Settings.CONFIG_GROUP);
		changed.setKey(key);
		return changed;
	}

	private static JScrollPane findScrollPane(Container root)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JScrollPane)
			{
				return (JScrollPane) child;
			}
			if (child instanceof Container)
			{
				JScrollPane found = findScrollPane((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * The content {@link FixedWidthPanel} inside a category/home panel's
	 * scroll wrap: view → BorderLayout.NORTH child.
	 */
	private static FixedWidthPanel contentOf(JComponent surface)
	{
		JScrollPane scroll = findScrollPane(surface);
		assertNotNull("scroll pane exists", scroll);
		JComponent view = (JComponent) scroll.getViewport().getView();
		return (FixedWidthPanel) view.getComponent(0);
	}

	/**
	 * Every scroll surface in the panel tree: the home view plus one per
	 * category.
	 */
	private List<JComponent> surfaces()
	{
		List<JComponent> surfaces = new ArrayList<>(panel.categories());
		surfaces.add(panel.home());
		return surfaces;
	}

	private JPanel tileGrid()
	{
		FixedWidthPanel content = contentOf(panel.home());
		assertEquals("tile view shows one grid", 1, content.getComponentCount());
		return (JPanel) content.getComponent(0);
	}

	private JPanel tileNamed(String name)
	{
		for (Component child : tileGrid().getComponents())
		{
			JLabel nameLabel = (JLabel) ((JPanel) child).getComponent(0);
			if (name.equals(nameLabel.getText()))
			{
				return (JPanel) child;
			}
		}
		return null;
	}

	@Test
	public void homeIsMuxRootWithSixTiles()
	{
		newPanel();

		assertEquals(1, panel.muxer().getComponentCount());
		assertSame(panel.home(), panel.muxer().getComponent(0));

		String[] expected = {"General", "Transports", "Teleport Restrictions",
			"Unlocks", "Bank", "Player-Owned House"};
		JPanel grid = tileGrid();
		assertEquals(6, grid.getComponentCount());
		for (int i = 0; i < expected.length; i++)
		{
			JPanel tile = (JPanel) grid.getComponent(i);
			JLabel nameLabel = (JLabel) tile.getComponent(0);
			JLabel statusLabel = (JLabel) tile.getComponent(1);
			assertEquals(expected[i], nameLabel.getText());
			assertFalse(expected[i] + " status is populated",
				statusLabel.getText().isEmpty());
		}
	}

	@Test
	public void drillPushAndBack()
	{
		newPanel();
		CategoryPanel transports = panel.categories().get(1);
		assertEquals("Transports", transports.categoryName());

		panel.drillInto(transports);
		assertEquals(2, panel.muxer().getComponentCount());
		assertSame(transports, panel.muxer().getComponent(1));

		panel.backToHome();
		assertEquals(1, panel.muxer().getComponentCount());
		assertSame(panel.home(), panel.muxer().getComponent(0));
	}

	@Test
	public void everyScrollViewIsViewportWidth()
	{
		newPanel();
		for (JComponent surface : surfaces())
		{
			JScrollPane scroll = findScrollPane(surface);
			JComponent view = (JComponent) scroll.getViewport().getView();
			assertTrue("scroll view is a FixedWidthPanel",
				view instanceof FixedWidthPanel);
			assertEquals("view pinned to viewport width",
				PluginPanel.PANEL_WIDTH, view.getPreferredSize().width);
			FixedWidthPanel content = (FixedWidthPanel) view.getComponent(0);
			assertEquals("content pinned to viewport width",
				PluginPanel.PANEL_WIDTH, content.getPreferredSize().width);
		}
	}

	@Test
	public void contentHasLeftMargin()
	{
		newPanel();
		for (JComponent surface : surfaces())
		{
			FixedWidthPanel content = contentOf(surface);
			Insets insets = content.getBorder().getBorderInsets(content);
			assertTrue("left inset is a real margin, not flush to the edge",
				insets.left >= 4);
		}
	}

	@Test
	public void masterControlsInsideViewport()
	{
		newPanel();
		CategoryPanel transports = panel.categories().get(1);
		int cards = 0;
		for (ShortestPathPanel.SearchRow row : transports.rows())
		{
			if (!(row.component instanceof TransportFamilyCard))
			{
				continue;
			}
			cards++;
			TransportFamilyCard card = (TransportFamilyCard) row.component;
			JComponent master = card.getMasterControl();
			assertNotNull(row.display + " master control", master);

			card.setSize(new Dimension(PluginPanel.PANEL_WIDTH,
				card.getPreferredSize().height));
			card.doLayout();
			Rectangle bounds = SwingUtilities.convertRectangle(
				master.getParent(), master.getBounds(), card);
			assertTrue(row.display + " master control is not under the scrollbar",
				bounds.getMaxX() <= PluginPanel.PANEL_WIDTH);
		}
		assertEquals("all ~21 transport cards checked", 23, cards);
	}

	@Test
	public void statusLinesLive() throws Exception
	{
		newPanel();
		JLabel bankStatus = (JLabel) tileNamed("Bank").getComponent(1);
		assertEquals("Off", bankStatus.getText());

		config.setIncludeBankPathValue(true);
		settings.onConfigChanged(event("includeBankPath"));
		flushEdt();

		assertEquals("Bank tile tracks the external edit", "On", bankStatus.getText());
	}

	/**
	 * {@code IconTextField.setText} asserts the EDT; the document listener
	 * fires synchronously inside the call, so when this returns the search
	 * handler has already rebuilt the visible view.
	 */
	private void setSearchText(String text) throws Exception
	{
		SwingUtilities.invokeAndWait(() -> panel.searchBar().setText(text));
	}

	@Test
	public void searchResultsAndDeepLink() throws Exception
	{
		newPanel();
		CategoryPanel transports = panel.categories().get(1);

		setSearchText("quetzal");
		assertSame("a query pops back to the home surface",
			panel.home(), panel.muxer().getComponent(panel.muxer().getComponentCount() - 1));

		FixedWidthPanel homeContent = contentOf(panel.home());
		boolean sawTransportsHeader = false;
		for (Component child : homeContent.getComponents())
		{
			if (child instanceof JLabel
				&& "Transports".equals(((JLabel) child).getText()))
			{
				sawTransportsHeader = true;
			}
		}
		assertTrue("results view groups under the Transports header",
			sawTransportsHeader);

		List<ShortestPathPanel.SearchRow> matches = panel.matchingRows(transports, "quetzal");
		assertFalse(matches.isEmpty());
		panel.deepLink(transports, matches.get(0));
		assertSame(transports,
			panel.muxer().getComponent(panel.muxer().getComponentCount() - 1));
		assertEquals("deep link lands filtered to the match",
			matches.size(), contentOf(transports).getComponentCount());

		setSearchText("");
		assertEquals("clearing restores the tile grid",
			6, tileGrid().getComponentCount());
		assertEquals("clearing restores the drilled category's full rows",
			transports.rows().size(), contentOf(transports).getComponentCount());
	}
}
