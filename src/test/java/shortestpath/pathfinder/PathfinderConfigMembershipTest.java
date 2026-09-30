package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.DBTableID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportMembership;
import shortestpath.transport.TransportType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for the opt-in members/free-to-play transport filter
 * ({@code filterMembersTransportsOnF2p}).
 */
@RunWith(MockitoJUnitRunner.class)
public class PathfinderConfigMembershipTest
{
	private static final String LARGE_DOOR = "Enter Large door 30388";

	@Mock
	private Client client;
	@Mock
	private ShortestPathConfig config;

	private PathfinderConfig pathfinderConfig;

	// ── Decision table ────────────────────────────────────────────────

	@Test
	public void filterOffAllowsMembersTransports()
	{
		for (boolean membersWorld : new boolean[]{true, false})
		{
			assertTrue(allowed(TransportMembership.MEMBERS, false, membersWorld));
			assertTrue(allowed(TransportMembership.F2P, false, membersWorld));
		}
	}

	@Test
	public void freeToPlayOnlyDependsOnlyOnWorldType()
	{
		for (boolean filterEnabled : new boolean[]{true, false})
		{
			assertFalse(allowed(TransportMembership.F2P_ONLY, filterEnabled, true));
			assertTrue(allowed(TransportMembership.F2P_ONLY, filterEnabled, false));
		}
	}

	@Test
	public void filterOnMembersWorld()
	{
		assertTrue(allowed(TransportMembership.MEMBERS, true, true));
		assertTrue(allowed(TransportMembership.F2P, true, true));
		assertFalse(allowed(TransportMembership.F2P_ONLY, true, true));
	}

	@Test
	public void filterOnFreeToPlayWorld()
	{
		assertFalse(allowed(TransportMembership.MEMBERS, true, false));
		assertTrue(allowed(TransportMembership.F2P, true, false));
		assertTrue(allowed(TransportMembership.F2P_ONLY, true, false));
	}

	@Test
	public void transportTypeIsExempt()
	{
		for (boolean filterEnabled : new boolean[]{true, false})
		{
			for (boolean membersWorld : new boolean[]{true, false})
			{
				assertTrue(PathfinderConfig.isMembershipAllowed(TransportMembership.MEMBERS,
					TransportType.TRANSPORT, filterEnabled, membersWorld));
			}
		}
	}

	@Test
	public void grappleShortcutsAreFiltered()
	{
		// Grapple shortcuts are refined from agility_shortcuts.tsv rows at load time
		assertFalse(PathfinderConfig.isMembershipAllowed(TransportMembership.MEMBERS,
			TransportType.GRAPPLE_SHORTCUT, true, false));
	}

	// ── World detection ───────────────────────────────────────────────

	@Test
	public void worldWithoutMembersFlagIsFreeToPlay()
	{
		refresh(false, EnumSet.noneOf(WorldType.class));
		assertFalse(pathfinderConfig.isMembersWorld());
	}

	@Test
	public void membersAndSeasonalWorldsAreMembersWorlds()
	{
		refresh(false, EnumSet.of(WorldType.MEMBERS));
		assertTrue(pathfinderConfig.isMembersWorld());
		refresh(false, EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL));
		assertTrue(pathfinderConfig.isMembersWorld());
	}

	@Test
	public void unknownWorldTypeCountsAsMembers()
	{
		refresh(false, null);
		assertTrue(pathfinderConfig.isMembersWorld());
	}

	@Test
	public void isMembersWorldType()
	{
		assertTrue(PathfinderConfig.isMembersWorldType(EnumSet.of(WorldType.MEMBERS)));
		assertTrue(PathfinderConfig.isMembersWorldType(null));
		assertFalse(PathfinderConfig.isMembersWorldType(EnumSet.noneOf(WorldType.class)));
		assertFalse(PathfinderConfig.isMembersWorldType(EnumSet.of(WorldType.PVP)));
	}

	// ── Real transport data ───────────────────────────────────────────

	@Test
	public void freeToPlayWorldWithFilterDropsMembersTransports()
	{
		refresh(true, EnumSet.noneOf(WorldType.class));
		List<Transport> active = activeTransports();

		assertEquals(0, count(active, TransportType.AGILITY_SHORTCUT));
		assertEquals(0, count(active, TransportType.GRAPPLE_SHORTCUT));
		assertTrue(hasDisplayInfo(active, "Lumbridge Home Teleport"));
		assertFalse(hasDisplayInfo(active, "Edgeville Home Teleport"));
		assertTrue(hasObjectInfo(active, LARGE_DOOR));
	}

	@Test
	public void membersWorldWithFilterDropsOnlyFreeToPlayOnlyTransports()
	{
		refresh(true, EnumSet.of(WorldType.MEMBERS));
		List<Transport> active = activeTransports();

		assertTrue(count(active, TransportType.AGILITY_SHORTCUT) > 0);
		assertTrue(hasDisplayInfo(active, "Edgeville Home Teleport"));
		assertFalse(hasObjectInfo(active, LARGE_DOOR));
	}

	@Test
	public void filterOffKeepsMembersTransportsAndGatesFreeToPlayOnlyByWorldType()
	{
		refresh(false, EnumSet.noneOf(WorldType.class));
		List<Transport> f2pWorld = activeTransports();
		refresh(false, EnumSet.of(WorldType.MEMBERS));
		List<Transport> membersWorld = activeTransports();

		assertTrue(count(f2pWorld, TransportType.AGILITY_SHORTCUT) > 0);
		assertTrue(hasDisplayInfo(f2pWorld, "Edgeville Home Teleport"));
		assertTrue(hasObjectInfo(f2pWorld, LARGE_DOOR));
		assertFalse(hasObjectInfo(membersWorld, LARGE_DOOR));
	}

	@Test
	public void walkingTransportsAreUnaffectedByTheFilter()
	{
		refresh(true, EnumSet.noneOf(WorldType.class));
		int f2pWorld = count(activeTransports(), TransportType.TRANSPORT);
		refresh(true, EnumSet.of(WorldType.MEMBERS));
		int membersWorld = count(activeTransports(), TransportType.TRANSPORT);

		assertTrue(f2pWorld > 0);
		assertEquals(membersWorld, f2pWorld);
	}

	private static boolean allowed(TransportMembership membership, boolean filterEnabled, boolean membersWorld)
	{
		return PathfinderConfig.isMembershipAllowed(membership, TransportType.TELEPORTATION_SPELL,
			filterEnabled, membersWorld);
	}

	private void refresh(boolean filterEnabled, EnumSet<WorldType> worldTypes)
	{
		pathfinderConfig = new TestPathfinderConfig(client, config, QuestState.FINISHED, true, true);
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10000000);
		when(config.useAgilityShortcuts()).thenReturn(true);
		when(config.useTeleportationSpellsHome()).thenReturn(true);
		when(config.useTeleportationPortals()).thenReturn(true);
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(config.filterMembersTransportsOnF2p()).thenReturn(filterEnabled);
		when(client.getWorldType()).thenReturn(worldTypes);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		pathfinderConfig.refresh();
	}

	/**
	 * Usable transports without a bank visit: those keyed by origin plus
	 * teleports, which have no origin and are kept in a separate list.
	 */
	private List<Transport> activeTransports()
	{
		Set<Transport> all = Collections.newSetFromMap(new IdentityHashMap<>());
		PrimitiveIntHashMap<Transport[]> active = pathfinderConfig.getTransports();
		for (int origin : active.keys())
		{
			all.addAll(Arrays.asList(active.get(origin)));
		}
		all.addAll(Arrays.asList(pathfinderConfig.getUsableTeleports(false)));
		return new ArrayList<>(all);
	}

	private static int count(List<Transport> transports, TransportType type)
	{
		int count = 0;
		for (Transport transport : transports)
		{
			if (transport.getType() == type)
			{
				count++;
			}
		}
		return count;
	}

	private static boolean hasDisplayInfo(List<Transport> transports, String displayInfo)
	{
		return transports.stream().anyMatch(t -> displayInfo.equals(t.getDisplayInfo()));
	}

	private static boolean hasObjectInfo(List<Transport> transports, String objectInfo)
	{
		return transports.stream().anyMatch(t -> objectInfo.equals(t.getObjectInfo()));
	}
}
