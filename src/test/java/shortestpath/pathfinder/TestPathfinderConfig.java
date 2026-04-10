package shortestpath.pathfinder;

import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import shortestpath.ShortestPathConfig;
import shortestpath.transport.Transport;

// This subclass is used to provide mocked implementations of methods from the normal
// PathfinderConfig. CRUCIAL: Not implemented via Mockito as these methods are called
// many times in the inner pathfinding loop.
//
// In particular, at the moment you can provide default values for:
//  * getQuestState: Whether all quests are completed or not started.
//  * varbitChecks: Ignore any varbitChecks.
//  * varPlayerChecks: Ignore any varPlayerChecks.
// Other methods are delegated to a normal PathfinderConfig.
public class TestPathfinderConfig extends PathfinderConfig {
    private final QuestState questState;
    private final boolean bypassVarbitChecks;
    private final boolean bypassVarPlayerChecks;

    public TestPathfinderConfig(Client client, ShortestPathConfig config) {
        this(client, config, QuestState.FINISHED, true, true);
    }

    public TestPathfinderConfig(Client client, ShortestPathConfig config, QuestState questState,
        boolean bypassVarbitChecks, boolean bypassVarPlayerChecks) {
        super(client, config);
        this.questState = questState;
        this.bypassVarbitChecks = bypassVarbitChecks;
        this.bypassVarPlayerChecks = bypassVarPlayerChecks;
    }

    @Override
    public QuestState getQuestState(Quest quest) {
        return questState;
    }

    @Override
    public boolean varbitChecks(Transport transport) {
        return bypassVarbitChecks || super.varbitChecks(transport);
    }

    @Override
    public boolean varPlayerChecks(Transport transport) {
        return bypassVarPlayerChecks || super.varPlayerChecks(transport);
    }
}
