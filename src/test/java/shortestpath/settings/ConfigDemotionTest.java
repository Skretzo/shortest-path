package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import net.runelite.client.config.ConfigItem;
import shortestpath.ShortestPathConfig;

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
	 * state keys. 70 names; the write-pair keys
	 * ({@code builtTeleportationBoxes}/{@code builtTeleportationPortalsPoh})
	 * appear once even though each is declared on both a getter and a
	 * {@code void set*} method.
	 */
	private static final Set<String> PANEL_OWNED_KEYS = new TreeSet<>(Arrays.asList(
		"avoidWilderness",
		"blockedTeleportItems",
		"builtTeleportationBoxes",
		"builtTeleportationPortalsPoh",
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
		"includeBankPath",
		"pohJewelleryBoxTier",
		"pohMountedItems",
		"pohNexusPortals",
		"respawnPrifddinas",
		"unlockBalloonLogBasket",
		"unlockCanoeAxe",
		"unlockDragontoothPassage",
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
		assertEquals("the pinned boundary is 70 keyNames", 70, PANEL_OWNED_KEYS.size());

		Set<String> hidden = new TreeSet<>();
		List<String> boundaryViolations = new ArrayList<>();
		for (Method method : ShortestPathConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null)
			{
				continue;
			}
			if (item.hidden())
			{
				hidden.add(item.keyName());
			}
			if (item.hidden() != PANEL_OWNED_KEYS.contains(item.keyName()))
			{
				boundaryViolations.add(method.getName() + " (keyName=" + item.keyName()
					+ ", hidden=" + item.hidden() + ")");
			}
		}

		assertTrue("methods whose hidden flag sits on the wrong side of the "
			+ "demotion boundary: " + boundaryViolations, boundaryViolations.isEmpty());

		Set<String> lostHidden = new TreeSet<>(PANEL_OWNED_KEYS);
		lostHidden.removeAll(hidden);
		assertTrue("panel-owned keys that lost hidden=true: " + lostHidden, lostHidden.isEmpty());

		Set<String> wronglyHidden = new TreeSet<>(hidden);
		wronglyHidden.removeAll(PANEL_OWNED_KEYS);
		assertTrue("settings-surface keys that wrongly gained hidden=true: " + wronglyHidden,
			wronglyHidden.isEmpty());

		assertEquals("hidden keyName set is exactly the panel-owned set",
			PANEL_OWNED_KEYS, hidden);
	}
}
