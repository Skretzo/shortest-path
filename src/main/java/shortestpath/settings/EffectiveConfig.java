package shortestpath.settings;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import net.runelite.client.config.Keybind;
import shortestpath.ShortestPathConfig;
import shortestpath.TileCounter;
import shortestpath.TileStyle;
import shortestpath.poh.JewelleryBoxTier;

/**
 * Decorator over a base {@link ShortestPathConfig} that applies the runtime
 * override map. Visible {@code @ConfigItem} methods are implemented
 * explicitly: methods whose return type has a coercion discipline consult
 * the override map under their own key and fall back to the base value when
 * the payload value is absent, the wrong type, or — for the string-parsed
 * enums — a string {@code fromType} does not recognise.
 *
 * <p>Hidden keys (raw {@code ConfigManager} keys, no interface method) read
 * through the typed {@link #val}/{@link #bool}/{@link #num}/{@link #set}
 * helpers: the base value comes from the injected
 * {@code hiddenBase} resolver, and keys with a coercion discipline consult
 * the override map the same way. Keys marked {@link ConfigKey.Coercion#NONE}
 * are inert — payload values never reach them.
 */
public final class EffectiveConfig implements ShortestPathConfig
{
	private final ShortestPathConfig base;
	private final Map<String, Object> overrides;
	private final Function<ConfigKey, Object> hiddenBase;

	EffectiveConfig(ShortestPathConfig base, Map<String, Object> overrides,
		Function<ConfigKey, Object> hiddenBase)
	{
		this.base = base;
		this.overrides = overrides;
		this.hiddenBase = hiddenBase;
	}

	@SuppressWarnings("unchecked")
	private <T> T override(ConfigKey key, T fallback)
	{
		Object value = overrides.get(key.getKey());
		return value != null && (fallback == null || fallback.getClass().isInstance(value)) ? (T) value : fallback;
	}

	/**
	 * The effective value for a string-parsed enum key: the payload string
	 * coerced through {@code fromType} when it produces a non-null value,
	 * {@code base} otherwise (absent key, non-String payload, unrecognised
	 * string). Mirrors the {@code Settings.coerceStringEnum} coercion
	 * discipline.
	 */
	private <T> T enumOverride(ConfigKey key, Function<String, T> fromType, T base)
	{
		Object value = overrides.get(key.getKey());
		if (value instanceof String)
		{
			T parsed = fromType.apply((String) value);
			if (parsed != null)
			{
				return parsed;
			}
		}
		return base;
	}

	/**
	 * The base (configured-level) value for a key: the proxy getter for
	 * visible keys, the keyed read for hidden keys.
	 */
	private Object baseOf(ConfigKey key)
	{
		return key.isKeyed() ? hiddenBase.apply(key) : key.getConfiguredGetter().apply(base);
	}

	/**
	 * The effective value for a key: the payload-coerced value for keys with
	 * a coercion discipline, the base value for inert (NONE) keys.
	 */
	@SuppressWarnings("unchecked")
	public <T> T val(ConfigKey key)
	{
		T fallback = (T) baseOf(key);
		return key.getCoercion() == ConfigKey.Coercion.NONE ? fallback : override(key, fallback);
	}

	/** Effective value of a {@code boolean} key. */
	public boolean bool(ConfigKey key)
	{
		return Boolean.TRUE.equals(val(key));
	}

	/** Effective value of an {@code int} key. */
	public int num(ConfigKey key)
	{
		Object value = val(key);
		return value instanceof Number ? ((Number) value).intValue() : 0;
	}

	/** Effective value of a {@code Set}-typed key. */
	@SuppressWarnings("unchecked")
	public <E> Set<E> set(ConfigKey key)
	{
		return (Set<E>) val(key);
	}

	/** {@code showTileCounter} — string-parsed enum coercion. */
	public TileCounter showTileCounter()
	{
		return enumOverride(ConfigKey.SHOW_TILE_COUNTER, TileCounter::fromType,
			(TileCounter) baseOf(ConfigKey.SHOW_TILE_COUNTER));
	}

	/** {@code pohJewelleryBoxTier} — string-parsed enum coercion. */
	public JewelleryBoxTier pohJewelleryBoxTier()
	{
		return enumOverride(ConfigKey.POH_JEWELLERY_BOX_TIER, JewelleryBoxTier::fromType,
			(JewelleryBoxTier) baseOf(ConfigKey.POH_JEWELLERY_BOX_TIER));
	}

	@Override
	public boolean showSidebarPanel()
	{
		Object value = overrides.get(ConfigKey.SHOW_SIDEBAR_PANEL.getKey());
		return value instanceof Boolean ? (boolean) value : base.showSidebarPanel();
	}

	@Override
	public boolean drawMap()
	{
		return bool(ConfigKey.DRAW_MAP);
	}

	@Override
	public boolean drawMinimap()
	{
		return bool(ConfigKey.DRAW_MINIMAP);
	}

	@Override
	public boolean drawTiles()
	{
		return bool(ConfigKey.DRAW_TILES);
	}

	@Override
	public boolean drawClickPoints()
	{
		return bool(ConfigKey.DRAW_CLICK_POINTS);
	}

	@Override
	public TileStyle pathStyle()
	{
		return enumOverride(ConfigKey.PATH_STYLE, TileStyle::fromType, base.pathStyle());
	}

	@Override
	public boolean showTeleportPulse()
	{
		return bool(ConfigKey.SHOW_TELEPORT_PULSE);
	}

	@Override
	public Color colourPath()
	{
		return val(ConfigKey.COLOUR_PATH);
	}

	@Override
	public Color colourPathCalculating()
	{
		return val(ConfigKey.COLOUR_PATH_CALCULATING);
	}

	@Override
	public Color colourPathUnreachable()
	{
		return val(ConfigKey.COLOUR_PATH_UNREACHABLE);
	}

	@Override
	public Color colourTransports()
	{
		return val(ConfigKey.COLOUR_TRANSPORTS);
	}

	@Override
	public Color colourCollisionMap()
	{
		return val(ConfigKey.COLOUR_COLLISION_MAP);
	}

	@Override
	public Color colourText()
	{
		return val(ConfigKey.COLOUR_TEXT);
	}

	@Override
	public Color colourTeleportPulse()
	{
		return val(ConfigKey.COLOUR_TELEPORT_PULSE);
	}

	@Override
	public Color colourBankPickupHighlight()
	{
		return val(ConfigKey.COLOUR_BANK_PICKUP_HIGHLIGHT);
	}

	@Override
	public Keybind clearPathHotkey()
	{
		return base.clearPathHotkey();
	}

	@Override
	public boolean drawTransports()
	{
		return bool(ConfigKey.DRAW_TRANSPORTS);
	}

	@Override
	public boolean drawCollisionMap()
	{
		return bool(ConfigKey.DRAW_COLLISION_MAP);
	}

	@Override
	public boolean drawDebugPanel()
	{
		return bool(ConfigKey.DRAW_DEBUG_PANEL);
	}

	@Override
	public boolean postTransports()
	{
		return bool(ConfigKey.POST_TRANSPORTS);
	}

	@Override
	public String unreachableText()
	{
		return base.unreachableText();
	}

	@Override
	public String builtTeleportationBoxes()
	{
		return base.builtTeleportationBoxes();
	}

	@Override
	public void setBuiltTeleportationBoxes(String content)
	{
		base.setBuiltTeleportationBoxes(content);
	}

	@Override
	public String builtTeleportationPortalsPoh()
	{
		return base.builtTeleportationPortalsPoh();
	}

	@Override
	public void setBuiltTeleportationPortalsPoh(String content)
	{
		base.setBuiltTeleportationPortalsPoh(content);
	}

	@Override
	public String blockedTeleportItems()
	{
		return base.blockedTeleportItems();
	}

	@Override
	public boolean unlockBalloonLogBasket()
	{
		Object value = overrides.get(ConfigKey.UNLOCK_BALLOON_LOG_BASKET.getKey());
		return value instanceof Boolean ? (boolean) value : base.unlockBalloonLogBasket();
	}

}
