package shortestpath.requirement;

import java.util.Collection;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import shortestpath.requirement.model.VarRequirement;

/**
 * The overridable seams the requirement gate chain reads through. The chain
 * never consults {@code PathfinderConfig} or the client directly; instead it
 * calls these hooks for every check a test or the dashboard harness may
 * bypass. {@code PathfinderConfig} supplies the implementation through a
 * private adapter that delegates to its own protected
 * {@code getQuestState}/{@code varbitChecks}/{@code varPlayerChecks} methods,
 * so overrides such as {@code TestPathfinderConfig}'s bypass flags keep
 * changing transport and bank-destination verdicts after the extraction.
 *
 * <p>Failure polarity contract: {@code varbitChecks} and
 * {@code varPlayerChecks} return {@code true} when a check FAILED — the same
 * polarity the {@code PathfinderConfig} methods and every existing override
 * use. Do not invert it.
 */
public interface RequirementHooks
{
	/**
	 * The state of one quest as the hook sees it this refresh.
	 */
	QuestState getQuestState(Quest quest);

	/**
	 * Whether any varbit requirement in the collection fails against this
	 * refresh's captured values. Returns {@code true} when a check FAILED.
	 */
	boolean varbitChecks(Collection<VarRequirement> requirements, long evaluationTimeMinutes);

	/**
	 * Whether any varplayer requirement in the collection fails against this
	 * refresh's captured values. Same failure polarity as
	 * {@link #varbitChecks(Collection, long)}.
	 */
	boolean varPlayerChecks(Collection<VarRequirement> requirements, long evaluationTimeMinutes);
}
