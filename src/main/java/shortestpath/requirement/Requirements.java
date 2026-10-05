package shortestpath.requirement;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import shortestpath.ShortestPathPlugin;
import shortestpath.SpiritTreePatchState;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeSnapshot;
import shortestpath.leagues.LeagueRegion;
import shortestpath.leagues.LeagueRegionChecker;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import shortestpath.transport.parser.SkillRequirementParser;

/**
 * The ordered gate chain deciding which transports can run this refresh.
 * Each named gate answers one rejection question and returns its
 * {@link RejectionReason}; {@link #check} evaluates them in the fixed order
 * below and returns the first non-{@code NONE} verdict, so the chain is the
 * single definition of transport admissibility. A transport passing every
 * gate is offered to the eligibility pools.
 *
 * <p>Boundary rule: facts the game or the config declares about the player —
 * skill levels, quest states, var values, owned items, declared unlocks,
 * respawn, sailing and league state, planted spirit trees — are read from
 * the {@link RequirementContext} snapshot captured once per refresh. Routing
 * policy — transport-type enablement, POH toggles, the teleportation-item
 * mode, jewellery-box tier and mounted items — is read from the immutable
 * {@link RoutingPolicy} snapshot taken at the same moment. Gates reach the
 * overridable quest/var seams through {@link RequirementHooks} and never
 * consult {@code PathfinderConfig} or the {@code Client} directly; client
 * state is only read while the context snapshot is built.
 *
 * <p>One instance is created per refresh, bound to that refresh's context
 * and policy, so a verdict can never mix facts from different refreshes.
 */
public final class Requirements
{
	private static final int MAX_SKILL_LEVEL = 99;

	/**
	 * Item ids that only exist on Deadman Mode worlds ({@code WorldType.DEADMAN}).
	 * Transports requiring them are filtered out on every other world type,
	 * regardless of the teleportation-item setting.
	 */
	private static final Set<Integer> DEADMAN_ONLY_ITEM_IDS = Set.of(
		ItemID.MAGIC_ROCK_OF_FAIRIES);

	/**
	 * Respawn landing tiles used by the Respawn Teleport spell and the POH respawn portal.
	 * Every respawn except Prifddinas exposes an {@code *_SPAWN} varbit; when Prifddinas is
	 * the active respawn all of them read 0, which is the same signature as the Lumbridge
	 * default, so both landings are gated on the declared respawn in config instead.
	 */
	private static final int LUMBRIDGE_RESPAWN = WorldPointUtil.packWorldPoint(3221, 3218, 0);
	private static final int PRIFDDINAS_RESPAWN = WorldPointUtil.packWorldPoint(3265, 6077, 0);

	private final RequirementContext context;
	private final RoutingPolicy policy;
	private final RequirementHooks hooks;
	// The context's skill-level getter defensively copies, so the
	// per-refresh instance keeps the snapshot's array rather than copying
	// it once per transport.
	private final int[] boostedSkillLevelsAndMore;

	public Requirements(RequirementContext context, RoutingPolicy policy, RequirementHooks hooks)
	{
		this.context = context;
		this.policy = policy;
		this.hooks = hooks;
		this.boostedSkillLevelsAndMore = context.getBoostedSkillLevelsAndMore();
	}

	/**
	 * Whether the transport may run this refresh — {@code true} when every
	 * gate passed ({@link RejectionReason#NONE}). The boolean adapter lets
	 * callers outside this package consume the verdict without naming the
	 * enum.
	 */
	public boolean usable(Transport transport)
	{
		return check(transport) == RejectionReason.NONE;
	}

	/**
	 * Whether the bank-destination requirement set is satisfied by this
	 * refresh's snapshot — the bank-side counterpart of {@link #usable}.
	 */
	public boolean satisfied(DestinationRequirements requirements)
	{
		return check(requirements) == RejectionReason.NONE;
	}

	/**
	 * Evaluates the gates in their fixed order and returns the first
	 * rejection, or {@link RejectionReason#NONE} when every gate passes.
	 * The order is part of the contract — later gates assume the earlier
	 * ones passed.
	 */
	RejectionReason check(Transport transport)
	{
		RejectionReason reason = sailing(transport);
		if (reason == RejectionReason.NONE) reason = pohDisabled(transport);
		if (reason == RejectionReason.NONE) reason = leagueRegion(transport);
		if (reason == RejectionReason.NONE) reason = typeDisabled(transport);
		if (reason == RejectionReason.NONE) reason = pohVariant(transport);
		if (reason == RejectionReason.NONE) reason = teleportationItem(transport);
		if (reason == RejectionReason.NONE) reason = respawn(transport);
		if (reason == RejectionReason.NONE) reason = unlockGate(transport);
		if (reason == RejectionReason.NONE) reason = jewelleryBoxTier(transport);
		if (reason == RejectionReason.NONE) reason = skillLevel(transport);
		if (reason == RejectionReason.NONE) reason = quest(transport);
		if (reason == RejectionReason.NONE) reason = varbit(transport);
		if (reason == RejectionReason.NONE) reason = varplayer(transport);
		if (reason == RejectionReason.NONE) reason = plantedSpiritTree(transport);
		if (reason == RejectionReason.NONE) reason = itemRequirement(transport);
		return reason;
	}

	/**
	 * Bank-destination admissibility: a {@link DestinationRequirements} is
	 * satisfied when its skill, quest, varbit and varplayer sets pass the
	 * same checks the transport gates above run. An empty requirement is
	 * always satisfied.
	 *
	 * <p>Bank evaluation intentionally reads this refresh's captured
	 * context values and routes through the shared overridable hooks
	 * ({@link RequirementHooks#varbitChecks}, {@link RequirementHooks#varPlayerChecks},
	 * the quest map the {@link RequirementHooks#getQuestState} hook feeds)
	 * instead of querying the client per tile as it used to. A bank tile and
	 * a transport can therefore never disagree about the same requirement
	 * inside one refresh, and test bypasses cover bank requirements too.
	 */
	RejectionReason check(DestinationRequirements requirements)
	{
		if (requirements == null || requirements.isEmpty())
		{
			return RejectionReason.NONE;
		}
		RejectionReason reason = skillLevel(requirements.getSkillLevels());
		if (reason == RejectionReason.NONE && !completedQuests(requirements.getQuests()))
		{
			reason = RejectionReason.QUEST;
		}
		if (reason == RejectionReason.NONE
			&& hooks.varbitChecks(requirements.getVarbits(), context.getVarbitValues(),
				context.getEvaluationTimeMinutes()))
		{
			reason = RejectionReason.VARBIT;
		}
		if (reason == RejectionReason.NONE
			&& hooks.varPlayerChecks(requirements.getVarPlayers(), context.getVarPlayerValues(),
				context.getEvaluationTimeMinutes()))
		{
			reason = RejectionReason.VARPLAYER;
		}
		return reason;
	}

	// Sailing: suppress teleports while the player is aboard a boat.
	// We don't model sailing navigation, so teleporting away mid-ocean would produce
	// confusing suggestions. Pathfinding resumes normally after disembarking.
	private RejectionReason sailing(Transport transport)
	{
		if (context.isOnSailingBoat() && transport.getType().isTeleport())
		{
			return RejectionReason.SAILING;
		}
		return RejectionReason.NONE;
	}

	// Master POH gate - if POH is disabled, reject all POH transports
	private RejectionReason pohDisabled(Transport transport)
	{
		if (!policy.usePoh())
		{
			int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
			int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());
			int destX = WorldPointUtil.unpackWorldX(transport.getDestination());
			int destY = WorldPointUtil.unpackWorldY(transport.getDestination());
			if (ShortestPathPlugin.isInsidePoh(originX, originY) || ShortestPathPlugin.isInsidePoh(destX, destY))
			{
				return RejectionReason.POH_DISABLED;
			}
		}
		return RejectionReason.NONE;
	}

	// League region gate: in seasonal mode, drop transports that touch the
	// always-blocked region or a region the player has not unlocked.
	private RejectionReason leagueRegion(Transport transport)
	{
		if (!isTransportRegionAllowed(transport))
		{
			return RejectionReason.LEAGUE_REGION;
		}
		return RejectionReason.NONE;
	}

	/**
	 * Whether both endpoints of the supplied transport are in unlocked
	 * regions for the current league state. Always-unlocked tiles
	 * (NEUTRAL, Varlamore, Karamja) pass through unchanged on any world.
	 *
	 * <p>If the transport declares a {@link Transport#getRegionOverride()
	 * region override}, it replaces the chunk-classifier result for the
	 * destination endpoint. Used for shortcuts whose destination chunk
	 * sits in a different region than the wiki classifies the shortcut
	 * under (e.g. Trollheim Wilderness climb — destination chunk is
	 * Wilderness, but the shortcut is wiki-listed as Asgarnia).
	 */
	private boolean isTransportRegionAllowed(Transport transport)
	{
		LeagueModeSnapshot leagueMode = context.getLeagueModeSnapshot();
		if (!leagueMode.isSeasonal())
		{
			return true;
		}
		LeagueRegion origin = LeagueRegionChecker.getRegion(transport.getOrigin());
		if (!leagueMode.isUnlocked(origin))
		{
			return false;
		}
		LeagueRegion destination = transport.getRegionOverride() != null
			? transport.getRegionOverride()
			: LeagueRegionChecker.getRegion(transport.getDestination());
		return leagueMode.isUnlocked(destination);
	}

	// Check if transport type is enabled in config
	private RejectionReason typeDisabled(Transport transport)
	{
		if (!policy.isTransportTypeEnabled(transport.getType()))
		{
			return RejectionReason.TYPE_DISABLED;
		}
		return RejectionReason.NONE;
	}

	// Handle POH variants for types that have them
	private RejectionReason pohVariant(Transport transport)
	{
		TransportType type = transport.getType();
		int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
		int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());
		int destX = WorldPointUtil.unpackWorldX(transport.getDestination());
		int destY = WorldPointUtil.unpackWorldY(transport.getDestination());

		if (!ShortestPathPlugin.isInsidePoh(originX, originY) && !ShortestPathPlugin.isInsidePoh(destX, destY))
		{
			return RejectionReason.NONE; // Not a POH transport
		}

		// POH fairy ring
		if (TransportType.FAIRY_RING.equals(type))
		{
			return policy.usePohFairyRing() ? RejectionReason.NONE : RejectionReason.POH_VARIANT;
		}
		// POH spirit tree
		if (TransportType.SPIRIT_TREE.equals(type))
		{
			return policy.usePohSpiritTree() ? RejectionReason.NONE : RejectionReason.POH_VARIANT;
		}
		// POH obelisk
		if (TransportType.WILDERNESS_OBELISK.equals(type))
		{
			return policy.usePohObelisk() ? RejectionReason.NONE : RejectionReason.POH_VARIANT;
		}
		if (TransportType.TELEPORTATION_PORTAL_POH.equals(type))
		{
			return isPohNexusPortalEnabled(policy.enabledPohNexusPortals(), transport.getDisplayInfo())
				? RejectionReason.NONE : RejectionReason.POH_VARIANT;
		}

		return RejectionReason.NONE;
	}

	// Handle special cases for teleportation items and seasonal transports
	private RejectionReason teleportationItem(Transport transport)
	{
		TransportType type = transport.getType();
		if (!TeleportRestriction.isItemTeleportType(type))
		{
			return RejectionReason.NONE; // Not a teleportation item type
		}

		LeagueModeSnapshot leagueMode = context.getLeagueModeSnapshot();
		// Seasonal transports only exist on seasonal worlds; a lingering config
		// toggle must not leak them into normal worlds.
		if (TransportType.SEASONAL_TRANSPORTS.equals(type) && !leagueMode.isSeasonal())
		{
			return RejectionReason.SEASONAL_WORLD;
		}

		// Mode-locked items (e.g. the Deadman-only Trinket of fairies) can never
		// be obtained on other world types, even when the ALL/UNLOCKED settings
		// bypass the inventory check.
		if (!leagueMode.isDeadman() && requiresModeLockedItem(transport, DEADMAN_ONLY_ITEM_IDS))
		{
			return RejectionReason.DEADMAN_ITEM;
		}

		// Player-declared per-item restrictions drop the transport from the
		// candidate set: the modes below can bypass item evaluation entirely,
		// so the restriction gate runs here, before eligibility is consulted.
		TransportItems itemRequirements = transport.getItemRequirements();
		if (itemRequirements != null && !itemRequirements.survivesBlockedItems(policy.blockedItemIds()))
		{
			return RejectionReason.BLOCKED_ITEM;
		}

		switch (policy.teleportationItemSetting())
		{
			case ALL:
				return RejectionReason.NONE;
			case ALL_NON_CONSUMABLE:
			case UNLOCKED_NON_CONSUMABLE:
			case INVENTORY_NON_CONSUMABLE:
			case INVENTORY_AND_BANK_NON_CONSUMABLE:
				return transport.isConsumable()
					? RejectionReason.TELEPORT_MODE
					: RejectionReason.NONE;
			case UNLOCKED:
				return RejectionReason.NONE; // Ownership is implied by the unlock check; items are never evaluated
			case INVENTORY:
			case INVENTORY_AND_BANK:
				return RejectionReason.NONE; // Will be checked later by the eligibility snapshot
			case NONE:
				return RejectionReason.TELEPORT_MODE;
		}
		return RejectionReason.NONE;
	}



	// Respawn rows for Prifddinas (and the colliding Lumbridge default) are
	// gated on the declared respawn in config, not on varbits
	private RejectionReason respawn(Transport transport)
	{
		if (!checkRespawnGate(transport))
		{
			return RejectionReason.RESPAWN_DECLARED;
		}
		return RejectionReason.NONE;
	}

	/**
	 * Gates respawn-destination transports on the declared respawn in config.
	 * When the active respawn is Prifddinas every {@code *_SPAWN} varbit reads 0,
	 * which is indistinguishable from the Lumbridge default, so the config option
	 * resolves the ambiguity in both directions.
	 */
	private boolean checkRespawnGate(Transport transport)
	{
		if (!transport.hasDisplayInfo("Respawn"))
		{
			return true;
		}
		int destination = transport.getDestination();
		if (destination == PRIFDDINAS_RESPAWN)
		{
			return context.isRespawnPrifddinas();
		}
		if (destination == LUMBRIDGE_RESPAWN)
		{
			return !context.isRespawnPrifddinas();
		}
		return true;
	}

	// Pure-unlock requirements are gated on the declared unlock set here,
	// ahead of the item evaluation in the eligibility snapshot — teleportation-item
	// modes can skip that evaluation entirely and must still honour the gate
	private RejectionReason unlockGate(Transport transport)
	{
		if (!checkUnlockGates(transport))
		{
			return RejectionReason.UNLOCK_GATE;
		}
		return RejectionReason.NONE;
	}

	/**
	 * Gates transports carrying a pure-unlock item requirement (every OR branch
	 * of the requirement is an unlock token) on the declared unlock set. Item
	 * pools can never satisfy such a requirement, and item evaluation is not the
	 * only path a transport can take to be counted usable — teleportation-item
	 * modes skip it entirely — so the gate is checked here for every transport.
	 */
	private boolean checkUnlockGates(Transport transport)
	{
		Set<Unlock> unlocks = context.getUnlocks();
		// The POH Honour teleportation box has no Items column to carry the
		// unlock term, so it is gated here by its display info, the same
		// singleton pattern as checkRespawnGate.
		if (TransportType.TELEPORTATION_BOX.equals(transport.getType())
			&& transport.hasDisplayInfo("Honour")
			&& !unlocks.contains(Unlock.XERICS_HONOUR))
		{
			return false;
		}
		TransportItems itemRequirements = transport.getItemRequirements();
		if (itemRequirements == null)
		{
			return true;
		}
		for (ItemRequirement requirement : itemRequirements.getRequirements())
		{
			if (!requirement.isPureUnlock())
			{
				continue;
			}
			boolean declared = false;
			for (ItemRequirement.Branch branch : requirement.getBranches())
			{
				if (unlocks.contains(branch.getUnlock()))
				{
					declared = true;
					break;
				}
			}
			if (!declared)
			{
				return false;
			}
		}
		return true;
	}

	// Handle jewellery box tier filtering
	private RejectionReason jewelleryBoxTier(Transport transport)
	{
		if (TransportType.TELEPORTATION_BOX.equals(transport.getType())
			&& !checkJewelleryBoxTier(transport))
		{
			return RejectionReason.JEWELLERY_BOX_TIER;
		}
		return RejectionReason.NONE;
	}

	/**
	 * Checks if a TELEPORTATION_BOX transport should be used based on POH settings.
	 * Handles jewellery box tiers and mounted items.
	 */
	private boolean checkJewelleryBoxTier(Transport transport)
	{
		String objectInfo = transport.getObjectInfo();
		if (objectInfo == null)
		{
			return false;
		}

		PohMountedItem mountedItem = PohMountedItem.fromObjectInfo(objectInfo);
		if (mountedItem != null)
		{
			// If mounted glory and ornate jewellery box is enabled, skip the glory
			// because the ornate box already covers all 4 destinations with correct prefixes
			if (PohMountedItem.GLORY.equals(mountedItem) && JewelleryBoxTier.ORNATE.equals(policy.pohJewelleryBoxTier()))
			{
				return false;
			}
			return isPohMountedItemEnabled(policy.enabledPohMountedItems(), objectInfo);
		}

		// Filter jewellery boxes by tier
		if (JewelleryBoxTier.NONE.equals(policy.pohJewelleryBoxTier()))
		{
			return false;
		}

		// Basic box (37492): destinations 1-9
		if (objectInfo.contains("Basic Jewellery Box 37492"))
		{
			return true; // All tiers include basic
		}

		// Fancy box (37501): destinations A-J
		if (objectInfo.contains("Fancy Jewellery Box 37501"))
		{
			return JewelleryBoxTier.FANCY.equals(policy.pohJewelleryBoxTier()) ||
				JewelleryBoxTier.ORNATE.equals(policy.pohJewelleryBoxTier());
		}

		// Ornate box (37520): destinations K-R
		if (objectInfo.contains("Ornate Jewellery Box 37520"))
		{
			return JewelleryBoxTier.ORNATE.equals(policy.pohJewelleryBoxTier());
		}

		return false;
	}

	private RejectionReason skillLevel(Transport transport)
	{
		return skillLevel(transport.getSkillLevels());
	}

	private RejectionReason skillLevel(int[] requiredLevels)
	{
		// In leagues some skills are disabled so the max total level is lower than
		// the standard 2376. Holding the item (e.g. Max cape) already proves the
		// player is maxed for the available skills, so skip the total-level check.
		final int totalLevelIndex = Skill.values().length;
		LeagueModeSnapshot leagueMode = context.getLeagueModeSnapshot();
		for (int i = 0; i < boostedSkillLevelsAndMore.length; i++)
		{
			if (leagueMode.isSeasonal() && i == totalLevelIndex)
			{
				continue;
			}
			int boostedLevel = boostedSkillLevelsAndMore[i];
			// A shorter requirement array (e.g. a hand-built
			// DestinationRequirements) declares no level for the missing
			// indices — read 0 rather than throwing.
			int requiredLevel = i < requiredLevels.length ? requiredLevels[i] : 0;
			if (requiredLevel == SkillRequirementParser.MAX_LEVEL)
			{
				requiredLevel = maximumLevel(i);
			}
			if (boostedLevel < requiredLevel)
			{
				return RejectionReason.SKILL_LEVEL;
			}
		}
		return RejectionReason.NONE;
	}

	private int maximumLevel(int index)
	{
		if (index < Skill.values().length)
		{
			return MAX_SKILL_LEVEL;
		}
		if (index == Skill.values().length)
		{
			return MAX_SKILL_LEVEL * Skill.values().length;
		}
		if (index == Skill.values().length + 1)
		{
			return 126;
		}
		if (index == Skill.values().length + 2)
		{
			return context.getCurrentMaxQuestPoints();
		}
		return SkillRequirementParser.MAX_LEVEL;
	}

	private RejectionReason quest(Transport transport)
	{
		if (transport.isQuestLocked() && !completedQuests(transport.getQuests()))
		{
			return RejectionReason.QUEST;
		}
		return RejectionReason.NONE;
	}

	private boolean completedQuests(Collection<Quest> quests)
	{
		Map<Quest, QuestState> questStates = context.getQuestStates();
		for (Quest quest : quests)
		{
			if (!QuestState.FINISHED.equals(questStates.getOrDefault(quest, QuestState.NOT_STARTED)))
			{
				return false;
			}
		}
		return true;
	}

	private RejectionReason varbit(Transport transport)
	{
		if (hooks.varbitChecks(transport.getVarRequirements(), context.getVarbitValues(),
			context.getEvaluationTimeMinutes()))
		{
			return RejectionReason.VARBIT;
		}
		return RejectionReason.NONE;
	}

	private RejectionReason varplayer(Transport transport)
	{
		if (hooks.varPlayerChecks(transport.getVarRequirements(), context.getVarPlayerValues(),
			context.getEvaluationTimeMinutes()))
		{
			return RejectionReason.VARPLAYER;
		}
		return RejectionReason.NONE;
	}

	private RejectionReason plantedSpiritTree(Transport transport)
	{
		TransportType type = transport.getType();
		if (TransportType.SPIRIT_TREE.equals(type) || TransportType.SEASONAL_TRANSPORTS.equals(type)
			|| TransportType.TELEPORTATION_ITEM.equals(type))
		{
			if (!checkPlantedSpiritTrees(transport))
			{
				return RejectionReason.PLANTED_SPIRIT_TREE;
			}
		}
		return RejectionReason.NONE;
	}

	private boolean checkPlantedSpiritTrees(Transport transport)
	{
		int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
		int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());

		// Check planted spirit tree origins (travel FROM a planted tree)
		if (isUnavailablePlantedSpiritTree(originX, originY))
		{
			return false;
		}

		// Check planted spirit tree destinations (travel TO a planted tree)
		int destX = WorldPointUtil.unpackWorldX(transport.getDestination());
		int destY = WorldPointUtil.unpackWorldY(transport.getDestination());

		return !isUnavailablePlantedSpiritTree(destX, destY);
	}

	/**
	 * Whether the tile is a planted spirit-tree patch whose tree the player
	 * cannot currently use: {@code false} when the tile is not a patch at
	 * all, {@code true} when no availability observation exists yet (the
	 * unresolved case) or when the patch is absent from the available set.
	 * Callers negate this to get "allowed".
	 */
	private boolean isUnavailablePlantedSpiritTree(int x, int y)
	{
		String treeName = SpiritTreePatchState.patchNameForTile(x, y);
		if (treeName == null)
		{
			return false;
		}
		Set<String> availableSpiritTrees = context.getAvailableSpiritTrees();
		if (availableSpiritTrees == null)
		{
			return true;
		}
		return !availableSpiritTrees.contains(treeName);
	}

	// The item OR-lists evaluate against the context's item pools; the
	// transport is rejected only when neither path state can satisfy them.
	private RejectionReason itemRequirement(Transport transport)
	{
		TransportEligibility eligibility = context.getEligibility();
		if (!eligibility.usable(transport, false) && !eligibility.usable(transport, true))
		{
			return RejectionReason.ITEM_REQUIREMENT;
		}
		return RejectionReason.NONE;
	}

	static boolean isPohNexusPortalEnabled(Set<PohNexusPortal> enabledPortals, String displayInfo)
	{
		PohNexusPortal portal = PohNexusPortal.fromDisplayInfo(displayInfo);
		return portal == null || enabledPortals.contains(portal);
	}

	static boolean isPohMountedItemEnabled(Set<PohMountedItem> enabledItems, String objectInfo)
	{
		PohMountedItem item = PohMountedItem.fromObjectInfo(objectInfo);
		return item == null || enabledItems.contains(item);
	}

	/**
	 * Whether the transport has an item requirement that can only be satisfied
	 * by mode-locked items — every alternative in some requirement branch is in
	 * {@code modeLockedItemIds}. A branch that also lists a normal item keeps
	 * the transport usable on every world.
	 */
	private static boolean requiresModeLockedItem(Transport transport, Set<Integer> modeLockedItemIds)
	{
		TransportItems itemRequirements = transport.getItemRequirements();
		if (itemRequirements == null)
		{
			return false;
		}
		for (int[] alternatives : itemRequirements.getItems())
		{
			boolean allLocked = alternatives.length > 0;
			for (int itemId : alternatives)
			{
				if (!modeLockedItemIds.contains(itemId))
				{
					allLocked = false;
					break;
				}
			}
			if (allLocked)
			{
				return true;
			}
		}
		return false;
	}
}
