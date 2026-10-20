package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import net.runelite.client.config.Keybind;
import shortestpath.ShortestPathConfig;
import shortestpath.TestShortestPathConfig;
import shortestpath.TileCounter;
import shortestpath.TileStyle;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.poh.PohMountedItem;
import shortestpath.poh.PohNexusPortal;
import shortestpath.transport.BalloonRoute;

/**
 * Pins the {@link EffectiveConfig} coercion contract: every coercible category
 * applies payload values only when they carry exactly the right runtime type
 * (or, for the string-parsed enums, exactly a {@code fromType} display string),
 * and every negative-space key stays inert no matter what the map holds.
 */
public class EffectiveConfigTest
{
	private static TestShortestPathConfig base()
	{
		return new TestShortestPathConfig();
	}

	private static EffectiveConfig effectiveWith(Map<String, Object> overrides)
	{
		Settings settings = Settings.wrap(base());
		settings.applyOverrides(overrides);
		return settings.effective();
	}

	@Test
	public void booleanOverrideApplies()
	{
		// The stub's default is true; a false payload value proves the override took effect.
		EffectiveConfig effective = effectiveWith(Map.of("avoidWilderness", false));
		assertFalse(effective.bool(ConfigKey.AVOID_WILDERNESS));
	}

	@Test
	public void booleanOverrideWrongTypeIsInert()
	{
		EffectiveConfig effective = effectiveWith(Map.of("avoidWilderness", "yes"));
		assertEquals(base().avoidWilderness(), effective.bool(ConfigKey.AVOID_WILDERNESS));
	}

	@Test
	public void intOverrideApplies()
	{
		EffectiveConfig effective = effectiveWith(Map.of("currencyThreshold", 50));
		assertEquals(50, effective.num(ConfigKey.CURRENCY_THRESHOLD));
	}

	@Test
	public void intOverrideWrongTypeIsInert()
	{
		// String "50" is not an Integer — the configured value stands.
		EffectiveConfig effective = effectiveWith(Map.of("currencyThreshold", "50"));
		assertEquals(base().currencyThreshold(), effective.num(ConfigKey.CURRENCY_THRESHOLD));
	}

	@Test
	public void jewelleryBoxTierDisplayStringApplies()
	{
		EffectiveConfig effective = effectiveWith(Map.of("pohJewelleryBoxTier", "Fancy"));
		assertEquals(JewelleryBoxTier.FANCY, effective.pohJewelleryBoxTier());
	}

	@Test
	public void jewelleryBoxTierEnumNameIsInert()
	{
		EffectiveConfig effective = effectiveWith(Map.of("pohJewelleryBoxTier", "FANCY"));
		assertEquals(base().pohJewelleryBoxTier(), effective.pohJewelleryBoxTier());
	}

	@Test
	public void tileCounterDisplayStringApplies()
	{
		EffectiveConfig effective = effectiveWith(Map.of("showTileCounter", "Remaining"));
		assertEquals(TileCounter.REMAINING, effective.showTileCounter());
	}

	@Test
	public void tileCounterEnumNameIsInert()
	{
		EffectiveConfig effective = effectiveWith(Map.of("showTileCounter", "REMAINING"));
		assertEquals(base().showTileCounter(), effective.showTileCounter());
	}

	@Test
	public void tileStyleDisplayStringApplies()
	{
		EffectiveConfig effective = effectiveWith(Map.of("pathStyle", "Lines"));
		assertEquals(TileStyle.LINES, effective.pathStyle());
	}

	@Test
	public void tileStyleEnumNameIsInert()
	{
		EffectiveConfig effective = effectiveWith(Map.of("pathStyle", "LINES"));
		assertEquals(base().pathStyle(), effective.pathStyle());
	}

	@Test
	public void colorOverrideApplies()
	{
		EffectiveConfig effective = effectiveWith(Map.of("colourText", Color.RED));
		assertEquals(Color.RED, effective.colourText());
	}

	@Test
	public void colorOverrideWrongTypeIsInert()
	{
		// Only a Color instance applies — a hex string falls back to base.
		EffectiveConfig effective = effectiveWith(Map.of("colourText", "#FF0000"));
		assertEquals(base().colourText(), effective.colourText());
	}

	// — Negative space: these keys are inert even when the map holds a value —

	@Test
	public void stringKeyIsInert()
	{
		EffectiveConfig effective = effectiveWith(Map.of("unreachableText", "custom unreachable text"));
		assertEquals(base().unreachableText(), effective.val(ConfigKey.UNREACHABLE_TEXT));
	}

	@Test
	public void blockedTeleportItemsKeyIsInert()
	{
		// The refresh path parses the configured CSV directly; a payload
		// value must never masquerade as applied through the decorator.
		EffectiveConfig effective = effectiveWith(Map.of("blockedTeleportItems", "13103"));
		assertEquals(base().blockedTeleportItems(), effective.str(ConfigKey.BLOCKED_TELEPORT_ITEMS));
	}

	@Test
	public void setKeysAreInert()
	{
		// Even correctly-typed Set payloads never reach the getters.
		EffectiveConfig effective = effectiveWith(Map.of(
			"pohNexusPortals", Set.of(PohNexusPortal.FALADOR),
			"pohMountedItems", Set.of(PohMountedItem.GLORY)));
		assertEquals(base().pohNexusPortals(), effective.set(ConfigKey.POH_NEXUS_PORTALS));
		assertEquals(base().pohMountedItems(), effective.set(ConfigKey.POH_MOUNTED_ITEMS));
	}

	@Test
	public void balloonFlagOverrideReachesTheUnwrittenRouteSet()
	{
		// The route set is payload-inert, but its unwritten default derives
		// from the legacy flag — an override on the flag must move the
		// derived set too, or the snapshot advertises the unlock while
		// routing unlocks nothing.
		EffectiveConfig effective = effectiveWith(Map.of("unlockBalloonLogBasket", true));
		assertTrue(effective.bool(ConfigKey.UNLOCK_BALLOON_LOG_BASKET));
		assertEquals(EnumSet.allOf(BalloonRoute.class), effective.balloonLogBasketRoutes());
	}

	@Test
	public void balloonFlagOffOverrideClearsTheFlagDerivedRouteSet()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		stub.setUnlockBalloonLogBasketValue(true);
		Settings settings = Settings.wrap(stub);
		settings.applyOverrides(Map.of("unlockBalloonLogBasket", false));
		assertEquals(EnumSet.noneOf(BalloonRoute.class),
			settings.effective().balloonLogBasketRoutes());
	}

	@Test
	public void writtenBalloonRouteSetWinsOverFlagOverride()
	{
		// The first written set takes over: an explicit partial selection
		// is not re-derived from the flag.
		TestShortestPathConfig stub = new TestShortestPathConfig();
		stub.setBalloonLogBasketRoutesValue(EnumSet.of(BalloonRoute.GRAND_TREE));
		Settings settings = Settings.wrap(stub);
		settings.applyOverrides(Map.of("unlockBalloonLogBasket", true));
		assertEquals(EnumSet.of(BalloonRoute.GRAND_TREE),
			settings.effective().balloonLogBasketRoutes());
	}

	@Test
	public void balloonRouteSetPayloadStaysInert()
	{
		// The set key itself keeps its NONE coercion — a payload carrying
		// a set value never reaches the getter.
		EffectiveConfig effective = effectiveWith(Map.of(
			"balloonLogBasketRoutes", Set.of(BalloonRoute.GRAND_TREE)));
		assertEquals(base().balloonLogBasketRoutes(), effective.balloonLogBasketRoutes());
	}

	@Test
	public void backendKeyIsInert()
	{
		EffectiveConfig effective = effectiveWith(Map.of("pathfinderAlgorithm", PathfinderBackend.ASTAR));
		assertEquals(PathfinderBackend.LEGACY, effective.val(ConfigKey.PATHFINDER_ALGORITHM));
	}

	@Test
	public void keybindKeyIsInert()
	{
		EffectiveConfig effective = effectiveWith(Map.of("clearPathHotkey", new Keybind(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK)));
		assertEquals(Keybind.NOT_SET, effective.clearPathHotkey());
	}

	@Test
	public void unknownKeyIsInertButEchoed()
	{
		Settings settings = Settings.wrap(base());
		settings.applyOverrides(Map.of("notARealKey", 42));
		assertEquals(base().avoidWilderness(), settings.effective().bool(ConfigKey.AVOID_WILDERNESS));
		assertEquals(base().currencyThreshold(), settings.effective().num(ConfigKey.CURRENCY_THRESHOLD));
		assertEquals(42, settings.rawOverrides().get("notARealKey"));
	}

	@Test
	public void emptyPayloadDoesNotClearOverrides()
	{
		Settings settings = Settings.wrap(base());
		settings.applyOverrides(Map.of("avoidWilderness", false));
		settings.applyOverrides(Map.of());
		assertFalse(settings.effective().bool(ConfigKey.AVOID_WILDERNESS));
	}

	@Test
	public void clearOverridesRestoresBase()
	{
		Settings settings = Settings.wrap(base());
		settings.applyOverrides(Map.of("avoidWilderness", false));
		settings.clearOverrides();
		assertTrue(settings.effective().bool(ConfigKey.AVOID_WILDERNESS));
		assertTrue(settings.rawOverrides().isEmpty());
	}

	@Test
	public void keyedCoerceIntUsesCallerSuppliedKey()
	{
		// The quetzal-whistle override key pairs with the shared costQuetzals
		// base getter — the caller supplies the key, not the decorator.
		TestShortestPathConfig base = base();
		Settings settings = Settings.wrap(base);
		settings.applyOverrides(Map.of("costQuetzalWhistle", 7));
		assertEquals(7, settings.coerceInt("costQuetzalWhistle", base.costQuetzals()));
		assertEquals(base.costQuetzals(), settings.coerceInt("costQuetzals", base.costQuetzals()));
	}

	@Test
	public void effectiveConfigDeclaresEveryInterfaceMethod() throws Exception
	{
		// A missed method would compile silently — it would inherit the
		// interface default body and ignore the base config entirely.
		for (Method method : ShortestPathConfig.class.getDeclaredMethods())
		{
			Method declared = EffectiveConfig.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
			assertEquals("not overridden by EffectiveConfig: " + method.getName(),
				EffectiveConfig.class, declared.getDeclaringClass());
		}
	}

	@Test
	public void wrapIsAPurePassthrough()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings wrapped = Settings.wrap(stub);
		assertSame(stub, wrapped.configured());
		assertTrue(wrapped.rawOverrides().isEmpty());
		assertEquals(stub.avoidWilderness(), wrapped.effective().bool(ConfigKey.AVOID_WILDERNESS));
		assertEquals(stub.currencyThreshold(), wrapped.effective().num(ConfigKey.CURRENCY_THRESHOLD));
		assertEquals(stub.unreachableText(), wrapped.effective().val(ConfigKey.UNREACHABLE_TEXT));
		assertEquals(stub.colourText(), wrapped.effective().colourText());
		assertEquals(stub.drawTiles(), wrapped.display().drawTiles());
	}
}
