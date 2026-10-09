package shortestpath.scheduler;

import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import shortestpath.ShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.items.ItemChange;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.poh.PohChange;
import shortestpath.settings.ConfigChange;
import shortestpath.settings.Effect;
import shortestpath.spirittree.TreeChange;

/**
 * Owns the "state changed, so what recomputes" policy: producer-declared change
 * facts and client events arrive on the typed channels below and are mapped to
 * scheduler verbs. This is the scheduler's sole policy caller — the verbs it
 * invokes are package-private so the rule is compiler-enforced. The
 * coordinator holds no lock; every channel runs on the client thread where the
 * shell's event handlers ran before it, and the only state it carries is the
 * game-state transition memory that moved with the login gate.
 */
@Slf4j
@Singleton
public class RefreshCoordinator
{
	/**
	 * The restart reasons the tree channel reproduces, keyed by the
	 * {@link TreeChange} key. {@code "varbit:<patch>"} keys match by prefix in
	 * {@link #treeRestartReason}, not by this map.
	 */
	private static final Map<String, String> TREE_RESTART_REASONS = Map.of(
		"profile", "profile change",
		"menu", "spirit trees");

	/**
	 * The log labels the POH channel reproduces, keyed by the {@link PohChange}
	 * key. The channel is log-only — the labels feed the debug/warn lines, not
	 * restarts.
	 */
	private static final Map<String, String> POH_LOG_LABELS = Map.of(
		"profile", "profile load",
		"dialog", "nexus dialog",
		"dialogLine", "nexus dialog line");

	private final PathScheduler scheduler;
	private final ShortestPathConfig config;

	// The login transition memory for gameStateChanged's three-state gate.
	private GameState lastGameState = null;
	private GameState lastLastGameState = null;

	@Inject
	public RefreshCoordinator(PathScheduler scheduler, ShortestPathConfig config)
	{
		this.scheduler = scheduler;
		this.config = config;
	}

	/**
	 * Maps an item-state change fact to its follow-up actions: the declared
	 * {@link Effect#ELIGIBILITY_STALE} effect lazily rebuilds the eligibility
	 * snapshot. {@code null} facts admit nothing and map to no action.
	 */
	public void itemsChanged(ItemChange change)
	{
		if (change != null && change.getEffects().contains(Effect.ELIGIBILITY_STALE))
		{
			scheduler.invalidateEligibility();
		}
	}

	/**
	 * Maps a spirit-tree change fact to its follow-up actions: the declared
	 * {@link Effect#ROUTE_INVALIDATING} effect restarts pathfinding with the
	 * running search's own start and targets. {@code null} facts admit nothing
	 * and map to no action, and with no search running the declare is a no-op.
	 */
	public void treeSetChanged(TreeChange change)
	{
		if (change != null
			&& change.getEffects().contains(Effect.ROUTE_INVALIDATING)
			&& scheduler.getActiveSearch() != null)
		{
			ActiveSearch pathfinder = scheduler.getActiveSearch();
			scheduler.restart(treeRestartReason(change.getKey()), pathfinder.getStart(), pathfinder.getTargets());
		}
	}

	/**
	 * Maps a POH change fact to its follow-up actions. {@link
	 * Effect#DISPLAY_ONLY} carries no action — the keybind map is read lazily
	 * by the display path, which sees it hot by reference — so it is logged and
	 * nothing else runs; any other effect violates the service contract and is
	 * surfaced rather than silently swallowed.
	 */
	public void pohChanged(PohChange change)
	{
		if (change == null || change.getEffects().isEmpty())
		{
			return;
		}
		String why = POH_LOG_LABELS.getOrDefault(change.getKey(), change.getKey());
		for (Effect effect : change.getEffects())
		{
			if (Effect.DISPLAY_ONLY.equals(effect))
			{
				log.debug("POH display refresh: {}", why);
			}
			else
			{
				log.warn("Unhandled POH change effect {} ({})", effect, why);
			}
		}
	}

	/**
	 * Maps a config change fact to its follow-up actions: the backend-prep side
	 * effect warms the selected backend, and a routing-input change restarts
	 * the running search. The debug-overlay side effect stays with the shell,
	 * which owns the overlay manager; {@link Effect#DISPLAY_ONLY} maps to no
	 * action.
	 */
	public void configChanged(ConfigChange change)
	{
		Set<Effect> effects = change.getEffects();
		if (effects.contains(Effect.SIDE_EFFECT_BACKEND_PREP))
		{
			scheduler.prepareBackend();
		}

		// A routing input changed; rerun pathfinding
		if (effects.contains(Effect.ROUTE_INVALIDATING) && scheduler.getActiveSearch() != null)
		{
			ActiveSearch pathfinder = scheduler.getActiveSearch();
			scheduler.restart("config: " + change.getKey(), pathfinder.getStart(), pathfinder.getTargets());
		}
	}

	/**
	 * A world hop queues a config refresh for the next client tick, once the
	 * new world's type (e.g. seasonal) has settled and league-mode
	 * auto-detection can re-resolve.
	 */
	public void worldChanged(int tick)
	{
		scheduler.deferRefresh(tick + 1);
	}

	/**
	 * A {@code LOGGING_IN} → {@code LOADING} → {@code LOGGED_IN} login
	 * transition queues a config refresh for the next client tick, once the
	 * fresh profile's state is readable. Other transitions only update the
	 * transition memory.
	 */
	public void gameStateChanged(GameState state, int tick)
	{
		GameState previousGameState = lastGameState;
		GameState previousPreviousGameState = lastLastGameState;
		lastLastGameState = lastGameState;
		lastGameState = state;

		if (!GameState.LOGGING_IN.equals(previousPreviousGameState)
			|| !GameState.LOADING.equals(previousGameState)
			|| !GameState.LOGGED_IN.equals(lastGameState))
		{
			return;
		}

		scheduler.deferRefresh(tick + 1);
	}

	/**
	 * Runs the deferred-work queue at the tick's drain position. The spirit-tree
	 * observation and the off-route decision occupy their own positions in the
	 * shell's tick sequence, before and after this call respectively.
	 */
	public void gameTick(int tick)
	{
		scheduler.drainDueTasks(tick);
	}

	/**
	 * The tick's off-route decision: when the player reaches a target tile the
	 * path is cleared, and when the player strays too far from the displayed
	 * route the search either clears or restarts from the current position,
	 * per config. With no running search there is nothing to guard — the shell
	 * already declined the tick, and the re-read below keeps that no-op exact.
	 */
	public void offRouteTick(int tick, int currentLocation)
	{
		ActiveSearch pathfinder = scheduler.getActiveSearch();
		if (pathfinder == null)
		{
			return;
		}

		for (int target : pathfinder.getTargets())
		{
			if (WorldPointUtil.distanceBetween(currentLocation, target) < config.reachedDistance())
			{
				scheduler.setTarget(WorldPointUtil.UNDEFINED);
				return;
			}
		}

		if (!scheduler.isStartPointSet() && !scheduler.isNearPath(currentLocation))
		{
			if (config.cancelInstead())
			{
				scheduler.setTarget(WorldPointUtil.UNDEFINED);
				return;
			}
			scheduler.restart("off route", currentLocation, pathfinder.getTargets());
		}
	}

	/**
	 * A plugin message asking for a path to {@code targets} from {@code start}.
	 * An empty target set re-uses the running search's targets — the
	 * recompute-on-same-targets restart the message surface relies on.
	 */
	public void targetRequest(int start, Set<Integer> targets)
	{
		ActiveSearch pathfinder = scheduler.getActiveSearch();
		boolean useOld = targets.isEmpty() && pathfinder != null;
		scheduler.restart("plugin message", start, useOld ? pathfinder.getTargets() : targets, useOld);
	}

	/**
	 * The restart reason for a spirit-tree fact: the literal the shell used to
	 * supply at the declare site, derived from the fact's key so the debug
	 * panel's restart log keeps its familiar text.
	 */
	private static String treeRestartReason(String key)
	{
		if (key.startsWith("varbit:"))
		{
			return "spirit tree varbit";
		}
		return TREE_RESTART_REASONS.getOrDefault(key, key);
	}
}
