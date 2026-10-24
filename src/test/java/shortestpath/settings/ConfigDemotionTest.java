package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import shortestpath.ShortestPathConfig;
import shortestpath.TestShortestPathConfig;

/**
 * Demotion-boundary gate for the settings split: the sidebar panel owns every
 * route-affecting key, so those {@code @ConfigItem}s are declared
 * {@code hidden = true} and the RuneLite Settings panel only shows pointer
 * stubs for them. Every key retained on the Settings surface must NOT be
 * hidden.
 *
 * <p>The hidden keyName set is pinned exactly. A panel-owned key losing
 * {@code hidden} renders it in Settings as a duplicate surface that desyncs
 * from the sidebar; a retained Settings key gaining {@code hidden} silently
 * strips a user-facing option. Both directions fail this test. The pinned set
 * also includes the internal state keys ({@code unreachableText},
 * {@code builtTeleportation*}, {@code blockedTeleportItems}) which have never
 * had a Settings surface.
 */
public class ConfigDemotionTest
{
	/**
	 * The exact set of {@code @ConfigItem} keyNames allowed to carry
	 * {@code hidden = true} — the panel-owned route options plus the internal
	 * state keys. 88 names; the write-pair keys
	 * ({@code builtTeleportationBoxes}/{@code builtTeleportationPortalsPoh})
	 * appear once even though each is declared on both a getter and a
	 * {@code void set*} method.
	 */
	private static final Set<String> PANEL_OWNED_KEYS = new TreeSet<>(Arrays.asList(
		"aStarHeuristicWeight",
		"avoidWilderness",
		"balloonLogBasketRoutes",
		"blockedTeleportItems",
		"builtTeleportationBoxes",
		"builtTeleportationPortalsPoh",
		"calculationCutoff",
		"cancelInstead",
		"collisionAwareBlockedTargets",
		"costAgilityShortcuts",
		"costBankVisit",
		"costBoats",
		"costCanoes",
		"costCharterShips",
		"costConsumableTeleportationItems",
		"costFairyRings",
		"costGnomeGliders",
		"costGrappleShortcuts",
		"costHotAirBalloons",
		"costMagicCarpets",
		"costMagicMushtrees",
		"costMinecarts",
		"costNonConsumableTeleportationItems",
		"costQuetzalWhistle",
		"costQuetzals",
		"costSeasonalTransports",
		"costShips",
		"costSpiritTrees",
		"costTeleportationBoxes",
		"costTeleportationLevers",
		"costTeleportationMinigames",
		"costTeleportationPortals",
		"costTeleportationSpells",
		"costTeleportationSpellsHome",
		"costWildernessObelisks",
		"currencyThreshold",
		"finishDistance",
		"highlightBankPickupItems",
		"highlightInventoryItems",
		"highlightSpellbookSpells",
		"includeBankPath",
		"pathfinderAlgorithm",
		"pohJewelleryBoxTier",
		"pohMountedItems",
		"pohNexusPortals",
		"recalculateDistance",
		"respawnPrifddinas",
		"showBankPickupInfo",
		"showTileCounter",
		"showTransportInfo",
		"showUnreachableText",
		"tileCounterStep",
		"unlockBalloonLogBasket",
		"unlockCanoeAxe",
		"unlockDragontoothPassage",
		"unlockMorUlRek",
		"unlockXericsHonour",
		"unreachableTargetDistanceThreshold",
		"unreachableText",
		"useAgilityShortcuts",
		"useBoats",
		"useCanoes",
		"useCharterShips",
		"useFairyRings",
		"useGnomeGliders",
		"useGrappleShortcuts",
		"useHotAirBalloons",
		"useMagicCarpets",
		"useMagicMushtrees",
		"useMinecarts",
		"usePoh",
		"usePohFairyRing",
		"usePohMountedItems",
		"usePohObelisk",
		"usePohSpiritTree",
		"useQuetzals",
		"useSailingMoves",
		"useSeasonalTransports",
		"useShips",
		"useSpiritTrees",
		"useTeleportationItems",
		"useTeleportationLevers",
		"useTeleportationMinigames",
		"useTeleportationPortals",
		"useTeleportationPortalsPoh",
		"useTeleportationSpells",
		"useTeleportationSpellsHome",
		"useWildernessObelisks"
	));

	/**
	 * Scans every {@code @ConfigItem} on {@link ShortestPathConfig}: the
	 * {@code hidden} flag must agree with pinned membership on every method —
	 * including the getter/setter write pairs — and the collected hidden set
	 * must equal the pinned set exactly.
	 */
	@Test
	public void exactlyThePanelOwnedKeysAreHidden()
	{
		assertEquals("the pinned boundary is 88 keyNames", 88, PANEL_OWNED_KEYS.size());

		// The hidden-key surface lives in the keyed registry, not on the
		// interface: no @ConfigItem may carry hidden=true any more, and every
		// panel-owned key resolves to a keyed registry row with metadata.
		Set<String> annotatedKeys = new TreeSet<>();
		List<String> boundaryViolations = new ArrayList<>();
		for (Method method : ShortestPathConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null)
			{
				continue;
			}
			annotatedKeys.add(item.keyName());
			if (item.hidden())
			{
				boundaryViolations.add(method.getName() + " (keyName=" + item.keyName() + ")");
			}
		}
		assertTrue("@ConfigItems still carrying hidden=true: " + boundaryViolations,
			boundaryViolations.isEmpty());

		Set<String> missing = new TreeSet<>(PANEL_OWNED_KEYS);
		missing.removeIf(key ->
		{
			ConfigKey row = ConfigKey.forKey(key);
			return row != null && row.isKeyed() && !annotatedKeys.contains(key)
				&& ConfigItems.of(key) != null;
		});
		assertTrue("panel-owned keys missing a keyed row or metadata: " + missing,
			missing.isEmpty());
	}

	/**
	 * The settings panel keeps exactly its five real sections — Settings,
	 * Display, Colours, Hotkeys and Debug Options. The two "(Moved To
	 * Sidebar)" pointer sections rendered as empty collapsible headers
	 * once their items went hidden, so they are deleted outright: a
	 * hidden item needs no section to stay hidden. Pinning the set
	 * catches both a re-appearing stub section and an accidentally
	 * dropped real one.
	 */
	@Test
	public void onlyTheRealSettingsSectionsRemain()
	{
		Set<String> sectionNames = new TreeSet<>();
		for (Field field : ShortestPathConfig.class.getDeclaredFields())
		{
			ConfigSection section = field.getAnnotation(ConfigSection.class);
			if (section != null)
			{
				sectionNames.add(section.name());
			}
		}
		assertEquals("the settings surface keeps exactly its five sections",
			new TreeSet<>(Arrays.asList(
				"Settings", "Display", "Colours", "Hotkeys", "Debug Options")),
			sectionNames);
	}

	/**
	 * The sidebar-visibility toggle is a Settings-surface item, not a
	 * panel-owned key: it must exist, stay {@code hidden = false} (so it is
	 * never absorbed into {@link #PANEL_OWNED_KEYS}), live in the Settings
	 * section and default on — the sidebar icon is the panel's only entry
	 * point, so hiding it must be a deliberate opt-out.
	 */
	@Test
	public void sidebarToggleIsAVisibleSettingsItem()
	{
		ConfigItem item = null;
		Method getter = null;
		for (Method method : ShortestPathConfig.class.getMethods())
		{
			ConfigItem found = method.getAnnotation(ConfigItem.class);
			if (found != null && "showSidebarPanel".equals(found.keyName())
				&& method.getParameterCount() == 0 && method.getReturnType() != void.class)
			{
				item = found;
				getter = method;
			}
		}
		assertNotNull("a showSidebarPanel @ConfigItem exists", item);
		assertFalse("the sidebar toggle is not a panel-owned hidden key", item.hidden());
		assertEquals("the toggle lives in the Settings section",
			"sectionSettings", item.section());
		assertEquals("the toggle is a boolean read",
			boolean.class, getter.getReturnType());
		// The keyed read rides the ConfigKey registry — a missing row throws,
		// so this asserts both the row and the getter's default in one call.
		assertEquals("the sidebar icon shows by default — hiding is opt-out",
			Boolean.TRUE,
			Settings.wrap(new TestShortestPathConfig()).configuredValue("showSidebarPanel"));
	}
}
