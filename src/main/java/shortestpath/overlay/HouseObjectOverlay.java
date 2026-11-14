package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.*;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.poh.*;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.*;
import shortestpath.transport.Transport;

/**
 * Outlines house teleports where they stand in the player's house: the one
 * the route uses next, and, as a debug option, every one the house
 * detection found, each with its name. Positions come from the detector's
 * one look per house load, so nothing searches the scene per frame.
 */
public class HouseObjectOverlay extends Overlay
{
	private static final Color ALL = new Color(0, 200, 255);
	private static final int FILL_ALPHA = 50;
	/** Reading the nexus menu (opening it) settles what it holds; closing it reroutes. */
	static final String OPEN_AND_CLOSE = "Open and close the Portal Nexus";

	private final Client client;
	private final PohDetector pohDetector;
	private final PathScheduler scheduler;
	private final PathAnnotator annotator;
	private final Settings settings;
	private final HouseRouteProgress progress;
	// The last walk found, and what it was found for.
	private int walkX = -1;
	private int walkY = -1;
	private int walkPlane = -1;
	private int walkTick = -1;
	private List<HouseObject> walkTargets = List.of();
	private List<int[]> walk = List.of();

	@Inject
	public HouseObjectOverlay(Client client, PohDetector pohDetector, PathScheduler scheduler,
		PathAnnotator annotator, Settings settings, HouseRouteProgress progress)
	{
		this.progress = progress;
		this.client = client;
		this.pohDetector = pohDetector;
		this.scheduler = scheduler;
		this.annotator = annotator;
		this.settings = settings;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean drawAll = settings.bool(ConfigKey.DRAW_POH_OBJECTS);
		boolean highlight = settings.bool(ConfigKey.HIGHLIGHT_POH_OBJECT);
		if (!drawAll && !highlight)
		{
			clearWalk();
			return null;
		}
		List<HouseObject> objects = pohDetector.houseObjects();
		if (objects.isEmpty())
		{
			// Out of the house (or reloading): let go of the last walk's scene objects.
			clearWalk();
			return null;
		}

		Transport exit = highlight ? exit() : null;
		List<HouseObject> next = exit == null ? List.of()
			: pick(objects, HouseScan.targetsFor(HouseScan.objectId(exit.getObjectInfo())));
		// In another player's house whose nexus is not read yet, the route assumes the nexus has the
		// destination; the player is told to open it and check.
		String checkFor = checkNexusFor(exit, next, pohDetector.isVisitedNexusUnread());
		Color routeColor = settings.get(ConfigKey.COLOUR_PATH);
		// The look covers every floor; only the player's floor is drawn.
		WorldView view = client.getTopLevelWorldView();
		int plane = view.getPlane();
		if (!next.isEmpty())
		{
			drawWalk(graphics, view, plane, next, routeColor);
		}
		for (HouseObject object : objects)
		{
			if (object.object().getPlane() != plane)
			{
				continue;
			}
			boolean isNext = next.contains(object);
			if (isNext)
			{
				outline(graphics, object.object(), routeColor);
			}
			else if (drawAll)
			{
				outline(graphics, object.object(), ALL);
			}
			if (isNext && checkFor != null)
			{
				label(graphics, object.object(), checkFor, routeColor);
			}
			else if (drawAll)
			{
				label(graphics, object.object(), HouseDebug.describe(object.id()), isNext ? routeColor : ALL);
			}
		}
		// The teleport is on another floor: nothing above is drawn for it, so say where to go.
		String elsewhere = otherFloorNote(next, plane, checkFor);
		Player player = client.getLocalPlayer();
		if (elsewhere != null && player != null)
		{
			net.runelite.api.Point point = player.getCanvasTextLocation(graphics, elsewhere, player.getLogicalHeight() + 40);
			if (point != null)
			{
				OverlayUtil.renderTextLocation(graphics, point, elsewhere, routeColor);
			}
		}
		return null;
	}

	/**
	 * What to tell the player when none of the route's house objects is on
	 * their floor: which teleport and whether it is up or down; null when one
	 * is on this floor or there is none.
	 */
	static String otherFloorNote(List<HouseObject> next, int plane, String checkFor)
	{
		if (next.isEmpty())
		{
			return null;
		}
		for (HouseObject object : next)
		{
			if (object.object().getPlane() == plane)
			{
				return null;
			}
		}
		HouseObject target = next.get(0);
		String what = checkFor != null ? checkFor : "Use the " + HouseDebug.name(target.id());
		return what + (target.object().getPlane() > plane ? " (upstairs)" : " (downstairs)");
	}

	/**
	 * The note for the nexus when the route leads to it only because a
	 * visited nexus is not read yet: "Open and close the Portal Nexus"; null
	 * otherwise.
	 */
	static String checkNexusFor(Transport exit, List<HouseObject> next, boolean nexusUnread)
	{
		if (!nexusUnread || exit == null || next.isEmpty() || !next.get(0).serves().contains("nexus"))
		{
			return null;
		}
		PohNexusPortal portal = PohNexusPortal.fromDisplayInfo(exit.getDisplayInfo());
		return portal == null ? null : OPEN_AND_CLOSE;
	}

	/**
	 * Draws the walk from the player to the nearest of {@code targets} on
	 * the player's floor as a line through the tile centers. The walk is
	 * found again once per game tick at most (a door opened or closed
	 * changes it), or at once when the player's tile, the floor or the
	 * targets change; never every frame.
	 */
	private void drawWalk(Graphics2D graphics, WorldView view, int plane, List<HouseObject> targets, Color color)
	{
		Player player = client.getLocalPlayer();
		LocalPoint at = player == null ? null : player.getLocalLocation();
		if (at == null || !at.isInScene())
		{
			return;
		}
		int x = at.getSceneX();
		int y = at.getSceneY();
		int tick = client.getTickCount();
		if (tick != walkTick || x != walkX || y != walkY || plane != walkPlane || !targets.equals(walkTargets))
		{
			walkTick = tick;
			walkX = x;
			walkY = y;
			walkPlane = plane;
			walkTargets = targets;
			walk = findWalk(view, plane, x, y, targets);
		}
		if (walk.size() < 2)
		{
			return;
		}
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		net.runelite.api.Point last = null;
		for (int[] tile : walk)
		{
			net.runelite.api.Point point = Perspective.localToCanvas(client, LocalPoint.fromScene(tile[0], tile[1], view), plane);
			if (point != null && last != null)
			{
				graphics.drawLine(last.getX(), last.getY(), point.getX(), point.getY());
			}
			last = point;
		}
	}

	private static List<int[]> findWalk(WorldView view, int plane, int x, int y, List<HouseObject> targets)
	{
		CollisionData[] maps = view.getCollisionMaps();
		if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
		{
			return List.of();
		}
		List<HousePath.Area> areas = new ArrayList<>();
		for (HouseObject target : targets)
		{
			HousePath.Area area = area(target.object(), plane);
			if (area != null)
			{
				areas.add(area);
			}
		}
		return HousePath.find(maps[plane].getFlags(), x, y, areas);
	}

	/** The tiles an object covers on {@code plane}, or null when it is on another floor. */
	private static HousePath.Area area(TileObject object, int plane)
	{
		if (object.getPlane() != plane)
		{
			return null;
		}
		if (object instanceof GameObject)
		{
			net.runelite.api.Point min = ((GameObject) object).getSceneMinLocation();
			net.runelite.api.Point max = ((GameObject) object).getSceneMaxLocation();
			if (min != null && max != null)
			{
				return new HousePath.Area(min.getX(), min.getY(), max.getX(), max.getY());
			}
		}
		LocalPoint local = object.getLocalLocation();
		if (local == null)
		{
			return null;
		}
		// Something mounted on a wall is used from its own tile only.
		boolean onWall = object instanceof DecorativeObject || object instanceof WallObject;
		return new HousePath.Area(local.getSceneX(), local.getSceneY(), local.getSceneX(), local.getSceneY(), onWall);
	}

	private void clearWalk()
	{
		walkTargets = List.of();
		walk = List.of();
		walkTick = -1;
	}

	/** The transport the route leaves the house by, or null. */
	private Transport exit()
	{
		ActiveSearch search = scheduler.getActiveSearch();
		List<PathStep> path = search == null ? null : search.getPath();
		if (path == null || path.isEmpty())
		{
			return null;
		}
		int entry = progress.houseVisit(path);
		return entry < 0 ? null : annotator.getPohExitTransport(path.get(entry).getPackedPosition(), path, entry - 1);
	}

	/**
	 * The index of the route's first step inside the house, or -1. A route
	 * planned outside the house reaches it partway through; the exit is
	 * looked for from there.
	 */
	static int firstHouseStep(List<PathStep> path)
	{
		return firstHouseStep(path, 0);
	}

	/** The index of the route's first step inside the house at or after {@code from}, or -1. */
	static int firstHouseStep(List<PathStep> path, int from)
	{
		for (int i = Math.max(0, from); i < path.size(); i++)
		{
			int point = path.get(i).getPackedPosition();
			if (PohService.isInsidePoh(WorldPointUtil.unpackWorldX(point), WorldPointUtil.unpackWorldY(point)))
			{
				return i;
			}
		}
		return -1;
	}

	/** The objects serving the first of {@code targets} the house has. */
	static List<HouseObject> pick(List<HouseObject> objects, List<String> targets)
	{
		for (String target : targets)
		{
			List<HouseObject> found = new ArrayList<>();
			for (HouseObject object : objects)
			{
				if (object.serves().contains(target))
				{
					found.add(object);
				}
			}
			if (!found.isEmpty())
			{
				return found;
			}
		}
		return List.of();
	}

	private static void outline(Graphics2D graphics, TileObject object, Color color)
	{
		Shape shape = object.getClickbox();
		if (shape == null)
		{
			shape = object.getCanvasTilePoly();
		}
		if (shape == null)
		{
			return;
		}
		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), FILL_ALPHA));
		graphics.fill(shape);
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2));
		graphics.draw(shape);
	}

	private static void label(Graphics2D graphics, TileObject object, String text, Color color)
	{
		net.runelite.api.Point point = object.getCanvasTextLocation(graphics, text, 0);
		if (point != null)
		{
			OverlayUtil.renderTextLocation(graphics, point, text, color);
		}
	}
}
