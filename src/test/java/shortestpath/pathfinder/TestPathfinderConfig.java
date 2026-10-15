package shortestpath.pathfinder;

import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import java.util.Collection;
import java.util.Map;
import shortestpath.ShortestPathConfig;
import shortestpath.transport.Transport;
import shortestpath.requirement.model.VarRequirement;

// This subclass is used to provide mocked implementations of methods from the normal
// PathfinderConfig. CRUCIAL: Not implemented via Mockito as these methods are called
// many times in the inner pathfinding loop.
//
// In particular, at the moment you can provide default values for:
//  * getQuestState: Whether all quests are completed or not started.
//  * varbitChecks: Ignore any varbitChecks.
//  * varPlayerChecks: Ignore any varPlayerChecks.
// Other methods are delegated to a normal PathfinderConfig.
public class TestPathfinderConfig extends PathfinderConfig
{
	private final QuestState questState;
	private final boolean bypassVarbitChecks;
	private final boolean bypassVarPlayerChecks;

	public TestPathfinderConfig(Client client, ShortestPathConfig config)
	{
		this(client, config, QuestState.FINISHED, true, true);
	}

	public TestPathfinderConfig(Client client, ShortestPathConfig config, QuestState questState,
		boolean bypassVarbitChecks, boolean bypassVarPlayerChecks)
	{
		super(client, config,
			TestWorldData.map(),
			TestWorldData.transports(),
			TestWorldData.destinations(),
			TestWorldData.filteredDestinations(),
			TestWorldData.bankRequirements());
		this.questState = questState;
		this.bypassVarbitChecks = bypassVarbitChecks;
		this.bypassVarPlayerChecks = bypassVarPlayerChecks;
	}

	@Override
	public QuestState getQuestState(Quest quest)
	{
		return questState;
	}

	@Override
	public boolean varbitChecks(Collection<VarRequirement> requirements, long evaluationTimeMinutes)
	{
		return !bypassVarbitChecks && super.varbitChecks(requirements, evaluationTimeMinutes);
	}

	@Override
	public boolean varbitChecks(Collection<VarRequirement> requirements,
		Map<Integer, Integer> values, long evaluationTimeMinutes)
	{
		return !bypassVarbitChecks && super.varbitChecks(requirements, values, evaluationTimeMinutes);
	}

	@Override
	public boolean varPlayerChecks(Collection<VarRequirement> requirements, long evaluationTimeMinutes)
	{
		return !bypassVarPlayerChecks && super.varPlayerChecks(requirements, evaluationTimeMinutes);
	}

	@Override
	public boolean varPlayerChecks(Collection<VarRequirement> requirements,
		Map<Integer, Integer> values, long evaluationTimeMinutes)
	{
		return !bypassVarPlayerChecks && super.varPlayerChecks(requirements, values, evaluationTimeMinutes);
	}

	/**
	 * Builds a real {@link TransportAvailability} holding the given transports. Mockito's
	 * default mock maker cannot mock the final class, so tests that need availability
	 * lookups go through this instead.
	 */
	public static TransportAvailability availabilityOf(Transport... transports)
	{
		TransportAvailability.Builder builder = new TransportAvailability.Builder(transports.length);
		for (Transport transport : transports)
		{
			builder.add(transport);
		}
		builder.remapPohTransports();
		return builder.build();
	}
}
