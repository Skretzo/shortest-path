package shortestpath.leagues;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
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
 * The unlock set is rebuilt from three area-slot varbits which the player
 * picks from on the league tutorial island. Each slot stores a numeric
 * region id (mapping defined in {@link #AREA_VARBIT_TO_REGION}). The
 * mapping is deliberately hard-coded here — these IDs are known not to
 * change during a league season.
 * </p>
 */
public class LeagueModeState
{
	/**
	 * Varbit IDs storing the three player-chosen area unlocks. Slot 0 is
	 * always Varlamore (no varbit). Refresh these from a seasonal-world
	 * cache dump if Jagex renumbers the unlock varbits.
	 */
	static final int AREA_1_VARBIT = 10052;
	static final int AREA_2_VARBIT = 10053;
	static final int AREA_3_VARBIT = 10054;

	/**
	 * Maps the numeric region id stored in an areaN varbit to its
	 * {@link LeagueRegion}.
	 */
	private static final Map<Integer, LeagueRegion> AREA_VARBIT_TO_REGION;

	static
	{
		Map<Integer, LeagueRegion> m = new HashMap<>();
		m.put(1, LeagueRegion.VARLAMORE);
		m.put(2, LeagueRegion.KARAMJA);
		m.put(3, LeagueRegion.ASGARNIA);
		m.put(4, LeagueRegion.KANDARIN);
		m.put(5, LeagueRegion.FREMENNIK);
		m.put(6, LeagueRegion.KOUREND);
		m.put(7, LeagueRegion.WILDERNESS);
		m.put(8, LeagueRegion.MORYTANIA);
		m.put(9, LeagueRegion.DESERT);
		m.put(10, LeagueRegion.TIRANNWN);
		m.put(11, LeagueRegion.MISTHALIN);
		AREA_VARBIT_TO_REGION = Collections.unmodifiableMap(m);
	}

	@Getter
	private boolean seasonal;

	private Set<LeagueRegion> unlockedRegions = EnumSet.noneOf(LeagueRegion.class);

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
			seasonal = false;
			unlockedRegions = EnumSet.noneOf(LeagueRegion.class);
			return;
		}
		EnumSet<WorldType> worldTypes = client.getWorldType();
		seasonal = worldTypes != null && worldTypes.contains(WorldType.SEASONAL);

		EnumSet<LeagueRegion> next = EnumSet.noneOf(LeagueRegion.class);
		if (seasonal)
		{
			// Varlamore is the always-unlocked starting region. Karamja is
			// awarded for free at 80 tasks but flows through the same
			// area-slot varbits as every other pick, so we trust the varbits
			// rather than auto-adding it here.
			next.add(LeagueRegion.VARLAMORE);
			addRegionFromSlot(client, AREA_1_VARBIT, next);
			addRegionFromSlot(client, AREA_2_VARBIT, next);
			addRegionFromSlot(client, AREA_3_VARBIT, next);
		}
		unlockedRegions = next;
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
		if (!seasonal)
		{
			return true;
		}
		if (region.isAlwaysBlocked())
		{
			return false;
		}
		return unlockedRegions.contains(region);
	}

	/**
	 * Whether the supplied tile is in the always-blocked region while the
	 * player is on a seasonal world. Returns {@code false} on non-seasonal
	 * worlds so normal pathfinding is unaffected.
	 */
	public boolean isInBlockedRegion(int packedPoint)
	{
		if (!seasonal)
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
		this.seasonal = seasonal;
		this.unlockedRegions = unlocked == null
			? EnumSet.noneOf(LeagueRegion.class)
			: EnumSet.copyOf(unlocked);
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
