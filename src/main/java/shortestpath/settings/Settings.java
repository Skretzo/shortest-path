package shortestpath.settings;

import java.awt.Color;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;

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
import shortestpath.settings.TeleportationItem;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;
import shortestpath.transport.TransportType;
import shortestpath.transport.TransportTypeConfig;

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

	/**
	 * Keyed listeners registered through {@link #listen}. Read on whichever
	 * thread a {@link ConfigChanged} arrives on, so the lists are copy-on-write.
	 */
	private final Map<String, List<Runnable>> listeners = new ConcurrentHashMap<>();

	/**
	 * Keys with a {@link #write} call on the stack, mapped to the thread
	 * performing the write. A {@link ConfigChanged} arriving on that same
	 * thread while its key is marked here is the write's own synchronous
	 * echo: it still republishes and classifies, but its listeners already
	 * fired inside {@link #write}, so the external-delivery path skips it.
	 * A same-key event arriving on a different thread is genuinely external
	 * and still delivers.
	 */
	private final Map<String, Thread> writeInFlight = new ConcurrentHashMap<>();

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
	 * Snapshots the routing settings the gate chain reads this refresh: the
	 * effective transport-type enablement (post-{@code disableUnless}), the
	 * teleportation-item mode and the POH toggles. Called once per refresh —
	 * after the transport-type derivations have run — so a built chain can
	 * never observe later config mutation. The built policy is published into
	 * the {@link #routing()} slot before returning, so the snapshot always
	 * reflects a complete refresh rather than mid-derivation state.
	 *
	 * <p>The scalar arguments are the effective values the caller already
	 * computed this refresh — production never re-reads config live.
	 */
	public RoutingPolicy buildRoutingPolicy(TransportTypeConfig transportTypeConfig,
		boolean usePoh, boolean usePohFairyRing, boolean usePohSpiritTree,
		boolean usePohObelisk, Set<PohNexusPortal> enabledPohNexusPortals,
		Set<PohMountedItem> enabledPohMountedItems, JewelleryBoxTier pohJewelleryBoxTier,
		int currencyThreshold, boolean includeBankPath)
	{
		EnumSet<TransportType> enabledTypes = EnumSet.noneOf(TransportType.class);
		for (TransportType type : TransportType.values())
		{
			if (transportTypeConfig.isEnabled(type))
			{
				enabledTypes.add(type);
			}
		}
		RoutingPolicy policy = new RoutingPolicy(enabledTypes,
			transportTypeConfig.getTeleportationItemSetting(),
			usePoh, usePohFairyRing, usePohSpiritTree, usePohObelisk,
			enabledPohNexusPortals, enabledPohMountedItems, pohJewelleryBoxTier,
			currencyThreshold, includeBankPath);
		publishRouting(policy);
		return policy;
	}

	// ---- Panel write/listen/keyed-read contract ---------------------------

	/**
	 * The sole mutation path for persisted settings: delegates straight to
	 * {@link ConfigManager#setConfiguration}, which dispatches the
	 * {@link ConfigChanged} synchronously on this thread. The echo is marked
	 * write-originated so {@link #onConfigChanged} republishes and classifies
	 * it but skips external listener delivery; same-key listeners fire here
	 * instead, synchronously, before {@code write} returns. Values pass
	 * through untouched — {@code Set}-typed values reach the manager as-is.
	 *
	 * <p>Unknown keys pass through to the manager unchanged, matching the
	 * permissive behaviour this contract replaces.
	 */
	public void write(String key, Object value)
	{
		if (configManager == null)
		{
			throw new UnsupportedOperationException(
				"a wrap-seam settings service has no config manager to write through");
		}
		writeInFlight.put(key, Thread.currentThread());
		try
		{
			configManager.setConfiguration(CONFIG_GROUP, key, value);
			fireListeners(key);
		}
		finally
		{
			writeInFlight.remove(key);
		}
	}

	/**
	 * Register a callback for changes to {@code key}: it runs synchronously
	 * inside {@link #write} for write-originated changes and on the EDT for
	 * external {@link ConfigChanged} events. Multiple listeners per key are
	 * supported and run in registration order.
	 *
	 * <p>Returns a handle that unregisters this listener when run. The
	 * service is a singleton, so a caller whose lifecycle ends — a panel
	 * rebuilt on every plugin enable — must drop its registrations or the
	 * dead component graph stays reachable and keeps firing on each
	 * subsequent change.
	 */
	public Runnable listen(String key, Runnable listener)
	{
		List<Runnable> keyed = listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
		keyed.add(listener);
		return () ->
		{
			keyed.remove(listener);
			if (keyed.isEmpty())
			{
				listeners.remove(key, keyed);
			}
		};
	}

	/**
	 * The configured value for {@code key} — the base proxy read, never the
	 * override-adjusted effective value, so the UI always displays what is
	 * stored. Unknown keys fail loudly.
	 */
	public Object configuredValue(String key)
	{
		ConfigKey row = ConfigKey.forKey(key);
		if (row == null)
		{
			throw new IllegalArgumentException("unknown config key: " + key);
		}
		return row.getGetter().apply(configured);
	}

	/** Typed convenience wrapper over {@link #configuredValue}. */
	public boolean configuredBool(String key)
	{
		return Boolean.TRUE.equals(configuredValue(key));
	}

	/**
	 * Typed convenience wrapper over {@link #configuredValue}: returns the
	 * stored {@code Set} untouched, or {@code null} when the key holds no
	 * set value.
	 */
	public Set<?> configuredSet(String key)
	{
		Object value = configuredValue(key);
		return value instanceof Set ? (Set<?>) value : null;
	}

	private void fireListeners(String key)
	{
		List<Runnable> keyed = listeners.get(key);
		if (keyed == null)
		{
			return;
		}
		for (Runnable listener : keyed)
		{
			listener.run();
		}
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
	 * group; an in-group key with no registry row produces a fact carrying
	 * an empty effect set.
	 *
	 * <p>Registered listeners for the event's key are delivered on the EDT —
	 * the same marshalling the panel applied to changes made elsewhere —
	 * except when the event is the echo of an in-flight {@link #write}: that
	 * change already delivered its same-key listeners synchronously inside
	 * {@code write}, so the external path skips it and no write ever echoes
	 * back to the writer twice. The write marker is thread-scoped: a
	 * same-key event arriving on a different thread mid-write is external,
	 * not the echo, and still delivers.
	 */
	public ConfigChange onConfigChanged(ConfigChanged event)
	{
		if (event == null || !CONFIG_GROUP.equals(event.getGroup()))
		{
			return null;
		}
		republish();
		String key = event.getKey();
		if (writeInFlight.get(key) != Thread.currentThread() && listeners.containsKey(key))
		{
			SwingUtilities.invokeLater(() -> fireListeners(key));
		}
		return new ConfigChange(key, effectsOf(key));
	}

	private static Set<Effect> effectsOf(String key)
	{
		ConfigKey row = ConfigKey.forKey(key);
		return row == null ? Set.of() : row.getEffects();
	}

	/**
	 * The keyed coercion helpers reproduce the legacy {@code override()} overload
	 * discipline for call sites whose override key is not the getter's own key
	 * name (the transport-type-driven reads): a payload value only takes effect
	 * when it carries exactly the type the key's coercion demands.
	 */
	public boolean coerceBoolean(String key, boolean base)
	{
		return coerceBoolean(key, base, published.overrides);
	}

	/**
	 * Snapshot variant: coerce against an overrides map the caller already
	 * captured, so a run of coercions observes one publication rather than
	 * re-reading the volatile slot per call.
	 */
	public boolean coerceBoolean(String key, boolean base, Map<String, Object> overrides)
	{
		Object value = overrides.get(key);
		return value instanceof Boolean ? (boolean) value : base;
	}

	public int coerceInt(String key, int base)
	{
		return coerceInt(key, base, published.overrides);
	}

	public int coerceInt(String key, int base, Map<String, Object> overrides)
	{
		Object value = overrides.get(key);
		return value instanceof Integer ? (int) value : base;
	}

	public Color coerceColor(String key, Color base)
	{
		Object value = published.overrides.get(key);
		return value instanceof Color ? (Color) value : base;
	}

	public TeleportationItem coerceTeleportationItem(String key, TeleportationItem base)
	{
		return coerceTeleportationItem(key, base, published.overrides);
	}

	public TeleportationItem coerceTeleportationItem(String key, TeleportationItem base,
		Map<String, Object> overrides)
	{
		return coerceStringEnum(key, TeleportationItem::fromType, base, overrides);
	}

	public JewelleryBoxTier coerceJewelleryBoxTier(String key, JewelleryBoxTier base)
	{
		return coerceStringEnum(key, JewelleryBoxTier::fromType, base, published.overrides);
	}

	public TileCounter coerceTileCounter(String key, TileCounter base)
	{
		return coerceStringEnum(key, TileCounter::fromType, base, published.overrides);
	}

	public TileStyle coerceTileStyle(String key, TileStyle base)
	{
		return coerceStringEnum(key, TileStyle::fromType, base, published.overrides);
	}

	private <T> T coerceStringEnum(String key, Function<String, T> fromType, T base,
		Map<String, Object> overrides)
	{
		Object value = overrides.get(key);
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
	 * Rebuild the views off the current override map and swap the published
	 * slot. Synchronized so a republish that captured map N cannot finish
	 * after — and silently revert — an {@link #applyOverrides}/{@link
	 * #clearOverrides} publication of map N+1: the read of
	 * {@code published.overrides} and the installation of the rebuilt
	 * snapshot run under the same monitor as every other publication.
	 */
	private synchronized void republish()
	{
		publish(published == null ? Map.of() : published.overrides);
	}

	private synchronized void publish(Map<String, Object> overrides)
	{
		EffectiveConfig effective = new EffectiveConfig(configured, overrides);
		published = new Published(
			overrides,
			effective,
			new DisplayView(effective, configured),
			new PohView(effective, configured),
			new BankView(effective),
			new UnlocksView(effective),
			new LifecycleView(effective, configured));
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
	 * Immutable snapshot of the display-facing values the plugin used to cache
	 * per change — path/transport drawing toggles, colours, counters, and label
	 * state.
	 *
	 * <p>Member mapping (the former plugin field bag, one home each):
	 * {@code draw*}/{@code show*}/{@code highlight*} toggles, the eight
	 * {@code colour*} values, {@code tileCounterStep},
	 * {@code unreachableTargetDistance} (key
	 * {@code unreachableTargetDistanceThreshold}), {@code showTileCounter},
	 * {@code pathStyle}, and {@code unreachableText}. The bank-pickup display
	 * pair ({@code showBankPickupInfo}, {@code colourBankPickupHighlight})
	 * lives here rather than in {@link BankView} because they are draw-state,
	 * not routing inputs.
	 *
	 * <p>Every member reads the effective (override-aware) value except
	 * {@code unreachableText}, which reads the configured value: payloads
	 * never coerced it, and it stays raw here.
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

		private DisplayView(ShortestPathConfig effective, ShortestPathConfig configured)
		{
			drawCollisionMap = effective.drawCollisionMap();
			drawMap = effective.drawMap();
			drawMinimap = effective.drawMinimap();
			drawTiles = effective.drawTiles();
			drawTransports = effective.drawTransports();
			showTransportInfo = effective.showTransportInfo();
			showBankPickupInfo = effective.showBankPickupInfo();
			showUnreachableText = effective.showUnreachableText();
			highlightBankPickupItems = effective.highlightBankPickupItems();
			highlightSpellbookSpells = effective.highlightSpellbookSpells();
			highlightInventoryItems = effective.highlightInventoryItems();
			showTeleportPulse = effective.showTeleportPulse();
			colourCollisionMap = effective.colourCollisionMap();
			colourPath = effective.colourPath();
			colourPathCalculating = effective.colourPathCalculating();
			colourPathUnreachable = effective.colourPathUnreachable();
			colourText = effective.colourText();
			colourTransports = effective.colourTransports();
			colourBankPickupHighlight = effective.colourBankPickupHighlight();
			colourTeleportPulse = effective.colourTeleportPulse();
			tileCounterStep = effective.tileCounterStep();
			unreachableTargetDistance = effective.unreachableTargetDistance();
			// Payloads never coerced this key — it stays a raw read.
			unreachableText = configured.unreachableText();
			showTileCounter = effective.showTileCounter();
			pathStyle = effective.pathStyle();
		}
	}

	/**
	 * Immutable snapshot of the player-owned-house routing inputs.
	 *
	 * <p>The {@code usePoh*} toggles and {@code pohJewelleryBoxTier} read the
	 * effective (override-aware) value. The two {@code Set} members read the
	 * configured value: they have no payload coercion, so callers see the same
	 * bypass semantics the raw {@code config.pohNexusPortals()}/
	 * {@code config.pohMountedItems()} reads always had.
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

		private PohView(ShortestPathConfig effective, ShortestPathConfig configured)
		{
			usePoh = effective.usePoh();
			usePohFairyRing = effective.usePohFairyRing();
			usePohSpiritTree = effective.usePohSpiritTree();
			useTeleportationPortalsPoh = effective.useTeleportationPortalsPoh();
			usePohMountedItems = effective.usePohMountedItems();
			usePohObelisk = effective.usePohObelisk();
			pohJewelleryBoxTier = effective.pohJewelleryBoxTier();
			// Set-typed keys are bypass reads — payloads never coerce them.
			Set<PohNexusPortal> portals = configured.pohNexusPortals();
			pohNexusPortals = portals == null ? Set.of() : Set.copyOf(portals);
			Set<PohMountedItem> mounted = configured.pohMountedItems();
			pohMountedItems = mounted == null ? Set.of() : Set.copyOf(mounted);
		}
	}

	/**
	 * Immutable snapshot of the bank-path routing inputs: whether routes may
	 * detour via a bank, the currency ceiling for paid transports, and the
	 * cost charged per bank visit. All members read the effective
	 * (override-aware) value. The bank-pickup <em>display</em> pair lives in
	 * {@link DisplayView} — this view covers routing inputs only.
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
	 * expose to the client, toggled in config. All members read the effective
	 * (override-aware) value.
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
	 *
	 * <p>These members mirror how the shell and refresh path always read them:
	 * every member is a raw configured read — lifecycle side-effect inputs
	 * never consulted the override map — except {@code postTransports}, which
	 * the transport-posting path has always read through the override layer.
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

		private LifecycleView(ShortestPathConfig effective, ShortestPathConfig configured)
		{
			pathfinderBackend = configured.pathfinderBackend();
			calculationCutoff = configured.calculationCutoff();
			recalculateDistance = configured.recalculateDistance();
			reachedDistance = configured.reachedDistance();
			cancelInstead = configured.cancelInstead();
			drawDebugPanel = configured.drawDebugPanel();
			postTransports = effective.postTransports();
		}
	}
}
