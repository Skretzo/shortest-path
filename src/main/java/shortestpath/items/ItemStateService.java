package shortestpath.items;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.settings.Effect;

/**
 * Owns the plugin's player item state observed from game events — the live
 * open-bank container and the container/varbit inputs that feed the
 * eligibility snapshot — and reports each admitted change to the shell as an
 * {@link ItemChange} fact. The service never touches the engine directly; the
 * shell maps the fact's declared effects to its follow-up actions.
 */
@Singleton
public class ItemStateService
{
	private final Client client;

	/**
	 * LIVE reference to the last-opened bank container — never copied or
	 * snapshotted, so it reads empty once the bank closes.
	 */
	private ItemContainer bank;

	@Inject
	public ItemStateService(Client client)
	{
		this.client = client;
	}

	private ItemStateService()
	{
		this(null);
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
	 * {@link #onContainerChanged} for the bank container id.
	 */
	public void noteBankContainer(ItemContainer container)
	{
		this.bank = container;
	}
}
