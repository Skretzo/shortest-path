package shortestpath.presentation;

import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Client;

import shortestpath.items.ItemStateService;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.poh.PohService;

/**
 * The presentation leaf: owns the shared edge-annotation and display
 * surface that the protocol serializers, the overlays, and the fairy-ring
 * panel all read — per-edge transport reconstruction, transport label
 * rewriting, POH exit display, the fairy-ring scroll block, and the
 * coarse display facades over the attached {@link PathfinderConfig}.
 * <p>
 * This package is a leaf: nothing here may reference the plugin type.
 * The engine config is late-bound by {@link #attach(PathfinderConfig)}
 * because the shell still constructs it in {@code startUp} and Guice
 * cannot inject it. A detached annotator — one built by
 * {@link #forTesting()} or never attached — answers every display read
 * with its empty/no-op arm rather than throwing.
 */
@Singleton
public class PathAnnotator
{
	private final Client client;
	private final PohService pohService;
	private final ItemStateService itemState;

	/**
	 * Late-bound by {@link #attach(PathfinderConfig)}: the shell still owns
	 * {@link PathfinderConfig} construction, so it cannot ride the
	 * constructor.
	 */
	private PathfinderConfig pathfinderConfig;

	@Inject
	public PathAnnotator(Client client, PohService pohService, ItemStateService itemState)
	{
		this.client = client;
		this.pohService = pohService;
		this.itemState = itemState;
	}

	private PathAnnotator()
	{
		this(null, null, null);
	}

	PathAnnotator(PohService pohService, ItemStateService itemState)
	{
		this(null, pohService, itemState);
	}

	/**
	 * A detached annotator for harnesses and tests: no client, no POH
	 * service, and no item-state service, so every dependent read takes its
	 * detached arm instead of throwing. {@link #attach(PathfinderConfig)}
	 * still wires the engine config for the reads that need it.
	 */
	public static PathAnnotator forTesting()
	{
		return new PathAnnotator();
	}

	/**
	 * Test seam for callers that exercise the POH display arms or the
	 * bank-pickup facade with real collaborators.
	 */
	public static PathAnnotator forTesting(PohService pohService, ItemStateService itemState)
	{
		return new PathAnnotator(pohService, itemState);
	}

	/**
	 * Hands over the engine config the shell constructed in {@code startUp}.
	 * Every config-backed read resolves through this attachment; reads made
	 * before it lands take the detached arm.
	 */
	public void attach(PathfinderConfig pathfinderConfig)
	{
		this.pathfinderConfig = pathfinderConfig;
	}
}
