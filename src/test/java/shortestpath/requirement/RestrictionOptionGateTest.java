package shortestpath.requirement;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.poh.PohMountedItem;
import shortestpath.poh.PohNexusPortal;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Adversarial coverage of the destination-scoped option-block gate in
 * {@link Requirements#check}: a {@code rep@dest} record blocks one
 * destination option of one teleport family. A transport row is dropped
 * only when EVERY branch that could satisfy it resolves to a blocked
 * (representative, destination) pair — a sibling destination of the same
 * family, a branch from another family, a non-family item or an unlock
 * alternative all keep the transport routable. The gate must compose with
 * every teleportation-item mode and must never resurrect a NONE-mode
 * rejection.
 *
 * <p>The fixture ids are real family members: {@code 13121} heads the
 * Ardougne-cloaks family (members 13121-13124, 20760; destinations
 * Kandarin Monastery and Ardougne Farm) and {@code 3853} heads the
 * Games-necklace family — the two give genuine cross-family branch cases
 * against the live {@code teleportation_items.tsv} serving rows.
 */
public class RestrictionOptionGateTest
{
	private static final int MONASTERY = WorldPointUtil.packWorldPoint(2607, 3221, 0);
	private static final int FARM = WorldPointUtil.packWorldPoint(2665, 3374, 0);
	private static final int CLOAK_REP = 13121;
	private static final int CLOAK_MEMBER = 13122;
	private static final int GAMES_REP = 3853;
	private static final int GAMES_MEMBER = 3855;
	private static final int OTHER_REP = 9013;
	private static final int XERICS_MEMBER = 13393;
	private static final int COINS = 995;

	private static Transport transportTo(int packedDest, String items)
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_ITEM)
			.destination(packedDest)
			.itemRequirements(items)
			.build();
	}

	private static RoutingPolicy policy(TeleportationItem mode,
		Map<Integer, Set<Integer>> optionBlocks)
	{
		return new RoutingPolicy(EnumSet.allOf(TransportType.class), mode,
			true, true, true, true, EnumSet.allOf(PohNexusPortal.class),
			EnumSet.allOf(PohMountedItem.class), JewelleryBoxTier.ORNATE,
			Integer.MAX_VALUE, true, Set.of(), Map.of(), optionBlocks);
	}

	private static RejectionReason check(TeleportationItem mode,
		Map<Integer, Set<Integer>> optionBlocks, Map<Integer, Integer> carried,
		Transport transport)
	{
		RequirementContext context = RequirementTestFixtures.context(
			RequirementTestFixtures.eligibility(mode, carried));
		return RequirementTestFixtures.requirements(context,
			policy(mode, optionBlocks)).check(transport);
	}

	/**
	 * The row serving the blocked destination dies: every OR branch maps to
	 * the cloak family's representative and that representative is blocked
	 * at the farm destination.
	 */
	@Test
	public void optionBlockRejectsWhenEveryBranchResolvesToTheBlockedDestination()
	{
		Transport farm = transportTo(FARM, CLOAK_REP + "=1||" + CLOAK_MEMBER + "=1");

		Assert.assertEquals(RejectionReason.BLOCKED_ITEM,
			check(TeleportationItem.ALL, Map.of(CLOAK_REP, Set.of(FARM)), Map.of(), farm));
	}

	/**
	 * The sibling destination of the same family is untouched: blocking the
	 * farm option must never kill the monastery row.
	 */
	@Test
	public void siblingDestinationOfTheSameFamilyStillRoutes()
	{
		Transport monastery = transportTo(MONASTERY, CLOAK_REP + "=1||" + CLOAK_MEMBER + "=1");

		Assert.assertEquals(RejectionReason.NONE,
			check(TeleportationItem.ALL, Map.of(CLOAK_REP, Set.of(FARM)), Map.of(), monastery));
	}

	/**
	 * Per-item rows die too: a row whose Items cell lists only the blocked
	 * family's ids has no surviving branch at the blocked destination.
	 */
	@Test
	public void perItemRowsAtTheBlockedDestinationDieAsWell()
	{
		Transport perItem = transportTo(FARM, CLOAK_MEMBER + "=1");

		Assert.assertEquals(RejectionReason.BLOCKED_ITEM,
			check(TeleportationItem.ALL, Map.of(CLOAK_REP, Set.of(FARM)), Map.of(), perItem));
	}

	/**
	 * A non-family id in an OR branch is never option-blocked — the coins
	 * alternative keeps the row routable.
	 */
	@Test
	public void nonFamilyItemBranchIsNeverOptionBlocked()
	{
		Transport farm = transportTo(FARM, CLOAK_MEMBER + "=1|" + COINS + "=1");

		Assert.assertEquals(RejectionReason.NONE,
			check(TeleportationItem.ALL, Map.of(CLOAK_REP, Set.of(FARM)), Map.of(), farm));
	}

	/**
	 * Cross-family OR rows are blocked independently: blocking only the
	 * cloak representative drops the cloak branch but the games-necklace
	 * branch still satisfies; blocking both kills the row.
	 */
	@Test
	public void crossFamilyBranchesAreBlockedIndependently()
	{
		Transport farm = transportTo(FARM, CLOAK_MEMBER + "=1|" + GAMES_MEMBER + "=1");

		Assert.assertEquals(RejectionReason.NONE,
			check(TeleportationItem.ALL, Map.of(CLOAK_REP, Set.of(FARM)), Map.of(), farm));
		Assert.assertEquals(RejectionReason.BLOCKED_ITEM,
			check(TeleportationItem.ALL,
				Map.of(CLOAK_REP, Set.of(FARM), GAMES_REP, Set.of(FARM)), Map.of(), farm));
	}

	/**
	 * An unlock alternative carries no item ids, so an option block can
	 * never remove it — the Xeric's-talisman row shape.
	 */
	@Test
	public void unlockBranchSurvivesAnOptionBlock()
	{
		Transport farm = transportTo(FARM, XERICS_MEMBER + "=1|UNLOCK_XERICS_HONOUR=1");

		Assert.assertEquals(RejectionReason.NONE,
			check(TeleportationItem.ALL, Map.of(OTHER_REP, Set.of(FARM)), Map.of(), farm));
	}

	/**
	 * The gate composes with every teleportation-item mode: a transport
	 * serving only the blocked destination is rejected under all nine —
	 * under inventory modes the item is carried so the option block is the
	 * sole cause; under NONE the block still rejects (it never needs the
	 * mode's TELEPORT_MODE verdict).
	 */
	@Test
	public void everyTeleportationItemModeAppliesTheOptionBlock()
	{
		Transport farm = transportTo(FARM, CLOAK_REP + "=1||" + CLOAK_MEMBER + "=1");
		Map<Integer, Set<Integer>> blocks = Map.of(CLOAK_REP, Set.of(FARM));
		Map<Integer, Integer> carried = Map.of(CLOAK_REP, 1);

		for (TeleportationItem mode : TeleportationItem.values())
		{
			RejectionReason reason = check(mode, blocks, carried, farm);
			Assert.assertEquals("mode " + mode + " must reject the option-blocked row",
				RejectionReason.BLOCKED_ITEM, reason);
		}
	}

	/**
	 * The NONE-mode rejection stands on its own and the option logic can
	 * never resurrect it: without a block the verdict is TELEPORT_MODE;
	 * with one it is BLOCKED_ITEM — rejected either way.
	 */
	@Test
	public void noneModeRejectionIsNotResurrectedByOptionLogic()
	{
		Transport farm = transportTo(FARM, CLOAK_REP + "=1");

		Assert.assertEquals(RejectionReason.TELEPORT_MODE,
			check(TeleportationItem.NONE, Map.of(), Map.of(), farm));
		RejectionReason blocked = check(TeleportationItem.NONE,
			Map.of(CLOAK_REP, Set.of(FARM)), Map.of(), farm);
		Assert.assertNotEquals(RejectionReason.NONE, blocked);
	}

	/**
	 * A transport with no Items requirement has no branches the option gate
	 * could kill.
	 */
	@Test
	public void transportsWithoutItemRequirementsAreUnaffected()
	{
		Transport noItems = new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_ITEM)
			.destination(FARM)
			.build();

		Assert.assertEquals(RejectionReason.NONE,
			check(TeleportationItem.ALL, Map.of(CLOAK_REP, Set.of(FARM)), Map.of(), noItems));
	}

	/**
	 * Staff (and offhand) substitute arrays do not participate in option
	 * blocking — a requirement satisfiable only through a staff id keeps
	 * its branch even when that id belongs to a blocked family.
	 */
	@Test
	public void staffAndOffhandSubstitutesAreNotOptionBlocked()
	{
		ItemRequirement staffOnly = new ItemRequirement(List.of(
			ItemRequirement.Branch.item(null, new int[]{CLOAK_REP}, null, 1)));
		Transport transport = new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_ITEM)
			.destination(FARM)
			.itemRequirements(new TransportItems(List.of(staffOnly)))
			.build();

		Assert.assertEquals(RejectionReason.NONE,
			check(TeleportationItem.ALL, Map.of(CLOAK_REP, Set.of(FARM)), Map.of(), transport));
	}

	/**
	 * The policy's option map is deep-frozen: mutating the source map or
	 * its inner sets after construction cannot move the snapshot, and the
	 * handed-out collections reject writes.
	 */
	@Test
	public void policyOptionMapIsDeeplyImmutable()
	{
		Set<Integer> dests = new HashSet<>(Set.of(FARM));
		Map<Integer, Set<Integer>> blocks = new HashMap<>();
		blocks.put(CLOAK_REP, dests);

		RoutingPolicy policy = policy(TeleportationItem.ALL, blocks);

		blocks.put(GAMES_REP, Set.of(MONASTERY));
		dests.add(MONASTERY);
		Assert.assertEquals(Map.of(CLOAK_REP, Set.of(FARM)), policy.blockedOptionDests());
		Assert.assertThrows(UnsupportedOperationException.class,
			() -> policy.blockedOptionDests().get(CLOAK_REP).add(1));
		Assert.assertThrows(UnsupportedOperationException.class,
			() -> policy.blockedOptionDests().put(42, Set.of(1)));
	}

	/**
	 * End-to-end: the CSV record flows through the parser into the policy
	 * and rejects exactly the blocked destination — the sibling routes.
	 */
	@Test
	public void csvOptionRecordFlowsIntoTheGate()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();
		Map<Integer, Set<Integer>> options = new HashMap<>();
		TeleportRestriction.parseBlocked(CLOAK_REP + "@" + FARM, blocked, thresholds, options);

		Assert.assertEquals(RejectionReason.BLOCKED_ITEM,
			check(TeleportationItem.ALL, options, Map.of(), transportTo(FARM, CLOAK_REP + "=1")));
		Assert.assertEquals(RejectionReason.NONE,
			check(TeleportationItem.ALL, options, Map.of(),
				transportTo(MONASTERY, CLOAK_REP + "=1")));
	}

	/**
	 * The empty-block fast path: no rep@dest records means no transport is
	 * ever option-blocked.
	 */
	@Test
	public void emptyOptionMapNeverBlocks()
	{
		Assert.assertEquals(RejectionReason.NONE,
			check(TeleportationItem.ALL, Map.of(), Map.of(), transportTo(FARM, CLOAK_REP + "=1")));
	}
}
