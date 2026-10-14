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
import shortestpath.pathfinder.AStarPathfinder;
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

		AStarPathfinder aStar = search instanceof AStarPathfinder ? (AStarPathfinder) search : null;
		PathfinderResult result = search.getResult();
		boolean problem = search == debug.getCancelledSearch() || isFailure(result);
		components.add(LineComponent.builder()
			.left((aStar != null ? "A*" : "Legacy") + " \u00b7 " + state(search, aStar, result, debug))
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
		if (aStar != null && !aStar.isShowingProvisionalPath())
		{
			tiles += ", " + aStar.getClickPoints().size() + " clicks";
		}
		components.add(makeLine("Path:", tiles));

		PathfinderStats stats = search.getStats();
		if (stats == null)
		{
			return;
		}
		components.add(makeLine("Time:", millis(stats.getElapsedTimeNanos())));
		if (aStar != null)
		{
			components.add(makeLine("  Forward search:", millis(aStar.getForwardSearchNanos())));
			components.add(makeLine("  Walk canonicalize:", millis(aStar.getWalkCanonicalizeNanos())));
			if (!aStar.getWalkDiagnostics().isEmpty())
				components.add(makeLine("  Legs kept as found:", Integer.toString(aStar.getWalkDiagnostics().size())));
			int keptSteps = aStar.getKeptStepIndices().size();
			components.add(makeLine("  Walk rewrite:", millis(aStar.getWalkRewriteNanos())
				+ (keptSteps > 0 ? ", " + keptSteps + " kept" : "")));
			components.add(makeLine("  Target prep:", aStar.isTargetReused() ? "reused"
				: millis(aStar.getReverseSearchNanos() + aStar.getHeuristicPrepareNanos())));
			components.add(makeLine("  Graph prep:", aStar.isGraphReused() ? "reused"
				: millis(aStar.getGraphPrepareNanos())));
			components.add(makeLine("  Account prep:", millis(aStar.getAccountPrepareNanos())));
			// Static data is built once; only the search that built it spends measurable time on it.
			if (aStar.getRoutingStaticNanos() >= STATIC_BUILD_THRESHOLD_NANOS)
			{
				components.add(makeLine("  Static data:", millis(aStar.getRoutingStaticNanos())));
			}
		}
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

	private static String state(ActiveSearch search, AStarPathfinder aStar, PathfinderResult result, DebugState debug)
	{
		if (search == debug.getCancelledSearch())
		{
			return "cancelled, not replaced";
		}
		if (aStar != null && aStar.isShowingProvisionalPath())
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
