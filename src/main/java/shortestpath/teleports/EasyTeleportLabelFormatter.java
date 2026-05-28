package shortestpath.teleports;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.runelite.client.util.Text;

/**
 * Rewrites Shortest Path transport hint labels using Easy Teleports user-defined
 * destination names when the separate Easy Teleports plugin is installed.
 * <p>
 * Mappings are loaded from {@code /easy_teleport_labels.txt}. Config is read from
 * RuneLite's {@link net.runelite.client.config.ConfigManager} using {@link #CONFIG_GROUP},
 * matching upstream Easy Teleports.
 *
 * @see <a href="https://github.com/LlemonDuck/easy-teleports">LlemonDuck/easy-teleports</a>
 */
public final class EasyTeleportLabelFormatter
{
	/**
	 * Easy Teleports config group ({@code easypharaohsceptre}), from
	 * <a href="https://github.com/LlemonDuck/easy-teleports/blob/31e38dbedb512292b0370dc9be0f72df6cd25e1b/src/main/java/com/duckblade/osrs/easyteleports/EasyTeleportsConfig.java">
	 * EasyTeleportsConfig.CONFIG_GROUP</a>.
	 */
	public static final String CONFIG_GROUP = "easypharaohsceptre";
	private static final String LABEL_SEPARATOR = ": ";
	private static final Map<String, ReplacementConfig> REPLACEMENTS = loadReplacements();

	private EasyTeleportLabelFormatter()
	{
	}

	/**
	 * Returns {@code displayInfo} with the destination suffix replaced when Easy Teleports
	 * has an enabled mapping and non-blank replacement for that label; otherwise returns
	 * {@code displayInfo} unchanged.
	 *
	 * @param displayInfo full Shortest Path label ({@code "Item: Destination"})
	 * @param configLookup reads Easy Teleports config keys from {@link #CONFIG_GROUP}
	 * @return formatted display label, or the original label when no replacement applies
	 */
	public static String format(String displayInfo, BiFunction<String, String, String> configLookup)
	{
		ReplacementConfig replacementConfig = REPLACEMENTS.get(displayInfo);
		if (replacementConfig == null || configLookup == null
			|| !Boolean.parseBoolean(configLookup.apply(CONFIG_GROUP, replacementConfig.enabledKey)))
		{
			return displayInfo;
		}

		String replacement = stripTags(configLookup.apply(CONFIG_GROUP, replacementConfig.replacementKey));
		if (replacement == null || replacement.isEmpty())
		{
			return displayInfo;
		}

		int separatorIndex = displayInfo.indexOf(LABEL_SEPARATOR);
		if (separatorIndex < 0)
		{
			return displayInfo;
		}

		return displayInfo.substring(0, separatorIndex + LABEL_SEPARATOR.length()) + replacement;
	}

	private static String stripTags(String value)
	{
		if (value == null)
		{
			return null;
		}

		return Text.removeTags(value).replace('\u00A0', ' ').trim();
	}

	private static Map<String, ReplacementConfig> loadReplacements()
	{
		Map<String, ReplacementConfig> replacements = new HashMap<>();
		InputStream input = EasyTeleportLabelFormatter.class.getResourceAsStream("/easy_teleport_labels.txt");
		if (input == null)
		{
			throw new IllegalStateException("Missing easy_teleport_labels.txt");
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isBlank() || line.startsWith("#"))
				{
					continue;
				}

				String[] fields = line.split("\t", -1);
				if (fields.length != 4)
				{
					throw new IllegalStateException("Invalid easy teleport label row: " + line);
				}

				replacements.put(fields[0] + LABEL_SEPARATOR + fields[1], new ReplacementConfig(fields[2], fields[3]));
			}
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Unable to load easy teleport labels", e);
		}
		return Map.copyOf(replacements);
	}

	private static final class ReplacementConfig
	{
		private final String enabledKey;
		private final String replacementKey;

		private ReplacementConfig(String enabledKey, String replacementKey)
		{
			this.enabledKey = enabledKey;
			this.replacementKey = replacementKey;
		}
	}
}
