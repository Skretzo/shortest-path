package shortestpath.presentation;

import net.runelite.api.Client;
import shortestpath.items.ItemStateService;
import shortestpath.poh.PohService;

/**
 * Builds {@link PathAnnotator} instances detached from Guice for tests and
 * harnesses. The production instance is the injected singleton.
 */
public final class PathAnnotatorTestServices
{
	private PathAnnotatorTestServices()
	{
	}

	/**
	 * A detached annotator: no client, no POH service, and no item-state
	 * service, so every dependent read takes its detached arm instead of
	 * throwing. {@link PathAnnotator#attach} still wires the engine config for
	 * the reads that need it.
	 */
	public static PathAnnotator forTesting()
	{
		return new PathAnnotator(null, null, null);
	}

	/**
	 * For callers that exercise the POH display arms or the bank-pickup facade
	 * with real collaborators.
	 */
	public static PathAnnotator forTesting(PohService pohService, ItemStateService itemState)
	{
		return new PathAnnotator(pohService, itemState);
	}

	/**
	 * For callers that exercise the missing-items label, which resolves item
	 * names through the client.
	 */
	public static PathAnnotator forTesting(Client client, PohService pohService, ItemStateService itemState)
	{
		return new PathAnnotator(client, pohService, itemState);
	}
}
