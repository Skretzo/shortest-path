package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import net.runelite.client.events.ConfigChanged;
import shortestpath.TestShortestPathConfig;
import shortestpath.requirement.RoutingPolicy;
import shortestpath.settings.TeleportationItem;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.transport.TransportType;

/**
 * Pins the two publication cadences on {@link Settings}: the view snapshots
 * (and the {@link EffectiveConfig} they derive from) are frozen between
 * republish events, while {@link #routing()} carries the refresh-built policy
 * on a separate slot that republishes never touch.
 */
public class RoutingSnapshotTest
{
	private static RoutingPolicy policy(boolean includeBankPath)
	{
		return new RoutingPolicy(
			EnumSet.of(TransportType.BOAT),
			TeleportationItem.NONE,
			false, false, false, false,
			Set.of(), Set.of(),
			JewelleryBoxTier.ORNATE,
			0, includeBankPath);
	}

	@Test
	public void routingIsNullUntilPublished()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		assertNull(settings.routing());
	}

	@Test
	public void republishDoesNotTouchTheRoutingSlot()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		RoutingPolicy policy = policy(true);
		settings.publishRouting(policy);

		settings.applyOverrides(Map.of("avoidWilderness", false));
		assertSame(policy, settings.routing());

		settings.clearOverrides();
		assertSame(policy, settings.routing());
	}

	@Test
	public void heldEffectiveConfigStaysFrozenAfterRepublish()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		EffectiveConfig held = settings.effective();

		settings.applyOverrides(Map.of("avoidWilderness", false, "currencyThreshold", 50));

		assertTrue("the held decorator still reads the pre-apply map",
			held.avoidWilderness());
		assertFalse("the published decorator reads the new map",
			settings.effective().avoidWilderness());
		assertEquals(50, settings.effective().currencyThreshold());
		assertNotSame(held, settings.effective());
	}

	@Test
	public void viewSnapshotsFreezeUntilARepublishEvent()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		Settings.BankView heldView = settings.bank();

		// Mutating the base config alone republishes nothing: a frozen view
		// keeps the captured value rather than tracking the live base.
		stub.setIncludeBankPathValue(true);
		assertFalse(heldView.includeBankPath());
		assertSame(heldView, settings.bank());

		// Any publication rebuilds the views off the current base + overrides.
		settings.applyOverrides(Map.of("avoidWilderness", false));
		assertTrue(settings.bank().includeBankPath());
		assertNotSame(heldView, settings.bank());
	}

	@Test
	public void configChangeEventRepublishesViews()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		Settings.BankView heldView = settings.bank();

		stub.setIncludeBankPathValue(true);

		ConfigChanged event = new ConfigChanged();
		event.setGroup("shortestpath");
		event.setKey("includeBankPath");
		settings.onConfigChanged(event);

		assertNotSame(heldView, settings.bank());
		assertTrue(settings.bank().includeBankPath());
	}

	@Test
	public void foreignGroupEventIsIgnored()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		Settings.BankView heldView = settings.bank();

		ConfigChanged event = new ConfigChanged();
		event.setGroup("someOtherPlugin");
		event.setKey("includeBankPath");
		assertNull(settings.onConfigChanged(event));

		assertSame(heldView, settings.bank());
	}
}
