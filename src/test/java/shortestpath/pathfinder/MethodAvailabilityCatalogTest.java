package shortestpath.pathfinder;

import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.MethodAvailability;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportMethod;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.transport.TransportType;

/**
 * Covers the teleport-method catalog and its per-method availability classification
 * ({@link PathfinderConfig#getMethodAvailability()}): the catalog must be the same full set in every
 * mode, and each method must carry the reason the player can(not) use it right now — independent of
 * the mode's possession/unlock bypasses.
 */
@RunWith(MockitoJUnitRunner.class)
public class MethodAvailabilityCatalogTest
{
	private static final int COWBELL_DESTINATION = WorldPointUtil.packWorldPoint(3259, 3277, 0);
	private static final int COWBELL_AMULET = 33104;

	@Mock
	Client client;
	@Mock
	ItemContainer inventory;
	@Mock
	ItemContainer bank;
	@Mock
	ShortestPathConfig config;

	@Before
	public void before()
	{
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10000000);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		// Classification must be independent of the (hidden) teleportation-item setting.
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
	}

	/**
	 * An Owned/Inventory planning copy, refreshed so the catalog is built.
	 */
	private PathfinderConfig refreshedPlanningCopy(PathfinderConfig base)
	{
		PathfinderConfig planning = base.copyForPlanning();
		planning.setPlanningMode(false);
		planning.setBypassItemPossession(false);
		planning.setConsiderBank(false);
		planning.refresh();
		return planning;
	}

	private static TeleportMethod cowbellMethod(Map<TeleportMethod, MethodAvailability> catalog)
	{
		return catalog.keySet().stream()
			.filter(m -> m.getType() == TransportType.TELEPORTATION_ITEM && m.getDestination() == COWBELL_DESTINATION)
			.findFirst()
			.orElseThrow(() -> new AssertionError("Cowbell amulet method missing from the catalog"));
	}

	@Test
	public void itemInInventoryClassifiesAvailable()
	{
		doReturn(new Item[]{new Item(COWBELL_AMULET, 1)}).when(inventory).getItems();
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);

		Map<TeleportMethod, MethodAvailability> catalog =
			refreshedPlanningCopy(new TestPathfinderConfig(client, config)).getMethodAvailability();

		assertEquals(MethodAvailability.AVAILABLE, catalog.get(cowbellMethod(catalog)));
	}

	@Test
	public void itemOnlyInBankClassifiesInBank()
	{
		// Bank contents known only via the snapshot captured while the bank was open — the classifier
		// must consult it in EVERY mode (here Owned/Inventory), not just the bank mode.
		PathfinderConfig base = new TestPathfinderConfig(client, config);
		base.setBankSnapshot(new Item[]{new Item(COWBELL_AMULET, 1)});

		Map<TeleportMethod, MethodAvailability> catalog =
			refreshedPlanningCopy(base).getMethodAvailability();

		assertEquals(MethodAvailability.IN_BANK, catalog.get(cowbellMethod(catalog)));
	}

	@Test
	public void itemNowhereClassifiesMissingItem()
	{
		Map<TeleportMethod, MethodAvailability> catalog =
			refreshedPlanningCopy(new TestPathfinderConfig(client, config)).getMethodAvailability();

		assertEquals(MethodAvailability.MISSING_ITEM, catalog.get(cowbellMethod(catalog)));
	}

	@Test
	public void lowSkillLevelClassifiesMissingLevel()
	{
		// Magic level 3: Varrock Teleport (25 Magic) is level-gated before any item check.
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(3);

		Map<TeleportMethod, MethodAvailability> catalog =
			refreshedPlanningCopy(new TestPathfinderConfig(client, config)).getMethodAvailability();

		TeleportMethod varrockTeleport = catalog.keySet().stream()
			.filter(m -> m.getType() == TransportType.TELEPORTATION_SPELL
				&& m.getDisplayInfo() != null && m.getDisplayInfo().startsWith("Varrock Teleport"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Varrock Teleport method missing from the catalog"));
		assertEquals(MethodAvailability.MISSING_LEVEL, catalog.get(varrockTeleport));
	}

	@Test
	public void fairyRingsClassifyMissingQuestWithoutQuestProgress()
	{
		// The fairy-ring network is gated on Fairytale II progress (varbit, unstubbed = 0). The type-level
		// classifier must report MISSING_QUEST for every fairy-ring method.
		Map<TeleportMethod, MethodAvailability> catalog =
			refreshedPlanningCopy(new TestPathfinderConfig(client, config)).getMethodAvailability();

		boolean sawFairyRing = false;
		for (Map.Entry<TeleportMethod, MethodAvailability> entry : catalog.entrySet())
		{
			if (entry.getKey().getType() == TransportType.FAIRY_RING)
			{
				sawFairyRing = true;
				assertEquals("Fairy ring " + entry.getKey().label(),
					MethodAvailability.MISSING_QUEST, entry.getValue());
			}
		}
		assertTrue("Catalog should contain fairy-ring methods", sawFairyRing);
	}

	@Test
	public void catalogKeySetIsIdenticalAcrossModes()
	{
		// The catalog is the full method universe in every mode; only the availability markers differ.
		PathfinderConfig planning = new TestPathfinderConfig(client, config).copyForPlanning();
		planning.setPlanningMode(false);
		planning.setBypassItemPossession(false);
		planning.setConsiderBank(false);
		planning.refresh();
		Set<TeleportMethod> ownedCatalog = planning.getMethodCatalog();

		planning.setPlanningMode(true);
		planning.setBypassItemPossession(true);
		planning.refresh();
		Set<TeleportMethod> everythingCatalog = planning.getMethodCatalog();

		assertFalse("Catalog should not be empty", ownedCatalog.isEmpty());
		assertEquals("Owned and Everything modes must expose the same catalog",
			everythingCatalog, ownedCatalog);
	}

	@Test
	public void bestPrefersMoreAvailableStatus()
	{
		assertEquals(MethodAvailability.AVAILABLE, MethodAvailability.AVAILABLE.best(MethodAvailability.IN_BANK));
		assertEquals(MethodAvailability.AVAILABLE, MethodAvailability.MISSING_QUEST.best(MethodAvailability.AVAILABLE));
		assertEquals(MethodAvailability.IN_BANK, MethodAvailability.IN_BANK.best(MethodAvailability.MISSING_ITEM));
		assertEquals(MethodAvailability.LOCKED, MethodAvailability.LOCKED.best(null));
	}
}
