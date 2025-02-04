package pathfinder;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import shortestpath.ShortestPathPlugin;

public class PluginMessageTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(ShortestPathPlugin.class);
        ExternalPluginManager.loadBuiltin(PluginMessageTestPlugin.class);
        RuneLite.main(args);
    }
}
