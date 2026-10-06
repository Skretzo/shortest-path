package shortestpath.leagues;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.WorldType;
import shortestpath.requirement.PlayerStateSource;

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
	 * The current facts, replaced wholesale by every {@link #refresh} /
	 * {@link #setForTest}. Held as one immutable object so a search thread
	 * reading through this live state always sees a consistent snapshot —
	 * never torn fields from two overlapping refreshes — and so
	 * {@link #snapshot()} can hand a per-refresh requirement context a frozen
	 * view that later refreshes cannot mutate.
	 */
	private volatile LeagueModeSnapshot current = LeagueModeSnapshot.NON_SEASONAL;

	/**
	 * Re-reads the world type and the area-unlock varbits through the player
	 * state source. Called from {@code PathfinderConfig.refresh()} which
	 * already runs on world change, login, and config edits.
	 *
	 * <p>
	 * On a {@code null} source this resets to a non-seasonal state with no
	 * extra unlocks; this is the safe default because non-seasonal logic
	 * mirrors normal pathfinding.
	 * </p>
	 */
	public void refresh(PlayerStateSource source)
	{
		if (source == null)
		{
			current = LeagueModeSnapshot.NON_SEASONAL;
			return;
		}
		EnumSet<WorldType> worldTypes = source.worldType();
		boolean seasonal = worldTypes != null && worldTypes.contains(WorldType.SEASONAL);
		boolean deadman = worldTypes != null && worldTypes.contains(WorldType.DEADMAN);

		EnumSet<LeagueRegion> next = EnumSet.noneOf(LeagueRegion.class);
		if (seasonal)
		{
			for (int varbitId : AREA_SELECTION_VARBITS)
			{
				addRegionFromSlot(source, varbitId, next);
			}
		}
		current = new LeagueModeSnapshot(seasonal, deadman, next);
	}

	/**
	 * The facts of the last refresh as an immutable value. A per-refresh
	 * requirement context retains this instead of this live object, so its
	 * verdicts cannot track a later refresh's writes.
	 */
	public LeagueModeSnapshot snapshot()
	{
		return current;
	}

	public boolean isSeasonal()
	{
		return current.isSeasonal();
	}

	/**
	 * Whether the player is on a Deadman Mode world ({@link WorldType#DEADMAN};
	 * tournament worlds are flagged DEADMAN too). Tracked here because this
	 * object already owns world-type detection.
	 */
	public boolean isDeadman()
	{
		return current.isDeadman();
	}

	/**
	 * Whether the supplied region is currently traversable. Outside of
	 * seasonal mode every region is considered unlocked.
	 */
	public boolean isUnlocked(LeagueRegion region)
	{
		return current.isUnlocked(region);
	}

	/**
	 * Whether the supplied tile is in the always-blocked region while the
	 * player is on a seasonal world. Returns {@code false} on non-seasonal
	 * worlds so normal pathfinding is unaffected.
	 */
	public boolean isInBlockedRegion(int packedPoint)
	{
		return current.isInBlockedRegion(packedPoint);
	}

	/**
	 * Test hook: forces the seasonal flag and unlock set without touching
	 * the client. The Deadman flag keeps its current value, matching the
	 * pre-snapshot behaviour where this only replaced seasonal state.
	 */
	public void setForTest(boolean seasonal, Set<LeagueRegion> unlocked)
	{
		current = new LeagueModeSnapshot(seasonal, current.isDeadman(), unlocked);
	}

	private static void addRegionFromSlot(PlayerStateSource source, int varbitId, Set<LeagueRegion> out)
	{
		int value = source.varbit(varbitId);
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
