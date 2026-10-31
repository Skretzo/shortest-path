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
import shortestpath.transport.BalloonRoute;

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
		return value != null && fallback != null && fallback.getClass().isInstance(value) ? (T) value : fallback;
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
		switch (key.getCoercion())
		{
			case NONE:
				return fallback;
			case TELEPORTATION_ITEM:
				return (T) enumOverride(key, TeleportationItem::fromType, (TeleportationItem) fallback);
			case JEWELLERY_BOX_TIER:
				return (T) enumOverride(key, JewelleryBoxTier::fromType, (JewelleryBoxTier) fallback);
			case TILE_COUNTER:
				return (T) enumOverride(key, TileCounter::fromType, (TileCounter) fallback);
			case TILE_STYLE:
				return (T) enumOverride(key, TileStyle::fromType, (TileStyle) fallback);
			default:
				return override(key, fallback);
		}
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
	@Override
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

	/**
	 * {@code balloonLogBasketRoutes} — the unwritten-key default derives
	 * from the legacy {@code unlockBalloonLogBasket} flag: re-derive it
	 * through the override-aware flag so a payload on that flag still
	 * produces "all routes" (or none) instead of advertising the unlock
	 * while routing sees the base flag's value. A written set equal to the
	 * flag-derived default is indistinguishable from unwritten here, and
	 * re-deriving it loses nothing: its value carried no more than the flag
	 * itself did. Any other written set wins outright.
	 */
	@SuppressWarnings("unchecked")
	public Set<BalloonRoute> balloonLogBasketRoutes()
	{
		Set<BalloonRoute> routes = (Set<BalloonRoute>) baseOf(ConfigKey.BALLOON_LOG_BASKET_ROUTES);
		Set<BalloonRoute> flagDerived = Boolean.TRUE.equals(baseOf(ConfigKey.UNLOCK_BALLOON_LOG_BASKET))
			? EnumSet.allOf(BalloonRoute.class)
			: EnumSet.noneOf(BalloonRoute.class);
		if (routes == null || routes.equals(flagDerived))
		{
			return bool(ConfigKey.UNLOCK_BALLOON_LOG_BASKET)
				? EnumSet.allOf(BalloonRoute.class)
				: EnumSet.noneOf(BalloonRoute.class);
		}
		return routes;
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

	// ---- Stock-panel-visible keyed keys ----------------------------------
	// These rows stay keyed (the base read runs through hiddenBase), so the
	// item getters delegate to the same val/bool/num coercion helpers the
	// hidden-key consumers use — a getter that fell through to the interface
	// default would report the declared default, not the stored value.

	@Override
	public boolean avoidWilderness()
	{
		return bool(ConfigKey.AVOID_WILDERNESS);
	}

	@Override
	public boolean collisionAwareBlockedTargets()
	{
		return bool(ConfigKey.COLLISION_AWARE_BLOCKED_TARGETS);
	}

	@Override
	public boolean useSailingMoves()
	{
		return bool(ConfigKey.USE_SAILING_MOVES);
	}

	@Override
	public int currencyThreshold()
	{
		return num(ConfigKey.CURRENCY_THRESHOLD);
	}

	@Override
	public boolean cancelInstead()
	{
		return bool(ConfigKey.CANCEL_INSTEAD);
	}

	@Override
	public int recalculateDistance()
	{
		return num(ConfigKey.RECALCULATE_DISTANCE);
	}

	@Override
	public int reachedDistance()
	{
		return num(ConfigKey.FINISH_DISTANCE);
	}

	@Override
	public int unreachableTargetDistance()
	{
		return num(ConfigKey.UNREACHABLE_TARGET_DISTANCE_THRESHOLD);
	}

	@Override
	public boolean showUnreachableText()
	{
		return bool(ConfigKey.SHOW_UNREACHABLE_TEXT);
	}

	@Override
	public int calculationCutoff()
	{
		return num(ConfigKey.CALCULATION_CUTOFF);
	}

	@Override
	public TeleportationItem useTeleportationItems()
	{
		return enumOverride(ConfigKey.USE_TELEPORTATION_ITEMS, TeleportationItem::fromType,
			(TeleportationItem) baseOf(ConfigKey.USE_TELEPORTATION_ITEMS));
	}

	@Override
	public int aStarHeuristicWeight()
	{
		return num(ConfigKey.A_STAR_HEURISTIC_WEIGHT);
	}

	@Override
	public boolean unlockCanoeAxe()
	{
		return bool(ConfigKey.UNLOCK_CANOE_AXE);
	}

	@Override
	public boolean unlockXericsHonour()
	{
		return bool(ConfigKey.UNLOCK_XERICS_HONOUR);
	}

	@Override
	public boolean unlockDragontoothPassage()
	{
		return bool(ConfigKey.UNLOCK_DRAGONTOOTH_PASSAGE);
	}

	@Override
	public boolean unlockMorUlRek()
	{
		return bool(ConfigKey.UNLOCK_MOR_UL_REK);
	}

	@Override
	public boolean respawnPrifddinas()
	{
		return bool(ConfigKey.RESPAWN_PRIFDDINAS);
	}

	@Override
	public boolean includeBankPath()
	{
		return bool(ConfigKey.INCLUDE_BANK_PATH);
	}

	@Override
	public int costBankVisit()
	{
		return num(ConfigKey.COST_BANK_VISIT);
	}

	@Override
	public boolean showBankPickupInfo()
	{
		return bool(ConfigKey.SHOW_BANK_PICKUP_INFO);
	}

	@Override
	public boolean highlightBankPickupItems()
	{
		return bool(ConfigKey.HIGHLIGHT_BANK_PICKUP_ITEMS);
	}

	@Override
	public boolean showTransportInfo()
	{
		return bool(ConfigKey.SHOW_TRANSPORT_INFO);
	}

	@Override
	public boolean highlightSpellbookSpells()
	{
		return bool(ConfigKey.HIGHLIGHT_SPELLBOOK_SPELLS);
	}

	@Override
	public boolean highlightInventoryItems()
	{
		return bool(ConfigKey.HIGHLIGHT_INVENTORY_ITEMS);
	}

	@Override
	public int tileCounterStep()
	{
		return num(ConfigKey.TILE_COUNTER_STEP);
	}
}
