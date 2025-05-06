package shortestpathaddons;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
    name = "Shortest Path Addons",
    description = "Extra features for the Shortest Path plugin",
    tags = {"pathfinder", "map", "waypoint", "navigation", "extra", "addon"}
)
public class ShortestPathAddonsPlugin extends Plugin {
    protected static final String CONFIG_GROUP = "shortestpathaddons";
    private static final String PLUGIN_MESSAGE_PATH = "path";
    private static final String PLUGIN_MESSAGE_CLEAR = "clear";
    private static final String PLUGIN_MESSAGE_START = "start";
    private static final String PLUGIN_MESSAGE_TARGET = "target";
    private static final String PLUGIN_MESSAGE_CONFIG_OVERRIDE = "config";

    @Inject
    private Client client;

    @Inject
    private ShortestPathAddonsConfig config;


    @Provides
    public ShortestPathAddonsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ShortestPathAddonsConfig.class);
    }

    @Override
    protected void startUp() {

    }

    @Override
    protected void shutDown() {

    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup())) {
            return;
        }

    }
}
