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
	/** Another player's house the player stands in, for routing; empty in one's own or outside. */
	private volatile Map<ConfigKey, Object> visitedHouse = Map.of();
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
		if (value == null)
		{
			value = visitedHouse.get(key);
		}
		return (T) (value != null ? value : storedOrDefault(key, true));
	}

	/**
	 * Routes with another player's house while the player stands in it:
	 * {@code values} replaces the per-character house keys for routing only
	 * ({@link #configured} and stored values are untouched); an empty map
	 * goes back to the player's own. Returns the change to hand the refresh
	 * coordinator, or null when nothing changed.
	 */
	public ConfigChange visitHouse(Map<ConfigKey, Object> values)
	{
		Map<ConfigKey, Object> next = Map.copyOf(values);
		if (next.equals(visitedHouse))
		{
			return null;
		}
		visitedHouse = next;
		return new ConfigChange("visited house", EnumSet.of(Effect.ROUTE_INVALIDATING));
	}

	/** Whether routing uses another player's house just now. */
	public boolean isVisitingHouse()
	{
		return !visitedHouse.isEmpty();
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
			store(key, value);
			cache.clear();
			fireListeners(key);
		}
		finally
		{
			writeInFlight.remove(key);
		}
	}

	/**
	 * Stores what the game showed for one character's house. Unlike
	 * {@link #write}, the listeners run on the Swing thread through the
	 * {@link ConfigChanged} echo, so this may be called from the client
	 * thread. Nothing is written when logged out or when the character
	 * already stores that value; returns whether it wrote.
	 */
	public boolean writeForCharacter(ConfigKey key, Object value)
	{
		if (configManager == null || !key.isPerCharacter() || Objects.equals(key.readOwn(configManager), value))
		{
			return false;
		}
		// RuneLite makes the character's profile on its first write while logged in; logged out it writes nothing.
		configManager.setRSProfileConfiguration(CONFIG_GROUP, key.getKey(), value);
		cache.clear();
		return configManager.getRSProfileKey() != null;
	}

	/**
	 * The logged-in character changed, so every per-character key may read a
	 * different value. Drops cached reads and refreshes the controls showing
	 * those keys; the returned change carries the routing effect.
	 */
	public ConfigChange characterChanged()
	{
		cache.clear();
		Set<Effect> effects = EnumSet.noneOf(Effect.class);
		for (ConfigKey key : ConfigKey.perCharacterKeys())
		{
			effects.addAll(key.getEffects());
			if (listeners.containsKey(key.getKey()))
			{
				SwingUtilities.invokeLater(() -> fireListeners(key.getKey()));
			}
		}
		return new ConfigChange("character", effects);
	}

	/** Per-character keys go to the logged-in character when there is one. */
	private void store(String key, Object value)
	{
		ConfigKey row = ConfigKey.forKey(key);
		if (row != null && row.isPerCharacter())
		{
			// Makes the character's profile if it has none yet; does nothing while logged out.
			configManager.setRSProfileConfiguration(CONFIG_GROUP, key, value);
			if (configManager.getRSProfileKey() != null)
			{
				return;
			}
		}
		configManager.setConfiguration(CONFIG_GROUP, key, value);
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
