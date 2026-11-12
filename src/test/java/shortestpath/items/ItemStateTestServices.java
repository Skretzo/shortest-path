package shortestpath.items;

import net.runelite.client.config.ConfigManager;
import shortestpath.scheduler.RefreshCoordinator;

/**
 * Builds {@link ItemStateService} instances detached from Guice for tests and
 * harnesses. The production instance is the injected singleton.
 */
public final class ItemStateTestServices
{
	private ItemStateTestServices()
	{
	}

	/**
	 * A detached instance: no coordinator, so its change declares no-op, and no
	 * config manager, so the last-seen snapshot lives in memory only.
	 */
	public static ItemStateService forTesting()
	{
		return new ItemStateService(null, null);
	}

	/**
	 * For asserting change declares: the returned instance hands admitted facts
	 * to the given (typically mocked) coordinator while staying detached from
	 * persistence.
	 */
	public static ItemStateService forTesting(RefreshCoordinator coordinator)
	{
		return new ItemStateService(coordinator, null);
	}

	/**
	 * For asserting profile persistence: the returned instance reads and writes
	 * the last-seen bank snapshot through the given (typically mocked) config
	 * manager.
	 */
	public static ItemStateService forTesting(RefreshCoordinator coordinator,
		ConfigManager configManager)
	{
		return new ItemStateService(coordinator, configManager);
	}
}
