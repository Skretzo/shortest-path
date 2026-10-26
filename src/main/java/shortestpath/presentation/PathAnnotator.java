package shortestpath.presentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.RequiredArgsConstructor;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.items.ItemStateService;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.Arrival;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.NodeGraph;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.poh.PohService;
import shortestpath.presentation.BankPickupRequirements.BankPickupResult;
import shortestpath.requirement.TransportEligibility;
import shortestpath.requirement.model.ItemRequirement;
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
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PathAnnotator
{
	/**
	 * Ordered {@code objInfo.contains} matcher → label-prefix pairs for the
	 * teleportation-box arm of {@link #getPohExitInfo}; the first match wins,
	 * and an unmatched (or absent) object info falls back to
	 * {@link #OBJINFO_FALLBACK_LABEL}. Package-visible so the label table is
	 * test-pinned.
	 */
	static final List<List<String>> OBJINFO_LABELS = List.of(
		List.of("Amulet of Glory", "Mounted Glory: "),
		List.of("Mythical cape", "Mythical Cape: "),
		List.of("Xeric's Talisman", "Xeric's Talisman: "),
		List.of("Digsite", "Digsite Pendant: "));
	static final String OBJINFO_FALLBACK_LABEL = "Jewelry Box: ";

	/**
	 * Label prefix per exit-transport type for {@link #getPohExitInfo}; a
	 * type absent from the map (including a null type) passes its display
	 * info through unprefixed, and the teleportation box takes the
	 * {@link #OBJINFO_LABELS} arm instead. The fairy-ring entry's missing
	 * colon is upstream display text, not a typo.
	 */
	static final Map<TransportType, String> EXIT_PREFIX = Map.of(
		TransportType.TELEPORTATION_PORTAL_POH, "Nexus: ",
		TransportType.FAIRY_RING, "Fairy Ring ",
		TransportType.SPIRIT_TREE, "Spirit Tree: ",
		TransportType.WILDERNESS_OBELISK, "Obelisk: ");

	private final Client client;
	private final PohService pohService;
	private final ItemStateService itemState;

	/**
	 * Late-bound by {@link #attach(PathfinderConfig)}: the shell still owns
	 * {@link PathfinderConfig} construction, so it cannot ride the
	 * constructor.
	 */
	private PathfinderConfig pathfinderConfig;

	private boolean fairyRingPanelOpen = false;
	// The missing-items label of missingItemsSearch, built on the client thread when first drawn.
	private ActiveSearch missingItemsSearch;
	private String missingItemsText;

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
	 * Test seam for callers that exercise the missing-items label, which
	 * resolves item names through the client.
	 */
	public static PathAnnotator forTesting(Client client, PohService pohService, ItemStateService itemState)
	{
		return new PathAnnotator(client, pohService, itemState);
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
	 * The label naming the items the displayed route's target needs but the player was not found to
	 * hold, or {@code null} when nothing is missing. Built once per search, on the client thread.
	 */
	public String getMissingTargetItemsText(ActiveSearch search)
	{
		if (search != missingItemsSearch)
		{
			missingItemsSearch = search;
			missingItemsText = null;
		}
		PathfinderResult result = search == null ? null : search.getResult();
		if (missingItemsText == null && result != null)
		{
			Arrival arrival = result.getArrival();
			List<ItemRequirement> missing = arrival == null ? List.of() : arrival.getMissingItems();
			missingItemsText = missing.isEmpty() ? "" : missingItemsLabel(missing);
		}
		return missingItemsText == null || missingItemsText.isEmpty() ? null : missingItemsText;
	}

	/** "Not found in inventory or bank: Spade, Rope or Knife", one entry per missing item group. */
	private String missingItemsLabel(List<ItemRequirement> missing)
	{
		List<String> groups = new ArrayList<>(missing.size());
		for (ItemRequirement group : missing)
		{
			List<String> names = new ArrayList<>();
			for (ItemRequirement.Branch branch : group.getBranches())
			{
				for (int id : branch.getItemIds())
				{
					// Requirement ids come from other plugins' messages; an invalid or renamed
					// id may resolve to no name rather than crash every rendered frame.
					ItemComposition def = client.getItemDefinition(id);
					String name = def == null ? null : def.getName();
					names.add(name == null || name.isEmpty() || "null".equals(name) ? "Item " + id : name);
				}
			}
			groups.add(String.join(" or ", names));
		}
		return "Not found in inventory or bank: " + String.join(", ", groups)
			+ (pathfinderConfig.getHeldItems().isBankSeen() ? "" : " (bank not checked)");
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
							String label = OBJINFO_FALLBACK_LABEL;
							if (objInfo != null)
							{
								for (List<String> pair : OBJINFO_LABELS)
								{
									if (objInfo.contains(pair.get(0)))
									{
										label = pair.get(1);
										break;
									}
								}
							}
							immediateExitInfo = label + exitInfo;
						}
						else
						{
							String prefix = exitType == null ? null : EXIT_PREFIX.get(exitType);
							immediateExitInfo = prefix == null ? exitInfo : prefix + exitInfo;
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

	public CollisionMap getMap()
	{
		return pathfinderConfig == null ? null : pathfinderConfig.getMap();
	}

	/**
	 * WARNING: This is a legacy wrapper for coarse display-oriented callers only.
	 * <p>
	 * It collapses banked/unbanked transport availability into a single view via
	 * PathfinderConfig.getTransports(), which is not valid for path-state-sensitive logic.
	 * <p>
	 * Do not use this for reasoning about which transports are available at a specific
	 * step of a path. Use PathfinderConfig.getTransportAvailability(BankVisitState) and the
	 * path's PathStep state instead.
	 */
	public PrimitiveIntHashMap<Transport[]> getTransports()
	{
		return pathfinderConfig == null ? null : pathfinderConfig.getTransports();
	}

	/**
	 * Every loaded transport grouped by origin for display, including transports the
	 * player cannot currently use. Same layout as {@link #getTransports()}.
	 */
	public PrimitiveIntHashMap<Transport[]> getAllDisplayTransports()
	{
		return pathfinderConfig == null ? null : pathfinderConfig.getAllDisplayTransports();
	}

	public TransportEligibility getEligibility()
	{
		return pathfinderConfig == null ? null : pathfinderConfig.getEligibility();
	}

	/**
	 * The bank-pickup projection for the active path — owned and cached by
	 * the engine config; this facade bridges the attached config in.
	 */
	public BankPickupResult getBankPickup(List<PathStep> path, int pathIndex, ActiveSearch search)
	{
		return pathfinderConfig == null
			? null
			: pathfinderConfig.getBankPickup(path, pathIndex, search);
	}

	public Set<Integer> getDestinations(String destinationType)
	{
		return pathfinderConfig == null ? null : pathfinderConfig.getDestinations(destinationType);
	}

	public boolean hasDestination(String destinationType)
	{
		return pathfinderConfig != null && pathfinderConfig.hasDestination(destinationType);
	}

	public boolean isBankOpen()
	{
		return itemState != null && itemState.getBank() != null;
	}

	/**
	 * The widget-loaded fairy-ring arm: the log panel counts as open only
	 * while a path is displayed; the flag gates the per-tick scroll.
	 */
	public void widgetLoaded(int groupId, ActiveSearch pathfinder)
	{
		if (pathfinder != null && groupId == InterfaceID.FAIRYRINGS_LOG)
		{
			fairyRingPanelOpen = true;
		}
	}

	/**
	 * The widget-closed fairy-ring arm: the log panel no longer shows.
	 */
	public void widgetClosed(int groupId)
	{
		if (groupId == InterfaceID.FAIRYRINGS_LOG)
		{
			fairyRingPanelOpen = false;
		}
	}

	/**
	 * The post-client-tick fairy-ring arm: while the panel is open and a
	 * path is displayed, scroll it to the ring code the path uses.
	 */
	public void postClientTick(ActiveSearch pathfinder)
	{
		if (fairyRingPanelOpen && pathfinder != null)
		{
			scrollFairyRingPanel(pathfinder);
		}
	}

	private void scrollFairyRingPanel(ActiveSearch pathfinder)
	{
		List<PathStep> path;

		if (client == null || pathfinder == null
			|| (path = pathfinder.getPath()) == null)
		{
			return;
		}

		String fairyRingCode = null;

		for (int i = 1; i < path.size(); i++)
		{
			PathStep currentStep = path.get(i - 1);
			PathStep nextStep = path.get(i);
			for (Transport transport : transportsForEdge(currentStep, nextStep))
			{
				if (TransportType.FAIRY_RING.equals(transport.getType()))
				{
					fairyRingCode = transport.getDisplayInfo();
				}
			}
		}
		if (fairyRingCode == null)
		{
			return;
		}

		Widget codeWidget = null;

		Widget favesPanel = client.getWidget(InterfaceID.FairyringsLog.FAVES);
		if (favesPanel != null)
		{
			for (Widget widget : favesPanel.getStaticChildren())
			{
				if (widget != null)
				{
					String widgetText = widget.getText();
					if ((fairyRingCode.equals(widgetText)
						|| ("(Shortest Path) " + fairyRingCode).equals(widgetText)))
					{
						codeWidget = widget;
						break;
					}
				}
			}
		}

		Widget contentsList = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
		if (contentsList != null && codeWidget == null)
		{
			for (Widget widget : contentsList.getDynamicChildren())
			{
				if (widget != null)
				{
					String widgetText = widget.getText();
					if ((fairyRingCode.equals(widgetText)
						|| ("(Shortest Path) " + fairyRingCode).equals(widgetText)))
					{
						codeWidget = widget;
						break;
					}
				}
			}
		}

		if (codeWidget == null)
		{
			return;
		}

		codeWidget.setTextColor(0x00FF00);
		String codeWidgetText = codeWidget.getText();
		if (codeWidgetText != null && !codeWidgetText.contains("(Shortest Path)"))
		{
			codeWidget.setText("(Shortest Path) " + codeWidgetText);
		}

		if (contentsList == null)
		{
			return;
		}

		int panelScrollY = Math.min(
			codeWidget.getRelativeY(),
			contentsList.getScrollHeight() - contentsList.getHeight()
		);

		contentsList.setScrollY(panelScrollY);
		contentsList.revalidateScroll();

		client.runScript(
			ScriptID.UPDATE_SCROLLBAR,
			InterfaceID.FairyringsLog.SCROLLBAR,
			InterfaceID.FairyringsLog.CONTENTS,
			panelScrollY
		);
	}
}
