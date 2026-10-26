package shortestpath.requirement;

import java.util.Collection;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import shortestpath.requirement.model.VarRequirement;

/**
 * The overridable seams the requirement gate chain reads through. The chain
 * never consults {@code PathfinderConfig} or the client directly; instead it
 * calls these hooks for every check a test or the dashboard harness may
 * bypass. {@code PathfinderConfig} supplies the implementation through a
 * private adapter that dispatches {@link #varChecks} on the
 * {@link VarRequirement.VarType} kind to its own keyed
 * {@code varbitChecks}/{@code varPlayerChecks} methods, so overrides such as
 * {@code TestPathfinderConfig}'s bypass flags keep changing transport and
 * bank-destination verdicts after the extraction.
 *
 * <p>Failure polarity contract: {@code varChecks} returns {@code true} when
 * a check FAILED — the same polarity the {@code PathfinderConfig} methods
 * and every existing override use. Do not invert it.
 */
public interface RequirementHooks
{
	/**
	 * The state of one quest as the hook sees it this refresh.
	 */
	QuestState getQuestState(Quest quest);

	/**
	 * Whether any requirement of the given {@link VarRequirement.VarType}
	 * kind in the collection fails against the supplied values — the map the
	 * calling chain captured into its context, so a stale chain always
	 * evaluates its own refresh's snapshot rather than whatever a newer
	 * refresh wrote. Only requirements whose
	 * {@link VarRequirement#getVarType()} equals {@code kind} are evaluated;
	 * the other var family is invisible to the call. Returns {@code true}
	 * when a check FAILED.
	 */
	boolean varChecks(Collection<VarRequirement> requirements,
		Map<Integer, Integer> values, long evaluationTimeMinutes,
		VarRequirement.VarType kind);
}
