package shortestpathaddons;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ShortestPathAddonsPlugin.CONFIG_GROUP)
public interface ShortestPathAddonsConfig extends Config {
    @ConfigSection(
        name = "Clues",
        description = "Options for the pathfinding to clues",
        position = 0
    )
    String sectionClues = "sectionClues";

    @ConfigItem(
        keyName = "clues",
        name = "Path to active clue",
        description = "Whether to display a path to the active clue scroll",
        position = 1,
        section = sectionClues
    )
    default boolean clues() {
        return true;
    }
}
