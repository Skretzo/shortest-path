package shortestpath.spirittree;

import shortestpath.scheduler.RefreshCoordinator;

/**
 * Builds {@link SpiritTreeService} instances detached from Guice for tests and
 * harnesses. The production instance is the injected singleton.
 */
public final class SpiritTreeTestServices
{
	private SpiritTreeTestServices()
	{
	}

	/**
	 * A detached service: no persistence and no client, so
	 * {@link SpiritTreeService#refreshAvailability} takes its detached arm and
	 * merges live samples into the published set instead of recording
	 * observations. No coordinator either, so change declares no-op.
	 */
	public static SpiritTreeService forTesting()
	{
		return new SpiritTreeService(null, null, null, null);
	}

	/**
	 * For asserting change declares: the returned instance hands admitted facts
	 * to the given (typically mocked) coordinator while staying detached from
	 * persistence and the client.
	 */
	public static SpiritTreeService forTesting(RefreshCoordinator coordinator)
	{
		return new SpiritTreeService(null, null, coordinator);
	}
}
