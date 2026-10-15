package shortestpath.items;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.requirement.TransportEligibility;
import shortestpath.requirement.model.Unlock;
import shortestpath.settings.Effect;
import shortestpath.settings.TeleportationItem;

/**
 * Owns the plugin's player item state observed from game events — the live
 * open-bank container and the container/varbit inputs that feed the
 * eligibility snapshot — and reports each admitted change to the shell as an
 * {@link ItemChange} fact. The service never writes back to the engine; the
 * shell maps the fact's declared effects to its follow-up actions, and
 * downstream projections key their caches on {@link #itemStateRevision()}
 * rather than pulling path types in here.
 */
@Singleton
public class ItemStateService
{
	/**
	 * LIVE reference to the last-opened bank container — never copied or
	 * snapshotted, so it reads empty once the bank closes.
	 */
	private ItemContainer bank;

	// Bumped by every admitted item-state change; readers that project this
	// state (e.g. the bank-pickup projection) key their caches on it instead
	// of this service observing their invalidation needs.
	private int itemStateRevision;

	@Inject
	public ItemStateService()
	{
	}

	/**
	 * Test/harness seam: returns an instance detached from Guice. The
	 * production instance is the injected singleton.
	 */
	public static ItemStateService forTesting()
	{
		return new ItemStateService();
	}

	/**
	 * Consumes an item-container event. The bank container id additionally
	 * updates the live bank reference; the tracked containers (bank,
	 * inventory, worn equipment) return a fact declaring
	 * {@link Effect#ELIGIBILITY_STALE}. Any other container is not item
	 * state and returns {@code null}.
	 */
	public ItemChange onContainerChanged(int containerId, ItemContainer container)
	{
		if (containerId == InventoryID.BANK)
		{
			this.bank = container;
		}
		if (containerId == InventoryID.BANK || containerId == InventoryID.INV
			|| containerId == InventoryID.WORN)
		{
			itemStateRevision++;
			return new ItemChange("container:" + containerId, Set.of(Effect.ELIGIBILITY_STALE));
		}
		return null;
	}

	/**
	 * Consumes a varbit event. Rune pouch contents and the Lumbridge Elite
	 * diary feed the eligibility snapshot but change without firing a
	 * container event, so their varbits return a fact declaring
	 * {@link Effect#ELIGIBILITY_STALE}. Any other varbit returns
	 * {@code null}.
	 */
	public ItemChange onVarbitChanged(int varbitId)
	{
		if (varbitId == VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE
			|| containsVarbit(OwnedItems.RUNE_POUCH_RUNE_VARBITS, varbitId)
			|| containsVarbit(OwnedItems.RUNE_POUCH_AMOUNT_VARBITS, varbitId))
		{
			itemStateRevision++;
			return new ItemChange("varbit:" + varbitId, Set.of(Effect.ELIGIBILITY_STALE));
		}
		return null;
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
	 * {@link #onContainerChanged} for the bank container id. Bumps
	 * {@link #itemStateRevision()} like the event path does, so a re-seeded
	 * bank is never served by a projection computed against the previous
	 * container.
	 */
	public void noteBankContainer(ItemContainer container)
	{
		this.bank = container;
		itemStateRevision++;
	}

	/**
	 * The item-state epoch: bumped by every admitted container/varbit change,
	 * by {@link #noteBankContainer}, and by {@link #reset}. Readers that cache
	 * a projection over this state must recompute when it moves.
	 */
	public int itemStateRevision()
	{
		return itemStateRevision;
	}

	/**
	 * Clears the session-scoped state — the live bank reference. The service
	 * is a singleton on the plugin injector, which RuneLite reuses across
	 * shutDown/startUp cycles, so the shell calls this on shutdown to restore
	 * the lifetime the bank field had when it lived on the per-session
	 * pathfinder config: nothing observed in one session may feed eligibility
	 * in the next. The revision bump keeps downstream caches from serving a
	 * projection computed against the cleared state.
	 */
	public void reset()
	{
		bank = null;
		itemStateRevision++;
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
			unlocks, bank != null);
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
