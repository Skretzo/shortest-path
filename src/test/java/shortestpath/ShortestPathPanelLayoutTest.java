package shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.Test;

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import shortestpath.items.ItemStateService;
import shortestpath.poh.PohNexusPortal;
import shortestpath.settings.Settings;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.BalloonRoute;

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
	// Every item id passed to ItemManager.getImage while the panel builds —
	// cards and tiles request their sprite through loadItemIcon, so an
	// iconless control shows up as a missing request here.
	private final List<Integer> iconRequests = new ArrayList<>();

	private ShortestPathPanel newPanel()
	{
		return newPanel(ItemStateService.forTesting());
	}

	private ShortestPathPanel newPanel(ItemStateService itemState)
	{
		iconRequests.clear();
		config = new TestShortestPathConfig();
		// A mocked ConfigManager routes Settings.write back into the stub
		// config, so panel-side writes (restriction toggles) round-trip the
		// way production's real manager does — including the synchronous
		// same-key listener echo.
		net.runelite.client.config.ConfigManager configManager =
			mock(net.runelite.client.config.ConfigManager.class);
		doAnswer(i ->
		{
			if ("blockedTeleportItems".equals(i.getArgument(1)))
			{
				Object value = i.getArgument(2);
				config.setBlockedTeleportItemsValue(value == null ? "" : String.valueOf(value));
			}
			else if ("balloonLogBasketRoutes".equals(i.getArgument(1)))
			{
				Object value = i.getArgument(2);
				@SuppressWarnings("unchecked")
				Set<BalloonRoute> routes = (Set<BalloonRoute>) value;
				config.setBalloonLogBasketRoutesValue(routes);
			}
			else if ("pohNexusPortals".equals(i.getArgument(1)))
			{
				Object value = i.getArgument(2);
				@SuppressWarnings("unchecked")
				Set<PohNexusPortal> portals = (Set<PohNexusPortal>) value;
				config.setPohNexusPortalsValue(portals);
			}
			return null;
		}).when(configManager).setConfiguration(
			any(String.class), any(String.class), any(Object.class));
		settings = new Settings(configManager, config);
		Client client = mock(Client.class);
		ClientThread clientThread = mock(ClientThread.class);
		// The Owned filter's container reads post through clientThread.invoke;
		// run them inline so a refresh completes on the test thread, leaving
		// only the Swing rebuild for flushEdt().
		doAnswer(i ->
		{
			((Runnable) i.getArgument(0)).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		ItemManager itemManager = mock(ItemManager.class);
		when(itemManager.getImage(anyInt())).thenAnswer(i ->
		{
			iconRequests.add((Integer) i.getArgument(0));
			return new AsyncBufferedImage(clientThread, 16, 16, BufferedImage.TYPE_INT_ARGB);
		});
		panel = new ShortestPathPanel(settings, client, clientThread, itemManager, itemState);
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

	/**
	 * The grid tile for {@code name}: whichever grid child carries a label
	 * with that exact text. Shape-agnostic so it holds across the tile
	 * restyle (plain panel vs. button component).
	 */
	private JComponent tileNamed(String name)
	{
		for (Component child : tileGrid().getComponents())
		{
			if (child instanceof Container
				&& containsLabelEqual((Container) child, name))
			{
				return (JComponent) child;
			}
		}
		return null;
	}

	/**
	 * The tile's live status line — the last {@link JLabel} in the tile
	 * subtree (icon/name sit above it in every tile shape).
	 */
	private static JLabel statusLabelOf(JComponent tile)
	{
		JLabel found = null;
		for (Component child : tile.getComponents())
		{
			if (child instanceof JLabel)
			{
				found = (JLabel) child;
			}
			if (child instanceof Container)
			{
				JLabel nested = statusLabelOf((JComponent) child);
				if (nested != null)
				{
					found = nested;
				}
			}
		}
		return found;
	}

	/** Every {@link JLabel} anywhere under {@code root}. */
	private static List<JLabel> labelsIn(Container root)
	{
		List<JLabel> labels = new ArrayList<>();
		for (Component child : root.getComponents())
		{
			if (child instanceof JLabel)
			{
				labels.add((JLabel) child);
			}
			if (child instanceof Container)
			{
				labels.addAll(labelsIn((Container) child));
			}
		}
		return labels;
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
			JComponent tile = (JComponent) grid.getComponent(i);
			assertTrue(expected[i] + " tile carries its name",
				containsLabelEqual(tile, expected[i]));
			JLabel statusLabel = statusLabelOf(tile);
			assertNotNull(expected[i] + " status line", statusLabel);
			assertFalse(expected[i] + " status is populated",
				statusLabel.getText().isEmpty());
		}
	}

	/**
	 * The home tiles are large square buttons: laid out at the fixed panel
	 * width each tile's bounds are square, it is a real {@link JButton}
	 * (Swing's armed/pressed model drives the press state), and it carries
	 * the icon + name + status trio of labels.
	 */
	@Test
	public void tilesAreSquareRaisedIconButtons()
	{
		newPanel();
		JPanel grid = tileGrid();
		grid.setSize(new Dimension(PluginPanel.PANEL_WIDTH - 16,
			grid.getPreferredSize().height));
		grid.doLayout();

		for (Component child : grid.getComponents())
		{
			assertTrue("tile is a raised button with Swing's armed/pressed model",
				child instanceof JButton);
			JComponent tile = (JComponent) child;
			assertTrue("tile bounds are square at the fixed panel width",
				Math.abs(tile.getBounds().width - tile.getBounds().height) <= 2);
			assertTrue("tile carries the icon + name + status labels",
				labelsIn(tile).size() >= 3);
		}

		assertTrue("tiles request their category item sprites",
			iconRequests.contains(ItemID.TRAIL_SEXTANT)
				&& iconRequests.contains(ItemID.CANOEING_PADDLE)
				&& iconRequests.contains(ItemID.RING_OF_DUELING_8)
				&& iconRequests.contains(ItemID.CRYSTAL_KEY)
				&& iconRequests.contains(ItemID.COINS)
				&& iconRequests.contains(ItemID.POH_TABLET_TELEPORTTOHOUSE));
	}

	@Test
	public void tileClickDrillsIntoCategory() throws Exception
	{
		newPanel();
		CategoryPanel unlocks = panel.categories().get(3);
		JComponent tile = tileNamed("Unlocks");
		assertNotNull(tile);
		assertTrue("tile fires like a button", tile instanceof JButton);

		((JButton) tile).doClick();
		flushEdt();
		assertSame("clicking a tile drills into its category",
			unlocks,
			panel.muxer().getComponent(panel.muxer().getComponentCount() - 1));
		panel.backToHome();
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
			if (master == null)
			{
				// The teleport-items card is masterless — its mode combo
				// lives in General; every other card carries a master.
				assertEquals("only the teleport-items card is masterless",
					"useTeleportationItems", card.getKeyName());
				continue;
			}

			card.setSize(new Dimension(PluginPanel.PANEL_WIDTH,
				card.getPreferredSize().height));
			card.doLayout();
			Rectangle bounds = SwingUtilities.convertRectangle(
				master.getParent(), master.getBounds(), card);
			assertTrue(row.display + " master control is not under the scrollbar",
				bounds.getMaxX() <= PluginPanel.PANEL_WIDTH);
		}
		assertEquals("all transport cards checked", 23, cards);
	}

	private TransportFamilyCard transportCardByKey(String keyName)
	{
		for (TransportFamilyCard card : panel.transportCards())
		{
			if (keyName.equals(card.getKeyName()))
			{
				return card;
			}
		}
		return null;
	}

	private static List<JComboBox<?>> combosIn(Container root)
	{
		List<JComboBox<?>> combos = new ArrayList<>();
		for (Component child : root.getComponents())
		{
			if (child instanceof JComboBox)
			{
				combos.add((JComboBox<?>) child);
			}
			if (child instanceof Container)
			{
				combos.addAll(combosIn((Container) child));
			}
		}
		return combos;
	}

	/**
	 * The {@link JComboBox} under {@code root} whose item list contains
	 * {@code item} — identifies which enum a combo binds rather than just
	 * that some combo exists.
	 */
	private static JComboBox<?> comboContaining(Container root, Object item)
	{
		for (JComboBox<?> combo : combosIn(root))
		{
			for (int i = 0; i < combo.getItemCount(); i++)
			{
				if (item.equals(combo.getItemAt(i)))
				{
					return combo;
				}
			}
		}
		return null;
	}

	@Test
	public void teleportationItemsModeComboLivesInGeneral()
	{
		newPanel();
		CategoryPanel general = panel.categories().get(0);
		assertEquals("General", general.categoryName());

		assertNotNull("the teleport-items mode combo renders in General",
			comboContaining(contentOf(general), TeleportationItem.NONE));
	}

	@Test
	public void teleportationItemsCardIsMasterless()
	{
		newPanel();
		TransportFamilyCard card = transportCardByKey("useTeleportationItems");
		assertNotNull("teleport-items transports card", card);

		assertNull("the card carries no master control — the mode combo "
				+ "lives in General", card.getMasterControl());
		assertTrue("mode != NONE counts the card as on", card.isMasterOn());

		card.setDetailOpen(true);
		assertTrue("masterless card still expands its detail",
			card.isDetailOpen());
		assertTrue("the detail keeps its threshold rows",
			card.getComponentCount() > 1);
	}

	@Test
	public void everyTransportCardRequestsAnIcon()
	{
		newPanel();
		// A card built with a null icon id never reaches getImage — the
		// requested ids therefore prove every card carries a sprite,
		// including the nine that were iconless.
		int[] requested = {
			ItemID.AHOY_TOY_BOAT, ItemID.SHILOSHIPTICKET,
			ItemID.GRANDTREE_WARSHIP, ItemID.ALUFT_GNOME_GOGGLES,
			ItemID.ZEP_TOY_BALLOON_RED, ItemID.EVENT_MUSHTREE_DUMMY,
			ItemID.POH_LEVER_OAK, ItemID.POH_DUMMY_PORTALNEXUS,
			ItemID.POH_SUPERIOR_GARDEN_WILDERNESS_OBELISK,
		};
		for (int id : requested)
		{
			assertTrue("card icon " + id + " was requested",
				iconRequests.contains(id));
		}
	}

	/**
	 * Long labels must wrap at the 225px content width instead of clipping:
	 * every row-type label is {@code <html>}-wrapped so the layout breaks it
	 * onto multiple lines, and a long label constrained to the row width
	 * actually grows in height rather than staying one clipped line.
	 */
	@Test
	public void labelsWrapInsteadOfClipping() throws Exception
	{
		newPanel();
		flushEdt();

		JLabel rowLabel = labelContaining(
			contentOf(panel.categories().get(0)), "avoidWilderness");
		assertNotNull("General check row label", rowLabel);
		assertTrue("row labels are html-wrapped",
			rowLabel.getText().startsWith("<html>"));

		TransportFamilyCard card = transportCardByKey("useAgilityShortcuts");
		assertNotNull(card);
		assertTrue("card name labels are html-wrapped",
			card.getNameLabel().getText().startsWith("<html>"));

		JPanel row = restrictionRowByName("Ardougne Cloaks");
		assertNotNull(row);
		assertNotNull("family name is rendered Title-Case",
			labelContaining(row, "Ardougne Cloaks"));
		expandRow(row);
		flushEdt();
		JCheckBox option = optionBox(row, "Kandarin Monastery");
		assertNotNull(option);
		assertTrue("option labels are html-wrapped",
			option.getText().startsWith("<html>"));

		// Behavioral wrap check: the items-disabled hint is the longest
		// label in the panel — sized to row width it must grow past one
		// line (wrapped) rather than clip.
		config.setUseTeleportationItemsValue(TeleportationItem.NONE);
		settings.onConfigChanged(event("useTeleportationItems"));
		flushEdt();
		CategoryPanel restrictions = panel.categories().get(2);
		restrictions.applyQuery("");
		JLabel hint = labelContaining(contentOf(restrictions),
			"Teleportation items are disabled");
		assertNotNull("items-disabled hint", hint);
		hint.setSize(200, 0);
		int oneLine = hint.getFontMetrics(hint.getFont()).getHeight();
		assertTrue("the hint wraps onto multiple lines inside the viewport",
			hint.getPreferredSize().height > oneLine);
	}

	@Test
	public void statusLinesLive() throws Exception
	{
		newPanel();
		JLabel bankStatus = statusLabelOf(tileNamed("Bank"));
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
		JLabel transportsStatus = statusLabelOf(tileNamed("Transports"));
		JLabel bankStatus = statusLabelOf(tileNamed("Bank"));
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
	public void restrictionTileCountsEveryRecordKind() throws Exception
	{
		newPanel();
		flushEdt();
		JLabel status = statusLabelOf(tileNamed("Teleport Restrictions"));
		assertEquals("All allowed", status.getText());

		// A rep@dest option block alone is an active restriction even
		// though no bare item id is blocked.
		int farmDest = WorldPointUtil.packWorldPoint(2665, 3374, 0);
		config.setBlockedTeleportItemsValue("13121@" + farmDest);
		settings.onConfigChanged(event("blockedTeleportItems"));
		flushEdt();
		assertEquals("an option block counts", "1 restricted", status.getText());

		config.setBlockedTeleportItemsValue("13121:5");
		settings.onConfigChanged(event("blockedTeleportItems"));
		flushEdt();
		assertEquals("a threshold override counts", "1 restricted", status.getText());

		config.setBlockedTeleportItemsValue("13121,13121:5");
		settings.onConfigChanged(event("blockedTeleportItems"));
		flushEdt();
		assertEquals("block and threshold sum", "2 restricted", status.getText());
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
	public void categoryNameQueryListsTheRestrictionFamilies() throws Exception
	{
		newPanel();
		// "restrictions" hits the category's own name but no family label:
		// the results view must expand the checklist like the drilled
		// category does rather than render a dangling header over zero rows.
		setSearchText("restrictions");

		FixedWidthPanel homeContent = contentOf(panel.home());
		assertTrue("name-hit query groups under the category header",
			resultsContainHeader("Teleport Restrictions"));
		assertTrue("and lists families beneath it",
			containsLabelText(homeContent, "Ardougne Cloaks"));
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

	private static ItemContainer containerWith(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		doReturn(items).when(container).getItems();
		return container;
	}

	@Test
	public void ownedFilterUnionsTheLastSeenBankSnapshot() throws Exception
	{
		// The Ardougne-cloaks family (member ids 13121..13124, 20760) exists
		// only in the remembered bank snapshot — every live container is
		// empty (the client mock serves nulls), so the family counts as
		// owned only if the filter unions the service's persisted record.
		ItemStateService itemState = ItemStateService.forTesting();
		itemState.noteLastSeenBank(containerWith(new Item(13121, 1)), true);
		newPanel(itemState);
		flushEdt();

		RestrictionListPanel restrictions = panel.restrictionListPanel();
		restrictions.setOwnedOnly(true);
		flushEdt();

		assertTrue("bank-snapshot-only families stay listed under the Owned filter",
			restrictions.matchingLabels("").contains("Ardougne Cloaks"));

		restrictions.setOwnedOnly(false);
		flushEdt();
	}

	@Test
	public void ownedFilterEmptyEverywhereDropsEveryFamily() throws Exception
	{
		// Control for the union assertion above: with nothing owned in any
		// source (empty snapshot, null live containers) no family survives.
		newPanel();
		flushEdt();

		RestrictionListPanel restrictions = panel.restrictionListPanel();
		restrictions.setOwnedOnly(true);
		flushEdt();

		assertTrue("with nothing owned anywhere the owned list is empty",
			restrictions.matchingLabels("").isEmpty());
	}

	// ---- Teleport-restriction option checkboxes ---------------------------

	/**
	 * The restriction row carrying the family name {@code familyName} — a
	 * direct child of the list panel found by an exact-label match so
	 * lookalike names ("Slayer ring" vs "Slayer ring (1-8)") stay distinct.
	 */
	private JPanel restrictionRowByName(String familyName)
	{
		for (Component child : panel.restrictionListPanel().getComponents())
		{
			if (child instanceof JPanel
				&& containsLabelEqual((Container) child, familyName))
			{
				return (JPanel) child;
			}
		}
		return null;
	}

	/** Strips the {@code <html>} wrap (and any centering tag inside it) that
	 *  longer labels carry so they wrap at the fixed viewport width —
	 *  assertions compare the visible text only. */
	private static String stripHtml(String text)
	{
		if (text == null)
		{
			return null;
		}
		return text.replace("<html>", "").replace("</html>", "")
			.replace("<center>", "").replace("</center>", "");
	}

	private static boolean containsLabelEqual(Container root, String text)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JLabel && text.equals(stripHtml(((JLabel) child).getText())))
			{
				return true;
			}
			if (child instanceof Container
				&& containsLabelEqual((Container) child, text))
			{
				return true;
			}
		}
		return false;
	}

	/** The first {@link JLabel} under {@code root} containing {@code needle}. */
	private static JLabel labelContaining(Container root, String needle)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JLabel
				&& ((JLabel) child).getText() != null
				&& ((JLabel) child).getText().contains(needle))
			{
				return (JLabel) child;
			}
			if (child instanceof Container)
			{
				JLabel found = labelContaining((Container) child, needle);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * Every labeled checkbox inside {@code root}: the option checkboxes
	 * carry the option's in-game teleport name while the master allow
	 * checkbox is textless, so text is the discriminator.
	 */
	private static List<JCheckBox> labeledCheckboxesIn(Container root)
	{
		List<JCheckBox> boxes = new ArrayList<>();
		for (Component child : root.getComponents())
		{
			if (child instanceof JCheckBox)
			{
				JCheckBox box = (JCheckBox) child;
				if (box.getText() != null && !box.getText().isEmpty())
				{
					boxes.add(box);
				}
			}
			else if (child instanceof Container)
			{
				boxes.addAll(labeledCheckboxesIn((Container) child));
			}
		}
		return boxes;
	}

	private static JCheckBox optionBox(Container row, String label)
	{
		for (JCheckBox box : labeledCheckboxesIn(row))
		{
			if (label.equals(stripHtml(box.getText())))
			{
				return box;
			}
		}
		return null;
	}

	/**
	 * The row's master allow checkbox — the textless JCheckBox in the header
	 * controls.
	 */
	private static JCheckBox masterBox(Container root)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JCheckBox
				&& (((JCheckBox) child).getText() == null
					|| ((JCheckBox) child).getText().isEmpty()))
			{
				return (JCheckBox) child;
			}
			if (child instanceof Container)
			{
				JCheckBox found = masterBox((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** Opens the row's detail well via its expander button. */
	private static void expandRow(Container row) throws Exception
	{
		SwingUtilities.invokeAndWait(() -> clickFirstButton(row));
	}

	private static boolean clickFirstButton(Container root)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JButton)
			{
				((JButton) child).doClick();
				return true;
			}
			if (child instanceof Container && clickFirstButton((Container) child))
			{
				return true;
			}
		}
		return false;
	}

	private static void click(JCheckBox box) throws Exception
	{
		SwingUtilities.invokeAndWait(box::doClick);
	}

	@Test
	public void ardougneCloaksRowRendersItsTwoOptionCheckboxes() throws Exception
	{
		newPanel();
		flushEdt();

		JPanel row = restrictionRowByName("Ardougne Cloaks");
		assertNotNull("Ardougne cloaks row", row);
		expandRow(row);
		flushEdt();

		List<JCheckBox> boxes = labeledCheckboxesIn(row);
		assertEquals("one checkbox per option", 2, boxes.size());
		assertNotNull("Kandarin Monastery option", optionBox(row, "Kandarin Monastery"));
		assertNotNull("Ardougne Farm option", optionBox(row, "Ardougne Farm"));
		assertTrue("unchecked options mean allowed",
			optionBox(row, "Kandarin Monastery").isSelected());
		assertTrue(optionBox(row, "Ardougne Farm").isSelected());
		assertTrue("the Options block carries its header",
			containsLabelEqual(row, "Options:"));
	}

	@Test
	public void skillsNecklaceGroupsTheFarmingGuildArrivalsIntoOneCheckbox() throws Exception
	{
		newPanel();
		flushEdt();

		JPanel row = restrictionRowByName("Skills Necklace (1-6)");
		assertNotNull(row);
		expandRow(row);
		flushEdt();

		List<JCheckBox> boxes = labeledCheckboxesIn(row);
		assertEquals("six guild options", 6, boxes.size());
		int farmingGuild = 0;
		for (JCheckBox box : boxes)
		{
			if ("Farming Guild".equals(stripHtml(box.getText())))
			{
				farmingGuild++;
			}
		}
		assertEquals("the inside/outside arrivals collapse to one option",
			1, farmingGuild);
	}

	@Test
	public void teleportToHouseRendersInsideAndOutsideOnly() throws Exception
	{
		newPanel();
		flushEdt();

		JPanel row = restrictionRowByName("Teleport To House");
		assertNotNull(row);
		expandRow(row);
		flushEdt();

		List<JCheckBox> boxes = labeledCheckboxesIn(row);
		assertEquals("the ten portal towns collapse to two options",
			2, boxes.size());
		assertNotNull(optionBox(row, "Inside the house"));
		assertNotNull(optionBox(row, "Outside the house"));
	}

	@Test
	public void singleOptionFamilyRendersNoOptionsBlock() throws Exception
	{
		newPanel();
		flushEdt();

		JPanel row = restrictionRowByName("Fremennik Sea Boots");
		assertNotNull(row);
		expandRow(row);
		flushEdt();

		assertTrue("a one-option family has no option checkboxes",
			labeledCheckboxesIn(row).isEmpty());
		assertFalse("and no Options header",
			containsLabelEqual(row, "Options:"));
	}

	@Test
	public void optionCheckboxWritesAndRemovesTheRepDestRecord() throws Exception
	{
		newPanel();
		flushEdt();

		JPanel row = restrictionRowByName("Ardougne Cloaks");
		assertNotNull(row);
		expandRow(row);
		flushEdt();

		JCheckBox farm = optionBox(row, "Ardougne Farm");
		assertNotNull(farm);
		int farmDest = WorldPointUtil.packWorldPoint(2665, 3374, 0);

		click(farm);
		flushEdt();
		assertTrue("unchecking writes rep@canonicalDest",
			config.blockedTeleportItems().contains("13121@" + farmDest));

		click(farm);
		flushEdt();
		assertFalse("re-checking removes the record",
			config.blockedTeleportItems().contains("13121@" + farmDest));
		assertFalse("empty rep keys never serialize",
			config.blockedTeleportItems().contains("@"));
	}

	@Test
	public void masterBlockSuppressesOptionRecordsInCsv() throws Exception
	{
		newPanel();
		flushEdt();

		JPanel row = restrictionRowByName("Ardougne Cloaks");
		assertNotNull(row);
		expandRow(row);
		flushEdt();

		JCheckBox farm = optionBox(row, "Ardougne Farm");
		assertNotNull("Ardougne Farm option checkbox", farm);
		click(farm);
		flushEdt();
		assertTrue(config.blockedTeleportItems().contains("@"));

		click(masterBox(row));
		flushEdt();
		String csv = config.blockedTeleportItems();
		assertTrue("the master block writes bare member ids",
			csv.contains("13121") && csv.contains("20760"));
		assertFalse("a blocked family never emits rep@dest records",
			csv.contains("@"));
	}

	@Test
	public void optionLabelsJoinTheRowSearchText() throws Exception
	{
		newPanel();
		flushEdt();

		assertTrue("an option-label query surfaces the family",
			panel.restrictionListPanel().matchingLabels("farm")
				.contains("Ardougne Cloaks"));
	}

	@Test
	public void externalOptionBlockResyncsTheCheckboxes() throws Exception
	{
		newPanel();
		flushEdt();

		int farmDest = WorldPointUtil.packWorldPoint(2665, 3374, 0);
		config.setBlockedTeleportItemsValue("13121@" + farmDest);
		settings.onConfigChanged(event("blockedTeleportItems"));
		flushEdt();

		JPanel row = restrictionRowByName("Ardougne Cloaks");
		assertNotNull(row);
		JCheckBox farm = optionBox(row, "Ardougne Farm");
		JCheckBox monastery = optionBox(row, "Kandarin Monastery");
		assertNotNull("Ardougne Farm option checkbox", farm);
		assertNotNull("Kandarin Monastery option checkbox", monastery);
		assertFalse("external record unchecks the option", farm.isSelected());
		assertTrue("sibling options stay allowed", monastery.isSelected());

		config.setBlockedTeleportItemsValue("");
		settings.onConfigChanged(event("blockedTeleportItems"));
		flushEdt();
		assertTrue("clearing restores the allowed state", farm.isSelected());
	}

	@Test
	public void nonCanonicalOptionBlockResyncsTheCheckbox() throws Exception
	{
		newPanel();
		flushEdt();

		// The Farming Guild option group covers two arrivals; the stored
		// record names the non-canonical member — the engine expands it to
		// the whole group, so the checkbox must deselect too.
		int insideGuild = WorldPointUtil.packWorldPoint(1248, 3725, 0);
		config.setBlockedTeleportItemsValue("11105@" + insideGuild);
		settings.onConfigChanged(event("blockedTeleportItems"));
		flushEdt();

		JPanel row = restrictionRowByName("Skills Necklace (1-6)");
		assertNotNull(row);
		expandRow(row);
		flushEdt();
		JCheckBox guild = optionBox(row, "Farming Guild");
		assertNotNull("Farming Guild option checkbox", guild);
		assertFalse("a non-canonical member record blocks the whole option",
			guild.isSelected());

		click(guild);
		flushEdt();
		assertFalse("re-checking removes the non-canonical record",
			config.blockedTeleportItems().contains("11105@" + insideGuild));
		assertFalse("empty rep keys never serialize",
			config.blockedTeleportItems().contains("@"));
	}

	// ---- Balloon log-basket routes -----------------------------------------

	@Test
	public void unlocksCategoryDropsTheBalloonRow()
	{
		newPanel();
		CategoryPanel unlocks = panel.categories().get(3);
		assertEquals("Unlocks", unlocks.categoryName());
		assertEquals("four real unlock toggles", 4, unlocks.rows().size());
		for (ShortestPathPanel.SearchRow row : unlocks.rows())
		{
			assertFalse("no balloon row remains in Unlocks",
				row.display.toLowerCase(Locale.ROOT).contains("balloon")
					|| row.text.toLowerCase(Locale.ROOT).contains("balloon"));
		}
	}

	@Test
	public void balloonCardCarriesPerRouteLogBasketCheckboxes()
	{
		newPanel();
		TransportFamilyCard card = transportCardByKey("useHotAirBalloons");
		assertNotNull(card);
		card.setDetailOpen(true);
		assertTrue("the balloon card still expands", card.isDetailOpen());

		assertNotNull("the detail carries the Log Basket Routes header",
			labelContaining(card, "Log Basket Routes"));
		List<JCheckBox> boxes = labeledCheckboxesIn(card);
		assertEquals("one checkbox per balloon route",
			BalloonRoute.values().length, boxes.size());
		for (BalloonRoute route : BalloonRoute.values())
		{
			assertNotNull(route.displayLabel() + " checkbox",
				optionBox(card, route.displayLabel()));
		}
	}

	@Test
	public void balloonRouteCheckboxWritesAndResyncsTheRouteSet() throws Exception
	{
		newPanel();
		flushEdt();
		TransportFamilyCard card = transportCardByKey("useHotAirBalloons");
		card.setDetailOpen(true);
		JCheckBox grandTree = optionBox(card, "Grand Tree (magic logs)");
		assertNotNull(grandTree);
		assertFalse("default legacy flag off selects no routes",
			grandTree.isSelected());

		click(grandTree);
		flushEdt();
		assertTrue("checking writes the route into the set",
			config.balloonLogBasketRoutes().contains(BalloonRoute.GRAND_TREE));
		assertFalse("unrelated routes stay unselected",
			config.balloonLogBasketRoutes().contains(BalloonRoute.VARROCK));

		click(grandTree);
		flushEdt();
		assertFalse("unchecking removes the route",
			config.balloonLogBasketRoutes().contains(BalloonRoute.GRAND_TREE));

		// External config edits mirror back into the checkboxes.
		config.setBalloonLogBasketRoutesValue(EnumSet.of(BalloonRoute.GRAND_TREE));
		settings.onConfigChanged(event("balloonLogBasketRoutes"));
		flushEdt();
		assertTrue("external write selects the box", grandTree.isSelected());

		config.setBalloonLogBasketRoutesValue(EnumSet.noneOf(BalloonRoute.class));
		settings.onConfigChanged(event("balloonLogBasketRoutes"));
		flushEdt();
		assertFalse("external clear unchecks the box", grandTree.isSelected());
	}

	@Test
	public void balloonRouteLabelsJoinTheCardSearchText() throws Exception
	{
		newPanel();
		CategoryPanel transports = panel.categories().get(1);
		TransportFamilyCard card = transportCardByKey("useHotAirBalloons");
		assertNotNull(card);

		transports.applyQuery("magic logs");
		assertTrue("a route-label query surfaces the card expanded",
			card.isDetailOpen());
		transports.applyQuery("");
	}

	@Test
	public void unlocksTileCountsFourKeys() throws Exception
	{
		newPanel();
		assertEquals("0/4 on", statusLabelOf(tileNamed("Unlocks")).getText());

		config.setUnlockCanoeAxeValue(true);
		settings.onConfigChanged(event("unlockCanoeAxe"));
		flushEdt();
		assertEquals("1/4 on", statusLabelOf(tileNamed("Unlocks")).getText());
	}

	// ---- Combo styling + tile-name fit ------------------------------------

	/**
	 * Every combo the panel builds — the General {@code comboRow} rows and a
	 * transport card's enum master — runs through one shared styling path:
	 * the same renderer class, the same dark closed-face background and the
	 * same preferred-width cap, so a long enum constant can never squeeze
	 * its row's label column into wrapping (the 3-line teleport-items row
	 * defect).
	 */
	@Test
	public void everyComboSharesOneStylingPath()
	{
		newPanel();

		List<JComboBox<?>> combos = new ArrayList<>();
		for (JComponent surface : surfaces())
		{
			combos.addAll(combosIn(surface));
		}
		assertFalse("the panel builds combos", combos.isEmpty());
		assertTrue("the General rows and the POH tier combo all surface",
			combos.size() >= 3);

		// A card bound to an enum-valued key exercises the master-combo
		// branch — no shipped card is enum-mastered today (the teleport
		// items card is masterless), so the path is proven on a live one.
		TransportFamilyCard card = new TransportFamilyCard(panel,
			"useTeleportationItems", "Teleportation Items", "", null);
		assertTrue("an enum-valued master renders a combo",
			card.getMasterControl() instanceof JComboBox);
		combos.add((JComboBox<?>) card.getMasterControl());

		Class<?> rendererClass = combos.get(0).getRenderer().getClass();
		for (JComboBox<?> combo : combos)
		{
			assertSame("one renderer class across panel and card combos",
				rendererClass, combo.getRenderer().getClass());
			assertEquals("the closed face keeps the dark tone",
				ColorScheme.DARKER_GRAY_COLOR, combo.getBackground());
			assertTrue("the combo's preferred width is capped",
				combo.getPreferredSize().width <= 110);
		}
	}

	/**
	 * The contrast direction: the closed face renders the dark tone while
	 * the expanded popup rows render the lighter medium grey — and the
	 * selection stays visible on it (the LAF's selection colour is the same
	 * medium grey, so a renderer that left it alone would hide the
	 * highlight).
	 */
	@Test
	public void comboPopupRowsRenderLighterThanTheClosedFace()
	{
		newPanel();
		JComboBox<?> combo = comboContaining(
			contentOf(panel.categories().get(0)), TeleportationItem.NONE);
		assertNotNull("the teleport-items mode combo", combo);

		// Raw types: the wildcard combo's renderer capture rejects typed calls.
		@SuppressWarnings({"rawtypes", "unchecked"})
		ListCellRenderer renderer = combo.getRenderer();
		JList list = new JList();
		Component face = (Component) renderer.getListCellRendererComponent(
			list, combo.getSelectedItem(), -1, false, false);
		Component popupRow = (Component) renderer.getListCellRendererComponent(
			list, combo.getItemAt(0), 0, false, false);
		Component selectedRow = (Component) renderer.getListCellRendererComponent(
			list, combo.getItemAt(0), 0, true, false);

		assertEquals("the closed face renders the dark tone",
			ColorScheme.DARKER_GRAY_COLOR, face.getBackground());
		assertEquals("popup rows render the lighter grey",
			ColorScheme.MEDIUM_GRAY_COLOR, popupRow.getBackground());
		assertFalse("the selected popup row stays visible",
			ColorScheme.MEDIUM_GRAY_COLOR.equals(selectedRow.getBackground()));
	}

	/**
	 * The teleport-items mode combo in General is the widest enum on the
	 * panel ("Inventory and Bank (perm)") — uncapped it squeezes the row's
	 * label column until the html-wrapped label needs three lines. Capping
	 * the combo's preferred width keeps the whole row one line tall.
	 */
	@Test
	public void teleportItemsComboRowStaysOneLine()
	{
		newPanel();
		JComboBox<?> combo = comboContaining(
			contentOf(panel.categories().get(0)), TeleportationItem.NONE);
		assertNotNull("the teleport-items mode combo", combo);
		assertTrue("the mode combo's preferred width is capped like the "
				+ "card combos", combo.getPreferredSize().width <= 110);
	}

	/**
	 * The long category names never clip on the square tiles: "Teleport
	 * Restrictions" and "Player-Owned House" exceed the ~102px tile edge on
	 * one line, so the name label wraps to two centered lines rather than
	 * truncating.
	 */
	@Test
	public void longTileNamesWrapInsteadOfClipping()
	{
		newPanel();
		for (String name : new String[]{"Teleport Restrictions", "Player-Owned House"})
		{
			JComponent tile = tileNamed(name);
			assertNotNull(name + " tile", tile);
			JLabel nameLabel = null;
			for (JLabel label : labelsIn(tile))
			{
				if (stripHtml(label.getText()).contains(name))
				{
					nameLabel = label;
				}
			}
			assertNotNull(name + " name label", nameLabel);
			assertTrue(name + " name label carries the html wrap",
				nameLabel.getText().startsWith("<html>"));
			nameLabel.setSize(100, 0);
			int oneLine = nameLabel.getFontMetrics(nameLabel.getFont()).getHeight();
			assertTrue(name + " wraps to a second line at tile width",
				nameLabel.getPreferredSize().height > oneLine);
		}
	}

	// ---- POH portal-nexus grouping ---------------------------------------

	@Test
	public void pohNexusPortalGroupsCoverAllPortals()
	{
		EnumSet<PohNexusPortal.PortalGroup> groups =
			EnumSet.noneOf(PohNexusPortal.PortalGroup.class);
		for (PohNexusPortal portal : PohNexusPortal.values())
		{
			assertNotNull(portal + " carries a spellbook group", portal.group());
			groups.add(portal.group());
		}
		assertEquals("all five buckets are used",
			PohNexusPortal.PortalGroup.values().length, groups.size());
		assertEquals("the wiki-verified list still covers 40 portals",
			40, PohNexusPortal.values().length);
	}

	// ---- POH collapsible groups -----------------------------------------

	private CategoryPanel pohCategory()
	{
		return panel.categories().get(5);
	}

	/** The {@link CollapsibleEnumGroup} row carrying {@code label}. */
	private CollapsibleEnumGroup<?> groupByLabel(String label)
	{
		for (ShortestPathPanel.SearchRow row : pohCategory().rows())
		{
			if (row.component instanceof CollapsibleEnumGroup
				&& label.equals(stripHtml(
					((CollapsibleEnumGroup<?>) row.component).getNameLabel().getText())))
			{
				return (CollapsibleEnumGroup<?>) row.component;
			}
		}
		return null;
	}

	@Test
	public void pohCategoryShowsCollapsibleGroupsNotFlatRows()
	{
		newPanel();
		int groups = 0;
		for (ShortestPathPanel.SearchRow row : pohCategory().rows())
		{
			if (row.component instanceof CollapsibleEnumGroup)
			{
				groups++;
			}
		}
		assertEquals("five spellbook groups + the mounted-items group", 6, groups);
		assertEquals("the collapsed POH category is compact — master + 4 rows "
				+ "+ 6 group headers instead of 40+ flat member rows",
			11, pohCategory().rows().size());
	}

	@Test
	public void memberToggleInsideGroupWritesTheEnumSet() throws Exception
	{
		newPanel();
		// POH master defaults off, which disables the member checkboxes —
		// enable it so a toggle can round-trip.
		config.setUsePohValue(true);
		settings.onConfigChanged(event("usePoh"));
		flushEdt();

		CollapsibleEnumGroup<?> ancient = groupByLabel("Ancient");
		assertNotNull(ancient);
		ancient.setDetailOpen(true);
		JCheckBox kharyrll = optionBox(ancient, "Kharyrll");
		assertNotNull(kharyrll);
		assertFalse(kharyrll.isSelected());

		click(kharyrll);
		flushEdt();
		assertTrue("member toggle writes the portal into the EnumSet",
			config.pohNexusPortals().contains(PohNexusPortal.KHARYRLL));
		assertFalse("unrelated portals stay unselected",
			config.pohNexusPortals().contains(PohNexusPortal.VARROCK));
	}

	@Test
	public void memberLabelSearchExpandsTheGroup() throws Exception
	{
		newPanel();
		CategoryPanel poh = pohCategory();
		CollapsibleEnumGroup<?> ancient = groupByLabel("Ancient");
		assertNotNull(ancient);
		assertFalse("groups start collapsed", ancient.isDetailOpen());

		assertFalse("a member-label query matches the group's row",
			panel.matchingRows(poh, "kharyrll").isEmpty());
		poh.applyQuery("kharyrll");
		assertTrue("the matched group row stays visible",
			SwingUtilities.isDescendingFrom(ancient, contentOf(poh)));
		assertTrue("a member-label query expands the containing group",
			ancient.isDetailOpen());
	}

	@Test
	public void emptyQueryRestoresGroupCollapsedState()
	{
		newPanel();
		CollapsibleEnumGroup<?> ancient = groupByLabel("Ancient");
		assertNotNull(ancient);
		ancient.setSearchExpanded();
		assertTrue(ancient.isDetailOpen());

		pohCategory().applyQuery("");
		assertFalse("clearing the query collapses search-expanded groups",
			ancient.isDetailOpen());
	}

	@Test
	public void groupCountTracksMemberSelection() throws Exception
	{
		newPanel();
		config.setUsePohValue(true);
		settings.onConfigChanged(event("usePoh"));
		flushEdt();

		CollapsibleEnumGroup<?> ancient = groupByLabel("Ancient");
		assertNotNull(ancient);
		assertEquals("0/8", ancient.getCountLabel().getText());
		ancient.setDetailOpen(true);

		JCheckBox kharyrll = optionBox(ancient, "Kharyrll");
		assertNotNull(kharyrll);
		click(kharyrll);
		flushEdt();
		assertEquals("the header count re-derives on member writes",
			"1/8", ancient.getCountLabel().getText());
	}

	@Test
	public void pohMasterOffDimsGroupsAndMembers() throws Exception
	{
		newPanel();
		config.setUsePohValue(true);
		settings.onConfigChanged(event("usePoh"));
		flushEdt();

		CollapsibleEnumGroup<?> ancient = groupByLabel("Ancient");
		assertNotNull(ancient);
		ancient.setDetailOpen(true);
		JCheckBox member = optionBox(ancient, "Kharyrll");
		assertNotNull(member);
		assertTrue("members are editable while POH is on", member.isEnabled());
		assertTrue(ancient.getNameLabel().isEnabled());

		config.setUsePohValue(false);
		settings.onConfigChanged(event("usePoh"));
		flushEdt();
		assertFalse("master off dims the group header", ancient.getNameLabel().isEnabled());
		assertFalse("master off dims the group members", member.isEnabled());
	}
}
