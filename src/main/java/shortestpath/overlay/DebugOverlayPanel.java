package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.*;
import shortestpath.poh.*;
import shortestpath.scheduler.*;
import shortestpath.pathfinder.*;

public class DebugOverlayPanel extends OverlayPanel
{
	private static final int PANEL_WIDTH = 230;
	private static final Color PROBLEM = Color.RED;
	private static final Color OWN = Color.GREEN;

	private final Client client;
	private final DebugState debugState;
	private final PathScheduler scheduler;
	private final PohDetector pohDetector;

	@Inject
	public DebugOverlayPanel(Client client, DebugState debugState, PathScheduler scheduler, PohDetector pohDetector)
	{
		super();
		this.client = client;
		this.debugState = debugState;
		this.scheduler = scheduler;
		this.pohDetector = pohDetector;

		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
	}

	private LineComponent makeLine(String left, String right)
	{
		return makeLine(left, right, Color.WHITE);
	}

	private LineComponent makeLine(String left, String right, Color rightColor)
	{
		return LineComponent.builder()
			.left(left)
			.right(right)
			.rightColor(rightColor)
			.build();
	}

	private LineComponent makeHeader(String text)
	{
		return LineComponent.builder()
			.left(text)
			.leftColor(Color.ORANGE)
			.build();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<LayoutableRenderableEntity> components = panelComponent.getChildren();
		DebugState debug = debugState;
		int tick = client.getTickCount();

		components.add(
			TitleComponent.builder()
				.text("Shortest Path Debug")
				.color(Color.ORANGE)
				.build());

		renderSearch(components, debug);
		renderRestart(components, debug, tick);
		renderErrors(components, debug, tick);
		renderHouse(components);

		return super.render(graphics);
	}

	/** What the house detector sees in the house the player stands in. */
	private void renderHouse(List<LayoutableRenderableEntity> components)
	{
		HouseDebug house = pohDetector.debug();
		if (house == null)
		{
			return;
		}
		components.add(LineComponent.builder()
			.left(house.own() ? "House: yours" : "House: not yours")
			.leftColor(house.own() ? OWN : PROBLEM)
			.build());
		// Short values only, so no line wraps; the sidebar's House Debug block has the full report.
		components.add(makeLine("Why:", house.reason()));
		components.add(makeLine("Read:", house.shortState()));
		components.add(makeLine("Routing:", !house.routingHere() ? "your house"
			: house.nexusUnread() ? "this house, nexus assumed" : "this house"));

		HouseScan scan = house.scan();
		if (scan != null)
		{
			// The spiritual fairy tree is both in one object.
			components.add(makeLine("Ring / tree:", house.hasCombo() ? "combo"
				: yesNo(scan.fairyRing()) + " / " + yesNo(scan.spiritTree())));
			components.add(makeLine("Obelisk:", yesNo(scan.obelisk())));
			components.add(makeLine("Nexus:", house.nexusShort()));
			components.add(makeLine("Box:", scan.boxTier().toString()));
			components.add(makeLine("Mounted:", String.valueOf(scan.mountedItems().size())));
			components.add(makeLine("Portals:", String.valueOf(scan.chamberPortals().size())));
		}
	}

	private static String yesNo(boolean value)
	{
		return value ? "yes" : "no";
	}

	private static String list(Collection<?> items)
	{
		if (items.isEmpty())
		{
			return "none";
		}
		StringBuilder sb = new StringBuilder();
		for (Object item : items)
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(item);
		}
		return sb.toString();
	}

	private void renderSearch(List<LayoutableRenderableEntity> components, DebugState debug)
	{
		ActiveSearch search = scheduler.getActiveSearch();
		if (search == null)
		{
			components.add(makeHeader("No search"));
			return;
		}

		PathfinderResult result = search.getResult();
		boolean problem = search == debug.getCancelledSearch() || isFailure(result);
		components.add(LineComponent.builder()
			.left(search.backendName() + " \u00b7 " + state(search, result, debug))
			.leftColor(problem ? PROBLEM : Color.ORANGE)
			.build());

		if (result != null)
		{
			components.add(makeLine("Result:", result.getTerminationReason() + ", cost " + result.getPathCost(),
				isFailure(result) ? PROBLEM : Color.WHITE));
			if (result.getMessage() != null)
			{
				components.add(makeLine("Message:", result.getMessage(), PROBLEM));
			}
		}

		List<PathStep> path = search.getPath();
		String tiles = path == null ? "none" : path.size() + " tiles";
				// While recalculating, the shown path is the previous route but the click count is the
		// new search's — suppress it rather than mix the two.
		if (!search.isShowingProvisionalPath())
		{
			tiles += ", " + search.getClickPoints().size() + " clicks";
		}
		components.add(makeLine("Path:", tiles));

		PathfinderStats stats = search.getStats();
		if (stats == null)
		{
			return;
		}
		components.add(makeLine("Time:", PathfinderStats.millis(stats.getElapsedTimeNanos())));
		search.debugStats().forEach((label, text) -> components.add(makeLine("  " + label + ":", text)));
	}

	private void renderRestart(List<LayoutableRenderableEntity> components, DebugState debug, int tick)
	{
		DebugState.Restart restart = debug.getRestart();
		if (restart == null)
		{
			return;
		}
		components.add(makeLine("Restarts:", restart.count + " (last: " + restart.reason + ", "
			+ ago(restart.tick, tick) + ")"));
		String outcome = restart.outcome;
		if (!DebugState.STARTED.equals(outcome))
		{
			components.add(makeLine("Restart outcome:", outcome, PROBLEM));
		}
	}

	private void renderErrors(List<LayoutableRenderableEntity> components, DebugState debug, int tick)
	{
		if (debug.getClientErrorCount() > 0)
		{
			components.add(makeLine("Client error:", error(debug.getClientErrorCount(), debug.getClientError(),
				debug.getClientErrorTick(), tick), PROBLEM));
		}
		if (debug.getSearchErrorCount() > 0)
		{
			components.add(makeLine("Search error:", error(debug.getSearchErrorCount(), debug.getSearchError(),
				debug.getSearchErrorTick(), tick), PROBLEM));
		}
	}

	private static String state(ActiveSearch search, PathfinderResult result, DebugState debug)
	{
		if (search == debug.getCancelledSearch())
		{
			return "cancelled, not replaced";
		}
		if (search.isShowingProvisionalPath())
		{
			return "running, previous route shown";
		}
		if (!search.isDone())
		{
			return "running";
		}
		return isFailure(result) ? "failed" : "done";
	}

	private static boolean isFailure(PathfinderResult result)
	{
		return result != null && result.getTerminationReason() == PathTerminationReason.BACKEND_FAILURE;
	}

	private static String error(int count, String message, int errorTick, int tick)
	{
		return count + "x, last " + ago(errorTick, tick) + ": " + message;
	}

	private static String ago(int then, int now)
	{
		return then < 0 ? "-" : (now - then) + " ticks ago";
	}
}
