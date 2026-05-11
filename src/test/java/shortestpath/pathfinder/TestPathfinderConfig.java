package shortestpath.pathfinder;

import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import java.util.Map;
import java.util.Set;
import shortestpath.Destination;
import shortestpath.DestinationRequirements;
import shortestpath.ShortestPathConfig;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;

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
	/**
	 * Initialization-on-Demand Holder: loads the five resource-heavy fields exactly once
	 * per JVM, so every TestPathfinderConfig instance shares the same pre-parsed data.
	 */
	private static final class ResourceHolder
	{
		static final SplitFlagMap MAP_DATA;
		static final Map<Integer, Set<Transport>> ALL_TRANSPORTS;
		static final Map<String, Set<Integer>> ALL_DESTINATIONS;
		static final Map<String, Set<Integer>> FILTERED_DESTINATIONS;
		static final Map<Integer, DestinationRequirements> BANK_REQUIREMENTS;

		static
		{
			MAP_DATA = SplitFlagMap.fromResources();
			ALL_TRANSPORTS = TransportLoader.loadAllFromResources();
			PathfinderConfig.remapPohDestinations(ALL_TRANSPORTS);
			ALL_DESTINATIONS = Destination.loadAllFromResources();
			FILTERED_DESTINATIONS = PathfinderConfig.filterDestinations(ALL_DESTINATIONS);
			BANK_REQUIREMENTS = Destination.loadBankRequirementsFromResources();
		}
	}

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
			ResourceHolder.MAP_DATA,
			ResourceHolder.ALL_TRANSPORTS,
			ResourceHolder.ALL_DESTINATIONS,
			ResourceHolder.FILTERED_DESTINATIONS,
			ResourceHolder.BANK_REQUIREMENTS);
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
	public boolean varbitChecks(Transport transport)
	{
		return !bypassVarbitChecks && super.varbitChecks(transport);
	}

	@Override
	public boolean varPlayerChecks(Transport transport)
	{
		return !bypassVarPlayerChecks && super.varPlayerChecks(transport);
	}
}
