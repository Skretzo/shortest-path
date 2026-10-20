package shortestpath.settings;

/**
 * The seam that supplies values for config keys which are not declared as
 * {@code @ConfigItem} methods on {@link shortestpath.ShortestPathConfig}
 * (the "hidden" keys). In production the values come from
 * {@code ConfigManager.getConfiguration} under the same persisted key names;
 * harnesses without a {@code ConfigManager} (the {@code Settings.wrap} seam)
 * implement this interface on their stub config instead.
 */
public interface KeyedValues
{
	/**
	 * The value for {@code keyName}, already in its typed form
	 * ({@code Boolean}, {@code Integer}, {@code String}, an enum constant or a
	 * {@code Set} of enum constants). {@code null} means "not set" — the
	 * caller falls back to the key's declared default.
	 */
	Object value(String keyName);
}
