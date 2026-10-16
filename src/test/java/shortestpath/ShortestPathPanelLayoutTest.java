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

import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import shortestpath.settings.TeleportationItem;

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

	/**
	 * The {@code BorderLayout.NORTH} header strip of a category panel — it
	 * spans the full frame width above the scroll area.
	 */
	private static Container headerOf(CategoryPanel category)
	{
		return (Container) ((BorderLayout) category.getLayout())
			.getLayoutComponent(BorderLayout.NORTH);
	}

	/**
	 * True when {@code root} (or a descendant) is a label whose text contains
	 * {@code needle} — walks hint wrappers and other nested containers.
	 */
	private static boolean containsLabelText(Container root, String needle)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JLabel
				&& ((JLabel) child).getText() != null
				&& ((JLabel) child).getText().contains(needle))
			{
				return true;
			}
			if (child instanceof Container
				&& containsLabelText((Container) child, needle))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * True when the home content shows a category group header (the results
	 * view's {@code BRAND_ORANGE} category labels) rather than the tile grid.
	 */
	private boolean resultsContainHeader(String name)
	{
		return containsLabelText(contentOf(panel.home()), name);
	}

	@Test
	public void everyCategoryHasBackHeader()
	{
		newPanel();
		for (CategoryPanel category : panel.categories())
		{
			Container header = headerOf(category);
			assertNotNull(category.categoryName() + " header strip", header);
			Component west = ((BorderLayout) header.getLayout())
				.getLayoutComponent(BorderLayout.WEST);
			assertTrue(category.categoryName() + " back button",
				west instanceof JButton);
			JButton back = (JButton) west;
			assertEquals("Back", back.getToolTipText());
			assertNotNull("back button carries the rotated chevron",
				back.getIcon());

			panel.drillInto(category);
			assertSame(category,
				panel.muxer().getComponent(panel.muxer().getComponentCount() - 1));
			back.doClick();
			assertEquals("back pops " + category.categoryName() + " to home",
				1, panel.muxer().getComponentCount());
			assertSame(panel.home(), panel.muxer().getComponent(0));
		}
	}

	@Test
	public void ownedCheckboxOnRestrictionsHeader()
	{
		newPanel();
		CategoryPanel restrictions = panel.categories().get(2);
		assertEquals("Teleport Restrictions", restrictions.categoryName());

		Container header = headerOf(restrictions);
		Component east = ((BorderLayout) header.getLayout())
			.getLayoutComponent(BorderLayout.EAST);
		assertTrue("header EAST holds the Owned filter",
			east instanceof JCheckBox);
		JCheckBox owned = (JCheckBox) east;
		assertEquals("Owned", owned.getText());

		// The header spans the full 242px frame above the scroll area: laid
		// out there, the checkbox lands fully inside the visible strip...
		header.setSize(new Dimension(
			PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH,
			header.getPreferredSize().height));
		header.doLayout();
		assertTrue("Owned checkbox fully inside the header strip",
			owned.getBounds().getMaxX()
				<= PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH);

		// ...and it lives on the header, never inside the scroll view where
		// the 17px scrollbar column could cover it.
		assertSame(header, owned.getParent());
		JScrollPane scroll = findScrollPane(restrictions);
		assertNotNull(scroll);
		assertFalse("Owned checkbox is not a scroll-view descendant",
			SwingUtilities.isDescendingFrom(owned,
				scroll.getViewport().getView()));
	}

	@Test
	public void statusRefreshOnExternalChange() throws Exception
	{
		newPanel();
		JLabel transportsStatus = (JLabel) tileNamed("Transports").getComponent(1);
		JLabel bankStatus = (JLabel) tileNamed("Bank").getComponent(1);
		assertEquals("Off", bankStatus.getText());
		int onBefore = Integer.parseInt(
			transportsStatus.getText().split("/")[0]);

		// The enum-combo card's key: NONE turns that one master off, which
		// must reach the tile through the external-change path.
		config.setUseTeleportationItemsValue(TeleportationItem.NONE);
		settings.onConfigChanged(event("useTeleportationItems"));
		flushEdt();
		assertEquals("Transports tile tracks the external edit",
			(onBefore - 1) + "/" + panel.transportCards().size() + " on",
			transportsStatus.getText());

		config.setIncludeBankPathValue(true);
		settings.onConfigChanged(event("includeBankPath"));
		flushEdt();
		assertEquals("Bank tile tracks the external edit", "On",
			bankStatus.getText());
	}

	@Test
	public void deepLinkRoundTrip() throws Exception
	{
		newPanel();
		CategoryPanel transports = panel.categories().get(1);

		setSearchText("quetz");
		assertSame("a query pops back to the home surface",
			panel.home(),
			panel.muxer().getComponent(panel.muxer().getComponentCount() - 1));
		assertTrue("results view groups under the Transports header",
			resultsContainHeader("Transports"));

		List<ShortestPathPanel.SearchRow> matches =
			panel.matchingRows(transports, "quetz");
		assertFalse(matches.isEmpty());
		panel.deepLink(transports, matches.get(0));
		assertSame("deep link pushes the owning category",
			transports,
			panel.muxer().getComponent(panel.muxer().getComponentCount() - 1));
		assertEquals("filtered landing shows only the matching rows",
			matches.size(), contentOf(transports).getComponentCount());
		assertEquals("query survives the deep link",
			"quetz", panel.getCurrentQuery());

		panel.backToHome();
		assertSame("back returns to the results view",
			panel.home(),
			panel.muxer().getComponent(panel.muxer().getComponentCount() - 1));
		assertTrue("results view still groups under Transports",
			resultsContainHeader("Transports"));
		assertEquals("quetz", panel.getCurrentQuery());

		setSearchText("");
		assertEquals("clearing restores the tile grid",
			6, tileGrid().getComponentCount());
		assertEquals("clearing restores the drilled category's full rows",
			transports.rows().size(), contentOf(transports).getComponentCount());
	}

	@Test
	public void noMatchShowsHint() throws Exception
	{
		newPanel();
		setSearchText("zzzzz");
		assertSame(panel.home(),
			panel.muxer().getComponent(panel.muxer().getComponentCount() - 1));
		assertTrue("no-match renders the pinned hint copy",
			containsLabelText(contentOf(panel.home()),
				"No options matching"));

		setSearchText("");
		assertEquals("clearing restores the tile grid",
			6, tileGrid().getComponentCount());
	}

	@Test
	public void itemsDisabledHintSurfaces() throws Exception
	{
		newPanel();
		CategoryPanel restrictions = panel.categories().get(2);

		config.setUseTeleportationItemsValue(TeleportationItem.NONE);
		settings.onConfigChanged(event("useTeleportationItems"));
		flushEdt();

		restrictions.applyQuery("");
		FixedWidthPanel content = contentOf(restrictions);
		assertSame("restrictions list stays visible carrying the hint",
			panel.restrictionListPanel(), content.getComponent(0));
		assertTrue("items-disabled hint replaces the rows",
			containsLabelText(content,
				ShortestPathPanel.RESTRICTIONS_DISABLED_HINT));
	}
}
