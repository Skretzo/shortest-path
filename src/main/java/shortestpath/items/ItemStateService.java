package shortestpath.items;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;

import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Effect;
import shortestpath.settings.Settings;

/**
 * Owns the plugin's player item state observed from game events — the live
 * open-bank container and the container/varbit inputs that feed the
 * eligibility snapshot — and declares each admitted change to the
 * {@link RefreshCoordinator} as an {@link ItemChange} fact. The service
 * never writes back to the engine; the coordinator maps the fact's declared
 * effects to its follow-up actions, and downstream projections key their
 * caches on {@link #itemStateRevision()} rather than pulling path types in
 * here.
 * <p>
 * Two bank concepts live here and must never cross. {@link #getBank()} is
 * the session-scoped LIVE eligibility reference: a handle on the last-opened
 * bank container that reads empty once the bank closes and is dropped by
 * {@link #reset()} so nothing observed in one session feeds eligibility in
 * the next. {@link #lastSeenBank()} is a per-account <i>last-seen</i> bank
 * contents record for the panel's Owned filter — explicitly display-only,
 * never consulted by eligibility, persisted through RSProfile so it survives
 * sessions and plugin restarts by design; {@link #reset()} does not clear it.
 * </p>
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ItemStateService
{
	private final RefreshCoordinator coordinator;
	private final ConfigManager configManager;

	/**
	 * LIVE reference to the last-opened bank container — never copied or
	 * snapshotted, so it reads empty once the bank closes.
	 */
	@Getter private ItemContainer bank;

	/**
	 * The per-account last-seen bank contents — an immutable id-to-quantity
	 * map remembered across sessions for the panel's Owned filter. Display
	 * state only: eligibility never reads it, and {@link #reset()} leaves it
	 * intact.
	 */
	private volatile Map<Integer, Integer> lastSeenBank = Map.of();

	// The serialized form last written to (or read back from) the profile;
	// identical re-persists are skipped on it, mirroring the PohService
	// lastSaved guard so steady-state bank ticks never churn the config file.
	private String lastSeenBankSaved = "";

	static final String LAST_SEEN_BANK_KEY = "lastSeenBankItems";

	// Bumped by every admitted item-state change; readers that project this
	// state (e.g. the bank-pickup projection) key their caches on it instead
	// of this service observing their invalidation needs.
	@Getter @Accessors(fluent = true) private int itemStateRevision;

	private ItemStateService()
	{
		this(null, null);
	}

	/**
	 * Test/harness seam: returns an instance detached from Guice. The
	 * production instance is the injected singleton; a detached instance
	 * carries no coordinator, so its change declares no-op, and no config
	 * manager, so the last-seen snapshot lives in memory only.
	 */
	public static ItemStateService forTesting()
	{
		return new ItemStateService();
	}

	/**
	 * Test seam for asserting change declares: the returned instance hands
	 * admitted facts to the given (typically mocked) coordinator while
	 * staying detached from persistence.
	 */
	public static ItemStateService forTesting(RefreshCoordinator coordinator)
	{
		return new ItemStateService(coordinator, null);
	}

	/**
	 * Test seam for asserting profile persistence: the returned instance
	 * reads and writes the last-seen bank snapshot through the given
	 * (typically mocked) config manager.
	 */
	public static ItemStateService forTesting(RefreshCoordinator coordinator,
		ConfigManager configManager)
	{
		return new ItemStateService(coordinator, configManager);
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
			itemStateRevision++;
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
			itemStateRevision++;
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
	 * Records the bank's contents for the display-side last-seen snapshot.
	 * The shell calls this on the client thread with the bank-open verdict
	 * already computed — the widget read behind it is a client-thread call
	 * the service cannot make without a client. A {@code false} verdict
	 * means the event is the post-close drain: the container reads empty
	 * because the bank is gone, not because it was emptied, so it is ignored
	 * and the previous snapshot survives. When the bank is open the
	 * container's current contents become the snapshot — a legitimately
	 * emptied bank persists as an empty snapshot — and only a change
	 * repersists.
	 */
	public void noteLastSeenBank(ItemContainer container, boolean bankOpen)
	{
		if (!bankOpen)
		{
			return;
		}
		Map<Integer, Integer> snapshot = new HashMap<>();
		OwnedItems.addContainer(snapshot, container);
		Map<Integer, Integer> immutable = Collections.unmodifiableMap(snapshot);
		if (!immutable.equals(lastSeenBank))
		{
			lastSeenBank = immutable;
			persistLastSeenBank();
		}
	}

	/**
	 * The per-account last-seen bank contents — an immutable id-to-quantity
	 * map remembered across sessions for the panel's Owned filter. Display
	 * state only: eligibility never reads it, and {@link #reset()} leaves it
	 * intact.
	 */
	public Map<Integer, Integer> lastSeenBank()
	{
		return lastSeenBank;
	}

	/**
	 * Loads the persisted last-seen bank snapshot for the active RuneScape
	 * profile — RSProfile keying gives per-account isolation for free.
	 * Hand-edited or stale profile data can never throw here: malformed
	 * entries are skipped with a warn and a missing or blank value loads as
	 * no snapshot. Detached instances (no config manager) clear to empty and
	 * return.
	 */
	public void loadFromProfile()
	{
		lastSeenBank = Map.of();
		lastSeenBankSaved = "";
		if (configManager == null)
		{
			return;
		}
		String stored = configManager.getRSProfileConfiguration(
			Settings.CONFIG_GROUP, LAST_SEEN_BANK_KEY);
		Map<Integer, Integer> loaded = new HashMap<>();
		if (stored != null && !stored.isBlank())
		{
			for (String rawEntry : stored.split(","))
			{
				String entry = rawEntry.trim();
				int split = entry.indexOf(':');
				if (split <= 0 || split == entry.length() - 1)
				{
					log.warn("ignoring malformed {} entry '{}'", LAST_SEEN_BANK_KEY, entry);
					continue;
				}
				try
				{
					int id = Integer.parseInt(entry.substring(0, split));
					int quantity = Integer.parseInt(entry.substring(split + 1));
					if (id < 0 || quantity <= 0)
					{
						log.warn("ignoring malformed {} entry '{}'", LAST_SEEN_BANK_KEY, entry);
						continue;
					}
					loaded.merge(id, quantity, Integer::sum);
				}
				catch (NumberFormatException e)
				{
					log.warn("ignoring malformed {} entry '{}'", LAST_SEEN_BANK_KEY, entry);
				}
			}
		}
		lastSeenBank = Collections.unmodifiableMap(loaded);
		lastSeenBankSaved = stored == null ? "" : stored;
	}

	/**
	 * Writes the snapshot to the active RuneScape profile as an
	 * {@code id:quantity} CSV sorted by item id. An empty snapshot unsets
	 * the key so "emptied the bank" and "never opened the bank" stay
	 * distinguishable on the next load from "nothing persisted". Identical
	 * serialized forms are skipped on {@link #lastSeenBankSaved}; detached
	 * instances persist nothing.
	 */
	private void persistLastSeenBank()
	{
		if (configManager == null)
		{
			return;
		}
		StringBuilder serialized = new StringBuilder();
		for (Map.Entry<Integer, Integer> entry : new TreeMap<>(lastSeenBank).entrySet())
		{
			if (serialized.length() > 0)
			{
				serialized.append(',');
			}
			serialized.append(entry.getKey().intValue())
				.append(':').append(entry.getValue().intValue());
		}
		String value = serialized.toString();
		if (value.equals(lastSeenBankSaved))
		{
			return;
		}
		lastSeenBankSaved = value;
		if (value.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(
				Settings.CONFIG_GROUP, LAST_SEEN_BANK_KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(
				Settings.CONFIG_GROUP, LAST_SEEN_BANK_KEY, value);
		}
	}

	/**
	 * The item-state epoch: bumped by every admitted container/varbit change,
	 * by {@link #noteBankContainer}, and by {@link #reset}. Readers that cache
	 * a projection over this state must recompute when it moves.
	 */


	/**
	 * Clears the session-scoped state — the live bank reference. The service
	 * is a singleton on the plugin injector, which RuneLite reuses across
	 * shutDown/startUp cycles, so the shell calls this on shutdown to restore
	 * the lifetime the bank field had when it lived on the per-session
	 * pathfinder config: nothing observed in one session may feed eligibility
	 * in the next. The revision bump keeps downstream caches from serving a
	 * projection computed against the cleared state. The last-seen snapshot
	 * is deliberately untouched — surviving sessions is its whole point.
	 */
	public void reset()
	{
		bank = null;
		itemStateRevision++;
	}
}
