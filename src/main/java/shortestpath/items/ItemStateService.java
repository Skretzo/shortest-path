package shortestpath.items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.requirement.BankPickupRequirements.BankPickupResult;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.requirement.TransportEligibility;
import shortestpath.requirement.model.Unlock;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Effect;
import shortestpath.settings.TeleportationItem;

/**
 * Owns the plugin's player item state observed from game events — the live
 * open-bank container, the container/varbit inputs that feed the eligibility
 * snapshot, and the bank-pickup projection cache — and declares each admitted
 * change to the {@link RefreshCoordinator} as an {@link ItemChange} fact. The
 * service never writes back to the engine; the coordinator maps the fact's
 * declared effects to its follow-up actions, and {@link #getBankPickup} reads
 * the engine config it is handed without retaining it.
 */
@Singleton
public class ItemStateService
{
	private final Client client;
	private final RefreshCoordinator coordinator;

	/**
	 * LIVE reference to the last-opened bank container — never copied or
	 * snapshotted, so it reads empty once the bank closes.
	 */
	private ItemContainer bank;

	// Bank pickup cache — invalidated when path, bank, or inventory changes.
	private BankPickupResult bankPickupCache;
	private List<PathStep> bankPickupCachePath;
	private int bankPickupCacheIndex = -1;
	private boolean bankPickupDirty = true;

	@Inject
	public ItemStateService(Client client, RefreshCoordinator coordinator)
	{
		this.client = client;
		this.coordinator = coordinator;
	}

	private ItemStateService()
	{
		this(null, null);
	}

	/**
	 * Test/harness seam: returns an instance detached from Guice. The
	 * production instance is the injected singleton. Without a client the
	 * detached instance cannot resolve item names — pickup phrases from
	 * {@link #getBankPickup} degrade to "Unknown item" placeholders — and
	 * without a coordinator its change declares no-op.
	 */
	public static ItemStateService forTesting()
	{
		return new ItemStateService();
	}

	/**
	 * Test/harness seam for callers that exercise {@link #getBankPickup} and
	 * need real item names in the pickup phrases.
	 */
	public static ItemStateService forTesting(Client client)
	{
		return new ItemStateService(client, null);
	}

	/**
	 * Test seam for asserting change declares: the returned instance hands
	 * admitted facts to the given (typically mocked) coordinator.
	 */
	public static ItemStateService forTesting(RefreshCoordinator coordinator)
	{
		return new ItemStateService(null, coordinator);
	}

	/**
	 * Consumes an item-container event. The bank container id additionally
	 * updates the live bank reference; the tracked containers (bank,
	 * inventory, worn equipment) declare a fact carrying
	 * {@link Effect#ELIGIBILITY_STALE} to the coordinator. Any other
	 * container is not item state and declares nothing.
	 */
	public void onContainerChanged(int containerId, ItemContainer container)
	{
		if (containerId == InventoryID.BANK)
		{
			this.bank = container;
		}
		if (containerId == InventoryID.BANK || containerId == InventoryID.INV
			|| containerId == InventoryID.WORN)
		{
			bankPickupDirty = true;
			declare(new ItemChange("container:" + containerId, Set.of(Effect.ELIGIBILITY_STALE)));
		}
	}

	/**
	 * Consumes a varbit event. Rune pouch contents and the Lumbridge Elite
	 * diary feed the eligibility snapshot but change without firing a
	 * container event, so their varbits declare a fact carrying
	 * {@link Effect#ELIGIBILITY_STALE} to the coordinator. Any other varbit
	 * declares nothing.
	 */
	public void onVarbitChanged(int varbitId)
	{
		if (varbitId == VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE
			|| containsVarbit(OwnedItems.RUNE_POUCH_RUNE_VARBITS, varbitId)
			|| containsVarbit(OwnedItems.RUNE_POUCH_AMOUNT_VARBITS, varbitId))
		{
			bankPickupDirty = true;
			declare(new ItemChange("varbit:" + varbitId, Set.of(Effect.ELIGIBILITY_STALE)));
		}
	}

	/**
	 * Hands an admitted fact to the refresh coordinator. Detached
	 * {@code forTesting} instances carry no coordinator, so the declare
	 * no-ops there; a null fact declares nothing, mirroring the return-null
	 * convention the shell's mapper used to guard.
	 */
	private void declare(ItemChange change)
	{
		if (change != null && coordinator != null)
		{
			coordinator.itemsChanged(change);
		}
	}

	private static boolean containsVarbit(int[] varbits, int varbitId)
	{
		for (int id : varbits)
		{
			if (id == varbitId)
			{
				return true;
			}
		}
		return false;
	}

	public ItemContainer getBank()
	{
		return bank;
	}

	/**
	 * Harness/test seeding seam; production writes arrive through
	 * {@link #onContainerChanged} for the bank container id. Dirties the
	 * pickup cache like the event path does, so a re-seeded bank is never
	 * served a projection computed against the previous container.
	 */
	public void noteBankContainer(ItemContainer container)
	{
		this.bank = container;
		bankPickupDirty = true;
	}

	/**
	 * Marks the bank pickup projection stale; the next {@link #getBankPickup}
	 * recomputes. Admitted container/varbit events dirty it internally; the
	 * shell marks it when a pathfinding restart begins a new path.
	 */
	public void markBankPickupDirty()
	{
		bankPickupDirty = true;
	}

	/**
	 * Clears the session-scoped state — the live bank reference and the
	 * bank-pickup projection cache. The service is a singleton on the plugin
	 * injector, which RuneLite reuses across shutDown/startUp cycles, so the
	 * shell calls this on shutdown to restore the lifetime the bank field had
	 * when it lived on the per-session pathfinder config: nothing observed in
	 * one session may feed eligibility in the next. The pickup cache is left
	 * dirty so the next {@link #getBankPickup} recomputes.
	 */
	public void reset()
	{
		bank = null;
		bankPickupCache = null;
		bankPickupCachePath = null;
		bankPickupCacheIndex = -1;
		bankPickupDirty = true;
	}

	/**
	 * Returns the cached bank pickup result for the given path step, recomputing only when
	 * the path, bank contents, or player inventory has changed since the last call.
	 * On a detached {@link #forTesting()} instance there is no client to resolve item
	 * names with, so the phrases degrade to "Unknown item" placeholders rather than
	 * throwing.
	 */
	public BankPickupResult getBankPickup(List<PathStep> path, int pathIndex, PathfinderConfig pathfinderConfig)
	{
		Set<Integer> bankLocations = pathfinderConfig.getDestinations("bank");
		if (bank == null || bankLocations == null
				|| path == null || pathIndex < 0 || pathIndex >= path.size())
		{
			return null;
		}
		if (!bankPickupDirty && path == bankPickupCachePath && pathIndex == bankPickupCacheIndex)
		{
			return bankPickupCache;
		}
		bankPickupCachePath = path;
		bankPickupCacheIndex = pathIndex;
		bankPickupDirty = false;
		bankPickupCache = BankPickupResult.compute(
				client, bank, pathfinderConfig, bankLocations, path, pathIndex);
		return bankPickupCache;
	}

	/**
	 * Captures the client state both the pathfinding verdicts and the bank-pickup plans
	 * read: the carried pool (inventory + worn + rune pouch in hand), the bank-path pool
	 * (which adds the bank contents when bank paths are enabled), the bank contents
	 * themselves, the runes inside a banked rune pouch, the fairy-ring staff gate and
	 * the currency threshold. Shared by
	 * {@link shortestpath.requirement.RequirementContext#capture RequirementContext.capture}
	 * and the lazy {@code PathfinderConfig#getEligibility()} rebuild so exactly one
	 * collection path exists.
	 */
	public static TransportEligibility collectEligibility(
		PlayerStateSource source,
		ItemContainer bank,
		TeleportationItem teleportationItemSetting,
		int currencyThreshold,
		boolean includeBankPath,
		Set<Unlock> unlocks)
	{
		Map<Integer, Integer> carriedItems = collectItems(source, bank,
			teleportationItemSetting, true, true, false, true);
		Map<Integer, Integer> bankPathItems = includeBankPath
			? collectItems(source, bank, teleportationItemSetting, true, true, true, true)
			: carriedItems;
		Map<Integer, Integer> bankHas = new HashMap<>();
		OwnedItems.addContainer(bankHas, bank);
		int bankPouchId = -1;
		for (int pouchId : OwnedItems.RUNE_POUCHES)
		{
			if (bankHas.containsKey(pouchId))
			{
				bankPouchId = pouchId;
				break;
			}
		}
		Map<Integer, Integer> bankPouchRunes = bankPouchId == -1
			? Map.of()
			: source.runePouchContents();
		boolean fairyRingStaffRequired =
			source.varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1;
		return new TransportEligibility(carriedItems, bankPathItems, bankHas, bankPouchId, bankPouchRunes,
			fairyRingStaffRequired, teleportationItemSetting, currencyThreshold,
			unlocks);
	}

	/**
	 * Item id to quantity over the selected containers, summed across containers.
	 */
	private static Map<Integer, Integer> collectItems(
		PlayerStateSource source,
		ItemContainer bank,
		TeleportationItem teleportationItemSetting,
		boolean checkInventory,
		boolean checkEquipment,
		boolean checkBank,
		boolean checkRunePouch)
	{
		Map<Integer, Integer> itemsAndQuantities = new HashMap<>(28 + 11 + 500);

		if (checkInventory)
		{
			OwnedItems.addContainer(itemsAndQuantities, source.itemContainer(InventoryID.INV));
		}

		if (checkEquipment)
		{
			OwnedItems.addContainer(itemsAndQuantities, source.itemContainer(InventoryID.WORN));
		}

		if (checkBank
			&& (TeleportationItem.INVENTORY_AND_BANK.equals(teleportationItemSetting)
				|| TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE.equals(teleportationItemSetting)))
		{
			OwnedItems.addContainer(itemsAndQuantities, bank);
		}

		if (checkRunePouch)
		{
			source.addRunePouchContents(itemsAndQuantities);
		}

		return itemsAndQuantities;
	}
}
