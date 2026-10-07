package shortestpath.requirement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.requirement.model.VarCheckType;
import shortestpath.requirement.model.VarRequirement;
import shortestpath.transport.parser.VarRequirementParser;

/**
 * The bank-destination adapter arm of the middleware: a bank tile's
 * {@link DestinationRequirements} goes through the same
 * {@link Requirements} chain as transports, reading the same refresh
 * snapshot and consulting the same {@link RequirementHooks} seam — a bank
 * tile and a transport can never disagree about the same requirement
 * inside one refresh.
 *
 * <p>The Fortis Colosseum row ({@code 4130>1999}) is the load-bearing
 * real-world case: a strict-greater varplayer gate that must fail closed
 * when the var was never captured.
 */
public class BankRequirementAdapterTest
{
	private static final TeleportationItem MODE = TeleportationItem.INVENTORY;

	private static Requirements requirements(RequirementContext context)
	{
		return RequirementTestFixtures.requirements(context, RequirementTestFixtures.policy(MODE));
	}

	// ------------------------------------------------------------------
	// Empty and absent requirements fast-path.
	// ------------------------------------------------------------------

	@Test
	public void absentAndEmptyRequirementsAreAlwaysSatisfied()
	{
		Requirements requirements = requirements(RequirementTestFixtures.context().build());

		assertTrue(requirements.satisfied(null));
		assertTrue(requirements.satisfied(DestinationRequirements.EMPTY));
		assertEquals(RejectionReason.NONE, requirements.check(DestinationRequirements.EMPTY));

		// A constructed-but-empty requirement is the same fast path: all
		// fields present and empty/zero still count as no requirements.
		DestinationRequirements constructed = new DestinationRequirements(
			new int[Skill.values().length + 3], Set.of(), Set.of(), Set.of());
		assertTrue(constructed.isEmpty());
		assertTrue(requirements.satisfied(constructed));
	}

	// ------------------------------------------------------------------
	// Per-gate coverage through the bank adapter.
	// ------------------------------------------------------------------

	@Test
	public void skillGatedBankFollowsBoostedLevels()
	{
		// Cooks' Guild shape: 99 Cooking.
		int[] skills = new int[Skill.values().length + 3];
		skills[Skill.COOKING.ordinal()] = 99;
		DestinationRequirements guild = RequirementTestFixtures.bankRequirements(
			skills, null, null, null);

		assertTrue(requirements(RequirementTestFixtures.context().build()).satisfied(guild));

		int[] below = RequirementTestFixtures.maxedSkills();
		below[Skill.COOKING.ordinal()] = 98;
		RequirementContext underleveled = RequirementTestFixtures.context()
			.boostedSkillLevelsAndMore(below).build();
		assertFalse(requirements(underleveled).satisfied(guild));
		assertEquals(RejectionReason.SKILL_LEVEL, requirements(underleveled).check(guild));
	}

	@Test
	public void questGatedBankFollowsTheHookFedQuestMap()
	{
		DestinationRequirements gated = RequirementTestFixtures.bankRequirements(
			null, Set.of(Quest.BONE_VOYAGE), null, null);

		// Missing from the captured map → NOT_STARTED → fails closed.
		RequirementContext missing = RequirementTestFixtures.context().build();
		assertFalse(requirements(missing).satisfied(gated));
		assertEquals(RejectionReason.QUEST, requirements(missing).check(gated));

		RequirementContext finished = RequirementTestFixtures.context()
			.questStates(Map.of(Quest.BONE_VOYAGE, QuestState.FINISHED)).build();
		assertTrue(requirements(finished).satisfied(gated));
	}

	@Test
	public void varbitGatedBankFollowsCapturedVarbits()
	{
		// Cooks' Guild door: varbit 4481 == 1.
		Set<VarRequirement> varbits = VarRequirementParser.forVarbits().parse("4481=1");
		DestinationRequirements gated = RequirementTestFixtures.bankRequirements(
			null, null, varbits, null);

		assertTrue(requirements(RequirementTestFixtures.context()
			.varbitValues(Map.of(4481, 1)).build()).satisfied(gated));

		RequirementContext closed = RequirementTestFixtures.context()
			.varbitValues(Map.of(4481, 0)).build();
		assertFalse(requirements(closed).satisfied(gated));
		assertEquals(RejectionReason.VARBIT, requirements(closed).check(gated));
	}

	@Test
	public void fortisColosseumVarplayerIsStrictlyGreater()
	{
		// bank.tsv: "1804 9501 0  Fortis Colosseum  ...  4130>1999" — the
		// colosseum bank is locked until varplayer 4130 exceeds 1999.
		Set<VarRequirement> varPlayers = VarRequirementParser.forVarPlayers().parse("4130>1999");
		assertEquals(1, varPlayers.size());
		assertEquals(VarCheckType.GREATER, varPlayers.iterator().next().getCheckType());
		DestinationRequirements fortis = RequirementTestFixtures.bankRequirements(
			null, null, null, varPlayers);

		// Present but below the threshold.
		RequirementContext below = RequirementTestFixtures.context()
			.varPlayerValues(Map.of(4130, 1500)).build();
		assertFalse(requirements(below).satisfied(fortis));
		assertEquals(RejectionReason.VARPLAYER, requirements(below).check(fortis));

		// Exactly at the threshold still fails — the check is strict >.
		RequirementContext at = RequirementTestFixtures.context()
			.varPlayerValues(Map.of(4130, 1999)).build();
		assertFalse(requirements(at).satisfied(fortis));

		// Above the threshold.
		RequirementContext above = RequirementTestFixtures.context()
			.varPlayerValues(Map.of(4130, 2000)).build();
		assertTrue(requirements(above).satisfied(fortis));
		assertEquals(RejectionReason.NONE, requirements(above).check(fortis));

		// The var was never captured: fail closed.
		RequirementContext absent = RequirementTestFixtures.context().build();
		assertFalse(requirements(absent).satisfied(fortis));
		assertEquals(RejectionReason.VARPLAYER, requirements(absent).check(fortis));
	}

	@Test
	public void bankRequirementsCombineConjunctively()
	{
		// A tile carrying two requirement kinds needs all of them.
		int[] skills = new int[Skill.values().length + 3];
		skills[Skill.COOKING.ordinal()] = 99;
		DestinationRequirements both = RequirementTestFixtures.bankRequirements(
			skills, null, VarRequirementParser.forVarbits().parse("4481=1"), null);

		RequirementContext varMissing = RequirementTestFixtures.context().build();
		assertFalse(requirements(varMissing).satisfied(both));

		int[] low = RequirementTestFixtures.maxedSkills();
		low[Skill.COOKING.ordinal()] = 1;
		RequirementContext lowSkill = RequirementTestFixtures.context()
			.boostedSkillLevelsAndMore(low).varbitValues(Map.of(4481, 1)).build();
		// Skill is checked before varbits, so the skill verdict wins.
		assertEquals(RejectionReason.SKILL_LEVEL, requirements(lowSkill).check(both));

		RequirementContext passing = RequirementTestFixtures.context()
			.varbitValues(Map.of(4481, 1)).build();
		assertTrue(requirements(passing).satisfied(both));
	}

	// ------------------------------------------------------------------
	// Snapshot contract: a built chain reads the captured maps, not the
	// live source — mutating the source after capture cannot move a verdict.
	// ------------------------------------------------------------------

	@Test
	public void verdictsReadTheCapturedSnapshotNotTheLiveSource()
	{
		MapPlayerStateSource source = new MapPlayerStateSource()
			.varp(4130, 2000);
		DestinationRequirements fortis = RequirementTestFixtures.bankRequirements(
			null, null, null, VarRequirementParser.forVarPlayers().parse("4130>1999"));

		RequirementContext context = RequirementContext.capture(
			source,
			new TestRequirementHooks(),
			RequirementTestFixtures.policy(MODE),
			0L,
			List.of(),
			Map.of(1, fortis),
			null,
			Set.of(),
			false,
			new shortestpath.leagues.LeagueModeState(),
			null);
		Requirements requirements = requirements(context);
		assertTrue(requirements.satisfied(fortis));

		// The source now reports a failing value; the already-built chain
		// still reads the snapshot from capture time.
		source.varp(4130, 0);
		assertTrue(requirements.satisfied(fortis));

		// And a fresh capture observes the new value.
		RequirementContext recaptured = RequirementContext.capture(
			source,
			new TestRequirementHooks(),
			RequirementTestFixtures.policy(MODE),
			0L,
			List.of(),
			Map.of(1, fortis),
			null,
			Set.of(),
			false,
			new shortestpath.leagues.LeagueModeState(),
			null);
		assertFalse(requirements(recaptured).satisfied(fortis));
	}
}
