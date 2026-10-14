package shortestpath;

import net.runelite.client.config.ConfigManager;

final class PathfinderConfigurationMigration
{
	private static final String ALGORITHM_KEY = "pathfinderAlgorithm";
	private static final String OLD_BACKEND_KEY = "pathfinderBackend";
	private static final String A_STAR_WEIGHT_KEY = "aStarHeuristicWeight";
	private static final String OLD_WEIGHT_KEY = "exactHeuristicWeight";

	private PathfinderConfigurationMigration()
	{
	}

	static void migrate(ConfigManager configManager, String configGroup)
	{
		String oldBackend = configManager.getConfiguration(configGroup, OLD_BACKEND_KEY);
		if (configManager.getConfiguration(configGroup, ALGORITHM_KEY) == null && oldBackend != null)
		{
			configManager.setConfiguration(configGroup, ALGORITHM_KEY,
				"EXACT".equals(oldBackend) ? "ASTAR" : "LEGACY");
		}

		if (oldBackend != null)
		{
			configManager.unsetConfiguration(configGroup, OLD_BACKEND_KEY);
		}

		String oldWeight = configManager.getConfiguration(configGroup, OLD_WEIGHT_KEY);
		if (oldWeight != null)
		{
			if (configManager.getConfiguration(configGroup, A_STAR_WEIGHT_KEY) == null)
			{
				configManager.setConfiguration(configGroup, A_STAR_WEIGHT_KEY, oldWeight);
			}
			configManager.unsetConfiguration(configGroup, OLD_WEIGHT_KEY);
		}
	}
}
