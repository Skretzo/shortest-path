package shortestpathaddons;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import shortestpathaddons.ShortestPathAddonsPlugin;

public class ShortestPathAddonsPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(ShortestPathAddonsPlugin.class);
        RuneLite.main(args);
    }
}
