package shortestpath.leagues;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.WorldType;

/**
 * Snapshot of the player's Demonic Pacts League state, refreshed once per
 * {@code PathfinderConfig.refresh()} cycle (i.e. on world change, login, or
 * any config-driven recompute).
 *
 * <p>
 * The Pathfinder asks two questions of this object on the hot path:
 * </p>
 * <ul>
 *   <li>{@link #isSeasonal()} — is the player currently on a Leagues world?
 *       When false, all league-specific filtering is bypassed.</li>
 *   <li>{@link #isUnlocked(LeagueRegion)} — has the player unlocked the
 *       supplied region? Always-unlocked regions
 *       ({@link LeagueRegion#isAlwaysUnlocked()}) return {@code true}
 *       regardless of seasonal status; always-blocked regions
 *       ({@link LeagueRegion#isAlwaysBlocked()}) always return {@code false}
 *       when seasonal.</li>
 * </ul>
 *
 * <p>
 * The unlock set is rebuilt from the {@code LEAGUE_AREA_SELECTION_*} varbit
 * slots ({@link #AREA_SELECTION_VARBITS}). Each slot stores a numeric area
 * id matching the wiki's enumeration (mapping defined in
 * {@link #AREA_VARBIT_TO_REGION}):
 * </p>
 * <ul>
 *   <li>Slot 0 ({@code 10662}) — pre-set to Varlamore on a seasonal world.</li>
 *   <li>Slot 1 ({@code 10663}) — Karamja, awarded for free with the player's
 *       first paid pick at 80 tasks.</li>
 *   <li>Slots 2-3 ({@code 10664}/{@code 10665}) — the player's three area
 *       picks at 200/300/450 tasks.</li>
 *   <li>Slots 4-5 ({@code 10666}/{@code 10667}) — reserved by the game for
 *       additional bonus unlocks; we read them defensively.</li>
 * </ul>
 *
 * <p>
 * The mapping is deliberately hard-coded here — these IDs are known not to
 * change during a league season.
 * </p>
 */
public class LeagueModeState
{
	/**
	 * Varbit IDs storing the league area unlocks. The values match
	 * {@code LEAGUE_AREA_SELECTION_0..5} from RuneLite's gameval VarbitID
	 * table. Slot 0 is the auto-set Varlamore slot, slot 1 is the Karamja
	 * free pick, and the remaining slots correspond to the three player
	 * picks at 200/300/450 tasks plus two bonus slots reserved by the game.
	 */
	static final int[] AREA_SELECTION_VARBITS = {
		10662, 10663, 10664, 10665, 10666, 10667,
	};

	/**
	 * Maps the numeric area id stored in a {@code LEAGUE_AREA_SELECTION_*}
	 * varbit to its {@link LeagueRegion}. Numbering was found through trial
	 * and error (id 1 = Misthalin is included for completeness even though
	 * it is never selectable).
	 */
	private static final Map<Integer, LeagueRegion> AREA_VARBIT_TO_REGION;

	static
	{
		AREA_VARBIT_TO_REGION = Map.ofEntries(
			Map.entry(1, LeagueRegion.MISTHALIN),
			Map.entry(2, LeagueRegion.KARAMJA),
			Map.entry(3, LeagueRegion.ASGARNIA),
			Map.entry(4, LeagueRegion.KANDARIN),
			Map.entry(5, LeagueRegion.MORYTANIA),
			Map.entry(6, LeagueRegion.DESERT),
			Map.entry(7, LeagueRegion.TIRANNWN),
			Map.entry(8, LeagueRegion.FREMENNIK),
			Map.entry(11, LeagueRegion.WILDERNESS),
			Map.entry(20, LeagueRegion.KOUREND),
			Map.entry(21, LeagueRegion.VARLAMORE));
	}

	/**
	 * Immutable per-refresh league facts. The pathfinding executor thread
	 * reads this object while the client thread refreshes it, so the state is
	 * swapped as a single volatile reference rather than mutated in place.
	 */
	private static final class State
	{
		private static final State EMPTY =
			new State(false, false, EnumSet.noneOf(LeagueRegion.class));

		private final boolean seasonal;
		private final boolean deadman;
		private final Set<LeagueRegion> unlockedRegions;

		private State(boolean seasonal, boolean deadman, Set<LeagueRegion> unlockedRegions)
		{
			this.seasonal = seasonal;
			this.deadman = deadman;
			this.unlockedRegions = unlockedRegions;
		}
	}

	private volatile State state = State.EMPTY;

	/**
	 * Re-reads {@link Client#getWorldType()} and the area-unlock varbits.
	 * Called from {@code PathfinderConfig.refresh()} which already runs on
	 * world change, login, and config edits.
	 *
	 * <p>
	 * Off the game thread (or on a {@code null} client) this resets to a
	 * non-seasonal state with no extra unlocks; this is the safe default
	 * because non-seasonal logic mirrors normal pathfinding.
	 * </p>
	 */
	public void refresh(Client client)
	{
		if (client == null)
		{
			state = State.EMPTY;
			return;
		}
		EnumSet<WorldType> worldTypes = client.getWorldType();
		boolean seasonal = worldTypes != null && worldTypes.contains(WorldType.SEASONAL);
		boolean deadman = worldTypes != null && worldTypes.contains(WorldType.DEADMAN);

		EnumSet<LeagueRegion> next = EnumSet.noneOf(LeagueRegion.class);
		if (seasonal)
		{
			for (int varbitId : AREA_SELECTION_VARBITS)
			{
				addRegionFromSlot(client, varbitId, next);
			}
		}
		state = new State(seasonal, deadman, Collections.unmodifiableSet(next));
	}

	public boolean isSeasonal()
	{
		return state.seasonal;
	}

	public boolean isDeadman()
	{
		return state.deadman;
	}

	/**
	 * Whether the supplied region is currently traversable. Outside of
	 * seasonal mode every region is considered unlocked.
	 */
	public boolean isUnlocked(LeagueRegion region)
	{
		if (region == null)
		{
			return true;
		}
		if (region.isAlwaysUnlocked())
		{
			return true;
		}
		State current = state;
		if (!current.seasonal)
		{
			return true;
		}
		if (region.isAlwaysBlocked())
		{
			return false;
		}
		return current.unlockedRegions.contains(region);
	}

	/**
	 * Whether the supplied tile is in the always-blocked region while the
	 * player is on a seasonal world. Returns {@code false} on non-seasonal
	 * worlds so normal pathfinding is unaffected.
	 */
	public boolean isInBlockedRegion(int packedPoint)
	{
		if (!state.seasonal)
		{
			return false;
		}
		return LeagueRegionChecker.getRegion(packedPoint).isAlwaysBlocked();
	}

	/**
	 * Test hook: forces the seasonal flag and unlock set without touching
	 * the client.
	 */
	public void setForTest(boolean seasonal, Set<LeagueRegion> unlocked)
	{
		this.state = new State(seasonal, state.deadman, unlocked == null
			? EnumSet.noneOf(LeagueRegion.class)
			: Collections.unmodifiableSet(EnumSet.copyOf(unlocked)));
	}

	private static void addRegionFromSlot(Client client, int varbitId, Set<LeagueRegion> out)
	{
		int value = client.getVarbitValue(varbitId);
		if (value <= 0)
		{
			return;
		}
		LeagueRegion region = AREA_VARBIT_TO_REGION.get(value);
		if (region != null)
		{
			out.add(region);
		}
	}
}
