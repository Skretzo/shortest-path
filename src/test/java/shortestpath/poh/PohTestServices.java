package shortestpath.poh;

import shortestpath.scheduler.RefreshCoordinator;

/**
 * Builds {@link PohService} instances detached from Guice for tests and
 * harnesses. The production instance is the injected singleton.
 */
public final class PohTestServices
{
	private PohTestServices()
	{
	}

	/**
	 * A detached service: no persistence, so every profile read and write
	 * takes its detached arm instead of touching storage, and no coordinator,
	 * so change declares no-op.
	 */
	public static PohService forTesting()
	{
		return new PohService(null, null, null);
	}

	/**
	 * For asserting change declares: the returned instance hands admitted facts
	 * to the given (typically mocked) coordinator while staying detached from
	 * persistence.
	 */
	public static PohService forTesting(RefreshCoordinator coordinator)
	{
		return new PohService(null, coordinator, null);
	}
}
