package shortestpath.requirement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.requirement.model.VarCheckType;
import shortestpath.requirement.model.VarRequirement;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Hook liveness: every {@link RequirementHooks} seam must reach BOTH
 * consumers — the transport verdict and the bank-destination verdict —
 * after the middleware extraction. A hook that stopped being consulted
 * would fail to move either side of the matrix asserted here.
 *
 * <p>The seams under test are the same ones {@code PathfinderConfig}
 * implements through its private adapter and {@code TestPathfinderConfig}
 * overrides: {@code getQuestState} feeds the captured quest map, and the
 * varbit/varplayer methods are consulted per check against the captured
 * values. No {@code Requirements} internals are mocked — the chains are
 * real and the verdicts come from {@code check}/{@code satisfied}.
 */
public class HookLivenessTest
{
	private static final TeleportationItem MODE = TeleportationItem.INVENTORY;
	private static final int BANK_TILE = WorldPointUtil.packWorldPoint(1804, 9501, 0);

	private static Transport transport()
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TRANSPORT)
			.origin(WorldPointUtil.packWorldPoint(2965, 3378, 0))
			.destination(WorldPointUtil.packWorldPoint(3213, 3424, 0))
			.build();
	}

	private static RequirementContext capture(MapPlayerStateSource source, TestRequirementHooks hooks,
		List<Transport> transports, Map<Integer, DestinationRequirements> bankRequirements)
	{
		return RequirementContext.capture(source, hooks, RequirementTestFixtures.policy(MODE), 0L,
			transports, bankRequirements, null, Set.of(), false, new LeagueModeState(), null);
	}

	private static Requirements chain(RequirementContext context, TestRequirementHooks hooks)
	{
		return new Requirements(context, RequirementTestFixtures.policy(MODE), hooks);
	}

	// ------------------------------------------------------------------
	// getQuestState — consulted while capture snapshots declared quests.
	// ------------------------------------------------------------------

	@Test
	public void questHookMovesTheTransportVerdict()
	{
		Transport gated = new Transport.TransportBuilder()
			.type(TransportType.TRANSPORT)
			.origin(WorldPointUtil.packWorldPoint(2965, 3378, 0))
			.destination(WorldPointUtil.packWorldPoint(3213, 3424, 0))
			.quests(Set.of(Quest.TREE_GNOME_VILLAGE))
			.build();

		TestRequirementHooks finished = new TestRequirementHooks()
			.questState(QuestState.FINISHED);
		RequirementContext done = capture(new MapPlayerStateSource(), finished,
			List.of(gated), Map.of());
		assertTrue(chain(done, finished).usable(gated));

		TestRequirementHooks unfinished = new TestRequirementHooks()
			.questState(QuestState.NOT_STARTED);
		RequirementContext notDone = capture(new MapPlayerStateSource(), unfinished,
			List.of(gated), Map.of());
		assertFalse(chain(notDone, unfinished).usable(gated));
		assertEquals(RejectionReason.QUEST, chain(notDone, unfinished).check(gated));
	}

	@Test
	public void questHookMovesTheBankVerdict()
	{
		DestinationRequirements gated = RequirementTestFixtures.bankRequirements(
			null, Set.of(Quest.BONE_VOYAGE), null, null);

		TestRequirementHooks finished = new TestRequirementHooks()
			.questState(QuestState.FINISHED);
		RequirementContext done = capture(new MapPlayerStateSource(), finished,
			List.of(), Map.of(BANK_TILE, gated));
		assertTrue(chain(done, finished).satisfied(gated));

		TestRequirementHooks unfinished = new TestRequirementHooks()
			.questState(QuestState.NOT_STARTED);
		RequirementContext notDone = capture(new MapPlayerStateSource(), unfinished,
			List.of(), Map.of(BANK_TILE, gated));
		assertFalse(chain(notDone, unfinished).satisfied(gated));
		assertEquals(RejectionReason.QUEST, chain(notDone, unfinished).check(gated));
	}

	// ------------------------------------------------------------------
	// varbitChecks — consulted per check against the captured map.
	// ------------------------------------------------------------------

	@Test
	public void varbitHookMovesTheTransportVerdictOnTheSameChain()
	{
		Transport gated = new Transport.TransportBuilder()
			.type(TransportType.TRANSPORT)
			.origin(WorldPointUtil.packWorldPoint(2965, 3378, 0))
			.destination(WorldPointUtil.packWorldPoint(3213, 3424, 0))
			.varbits("4481=1")
			.build();
		RequirementContext context = RequirementTestFixtures.context()
			.varbitValues(Map.of(4481, 1)).build();
		TestRequirementHooks hooks = new TestRequirementHooks();
		Requirements requirements = chain(context, hooks);

		// The map satisfies the check; flipping the bypass flag forces the
		// failure polarity through the same already-built chain — proof the
		// gate reads the seam per call, not a verdict frozen at capture.
		assertTrue(requirements.usable(gated));
		hooks.failVarbits(true);
		assertFalse(requirements.usable(gated));
		assertEquals(RejectionReason.VARBIT, requirements.check(gated));
	}

	@Test
	public void varbitHookMovesTheBankVerdictOnTheSameChain()
	{
		DestinationRequirements gated = RequirementTestFixtures.bankRequirements(
			null, null, Set.of(VarRequirement.varbit(4481, 1, VarCheckType.EQUAL)), null);
		RequirementContext context = RequirementTestFixtures.context()
			.varbitValues(Map.of(4481, 1)).build();
		TestRequirementHooks hooks = new TestRequirementHooks();
		Requirements requirements = chain(context, hooks);

		assertTrue(requirements.satisfied(gated));
		hooks.failVarbits(true);
		assertFalse(requirements.satisfied(gated));
		assertEquals(RejectionReason.VARBIT, requirements.check(gated));
	}

	// ------------------------------------------------------------------
	// varPlayerChecks — same polarity, bank and transport.
	// ------------------------------------------------------------------

	@Test
	public void varPlayerHookMovesTheTransportVerdictOnTheSameChain()
	{
		Transport gated = new Transport.TransportBuilder()
			.type(TransportType.TRANSPORT)
			.origin(WorldPointUtil.packWorldPoint(2965, 3378, 0))
			.destination(WorldPointUtil.packWorldPoint(3213, 3424, 0))
			.varPlayers("4130>1999")
			.build();
		RequirementContext context = RequirementTestFixtures.context()
			.varPlayerValues(Map.of(4130, 2000)).build();
		TestRequirementHooks hooks = new TestRequirementHooks();
		Requirements requirements = chain(context, hooks);

		assertTrue(requirements.usable(gated));
		hooks.failVarPlayers(true);
		assertFalse(requirements.usable(gated));
		assertEquals(RejectionReason.VARPLAYER, requirements.check(gated));
	}

	@Test
	public void varPlayerHookMovesTheBankVerdictOnTheSameChain()
	{
		DestinationRequirements gated = RequirementTestFixtures.bankRequirements(
			null, null, null,
			Set.of(VarRequirement.varPlayer(4130, 1999, VarCheckType.GREATER)));
		RequirementContext context = RequirementTestFixtures.context()
			.varPlayerValues(Map.of(4130, 2000)).build();
		TestRequirementHooks hooks = new TestRequirementHooks();
		Requirements requirements = chain(context, hooks);

		assertTrue(requirements.satisfied(gated));
		hooks.failVarPlayers(true);
		assertFalse(requirements.satisfied(gated));
		assertEquals(RejectionReason.VARPLAYER, requirements.check(gated));
	}
}
