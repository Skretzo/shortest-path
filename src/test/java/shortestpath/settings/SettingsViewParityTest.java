package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.Map;

import org.junit.Test;

import shortestpath.TestShortestPathConfig;
import shortestpath.TileCounter;
import shortestpath.TileStyle;

/**
 * Member-for-member pin on the {@link Settings.DisplayView} and
 * {@link Settings.LifecycleView} snapshots: every fluent member must read
 * exactly its own key. Boolean members are driven to a mixed true/false
 * pattern and every other member to a distinct value, so a member wired
 * to the wrong key reads a different value. Complements the
 * freeze/cadence coverage in {@link RoutingSnapshotTest}, which pins when
 * the snapshots republish rather than what each member reads.
 */
public class SettingsViewParityTest
{
	@Test
	public void displayViewMembersReadTheirOwnKeys()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		stub.set("unreachableText", "custom unreachable");
		stub.set("unreachableTargetDistanceThreshold", 77);
		stub.set("tileCounterStep", 13);
		Settings settings = Settings.wrap(stub);

		Color collisionMapColour = new Color(11, 22, 33);
		Color pathColour = new Color(44, 55, 66);
		Color pathCalculatingColour = new Color(77, 88, 99);
		Color pathUnreachableColour = new Color(101, 112, 123);
		Color textColour = new Color(134, 145, 156);
		Color transportsColour = new Color(167, 178, 189);
		Color bankPickupColour = new Color(190, 201, 212);
		Color teleportPulseColour = new Color(213, 224, 235);
		settings.applyOverrides(Map.ofEntries(
			Map.entry("drawCollisionMap", true),
			Map.entry("drawMap", false),
			Map.entry("drawMinimap", true),
			Map.entry("drawTiles", false),
			Map.entry("drawTransports", true),
			Map.entry("drawClickPoints", true),
			Map.entry("showTransportInfo", false),
			Map.entry("showBankPickupInfo", true),
			Map.entry("showUnreachableText", false),
			Map.entry("highlightBankPickupItems", true),
			Map.entry("highlightSpellbookSpells", false),
			Map.entry("highlightInventoryItems", true),
			Map.entry("showTeleportPulse", false),
			Map.entry("colourCollisionMap", collisionMapColour),
			Map.entry("colourPath", pathColour),
			Map.entry("colourPathCalculating", pathCalculatingColour),
			Map.entry("colourPathUnreachable", pathUnreachableColour),
			Map.entry("colourText", textColour),
			Map.entry("colourTransports", transportsColour),
			Map.entry("colourBankPickupHighlight", bankPickupColour),
			Map.entry("colourTeleportPulse", teleportPulseColour),
			Map.entry("showTileCounter", "Remaining"),
			Map.entry("pathStyle", "Lines")));

		Settings.DisplayView view = settings.display();
		assertTrue(view.drawCollisionMap());
		assertFalse(view.drawMap());
		assertTrue(view.drawMinimap());
		assertFalse(view.drawTiles());
		assertTrue(view.drawTransports());
		assertTrue(view.drawClickPoints());
		assertFalse(view.showTransportInfo());
		assertTrue(view.showBankPickupInfo());
		assertFalse(view.showUnreachableText());
		assertTrue(view.highlightBankPickupItems());
		assertFalse(view.highlightSpellbookSpells());
		assertTrue(view.highlightInventoryItems());
		assertFalse(view.showTeleportPulse());
		assertEquals(collisionMapColour, view.colourCollisionMap());
		assertEquals(pathColour, view.colourPath());
		assertEquals(pathCalculatingColour, view.colourPathCalculating());
		assertEquals(pathUnreachableColour, view.colourPathUnreachable());
		assertEquals(textColour, view.colourText());
		assertEquals(transportsColour, view.colourTransports());
		assertEquals(bankPickupColour, view.colourBankPickupHighlight());
		assertEquals(teleportPulseColour, view.colourTeleportPulse());
		assertEquals(13, view.tileCounterStep());
		assertEquals(77, view.unreachableTargetDistance());
		assertEquals("custom unreachable", view.unreachableText());
		assertEquals(TileCounter.REMAINING, view.showTileCounter());
		assertEquals(TileStyle.LINES, view.pathStyle());
	}

	@Test
	public void lifecycleViewMembersReadTheirOwnReads()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		stub.set("calculationCutoff", 11);
		stub.set("recalculateDistance", 22);
		stub.set("finishDistance", 33);
		stub.set("cancelInstead", true);
		Settings settings = Settings.wrap(stub);
		// Every raw-read member gets a payload that differs from the stub:
		// the stub value must win. Only postTransports honours its payload.
		settings.applyOverrides(Map.of(
			"postTransports", true,
			"drawDebugPanel", true,
			"cancelInstead", false,
			"calculationCutoff", 99,
			"recalculateDistance", 98,
			"finishDistance", 97));

		Settings.LifecycleView view = settings.lifecycle();
		assertEquals(11, view.calculationCutoff());
		assertEquals(22, view.recalculateDistance());
		assertEquals(33, view.reachedDistance());
		assertTrue("stub true, payload false is ignored", view.cancelInstead());
		// drawDebugPanel is a raw configured read — the payload map never
		// reaches it, so the member reports the stub's false, not the true payload.
		assertFalse("the stub declares drawDebugPanel false", stub.drawDebugPanel());
		assertFalse("stub false, payload true is ignored", view.drawDebugPanel());
		assertTrue(view.postTransports());
	}

	@Test
	public void baseReadsIgnorePayloadValues()
	{
		// unreachableText is inert to payloads (Coercion.NONE): the view
		// member keeps the base read even when the payload map carries the
		// key.
		TestShortestPathConfig stub = new TestShortestPathConfig();
		stub.set("unreachableText", "stored");
		Settings settings = Settings.wrap(stub);
		settings.applyOverrides(Map.of("unreachableText", "payload"));

		assertEquals("stored", settings.display().unreachableText());
	}
}
