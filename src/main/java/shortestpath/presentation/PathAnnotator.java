package shortestpath.presentation;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import net.runelite.api.Client;

import shortestpath.WorldPointUtil;
import shortestpath.items.ItemStateService;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.NodeGraph;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.poh.PohService;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

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

	/**
	 * This reconstructs the candidate transports for a rendered path edge from the current path state.
	 * <p>
	 * The important detail is that path display logic is edge-based, not node-based:
	 * - origin position comes from currentStep
	 * - destination position comes from nextStep
	 * - the applicable transport set may depend on whether the edge transitions into banked state
	 * <p>
	 * That last point is the awkward one. Banking is not represented as its own explicit path edge;
	 * instead the "becomes banked" state change is conflated into the movement/transport edge that
	 * reaches the banked destination step. As a result, callers cannot safely resolve transports from
	 * a single PathStep alone: using only currentStep can miss bank-gated transports, while using only
	 * nextStep loses the origin tile of the edge. This helper therefore takes both steps and resolves
	 * transports for the edge between them.
	 * <p>
	 * When the destination step carries the transport the search actually chose (steps built by
	 * {@link NodeGraph#getPathSteps} do), that transport is the answer and this function returns
	 * it directly as a singleton — reconstruction is only a fallback for identity-less steps
	 * (e.g. steps built via the two-argument {@link PathStep} constructor). The destination-based
	 * fallback remains inherently ambiguous when multiple valid transports share the same
	 * origin/destination pair under the same edge state.
	 * <p>
	 * Note that the fallback also performs filtering by the transport target, so callers of this
	 * function can directly iterate over the returned transports.
	 */
	public Set<Transport> transportsForEdge(PathStep currentStep, PathStep nextStep)
	{
		if (currentStep == null || nextStep == null)
		{
			return Set.of();
		}
		// The step knows which transport produced the edge into it: no reconstruction needed.
		if (nextStep.getTransport() != null)
		{
			return Set.of(nextStep.getTransport());
		}
		if (pathfinderConfig == null)
		{
			// Detached (un-attached or forTesting) annotator — no engine config
			// to reconstruct the edge against.
			return Set.of();
		}
		BankVisitState bankVisitState =
			currentStep.getBankVisitState() == BankVisitState.BANKED
				|| nextStep.getBankVisitState() == BankVisitState.BANKED
				? BankVisitState.BANKED : BankVisitState.CARRIED;
		// Get the transports which start from the position of starting step.
		Set<Transport> stepTransports = new HashSet<>(Arrays.asList(
			pathfinderConfig.getTransportsPacked(bankVisitState)
				.getOrDefault(currentStep.getPackedPosition(), TransportAvailability.EMPTY_TRANSPORTS)));
		// Add the teleports, which might be used from anywhere.
		stepTransports.addAll(Arrays.asList(pathfinderConfig.getUsableTeleports(bankVisitState)));
		// Remove transports which do not target the correct location.
		stepTransports.removeIf(transport -> transport.getDestination() != nextStep.getPackedPosition());
		// Remove teleports that share destinations with a local transport type on this edge.
		// For example, if the path uses a QUETZAL (local) transport, suppress QUETZAL_WHISTLE hints.
		// Also suppress them when the edge distance is within the shared type's radius threshold,
		// which occurs when the path is simply walking to a landing site (not teleporting to it).
		Set<TransportType> localTypes = EnumSet.noneOf(TransportType.class);
		for (Transport t : stepTransports)
		{
			if (t.getOrigin() != Transport.UNDEFINED_ORIGIN && t.getType() != null)
			{
				localTypes.add(t.getType());
			}
		}
		int edgeDistance = WorldPointUtil.distanceBetween2D(currentStep.getPackedPosition(), nextStep.getPackedPosition());
		stepTransports.removeIf(t ->
		{
			if (t.getOrigin() != Transport.UNDEFINED_ORIGIN || t.getType() == null)
			{
				return false; // keep local transports
			}
			if (edgeDistance <= 1)
			{
				// A single-tile edge is a walked step, so the path did not teleport
				// here: the destination tile is just a teleport landing site the
				// path passes through or ends at, and the teleport hint is noise.
				return true;
			}
			TransportType sharedType = t.getType().sharesDestinationsWith();
			if (sharedType == null)
			{
				return false; // not a shared-destination teleport, keep it
			}
			// Suppress if a local transport of the shared type is present on this edge (Issue 1),
			// or if the edge is within the shared type's radius threshold, meaning the path is
			// walking to the landing site rather than teleporting there (Issue 2).
			return localTypes.contains(sharedType)
				|| (sharedType.getRadiusThreshold() != null && edgeDistance <= sharedType.getRadiusThreshold());
		});
		return stepTransports;
	}

	public PathStep nextPathStep(List<PathStep> path, int index)
	{
		if (path == null || index < 0 || index + 1 >= path.size())
		{
			return null;
		}
		return path.get(index + 1);
	}

	public String formatTransportDisplay(Transport transport)
	{
		String info = transport.getDisplayInfo();
		if (info == null || info.isEmpty())
		{
			return info;
		}
		if (TransportType.TELEPORTATION_PORTAL_POH.equals(transport.getType()))
		{
			return pohService == null ? info : pohService.apply(info);
		}
		return info;
	}

	/**
	 * Checks if the destination is inside POH and looks ahead in the path to find the exit transport.
	 * If the immediate exit leads to a fairy ring or other notable transport shortly after,
	 * that information is included instead.
	 *
	 * @param destination  The destination point to check
	 * @param path         The full path
	 * @param currentIndex The current index in the path
	 * @return The display info of the POH exit transport, or null if not applicable
	 */
	public String getPohExitInfo(int destination, List<PathStep> path, int currentIndex)
	{
		if (path == null || currentIndex < 0)
		{
			return null;
		}

		int destX = WorldPointUtil.unpackWorldX(destination);
		int destY = WorldPointUtil.unpackWorldY(destination);

		// Check if destination is inside POH
		if (!PohService.isInsidePoh(destX, destY))
		{
			return null;
		}

		String immediateExitInfo = null;

		// Look ahead in the path to find the next transport that exits POH
		for (int i = currentIndex + 1; i < path.size() - 1; i++)
		{
			int stepLocation = path.get(i).getPackedPosition();
			int nextLocation = path.get(i + 1).getPackedPosition();

			int stepX = WorldPointUtil.unpackWorldX(stepLocation);
			int stepY = WorldPointUtil.unpackWorldY(stepLocation);
			int nextX = WorldPointUtil.unpackWorldX(nextLocation);
			int nextY = WorldPointUtil.unpackWorldY(nextLocation);

			// Check if this step is inside POH but next step is outside (exit transport)
			boolean stepInsidePoh = PohService.isInsidePoh(stepX, stepY);
			boolean nextInsidePoh = PohService.isInsidePoh(nextX, nextY);

			if (stepInsidePoh && !nextInsidePoh)
			{
				// Found the exit transport - get its display info using bank-aware lookup
				PathStep currentStep = path.get(i);
				PathStep nextStep = path.get(i + 1);
				for (Transport transport : transportsForEdge(currentStep, nextStep))
				{
					String exitInfo = formatTransportDisplay(transport);
					if (exitInfo != null && !exitInfo.isEmpty())
					{
						TransportType exitType = transport.getType();
						if (TransportType.TELEPORTATION_BOX.equals(exitType))
						{
							String objInfo = transport.getObjectInfo();
							if (objInfo != null && objInfo.contains("Amulet of Glory"))
							{
								immediateExitInfo = "Mounted Glory: " + exitInfo;
							}
							else if (objInfo != null && objInfo.contains("Mythical cape"))
							{
								immediateExitInfo = "Mythical Cape: " + exitInfo;
							}
							else if (objInfo != null && objInfo.contains("Xeric's Talisman"))
							{
								immediateExitInfo = "Xeric's Talisman: " + exitInfo;
							}
							else if (objInfo != null && objInfo.contains("Digsite"))
							{
								immediateExitInfo = "Digsite Pendant: " + exitInfo;
							}
							else
							{
								immediateExitInfo = "Jewelry Box: " + exitInfo;
							}
						}
						else if (TransportType.TELEPORTATION_PORTAL_POH.equals(exitType))
						{
							immediateExitInfo = "Nexus: " + exitInfo;
						}
						else if (TransportType.FAIRY_RING.equals(exitType))
						{
							immediateExitInfo = "Fairy Ring " + exitInfo;
						}
						else if (TransportType.SPIRIT_TREE.equals(exitType))
						{
							immediateExitInfo = "Spirit Tree: " + exitInfo;
						}
						else if (TransportType.WILDERNESS_OBELISK.equals(exitType))
						{
							immediateExitInfo = "Obelisk: " + exitInfo;
						}
						else
						{
							immediateExitInfo = exitInfo;
						}
					}
					break;
				}
				break;
			}

			// If we've left POH without finding a transport, stop looking
			if (!stepInsidePoh)
			{
				break;
			}
		}

		return immediateExitInfo;
	}
}
