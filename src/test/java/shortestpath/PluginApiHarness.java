package shortestpath;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.AStarRoutingStaticProvider;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.pathfinder.TestPathfinderConfig;
import shortestpath.settings.TeleportationItem;

/**
 * Drives a real {@link ShortestPathPlugin} through its public plugin-message API, the way another
 * plugin does: messages go in through {@code onPluginMessage}, and the plugin's own messages are
 * captured from the event bus. The game client is mocked, with an inventory, worn items and a bank
 * the test sets; the pathfinder runs on the real map and transport data.
 *
 * <p>The client thread runs work immediately on the calling thread, and the test thread is the
 * client thread, so every refresh and restart happens synchronously; only searches run on the
 * plugin's pathfinding thread.
 */
final class PluginApiHarness implements AutoCloseable
{
	private static final long TIMEOUT_SECONDS = 60;
	// Building the A* backend's static data takes seconds, so every harness shares one.
	private static final AStarRoutingStaticProvider ASTAR_STATIC =
		new AStarRoutingStaticProvider(() -> new CollisionMap(SplitFlagMap.fromResources()));
	private static final AtomicInteger IDS = new AtomicInteger();

	final ShortestPathPlugin plugin = new ShortestPathPlugin();
	private shortestpath.scheduler.PathScheduler scheduler;
	final Settings settings = new Settings();
	private final Client client = mock(Client.class);
	private final ItemContainer inventory = mock(ItemContainer.class);
	private final ItemContainer worn = mock(ItemContainer.class);
	private final BlockingQueue<PluginMessage> posted = new LinkedBlockingQueue<>();
	private final TestPathfinderConfig pathfinderConfig;
	private shortestpath.items.ItemStateService itemState;

	/** Plugin settings with the values the tests change. */
	static final class Settings extends TestShortestPathConfig
	{
		PathfinderBackend selectedBackend = PathfinderBackend.LEGACY;
		boolean includeBankPath;
		int costBankVisit;
		TeleportationItem teleportationItems = TeleportationItem.NONE;
		int calculationCutoff = 50;

		@Override
		public Object value(String keyName)
		{
			switch (keyName)
			{
				case "pathfinderAlgorithm":
					return selectedBackend;
				case "includeBankPath":
					return includeBankPath;
				case "costBankVisit":
					return costBankVisit;
				case "useTeleportationItems":
					return teleportationItems;
				case "calculationCutoff":
					return calculationCutoff;
				default:
					return super.value(keyName);
			}
		}
	}

	PluginApiHarness()
	{
		Thread clientThreadOwner = Thread.currentThread();
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(clientThreadOwner);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
		doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
		doReturn(worn).when(client).getItemContainer(InventoryID.WORN);
		doAnswer(call ->
		{
			ItemComposition item = mock(ItemComposition.class);
			when(item.getName()).thenReturn("Item " + call.getArgument(0));
			return item;
		}).when(client).getItemDefinition(anyInt());
		carry();
		wear();

		ClientThread clientThread = mock(ClientThread.class);
		doAnswer(call ->
		{
			((Runnable) call.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		doAnswer(call -> ((BooleanSupplier) call.getArgument(0)).getAsBoolean())
			.when(clientThread).invokeLater(any(BooleanSupplier.class));

		EventBus eventBus = mock(EventBus.class);
		doAnswer(call ->
		{
			Object event = call.getArgument(0);
			if (event instanceof PluginMessage)
			{
				posted.add((PluginMessage) event);
			}
			return null;
		}).when(eventBus).post(any());

		pathfinderConfig = new TestPathfinderConfig(client, settings);
		shortestpath.settings.Settings settingsService = shortestpath.settings.Settings.wrap(settings);
		set("client", client);
		set("clientThread", clientThread);
		set("config", settings);
		set("settings", settingsService);
		shortestpath.poh.PohService pohService = shortestpath.poh.PohService.forTesting();
		set("pohService", pohService);
		set("pathfinderConfig", pathfinderConfig);
		// The harness builds the plugin without DI, so the scheduler, the refresh
		// coordinator and the attach-time bindings are seeded the same way the
		// shell's startUp wires them. The world map point manager moved into the
		// scheduler with the marker machinery, and the item-state service declares
		// its change facts straight to the coordinator.
		scheduler = new shortestpath.scheduler.PathScheduler(client, clientThread, settings,
			mock(WorldMapPointManager.class), new shortestpath.scheduler.DebugState());
		shortestpath.scheduler.RefreshCoordinator coordinator =
			new shortestpath.scheduler.RefreshCoordinator(scheduler, settings);
		itemState = shortestpath.items.ItemStateService.forTesting(coordinator);
		shortestpath.sailing.SailingService sailing =
			shortestpath.sailing.SailingService.forTesting(client, coordinator);
		pathfinderConfig.setItemStateService(itemState);
		pathfinderConfig.setSailingService(sailing);
		set("itemState", itemState);
		set("sailing", sailing);
		shortestpath.presentation.PathAnnotator annotator =
			shortestpath.presentation.PathAnnotator.forTesting(pohService, itemState);
		annotator.attach(pathfinderConfig);
		shortestpath.api.PluginMessageHandler apiHandler =
			new shortestpath.api.PluginMessageHandler(client, clientThread, eventBus, scheduler,
				coordinator, settingsService, annotator);
		set("apiHandler", apiHandler);
		scheduler.attach(pathfinderConfig, sailing, apiHandler);
		set("scheduler", scheduler);
		set("coordinator", coordinator);
		set("aStarRoutingStatic", ASTAR_STATIC);
	}

	private void set(String name, Object value)
	{
		try
		{
			Field field = ShortestPathPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(plugin, value);
		}
		catch (NoSuchFieldException e)
		{
			// Fields the pathfinding machinery move relocated onto the scheduler.
			try
			{
				Field field = shortestpath.scheduler.PathScheduler.class.getDeclaredField(name);
				field.setAccessible(true);
				field.set(scheduler, value);
			}
			catch (ReflectiveOperationException e2)
			{
				throw new AssertionError(e2);
			}
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	/** Sets the inventory, as alternating item id and quantity. */
	PluginApiHarness carry(int... idsAndQuantities)
	{
		doReturn(items(idsAndQuantities)).when(inventory).getItems();
		return this;
	}

	/** Sets the worn items, as alternating item id and quantity. */
	PluginApiHarness wear(int... idsAndQuantities)
	{
		doReturn(items(idsAndQuantities)).when(worn).getItems();
		return this;
	}

	/** Sets the bank as seen this session, as alternating item id and quantity. */
	PluginApiHarness bank(int... idsAndQuantities)
	{
		ItemContainer bank = mock(ItemContainer.class);
		doReturn(items(idsAndQuantities)).when(bank).getItems();
		itemState.noteBankContainer(bank);
		return this;
	}

	/** Forgets the bank, as before it is first opened in a session. */
	PluginApiHarness bankNotSeen()
	{
		itemState.noteBankContainer(null);
		return this;
	}

	private static Item[] items(int... idsAndQuantities)
	{
		Item[] items = new Item[idsAndQuantities.length / 2];
		for (int i = 0; i < items.length; i++)
		{
			items[i] = new Item(idsAndQuantities[2 * i], idsAndQuantities[2 * i + 1]);
		}
		return items;
	}

	void send(String name, Map<String, Object> data)
	{
		plugin.onPluginMessage(new PluginMessage(ShortestPathPlugin.CONFIG_GROUP, name, data));
	}

	/** Sends a {@code path} message and waits for the displayed search it starts to finish. */
	ActiveSearch path(Object start, Object target)
	{
		Map<String, Object> data = new HashMap<>();
		if (start != null)
		{
			data.put("start", start);
		}
		if (target != null)
		{
			data.put("target", target);
		}
		ActiveSearch before = plugin.getActiveSearch();
		send("path", data);
		ActiveSearch search = plugin.getActiveSearch();
		if (search == null || search == before)
		{
			return search;
		}
		awaitDone(search);
		return search;
	}

	void awaitDone(ActiveSearch search)
	{
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
		while (!search.isDone() || search.getResult() == null)
		{
			if (System.nanoTime() > deadline)
			{
				throw new AssertionError("the displayed search did not finish");
			}
			Thread.onSpinWait();
		}
	}

	/** Sends a {@code query} message and waits for its {@code result}. */
	Map<String, Object> query(Object start, Object target)
	{
		Object id = "query-" + IDS.incrementAndGet();
		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		data.put("start", start);
		data.put("target", target);
		send("query", data);
		Map<String, Object> result = awaitReply("result", id);
		awaitPathfindingThreadIdle();
		return result;
	}

	/**
	 * Waits for the pathfinding thread to finish its current task. The plugin forgets a finished
	 * query just after posting its result, and refreshes the player's items for the next query only
	 * once no query is left. In the client the result reaches the caller a frame later, but here it
	 * arrives at once, so a test that changes items and queries again must wait for that clean-up.
	 */
	private void awaitPathfindingThreadIdle()
	{
		try
		{
			Runnable nothing = () ->
			{
			};
			((ExecutorService) get("pathfindingExecutor")).submit(nothing).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
		catch (ExecutionException | TimeoutException e)
		{
			throw new AssertionError(e);
		}
	}

	/** Sends a {@code getTarget} message and returns the {@code currentTarget} reply. */
	Map<String, Object> currentTarget()
	{
		Object id = "target-" + IDS.incrementAndGet();
		Map<String, Object> data = new HashMap<>();
		data.put("id", id);
		send("getTarget", data);
		return awaitReply("currentTarget", id);
	}

	private Map<String, Object> awaitReply(String name, Object id)
	{
		List<PluginMessage> skipped = new ArrayList<>();
		try
		{
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
			while (true)
			{
				PluginMessage message = posted.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
				if (message == null)
				{
					throw new AssertionError("no " + name + " message for " + id);
				}
				if (name.equals(message.getName()) && id.equals(message.getData().get("id")))
				{
					return message.getData();
				}
				skipped.add(message);
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
		finally
		{
			posted.addAll(skipped);
		}
	}

	/** The missing-items label the displayed path draws on the player's tile, or null. */
	String missingItemsLabel()
	{
		return plugin.getMissingTargetItemsText();
	}

	@Override
	public void close()
	{
		send("clear", new HashMap<>());
		((ExecutorService) get("pathfindingExecutor")).shutdownNow();
	}

	private Object get(String name)
	{
		try
		{
			Field field = ShortestPathPlugin.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(plugin);
		}
		catch (NoSuchFieldException e)
		{
			try
			{
				Field field = shortestpath.scheduler.PathScheduler.class.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(scheduler);
			}
			catch (ReflectiveOperationException e2)
			{
				throw new AssertionError(e2);
			}
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}
}
