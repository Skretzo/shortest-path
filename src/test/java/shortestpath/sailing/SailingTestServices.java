package shortestpath.sailing;

import net.runelite.api.Client;
import shortestpath.scheduler.RefreshCoordinator;

/**
 * Builds {@link SailingService} instances detached from Guice for tests and
 * harnesses. The production instance is the injected singleton.
 */
public final class SailingTestServices
{
	private SailingTestServices()
	{
	}

	/**
	 * An instance with no client and no coordinator. Without a client it
	 * resolves no entity state, so a refresh captures the varbit side of the
	 * handed source alone.
	 */
	public static SailingService forTesting()
	{
		return new SailingService(null, null);
	}

	/**
	 * For callers that exercise the entity side of the capture: the handed
	 * client drives the boat lookup while the instance stays detached from the
	 * coordinator channel.
	 */
	public static SailingService forTesting(Client client)
	{
		return new SailingService(client, null);
	}

	/**
	 * For asserting change declares: the returned instance hands minted facts
	 * to the given (typically mocked) {@link RefreshCoordinator}.
	 */
	public static SailingService forTesting(RefreshCoordinator coordinator)
	{
		return new SailingService(null, coordinator);
	}

	/**
	 * For callers that need both sides wired: entity capture through the
	 * handed client and fact declares to the handed {@link RefreshCoordinator}.
	 */
	public static SailingService forTesting(Client client, RefreshCoordinator coordinator)
	{
		return new SailingService(client, coordinator);
	}
}
