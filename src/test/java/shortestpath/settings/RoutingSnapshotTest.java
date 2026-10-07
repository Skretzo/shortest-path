package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import net.runelite.client.events.ConfigChanged;
import shortestpath.TestShortestPathConfig;
import shortestpath.requirement.RoutingPolicy;
import shortestpath.settings.TeleportationItem;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.transport.TransportType;
import shortestpath.transport.TransportTypeConfig;

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
			0, includeBankPath,
			Set.of(), Map.of());
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

	/**
	 * The service builds the routing policy itself: the built snapshot is
	 * published into the {@link #routing()} slot before the call returns, and
	 * the enabled-type set is frozen off the (post-disableUnless)
	 * transport-type view it was handed.
	 */
	/**
	 * The unlocks view must carry every declared {@code unlock*} registry
	 * row — a missing member is invisible until a consumer trusts the
	 * snapshot and silently loses an unlock.
	 */
	@Test
	public void unlocksViewCoversEveryDeclaredUnlockKey()
	{
		Set<String> fields = new HashSet<>();
		for (Field field : Settings.UnlocksView.class.getDeclaredFields())
		{
			fields.add(field.getName());
		}
		for (ConfigKey row : ConfigKey.values())
		{
			if (row.getKey().startsWith("unlock"))
			{
				assertTrue("UnlocksView has no member for declared unlock key "
					+ row.getKey(), fields.contains(row.getKey()));
			}
		}
	}

	@Test
	public void buildRoutingPolicyPublishesTheBuiltSnapshot()
	{
		TestShortestPathConfig config = new TestShortestPathConfig();
		Settings settings = Settings.wrap(config);
		TransportTypeConfig transportTypeConfig = new TransportTypeConfig(config, settings);
		transportTypeConfig.setEnabled(TransportType.BOAT, false);

		RoutingPolicy policy = settings.buildRoutingPolicy(transportTypeConfig,
			false, true, false, true, Set.of(), Set.of(),
			JewelleryBoxTier.ORNATE, 42, true, Set.of(), Map.of());

		assertSame("the built policy is the published snapshot", policy, settings.routing());
		assertTrue(policy.isTransportTypeEnabled(TransportType.TRANSPORT));
		assertFalse("disableUnless-derived state is frozen into the build",
			policy.isTransportTypeEnabled(TransportType.BOAT));
		assertTrue(policy.usePohFairyRing());
		assertFalse(policy.usePoh());
		assertEquals(42, policy.currencyThreshold());
		assertTrue(policy.includeBankPath());
	}
}
