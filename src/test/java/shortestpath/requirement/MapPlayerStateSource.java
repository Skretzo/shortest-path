package shortestpath.requirement;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import shortestpath.items.OwnedItems;

/**
 * Map-backed {@link PlayerStateSource} for client-free requirement tests.
 * Every read is answered from test-supplied tables so
 * {@link RequirementContext#capture} runs end-to-end without a
 * {@code Client}: varbits and varplayers, boosted and real skill levels,
 * total and combat level (the combat slot falls through to the interface
 * default, computed from the real levels), quest states, the quest-points
 * maximum, player location, modal-widget state, item containers, rune-pouch
 * contents and the league world types.
 *
 * <p>Thread contract: the fake treats the client-thread checks as satisfied
 * by default ({@link #isOnClientThread()} true, {@link #checkOnClientThread()}
 * a no-op). Flipping {@link #offClientThread()} makes the checks honour the
 * loud contract, which lets tests prove the contract on the fake itself when
 * needed. State getters never consult the flag — a map fake exists precisely
 * so capture can be exercised anywhere.
 */
final class MapPlayerStateSource implements PlayerStateSource
{
	private GameState gameState = GameState.LOGGED_IN;
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Integer> varps = new HashMap<>();
	private final Map<Skill, Integer> boostedSkillLevels = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> realSkillLevels = new EnumMap<>(Skill.class);
	private int totalLevel;
	private final Map<Quest, QuestState> questStates = new EnumMap<>(Quest.class);
	private int maximumQuestPoints;
	private WorldPoint localPlayerWorldLocation;
	private boolean modalWidgetOpen;
	private final Map<Integer, ItemContainer> itemContainers = new HashMap<>();
	private final Map<Integer, Integer> runePouchRunes = new HashMap<>();
	private EnumSet<WorldType> worldTypes = EnumSet.noneOf(WorldType.class);
	private boolean onClientThread = true;

	MapPlayerStateSource gameState(GameState state)
	{
		gameState = state;
		return this;
	}

	/** Sets a varbit value; later calls overwrite, so post-capture mutation tests can change it. */
	MapPlayerStateSource varbit(int id, int value)
	{
		varbits.put(id, value);
		return this;
	}

	/** Sets a varplayer value; later calls overwrite. */
	MapPlayerStateSource varp(int id, int value)
	{
		varps.put(id, value);
		return this;
	}

	MapPlayerStateSource boostedSkillLevel(Skill skill, int level)
	{
		boostedSkillLevels.put(skill, level);
		return this;
	}

	MapPlayerStateSource realSkillLevel(Skill skill, int level)
	{
		realSkillLevels.put(skill, level);
		return this;
	}

	MapPlayerStateSource totalLevel(int level)
	{
		totalLevel = level;
		return this;
	}

	MapPlayerStateSource questState(Quest quest, QuestState state)
	{
		questStates.put(quest, state);
		return this;
	}

	MapPlayerStateSource maximumQuestPoints(int points)
	{
		maximumQuestPoints = points;
		return this;
	}

	MapPlayerStateSource localPlayerWorldLocation(WorldPoint point)
	{
		localPlayerWorldLocation = point;
		return this;
	}

	MapPlayerStateSource modalWidgetOpen(boolean open)
	{
		modalWidgetOpen = open;
		return this;
	}

	MapPlayerStateSource itemContainer(int inventoryId, ItemContainer container)
	{
		itemContainers.put(inventoryId, container);
		return this;
	}

	MapPlayerStateSource runePouchRune(int runeId, int amount)
	{
		runePouchRunes.put(runeId, amount);
		return this;
	}

	MapPlayerStateSource worldTypes(WorldType first, WorldType... rest)
	{
		worldTypes = EnumSet.of(first, rest);
		return this;
	}

	/** Puts the fake off the client thread: the thread check becomes loud. */
	MapPlayerStateSource offClientThread()
	{
		onClientThread = false;
		return this;
	}

	@Override
	public GameState gameState()
	{
		return gameState;
	}

	@Override
	public int varbit(int varbitId)
	{
		return varbits.getOrDefault(varbitId, 0);
	}

	@Override
	public int varp(int varpId)
	{
		return varps.getOrDefault(varpId, 0);
	}

	@Override
	public int boostedSkillLevel(Skill skill)
	{
		return boostedSkillLevels.getOrDefault(skill, 0);
	}

	@Override
	public int realSkillLevel(Skill skill)
	{
		return realSkillLevels.getOrDefault(skill, 0);
	}

	@Override
	public int totalLevel()
	{
		return totalLevel;
	}

	@Override
	public QuestState questState(Quest quest)
	{
		return questStates.getOrDefault(quest, QuestState.NOT_STARTED);
	}

	@Override
	public int maximumQuestPoints()
	{
		return maximumQuestPoints;
	}

	@Override
	public WorldPoint localPlayerWorldLocation()
	{
		return localPlayerWorldLocation;
	}

	@Override
	public boolean modalWidgetOpen()
	{
		return modalWidgetOpen;
	}

	@Override
	public ItemContainer itemContainer(int inventoryId)
	{
		return itemContainers.get(inventoryId);
	}

	@Override
	public Map<Integer, Integer> runePouchContents()
	{
		return runePouchRunes;
	}

	@Override
	public void addRunePouchContents(Map<Integer, Integer> owned)
	{
		// Same shape as the production reader: the pouch runes are only added
		// when the carried set already holds a rune pouch.
		if (OwnedItems.RUNE_POUCHES.stream().noneMatch(owned::containsKey))
		{
			return;
		}
		runePouchRunes.forEach((runeId, amount) -> owned.merge(runeId, amount, Integer::sum));
	}

	@Override
	public EnumSet<WorldType> worldType()
	{
		return worldTypes;
	}

	@Override
	public void checkOnClientThread()
	{
		if (!onClientThread)
		{
			throw new IllegalStateException(
				"player-state reads must run on the client thread; route the caller through the client thread");
		}
	}

	@Override
	public boolean isOnClientThread()
	{
		return onClientThread;
	}
}
