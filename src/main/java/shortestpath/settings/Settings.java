package shortestpath.settings;

import java.awt.Color;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.ShortestPathConfig;
import shortestpath.TileCounter;
import shortestpath.TileStyle;
import shortestpath.requirement.RoutingPolicy;
import shortestpath.requirement.TeleportationItem;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;

/**
 * The injected owner of configured state: the base {@link ShortestPathConfig}
 * proxy, the runtime override map received through plugin-message payloads, and
 * the immutable snapshots consumers read.
 *
 * <p>Two publication cadences exist by design:
 * <ul>
 * <li>{@link #display()}, {@link #poh()}, {@link #bank()}, {@link #unlocks()},
 * and {@link #lifecycle()} are immutable view snapshots republished on every
 * override apply/clear and every {@link ConfigChanged} for this group. They are
 * captured from {@link #effective()}, so payload overrides and persisted config
 * both reach them.</li>
 * <li>{@link #routing()} is a different cadence: it returns the most recently
 * built {@link RoutingPolicy}, published by the refresh path after
 * transport-type derivations run — never republished by a config event.</li>
 * </ul>
 *
 * <p>The override map is stored verbatim — keys are never validated on apply,
 * so unknown keys survive for the raw echo — and every publication is an
 * immutable copy swapped through a single volatile reference, so readers never
 * observe a torn map/decorator/view combination.
 *
 * <p>This package is a leaf: it must not reference the plugin class.
 */
@Singleton
public class Settings
{
	private static final String CONFIG_GROUP = resolveConfigGroup();

	/**
	 * Retained for the persisted write path (panel edits write through the
	 * config manager); reads here go through the injected config proxy.
	 */
	private final ConfigManager configManager;
	private final ShortestPathConfig configured;

	/**
	 * The one publication slot. Overridden values, the effective decorator, and
	 * every view snapshot are derived together and swapped together, so a reader
	 * never sees a payload apply's map without its matching views.
	 */
	private volatile Published published;

	/**
	 * The most recently built routing policy. Published by the refresh path once
	 * transport-type derivations settle; {@code null} until the first build.
	 */
	private volatile RoutingPolicy routing;

	@Inject
	public Settings(ConfigManager configManager, ShortestPathConfig configured)
	{
		this.configManager = configManager;
		this.configured = configured;
		republish();
	}

	/**
	 * Test/harness seam: a service over the given config with no overrides and
	 * no config manager, so {@link #effective()} and the views pass the stub
	 * config through unchanged.
	 */
	Settings(ShortestPathConfig configured)
	{
		this(null, configured);
	}

	public static Settings wrap(ShortestPathConfig config)
	{
		return new Settings(config);
	}

	private static String resolveConfigGroup()
	{
		ConfigGroup group = ShortestPathConfig.class.getAnnotation(ConfigGroup.class);
		return group == null ? "" : group.value();
	}

	/**
	 * The raw config proxy — configured values with no overrides applied.
	 */
	public ShortestPathConfig configured()
	{
		return configured;
	}

	/**
	 * The override-applying decorator over {@link #configured()}: coercible keys
	 * read the payload map, every other key reads the configured value.
	 */
	public EffectiveConfig effective()
	{
		return published.effective;
	}

	/**
	 * The stored runtime override map, verbatim and immutable. Unknown keys are
	 * preserved for the payload echo.
	 */
	public Map<String, Object> rawOverrides()
	{
		return published.overrides;
	}

	public DisplayView display()
	{
		return published.display;
	}

	public PohView poh()
	{
		return published.poh;
	}

	public BankView bank()
	{
		return published.bank;
	}

	public UnlocksView unlocks()
	{
		return published.unlocks;
	}

	public LifecycleView lifecycle()
	{
		return published.lifecycle;
	}

	/**
	 * The routing policy built by the last refresh, or {@code null} until the
	 * first build publishes one.
	 */
	public RoutingPolicy routing()
	{
		return routing;
	}

	/**
	 * Publish a freshly built routing policy. Called from the refresh path after
	 * disableUnless derivations, so the snapshot always reflects a complete
	 * refresh rather than mid-derivation state.
	 */
	public void publishRouting(RoutingPolicy policy)
	{
		routing = policy;
	}

	/**
	 * Apply a plugin-message {@code "config"} payload as the runtime override
	 * map. The map is copied and stored immutably; an empty (or null) payload
	 * is a no-op and does not clear existing overrides — clearing is the
	 * explicit {@link #clearOverrides()} path.
	 */
	public void applyOverrides(Map<String, Object> payload)
	{
		if (payload == null || payload.isEmpty())
		{
			return;
		}
		publish(Collections.unmodifiableMap(new HashMap<>(payload)));
	}

	/**
	 * Drop all runtime overrides; effective reads return to configured values.
	 */
	public void clearOverrides()
	{
		publish(Map.of());
	}

	/**
	 * Consume a config-change event: republish the view snapshots off the
	 * updated base values and return a fact describing the change for the
	 * shell to act on. Returns {@code null} for events outside this config
	 * group.
	 */
	public ConfigChange onConfigChanged(ConfigChanged event)
	{
		if (event == null || !CONFIG_GROUP.equals(event.getGroup()))
		{
			return null;
		}
		republish();
		return new ConfigChange(event.getKey(), effectsOf(event.getKey()));
	}

	private static Set<Effect> effectsOf(String key)
	{
		if ("drawDebugPanel".equals(key))
		{
			return EnumSet.of(Effect.SIDE_EFFECT_DEBUG_OVERLAY);
		}
		if ("pathfinderBackend".equals(key))
		{
			return EnumSet.of(Effect.SIDE_EFFECT_BACKEND_PREP, Effect.ROUTE_INVALIDATING);
		}
		ConfigKey row = ConfigKey.forKey(key);
		if (row != null && row.isRouteInvalidating())
		{
			return EnumSet.of(Effect.ROUTE_INVALIDATING);
		}
		return EnumSet.of(Effect.DISPLAY_ONLY);
	}

	/**
	 * The keyed coercion helpers reproduce the legacy {@code override()} overload
	 * discipline for call sites whose override key is not the getter's own key
	 * name (the transport-type-driven reads): a payload value only takes effect
	 * when it carries exactly the type the key's coercion demands.
	 */
	public boolean coerceBoolean(String key, boolean base)
	{
		Object value = published.overrides.get(key);
		return value instanceof Boolean ? (boolean) value : base;
	}

	public int coerceInt(String key, int base)
	{
		Object value = published.overrides.get(key);
		return value instanceof Integer ? (int) value : base;
	}

	public Color coerceColor(String key, Color base)
	{
		Object value = published.overrides.get(key);
		return value instanceof Color ? (Color) value : base;
	}

	public TeleportationItem coerceTeleportationItem(String key, TeleportationItem base)
	{
		return coerceStringEnum(key, TeleportationItem::fromType, base);
	}

	public JewelleryBoxTier coerceJewelleryBoxTier(String key, JewelleryBoxTier base)
	{
		return coerceStringEnum(key, JewelleryBoxTier::fromType, base);
	}

	public TileCounter coerceTileCounter(String key, TileCounter base)
	{
		return coerceStringEnum(key, TileCounter::fromType, base);
	}

	public TileStyle coerceTileStyle(String key, TileStyle base)
	{
		return coerceStringEnum(key, TileStyle::fromType, base);
	}

	private <T> T coerceStringEnum(String key, Function<String, T> fromType, T base)
	{
		Object value = published.overrides.get(key);
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

	private void republish()
	{
		publish(published == null ? Map.of() : published.overrides);
	}

	private void publish(Map<String, Object> overrides)
	{
		EffectiveConfig effective = new EffectiveConfig(configured, overrides);
		published = new Published(
			overrides,
			effective,
			new DisplayView(effective),
			new PohView(effective),
			new BankView(effective),
			new UnlocksView(effective),
			new LifecycleView(effective));
	}

	private static final class Published
	{
		private final Map<String, Object> overrides;
		private final EffectiveConfig effective;
		private final DisplayView display;
		private final PohView poh;
		private final BankView bank;
		private final UnlocksView unlocks;
		private final LifecycleView lifecycle;

		private Published(Map<String, Object> overrides, EffectiveConfig effective,
			DisplayView display, PohView poh, BankView bank, UnlocksView unlocks,
			LifecycleView lifecycle)
		{
			this.overrides = overrides;
			this.effective = effective;
			this.display = display;
			this.poh = poh;
			this.bank = bank;
			this.unlocks = unlocks;
			this.lifecycle = lifecycle;
		}
	}

	/**
	 * Immutable snapshot of the display-facing values the plugin caches per
	 * change — path/transport drawing toggles, colours, and label state.
	 */
	@Getter
	@Accessors(fluent = true)
	public static final class DisplayView
	{
		private final boolean drawCollisionMap;
		private final boolean drawMap;
		private final boolean drawMinimap;
		private final boolean drawTiles;
		private final boolean drawTransports;
		private final boolean showTransportInfo;
		private final boolean showBankPickupInfo;
		private final boolean showUnreachableText;
		private final boolean highlightBankPickupItems;
		private final boolean highlightSpellbookSpells;
		private final boolean highlightInventoryItems;
		private final boolean showTeleportPulse;
		private final Color colourCollisionMap;
		private final Color colourPath;
		private final Color colourPathCalculating;
		private final Color colourPathUnreachable;
		private final Color colourText;
		private final Color colourTransports;
		private final Color colourBankPickupHighlight;
		private final Color colourTeleportPulse;
		private final int tileCounterStep;
		private final int unreachableTargetDistance;
		private final String unreachableText;
		private final TileCounter showTileCounter;
		private final TileStyle pathStyle;

		private DisplayView(ShortestPathConfig config)
		{
			drawCollisionMap = config.drawCollisionMap();
			drawMap = config.drawMap();
			drawMinimap = config.drawMinimap();
			drawTiles = config.drawTiles();
			drawTransports = config.drawTransports();
			showTransportInfo = config.showTransportInfo();
			showBankPickupInfo = config.showBankPickupInfo();
			showUnreachableText = config.showUnreachableText();
			highlightBankPickupItems = config.highlightBankPickupItems();
			highlightSpellbookSpells = config.highlightSpellbookSpells();
			highlightInventoryItems = config.highlightInventoryItems();
			showTeleportPulse = config.showTeleportPulse();
			colourCollisionMap = config.colourCollisionMap();
			colourPath = config.colourPath();
			colourPathCalculating = config.colourPathCalculating();
			colourPathUnreachable = config.colourPathUnreachable();
			colourText = config.colourText();
			colourTransports = config.colourTransports();
			colourBankPickupHighlight = config.colourBankPickupHighlight();
			colourTeleportPulse = config.colourTeleportPulse();
			tileCounterStep = config.tileCounterStep();
			unreachableTargetDistance = config.unreachableTargetDistance();
			unreachableText = config.unreachableText();
			showTileCounter = config.showTileCounter();
			pathStyle = config.pathStyle();
		}
	}

	/**
	 * Immutable snapshot of the player-owned-house routing inputs.
	 */
	@Getter
	@Accessors(fluent = true)
	public static final class PohView
	{
		private final boolean usePoh;
		private final boolean usePohFairyRing;
		private final boolean usePohSpiritTree;
		private final boolean useTeleportationPortalsPoh;
		private final boolean usePohMountedItems;
		private final boolean usePohObelisk;
		private final JewelleryBoxTier pohJewelleryBoxTier;
		private final Set<PohNexusPortal> pohNexusPortals;
		private final Set<PohMountedItem> pohMountedItems;

		private PohView(ShortestPathConfig config)
		{
			usePoh = config.usePoh();
			usePohFairyRing = config.usePohFairyRing();
			usePohSpiritTree = config.usePohSpiritTree();
			useTeleportationPortalsPoh = config.useTeleportationPortalsPoh();
			usePohMountedItems = config.usePohMountedItems();
			usePohObelisk = config.usePohObelisk();
			pohJewelleryBoxTier = config.pohJewelleryBoxTier();
			Set<PohNexusPortal> portals = config.pohNexusPortals();
			pohNexusPortals = portals == null ? Set.of() : Set.copyOf(portals);
			Set<PohMountedItem> mounted = config.pohMountedItems();
			pohMountedItems = mounted == null ? Set.of() : Set.copyOf(mounted);
		}
	}

	/**
	 * Immutable snapshot of the bank-path routing inputs.
	 */
	@Getter
	@Accessors(fluent = true)
	public static final class BankView
	{
		private final boolean includeBankPath;
		private final int currencyThreshold;
		private final int costBankVisit;

		private BankView(ShortestPathConfig config)
		{
			includeBankPath = config.includeBankPath();
			currencyThreshold = config.currencyThreshold();
			costBankVisit = config.costBankVisit();
		}
	}

	/**
	 * Immutable snapshot of the declared unlocks — states the game does not
	 * expose to the client, toggled in config.
	 */
	@Getter
	@Accessors(fluent = true)
	public static final class UnlocksView
	{
		private final boolean unlockCanoeAxe;
		private final boolean unlockXericsHonour;
		private final boolean unlockDragontoothPassage;

		private UnlocksView(ShortestPathConfig config)
		{
			unlockCanoeAxe = config.unlockCanoeAxe();
			unlockXericsHonour = config.unlockXericsHonour();
			unlockDragontoothPassage = config.unlockDragontoothPassage();
		}
	}

	/**
	 * Immutable snapshot of the running-search lifecycle inputs: backend
	 * selection, search cutoffs, and the debug/post toggles that act on a live
	 * search rather than display state.
	 */
	@Getter
	@Accessors(fluent = true)
	public static final class LifecycleView
	{
		private final PathfinderBackend pathfinderBackend;
		private final int calculationCutoff;
		private final int recalculateDistance;
		private final int reachedDistance;
		private final boolean cancelInstead;
		private final boolean drawDebugPanel;
		private final boolean postTransports;

		private LifecycleView(ShortestPathConfig config)
		{
			pathfinderBackend = config.pathfinderBackend();
			calculationCutoff = config.calculationCutoff();
			recalculateDistance = config.recalculateDistance();
			reachedDistance = config.reachedDistance();
			cancelInstead = config.cancelInstead();
			drawDebugPanel = config.drawDebugPanel();
			postTransports = config.postTransports();
		}
	}
}
