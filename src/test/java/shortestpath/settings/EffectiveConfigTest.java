package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import net.runelite.client.config.Keybind;
import shortestpath.ShortestPathConfig;
import shortestpath.TestShortestPathConfig;
import shortestpath.TileCounter;
import shortestpath.TileStyle;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;

/**
 * Pins the {@link EffectiveConfig} coercion contract: every coercible category
 * applies payload values only when they carry exactly the right runtime type
 * (or, for the string-parsed enums, exactly a {@code fromType} display string),
 * and every negative-space key stays inert no matter what the map holds.
 */
public class EffectiveConfigTest
{
	private static ShortestPathConfig base()
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
		assertFalse(effective.avoidWilderness());
	}

	@Test
	public void booleanOverrideWrongTypeIsInert()
	{
		EffectiveConfig effective = effectiveWith(Map.of("avoidWilderness", "yes"));
		assertEquals(base().avoidWilderness(), effective.avoidWilderness());
	}

	@Test
	public void intOverrideApplies()
	{
		EffectiveConfig effective = effectiveWith(Map.of("currencyThreshold", 50));
		assertEquals(50, effective.currencyThreshold());
	}

	@Test
	public void intOverrideWrongTypeIsInert()
	{
		// String "50" is not an Integer — the configured value stands.
		EffectiveConfig effective = effectiveWith(Map.of("currencyThreshold", "50"));
		assertEquals(base().currencyThreshold(), effective.currencyThreshold());
	}

	@Test
	public void teleportationItemDisplayStringApplies()
	{
		EffectiveConfig effective = effectiveWith(Map.of("useTeleportationItems", "Inventory and Bank"));
		assertEquals(TeleportationItem.INVENTORY_AND_BANK, effective.useTeleportationItems());
	}

	@Test
	public void teleportationItemEnumNameIsInert()
	{
		// Payload grammar is the fromType display string, not Enum.name().
		EffectiveConfig effective = effectiveWith(Map.of("useTeleportationItems", "INVENTORY_AND_BANK"));
		assertEquals(base().useTeleportationItems(), effective.useTeleportationItems());
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
		assertEquals(base().unreachableText(), effective.unreachableText());
	}

	@Test
	public void blockedTeleportItemsKeyIsInert()
	{
		// The refresh path parses the configured CSV directly; a payload
		// value must never masquerade as applied through the decorator.
		EffectiveConfig effective = effectiveWith(Map.of("blockedTeleportItems", "13103"));
		assertEquals(base().blockedTeleportItems(), effective.blockedTeleportItems());
	}

	@Test
	public void setKeysAreInert()
	{
		// Even correctly-typed Set payloads never reach the getters.
		EffectiveConfig effective = effectiveWith(Map.of(
			"pohNexusPortals", Set.of(PohNexusPortal.FALADOR),
			"pohMountedItems", Set.of(PohMountedItem.GLORY)));
		assertEquals(base().pohNexusPortals(), effective.pohNexusPortals());
		assertEquals(base().pohMountedItems(), effective.pohMountedItems());
	}

	@Test
	public void backendKeyIsInert()
	{
		EffectiveConfig effective = effectiveWith(Map.of("pathfinderBackend", PathfinderBackend.EXACT));
		assertEquals(PathfinderBackend.LEGACY, effective.pathfinderBackend());
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
		assertEquals(base().avoidWilderness(), settings.effective().avoidWilderness());
		assertEquals(base().currencyThreshold(), settings.effective().currencyThreshold());
		assertEquals(42, settings.rawOverrides().get("notARealKey"));
	}

	@Test
	public void emptyPayloadDoesNotClearOverrides()
	{
		Settings settings = Settings.wrap(base());
		settings.applyOverrides(Map.of("avoidWilderness", false));
		settings.applyOverrides(Map.of());
		assertFalse(settings.effective().avoidWilderness());
	}

	@Test
	public void clearOverridesRestoresBase()
	{
		Settings settings = Settings.wrap(base());
		settings.applyOverrides(Map.of("avoidWilderness", false));
		settings.clearOverrides();
		assertTrue(settings.effective().avoidWilderness());
		assertTrue(settings.rawOverrides().isEmpty());
	}

	@Test
	public void keyedCoerceIntUsesCallerSuppliedKey()
	{
		// The quetzal-whistle override key pairs with the shared costQuetzals
		// base getter — the caller supplies the key, not the decorator.
		ShortestPathConfig base = base();
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
		assertEquals(stub.avoidWilderness(), wrapped.effective().avoidWilderness());
		assertEquals(stub.currencyThreshold(), wrapped.effective().currencyThreshold());
		assertEquals(stub.useTeleportationItems(), wrapped.effective().useTeleportationItems());
		assertEquals(stub.unreachableText(), wrapped.effective().unreachableText());
		assertEquals(stub.colourText(), wrapped.effective().colourText());
		assertEquals(stub.drawTiles(), wrapped.display().drawTiles());
	}
}
