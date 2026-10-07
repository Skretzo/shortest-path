package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.Test;

import net.runelite.client.events.ConfigChanged;
import shortestpath.TestShortestPathConfig;

/**
 * Pins the typed {@link ConfigChange} classification contract: every config
 * key resolves to the exact {@link Effect} set the shell must act on. The
 * routing-read keys carry {@link Effect#ROUTE_INVALIDATING} — the same rows
 * the refresh path reads, so "is read by routing" and "invalidates the route"
 * cannot diverge — while display-only keys never invalidate and the two
 * side-effect keys carry their own effects.
 *
 * <p>Coverage is per-key and explicit: the former restart-regex arms, the
 * keys the regex silently missed but the refresh path always read, the
 * multi-effect backend key, the early-return debug-overlay key, and the
 * display-only keys.
 */
public class ConfigChangeTest
{
	private static final Set<Effect> ROUTE_ONLY = EnumSet.of(Effect.ROUTE_INVALIDATING);
	private static final Set<Effect> DISPLAY = EnumSet.of(Effect.DISPLAY_ONLY);

	private static ConfigChange change(String key)
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		ConfigChanged event = new ConfigChanged();
		event.setGroup("shortestpath");
		event.setKey(key);
		return settings.onConfigChanged(event);
	}

	private static void assertEffects(String key, Set<Effect> expected)
	{
		ConfigChange change = change(key);
		assertEquals("fact carries the event key", key, change.getKey());
		assertEquals("effects for " + key, expected, change.getEffects());
	}

	private static void assertRouteInvalidating(String key)
	{
		assertEffects(key, ROUTE_ONLY);
	}

	private static void assertDisplayOnly(String key)
	{
		ConfigChange change = change(key);
		assertEquals("fact carries the event key", key, change.getKey());
		assertFalse(key + " must not invalidate a running route",
			change.getEffects().contains(Effect.ROUTE_INVALIDATING));
		assertEquals("effects for " + key, DISPLAY, change.getEffects());
	}

	@Test
	public void formerRestartArmsAreRouteInvalidating()
	{
		assertRouteInvalidating("avoidWilderness");
		assertRouteInvalidating("includeBankPath");
		assertRouteInvalidating("currencyThreshold");
		assertRouteInvalidating("exactHeuristicWeight");
		assertRouteInvalidating("usePoh");
		assertRouteInvalidating("costConsumableTeleportationItems");
		assertRouteInvalidating("unlockCanoeAxe");
	}

	@Test
	public void transportToggleAndCostKeysAreRouteInvalidating()
	{
		assertRouteInvalidating("useAgilityShortcuts");
		assertRouteInvalidating("useGrappleShortcuts");
		assertRouteInvalidating("useBoats");
		assertRouteInvalidating("useCanoes");
		assertRouteInvalidating("useCharterShips");
		assertRouteInvalidating("useShips");
		assertRouteInvalidating("useFairyRings");
		assertRouteInvalidating("useGnomeGliders");
		assertRouteInvalidating("useHotAirBalloons");
		assertRouteInvalidating("useMagicCarpets");
		assertRouteInvalidating("useMagicMushtrees");
		assertRouteInvalidating("useMinecarts");
		assertRouteInvalidating("useQuetzals");
		assertRouteInvalidating("useSeasonalTransports");
		assertRouteInvalidating("useSpiritTrees");
		assertRouteInvalidating("useTeleportationItems");
		assertRouteInvalidating("useTeleportationLevers");
		assertRouteInvalidating("useTeleportationMinigames");
		assertRouteInvalidating("useTeleportationPortals");
		assertRouteInvalidating("useTeleportationSpells");
		assertRouteInvalidating("useTeleportationSpellsHome");
		assertRouteInvalidating("useWildernessObelisks");
		assertRouteInvalidating("usePohFairyRing");
		assertRouteInvalidating("usePohSpiritTree");
		assertRouteInvalidating("usePohObelisk");

		assertRouteInvalidating("costAgilityShortcuts");
		assertRouteInvalidating("costGrappleShortcuts");
		assertRouteInvalidating("costBoats");
		assertRouteInvalidating("costCanoes");
		assertRouteInvalidating("costCharterShips");
		assertRouteInvalidating("costShips");
		assertRouteInvalidating("costFairyRings");
		assertRouteInvalidating("costGnomeGliders");
		assertRouteInvalidating("costHotAirBalloons");
		assertRouteInvalidating("costMagicCarpets");
		assertRouteInvalidating("costMagicMushtrees");
		assertRouteInvalidating("costMinecarts");
		assertRouteInvalidating("costQuetzals");
		assertRouteInvalidating("costQuetzalWhistle");
		assertRouteInvalidating("costSeasonalTransports");
		assertRouteInvalidating("costSpiritTrees");
		assertRouteInvalidating("costNonConsumableTeleportationItems");
		assertRouteInvalidating("costTeleportationBoxes");
		assertRouteInvalidating("costTeleportationLevers");
		assertRouteInvalidating("costTeleportationMinigames");
		assertRouteInvalidating("costTeleportationPortals");
		assertRouteInvalidating("costTeleportationSpells");
		assertRouteInvalidating("costTeleportationSpellsHome");
		assertRouteInvalidating("costWildernessObelisks");
		assertRouteInvalidating("costBankVisit");
	}

	@Test
	public void unlockKeysAreRouteInvalidating()
	{
		assertRouteInvalidating("unlockCanoeAxe");
		assertRouteInvalidating("unlockXericsHonour");
		assertRouteInvalidating("unlockDragontoothPassage");
	}

	/**
	 * The gap-closure rows: keys the refresh path reads that the original
	 * restart regex never matched — its {@code use\w+}/{@code cost\w+}/
	 * {@code unlock\w+} prefixes and literals could not see the
	 * {@code poh*} names or the unrelated literals below, which gained
	 * regex arms only after the misses were noticed. That drift class is
	 * what the typed table exists to kill. The two {@code use*} toggles
	 * were covered all along; they are pinned here because the refresh
	 * reaches them indirectly, through the {@code pohNexusPortals()}/
	 * {@code pohMountedItems()} default methods.
	 */
	@Test
	public void refreshReadsTheRegexMissedAreRouteInvalidating()
	{
		assertRouteInvalidating("calculationCutoff");
		assertRouteInvalidating("unreachableTargetDistanceThreshold");
		assertRouteInvalidating("respawnPrifddinas");
		assertRouteInvalidating("blockedTeleportItems");
		assertRouteInvalidating("pohNexusPortals");
		assertRouteInvalidating("pohMountedItems");
		assertRouteInvalidating("pohJewelleryBoxTier");
		assertRouteInvalidating("useTeleportationPortalsPoh");
		assertRouteInvalidating("usePohMountedItems");
	}

	@Test
	public void pathfinderBackendCarriesBothEffects()
	{
		assertEffects("pathfinderBackend",
			EnumSet.of(Effect.ROUTE_INVALIDATING, Effect.SIDE_EFFECT_BACKEND_PREP));
	}

	@Test
	public void drawDebugPanelIsOverlaySideEffectOnly()
	{
		assertEffects("drawDebugPanel", EnumSet.of(Effect.SIDE_EFFECT_DEBUG_OVERLAY));
	}

	@Test
	public void displayKeysAreNotRouteInvalidating()
	{
		assertDisplayOnly("unreachableText");
		assertDisplayOnly("showUnreachableText");
		assertDisplayOnly("showTransportInfo");
		assertDisplayOnly("showBankPickupInfo");
		assertDisplayOnly("highlightBankPickupItems");
		assertDisplayOnly("highlightSpellbookSpells");
		assertDisplayOnly("highlightInventoryItems");
		assertDisplayOnly("drawMap");
		assertDisplayOnly("drawMinimap");
		assertDisplayOnly("drawTiles");
		assertDisplayOnly("drawTransports");
		assertDisplayOnly("drawCollisionMap");
		assertDisplayOnly("pathStyle");
		assertDisplayOnly("showTeleportPulse");
		assertDisplayOnly("colourPath");
		assertDisplayOnly("colourPathCalculating");
		assertDisplayOnly("colourPathUnreachable");
		assertDisplayOnly("colourTransports");
		assertDisplayOnly("colourCollisionMap");
		assertDisplayOnly("colourText");
		assertDisplayOnly("colourTeleportPulse");
		assertDisplayOnly("colourBankPickupHighlight");
		assertDisplayOnly("showTileCounter");
		assertDisplayOnly("tileCounterStep");
	}

	/**
	 * Search-lifecycle keys are read mid-search by the shell and the finder,
	 * not by the refresh path — changing them republishes the views but must
	 * not restart a running route.
	 */
	@Test
	public void lifecycleKeysAreNotRouteInvalidating()
	{
		assertDisplayOnly("cancelInstead");
		assertDisplayOnly("recalculateDistance");
		assertDisplayOnly("finishDistance");
		assertDisplayOnly("postTransports");
		assertDisplayOnly("clearPathHotkey");
		assertDisplayOnly("builtTeleportationBoxes");
		assertDisplayOnly("builtTeleportationPortalsPoh");
	}

	@Test
	public void unknownInGroupKeyProducesAnEmptyEffectFact()
	{
		ConfigChange change = change("notARealKey");
		assertEquals("notARealKey", change.getKey());
		assertTrue("unknown keys classify to no effects", change.getEffects().isEmpty());
	}

	@Test
	public void foreignGroupEventProducesNoFact()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		ConfigChanged event = new ConfigChanged();
		event.setGroup("someOtherPlugin");
		event.setKey("avoidWilderness");
		assertNull(settings.onConfigChanged(event));
	}
}
