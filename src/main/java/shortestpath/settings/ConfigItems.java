package shortestpath.settings;

import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import shortestpath.Util;

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
	@AllArgsConstructor
	public static final class Meta
	{
		final String key;
		@Getter @Accessors(fluent = true) final String name;
		@Getter @Accessors(fluent = true) final String description;
		/** The {@code @Range} lower bound, or {@code null} when unbounded. */
		@Getter @Accessors(fluent = true) final Integer min;
		/** The {@code @Range} upper bound, or {@code null} when unbounded. */
		@Getter @Accessors(fluent = true) final Integer max;
	}

	private static final Map<String, Meta> META = load();

	public static Meta of(String key)
	{
		return META.get(key);
	}

	private static Map<String, Meta> load()
	{
		Map<String, Meta> meta = new HashMap<>();
		try
		{
			// '#' rows absent in config-items.tsv — skip-set widened to the shared reader's
			Util.each("/config-items.tsv", (line, cols) ->
			{
				String key = cols[0];
				String name = cols.length > 1 ? cols[1] : "";
				String description = cols.length > 2 ? cols[2] : "";
				Integer min = cols.length > 3 && !cols[3].isEmpty() ? Integer.valueOf(cols[3]) : null;
				Integer max = cols.length > 4 && !cols[4].isEmpty() ? Integer.valueOf(cols[4]) : null;
				meta.put(key, new Meta(key, name, description, min, max));
			});
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load config-items.tsv", e);
		}
		return meta;
	}
}
