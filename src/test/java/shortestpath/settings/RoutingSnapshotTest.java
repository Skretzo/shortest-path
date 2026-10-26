package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import net.runelite.client.events.ConfigChanged;
import shortestpath.TestShortestPathConfig;
import shortestpath.requirement.RoutingPolicy;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.transport.TransportType;
import shortestpath.transport.TransportTypeConfig;

/**
 * Pins the publication cadence on {@link Settings}: the view snapshots (and
 * the {@link EffectiveConfig} they derive from) are frozen between republish
 * events — a held snapshot keeps its captured values rather than tracking the
 * live base.
 */
public class RoutingSnapshotTest
{
	@Test
	public void heldEffectiveConfigStaysFrozenAfterRepublish()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		EffectiveConfig held = settings.effective();

		settings.applyOverrides(Map.of("avoidWilderness", false, "currencyThreshold", 50));

		assertTrue("the held decorator still reads the pre-apply map",
			held.bool(ConfigKey.AVOID_WILDERNESS));
		assertFalse("the published decorator reads the new map",
			settings.effective().bool(ConfigKey.AVOID_WILDERNESS));
		assertEquals(50, settings.effective().num(ConfigKey.CURRENCY_THRESHOLD));
		assertNotSame(held, settings.effective());
	}

	@Test
	public void viewSnapshotsFreezeUntilARepublishEvent()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		Settings.DisplayView heldView = settings.display();

		// Mutating the base config alone republishes nothing: a frozen view
		// keeps the captured value rather than tracking the live base.
		stub.setShowBankPickupInfoValue(true);
		assertFalse(heldView.showBankPickupInfo());
		assertSame(heldView, settings.display());

		// Any publication rebuilds the views off the current base + overrides.
		settings.applyOverrides(Map.of("avoidWilderness", false));
		assertTrue(settings.display().showBankPickupInfo());
		assertNotSame(heldView, settings.display());
	}

	@Test
	public void configChangeEventRepublishesViews()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		Settings.DisplayView heldView = settings.display();

		stub.setShowBankPickupInfoValue(true);

		ConfigChanged event = new ConfigChanged();
		event.setGroup("shortestpath");
		event.setKey("showBankPickupInfo");
		settings.onConfigChanged(event);

		assertNotSame(heldView, settings.display());
		assertTrue(settings.display().showBankPickupInfo());
	}

	@Test
	public void foreignGroupEventIsIgnored()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		Settings.DisplayView heldView = settings.display();

		ConfigChanged event = new ConfigChanged();
		event.setGroup("someOtherPlugin");
		event.setKey("showBankPickupInfo");
		assertNull(settings.onConfigChanged(event));

		assertSame(heldView, settings.display());
	}

	/**
	 * The service builds the routing policy itself: the enabled-type set in
	 * the built snapshot is frozen off the (post-{@code disableUnless})
	 * transport-type view it was handed, and the scalar arguments are the
	 * effective values the caller already computed this refresh.
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
	public void buildRoutingPolicyBuildsTheSnapshot()
	{
		TestShortestPathConfig config = new TestShortestPathConfig();
		Settings settings = Settings.wrap(config);
		TransportTypeConfig transportTypeConfig = new TransportTypeConfig(config, settings);
		transportTypeConfig.setEnabled(TransportType.BOAT, false);

		RoutingPolicy policy = settings.buildRoutingPolicy(transportTypeConfig,
			false, true, false, true, Set.of(), Set.of(),
			JewelleryBoxTier.ORNATE, 42, true, Set.of(), Map.of());

		assertTrue(policy.isTransportTypeEnabled(TransportType.TRANSPORT));
		assertFalse("disableUnless-derived state is frozen into the build",
			policy.isTransportTypeEnabled(TransportType.BOAT));
		assertTrue(policy.usePohFairyRing());
		assertFalse(policy.usePoh());
		assertEquals(42, policy.currencyThreshold());
		assertTrue(policy.includeBankPath());
	}
}
