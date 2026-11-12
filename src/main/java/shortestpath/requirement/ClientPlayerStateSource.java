package shortestpath.requirement;

import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import shortestpath.requirement.model.*;
import net.runelite.api.widgets.WidgetModalMode;
import shortestpath.items.OwnedItems;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@link Client}-backed {@link PlayerStateSource} and the only client
 * reader on the requirement capture path. Every getter checks
 * {@link #checkOnClientThread()} first, so an off-thread capture read throws
 * {@link IllegalStateException} instead of racing the client — the refresh
 * path used to no-op silently in that situation, which made a missed caller
 * indistinguishable from a correct one. {@link #isOnClientThread()} is the
 * same comparison without the throw, for the eligibility envelope's
 * stale-snapshot contract.
 */
@AllArgsConstructor
@Slf4j
public final class ClientPlayerStateSource implements PlayerStateSource
{
	// [proc,get_music_varp](int)(int): takes a one-based MUSICMULTI index, returns that varp's current value.
	public static final int GET_MUSIC_VARP = 7305;

	private final Client client;


	@Override
	public GameState gameState()
	{
		checkOnClientThread();
		return client.getGameState();
	}

	@Override
	public int varbit(int varbitId)
	{
		checkOnClientThread();
		return client.getVarbitValue(varbitId);
	}

	@Override
	public int varp(int varpId)
	{
		checkOnClientThread();
		return client.getVarpValue(varpId);
	}

	@Override
	public int boostedSkillLevel(Skill skill)
	{
		checkOnClientThread();
		return client.getBoostedSkillLevel(skill);
	}

	@Override
	public int realSkillLevel(Skill skill)
	{
		checkOnClientThread();
		return client.getRealSkillLevel(skill);
	}

	@Override
	public int totalLevel()
	{
		checkOnClientThread();
		return client.getTotalLevel();
	}

	@Override
	public QuestState questState(Quest quest)
	{
		checkOnClientThread();
		return quest.getState(client);
	}

	@Override
	public MusicTracks musicTracks()
	{
		checkOnClientThread();
		int unlocked = 0;
		int required = 0;
		List<String> lockedTracks = new ArrayList<>();
		Map<Integer, Integer> varpValues = new HashMap<>();
		for (int row : client.getDBTableRows(DBTableID.Music.ID))
		{
			// Holiday is an event ID; -1 means the track is not tied to a holiday.
			if ((Integer) client.getDBTableField(row, DBTableID.Music.COL_RELEASE_TYPE, 0)[0] == 0 ||
				(Integer) client.getDBTableField(row, DBTableID.Music.COL_HIDDEN, 0)[0] != 0 ||
				(Integer) client.getDBTableField(row, DBTableID.Music.COL_HOLIDAY, 0)[0] != -1)
			{
				continue;
			}
			required++;
			if ((Integer) client.getDBTableField(row, DBTableID.Music.COL_AUTOMATIC_UNLOCK, 0)[0] != 0)
			{
				unlocked++;
				continue;
			}
			// One-based MUSICMULTI index, passed to get_music_varp
			int varpIndex = (Integer) client.getDBTableField(row, DBTableID.Music.COL_VARIABLE, 0)[0];
			int bit = (Integer) client.getDBTableField(row, DBTableID.Music.COL_VARIABLE, 1)[0];
			boolean isUnlocked = false;
			if (varpIndex >= 1 && bit >= 0 && bit < Integer.SIZE)
			{
				Integer varpValue = varpValues.get(varpIndex);
				if (varpValue == null)
				{
					client.runScript(GET_MUSIC_VARP, varpIndex);
					varpValue = client.getIntStack()[0];
					varpValues.put(varpIndex, varpValue);
				}
				isUnlocked = (varpValue & (1 << bit)) != 0;
			}
			if (isUnlocked)
			{
				unlocked++;
			}
			else if (log.isDebugEnabled())
			{
				Object[] name = client.getDBTableField(row, DBTableID.Music.COL_DISPLAYNAME, 0);
				lockedTracks.add(name.length > 0 ? String.valueOf(name[0]) : "row " + row);
			}
		}
		log.debug("Music cape: {} required tracks locked ({} unlocked / {} required): {}",
			required - unlocked, unlocked, required, lockedTracks);
		if (required == 0)
		{
			log.debug("Music cape: no required track data available; teleport remains unavailable");
			required = SkillLevels.MAX_LEVEL;
		}
		return new MusicTracks(unlocked, required);
	}

	@Override
	public int maximumQuestPoints()
	{
		checkOnClientThread();
		return client.getDBTableRows(DBTableID.Quest.ID).stream()
			.filter(row -> (Integer) client.getDBTableField(
				row,
				DBTableID.Quest.COL_RELEASE_TYPE,
				0
			)[0] != 0)
			.mapToInt(row -> (Integer) client.getDBTableField(
				row,
				DBTableID.Quest.COL_QUESTPOINTS_OVERRIDE,
				0
			)[0])
			.sum();
	}

	@Override
	public WorldPoint localPlayerWorldLocation()
	{
		checkOnClientThread();
		Player localPlayer = client.getLocalPlayer();
		return localPlayer == null ? null : localPlayer.getWorldLocation();
	}

	@Override
	public boolean modalWidgetOpen()
	{
		checkOnClientThread();
		return modalWidgetOpen(client);
	}

	/**
	 * Whether a modal widget is currently open. Varbit updates are not
	 * transmitted while one is, so {@code FARMING_TRANSMIT_*} reads return
	 * whatever was last received — which can be the value of the other patch
	 * sharing the region-scoped slot. Callers must skip sampling while this
	 * returns true rather than record a cross-contaminated value.
	 * (Same guard as FarmingTracker.updateData.)
	 */
	public static boolean modalWidgetOpen(Client client)
	{
		HashTable<WidgetNode> componentTable = client.getComponentTable();
		if (componentTable == null)
		{
			return false;
		}
		for (WidgetNode widgetNode : componentTable)
		{
			if (widgetNode.getModalMode() != WidgetModalMode.NON_MODAL)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public ItemContainer itemContainer(int inventoryId)
	{
		checkOnClientThread();
		return client.getItemContainer(inventoryId);
	}

	@Override
	public Map<Integer, Integer> runePouchContents()
	{
		checkOnClientThread();
		return OwnedItems.runePouchContents(client);
	}

	@Override
	public void addRunePouchContents(Map<Integer, Integer> owned)
	{
		checkOnClientThread();
		OwnedItems.addRunePouchContents(client, owned);
	}

	@Override
	public EnumSet<WorldType> worldType()
	{
		checkOnClientThread();
		return client.getWorldType();
	}

	@Override
	public void checkOnClientThread()
	{
		if (!isOnClientThread())
		{
			throw new IllegalStateException(
				"Player-state reads must run on the client thread; route the caller through the client thread");
		}
	}

	@Override
	public boolean isOnClientThread()
	{
		return Thread.currentThread().equals(client.getClientThread());
	}
}
