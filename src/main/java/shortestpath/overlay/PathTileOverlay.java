package shortestpath.overlay;

import com.google.inject.Inject;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.PrimitiveIntList;
import shortestpath.ShortestPathPlugin;
import shortestpath.TileCounter;
import shortestpath.TileStyle;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.transport.BankPickupRequirements;
import shortestpath.transport.Transport;

public class PathTileOverlay extends Overlay
{
	private static final int TRANSPORT_LABEL_GAP = 3;
	private static final long TRACER_STEP_MS = 200L;
	private static final int TRACER_WINDOW_TILES = 60;
	private static final int SPRITE_STRIDE = 10;
	private static final int SPRITE_ITEM_ID = ItemID.GAUNTLET_ESCAPE_CRYSTAL;
	private final Client client;
	private final ShortestPathPlugin plugin;
	private final ItemManager itemManager;
	private int playerTileLabelOffset = 0;
	private boolean teleportPulseDrawn = false;

	@Inject
	public PathTileOverlay(Client client, ShortestPathPlugin plugin, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(Overlay.PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	private static final Color COLOR_AVAILABLE = Color.WHITE;
	private static final Color COLOR_UNAVAILABLE = Color.ORANGE;

	private void renderTransports(Graphics2D graphics)
	{
		PrimitiveIntHashMap<Transport[]> allTransports = plugin.getAllDisplayTransports();
		PrimitiveIntHashMap<Transport[]> availableTransports = plugin.getTransports();

		for (int a : allTransports.keys())
		{
			if (a == Transport.UNDEFINED_ORIGIN)
			{
				continue; // skip teleports
			}

			Point ca = tileCenter(a);

			if (ca == null)
			{
				continue;
			}

			boolean drawStart = false;
			StringBuilder s = new StringBuilder();
			Transport[] availableAtOrigin = availableTransports.getOrDefault(a, TransportAvailability.EMPTY_TRANSPORTS);

			for (Transport b : allTransports.getOrDefault(a, TransportAvailability.EMPTY_TRANSPORTS))
			{
				if (b == null || (b.getType() != null && b.getType().isTeleport()))
				{
					continue; // skip teleports
				}

				boolean isAvailable = Arrays.asList(availableAtOrigin).contains(b);
				graphics.setColor(isAvailable ? COLOR_AVAILABLE : COLOR_UNAVAILABLE);

				PrimitiveIntList destinations = WorldPointUtil.toLocalInstance(client, b.getDestination());
				for (int i = 0; i < destinations.size(); i++)
				{
					int destination = destinations.get(i);
					if (destination == Transport.UNDEFINED_DESTINATION)
					{
						continue;
					}
					Point cb = tileCenter(destination);
					if (cb != null)
					{
						graphics.drawLine(ca.getX(), ca.getY(), cb.getX(), cb.getY());
						drawStart = true;
					}
					if (WorldPointUtil.unpackWorldPlane(destination) > WorldPointUtil.unpackWorldPlane(a))
					{
						s.append("+");
					}
					else if (WorldPointUtil.unpackWorldPlane(destination) < WorldPointUtil.unpackWorldPlane(a))
					{
						s.append("-");
					}
					else
					{
						s.append("=");
					}
				}
			}

			if (drawStart)
			{
				drawTile(graphics, a, plugin.colourTransports, -1, true);
			}

			graphics.setColor(Color.WHITE);
			graphics.drawString(s.toString(), ca.getX(), ca.getY());
		}
	}

	private void renderCollisionMap(Graphics2D graphics)
	{
		CollisionMap map = plugin.getMap();
		for (Tile[] row : client.getTopLevelWorldView().getScene().getTiles()[client.getTopLevelWorldView().getPlane()])
		{
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}

				Polygon tilePolygon = Perspective.getCanvasTilePoly(client, tile.getLocalLocation());

				if (tilePolygon == null)
				{
					continue;
				}

				int location = WorldPointUtil.fromLocalInstance(client, tile.getLocalLocation());
				int x = WorldPointUtil.unpackWorldX(location);
				int y = WorldPointUtil.unpackWorldY(location);
				int z = WorldPointUtil.unpackWorldPlane(location);

				String s = (!map.n(x, y, z) ? "n" : "") +
					(!map.s(x, y, z) ? "s" : "") +
					(!map.e(x, y, z) ? "e" : "") +
					(!map.w(x, y, z) ? "w" : "");

				if (map.isBlocked(x, y, z))
				{
					graphics.setColor(plugin.colourCollisionMap);
					graphics.fill(tilePolygon);
				}
				if (!s.isEmpty() && !s.equals("nsew"))
				{
					graphics.setColor(Color.WHITE);
					int stringX = (int) (tilePolygon.getBounds().getCenterX()
						- graphics.getFontMetrics().getStringBounds(s, graphics).getWidth() / 2);
					int stringY = (int) tilePolygon.getBounds().getCenterY();
					graphics.drawString(s, stringX, stringY);
				}
			}
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		playerTileLabelOffset = 0;
		teleportPulseDrawn = false;

		if (plugin.drawTransports)
		{
			renderTransports(graphics);
		}

		if (plugin.drawCollisionMap)
		{
			renderCollisionMap(graphics);
		}

		if (plugin.drawTiles && plugin.getPathfinder() != null && plugin.getPathfinder().getPath() != null)
		{
			Color colorCalculating = new Color(
				plugin.colourPathCalculating.getRed(),
				plugin.colourPathCalculating.getGreen(),
				plugin.colourPathCalculating.getBlue(),
				plugin.colourPathCalculating.getAlpha() / 2);
			Color pathColor = plugin.getPathColor();
			Color color = new Color(
				pathColor.getRed(),
				pathColor.getGreen(),
				pathColor.getBlue(),
				pathColor.getAlpha() / 2);

			List<PathStep> path = plugin.getPathfinder().getPath();
			int counter = 0;
			if (TileStyle.LINES.equals(plugin.pathStyle) || TileStyle.ARROW_LINE.equals(plugin.pathStyle))
			{
				boolean arrows = TileStyle.ARROW_LINE.equals(plugin.pathStyle);
				for (int i = 1; i < path.size(); i++)
				{
					PathStep currentStep = path.get(i - 1);
					PathStep nextStep = path.get(i);
					// Arrowheads only where they carry information: at direction changes and the end.
					boolean head = arrows && (i == path.size() - 1
						|| directionChanges(currentStep.getPackedPosition(), nextStep.getPackedPosition(),
							path.get(i + 1).getPackedPosition()));
					drawLine(graphics, currentStep.getPackedPosition(), nextStep.getPackedPosition(), color,
						1 + counter++, head);
					drawTransportInfo(graphics, currentStep, nextStep, path, i - 1);
				}
			}
			else if (TileStyle.TURN_MARKERS.equals(plugin.pathStyle))
			{
				for (int i = 0; i < path.size(); i++)
				{
					PathStep currentStep = path.get(i);
					int pathPoint = currentStep.getPackedPosition();
					int pathX = WorldPointUtil.unpackWorldX(pathPoint);
					int pathY = WorldPointUtil.unpackWorldY(pathPoint);
					// Markers only where the route changes direction, plus both endpoints.
					boolean marker = i == 0 || i == path.size() - 1
						|| directionChanges(path.get(i - 1).getPackedPosition(), pathPoint,
							path.get(i + 1).getPackedPosition());
					// Skip markers inside POH (no collision data, tiles render at wrong positions)
					if (marker && !ShortestPathPlugin.isInsidePoh(pathX, pathY))
					{
						drawTile(graphics, pathPoint, color, counter, true);
						drawTurnMarkerGlyph(graphics, path, i);
					}
					counter++;
					drawTransportInfo(graphics, currentStep, plugin.nextPathStep(path, i), path, i);
				}
				drawUnreachedTargets(graphics, path, colorCalculating, true);
			}
			else if (TileStyle.TRACER.equals(plugin.pathStyle))
			{
				// Faint polyline under the moving marker so the full route stays readable
				// without looking identical to LINES.
				Color tracerLineColour = new Color(color.getRed(), color.getGreen(), color.getBlue(),
					color.getAlpha() / 2);
				for (int i = 1; i < path.size(); i++)
				{
					PathStep currentStep = path.get(i - 1);
					PathStep nextStep = path.get(i);
					drawLine(graphics, currentStep.getPackedPosition(), nextStep.getPackedPosition(), tracerLineColour,
						1 + counter++, false);
					drawTransportInfo(graphics, currentStep, nextStep, path, i - 1);
				}
				if (!path.isEmpty())
				{
					// One marker walks the path on a wall-clock phase — the overlay repaints
					// every frame, so no tick subscription is needed (the drawTeleportPulse idiom).
					// The window caps the walk to the slice near the player: over a full-length
					// path the marker sits off-screen most of the time on long routes.
					int tracerWindow = Math.min(path.size(), TRACER_WINDOW_TILES);
					int tracerIndex = (int) ((System.currentTimeMillis() / TRACER_STEP_MS) % tracerWindow);
					int tracerPoint = path.get(tracerIndex).getPackedPosition();
					int tracerX = WorldPointUtil.unpackWorldX(tracerPoint);
					int tracerY = WorldPointUtil.unpackWorldY(tracerPoint);
					if (!ShortestPathPlugin.isInsidePoh(tracerX, tracerY))
					{
						Point current = tileCenter(tracerPoint);
						if (current != null)
						{
							Color previousColour = graphics.getColor();
							graphics.setColor(pathColor);
							int radius = 6;
							graphics.fillOval(current.getX() - radius, current.getY() - radius, radius * 2, radius * 2);
							if (tracerIndex + 1 < path.size())
							{
								Point next = tileCenter(path.get(tracerIndex + 1).getPackedPosition());
								if (next != null)
								{
									ArrowHead.draw(graphics, current.getX(), current.getY(), next.getX(), next.getY(), 10);
								}
							}
							graphics.setColor(previousColour);
						}
					}
				}
				drawUnreachedTargets(graphics, path, colorCalculating, true);
			}
			else if (TileStyle.SPRITE_MARKERS.equals(plugin.pathStyle))
			{
				for (int i = 0; i < path.size(); i++)
				{
					PathStep currentStep = path.get(i);
					int pathPoint = currentStep.getPackedPosition();
					int pathX = WorldPointUtil.unpackWorldX(pathPoint);
					int pathY = WorldPointUtil.unpackWorldY(pathPoint);
					// Bounded stride — a sprite every SPRITE_STRIDE tiles plus the destination,
					// never one sprite per tile (per-tile sprite density is too heavy).
					boolean marker = i % SPRITE_STRIDE == 0 || i == path.size() - 1;
					// Skip sprites inside POH (no collision data, tiles render at wrong positions)
					if (marker && !ShortestPathPlugin.isInsidePoh(pathX, pathY))
					{
						Point p = tileCenter(pathPoint);
						if (p != null)
						{
							BufferedImage sprite = itemManager.getImage(SPRITE_ITEM_ID);
							if (sprite != null)
							{
								if (i == path.size() - 1)
								{
									// The destination reads as the endpoint: larger sprite plus a ring.
									int dw = sprite.getWidth() * 3 / 2;
									int dh = sprite.getHeight() * 3 / 2;
									graphics.drawImage(sprite, p.getX() - dw / 2, p.getY() - dh / 2, dw, dh, null);
									int radius = Math.max(dw, dh) / 2 + 3;
									Color previousColour = graphics.getColor();
									graphics.setColor(plugin.colourText);
									graphics.drawOval(p.getX() - radius, p.getY() - radius, radius * 2, radius * 2);
									graphics.setColor(previousColour);
								}
								else
								{
									graphics.drawImage(sprite, p.getX() - sprite.getWidth() / 2,
										p.getY() - sprite.getHeight() / 2, null);
								}
							}
							drawCounter(graphics, p.getX(), p.getY(), counter);
						}
					}
					counter++;
					drawTransportInfo(graphics, currentStep, plugin.nextPathStep(path, i), path, i);
				}
				drawUnreachedTargets(graphics, path, colorCalculating, true);
			}
			else
			{
				boolean showTiles = TileStyle.TILES.equals(plugin.pathStyle);
				for (int i = 0; i < path.size(); i++)
				{
					// Skip drawing tiles inside POH (no collision data, tiles render at wrong positions)
					PathStep currentStep = path.get(i);
					int pathPoint = currentStep.getPackedPosition();
					int pathX = WorldPointUtil.unpackWorldX(pathPoint);
					int pathY = WorldPointUtil.unpackWorldY(pathPoint);
					if (!ShortestPathPlugin.isInsidePoh(pathX, pathY))
					{
						drawTile(graphics, pathPoint, color, counter, showTiles);
					}
					counter++;
					drawTransportInfo(graphics, currentStep, plugin.nextPathStep(path, i), path, i);
				}
				drawUnreachedTargets(graphics, path, colorCalculating, showTiles);
			}

			if (plugin.isPathUnreachable() && plugin.showUnreachableText)
			{
				playerTileLabelOffset += drawLabelOnPlayerTile(graphics, plugin.unreachableText, playerTileLabelOffset);
			}
		}

		return null;
	}

	private Point tileCenter(int b)
	{
		if (b == WorldPointUtil.UNDEFINED || client == null)
		{
			return null;
		}

		if (WorldPointUtil.unpackWorldPlane(b) != client.getTopLevelWorldView().getPlane())
		{
			return null;
		}

		LocalPoint lp = WorldPointUtil.toLocalPoint(client, b);
		if (lp == null)
		{
			return null;
		}

		Polygon poly = Perspective.getCanvasTilePoly(client, lp);
		if (poly == null)
		{
			return null;
		}

		int cx = poly.getBounds().x + poly.getBounds().width / 2;
		int cy = poly.getBounds().y + poly.getBounds().height / 2;
		return new Point(cx, cy);
	}

	private void drawTile(Graphics2D graphics, int location, Color color, int counter, boolean draw)
	{
		if (client == null)
		{
			return;
		}

		PrimitiveIntList points = WorldPointUtil.toLocalInstance(client, location);
		for (int i = 0; i < points.size(); i++)
		{
			int point = points.get(i);
			if (point == WorldPointUtil.UNDEFINED)
			{
				continue;
			}

			LocalPoint lp = WorldPointUtil.toLocalPoint(client, point);
			if (lp == null)
			{
				continue;
			}

			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly == null)
			{
				continue;
			}

			if (draw)
			{
				graphics.setColor(color);
				graphics.fill(poly);
			}

			drawCounter(graphics, poly.getBounds().getCenterX(), poly.getBounds().getCenterY(), counter);
		}
	}

	private void drawUnreachedTargets(Graphics2D graphics, List<PathStep> path, Color colorCalculating, boolean draw)
	{
		for (int target : plugin.getPathfinder().getTargets())
		{
			if (!path.isEmpty() && target != path.get(path.size() - 1).getPackedPosition())
			{
				drawTile(graphics, target, colorCalculating, -1, draw);
			}
		}
	}

	private static boolean directionChanges(int previous, int current, int next)
	{
		int dx1 = WorldPointUtil.unpackWorldX(current) - WorldPointUtil.unpackWorldX(previous);
		int dy1 = WorldPointUtil.unpackWorldY(current) - WorldPointUtil.unpackWorldY(previous);
		int dx2 = WorldPointUtil.unpackWorldX(next) - WorldPointUtil.unpackWorldX(current);
		int dy2 = WorldPointUtil.unpackWorldY(next) - WorldPointUtil.unpackWorldY(current);
		return dx1 != dx2 || dy1 != dy2;
	}

	// Turn markers get a glyph inside the fill: an arrowhead along the outgoing
	// segment so the marker reads as "turn this way", a ring for the destination
	// which has no outgoing segment.
	private void drawTurnMarkerGlyph(Graphics2D graphics, List<PathStep> path, int index)
	{
		Point here = tileCenter(path.get(index).getPackedPosition());
		if (here == null)
		{
			return;
		}
		Color previousColour = graphics.getColor();
		graphics.setColor(plugin.colourText);
		if (index + 1 < path.size())
		{
			Point next = tileCenter(path.get(index + 1).getPackedPosition());
			if (next != null)
			{
				ArrowHead.draw(graphics, here.getX(), here.getY(), next.getX(), next.getY(), 8);
			}
		}
		else
		{
			int radius = 5;
			graphics.drawOval(here.getX() - radius, here.getY() - radius, radius * 2, radius * 2);
		}
		graphics.setColor(previousColour);
	}

	private void drawLine(Graphics2D graphics, int startLoc, int endLoc, Color color, int counter, boolean arrowHead)
	{
		PrimitiveIntList starts = WorldPointUtil.toLocalInstance(client, startLoc);
		PrimitiveIntList ends = WorldPointUtil.toLocalInstance(client, endLoc);

		if (starts.isEmpty() || ends.isEmpty())
		{
			return;
		}

		int start = starts.get(0);
		int end = ends.get(0);

		final int z = client.getTopLevelWorldView().getPlane();
		if (WorldPointUtil.unpackWorldPlane(start) != z)
		{
			return;
		}

		LocalPoint lpStart = WorldPointUtil.toLocalPoint(client, start);
		LocalPoint lpEnd = WorldPointUtil.toLocalPoint(client, end);

		if (lpStart == null || lpEnd == null)
		{
			return;
		}

		final int startHeight = Perspective.getTileHeight(client, lpStart, z);
		final int endHeight = Perspective.getTileHeight(client, lpEnd, z);

		Point p1 = Perspective.localToCanvas(client, lpStart.getX(), lpStart.getY(), startHeight);
		Point p2 = Perspective.localToCanvas(client, lpEnd.getX(), lpEnd.getY(), endHeight);

		if (p1 == null || p2 == null)
		{
			return;
		}

		Line2D.Double line = new Line2D.Double(p1.getX(), p1.getY(), p2.getX(), p2.getY());

		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(4));
		graphics.draw(line);
		if (arrowHead)
		{
			ArrowHead.draw(graphics, p1.getX(), p1.getY(), p2.getX(), p2.getY(), 12);
		}

		if (counter == 1)
		{
			drawCounter(graphics, p1.getX(), p1.getY(), 0);
		}
		drawCounter(graphics, p2.getX(), p2.getY(), counter);
	}

	private void drawCounter(Graphics2D graphics, double x, double y, int counter)
	{
		if (counter >= 0 && !TileCounter.DISABLED.equals(plugin.showTileCounter))
		{
			int n = plugin.tileCounterStep > 0 ? plugin.tileCounterStep : 1;
			int s = plugin.getPathfinder().getPath().size();
			if ((counter % n != 0) && (s != (counter + 1)))
			{
				return;
			}
			if (TileCounter.REMAINING.equals(plugin.showTileCounter))
			{
				counter = s - counter - 1;
			}
			if (n > 1 && counter == 0)
			{
				return;
			}
			String counterText = Integer.toString(counter);
			graphics.setColor(plugin.colourText);
			graphics.drawString(
				counterText,
				(int) (x - graphics.getFontMetrics().getStringBounds(counterText, graphics).getWidth() / 2), (int) y);
		}
	}

	private int drawLabelAtCanvasPoint(Graphics2D graphics, Point point, String text, int verticalOffset)
	{
		if (point == null || text == null || text.isEmpty())
		{
			return 0;
		}

		double height = drawLabel(graphics, point, text, verticalOffset);

		return (int) height + TRANSPORT_LABEL_GAP;
	}

	private int drawLabelAtPackedLocation(Graphics2D graphics, int location, String text, int verticalOffset)
	{
		PrimitiveIntList points = WorldPointUtil.toLocalInstance(client, location);
		for (int i = 0; i < points.size(); i++)
		{
			LocalPoint lp = WorldPointUtil.toLocalPoint(client, points.get(i));
			if (lp == null)
			{
				continue;
			}

			Point p = Perspective.localToCanvas(client, lp, client.getTopLevelWorldView().getPlane());
			if (p == null)
			{
				continue;
			}

			verticalOffset += drawLabelAtCanvasPoint(graphics, p, text, verticalOffset);
		}
		return verticalOffset;
	}

	/**
	 * A pulsing "teleport from here" highlight: diamond rings expanding out from the tile and fading,
	 * looping. Drawn every frame (scene overlays repaint continuously) off wall-clock time, so the motion
	 * stays smooth regardless of game ticks. Anchored to the tile the player casts from — for a
	 * cast-from-anywhere teleport that sits under the player, drawing the eye to "teleport now".
	 */
	private void drawTeleportPulse(Graphics2D graphics, int location)
	{
		PrimitiveIntList points = WorldPointUtil.toLocalInstance(client, location);
		for (int i = 0; i < points.size(); i++)
		{
			LocalPoint lp = WorldPointUtil.toLocalPoint(client, points.get(i));
			if (lp == null)
			{
				continue;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly == null || poly.npoints == 0)
			{
				continue;
			}
			final double cx = poly.getBounds().getCenterX();
			final double cy = poly.getBounds().getCenterY();

			final long period = 1400L;
			final int rings = 2;
			final Color base = plugin.colourTeleportPulse;
			final Color previousColour = graphics.getColor();
			final Stroke previousStroke = graphics.getStroke();
			graphics.setStroke(new BasicStroke(2.2f));
			for (int r = 0; r < rings; r++)
			{
				// Stagger the two rings by half a period so one is always small/bright while the other
				// is large/faint — a continuous outward pulse.
				double phase = ((System.currentTimeMillis() + (long) (r * period / (double) rings)) % period)
					/ (double) period;
				double scale = 1.0 + phase * 2.6;
				int alpha = (int) Math.round(170 * (1.0 - phase));
				if (alpha <= 0)
				{
					continue;
				}
				Path2D ring = new Path2D.Double();
				for (int v = 0; v < poly.npoints; v++)
				{
					double x = cx + (poly.xpoints[v] - cx) * scale;
					double y = cy + (poly.ypoints[v] - cy) * scale;
					if (v == 0)
					{
						ring.moveTo(x, y);
					}
					else
					{
						ring.lineTo(x, y);
					}
				}
				ring.closePath();
				graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
				graphics.draw(ring);
			}
			graphics.setColor(previousColour);
			graphics.setStroke(previousStroke);
		}
	}

	private double drawLabel(Graphics2D graphics, Point point, String text, int verticalOffset)
	{
		Rectangle2D textBounds = graphics.getFontMetrics().getStringBounds(text, graphics);
		double height = textBounds.getHeight();
		int x = (int) (point.getX() - textBounds.getWidth() / 2);
		int y = (int) (point.getY() - height) - verticalOffset;
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(plugin.colourText);
		graphics.drawString(text, x, y);
		return height;
	}

	private int drawLabelOnPlayerTile(Graphics2D graphics, String text, int verticalOffset)
	{
		if (client.getLocalPlayer() == null)
		{
			return 0;
		}

		Point playerPoint = Perspective.localToCanvas(client, client.getLocalPlayer().getLocalLocation(), client.getTopLevelWorldView().getPlane());
		return drawLabelAtCanvasPoint(graphics, playerPoint, text, verticalOffset);
	}

	private void drawTransportInfo(Graphics2D graphics, PathStep currentStep, PathStep nextStep, List<PathStep> path, int pathIndex)
	{
		int location = currentStep.getPackedPosition();
		if (nextStep == null ||
			WorldPointUtil.unpackWorldPlane(location) != client.getTopLevelWorldView().getPlane())
		{
			return;
		}

		// Sailing: teleports are suppressed while aboard a boat. When the path is
		// unreachable as a result, show a one-time hint on the player tile.
		if (plugin.showTransportInfo && pathIndex == 0 && plugin.getPathfinderConfig().isOnSailingBoat()
			&& plugin.getPathfinder().isDone() && plugin.isPathUnreachable())
		{
			playerTileLabelOffset = drawLabelOnPlayerTile(graphics,
				"Disembark the boat to resume pathfinding", playerTileLabelOffset);
			return;
		}

		if (plugin.isPathUnreachable() || !plugin.getPathfinder().isDone())
		{
			return;
		}
		int locationEnd = nextStep.getPackedPosition();
		Set<Transport> candidateTransports = plugin.transportsForEdge(currentStep, nextStep);

		// Teleports ("use this item/spell") get a pulsing highlight on the tile you cast
		// from. Only the first teleport edge of the path pulses — the next "teleport
		// now" moment — and the pulse is independent of the transport info labels.
		if (plugin.showTeleportPulse && !teleportPulseDrawn)
		{
			for (Transport transport : candidateTransports)
			{
				if (transport.getType() != null && transport.getType().isTeleport())
				{
					drawTeleportPulse(graphics, location);
					teleportPulseDrawn = true;
					break;
				}
			}
		}

		if (!plugin.showTransportInfo)
		{
			return;
		}

		// Workaround for weird pathing inside PoH to instead show info on the player
		// tile
		LocalPoint playerLocalPoint = client.getLocalPlayer().getLocalLocation();
		WorldPoint playerWorldPoint = client.getLocalPlayer().getWorldLocation();
		if (client.getTopLevelWorldView().isInstance())
		{
			playerWorldPoint = WorldPoint.fromLocalInstance(client, playerLocalPoint);
		}
		int playerPackedPoint = WorldPointUtil.packWorldPoint(playerWorldPoint);
		int px = WorldPointUtil.unpackWorldX(playerPackedPoint);
		int py = WorldPointUtil.unpackWorldY(playerPackedPoint);
		int tx = WorldPointUtil.unpackWorldX(location);
		int ty = WorldPointUtil.unpackWorldY(location);
		boolean transportAndPlayerInsidePoh = ShortestPathPlugin.isInsidePoh(tx, ty)
			&& ShortestPathPlugin.isInsidePoh(px, py);

		// When inside POH, only show the POH exit info once (not per-transport)
		if (transportAndPlayerInsidePoh)
		{
			String pohExitInfo = plugin.getPohExitInfo(location, path, pathIndex - 1);

			if (pohExitInfo == null)
			{
				return;
			}
			String text = "Exit: " + pohExitInfo;

			Point p = Perspective.localToCanvas(client, playerLocalPoint, client.getTopLevelWorldView().getPlane());
			if (p == null)
			{
				return;
			}

			drawLabel(graphics, p, text, 0);
			return;
		}

		// Check if this is a bank step and items need to be picked up
		{
			BankPickupRequirements.BankPickupResult bankPickup = plugin.getBankPickup(path, pathIndex);
			if (bankPickup != null && !bankPickup.phrases.isEmpty())
			{
				List<String> bankPickupItems = bankPickup.phrases;
				String pickupText = "Pick up: " + String.join(", ", bankPickupItems);
				playerTileLabelOffset = drawLabelAtPackedLocation(graphics, location, pickupText, playerTileLabelOffset);

				// By default, bank pickup info replaces the default transport hint text;
				// enable the option to show both
				if (!plugin.showBankPickupInfo)
				{
					return;
				}
			}
		}

		// Only show transports the player can currently use; fall back to all if none are usable.
		Map<Integer, Integer> playerHas = BankPickupRequirements.collectPlayerItems(client);
		List<Transport> usableTransports = new ArrayList<>();
		for (Transport t : candidateTransports)
		{
			if (BankPickupRequirements.transportSatisfiedBy(t, playerHas))
			{
				usableTransports.add(t);
			}
		}
		Collection<Transport> transportsToShow = usableTransports.isEmpty() ? candidateTransports : usableTransports;

		for (Transport transport : transportsToShow)
		{
			String text = plugin.formatTransportDisplay(transport);
			if (text == null || text.isEmpty())
			{
				continue;
			}

			// Check if this transport goes to POH - if so, look ahead to find the exit
			// transport
			String pohExitInfo = plugin.getPohExitInfo(locationEnd, path, pathIndex);
			if (pohExitInfo != null)
			{
				text = text + " (Exit: " + pohExitInfo + ")";
			}

			playerTileLabelOffset = drawLabelAtPackedLocation(graphics, location, text, playerTileLabelOffset);
		}
	}
}
