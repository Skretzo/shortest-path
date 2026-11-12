package shortestpath.settings;

/**
 * Stored values for {@link Settings#wrap}, which has no {@code ConfigManager}:
 * test stub configs implement this.
 */
public interface KeyedValues
{
	/** The typed stored value for {@code keyName}, or null when it was never written. */
	Object value(String keyName);
}
