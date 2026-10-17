package shortestpath.requirement;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.settings.TeleportationItem;
import shortestpath.poh.PohMountedItem;
import shortestpath.poh.PohNexusPortal;
import shortestpath.transport.TransportType;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Immutable snapshot of the user routing settings the requirement gates read,
 * captured once per refresh. What goes in here is routing policy — which
 * transport types are enabled (including the quest/varbit-derived
 * {@code disableUnless} overrides, so the snapshot is taken only after those
 * derivations ran), the POH master and variant toggles, the enabled nexus
 * portals and mounted items, the jewellery-box tier, the teleportation-item
 * mode, the currency threshold, whether bank paths are included, and the
 * player-declared blocked teleport items with their {@code id:N} threshold
 * overrides. Facts the
 * game or config asserts about the player — skill levels, quest states, var
 * values, owned items, declared unlocks, respawn, sailing and league state,
 * planted spirit trees — are state, not policy, and live on
 * {@link RequirementContext} instead.
 *
 * <p>Every collection is frozen at construction and the transport-type
 * enablement is copied out of the mutable {@code TransportTypeConfig}, so
 * mutating config after the snapshot is built cannot change the verdicts of
 * a gate chain already holding it.
 */
public final class RoutingPolicy
{
	private final EnumSet<TransportType> enabledTypes;
	@Getter @Accessors(fluent = true) private final TeleportationItem teleportationItemSetting;
	@Getter @Accessors(fluent = true) private final boolean usePoh;
	@Getter @Accessors(fluent = true) private final boolean usePohFairyRing;
	@Getter @Accessors(fluent = true) private final boolean usePohSpiritTree;
	@Getter @Accessors(fluent = true) private final boolean usePohObelisk;
	@Getter @Accessors(fluent = true) private final Set<PohNexusPortal> enabledPohNexusPortals;
	@Getter @Accessors(fluent = true) private final Set<PohMountedItem> enabledPohMountedItems;
	@Getter @Accessors(fluent = true) private final JewelleryBoxTier pohJewelleryBoxTier;
	@Getter @Accessors(fluent = true) private final int currencyThreshold;
	@Getter @Accessors(fluent = true) private final boolean includeBankPath;
	@Getter @Accessors(fluent = true) private final Set<Integer> blockedItemIds;
	private final Map<Integer, Integer> itemThresholdOverrides;
	private final Map<Integer, Set<Integer>> blockedOptionDests;

	public RoutingPolicy(
		Set<TransportType> enabledTypes,
		TeleportationItem teleportationItemSetting,
		boolean usePoh,
		boolean usePohFairyRing,
		boolean usePohSpiritTree,
		boolean usePohObelisk,
		Set<PohNexusPortal> enabledPohNexusPortals,
		Set<PohMountedItem> enabledPohMountedItems,
		JewelleryBoxTier pohJewelleryBoxTier,
		int currencyThreshold,
		boolean includeBankPath,
		Set<Integer> blockedItemIds,
		Map<Integer, Integer> itemThresholdOverrides)
	{
		this(enabledTypes, teleportationItemSetting, usePoh, usePohFairyRing,
			usePohSpiritTree, usePohObelisk, enabledPohNexusPortals,
			enabledPohMountedItems, pohJewelleryBoxTier, currencyThreshold,
			includeBankPath, blockedItemIds, itemThresholdOverrides, Map.of());
	}

	public RoutingPolicy(
		Set<TransportType> enabledTypes,
		TeleportationItem teleportationItemSetting,
		boolean usePoh,
		boolean usePohFairyRing,
		boolean usePohSpiritTree,
		boolean usePohObelisk,
		Set<PohNexusPortal> enabledPohNexusPortals,
		Set<PohMountedItem> enabledPohMountedItems,
		JewelleryBoxTier pohJewelleryBoxTier,
		int currencyThreshold,
		boolean includeBankPath,
		Set<Integer> blockedItemIds,
		Map<Integer, Integer> itemThresholdOverrides,
		Map<Integer, Set<Integer>> blockedOptionDests)
	{
		this.enabledTypes = enabledTypes.isEmpty()
			? EnumSet.noneOf(TransportType.class)
			: EnumSet.copyOf(enabledTypes);
		this.teleportationItemSetting = teleportationItemSetting;
		this.usePoh = usePoh;
		this.usePohFairyRing = usePohFairyRing;
		this.usePohSpiritTree = usePohSpiritTree;
		this.usePohObelisk = usePohObelisk;
		this.enabledPohNexusPortals = Set.copyOf(enabledPohNexusPortals);
		this.enabledPohMountedItems = Set.copyOf(enabledPohMountedItems);
		this.pohJewelleryBoxTier = pohJewelleryBoxTier;
		this.currencyThreshold = currencyThreshold;
		this.includeBankPath = includeBankPath;
		this.blockedItemIds = Set.copyOf(blockedItemIds);
		this.itemThresholdOverrides = Map.copyOf(itemThresholdOverrides);
		// Inner sets are copied too — a mutable set handed out through the
		// outer map would defeat the freeze every other collection gets.
		Map<Integer, Set<Integer>> optionDests = new HashMap<>();
		for (Map.Entry<Integer, Set<Integer>> entry : blockedOptionDests.entrySet())
		{
			optionDests.put(entry.getKey(), Set.copyOf(entry.getValue()));
		}
		this.blockedOptionDests = Map.copyOf(optionDests);
	}

	/**
	 * Whether the transport type is enabled in the captured view — the answer
	 * {@code TransportTypeConfig.isEnabled} would have given at snapshot time,
	 * after the {@code disableUnless} derivations. The enabled set itself is
	 * never handed out, so there is no live mutable reference to leak.
	 */
	public boolean isTransportTypeEnabled(TransportType type)
	{
		return enabledTypes.contains(type);
	}












	/**
	 * Item ids the player excluded from routing via the hidden
	 * {@code blockedTeleportItems} CSV — a candidate-set filter, not a fact
	 * about what the player owns, so it lives on the policy side.
	 */


	/**
	 * Per-item tiles-saved threshold overrides parsed from the {@code id:N}
	 * records of {@code blockedTeleportItems}; consumed by the additional-cost
	 * path, not by the verdict chain.
	 */
	public Map<Integer, Integer> itemThresholdOverrides()
	{
		return itemThresholdOverrides;
	}

	/**
	 * Destination-scoped restriction blocks parsed from the {@code rep@dest}
	 * records of {@code blockedTeleportItems}: a family's representative
	 * (display) item id to the packed destinations the player excluded for
	 * it. Inner destination sets are frozen at construction, matching the
	 * snapshot rule every other collection follows.
	 */
	public Map<Integer, Set<Integer>> blockedOptionDests()
	{
		return blockedOptionDests;
	}
}
