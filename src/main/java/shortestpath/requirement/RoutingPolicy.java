package shortestpath.requirement;

import java.util.EnumSet;
import java.util.Set;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;
import shortestpath.transport.TransportType;

/**
 * Immutable snapshot of the user routing settings the requirement gates read,
 * captured once per refresh. What goes in here is routing policy — which
 * transport types are enabled (including the quest/varbit-derived
 * {@code disableUnless} overrides, so the snapshot is taken only after those
 * derivations ran), the POH master and variant toggles, the enabled nexus
 * portals and mounted items, the jewellery-box tier, the teleportation-item
 * mode, the currency threshold and whether bank paths are included. Facts the
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
	private final TeleportationItem teleportationItemSetting;
	private final boolean usePoh;
	private final boolean usePohFairyRing;
	private final boolean usePohSpiritTree;
	private final boolean usePohObelisk;
	private final Set<PohNexusPortal> enabledPohNexusPortals;
	private final Set<PohMountedItem> enabledPohMountedItems;
	private final JewelleryBoxTier pohJewelleryBoxTier;
	private final int currencyThreshold;
	private final boolean includeBankPath;

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
		boolean includeBankPath)
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

	public TeleportationItem teleportationItemSetting()
	{
		return teleportationItemSetting;
	}

	public boolean usePoh()
	{
		return usePoh;
	}

	public boolean usePohFairyRing()
	{
		return usePohFairyRing;
	}

	public boolean usePohSpiritTree()
	{
		return usePohSpiritTree;
	}

	public boolean usePohObelisk()
	{
		return usePohObelisk;
	}

	public Set<PohNexusPortal> enabledPohNexusPortals()
	{
		return enabledPohNexusPortals;
	}

	public Set<PohMountedItem> enabledPohMountedItems()
	{
		return enabledPohMountedItems;
	}

	public JewelleryBoxTier pohJewelleryBoxTier()
	{
		return pohJewelleryBoxTier;
	}

	public int currencyThreshold()
	{
		return currencyThreshold;
	}

	public boolean includeBankPath()
	{
		return includeBankPath;
	}
}
