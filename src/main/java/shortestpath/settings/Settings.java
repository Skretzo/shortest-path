package shortestpath.settings;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import javax.inject.*;
import javax.swing.SwingUtilities;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.*;
import net.runelite.client.events.ConfigChanged;
import shortestpath.ShortestPathConfig;

/**
 * Reads and writes the plugin's settings. A key's value is, in order: the
 * override another plugin sent through the plugin-message API (when it has
 * the key's type), the stored value, then the key's default.
 *
 * <p>The sidebar panel edits stored values with {@link #write} and shows them
 * with {@link #configured}, which ignores overrides.
 *
 * <p>This package is a leaf: it must not reference the plugin class.
 */
@Singleton
@Slf4j
public class Settings
{
	public static final String CONFIG_GROUP = "shortestpath";

	private final ConfigManager configManager;
	/** A key's stored value, or null when it was never written. */
	private final Function<ConfigKey, Object> stored;
	/** Stored values read since the last config change; overlays read settings every frame. */
	private final Map<ConfigKey, Optional<Object>> cache = new ConcurrentHashMap<>();
	private final Map<String, ConfigItemDescriptor> itemDescriptors = new HashMap<>();
	/** The overrides as received, unknown keys included, so they can be echoed back. */
	@Getter
	private volatile Map<String, Object> overrides = Map.of();
	private final Map<String, List<Runnable>> listeners = new ConcurrentHashMap<>();
	private final Map<String, Thread> writeInFlight = new ConcurrentHashMap<>();

	@Inject
	public Settings(ConfigManager configManager, ShortestPathConfig config)
	{
		this.configManager = configManager;
		this.stored = key -> cache.computeIfAbsent(key, k -> Optional.ofNullable(k.read(configManager))).orElse(null);
		ConfigDescriptor descriptor = configManager.getConfigDescriptor(config);
		for (ConfigItemDescriptor item : descriptor == null ? List.<ConfigItemDescriptor>of() : descriptor.getItems())
		{
			itemDescriptors.putIfAbsent(item.key(), item);
		}
	}

	private Settings(ConfigManager configManager, Function<ConfigKey, Object> stored)
	{
		this.configManager = configManager;
		this.stored = stored;
	}

	/**
	 * Settings for tests: stored values come from {@code config} when it is a
	 * {@link KeyedValues}, otherwise every key reads its default. Writes go to
	 * {@code configManager}, which may be null.
	 */
	public static Settings wrap(ConfigManager configManager, ShortestPathConfig config)
	{
		return new Settings(configManager, config instanceof KeyedValues
			? key -> ((KeyedValues) config).value(key.getKey())
			: key -> null);
	}

	public static Settings wrap(ShortestPathConfig config)
	{
		return wrap(null, config);
	}

	/** The value routing and drawing use: override, else stored, else default. */
	@SuppressWarnings("unchecked")
	public <T> T get(ConfigKey key)
	{
		Object value = key.coerce(overrides.get(key.getKey()));
		return (T) (value != null ? value : storedOrDefault(key, true));
	}

	public boolean bool(ConfigKey key)
	{
		return Boolean.TRUE.equals(get(key));
	}

	public int num(ConfigKey key)
	{
		Object value = get(key);
		return value instanceof Number ? ((Number) value).intValue() : 0;
	}

	/** The value the player configured, ignoring overrides: stored, else default. */
	@SuppressWarnings("unchecked")
	public <T> T configured(String key)
	{
		ConfigKey row = ConfigKey.forKey(key);
		if (row == null)
		{
			throw new IllegalArgumentException("unknown config key: " + key);
		}
		return (T) storedOrDefault(row, false);
	}

	private Object storedOrDefault(ConfigKey key, boolean withOverrides)
	{
		Object value = stored.apply(key);
		if (value != null)
		{
			return value;
		}
		ConfigKey flag = key.getFlag();
		if (flag == null)
		{
			return key.getDef();
		}
		return key.flagDefault(withOverrides ? bool(flag) : Boolean.TRUE.equals(storedOrDefault(flag, false)));
	}

	public void applyOverrides(Map<String, Object> payload)
	{
		if (payload != null && !payload.isEmpty())
		{
			overrides = Collections.unmodifiableMap(new HashMap<>(payload));
		}
	}

	public void clearOverrides()
	{
		overrides = Map.of();
	}

	/** Panel metadata for a key: the bundled {@code config-items.tsv} row, else the {@code @ConfigItem}. */
	public ConfigItems.Meta item(String key)
	{
		ConfigItems.Meta meta = ConfigItems.of(key);
		ConfigItemDescriptor descriptor = itemDescriptors.get(key);
		if (meta != null || descriptor == null)
		{
			return meta;
		}
		Range range = descriptor.getRange();
		return new ConfigItems.Meta(key, descriptor.getItem().name(), descriptor.getItem().description(),
			range == null ? null : range.min(), range == null ? null : range.max());
	}

	/**
	 * Stores a value and runs the key's listeners now. The {@link ConfigChanged}
	 * echo of this write does not run them a second time.
	 */
	public void write(String key, Object value)
	{
		if (configManager == null)
		{
			throw new UnsupportedOperationException("settings without a config manager cannot write");
		}
		writeInFlight.put(key, Thread.currentThread());
		try
		{
			configManager.setConfiguration(CONFIG_GROUP, key, value);
			cache.clear();
			fireListeners(key);
		}
		finally
		{
			writeInFlight.remove(key);
		}
	}

	/** Runs {@code listener} whenever {@code key} changes; the returned runnable unregisters it. */
	public Runnable listen(String key, Runnable listener)
	{
		listeners.compute(key, (k, keyed) ->
		{
			keyed = keyed != null ? keyed : new CopyOnWriteArrayList<>();
			keyed.add(listener);
			return keyed;
		});
		return () -> listeners.computeIfPresent(key, (k, keyed) ->
		{
			keyed.remove(listener);
			return keyed.isEmpty() ? null : keyed;
		});
	}

	void fireListeners(String key)
	{
		for (Runnable listener : listeners.getOrDefault(key, List.of()))
		{
			try
			{
				listener.run();
			}
			catch (RuntimeException e)
			{
				log.warn("settings listener for key {} threw; continuing with remaining listeners", key, e);
			}
		}
	}

	/** The change a config event makes, or null when it is for another plugin. */
	public ConfigChange onConfigChanged(ConfigChanged event)
	{
		if (event == null || !CONFIG_GROUP.equals(event.getGroup()))
		{
			return null;
		}
		cache.clear();
		String key = event.getKey();
		if (writeInFlight.get(key) != Thread.currentThread() && listeners.containsKey(key))
		{
			SwingUtilities.invokeLater(() -> fireListeners(key));
		}
		ConfigKey row = ConfigKey.forKey(key);
		return new ConfigChange(key, row == null ? Set.of() : row.getEffects());
	}
}
