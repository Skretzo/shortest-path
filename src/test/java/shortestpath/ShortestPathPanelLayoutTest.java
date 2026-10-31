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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;

import org.junit.Test;

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import shortestpath.items.ItemStateService;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.poh.PohNexusPortal;
import shortestpath.requirement.TeleportRestriction;
import shortestpath.settings.Settings;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.BalloonRoute;

/**
 * Headless structure tests pinning the sidebar layout contract: a landing
 * tile grid swapping to a drilled section view (each its own scroll under
 * a pinned back-arrow header), section content pinned to the 225px
 * viewport (so the scrollbar never covers content or the master toggles),
 * a real left inset on every scroll surface, and in-place search
 * filtering per section.
 *
 * <p>The harness wraps {@link TestShortestPathConfig} through the
 * {@link Settings#wrap} seam — reads and listeners work, writes are inert.</p>
 */
public class ShortestPathPanelLayoutTest
{
	private TestShortestPathConfig config = new TestShortestPathConfig();
	private Settings settings;
	private ShortestPathPanel panel;
	private Plugin plugin;
	// Every item id passed to ItemManager.getImage while the panel builds —
	// cards and tiles request their sprite through loadItemIcon, so an
	// iconless control shows up as a missing request here.
	private final List<Integer> iconRequests = new ArrayList<>();
	// Every sprite archive passed to SpriteManager.getSpriteAsync while the
	// panel builds — tiles whose imagery is a client sprite (not an item)
	// route through loadSpriteIcon instead of loadItemIcon.
	private final List<Integer> spriteRequests = new ArrayList<>();
	// Every event the panel posts on the shared bus — the footer gear
	// opens the classic config page by posting an overlay-menu click.
	private final List<Object> postedEvents = new ArrayList<>();

	private ShortestPathPanel newPanel()
	{
		return newPanel(ItemStateService.forTesting());
	}

	private ShortestPathPanel newPanel(ItemStateService itemState)
	{
		iconRequests.clear();
		spriteRequests.clear();
		postedEvents.clear();
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
		// Client-sprite requests record their archive id; the async
		// delivery itself stays unstubbed so labels simply keep no icon —
		// the pin is that the request was made, not that it resolved.
		SpriteManager spriteManager = mock(SpriteManager.class);
		doAnswer(i ->
		{
			spriteRequests.add((Integer) i.getArgument(0));
			return null;
		}).when(spriteManager).getSpriteAsync(anyInt(), anyInt(), any(Consumer.class));
		// The Settings tile's config deep-link posts on the shared bus —
		// the mock records every post rather than delivering it.
		EventBus eventBus = mock(EventBus.class);
		doAnswer(i ->
		{
			postedEvents.add(i.getArgument(0));
			return null;
		}).when(eventBus).post(any());
		plugin = mock(Plugin.class);
		panel = new ShortestPathPanel(settings, client, clientThread, itemManager,
			spriteManager, itemState, eventBus, plugin);
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
	 * scroll wrap: view → BorderLayout.NORTH child — the innermost
	 * {@code FixedWidthPanel} on that branch, so the returned panel is
	 * the row container and not its scroll wrap.
	 */
	private static FixedWidthPanel contentOf(Container surface)
	{
		for (Component child : surface.getComponents())
		{
			if (child instanceof FixedWidthPanel)
			{
				FixedWidthPanel inner = contentOf((FixedWidthPanel) child);
				return inner != null ? inner : (FixedWidthPanel) child;
			}
			if (child instanceof Container)
			{
				// Container, not JComponent — a combo box's
				// CellRendererPane child is a plain AWT container.
				FixedWidthPanel found = contentOf((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * Every scroll surface in the panel tree: the home view plus one per
	 * category.
	 */
	private List<JComponent> surfaces()
	{
		List<JComponent> surfaces = new ArrayList<>(panel.categories());
		surfaces.add(homeView());
		return surfaces;
	}

	/**
	 * The panel body — the {@link java.awt.CardLayout} container swapping
	 * the home tile grid for a drilled section.
	 */
	private Container body()
	{
		return (Container) ((BorderLayout) panel.getLayout())
			.getLayoutComponent(BorderLayout.CENTER);
	}

	/** The landing tile grid — the body's first card. */
	private JComponent homeView()
	{
		return (JComponent) body().getComponent(0);
	}

	/** Every {@link JScrollPane} anywhere under {@code root}. */
	private static List<JScrollPane> scrollsIn(Container root)
	{
		List<JScrollPane> scrolls = new ArrayList<>();
		for (Component child : root.getComponents())
		{
			if (child instanceof JScrollPane)
			{
				scrolls.add((JScrollPane) child);
			}
			if (child instanceof Container)
			{
				scrolls.addAll(scrollsIn((Container) child));
			}
		}
		return scrolls;
	}

	/** The back-arrow button on a section's pinned header. */
	private static JButton backButton(CategoryPanel category)
	{
		for (JButton button : buttonsIn(headerOf(category)))
		{
			if ("Back".equals(button.getToolTipText()))
			{
				return button;
			}
		}
		return null;
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
	public void theScrollViewIsViewportWidth()
	{
		newPanel();
		List<JScrollPane> scrolls = scrollsIn(panel);
		assertEquals("the home view plus one scroll per section",
			4, scrolls.size());
		for (JScrollPane scroll : scrolls)
		{
			JComponent view = (JComponent) scroll.getViewport().getView();
			assertTrue("scroll view is a FixedWidthPanel",
				view instanceof FixedWidthPanel);
			assertEquals("view pinned to viewport width",
				PluginPanel.PANEL_WIDTH, view.getPreferredSize().width);
			assertEquals("the surfaced content is viewport width",
				PluginPanel.PANEL_WIDTH,
				((JComponent) view.getComponent(0)).getPreferredSize().width);
		}
	}

	/**
	 * The two-state shell: the landing view is a tile grid — one tile per
	 * section plus the Settings tile — and a tile click swaps the body to
	 * that section's own scroll; the section header's back arrow returns
	 * to the grid.
	 */
	@Test
	public void sidebarOpensOnTileGridAndDrillsIntoSections()
	{
		newPanel();
		assertEquals("exactly three sections", 3, panel.categories().size());
		assertEquals("Transports", panel.categories().get(0).categoryName());
		assertEquals("Teleport Restrictions", panel.categories().get(1).categoryName());
		assertEquals("Player-Owned House", panel.categories().get(2).categoryName());

		JComponent home = homeView();
		List<JButton> tileButtons = tileButtonsIn(home);
		assertEquals("one tile per section plus the Settings tile",
			4, tileButtons.size());

		// CardLayout flags only the current card visible once the body
		// lays out — the tile grid is the initial card.
		body().setSize(PluginPanel.PANEL_WIDTH, 600);
		body().doLayout();
		assertTrue("the tile grid is the initial view", home.isVisible());
		for (CategoryPanel section : panel.categories())
		{
			assertFalse(section.categoryName() + " starts hidden",
				section.isVisible());
			assertNotNull(section.categoryName() + " scrolls its own rows",
				findScrollPane(section));
			assertNotNull(section.categoryName()
				+ " header carries a back arrow", backButton(section));
		}

		// Drill: the second tile opens Teleport Restrictions; its back
		// arrow returns to the tile grid.
		tileButtons.get(1).doClick();
		assertTrue("the drilled section shows",
			panel.categories().get(1).isVisible());
		assertFalse("the grid is swapped out", home.isVisible());
		backButton(panel.categories().get(1)).doClick();
		assertTrue("the back arrow returns to the tile grid",
			home.isVisible());
		assertFalse(panel.categories().get(1).isVisible());
	}

	/**
	 * A query filters every section's rows in place — matching rows stay,
	 * the rest drop out of the content; clearing restores the full set.
	 */
	@Test
	public void searchFiltersRowsInPlace() throws Exception
	{
		newPanel();
		CategoryPanel transports = panel.categories().get(0);
		int fullRows = transports.rows().size();

		setSearchText("agility");
		FixedWidthPanel filtered = contentOf(transports);
		assertTrue("the query leaves only the matching rows",
			filtered.getComponentCount() < fullRows
				&& filtered.getComponentCount() > 0);

		setSearchText("");
		assertEquals("clearing restores the section's rows",
			fullRows, contentOf(transports).getComponentCount());
	}

	private static java.util.List<JButton> buttonsIn(Container root)
	{
		java.util.List<JButton> buttons = new ArrayList<>();
		for (Component child : root.getComponents())
		{
			if (child instanceof JButton)
			{
				buttons.add((JButton) child);
			}
			if (child instanceof Container)
			{
				buttons.addAll(buttonsIn((Container) child));
			}
		}
		return buttons;
	}

	/**
	 * The home grid's tile buttons — the footer link buttons are
	 * JButtons too, and look-and-feels that draw scrollbar arrow
	 * buttons (Metal, Nimbus) install more inside the home scroll
	 * wrap, so tiles carry the {@link ShortestPathPanel#HOME_TILE}
	 * client-property marker to stay identifiable on every platform.
	 */
	private static List<JButton> tileButtonsIn(Container root)
	{
		List<JButton> tiles = new ArrayList<>();
		for (JButton button : buttonsIn(root))
		{
			if (Boolean.TRUE.equals(
				button.getClientProperty(ShortestPathPanel.HOME_TILE)))
			{
				tiles.add(button);
			}
		}
		return tiles;
	}

	/**
	 * The home grid's non-section tile and its footer link row: the
	 * Settings tile opens the plugin's stock config page by posting the
	 * overlay-config menu click the config surface resolves off the
	 * event overlay's plugin; the footer buttons open their URLs
	 * through the system browser (the URL rides as the action command
	 * so the target is pinned without a real browser).
	 */
	@Test
	public void settingsTileDeepLinksTheConfigPageAndFooterCarriesLinks()
	{
		newPanel();
		JComponent home = homeView();

		List<JButton> tiles = tileButtonsIn(home);
		JButton settings = tiles.get(3);
		assertEquals("Settings", settings.getToolTipText());
		settings.doClick();
		assertEquals("one config-open event posted", 1, postedEvents.size());
		OverlayMenuClicked clicked = (OverlayMenuClicked) postedEvents.get(0);
		assertEquals(MenuAction.RUNELITE_OVERLAY_CONFIG,
			clicked.getEntry().getMenuAction());
		assertSame("the event's overlay resolves back to the plugin",
			plugin, clicked.getOverlay().getPlugin());

		List<JButton> links = new ArrayList<>();
		for (JButton button : buttonsIn(home))
		{
			String command = button.getActionCommand();
			if (command != null && !command.isEmpty())
			{
				links.add(button);
			}
		}
		assertEquals("github + discord footer buttons", 2, links.size());
		assertEquals("https://github.com/Skretzo/shortest-path",
			links.get(0).getActionCommand());
		assertEquals("https://discord.gg/uX47xg8u3M",
			links.get(1).getActionCommand());
	}

	/**
	 * The Transports tile wears the world map's transport POI glyph — a
	 * client sprite routed through {@code loadSpriteIcon}, not an item
	 * icon.
	 */
	@Test
	public void transportsTileUsesTheWorldmapTransportIcon()
	{
		newPanel();
		assertTrue("the Transports tile requests the world map's "
				+ "transportation POI sprite",
			spriteRequests.contains(SpriteID.Mapfunction.TRANSPORTATION));
		assertFalse("the transport POI icon is a sprite, not an item",
			iconRequests.contains(SpriteID.Mapfunction.TRANSPORTATION));
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
		CategoryPanel transports = panel.categories().get(0);
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
				// lives in the plugin configuration; every other card
				// carries a master.
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
		assertEquals("all transport cards checked", 22, cards);
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


	private static ShortestPathPanel.SearchRow rowByDisplay(
		CategoryPanel category, String display)
	{
		for (ShortestPathPanel.SearchRow row : category.rows())
		{
			if (display.equals(row.display))
			{
				return row;
			}
		}
		return null;
	}

	private static JSpinner spinnerIn(Container root)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JSpinner)
			{
				return (JSpinner) child;
			}
			if (child instanceof Container)
			{
				JSpinner found = spinnerIn((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}


	@Test
	public void numberBoxesKeepTheirLimits()
	{
		newPanel();
		CategoryPanel general = panel.categories().get(0);
		assertLimits(general, "Recalculate Distance", -1, 20000);
		assertLimits(general, "Finish Distance", -1, 50);
		assertLimits(general, "Calculation Cutoff", 1, 30);
		assertLimits(general, "A* Heuristic Weight", 100, 300);
	}

	private static void assertLimits(CategoryPanel category, String display, int min, int max)
	{
		SpinnerNumberModel model = (SpinnerNumberModel) spinnerIn(rowByDisplay(category, display).component).getModel();
		assertEquals(display + " min", min, model.getMinimum());
		assertEquals(display + " max", max, model.getMaximum());
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
			ItemID.POH_LEVER_OAK, ItemID.DT2_ANIMA_PORTAL,
			ItemID.POH_SUPERIOR_GARDEN_WILDERNESS_OBELISK,
			ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE,
			ItemID.POH_RUG_3,
			ItemID.CASTLEWARS_SARADOMIN_BANNER,
		};
		for (int id : requested)
		{
			assertTrue("card icon " + id + " was requested",
				iconRequests.contains(id));
		}
		// The rejected sprite ids stay swapped out: the bare grapple hook,
		// the spriteless magic-carpet placeholder and the spell-icon item.
		for (int id : new int[]{ItemID.XBOWS_GRAPPLE_HOOK,
			ItemID.MAGIC_CARPET, ItemID.MINIGAME_TELEPORT})
		{
			assertFalse("replaced icon " + id + " is no longer requested",
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
			contentOf(pohCategory()), "POH Fairy Ring");
		assertNotNull("a check row label", rowLabel);
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
		CategoryPanel restrictions = panel.categories().get(1);
		restrictions.applyQuery("");
		JLabel hint = labelContaining(contentOf(restrictions),
			"Teleportation items are disabled");
		assertNotNull("items-disabled hint", hint);
		hint.setSize(200, 0);
		int oneLine = hint.getFontMetrics(hint.getFont()).getHeight();
		assertTrue("the hint wraps onto multiple lines inside the viewport",
			hint.getPreferredSize().height > oneLine);
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



	@Test
	public void ownedCheckboxOnRestrictionsHeader()
	{
		newPanel();
		CategoryPanel restrictions = panel.categories().get(1);
		assertEquals("Teleport Restrictions", restrictions.categoryName());

		Container header = headerOf(restrictions);
		Component east = ((BorderLayout) header.getLayout())
			.getLayoutComponent(BorderLayout.EAST);
		assertTrue("header EAST holds the Owned filter",
			east instanceof JCheckBox);
		JCheckBox owned = (JCheckBox) east;
		assertEquals("Owned", owned.getText());

		// Laid out at viewport width the checkbox lands fully inside the
		// section header strip...
		header.setSize(new Dimension(
			PluginPanel.PANEL_WIDTH, header.getPreferredSize().height));
		header.doLayout();
		assertTrue("Owned checkbox fully inside the header strip",
			owned.getBounds().getMaxX() <= PluginPanel.PANEL_WIDTH);

		// ...and it lives on that header, which stays pinned above the
		// section's own scroll rather than scrolling with the rows.
		assertSame(header, owned.getParent());
		JScrollPane scroll = findScrollPane(restrictions);
		assertNotNull("the section scrolls its rows under the pinned header",
			scroll);
		assertFalse("the Owned filter stays pinned on the header",
			SwingUtilities.isDescendingFrom(owned,
				scroll.getViewport().getView()));
	}





	@Test
	public void categoryNameQueryListsTheRestrictionFamilies() throws Exception
	{
		newPanel();
		// "restrictions" hits the section's own name but no family label:
		// the checklist must expand in place rather than leave a dangling
		// header over zero rows.
		setSearchText("restrictions");
		flushEdt();

		CategoryPanel restrictions = panel.categories().get(1);
		assertTrue("name-hit query keeps the families listed",
			containsLabelText(contentOf(restrictions), "Ardougne Cloaks"));
	}

	@Test
	public void itemsDisabledHintSurfaces() throws Exception
	{
		newPanel();
		CategoryPanel restrictions = panel.categories().get(1);

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

	/**
	 * The injected fairy-ring staff requirement rides no generated family —
	 * the checklist lists it as the code-defined "Fairy Staves" row, a staff
	 * name search surfaces it, and unchecking writes every staff variant to
	 * the {@code blockedTeleportItems} CSV the ring gate reads.
	 */
	@Test
	public void fairyStavesRowBlocksEveryStaffVariant() throws Exception
	{
		newPanel();
		flushEdt();

		JPanel row = restrictionRowByName("Fairy Staves");
		assertNotNull("the injected staff requirement is a checklist row", row);
		assertTrue("a staff-name search surfaces the row",
			panel.restrictionListPanel().matchingLabels("dramen")
				.contains("Fairy Staves"));

		click(masterBox(row));
		flushEdt();

		Set<Integer> blocked = new HashSet<>();
		TeleportRestriction.parseBlocked(config.blockedTeleportItems(),
			blocked, new HashMap<>());
		for (int id : ItemVariations.fromName("DRAMEN_STAFF").getIds())
		{
			assertTrue("unchecking writes staff id " + id + " to the CSV",
				blocked.contains(id));
		}
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
	public void balloonCardCarriesPerRouteLogBasketCheckboxes()
	{
		newPanel();
		TransportFamilyCard card = transportCardByKey("useHotAirBalloons");
		assertNotNull(card);
		card.setDetailOpen(true);
		assertTrue("the balloon card still expands", card.isDetailOpen());

		CollapsibleEnumGroup<?> basket = groupByLabel("Log Basket Routes");
		assertNotNull("the detail carries the Log Basket Routes group", basket);
		basket.setDetailOpen(true);
		List<JCheckBox> boxes = labeledCheckboxesIn(basket);
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
		CategoryPanel transports = panel.categories().get(0);
		TransportFamilyCard card = transportCardByKey("useHotAirBalloons");
		assertNotNull(card);

		transports.applyQuery("magic logs");
		assertTrue("a route-label query surfaces the card expanded",
			card.isDetailOpen());
		transports.applyQuery("");
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
		assertTrue("the POH tier combo surfaces", combos.size() >= 1);

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
			contentOf(pohCategory()), JewelleryBoxTier.ORNATE);
		assertNotNull("the POH tier combo", combo);

		// Raw types: the wildcard combo's renderer capture rejects typed
		// calls. The renderer is a flyweight — the same JLabel comes back
		// every call — so each background is read before the next render
		// repaints it.
		@SuppressWarnings({"rawtypes", "unchecked"})
		ListCellRenderer renderer = combo.getRenderer();
		JList list = new JList();
		assertEquals("the closed face renders the dark tone",
			ColorScheme.DARKER_GRAY_COLOR,
			((Component) renderer.getListCellRendererComponent(
				list, combo.getSelectedItem(), -1, false, false)).getBackground());
		assertEquals("popup rows render the lighter grey",
			ColorScheme.MEDIUM_GRAY_COLOR,
			((Component) renderer.getListCellRendererComponent(
				list, combo.getItemAt(0), 0, false, false)).getBackground());
		assertFalse("the selected popup row stays visible",
			ColorScheme.MEDIUM_GRAY_COLOR.equals(
				((Component) renderer.getListCellRendererComponent(
					list, combo.getItemAt(0), 0, true, false)).getBackground()));
	}




	// ---- Expandable surfaces: dark header over a background-toned,
	// ---- dark-framed detail well ---------------------------------------

	/**
	 * Every expandable surface paints the same chrome: the header bar — the
	 * line carrying the name, expander and master control — is an opaque
	 * {@code DARKER_GRAY_COLOR} strip, and the detail well that opens
	 * beneath it keeps the category's {@code DARK_GRAY_COLOR} background
	 * inside a header-dark frame that omits its top edge — the well reads
	 * as a continuation of the bar, boxed on the three open sides.
	 * Transport cards, restriction rows and the collapsible enum groups
	 * all share the one pairing so no site can drift to its own colours.
	 */
	@Test
	public void expandableSurfacesShareTheBorderedWellChrome()
	{
		newPanel();

		TransportFamilyCard card = transportCardByKey("useAgilityShortcuts");
		assertNotNull("a transport card", card);
		assertWellChrome(card, "transport card");

		JPanel row = restrictionRowByName("Ardougne Cloaks");
		assertNotNull("a restriction row", row);
		assertWellChrome(row, "restriction row");

		CollapsibleEnumGroup<?> group = groupByLabel("Ancient");
		assertNotNull("a collapsible enum group", group);
		assertWellChrome(group, "enum group");
	}

	/**
	 * Pins child 0 as the opaque dark header bar and the last child as the
	 * detail well — category-toned (transparent or {@code DARK_GRAY_COLOR},
	 * never the light medium-grey fill) and carrying the 3px dark frame
	 * border on its left, bottom and right edges only (the header bar
	 * supplies the top edge). Every expandable surface builds its header
	 * and detail as its only two children, in that order.
	 */
	private static void assertWellChrome(JComponent surface, String what)
	{
		JComponent header = (JComponent) surface.getComponent(0);
		JComponent detail = (JComponent) surface.getComponent(
			surface.getComponentCount() - 1);
		assertTrue(what + " header bar is opaque", header.isOpaque());
		assertEquals(what + " header bar carries the dark tone",
			ColorScheme.DARKER_GRAY_COLOR, header.getBackground());
		assertFalse(what + " detail well never paints the light fill",
			ColorScheme.MEDIUM_GRAY_COLOR.equals(detail.getBackground()));
		assertTrue(what + " detail well keeps the category tone",
			!detail.isOpaque()
				|| ColorScheme.DARK_GRAY_COLOR.equals(detail.getBackground()));
		assertTrue(what + " detail well carries the dark frame border",
			hasAccentBorder(detail.getBorder(), ColorScheme.DARKER_GRAY_COLOR));
		Insets frame = frameInsets(detail.getBorder(), detail);
		assertNotNull(what + " detail well carries a matte frame", frame);
		assertEquals(what + " frame leaves the top edge open — the header "
				+ "bar closes it", 0, frame.top);
		assertEquals(what + " frame boxes the left edge at 3px", 3, frame.left);
		assertEquals(what + " frame boxes the bottom edge at 3px", 3, frame.bottom);
		assertEquals(what + " frame boxes the right edge at 3px", 3, frame.right);
	}

	/**
	 * Whether {@code border} — or either side of a {@link CompoundBorder} —
	 * is a line/matte edge painted in {@code colour}.
	 */
	private static boolean hasAccentBorder(Border border, Color colour)
	{
		if (border instanceof CompoundBorder)
		{
			CompoundBorder compound = (CompoundBorder) border;
			return hasAccentBorder(compound.getOutsideBorder(), colour)
				|| hasAccentBorder(compound.getInsideBorder(), colour);
		}
		if (border instanceof LineBorder)
		{
			return colour.equals(((LineBorder) border).getLineColor());
		}
		if (border instanceof MatteBorder)
		{
			return colour.equals(((MatteBorder) border).getMatteColor());
		}
		return false;
	}

	/**
	 * The insets of the {@link MatteBorder} — or {@link LineBorder} — in
	 * {@code border}, looking through either side of a
	 * {@link CompoundBorder}; {@code null} when no framed edge exists.
	 */
	private static Insets frameInsets(Border border, JComponent owner)
	{
		if (border instanceof CompoundBorder)
		{
			CompoundBorder compound = (CompoundBorder) border;
			Insets outside = frameInsets(compound.getOutsideBorder(), owner);
			return outside != null ? outside
				: frameInsets(compound.getInsideBorder(), owner);
		}
		if (border instanceof MatteBorder || border instanceof LineBorder)
		{
			return border.getBorderInsets(owner);
		}
		return null;
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
		return panel.categories().get(2);
	}

	/** The {@link CollapsibleEnumGroup} carrying {@code label} anywhere in
	 * the panel tree — groups nest inside family-card detail wells now. */
	private CollapsibleEnumGroup<?> groupByLabel(String label)
	{
		return groupByLabel(panel, label);
	}

	private static CollapsibleEnumGroup<?> groupByLabel(Container root, String label)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof CollapsibleEnumGroup
				&& label.equals(stripHtml(
					((CollapsibleEnumGroup<?>) child).getNameLabel().getText())))
			{
				return (CollapsibleEnumGroup<?>) child;
			}
			if (child instanceof Container)
			{
				CollapsibleEnumGroup<?> found = groupByLabel((Container) child, label);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** The POH family card — the house section is a single card row. */
	private TransportFamilyCard pohCard()
	{
		return transportCardByKey("usePoh");
	}

	/**
	 * The caption row component carrying {@code caption} inside the POH
	 * card's detail well — the caption is a plain label row the well dims
	 * with the master like every detail child.
	 */
	private JComponent pohCaptionRow(String caption)
	{
		TransportFamilyCard card = pohCard();
		if (card == null)
		{
			return null;
		}
		for (Component child : card.detail.getComponents())
		{
			if (child instanceof JComponent
				&& containsLabelEqual((JComponent) child, caption))
			{
				return (JComponent) child;
			}
		}
		return null;
	}

	@Test
	public void pohCategoryShowsCollapsibleGroupsNotFlatRows()
	{
		newPanel();
		assertEquals("the POH section is a single family-card row",
			1, pohCategory().rows().size());
		TransportFamilyCard card = pohCard();
		assertNotNull("the usePoh card", card);
		assertNotNull("the card carries the usePoh master checkbox",
			card.getMasterControl());

		int groups = 0;
		for (Component child : card.detail.getComponents())
		{
			if (child instanceof CollapsibleEnumGroup)
			{
				groups++;
			}
		}
		assertEquals("five spellbook groups + the mounted-items group", 6, groups);
		assertEquals("the card detail is compact — 4 rows + 2 captions "
				+ "+ 6 group headers instead of 40+ flat member rows",
			12, card.detail.getComponentCount());
	}

	/**
	 * The house card is its section's only row, so it drops the
	 * collapsible chrome a lone boxed expander would draw: no expander,
	 * the detail sits always expanded, and the well carries no frame
	 * border — it reads as a flat labelled group like the surrounding
	 * rows. The inner enum groups keep their own expanders.
	 */
	@Test
	public void pohCardRendersFlatNotCollapsible()
	{
		newPanel();
		TransportFamilyCard card = pohCard();
		assertNotNull("the usePoh card", card);
		assertTrue("flat card's detail is always expanded",
			card.detail.isVisible());
		assertNull("flat card grows no expander", card.expander);
		assertNull("flat card's detail carries no frame border",
			frameInsets(card.detail.getBorder(), card.detail));
		assertFalse("the card body sits transparent on the section tone",
			card.isOpaque());
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
			poh.matchingRows("kharyrll").isEmpty());
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

	/**
	 * Group headers and their member checkboxes inherit the LAF defaults —
	 * no explicit foreground or font is set on them, so they render in the
	 * LAF's own tones like any undecorated label/checkbox. Only the
	 * {@code n/total} count carries a deliberate style (secondary small
	 * font); the panel's plain-white row typography belongs to the section
	 * captions, not the group rows.
	 */
	@Test
	public void enumGroupLabelsInheritTheLafTone()
	{
		newPanel();

		CollapsibleEnumGroup<?> ancient = groupByLabel("Ancient");
		assertNotNull(ancient);
		assertEquals("group name inherits the label LAF foreground",
			new JLabel().getForeground(), ancient.getNameLabel().getForeground());
		assertEquals("group name inherits the label LAF font",
			new JLabel().getFont(), ancient.getNameLabel().getFont());

		JCheckBox member = optionBox(ancient, "Kharyrll");
		assertNotNull("a member checkbox", member);
		assertEquals("member checkbox inherits the checkbox LAF foreground",
			new JCheckBox().getForeground(), member.getForeground());
		assertEquals("member checkbox inherits the checkbox LAF font",
			new JCheckBox().getFont(), member.getFont());

		assertEquals("the count label keeps the secondary tone",
			ColorScheme.LIGHT_GRAY_COLOR, ancient.getCountLabel().getForeground());
	}

	// ---- POH section captions ---------------------------------------------

	/**
	 * The five nexus spellbook groups and the mounted-items group share
	 * identical chrome — without captions the six rows read as one
	 * undifferentiated list. A "Portal Nexus" caption precedes the
	 * spellbook groups and a "Mounted Items" caption precedes the
	 * mounted group.
	 */
	@Test
	public void pohCaptionsDivideNexusGroupsFromMountedItems()
	{
		newPanel();
		TransportFamilyCard card = pohCard();
		assertNotNull("the usePoh card", card);
		Component[] children = card.detail.getComponents();
		int nexusCaption = -1;
		int mountedCaption = -1;
		int mountedGroup = -1;
		int groupsBetween = 0;
		for (int i = 0; i < children.length; i++)
		{
			Component child = children[i];
			if (child instanceof JComponent
				&& containsLabelEqual((JComponent) child, "Portal Nexus"))
			{
				nexusCaption = i;
			}
			else if (child instanceof JComponent
				&& containsLabelEqual((JComponent) child, "Mounted Items"))
			{
				mountedCaption = i;
			}
			if (child instanceof CollapsibleEnumGroup)
			{
				if (nexusCaption >= 0 && mountedCaption < 0)
				{
					groupsBetween++;
				}
				String groupLabel = stripHtml(
					((CollapsibleEnumGroup<?>) child).getNameLabel().getText());
				if ("All items".equals(groupLabel))
				{
					mountedGroup = i;
				}
			}
		}
		assertTrue("a 'Portal Nexus' caption row", nexusCaption >= 0);
		assertTrue("a 'Mounted Items' caption row", mountedCaption >= 0);
		assertEquals("the nexus caption heads all five spellbook groups",
			5, groupsBetween);
		assertTrue("the mounted caption precedes its group",
			mountedGroup >= 0 && mountedCaption >= 0 && mountedCaption < mountedGroup);
	}

	/**
	 * The section captions render like a regular panel row — the default
	 * (regular-size) font in the same foreground a sibling
	 * {@code checkRow} label carries — so "Portal Nexus" and "Mounted
	 * Items" read as ordinary labels over the grouped rows they head, not
	 * as small secondary hints.
	 */
	@Test
	public void pohCaptionsUseThePanelRowTypography()
	{
		newPanel();
		TransportFamilyCard card = pohCard();
		assertNotNull("the usePoh card", card);

		// The reference: a sibling checkRow label in the same detail well
		// (hidden rows take their display name from the bundled key metadata).
		JLabel reference = labelContaining(card.detail, "POH Fairy Ring");
		assertNotNull("a sibling check row label", reference);

		JComponent captionRow = pohCaptionRow("Portal Nexus");
		assertNotNull("the Portal Nexus caption row", captionRow);
		JLabel captionLabel = null;
		for (JLabel label : labelsIn(captionRow))
		{
			if ("Portal Nexus".equals(stripHtml(label.getText())))
			{
				captionLabel = label;
			}
		}
		assertNotNull("the caption label", captionLabel);
		assertEquals("the caption uses the row foreground",
			reference.getForeground(), captionLabel.getForeground());
		assertEquals("the caption uses the regular row font",
			reference.getFont(), captionLabel.getFont());
	}

	/**
	 * Captions are navigational chrome, not controls — but they join the
	 * dim contract so a disabled POH greys them out with the rows they
	 * head.
	 */
	@Test
	public void pohCaptionsDimWithTheMasterToggle() throws Exception
	{
		newPanel();
		JComponent captionRow = pohCaptionRow("Portal Nexus");
		assertNotNull("the Portal Nexus caption row", captionRow);
		JLabel captionLabel = null;
		for (JLabel label : labelsIn(captionRow))
		{
			if ("Portal Nexus".equals(stripHtml(label.getText())))
			{
				captionLabel = label;
			}
		}
		assertNotNull("the caption label", captionLabel);
		assertFalse("master off dims the caption", captionLabel.isEnabled());

		config.setUsePohValue(true);
		settings.onConfigChanged(event("usePoh"));
		flushEdt();
		assertTrue("master on restores the caption", captionLabel.isEnabled());
	}

	/**
	 * A caption carries the section's search text so a "portal nexus"
	 * query keeps it visible over the groups it heads.
	 */
	@Test
	public void pohCaptionsSurfaceThroughSearch()
	{
		newPanel();
		// The caption's search text folds into the card row's match text,
		// so a "portal nexus" query keeps the card (and its well) surfaced.
		List<ShortestPathPanel.SearchRow> matches =
			pohCategory().matchingRows("portal nexus");
		assertEquals("the query matches only the POH card row",
			1, matches.size());
		assertSame("the match is the usePoh card",
			pohCard(), matches.get(0).component);
	}
}
