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
import shortestpath.DebugState;
import shortestpath.ShortestPathPlugin;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathTerminationReason;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathfinderStats;

public class DebugOverlayPanel extends OverlayPanel
{
	private static final int PANEL_WIDTH = 230;
	private static final Color PROBLEM = Color.RED;
	private static final long STATIC_BUILD_THRESHOLD_NANOS = 1_000_000;

	private final ShortestPathPlugin plugin;
	private final Client client;

	@Inject
	public DebugOverlayPanel(ShortestPathPlugin plugin, Client client)
	{
		super(plugin);
		this.plugin = plugin;
		this.client = client;

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
		DebugState debug = plugin.getDebugState();
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
		ActiveSearch search = plugin.getActiveSearch();
		if (search == null)
		{
			components.add(makeHeader("No search"));
			return;
		}

		ExactPathfinder exact = search instanceof ExactPathfinder ? (ExactPathfinder) search : null;
		PathfinderResult result = search.getResult();
		boolean problem = search == debug.getCancelledSearch() || isFailure(result);
		components.add(LineComponent.builder()
			.left((exact != null ? "Exact" : "Legacy") + " \u00b7 " + state(search, exact, result, debug))
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
		components.add(makeLine("Path:", path == null ? "none" : path.size() + " tiles"));

		PathfinderStats stats = search.getStats();
		if (stats == null)
		{
			return;
		}
		components.add(makeLine("Time:", millis(stats.getElapsedTimeNanos())));
		if (exact != null)
		{
			components.add(makeLine("  Forward search:", millis(exact.getForwardSearchNanos())));
			components.add(makeLine("  Target prep:", exact.isTargetReused() ? "reused"
				: millis(exact.getReverseSearchNanos() + exact.getHeuristicPrepareNanos())));
			components.add(makeLine("  Graph prep:", exact.isGraphReused() ? "reused"
				: millis(exact.getGraphPrepareNanos())));
			components.add(makeLine("  Account prep:", millis(exact.getAccountPrepareNanos())));
			// Static data is built once; only the search that built it spends measurable time on it.
			if (exact.getRoutingStaticNanos() >= STATIC_BUILD_THRESHOLD_NANOS)
			{
				components.add(makeLine("  Static data:", millis(exact.getRoutingStaticNanos())));
			}
		}
	}

	private void renderRestart(List<LayoutableRenderableEntity> components, DebugState debug, int tick)
	{
		if (debug.getRestartReason() == null)
		{
			return;
		}
		components.add(makeLine("Restarts:", debug.getRestartCount() + " (last: " + debug.getRestartReason() + ", "
			+ ago(debug.getRestartTick(), tick) + ")"));
		String outcome = debug.getRestartOutcome();
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

	private static String state(ActiveSearch search, ExactPathfinder exact, PathfinderResult result, DebugState debug)
	{
		if (search == debug.getCancelledSearch())
		{
			return "cancelled, not replaced";
		}
		if (exact != null && exact.isShowingProvisionalPath())
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

	private static String millis(long nanos)
	{
		return String.format("%.2fms", nanos / 1_000_000.0);
	}
}
