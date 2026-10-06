package shortestpath.requirement;

import java.util.EnumSet;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;

/**
 * The player-state reads the requirement capture path performs, exposed
 * behind an interface so capture runs against an injectable source rather
 * than the {@code Client} directly. Every read corresponds 1:1 to a call the
 * refresh path used to make on the client: login state, varbits and
 * varplayers, boosted and real skill levels, total and combat level, quest
 * state, the quest-points database maximum, the local player's world
 * location, modal-widget state, inventory/worn item containers, rune-pouch
 * contents and the league world type.
 *
 * <p>Thread contract: capture reads are only valid on the client thread.
 * Every state getter is expected to call {@link #checkOnClientThread()}
 * first and throw {@link IllegalStateException} off the client thread — an
 * off-thread capture previously raced the client and silently skipped the
 * refresh, so it now fails loudly instead. {@link #isOnClientThread()} is
 * the non-throwing predicate for callers that may legitimately run off the
 * client thread and need a stale-read envelope instead of a crash.
 *
 * <p>The sole production implementation is {@link ClientPlayerStateSource};
 * tests may supply map-backed fakes that answer from fixed values and treat
 * the thread checks as satisfied.
 */
public interface PlayerStateSource
{
	/**
	 * The client's login state; capture gates on {@link GameState#LOGGED_IN}.
	 */
	GameState gameState();

	/**
	 * The current value of one varbit.
	 */
	int varbit(int varbitId);

	/**
	 * The current value of one varplayer.
	 */
	int varp(int varpId);

	/**
	 * The boosted (effective) level of one skill.
	 */
	int boostedSkillLevel(Skill skill);

	/**
	 * The real (unboosted) level of one skill.
	 */
	int realSkillLevel(Skill skill);

	/**
	 * The player's total level.
	 */
	int totalLevel();

	/**
	 * The player's combat level, derived from the real skill levels through
	 * the shared {@link #computeCombatLevel} formula so every source computes
	 * it identically.
	 */
	default int combatLevel()
	{
		return computeCombatLevel(
			realSkillLevel(Skill.ATTACK),
			realSkillLevel(Skill.STRENGTH),
			realSkillLevel(Skill.DEFENCE),
			realSkillLevel(Skill.HITPOINTS),
			realSkillLevel(Skill.MAGIC),
			realSkillLevel(Skill.RANGED),
			realSkillLevel(Skill.PRAYER));
	}

	/**
	 * The state of one quest as the source reports it.
	 */
	QuestState questState(Quest quest);

	/**
	 * The maximum attainable quest points, summed over the released quest
	 * rows of the quest-points database table.
	 */
	int maximumQuestPoints();

	/**
	 * The local player's world location, or {@code null} when the player or
	 * its location is unavailable. Feeds in-region spirit-tree sampling.
	 */
	WorldPoint localPlayerWorldLocation();

	/**
	 * Whether a modal widget is open; varbits are not transmitted while one
	 * is, so live patch sampling must be skipped.
	 */
	boolean modalWidgetOpen();

	/**
	 * One item container by inventory id (inventory/worn).
	 */
	ItemContainer itemContainer(int inventoryId);

	/**
	 * The runes inside the rune pouch as rune id to amount, read from the
	 * pouch varbits (current wherever the pouch is, including the bank).
	 */
	Map<Integer, Integer> runePouchContents();

	/**
	 * Adds the runes in the rune pouch to {@code owned} when it already holds
	 * a rune pouch.
	 */
	void addRunePouchContents(Map<Integer, Integer> owned);

	/**
	 * The world types of the world the player is on (seasonal/deadman league
	 * detection).
	 */
	EnumSet<WorldType> worldType();

	/**
	 * Throws {@link IllegalStateException} when the calling thread is not the
	 * client thread. Every state getter calls this first, so an off-thread
	 * capture fails loudly instead of racing the client.
	 */
	void checkOnClientThread();

	/**
	 * Whether the calling thread is the client thread. Non-throwing: the
	 * eligibility envelope uses it to return a stale snapshot off-thread
	 * rather than fail.
	 */
	boolean isOnClientThread();

	/**
	 * Pure combat-level formula, shared by every source implementation.
	 */
	static int computeCombatLevel(int attack, int strength, int defence, int hitpoints, int magic, int ranged, int prayer)
	{
		// Integer division is intentional here — it matches the OSRS floor(x/2) steps in the formula.
		double base = 0.25 * (defence + hitpoints + Math.floorDiv(prayer, 2));
		double melee = (13 * (attack + strength)) / 40.0;
		double range = (13 * (3 * Math.floorDiv(ranged, 2))) / 40.0;
		double mage = (13 * (3 * Math.floorDiv(magic, 2))) / 40.0;
		return (int) Math.floor(base + Math.max(Math.max(melee, range), Math.max(melee, mage)));
	}
}
