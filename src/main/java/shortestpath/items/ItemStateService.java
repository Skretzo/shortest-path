package shortestpath.items;

import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;

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

	public ItemChange onContainerChanged(int containerId, ItemContainer container)
	{
		return null;
	}

	public ItemChange onVarbitChanged(int varbitId)
	{
		return null;
	}

	public ItemContainer bank()
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
