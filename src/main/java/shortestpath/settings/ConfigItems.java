package shortestpath.settings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Display metadata for the hidden config keys — the {@code name},
 * {@code description} and {@code @Range} bounds that used to live in the
 * deleted {@code @ConfigItem} annotations, moved to
 * {@code config-items.tsv} (key, name, description, min, max).
 */
public final class ConfigItems
{
	/**
	 * The display record {@link Settings#item} returns; accessor names match
	 * {@code ConfigItem}'s so the panel reads the same fields either way.
	 */
	public static final class Meta
	{
		final String key;
		final String name;
		final String description;
		final Integer min;
		final Integer max;

		public Meta(String key, String name, String description, Integer min, Integer max)
		{
			this.key = key;
			this.name = name;
			this.description = description;
			this.min = min;
			this.max = max;
		}

		public String name()
		{
			return name;
		}

		public String description()
		{
			return description;
		}

		/** The {@code @Range} lower bound, or {@code null} when unbounded. */
		public Integer min()
		{
			return min;
		}

		/** The {@code @Range} upper bound, or {@code null} when unbounded. */
		public Integer max()
		{
			return max;
		}
	}

	private static final Map<String, Meta> META = load();

	public static Meta of(String key)
	{
		return META.get(key);
	}

	private static Map<String, Meta> load()
	{
		Map<String, Meta> meta = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			ConfigItems.class.getResourceAsStream("/config-items.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty())
				{
					continue;
				}
				String[] cols = line.split("\t", -1);
				String key = cols[0];
				String name = cols.length > 1 ? cols[1] : "";
				String description = cols.length > 2 ? cols[2] : "";
				Integer min = cols.length > 3 && !cols[3].isEmpty() ? Integer.valueOf(cols[3]) : null;
				Integer max = cols.length > 4 && !cols[4].isEmpty() ? Integer.valueOf(cols[4]) : null;
				meta.put(key, new Meta(key, name, description, min, max));
			}
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load config-items.tsv", e);
		}
		return meta;
	}
}
