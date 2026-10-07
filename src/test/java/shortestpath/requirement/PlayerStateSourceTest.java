package shortestpath.requirement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import shortestpath.transport.parser.VarRequirementParser;

/**
 * The capture-seam contract: {@link RequirementContext#capture} reads the
 * player only through {@link PlayerStateSource}, so a map-backed source
 * exercises the whole path — var and quest tables, the eligibility
 * snapshot, sailing and league state — with no {@code Client} anywhere.
 * The production source's loud off-thread contract is pinned against a
 * mocked {@code Client} (the only client mock on this path): every getter
 * must throw, and {@code isOnClientThread} must answer without throwing.
 */
public class PlayerStateSourceTest
{
	private static final TeleportationItem MODE = TeleportationItem.INVENTORY;
	private static final int FALADOR = WorldPointUtil.packWorldPoint(2965, 3378, 0);
	private static final int VARROCK = WorldPointUtil.packWorldPoint(3213, 3424, 0);
	private static final int POH = WorldPointUtil.packWorldPoint(1858, 7051, 0);
	private static final int BANK_TILE = WorldPointUtil.packWorldPoint(1804, 9501, 0);

	private static ItemContainer container(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		return container;
	}

	// ------------------------------------------------------------------
	// RequirementContext.capture end-to-end over the map-backed source.
	// ------------------------------------------------------------------

	@Test
	public void capturePopulatesTheContextWithoutAClient()
	{
		MapPlayerStateSource source = new MapPlayerStateSource()
			.varbit(1234, 7)
			.varp(4130, 2000)
			.varp(VarPlayerID.QP, 42)
			.boostedSkillLevel(Skill.COOKING, 99)
			.totalLevel(1500)
			.maximumQuestPoints(200)
			.itemContainer(InventoryID.INV, container(new Item(ItemID.LAWRUNE, 5)));

		TestRequirementHooks hooks = new TestRequirementHooks()
			.questState(QuestState.FINISHED);

		Transport gated = new Transport.TransportBuilder()
			.type(TransportType.TRANSPORT)
			.origin(FALADOR).destination(VARROCK)
			.varbits("1234=7")
			.quests(Set.of(Quest.TREE_GNOME_VILLAGE))
			.itemRequirements(new TransportItems(List.of(
				new ItemRequirement(new int[]{ItemID.LAWRUNE}, null, null, 1))))
			.build();
		DestinationRequirements bankGated = RequirementTestFixtures.bankRequirements(
			null, Set.of(Quest.BONE_VOYAGE), null,
			VarRequirementParser.forVarPlayers().parse("4130>1999"));

		RequirementContext context = RequirementContext.capture(source, hooks,
			RequirementTestFixtures.policy(MODE), 0L,
			List.of(gated), Map.of(BANK_TILE, bankGated), null,
			Set.of(), false, new LeagueModeState(), null);

		// Round-trip: the declared varbit, varplayer and quests landed in the
		// snapshot the gates read.
		assertEquals(Integer.valueOf(7), context.getVarbitValues().get(1234));
		assertEquals(Integer.valueOf(2000), context.getVarPlayerValues().get(4130));
		assertEquals(QuestState.FINISHED, context.getQuestStates().get(Quest.TREE_GNOME_VILLAGE));
		assertEquals(QuestState.FINISHED, context.getQuestStates().get(Quest.BONE_VOYAGE));
		assertEquals(99, context.getBoostedSkillLevelsAndMore()[Skill.COOKING.ordinal()]);
		assertEquals(1500, context.getBoostedSkillLevelsAndMore()[Skill.values().length]);
		assertEquals(42, context.getBoostedSkillLevelsAndMore()[Skill.values().length + 2]);
		assertEquals(200, context.getCurrentMaxQuestPoints());
		assertFalse(context.isOnSailingBoat());

		// The verdicts prove the captured facts reached the gates: the varbit,
		// the hook-fed quest states, and the carried item through the
		// eligibility snapshot all evaluate satisfied.
		Requirements requirements = new Requirements(context,
			RequirementTestFixtures.policy(MODE), new TestRequirementHooks());
		assertEquals(RejectionReason.NONE, requirements.check(gated));
		assertTrue(requirements.satisfied(bankGated));

		// A carried-item shortfall is equally visible: the eligibility
		// snapshot only holds what the containers supplied.
		Transport missingItem = new Transport.TransportBuilder()
			.type(TransportType.BOAT)
			.origin(FALADOR).destination(VARROCK)
			.itemRequirements(new TransportItems(List.of(
				new ItemRequirement(new int[]{ItemID.WATERRUNE}, null, null, 1))))
			.build();
		assertEquals(RejectionReason.ITEM_REQUIREMENT, requirements.check(missingItem));
	}

	@Test
	public void captureReflectsTheSailingVarbit()
	{
		MapPlayerStateSource ashore = new MapPlayerStateSource();
		assertFalse(RequirementContext.capture(ashore, new TestRequirementHooks(),
			RequirementTestFixtures.policy(MODE), 0L, List.of(), Map.of(), null,
			Set.of(), false, new LeagueModeState(), null).isOnSailingBoat());

		MapPlayerStateSource aboard = new MapPlayerStateSource()
			.varbit(VarbitID.SAILING_BOARDED_BOAT, 1);
		assertTrue(RequirementContext.capture(aboard, new TestRequirementHooks(),
			RequirementTestFixtures.policy(MODE), 0L, List.of(), Map.of(), null,
			Set.of(), false, new LeagueModeState(), null).isOnSailingBoat());
	}

	// ------------------------------------------------------------------
	// The map fake's own contract: configurable thread predicate.
	// ------------------------------------------------------------------

	@Test
	public void mapSourceHonoursTheThreadContract()
	{
		MapPlayerStateSource source = new MapPlayerStateSource();
		assertTrue(source.isOnClientThread());
		source.checkOnClientThread();

		source.offClientThread();
		assertFalse(source.isOnClientThread());
		assertThrows(IllegalStateException.class, source::checkOnClientThread);
	}

	// ------------------------------------------------------------------
	// ClientPlayerStateSource loud off-thread contract — the single
	// sanctioned client-mock test on this path.
	// ------------------------------------------------------------------

	private static Client offThreadClient()
	{
		Client client = mock(Client.class);
		Thread clientThread = new Thread("fake-client-thread");
		// The contract under test only holds because the reported client
		// thread is a different object than the calling thread — pin that.
		assertNotSame(Thread.currentThread(), clientThread);
		when(client.getClientThread()).thenReturn(clientThread);
		return client;
	}

	@Test
	public void everyCaptureGetterThrowsOffTheClientThread()
	{
		PlayerStateSource source = new ClientPlayerStateSource(offThreadClient());
		Map<Integer, Integer> owned = new HashMap<>();

		assertThrows(IllegalStateException.class, source::checkOnClientThread);
		assertThrows(IllegalStateException.class, source::gameState);
		assertThrows(IllegalStateException.class, () -> source.varbit(1));
		assertThrows(IllegalStateException.class, () -> source.varp(1));
		assertThrows(IllegalStateException.class, () -> source.boostedSkillLevel(Skill.COOKING));
		assertThrows(IllegalStateException.class, () -> source.realSkillLevel(Skill.COOKING));
		assertThrows(IllegalStateException.class, source::totalLevel);
		assertThrows(IllegalStateException.class, () -> source.questState(Quest.TREE_GNOME_VILLAGE));
		assertThrows(IllegalStateException.class, source::maximumQuestPoints);
		assertThrows(IllegalStateException.class, source::localPlayerWorldLocation);
		assertThrows(IllegalStateException.class, source::modalWidgetOpen);
		assertThrows(IllegalStateException.class, () -> source.itemContainer(InventoryID.INV));
		assertThrows(IllegalStateException.class, source::runePouchContents);
		assertThrows(IllegalStateException.class, () -> source.addRunePouchContents(owned));
		assertThrows(IllegalStateException.class, source::worldType);
	}

	@Test
	public void captureEntryFailsLoudlyOffTheClientThread()
	{
		PlayerStateSource source = new ClientPlayerStateSource(offThreadClient());
		assertThrows(IllegalStateException.class, () -> RequirementContext.capture(
			source, new TestRequirementHooks(),
			RequirementTestFixtures.policy(MODE), 0L, List.of(), Map.of(), null,
			Set.of(), false, new LeagueModeState(), null));
	}

	@Test
	public void isOnClientThreadAnswersWithoutThrowing()
	{
		// Off the client thread the predicate reports false; it must not
		// throw — the stale-eligibility envelope depends on it.
		PlayerStateSource offThread = new ClientPlayerStateSource(offThreadClient());
		assertFalse(offThread.isOnClientThread());

		Client onThread = mock(Client.class);
		when(onThread.getClientThread()).thenReturn(Thread.currentThread());
		PlayerStateSource on = new ClientPlayerStateSource(onThread);
		assertTrue(on.isOnClientThread());
		on.checkOnClientThread();
	}

	// ------------------------------------------------------------------
	// Snapshot immutability: a chain built before a mutation keeps the
	// frozen verdict — the policy copies its sets, the context copies its
	// maps.
	// ------------------------------------------------------------------

	@Test
	public void aBuiltPolicyIsUnmovedByMutatingTheSourceSets()
	{
		Set<TransportType> enabledTypes = EnumSet.allOf(TransportType.class);
		Set<PohNexusPortal> portals = EnumSet.allOf(PohNexusPortal.class);
		Set<PohMountedItem> mounted = EnumSet.allOf(PohMountedItem.class);
		RoutingPolicy policy = new RoutingPolicy(enabledTypes, MODE, true, true, true, true,
			portals, mounted, JewelleryBoxTier.BASIC, Integer.MAX_VALUE, true);

		RequirementContext context = RequirementTestFixtures.context().build();
		Requirements requirements = new Requirements(context, policy,
			new TestRequirementHooks());

		Transport canoe = new Transport.TransportBuilder()
			.type(TransportType.CANOE).origin(FALADOR).destination(VARROCK).build();
		Transport faladorPortal = new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_PORTAL_POH).origin(POH).destination(VARROCK)
			.displayInfo("Falador Portal").build();
		Transport mountedGlory = new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_BOX).origin(POH).destination(VARROCK)
			.objectInfo("Edgeville Amulet of Glory 13523").build();

		assertEquals(RejectionReason.NONE, requirements.check(canoe));
		assertEquals(RejectionReason.NONE, requirements.check(faladorPortal));
		assertEquals(RejectionReason.NONE, requirements.check(mountedGlory));

		// Mutate everything the policy was built from — the existing chain's
		// verdicts cannot move.
		enabledTypes.clear();
		portals.clear();
		mounted.clear();

		assertEquals(RejectionReason.NONE, requirements.check(canoe));
		assertEquals(RejectionReason.NONE, requirements.check(faladorPortal));
		assertEquals(RejectionReason.NONE, requirements.check(mountedGlory));

		// The sets the policy hands out are themselves immutable.
		assertThrows(UnsupportedOperationException.class, policy.enabledPohNexusPortals()::clear);
		assertThrows(UnsupportedOperationException.class, policy.enabledPohMountedItems()::clear);
	}

	@Test
	public void aBuiltContextIsUnmovedByMutatingTheSourceMaps()
	{
		Map<Quest, QuestState> questStates = new HashMap<>();
		questStates.put(Quest.TREE_GNOME_VILLAGE, QuestState.FINISHED);
		Map<Integer, Integer> varbits = new HashMap<>();
		varbits.put(4481, 1);

		RequirementContext context = RequirementTestFixtures.context()
			.questStates(questStates).varbitValues(varbits).build();
		Requirements requirements = new Requirements(context,
			RequirementTestFixtures.policy(MODE), new TestRequirementHooks());

		Transport gated = new Transport.TransportBuilder()
			.type(TransportType.TRANSPORT).origin(FALADOR).destination(VARROCK)
			.quests(Set.of(Quest.TREE_GNOME_VILLAGE))
			.varbits("4481=1")
			.build();
		assertEquals(RejectionReason.NONE, requirements.check(gated));

		questStates.put(Quest.TREE_GNOME_VILLAGE, QuestState.NOT_STARTED);
		varbits.put(4481, 0);

		assertEquals(RejectionReason.NONE, requirements.check(gated));
	}
}
