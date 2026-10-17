package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import shortestpath.scheduler.DebugState;
import shortestpath.scheduler.PathScheduler;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathTerminationReason;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathfinderStats;

public class DebugOverlayPanel extends OverlayPanel
{
	private static final int PANEL_WIDTH = 230;
	private static final Color PROBLEM = Color.RED;

	private final Client client;
	private final DebugState debugState;
	private final PathScheduler scheduler;

	@Inject
	public DebugOverlayPanel(Client client, DebugState debugState, PathScheduler scheduler)
	{
		super();
		this.client = client;
		this.debugState = debugState;
		this.scheduler = scheduler;

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

		return super.render(graphics);
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
