package shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;

public class PathfinderConfigurationMigrationTest
{
	private static final String GROUP = ShortestPathPlugin.CONFIG_GROUP;

	@Test
	public void migratesOldAStarSelectionAndWeight()
	{
		Map<String, String> values = new HashMap<>();
		values.put("pathfinderBackend", "EXACT");
		values.put("exactHeuristicWeight", "150");
		ConfigManager manager = manager(values);

		PathfinderConfigurationMigration.migrate(manager, GROUP);

		assertEquals("ASTAR", values.get("pathfinderAlgorithm"));
		assertEquals("150", values.get("aStarHeuristicWeight"));
		assertNull(values.get("pathfinderBackend"));
		assertNull(values.get("exactHeuristicWeight"));
	}

	@Test
	public void migratesLegacyAndPrefersExistingNewValues()
	{
		Map<String, String> legacy = new HashMap<>();
		legacy.put("pathfinderBackend", "LEGACY");
		PathfinderConfigurationMigration.migrate(manager(legacy), GROUP);
		assertEquals("LEGACY", legacy.get("pathfinderAlgorithm"));

		Map<String, String> existing = new HashMap<>();
		existing.put("pathfinderAlgorithm", "ASTAR");
		existing.put("pathfinderBackend", "LEGACY");
		existing.put("aStarHeuristicWeight", "200");
		existing.put("exactHeuristicWeight", "125");
		ConfigManager manager = manager(existing);
		PathfinderConfigurationMigration.migrate(manager, GROUP);
		Map<String, String> migrated = new HashMap<>(existing);
		PathfinderConfigurationMigration.migrate(manager, GROUP);

		assertEquals(migrated, existing);
		assertEquals("ASTAR", existing.get("pathfinderAlgorithm"));
		assertEquals("200", existing.get("aStarHeuristicWeight"));
	}

	private static ConfigManager manager(Map<String, String> values)
	{
		ConfigManager manager = mock(ConfigManager.class);
		when(manager.getConfiguration(anyString(), anyString()))
			.thenAnswer(call -> values.get(call.getArgument(1)));
		doAnswer(call ->
		{
			values.put(call.getArgument(1), call.getArgument(2));
			return null;
		}).when(manager).setConfiguration(anyString(), anyString(), anyString());
		doAnswer(call ->
		{
			values.remove(call.getArgument(1));
			return null;
		}).when(manager).unsetConfiguration(anyString(), anyString());
		return manager;
	}
}
