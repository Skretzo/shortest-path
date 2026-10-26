package shortestpath.requirement;

import java.util.EnumSet;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.HashTable;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import shortestpath.requirement.model.MusicTracks;
import shortestpath.requirement.model.SkillLevels;
import net.runelite.api.widgets.WidgetModalMode;
import shortestpath.items.OwnedItems;
import lombok.AllArgsConstructor;

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
public final class ClientPlayerStateSource implements PlayerStateSource
{
	// Music database variables use a one-based MUSICMULTI index, not a varplayer ID.
	private static final int[] MUSIC_VARPS = {
		VarPlayerID.MUSICMULTI_1, VarPlayerID.MUSICMULTI_2, VarPlayerID.MUSICMULTI_3,
		VarPlayerID.MUSICMULTI_4, VarPlayerID.MUSICMULTI_5, VarPlayerID.MUSICMULTI_6,
		VarPlayerID.MUSICMULTI_7, VarPlayerID.MUSICMULTI_8, VarPlayerID.MUSICMULTI_9,
		VarPlayerID.MUSICMULTI_10, VarPlayerID.MUSICMULTI_11, VarPlayerID.MUSICMULTI_12,
		VarPlayerID.MUSICMULTI_13, VarPlayerID.MUSICMULTI_14, VarPlayerID.MUSICMULTI_15,
		VarPlayerID.MUSICMULTI_16, VarPlayerID.MUSICMULTI_17, VarPlayerID.MUSICMULTI_18,
		VarPlayerID.MUSICMULTI_19, VarPlayerID.MUSICMULTI_20, VarPlayerID.MUSICMULTI_21,
		VarPlayerID.MUSICMULTI_22, VarPlayerID.MUSICMULTI_23, VarPlayerID.MUSICMULTI_24,
		VarPlayerID.MUSICMULTI_25, VarPlayerID.MUSICMULTI_26, VarPlayerID.MUSICMULTI_27
	};

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
		for (int row : client.getDBTableRows(DBTableID.Music.ID))
		{
			if ((Integer) client.getDBTableField(row, DBTableID.Music.COL_RELEASE_TYPE, 0)[0] == 0 ||
				(Integer) client.getDBTableField(row, DBTableID.Music.COL_HIDDEN, 0)[0] != 0 ||
				(Integer) client.getDBTableField(row, DBTableID.Music.COL_HOLIDAY, 0)[0] != 0)
			{
				continue;
			}
			required++;
			if ((Integer) client.getDBTableField(row, DBTableID.Music.COL_AUTOMATIC_UNLOCK, 0)[0] != 0)
			{
				unlocked++;
				continue;
			}
			Object[] variable = client.getDBTableField(row, DBTableID.Music.COL_VARIABLE, 0);
			int varpIndex = (Integer) variable[0] - 1;
			int bit = (Integer) variable[1];
			if (varpIndex >= 0 && varpIndex < MUSIC_VARPS.length && bit >= 0 && bit < Integer.SIZE &&
				(client.getVarpValue(MUSIC_VARPS[varpIndex]) & (1 << bit)) != 0)
			{
				unlocked++;
			}
		}
		if (required == 0)
		{
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
				DBTableID.Quest.COL_QUESTPOINTS,
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
