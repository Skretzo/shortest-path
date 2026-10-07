package shortestpath.requirement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.leagues.LeagueRegion;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Adversarial suite over the ordered {@link Requirements} gate chain: every
 * gate in order, the {@link RejectionReason} each one reports, and the
 * sequencing that decides which reason wins when several gates would reject.
 * Contexts and policies come from {@link RequirementTestFixtures}; each case
 * defaults to the always-passing snapshot and changes only what the gate
 * under test reads, so a returned reason is provably that gate's verdict.
 *
 * <p>No {@code Client} is involved anywhere — the chain reads the frozen
 * {@link RequirementContext} and {@link RoutingPolicy} only.
 */
public class RequirementsTest
{
	private static final int FALADOR = WorldPointUtil.packWorldPoint(2965, 3378, 0);
	private static final int VARROCK = WorldPointUtil.packWorldPoint(3213, 3424, 0);
	private static final int POH = WorldPointUtil.packWorldPoint(1858, 7051, 0);
	// Region 12850 — Lumbridge castle — classifies as MISTHALIN, the always-blocked league region.
	private static final int LUMBRIDGE = WorldPointUtil.packWorldPoint(3222, 3218, 0);
	// Region 11058 — inside the Brimhaven planted spirit-tree patch bounds — classifies as KARAMJA.
	private static final int BRIMHAVEN_PATCH = WorldPointUtil.packWorldPoint(2802, 3203, 0);
	// Inside the Port Sarim planted spirit-tree patch bounds (3058-3062, 3256-3260).
	private static final int PORT_SARIM_PATCH = WorldPointUtil.packWorldPoint(3060, 3258, 0);
	private static final int LUMBRIDGE_RESPAWN = WorldPointUtil.packWorldPoint(3221, 3218, 0);
	private static final int PRIFDDINAS_RESPAWN = WorldPointUtil.packWorldPoint(3265, 6077, 0);

	private static final int COINS = ItemID.COINS;
	private static final int LAW_RUNE = ItemID.LAWRUNE;
	private static final int AIR_RUNE = ItemID.AIRRUNE;
	private static final int DEADMAN_ONLY = ItemID.MAGIC_ROCK_OF_FAIRIES;

	private static int pack(int x, int y, int plane)
	{
		return WorldPointUtil.packWorldPoint(x, y, plane);
	}

	private static Transport.TransportBuilder transport(TransportType type)
	{
		return new Transport.TransportBuilder().type(type).origin(FALADOR).destination(VARROCK);
	}

	private static Requirements requirements(RequirementContext context, RoutingPolicy policy)
	{
		return RequirementTestFixtures.requirements(context, policy);
	}

	private static LeagueModeState seasonal(LeagueRegion... unlocked)
	{
		LeagueModeState state = new LeagueModeState();
		// setForTest translates null to an empty EnumSet; Set.of() is empty
		// here, which EnumSet.copyOf rejects.
		state.setForTest(true, unlocked.length == 0 ? null : Set.of(unlocked));
		return state;
	}

	private static LeagueModeState deadman()
	{
		LeagueModeState state = new LeagueModeState();
		state.refresh(new MapPlayerStateSource().worldTypes(WorldType.DEADMAN));
		return state;
	}

	private static ItemRequirement.Branch branch(int... itemIds)
	{
		return new ItemRequirement.Branch(itemIds, null, null, 1);
	}

	private static ItemRequirement.Branch unlockBranch(Unlock unlock)
	{
		return new ItemRequirement.Branch(null, null, null, 1, unlock);
	}

	private static ItemRequirement item(int itemId)
	{
		return new ItemRequirement(new int[]{itemId}, null, null, 1);
	}

	private static ItemRequirement item(int itemId, int quantity)
	{
		return new ItemRequirement(new int[]{itemId}, null, null, quantity);
	}

	private static TransportItems items(ItemRequirement... requirements)
	{
		return new TransportItems(List.of(requirements));
	}

	// ------------------------------------------------------------------
	// RejectionReason pins — the enum is review instrumentation; every
	// non-NONE constant is referenced so a removal or rename breaks here.
	// ------------------------------------------------------------------

	@Test
	public void everyRejectionConstantIsPinnedByReference()
	{
		// 19 constants: NONE plus the 18 rejection reasons below.
		assertEquals(19, RejectionReason.values().length);
		RejectionReason[] reasons = {
			RejectionReason.SAILING,
			RejectionReason.POH_DISABLED,
			RejectionReason.LEAGUE_REGION,
			RejectionReason.TYPE_DISABLED,
			RejectionReason.POH_VARIANT,
			RejectionReason.SEASONAL_WORLD,
			RejectionReason.DEADMAN_ITEM,
			RejectionReason.BLOCKED_ITEM,
			RejectionReason.TELEPORT_MODE,
			RejectionReason.RESPAWN_DECLARED,
			RejectionReason.UNLOCK_GATE,
			RejectionReason.JEWELLERY_BOX_TIER,
			RejectionReason.SKILL_LEVEL,
			RejectionReason.QUEST,
			RejectionReason.VARBIT,
			RejectionReason.VARPLAYER,
			RejectionReason.PLANTED_SPIRIT_TREE,
			RejectionReason.ITEM_REQUIREMENT,
		};
		for (RejectionReason reason : reasons)
		{
			assertFalse(reason + " must not be the acceptance verdict", reason == RejectionReason.NONE);
		}
	}

	@Test
	public void blockedItemKeepsItsReservedSeatInsideTheTeleportationItemGate()
	{
		// The per-item restriction gate seats inside the teleportation-item
		// gate — after the seasonal and deadman checks, before the mode
		// dispatch — because the bypass modes below skip item evaluation and
		// must not skip restrictions. The enum reserves the constant at that
		// seat; this marker pins its position so a reorder is visible.
		assertEquals(RejectionReason.DEADMAN_ITEM.ordinal() + 1, RejectionReason.BLOCKED_ITEM.ordinal());
		assertEquals(RejectionReason.BLOCKED_ITEM.ordinal() + 1, RejectionReason.TELEPORT_MODE.ordinal());
		assertNotNull(RejectionReason.valueOf("BLOCKED_ITEM"));
	}

	// ------------------------------------------------------------------
	// Gate 1: sailing — suppress teleports while aboard a boat.
	// ------------------------------------------------------------------

	@Test
	public void sailingRejectsTeleportsAboardABoat()
	{
		RequirementContext aboard = RequirementTestFixtures.context()
			.onSailingBoat(true).build();
		Requirements requirements = requirements(aboard, RequirementTestFixtures.policy(TeleportationItem.INVENTORY));

		assertEquals(RejectionReason.SAILING,
			requirements.check(transport(TransportType.TELEPORTATION_ITEM).build()));
		assertEquals(RejectionReason.SAILING,
			requirements.check(transport(TransportType.TELEPORTATION_SPELL).build()));
		assertEquals(RejectionReason.SAILING,
			requirements.check(transport(TransportType.TELEPORTATION_MINIGAME).build()));
		assertEquals(RejectionReason.SAILING,
			requirements.check(transport(TransportType.QUETZAL_WHISTLE).build()));
		// A home teleport is a teleport too.
		assertEquals(RejectionReason.SAILING,
			requirements.check(transport(TransportType.TELEPORTATION_SPELL_HOME).build()));
	}

	@Test
	public void sailingDoesNotRejectNonTeleportTransports()
	{
		RequirementContext aboard = RequirementTestFixtures.context()
			.onSailingBoat(true).build();
		Requirements requirements = requirements(aboard, RequirementTestFixtures.policy(TeleportationItem.INVENTORY));

		// Boats, transports, levers and POH-family types are not teleports.
		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.BOAT).build()));
		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.TRANSPORT).build()));
		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.TELEPORTATION_LEVER).build()));
		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.FAIRY_RING).build()));
	}

	@Test
	public void sailingRunsBeforePohAndTypeGates()
	{
		// A teleport out of a disabled POH while aboard — sailing is first.
		RequirementContext aboard = RequirementTestFixtures.context()
			.onSailingBoat(true).build();
		Set<TransportType> withoutTeleportItems = EnumSet.allOf(TransportType.class);
		withoutTeleportItems.remove(TransportType.TELEPORTATION_ITEM);
		RoutingPolicy policy = RequirementTestFixtures.policy()
			.usePoh(false)
			.enabledTypes(withoutTeleportItems)
			.teleportationItemSetting(TeleportationItem.INVENTORY)
			.build();
		Requirements requirements = requirements(aboard, policy);

		Transport transport = transport(TransportType.TELEPORTATION_ITEM).origin(POH).build();
		assertEquals(RejectionReason.SAILING, requirements.check(transport));
	}

	// ------------------------------------------------------------------
	// Gate 2: POH master — POH endpoints are unreachable when POH is off.
	// ------------------------------------------------------------------

	@Test
	public void disabledPohRejectsTransportsTouchingTheHouse()
	{
		RoutingPolicy noPoh = RequirementTestFixtures.policy()
			.usePoh(false)
			.teleportationItemSetting(TeleportationItem.INVENTORY)
			.build();
		Requirements requirements = requirements(RequirementTestFixtures.context().build(), noPoh);

		assertEquals(RejectionReason.POH_DISABLED,
			requirements.check(transport(TransportType.TRANSPORT).origin(POH).build()));
		assertEquals(RejectionReason.POH_DISABLED,
			requirements.check(transport(TransportType.TRANSPORT).destination(POH).build()));
		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.TRANSPORT).build()));
	}

	@Test
	public void disabledPohRunsBeforeTypeEnablement()
	{
		Set<TransportType> noCanoe = EnumSet.allOf(TransportType.class);
		noCanoe.remove(TransportType.CANOE);
		RoutingPolicy policy = RequirementTestFixtures.policy()
			.usePoh(false)
			.enabledTypes(noCanoe)
			.teleportationItemSetting(TeleportationItem.INVENTORY)
			.build();
		Requirements requirements = requirements(RequirementTestFixtures.context().build(), policy);

		Transport canoeFromPoh = transport(TransportType.CANOE).origin(POH).build();
		assertEquals(RejectionReason.POH_DISABLED, requirements.check(canoeFromPoh));
	}

	// ------------------------------------------------------------------
	// Gate 3: league region — seasonal worlds drop transports touching
	// regions the player has not unlocked (and the always-blocked region).
	// ------------------------------------------------------------------

	@Test
	public void seasonalWorldRejectsTransportsIntoLockedOrBlockedRegions()
	{
		RequirementContext seasonalNoUnlocks = RequirementTestFixtures.context()
			.leagueModeState(seasonal()).build();
		Requirements requirements = requirements(seasonalNoUnlocks,
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));

		// Misthalin is always-blocked: even an unlocked set cannot contain it.
		assertEquals(RejectionReason.LEAGUE_REGION,
			requirements.check(transport(TransportType.TRANSPORT).origin(LUMBRIDGE).build()));
		assertEquals(RejectionReason.LEAGUE_REGION,
			requirements.check(transport(TransportType.TRANSPORT).origin(POH).destination(LUMBRIDGE).build()));
		// Karamja is a pick-gated region: absent from the empty unlock set.
		assertEquals(RejectionReason.LEAGUE_REGION,
			requirements.check(transport(TransportType.TRANSPORT).origin(POH).destination(BRIMHAVEN_PATCH).build()));
	}

	@Test
	public void unlockedLeagueRegionAdmitsTheTransport()
	{
		RequirementContext karamja = RequirementTestFixtures.context()
			.leagueModeState(seasonal(LeagueRegion.KARAMJA)).build();
		Requirements requirements = requirements(karamja,
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));

		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.TRANSPORT).origin(POH).destination(BRIMHAVEN_PATCH).build()));
		// NEUTRAL endpoints are reachable on any seasonal world.
		RequirementContext seasonalNoUnlocks = RequirementTestFixtures.context()
			.leagueModeState(seasonal()).build();
		Requirements neutral = requirements(seasonalNoUnlocks,
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));
		assertEquals(RejectionReason.NONE,
			neutral.check(transport(TransportType.TRANSPORT).origin(POH).destination(POH).build()));
	}

	@Test
	public void regionOverrideReplacesTheDestinationClassification()
	{
		RequirementContext seasonalNoUnlocks = RequirementTestFixtures.context()
			.leagueModeState(seasonal()).build();
		Requirements requirements = requirements(seasonalNoUnlocks,
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));

		// The destination lies in Misthalin, but the row claims Varlamore —
		// the override wins and the transport survives the league gate.
		Transport overridden = transport(TransportType.TRANSPORT)
			.origin(POH).destination(LUMBRIDGE)
			.regionOverride(LeagueRegion.VARLAMORE)
			.build();
		assertEquals(RejectionReason.NONE, requirements.check(overridden));
	}

	@Test
	public void leagueRegionRunsBeforeTypeEnablement()
	{
		Set<TransportType> noCanoe = EnumSet.allOf(TransportType.class);
		noCanoe.remove(TransportType.CANOE);
		RequirementContext seasonalNoUnlocks = RequirementTestFixtures.context()
			.leagueModeState(seasonal()).build();
		RoutingPolicy policy = RequirementTestFixtures.policy()
			.enabledTypes(noCanoe)
			.teleportationItemSetting(TeleportationItem.INVENTORY)
			.build();
		Requirements requirements = requirements(seasonalNoUnlocks, policy);

		Transport canoe = transport(TransportType.CANOE).origin(LUMBRIDGE).build();
		assertEquals(RejectionReason.LEAGUE_REGION, requirements.check(canoe));
	}

	// ------------------------------------------------------------------
	// Gate 4: transport type enablement.
	// ------------------------------------------------------------------

	@Test
	public void disabledTypeIsRejected()
	{
		Set<TransportType> enabled = EnumSet.allOf(TransportType.class);
		enabled.remove(TransportType.CANOE);
		RoutingPolicy policy = RequirementTestFixtures.policy()
			.enabledTypes(enabled)
			.teleportationItemSetting(TeleportationItem.INVENTORY)
			.build();
		Requirements requirements = requirements(RequirementTestFixtures.context().build(), policy);

		assertEquals(RejectionReason.TYPE_DISABLED,
			requirements.check(transport(TransportType.CANOE).build()));
		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.BOAT).build()));
	}

	@Test
	public void typeEnablementRunsBeforePohVariants()
	{
		Set<TransportType> enabled = EnumSet.allOf(TransportType.class);
		enabled.remove(TransportType.FAIRY_RING);
		RoutingPolicy policy = RequirementTestFixtures.policy()
			.enabledTypes(enabled)
			.usePohFairyRing(false)
			.teleportationItemSetting(TeleportationItem.INVENTORY)
			.build();
		Requirements requirements = requirements(RequirementTestFixtures.context().build(), policy);

		Transport pohFairyRing = transport(TransportType.FAIRY_RING).origin(POH).build();
		assertEquals(RejectionReason.TYPE_DISABLED, requirements.check(pohFairyRing));
	}

	// ------------------------------------------------------------------
	// Gate 5: POH variants — fairy ring, spirit tree, obelisk, nexus portal.
	// ------------------------------------------------------------------

	@Test
	public void pohFairyRingAndSpiritTreeFollowTheirVariantToggles()
	{
		RequirementContext context = RequirementTestFixtures.context().build();
		RoutingPolicy off = RequirementTestFixtures.policy()
			.usePohFairyRing(false).usePohSpiritTree(false).usePohObelisk(false)
			.teleportationItemSetting(TeleportationItem.INVENTORY).build();
		Requirements requirements = requirements(context, off);

		assertEquals(RejectionReason.POH_VARIANT,
			requirements.check(transport(TransportType.FAIRY_RING).origin(POH).build()));
		assertEquals(RejectionReason.POH_VARIANT,
			requirements.check(transport(TransportType.SPIRIT_TREE).origin(POH).build()));
		assertEquals(RejectionReason.POH_VARIANT,
			requirements.check(transport(TransportType.WILDERNESS_OBELISK).destination(POH).build()));
		// The variant toggles only bite inside the POH: overland fairy rings
		// and spirit trees are unaffected.
		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.FAIRY_RING).build()));
		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.SPIRIT_TREE).build()));
	}

	@Test
	public void pohNexusPortalFollowsTheEnabledPortalSet()
	{
		RequirementContext context = RequirementTestFixtures.context().build();
		Set<PohNexusPortal> withoutFalador = EnumSet.allOf(PohNexusPortal.class);
		withoutFalador.remove(PohNexusPortal.FALADOR);
		RoutingPolicy policy = RequirementTestFixtures.policy()
			.enabledPohNexusPortals(withoutFalador)
			.teleportationItemSetting(TeleportationItem.INVENTORY)
			.build();
		Requirements requirements = requirements(context, policy);

		Transport faladorPortal = transport(TransportType.TELEPORTATION_PORTAL_POH)
			.origin(POH).displayInfo("Falador Portal").build();
		assertEquals(RejectionReason.POH_VARIANT, requirements.check(faladorPortal));

		Transport varrockPortal = transport(TransportType.TELEPORTATION_PORTAL_POH)
			.origin(POH).displayInfo("Varrock Portal").build();
		assertEquals(RejectionReason.NONE, requirements.check(varrockPortal));
	}

	@Test
	public void unmappedNexusPortalDisplayInfoIsPermissive()
	{
		// A portal name the enum does not know stays routable — the gate is
		// a convenience filter, not a whitelist the data must satisfy.
		RequirementContext context = RequirementTestFixtures.context().build();
		RoutingPolicy policy = RequirementTestFixtures.policy()
			.enabledPohNexusPortals(Set.of())
			.teleportationItemSetting(TeleportationItem.INVENTORY)
			.build();
		Requirements requirements = requirements(context, policy);

		Transport unknown = transport(TransportType.TELEPORTATION_PORTAL_POH)
			.origin(POH).displayInfo("Mystery Portal").build();
		assertEquals(RejectionReason.NONE, requirements.check(unknown));
	}

	// ------------------------------------------------------------------
	// Gate 6: teleportation items — seasonal world, deadman-only items,
	// the reserved blocked-item seat, then the configured mode.
	// ------------------------------------------------------------------

	@Test
	public void seasonalTransportsRejectOutsideSeasonalWorlds()
	{
		RequirementContext normal = RequirementTestFixtures.context().build();
		Requirements requirements = requirements(normal,
			RequirementTestFixtures.policy(TeleportationItem.ALL));

		assertEquals(RejectionReason.SEASONAL_WORLD,
			requirements.check(transport(TransportType.SEASONAL_TRANSPORTS).build()));
	}

	@Test
	public void seasonalCheckRunsBeforeTheModeDispatch()
	{
		// Even NONE — which would reject every teleport-item family — reports
		// the seasonal verdict first.
		RequirementContext normal = RequirementTestFixtures.context().build();
		Requirements requirements = requirements(normal,
			RequirementTestFixtures.policy(TeleportationItem.NONE));

		assertEquals(RejectionReason.SEASONAL_WORLD,
			requirements.check(transport(TransportType.SEASONAL_TRANSPORTS).build()));
	}

	@Test
	public void seasonalTransportsObeyTheModeOnSeasonalWorlds()
	{
		LeagueModeState seasonalState = seasonal(LeagueRegion.VARLAMORE);
		RequirementContext seasonalAll = RequirementTestFixtures.context()
			.leagueModeState(seasonalState)
			.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.ALL, Map.of()))
			.build();
		RequirementContext seasonalNone = RequirementTestFixtures.context()
			.leagueModeState(seasonalState)
			.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.NONE, Map.of()))
			.build();
		Requirements all = requirements(seasonalAll,
			RequirementTestFixtures.policy(TeleportationItem.ALL));
		Requirements none = requirements(seasonalNone,
			RequirementTestFixtures.policy(TeleportationItem.NONE));

		// POH endpoints classify NEUTRAL, so the league gate passes on a
		// seasonal world and the mode dispatch is what gets exercised.
		assertEquals(RejectionReason.NONE,
			all.check(transport(TransportType.SEASONAL_TRANSPORTS).origin(POH).destination(POH).build()));
		assertEquals(RejectionReason.TELEPORT_MODE,
			none.check(transport(TransportType.SEASONAL_TRANSPORTS).origin(POH).destination(POH).build()));
	}

	@Test
	public void deadmanOnlyItemTransportsRejectOutsideDeadmanWorlds()
	{
		RequirementContext normal = RequirementTestFixtures.context().build();
		Transport trinket = transport(TransportType.TELEPORTATION_ITEM)
			.itemRequirements(items(item(DEADMAN_ONLY)))
			.build();

		// The bypass modes cannot rescue a mode-locked item — the deadman
		// check sits before the mode dispatch.
		for (TeleportationItem mode : new TeleportationItem[]{
			TeleportationItem.ALL, TeleportationItem.UNLOCKED, TeleportationItem.INVENTORY})
		{
			assertEquals(mode + " must not bypass the deadman gate",
				RejectionReason.DEADMAN_ITEM,
				requirements(normal, RequirementTestFixtures.policy(mode)).check(trinket));
		}
	}

	@Test
	public void deadmanWorldAdmitsDeadmanOnlyItems()
	{
		RequirementContext deadman = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.ALL, Map.of()))
			.leagueModeState(deadman()).build();
		Requirements requirements = requirements(deadman,
			RequirementTestFixtures.policy(TeleportationItem.ALL));

		Transport trinket = transport(TransportType.TELEPORTATION_ITEM)
			.itemRequirements(items(item(DEADMAN_ONLY)))
			.build();
		assertEquals(RejectionReason.NONE, requirements.check(trinket));
	}

	@Test
	public void deadmanCheckTreatsBranchVariationsAsAlternativesOfOneItem()
	{
		// One branch whose variation ids mix a deadman-only item with a normal
		// item keeps the transport usable: the row is not all-locked.
		RequirementContext normal = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.ALL, Map.of()))
			.build();
		Requirements requirements = requirements(normal,
			RequirementTestFixtures.policy(TeleportationItem.ALL));

		Transport mixedVariations = transport(TransportType.TELEPORTATION_ITEM)
			.itemRequirements(items(new ItemRequirement(new int[]{DEADMAN_ONLY, LAW_RUNE}, null, null, 1)))
			.build();
		assertEquals(RejectionReason.NONE, requirements.check(mixedVariations));
	}

	@Test
	public void deadmanCheckRejectsARequirementWhoseOnlyBranchIsDeadmanOnly()
	{
		// An AND-ed deadman-only requirement next to a normal one still marks
		// the transport mode-locked: the deadman branch has no alternative.
		RequirementContext normal = RequirementTestFixtures.context().build();
		Requirements requirements = requirements(normal,
			RequirementTestFixtures.policy(TeleportationItem.ALL));

		Transport anded = transport(TransportType.TELEPORTATION_ITEM)
			.itemRequirements(items(item(DEADMAN_ONLY), item(LAW_RUNE)))
			.build();
		assertEquals(RejectionReason.DEADMAN_ITEM, requirements.check(anded));
	}

	@Test
	public void everyTeleportationItemModeIsExercisedOnAConsumable()
	{
		// A consumable teleport the player carries. Modes that exclude
		// consumables reject at the mode seat; the deferring modes fall
		// through to item evaluation which the carried item satisfies.
		Map<TeleportationItem, RejectionReason> expected = new EnumMap<>(TeleportationItem.class);
		expected.put(TeleportationItem.NONE, RejectionReason.TELEPORT_MODE);
		expected.put(TeleportationItem.INVENTORY, RejectionReason.NONE);
		expected.put(TeleportationItem.INVENTORY_NON_CONSUMABLE, RejectionReason.TELEPORT_MODE);
		expected.put(TeleportationItem.INVENTORY_AND_BANK, RejectionReason.NONE);
		expected.put(TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE, RejectionReason.TELEPORT_MODE);
		expected.put(TeleportationItem.UNLOCKED, RejectionReason.NONE);
		expected.put(TeleportationItem.UNLOCKED_NON_CONSUMABLE, RejectionReason.TELEPORT_MODE);
		expected.put(TeleportationItem.ALL, RejectionReason.NONE);
		expected.put(TeleportationItem.ALL_NON_CONSUMABLE, RejectionReason.TELEPORT_MODE);
		assertEquals("the matrix must cover every mode",
			TeleportationItem.values().length, expected.size());
		Transport consumable = transport(TransportType.TELEPORTATION_ITEM)
			.isConsumable(true)
			.itemRequirements(items(item(LAW_RUNE)))
			.build();

		for (Map.Entry<TeleportationItem, RejectionReason> entry : expected.entrySet())
		{
			TeleportationItem mode = entry.getKey();
			RequirementContext context = RequirementTestFixtures.context()
				.eligibility(RequirementTestFixtures.eligibility(mode, Map.of(LAW_RUNE, 1)))
				.build();
			assertEquals(mode + " on a carried consumable",
				entry.getValue(),
				requirements(context, RequirementTestFixtures.policy(mode)).check(consumable));
		}
	}

	@Test
	public void everyTeleportationItemModeIsExercisedOnANonConsumable()
	{
		// A non-consumable teleport the player carries: only NONE rejects at
		// the mode seat; every other mode defers or bypasses.
		Map<TeleportationItem, RejectionReason> expected = new EnumMap<>(TeleportationItem.class);
		expected.put(TeleportationItem.NONE, RejectionReason.TELEPORT_MODE);
		expected.put(TeleportationItem.INVENTORY, RejectionReason.NONE);
		expected.put(TeleportationItem.INVENTORY_NON_CONSUMABLE, RejectionReason.NONE);
		expected.put(TeleportationItem.INVENTORY_AND_BANK, RejectionReason.NONE);
		expected.put(TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE, RejectionReason.NONE);
		expected.put(TeleportationItem.UNLOCKED, RejectionReason.NONE);
		expected.put(TeleportationItem.UNLOCKED_NON_CONSUMABLE, RejectionReason.NONE);
		expected.put(TeleportationItem.ALL, RejectionReason.NONE);
		expected.put(TeleportationItem.ALL_NON_CONSUMABLE, RejectionReason.NONE);
		Transport nonConsumable = transport(TransportType.TELEPORTATION_ITEM)
			.itemRequirements(items(item(LAW_RUNE)))
			.build();

		for (Map.Entry<TeleportationItem, RejectionReason> entry : expected.entrySet())
		{
			TeleportationItem mode = entry.getKey();
			RequirementContext context = RequirementTestFixtures.context()
				.eligibility(RequirementTestFixtures.eligibility(mode, Map.of(LAW_RUNE, 1)))
				.build();
			assertEquals(mode + " on a carried non-consumable",
				entry.getValue(),
				requirements(context, RequirementTestFixtures.policy(mode)).check(nonConsumable));
		}
	}

	@Test
	public void deferringModesHandTheVerdictToItemEvaluation()
	{
		Transport teleport = transport(TransportType.TELEPORTATION_ITEM)
			.itemRequirements(items(item(LAW_RUNE)))
			.build();

		// INVENTORY: carried pool lacks the rune and bank-path adds nothing.
		RequirementContext without = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.INVENTORY, Map.of()))
			.build();
		assertEquals(RejectionReason.ITEM_REQUIREMENT,
			requirements(without, RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(teleport));

		// INVENTORY_AND_BANK: the bank-path pool supplies what the player
		// does not carry — the transport stays usable from a bank.
		RequirementContext banked = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.bankedEligibility(
				TeleportationItem.INVENTORY_AND_BANK, Map.of(), Map.of(LAW_RUNE, 1)))
			.build();
		assertEquals(RejectionReason.NONE,
			requirements(banked, RequirementTestFixtures.policy(TeleportationItem.INVENTORY_AND_BANK)).check(teleport));

		// UNLOCKED and ALL bypass item evaluation entirely.
		for (TeleportationItem mode : new TeleportationItem[]{TeleportationItem.UNLOCKED, TeleportationItem.ALL})
		{
			RequirementContext bypass = RequirementTestFixtures.context()
				.eligibility(RequirementTestFixtures.eligibility(mode, Map.of()))
				.build();
			assertEquals(mode + " bypasses the item check",
				RejectionReason.NONE,
				requirements(bypass, RequirementTestFixtures.policy(mode)).check(teleport));
		}
	}

	@Test
	public void quetzalWhistleJoinsTheTeleportationItemFamily()
	{
		assertEquals(RejectionReason.TELEPORT_MODE,
			requirements(RequirementTestFixtures.context()
					.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.NONE, Map.of()))
					.build(),
				RequirementTestFixtures.policy(TeleportationItem.NONE))
				.check(transport(TransportType.QUETZAL_WHISTLE).build()));
		assertEquals(RejectionReason.NONE,
			requirements(RequirementTestFixtures.context()
					.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.ALL, Map.of()))
					.build(),
				RequirementTestFixtures.policy(TeleportationItem.ALL))
				.check(transport(TransportType.QUETZAL_WHISTLE).build()));
	}

	@Test
	public void nonTeleportFamilyTypesSkipTheGate()
	{
		RequirementContext context = RequirementTestFixtures.context().build();
		Requirements none = requirements(context,
			RequirementTestFixtures.policy(TeleportationItem.NONE));
		// NONE must not reject transports outside the teleport-item family.
		assertEquals(RejectionReason.NONE, none.check(transport(TransportType.BOAT).build()));
		assertEquals(RejectionReason.NONE, none.check(transport(TransportType.FAIRY_RING).build()));
		assertEquals(RejectionReason.NONE, none.check(transport(TransportType.TELEPORTATION_SPELL).build()));
	}

	// ------------------------------------------------------------------
	// Gate 7: respawn — the Lumbridge/Prifddinas landing is gated on the
	// declared respawn because both read the same all-zero varbit signature.
	// ------------------------------------------------------------------

	@Test
	public void respawnDestinationsFollowTheDeclaredRespawn()
	{
		Transport prifRespawn = transport(TransportType.TRANSPORT)
			.destination(PRIFDDINAS_RESPAWN).displayInfo("Respawn Teleport").build();
		Transport lumbridgeRespawn = transport(TransportType.TRANSPORT)
			.destination(LUMBRIDGE_RESPAWN).displayInfo("Respawn Teleport").build();

		Requirements prif = requirements(
			RequirementTestFixtures.context().respawnPrifddinas(true).build(),
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));
		Requirements notPrif = requirements(
			RequirementTestFixtures.context().respawnPrifddinas(false).build(),
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));

		assertEquals(RejectionReason.NONE, prif.check(prifRespawn));
		assertEquals(RejectionReason.RESPAWN_DECLARED, prif.check(lumbridgeRespawn));
		assertEquals(RejectionReason.NONE, notPrif.check(lumbridgeRespawn));
		assertEquals(RejectionReason.RESPAWN_DECLARED, notPrif.check(prifRespawn));
	}

	@Test
	public void nonRespawnDisplayInfoSkipsTheGate()
	{
		Requirements notPrif = requirements(
			RequirementTestFixtures.context().respawnPrifddinas(false).build(),
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));
		// Same Prifddinas tile, no Respawn marker — not a respawn row.
		Transport plain = transport(TransportType.TRANSPORT)
			.destination(PRIFDDINAS_RESPAWN).displayInfo("Teleport").build();
		assertEquals(RejectionReason.NONE, notPrif.check(plain));
		// A Respawn-marked row to some other landing is unaffected.
		Transport otherLanding = transport(TransportType.TRANSPORT)
			.destination(VARROCK).displayInfo("Respawn Teleport").build();
		assertEquals(RejectionReason.NONE, notPrif.check(otherLanding));
	}

	@Test
	public void respawnRunsBeforeTheUnlockGate()
	{
		Requirements notPrif = requirements(
			RequirementTestFixtures.context().respawnPrifddinas(false).build(),
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));
		Transport both = transport(TransportType.TRANSPORT)
			.destination(PRIFDDINAS_RESPAWN).displayInfo("Respawn Teleport")
			.itemRequirements(items(new ItemRequirement(List.of(unlockBranch(Unlock.XERICS_HONOUR)))))
			.build();
		assertEquals(RejectionReason.RESPAWN_DECLARED, notPrif.check(both));
	}

	// ------------------------------------------------------------------
	// Gate 8: unlock — pure-unlock requirements and the Honour box need a
	// declared unlock; item bypass modes must not skip them.
	// ------------------------------------------------------------------

	@Test
	public void pureUnlockRequirementsNeedTheDeclaredUnlock()
	{
		Transport unlockGated = transport(TransportType.TRANSPORT)
			.itemRequirements(items(new ItemRequirement(List.of(unlockBranch(Unlock.XERICS_HONOUR)))))
			.build();

		Requirements without = requirements(
			RequirementTestFixtures.context().build(),
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));
		assertEquals(RejectionReason.UNLOCK_GATE, without.check(unlockGated));

		RequirementContext unlocked = RequirementTestFixtures.context()
			.eligibility(new TransportEligibility(Map.of(), Map.of(), Map.of(), -1, Map.of(),
				false, TeleportationItem.INVENTORY, Integer.MAX_VALUE, Set.of(Unlock.XERICS_HONOUR)))
			.unlocks(Set.of(Unlock.XERICS_HONOUR))
			.build();
		assertEquals(RejectionReason.NONE,
			requirements(unlocked, RequirementTestFixtures.policy(TeleportationItem.INVENTORY))
				.check(unlockGated));
	}

	@Test
	public void honourTeleportBoxNeedsXericsHonour()
	{
		Transport honourBox = transport(TransportType.TELEPORTATION_BOX)
			.origin(POH)
			.displayInfo("5: Honour")
			.objectInfo("Honour Xeric's Talisman 33415")
			.build();

		Requirements without = requirements(
			RequirementTestFixtures.context().build(),
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));
		assertEquals(RejectionReason.UNLOCK_GATE, without.check(honourBox));

		RequirementContext unlocked = RequirementTestFixtures.context()
			.unlocks(Set.of(Unlock.XERICS_HONOUR))
			.build();
		assertEquals(RejectionReason.NONE,
			requirements(unlocked, RequirementTestFixtures.policy(TeleportationItem.INVENTORY))
				.check(honourBox));
	}

	@Test
	public void itemBypassModesCannotSkipTheUnlockGate()
	{
		// A pure-unlock requirement on a teleportation item: ALL would bypass
		// item evaluation, but the unlock gate runs earlier for every type.
		Transport unlockGated = transport(TransportType.TELEPORTATION_ITEM)
			.itemRequirements(items(new ItemRequirement(List.of(unlockBranch(Unlock.DRAGONTOOTH)))))
			.build();
		Requirements all = requirements(
			RequirementTestFixtures.context().build(),
			RequirementTestFixtures.policy(TeleportationItem.ALL));
		assertEquals(RejectionReason.UNLOCK_GATE, all.check(unlockGated));
	}

	@Test
	public void anUnlockAlternativeInsideAnOrGroupRelievesTheItemTerm()
	{
		// CANOE_AXE is an OR relief, not a gate: with the unlock declared the
		// transport needs no carried axe; without it the item term applies.
		Transport axeOrUnlock = transport(TransportType.CANOE)
			.itemRequirements(items(new ItemRequirement(List.of(
				branch(ItemID.RUNE_AXE), unlockBranch(Unlock.CANOE_AXE)))))
			.build();

		RequirementContext unlocked = RequirementTestFixtures.context()
			.eligibility(new TransportEligibility(Map.of(), Map.of(), Map.of(), -1, Map.of(),
				false, TeleportationItem.INVENTORY, Integer.MAX_VALUE, Set.of(Unlock.CANOE_AXE)))
			.unlocks(Set.of(Unlock.CANOE_AXE))
			.build();
		assertEquals(RejectionReason.NONE,
			requirements(unlocked, RequirementTestFixtures.policy(TeleportationItem.INVENTORY))
				.check(axeOrUnlock));

		RequirementContext noItemsNoUnlock = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.INVENTORY, Map.of()))
			.build();
		assertEquals(RejectionReason.ITEM_REQUIREMENT,
			requirements(noItemsNoUnlock, RequirementTestFixtures.policy(TeleportationItem.INVENTORY))
				.check(axeOrUnlock));
	}

	// ------------------------------------------------------------------
	// Gate 9: jewellery box tier and mounted items on teleportation boxes.
	// ------------------------------------------------------------------

	@Test
	public void jewelleryBoxTierFiltersTheBoxLevels()
	{
		Transport ornateBox = transport(TransportType.TELEPORTATION_BOX)
			.origin(POH).objectInfo("Teleport Menu Ornate Jewellery Box 37520").build();
		Transport fancyBox = transport(TransportType.TELEPORTATION_BOX)
			.origin(POH).objectInfo("Teleport Menu Fancy Jewellery Box 37501").build();
		Transport basicBox = transport(TransportType.TELEPORTATION_BOX)
			.origin(POH).objectInfo("Teleport Menu Basic Jewellery Box 37492").build();

		RequirementContext context = RequirementTestFixtures.context().build();
		for (JewelleryBoxTier tier : JewelleryBoxTier.values())
		{
			Requirements requirements = requirements(context,
				RequirementTestFixtures.policy()
					.pohJewelleryBoxTier(tier)
					.teleportationItemSetting(TeleportationItem.INVENTORY)
					.build());
			assertEquals("ornate under " + tier,
				tier == JewelleryBoxTier.ORNATE ? RejectionReason.NONE : RejectionReason.JEWELLERY_BOX_TIER,
				requirements.check(ornateBox));
			assertEquals("fancy under " + tier,
				(tier == JewelleryBoxTier.ORNATE || tier == JewelleryBoxTier.FANCY)
					? RejectionReason.NONE : RejectionReason.JEWELLERY_BOX_TIER,
				requirements.check(fancyBox));
			assertEquals("basic under " + tier,
				tier == JewelleryBoxTier.NONE ? RejectionReason.JEWELLERY_BOX_TIER : RejectionReason.NONE,
				requirements.check(basicBox));
		}
	}

	@Test
	public void teleportBoxWithoutObjectInfoFailsClosed()
	{
		Transport noInfo = transport(TransportType.TELEPORTATION_BOX).origin(POH).build();
		Requirements requirements = requirements(
			RequirementTestFixtures.context().build(),
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));
		assertEquals(RejectionReason.JEWELLERY_BOX_TIER, requirements.check(noInfo));
	}

	@Test
	public void ornateBoxSupersedesTheMountedGlory()
	{
		Transport mountedGlory = transport(TransportType.TELEPORTATION_BOX)
			.origin(POH).objectInfo("Edgeville Amulet of Glory 13523").build();
		RequirementContext context = RequirementTestFixtures.context().build();

		// Ornate covers every glory destination, so the mounted glory is
		// filtered regardless of the mounted-item toggle.
		assertEquals(RejectionReason.JEWELLERY_BOX_TIER,
			requirements(context, RequirementTestFixtures.policy()
				.pohJewelleryBoxTier(JewelleryBoxTier.ORNATE)
				.teleportationItemSetting(TeleportationItem.INVENTORY)
				.build()).check(mountedGlory));
	}

	@Test
	public void mountedItemsFollowTheEnabledSet()
	{
		Transport mountedGlory = transport(TransportType.TELEPORTATION_BOX)
			.origin(POH).objectInfo("Edgeville Amulet of Glory 13523").build();
		RequirementContext context = RequirementTestFixtures.context().build();

		Set<PohMountedItem> withoutGlory = EnumSet.allOf(PohMountedItem.class);
		withoutGlory.remove(PohMountedItem.GLORY);
		assertEquals(RejectionReason.JEWELLERY_BOX_TIER,
			requirements(context, RequirementTestFixtures.policy()
				.pohJewelleryBoxTier(JewelleryBoxTier.BASIC)
				.enabledPohMountedItems(withoutGlory)
				.teleportationItemSetting(TeleportationItem.INVENTORY)
				.build()).check(mountedGlory));
		assertEquals(RejectionReason.NONE,
			requirements(context, RequirementTestFixtures.policy()
				.pohJewelleryBoxTier(JewelleryBoxTier.BASIC)
				.teleportationItemSetting(TeleportationItem.INVENTORY)
				.build()).check(mountedGlory));
	}

	// ------------------------------------------------------------------
	// Gate 10: skill levels — including the Max sentinel and the trailing
	// total/combat/quest-point slots.
	// ------------------------------------------------------------------

	@Test
	public void skillRequirementsCompareAgainstBoostedLevels()
	{
		Transport ninetyNineCooking = transport(TransportType.TRANSPORT)
			.skillLevels("99 Cooking").build();
		assertEquals("the TSV grammar must place Cooking", 99,
			ninetyNineCooking.getSkillLevels()[Skill.COOKING.ordinal()]);

		assertEquals(RejectionReason.NONE,
			requirements(RequirementTestFixtures.context().build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(ninetyNineCooking));

		int[] nearlyMaxed = RequirementTestFixtures.maxedSkills();
		nearlyMaxed[Skill.COOKING.ordinal()] = 50;
		RequirementContext low = RequirementTestFixtures.context()
			.boostedSkillLevelsAndMore(nearlyMaxed).build();
		assertEquals(RejectionReason.SKILL_LEVEL,
			requirements(low, RequirementTestFixtures.policy(TeleportationItem.INVENTORY))
				.check(ninetyNineCooking));
	}

	@Test
	public void maxSkillRequirementResolvesPerSlot()
	{
		Transport maxed = transport(TransportType.TRANSPORT).skillLevels("Max Cooking").build();
		assertEquals(RejectionReason.NONE,
			requirements(RequirementTestFixtures.context().build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(maxed));

		int[] below = RequirementTestFixtures.maxedSkills();
		below[Skill.COOKING.ordinal()] = 98;
		RequirementContext low = RequirementTestFixtures.context()
			.boostedSkillLevelsAndMore(below).build();
		assertEquals(RejectionReason.SKILL_LEVEL,
			requirements(low, RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(maxed));
	}

	@Test
	public void totalAndQuestPointSlotsAreChecked()
	{
		int[] required = new int[Skill.values().length + 3];
		required[Skill.values().length] = 2000;      // total level
		required[Skill.values().length + 2] = 150;   // quest points
		Transport gated = transport(TransportType.TRANSPORT).startSkillLevels(required).build();

		assertEquals(RejectionReason.NONE,
			requirements(RequirementTestFixtures.context().build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(gated));

		int[] low = RequirementTestFixtures.maxedSkills();
		low[Skill.values().length] = 1999;
		RequirementContext lowTotal = RequirementTestFixtures.context()
			.boostedSkillLevelsAndMore(low).build();
		assertEquals(RejectionReason.SKILL_LEVEL,
			requirements(lowTotal, RequirementTestFixtures.policy(TeleportationItem.INVENTORY))
				.check(gated));
	}

	@Test
	public void skillGateRunsBeforeQuests()
	{
		Transport both = transport(TransportType.TRANSPORT)
			.skillLevels("99 Cooking")
			.quests(Set.of(Quest.TREE_GNOME_VILLAGE))
			.build();
		int[] low = RequirementTestFixtures.maxedSkills();
		low[Skill.COOKING.ordinal()] = 50;
		RequirementContext context = RequirementTestFixtures.context()
			.boostedSkillLevelsAndMore(low).build();
		assertEquals(RejectionReason.SKILL_LEVEL,
			requirements(context, RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(both));
	}

	// ------------------------------------------------------------------
	// Gate 11: quests.
	// ------------------------------------------------------------------

	@Test
	public void unfinishedQuestRejectsTheTransport()
	{
		Transport questLocked = transport(TransportType.TRANSPORT)
			.quests(Set.of(Quest.TREE_GNOME_VILLAGE)).build();

		// No captured state means NOT_STARTED — quests fail closed.
		assertEquals(RejectionReason.QUEST,
			requirements(RequirementTestFixtures.context().build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(questLocked));
		assertEquals(RejectionReason.QUEST,
			requirements(RequirementTestFixtures.context()
					.questStates(Map.of(Quest.TREE_GNOME_VILLAGE, QuestState.IN_PROGRESS)).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(questLocked));
		assertEquals(RejectionReason.NONE,
			requirements(RequirementTestFixtures.context()
					.questStates(Map.of(Quest.TREE_GNOME_VILLAGE, QuestState.FINISHED)).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(questLocked));
	}

	@Test
	public void everyRequiredQuestMustBeFinished()
	{
		Transport twoQuests = transport(TransportType.TRANSPORT)
			.quests(Set.of(Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE)).build();
		RequirementContext oneDone = RequirementTestFixtures.context()
			.questStates(Map.of(Quest.TREE_GNOME_VILLAGE, QuestState.FINISHED)).build();
		assertEquals(RejectionReason.QUEST,
			requirements(oneDone, RequirementTestFixtures.policy(TeleportationItem.INVENTORY))
				.check(twoQuests));
	}

	// ------------------------------------------------------------------
	// Gates 12-13: varbits and varplayers through the hooks seam.
	// ------------------------------------------------------------------

	@Test
	public void varbitRequirementEvaluatesCapturedValues()
	{
		Transport gated = transport(TransportType.TRANSPORT).varbits("4481=1").build();

		assertEquals(RejectionReason.NONE,
			requirements(RequirementTestFixtures.context()
					.varbitValues(Map.of(4481, 1)).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(gated));
		assertEquals(RejectionReason.VARBIT,
			requirements(RequirementTestFixtures.context()
					.varbitValues(Map.of(4481, 0)).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(gated));
		// A varbit absent from the captured map fails closed.
		assertEquals(RejectionReason.VARBIT,
			requirements(RequirementTestFixtures.context().build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(gated));
	}

	@Test
	public void varplayerRequirementUsesStrictGreaterThan()
	{
		// The bank.tsv Fortis Colosseum shape: varplayer 4130 above 1999.
		Transport gated = transport(TransportType.TRANSPORT).varPlayers("4130>1999").build();

		assertEquals(RejectionReason.VARPLAYER,
			requirements(RequirementTestFixtures.context()
					.varPlayerValues(Map.of(4130, 1999)).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(gated));
		assertEquals(RejectionReason.NONE,
			requirements(RequirementTestFixtures.context()
					.varPlayerValues(Map.of(4130, 2000)).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(gated));
		assertEquals(RejectionReason.VARPLAYER,
			requirements(RequirementTestFixtures.context().build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(gated));
	}

	@Test
	public void cooldownMinuteEdgeFailsClosed()
	{
		// @ is the cooldown-minutes check: the requirement passes only when
		// strictly more than the cooldown has elapsed since the stored use.
		Transport cooldown = transport(TransportType.TRANSPORT).varPlayers("3095@10").build();

		// evaluationTime 1000, last use 990 → exactly 10 elapsed → not enough.
		assertEquals(RejectionReason.VARPLAYER,
			requirements(RequirementTestFixtures.context()
					.evaluationTimeMinutes(1000)
					.varPlayerValues(Map.of(3095, 990)).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(cooldown));
		// 989 → 11 elapsed → passes.
		assertEquals(RejectionReason.NONE,
			requirements(RequirementTestFixtures.context()
					.evaluationTimeMinutes(1000)
					.varPlayerValues(Map.of(3095, 989)).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(cooldown));
		// Varbit cooldowns run through the varbit hook instead.
		Transport varbitCooldown = transport(TransportType.TRANSPORT).varbits("9999@60").build();
		assertEquals(RejectionReason.VARBIT,
			requirements(RequirementTestFixtures.context()
					.evaluationTimeMinutes(100)
					.varbitValues(Map.of(9999, 40)).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(varbitCooldown));
		assertEquals(RejectionReason.NONE,
			requirements(RequirementTestFixtures.context()
					.evaluationTimeMinutes(100)
					.varbitValues(Map.of(9999, 39)).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(varbitCooldown));
	}

	@Test
	public void varbitRunsBeforeVarplayer()
	{
		Transport both = transport(TransportType.TRANSPORT)
			.varbits("4481=1").varPlayers("4130>1999").build();
		RequirementContext failing = RequirementTestFixtures.context()
			.varbitValues(Map.of(4481, 0)).varPlayerValues(Map.of(4130, 0)).build();
		assertEquals(RejectionReason.VARBIT,
			requirements(failing, RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(both));
	}

	// ------------------------------------------------------------------
	// Gate 14: planted spirit trees — transports touching a patch tile are
	// admitted only when that patch reports a travelable tree.
	// ------------------------------------------------------------------

	@Test
	public void plantedSpiritTreeEndpointsFollowTheAvailabilitySet()
	{
		Transport toPatch = transport(TransportType.SPIRIT_TREE)
			.destination(PORT_SARIM_PATCH).build();
		Transport fromPatch = transport(TransportType.SPIRIT_TREE)
			.origin(PORT_SARIM_PATCH).build();

		for (Transport bound : new Transport[]{toPatch, fromPatch})
		{
			assertEquals(RejectionReason.NONE,
				requirements(RequirementTestFixtures.context()
						.availableSpiritTrees(Set.of("Port Sarim")).build(),
					RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(bound));
			assertEquals(RejectionReason.PLANTED_SPIRIT_TREE,
				requirements(RequirementTestFixtures.context()
						.availableSpiritTrees(Set.of()).build(),
					RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(bound));
			// A different planted patch does not satisfy this one.
			assertEquals(RejectionReason.PLANTED_SPIRIT_TREE,
				requirements(RequirementTestFixtures.context()
						.availableSpiritTrees(Set.of("Brimhaven")).build(),
					RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(bound));
		}
	}

	@Test
	public void unresolvedSpiritTreeAvailabilityFailsClosed()
	{
		// availableSpiritTrees == null means no observation has resolved yet;
		// patch-touching transports stay unusable rather than routing through
		// a tree that may not be planted.
		Transport toPatch = transport(TransportType.SPIRIT_TREE)
			.destination(PORT_SARIM_PATCH).build();
		assertEquals(RejectionReason.PLANTED_SPIRIT_TREE,
			requirements(RequirementTestFixtures.context().build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(toPatch));
	}

	@Test
	public void spiritTreeTransportsAwayFromPatchesAreUnaffected()
	{
		// Regular network tiles are not patch tiles — the gate never applies,
		// whatever the availability set holds.
		Transport normal = transport(TransportType.SPIRIT_TREE).build();
		assertEquals(RejectionReason.NONE,
			requirements(RequirementTestFixtures.context()
					.availableSpiritTrees(Set.of()).build(),
				RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(normal));
	}

	@Test
	public void plantedSpiritTreeRunsBeforeItemEvaluation()
	{
		// Unavailable patch plus a missing item: the planted-tree gate is
		// earlier in the chain than the eligibility check.
		Transport both = transport(TransportType.TELEPORTATION_ITEM)
			.destination(PORT_SARIM_PATCH)
			.itemRequirements(items(item(LAW_RUNE)))
			.build();
		RequirementContext context = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.INVENTORY, Map.of()))
			.availableSpiritTrees(Set.of())
			.build();
		assertEquals(RejectionReason.PLANTED_SPIRIT_TREE,
			requirements(context, RequirementTestFixtures.policy(TeleportationItem.INVENTORY)).check(both));
	}

	// ------------------------------------------------------------------
	// Gate 15: item requirements through the eligibility snapshot.
	// ------------------------------------------------------------------

	@Test
	public void nullAndEmptyItemRequirementsPass()
	{
		Requirements requirements = requirements(
			RequirementTestFixtures.context().build(),
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));

		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.BOAT).build()));
		assertEquals(RejectionReason.NONE,
			requirements.check(transport(TransportType.BOAT)
				.itemRequirements(new TransportItems(List.of())).build()));
	}

	@Test
	public void anItemRequirementNeedsAtLeastOneBranch()
	{
		assertThrows(IllegalArgumentException.class,
			() -> new ItemRequirement(List.of()));
	}

	@Test
	public void orBranchesAreAlternativesAndRequirementsAreConjunctive()
	{
		// OR within a requirement: either alternative satisfies it.
		Transport orTransport = transport(TransportType.BOAT)
			.itemRequirements(items(new ItemRequirement(List.of(branch(LAW_RUNE), branch(AIR_RUNE)))))
			.build();
		// AND across requirements: every requirement must hold.
		Transport andTransport = transport(TransportType.BOAT)
			.itemRequirements(items(item(LAW_RUNE), item(AIR_RUNE)))
			.build();

		for (Map<Integer, Integer> carried : List.of(
			Map.of(LAW_RUNE, 1), Map.of(AIR_RUNE, 1), Map.<Integer, Integer>of()))
		{
			RequirementContext context = RequirementTestFixtures.context()
				.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.NONE, carried))
				.build();
			Requirements requirements = requirements(context,
				RequirementTestFixtures.policy(TeleportationItem.NONE));
			boolean hasEither = carried.containsKey(LAW_RUNE) || carried.containsKey(AIR_RUNE);
			boolean hasBoth = carried.containsKey(LAW_RUNE) && carried.containsKey(AIR_RUNE);
			assertEquals("OR over " + carried,
				hasEither ? RejectionReason.NONE : RejectionReason.ITEM_REQUIREMENT,
				requirements.check(orTransport));
			assertEquals("AND over " + carried,
				hasBoth ? RejectionReason.NONE : RejectionReason.ITEM_REQUIREMENT,
				requirements.check(andTransport));
		}
	}

	@Test
	public void itemVariationsInsideOneBranchAreAlternatives()
	{
		// Variation ids on a single branch are the same item: either works.
		Transport transport = transport(TransportType.BOAT)
			.itemRequirements(items(new ItemRequirement(new int[]{LAW_RUNE, AIR_RUNE}, null, null, 1)))
			.build();
		RequirementContext context = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.eligibility(TeleportationItem.NONE, Map.of(AIR_RUNE, 3)))
			.build();
		assertEquals(RejectionReason.NONE,
			requirements(context, RequirementTestFixtures.policy(TeleportationItem.NONE)).check(transport));
	}

	@Test
	public void bankPathPoolSatisfiesWhatCarriedLacks()
	{
		Transport transport = transport(TransportType.BOAT)
			.itemRequirements(items(item(LAW_RUNE))).build();
		RequirementContext context = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.bankedEligibility(
				TeleportationItem.NONE, Map.of(), Map.of(LAW_RUNE, 1)))
			.build();
		assertEquals(RejectionReason.NONE,
			requirements(context, RequirementTestFixtures.policy(TeleportationItem.NONE)).check(transport));
	}

	@Test
	public void currencyRequirementsRespectTheSpendThreshold()
	{
		// A boat costing 20k: carried 25k suffices with an unbounded
		// threshold, but a 5k spend cap blocks the payment.
		Transport fare = transport(TransportType.BOAT)
			.itemRequirements(items(item(COINS, 20_000))).build();

		RequirementContext unbounded = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.eligibility(
				TeleportationItem.NONE, Map.of(COINS, 25_000)))
			.build();
		assertEquals(RejectionReason.NONE,
			requirements(unbounded, RequirementTestFixtures.policy(TeleportationItem.NONE)).check(fare));

		RequirementContext capped = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.eligibility(
				TeleportationItem.NONE, Map.of(COINS, 25_000), 5_000))
			.build();
		assertEquals(RejectionReason.ITEM_REQUIREMENT,
			requirements(capped, RequirementTestFixtures.policy(TeleportationItem.NONE)).check(fare));
	}

	@Test
	public void aBranchWithoutItemIdsIsNeverSatisfiedByItems()
	{
		// A non-unlock branch carrying no item ids contributes nothing —
		// neither items nor unlocks can satisfy it, so the requirement fails.
		Transport transport = transport(TransportType.BOAT)
			.itemRequirements(items(new ItemRequirement(List.of(
				new ItemRequirement.Branch(null, null, null, 1)))))
			.build();
		RequirementContext context = RequirementTestFixtures.context()
			.eligibility(RequirementTestFixtures.eligibility(
				TeleportationItem.NONE, Map.of(LAW_RUNE, 99)))
			.build();
		assertEquals(RejectionReason.ITEM_REQUIREMENT,
			requirements(context, RequirementTestFixtures.policy(TeleportationItem.NONE)).check(transport));
	}

	// ------------------------------------------------------------------
	// Cross-gate: the chain reports the first rejecting gate, and usable()
	// is the boolean view of the same verdict.
	// ------------------------------------------------------------------

	@Test
	public void theChainReportsTheEarliestFailingGate()
	{
		// Quest beats varbit; varplayer beats planted spirit tree.
		Transport questVarbit = transport(TransportType.TRANSPORT)
			.quests(Set.of(Quest.TREE_GNOME_VILLAGE))
			.varbits("4481=1").build();
		RequirementContext failing = RequirementTestFixtures.context()
			.varbitValues(Map.of(4481, 0)).build();
		assertEquals(RejectionReason.QUEST,
			requirements(failing, RequirementTestFixtures.policy(TeleportationItem.INVENTORY))
				.check(questVarbit));

		Transport varplayerTree = transport(TransportType.SPIRIT_TREE)
			.destination(PORT_SARIM_PATCH)
			.varPlayers("4130>1999").build();
		RequirementContext treeContext = RequirementTestFixtures.context()
			.varPlayerValues(Map.of(4130, 0))
			.availableSpiritTrees(Set.of())
			.build();
		assertEquals(RejectionReason.VARPLAYER,
			requirements(treeContext, RequirementTestFixtures.policy(TeleportationItem.INVENTORY))
				.check(varplayerTree));
	}

	@Test
	public void usableMirrorsTheCheckVerdict()
	{
		RequirementContext context = RequirementTestFixtures.context().build();
		Requirements requirements = requirements(context,
			RequirementTestFixtures.policy(TeleportationItem.INVENTORY));

		Transport plain = transport(TransportType.BOAT).build();
		Transport gated = transport(TransportType.BOAT)
			.quests(Set.of(Quest.BONE_VOYAGE)).build();
		assertTrue(requirements.usable(plain));
		assertFalse(requirements.usable(gated));
		assertEquals(RejectionReason.NONE, requirements.check(plain));
		assertEquals(RejectionReason.QUEST, requirements.check(gated));
	}
}
