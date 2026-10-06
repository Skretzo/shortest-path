package shortestpath.requirement;

import java.util.Collection;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import shortestpath.requirement.model.VarRequirement;

/**
 * A {@link RequirementHooks} implementation for tests — the same seam the
 * production {@code PathfinderConfig} adapter feeds. Quest state comes from a
 * settable field (the hook is consulted while the context captures declared
 * quests); varbit and varplayer evaluation runs the real
 * {@link VarRequirement#check} semantics against the maps captured into the
 * context under test, mirroring how the config's bypassable methods read the
 * per-refresh snapshot.
 *
 * <p>The {@code failVarbits}/{@code failVarPlayers} knobs force the failure
 * polarity ({@code true} = a check FAILED) for liveness assertions: flipping
 * one must move the verdict of a chain that already exists, proving the gates
 * read the seam per call rather than a frozen copy.
 */
final class TestRequirementHooks implements RequirementHooks
{
	private final Map<Integer, Integer> varbitValues;
	private final Map<Integer, Integer> varPlayerValues;
	private QuestState questState = QuestState.FINISHED;
	private boolean failVarbits;
	private boolean failVarPlayers;

	/**
	 * Hooks whose var maps are the ones captured in {@code context} — the same
	 * binding the refresh path produces between the snapshot and the config's
	 * var-check methods.
	 */
	static TestRequirementHooks forContext(RequirementContext context)
	{
		return new TestRequirementHooks(context.getVarbitValues(), context.getVarPlayerValues());
	}

	TestRequirementHooks(Map<Integer, Integer> varbitValues, Map<Integer, Integer> varPlayerValues)
	{
		this.varbitValues = varbitValues;
		this.varPlayerValues = varPlayerValues;
	}

	TestRequirementHooks questState(QuestState state)
	{
		questState = state;
		return this;
	}

	TestRequirementHooks failVarbits(boolean fail)
	{
		failVarbits = fail;
		return this;
	}

	TestRequirementHooks failVarPlayers(boolean fail)
	{
		failVarPlayers = fail;
		return this;
	}

	@Override
	public QuestState getQuestState(Quest quest)
	{
		return questState;
	}

	@Override
	public boolean varbitChecks(Collection<VarRequirement> requirements, long evaluationTimeMinutes)
	{
		for (VarRequirement requirement : requirements)
		{
			if (requirement.isVarbit()
				&& (failVarbits || !requirement.check(varbitValues, evaluationTimeMinutes)))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean varPlayerChecks(Collection<VarRequirement> requirements, long evaluationTimeMinutes)
	{
		for (VarRequirement requirement : requirements)
		{
			if (requirement.isVarPlayer()
				&& (failVarPlayers || !requirement.check(varPlayerValues, evaluationTimeMinutes)))
			{
				return true;
			}
		}
		return false;
	}
}
