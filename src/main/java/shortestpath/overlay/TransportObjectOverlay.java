package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import shortestpath.PrimitiveIntList;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathRenderState;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.Settings;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Outlines the object or NPC to click for the next transport on the path: the id that ends the
 * transport's "menuOption menuTarget objectID" text, looked up near the transport's origin tile,
 * in the path's colour. When a left click would pick another option, the option to pick is written above it.
 */
public class TransportObjectOverlay extends AbstractHighlightOverlay
{
	// Almost every transport object is within a tile or two of the origin; NPCs wander further
	private static final int OBJECT_RADIUS = 3;
	private static final int NPC_RADIUS = 5;

	private final ModelOutlineRenderer outlineRenderer;
	private final PathRenderState renderState;
	// The scene only changes on game ticks, so the lookup runs once per tick and transport
	private int searchedTick = -1;
	private Transport searchedTransport;
	private TileObject object;
	private NPC npc;
	private String option;

	@Inject
	public TransportObjectOverlay(Client client, Settings settings, PathScheduler scheduler,
		PathAnnotator annotator, ModelOutlineRenderer outlineRenderer, PathRenderState renderState)
	{
		super(client, scheduler, annotator, settings);
		this.outlineRenderer = outlineRenderer;
		this.renderState = renderState;
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	protected boolean enabled()
	{
		return settings.display().highlightTransportObjects();
	}

	@Override
	protected Dimension drawHighlights(Graphics2D graphics, List<PathStep> path, int pathIndex,
		ActiveSearch search)
	{
		if (!search.isDone())
		{
			return null;
		}
		Transport transport = nextTransport(path, pathIndex);
		if (transport != searchedTransport || client.getTickCount() != searchedTick)
		{
			searchedTransport = transport;
			searchedTick = client.getTickCount();
			find(transport);
			// Fairy rings show their code in the transport info instead of "Configure"
			option = object == null && npc == null || TransportType.FAIRY_RING.equals(transport.getType())
				? null : optionToPick(transport.getObjectInfo(), actions());
		}
		if (object != null)
		{
			outlineRenderer.drawOutline(object, 2, renderState.getPathColor(), 2);
		}
		else if (npc != null)
		{
			outlineRenderer.drawOutline(npc, 2, renderState.getPathColor(), 2);
		}
		Shape shape = option == null ? null : object != null ? object.getClickbox() : npc.getConvexHull();
		if (shape != null)
		{
			// Centred just above the top of the object
			Rectangle bounds = shape.getBounds();
			OverlayUtil.renderTextLocation(graphics, new Point((int) bounds.getCenterX()
				- graphics.getFontMetrics().stringWidth(option) / 2, bounds.y - 2), option, settings.display().colourText());
		}
		return null;
	}

	/**
	 * The option the transport's "menuOption menuTarget objectID" text starts with when it is one of
	 * the actions but not the first, which a left click picks; otherwise null.
	 */
	static String optionToPick(String objectInfo, String[] actions)
	{
		if (objectInfo == null || actions == null)
		{
			return null;
		}
		List<String> options = Arrays.stream(actions).filter(a -> a != null && !a.isEmpty()).collect(Collectors.toList());
		// The longest that fits, as an option can be the first words of another
		String match = options.stream().filter(a -> objectInfo.regionMatches(true, 0, a + " ", 0, a.length() + 1))
			.max(Comparator.comparingInt(String::length)).orElse(null);
		return match == null || match.equals(options.get(0)) ? null : match;
	}

	/** The found object's or NPC's actions in its current form. */
	private String[] actions()
	{
		if (object == null)
		{
			NPCComposition composition = npc.getTransformedComposition();
			return composition == null ? null : composition.getActions();
		}
		ObjectComposition composition = client.getObjectDefinition(object.getId());
		if (composition != null && composition.getImpostorIds() != null)
		{
			composition = composition.getImpostor();
		}
		return composition == null ? null : composition.getActions();
	}

	/** The transport of the first transport edge after the player's step, or null. */
	static Transport nextTransport(List<PathStep> path, int pathIndex)
	{
		return path.subList(pathIndex + 1, path.size()).stream().map(PathStep::getTransport)
			.filter(Objects::nonNull).findFirst().orElse(null);
	}

	/** The id that ends a "menuOption menuTarget objectID" text, or -1 when it has none. */
	static int objectId(String objectInfo)
	{
		return objectInfo != null && objectInfo.matches(".+ \\d{1,9}")
			? Integer.parseInt(objectInfo.substring(objectInfo.lastIndexOf(' ') + 1)) : -1;
	}

	/** Finds the nearest matching object around the origin, else the nearest matching NPC. */
	private void find(Transport transport)
	{
		object = null;
		npc = null;
		int id = transport == null ? -1 : objectId(transport.getObjectInfo());
		if (id < 0 || transport.getOrigin() == Transport.UNDEFINED_ORIGIN)
		{
			return;
		}
		// In an instance the origin is a template tile; its first copy in the scene is used
		PrimitiveIntList points = WorldPointUtil.toLocalInstance(client, transport.getOrigin());
		LocalPoint origin = null;
		for (int i = 0; origin == null && i < points.size(); i++)
		{
			origin = WorldPointUtil.toLocalPoint(client, points.get(i));
		}
		if (origin == null)
		{
			return;
		}
		int ox = origin.getSceneX();
		int oy = origin.getSceneY();
		WorldView view = client.getTopLevelWorldView();
		Tile[][] tiles = view.getScene().getTiles()[view.getPlane()];
		int best = OBJECT_RADIUS + 1;
		for (int x = Math.max(0, ox - OBJECT_RADIUS); x <= Math.min(tiles.length - 1, ox + OBJECT_RADIUS); x++)
		{
			for (int y = Math.max(0, oy - OBJECT_RADIUS); y <= Math.min(tiles[x].length - 1, oy + OBJECT_RADIUS); y++)
			{
				int distance = Math.max(Math.abs(x - ox), Math.abs(y - oy));
				TileObject match = distance < best && tiles[x][y] != null ? matching(tiles[x][y], id) : null;
				if (match != null)
				{
					object = match;
					best = distance;
				}
			}
		}
		if (object != null)
		{
			return;
		}
		best = NPC_RADIUS + 1;
		for (NPC candidate : view.npcs())
		{
			LocalPoint at = candidate.getLocalLocation();
			int distance = Math.max(Math.abs(at.getSceneX() - ox), Math.abs(at.getSceneY() - oy));
			// The data names an NPC by its base id (charter crews) or by the form it is in (Holgart)
			if (distance < best && (candidate.getId() == id
				|| candidate.getComposition() != null && candidate.getComposition().getId() == id))
			{
				npc = candidate;
				best = distance;
			}
		}
	}

	private TileObject matching(Tile tile, int id)
	{
		return Stream.concat(Stream.of(tile.getWallObject(), tile.getDecorativeObject(), tile.getGroundObject()),
			Stream.of(tile.getGameObjects())).filter(o -> o != null && (o.getId() == id || isForm(o.getId(), id)))
			.findFirst().orElse(null);
	}

	/** Whether the data's id is one of the forms a varbit turns the object into. */
	private boolean isForm(int objectId, int id)
	{
		ObjectComposition composition = client.getObjectDefinition(objectId);
		return composition != null && composition.getImpostorIds() != null
			&& IntStream.of(composition.getImpostorIds()).anyMatch(form -> form == id);
	}
}
