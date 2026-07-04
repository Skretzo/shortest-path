package shortestpath;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.transport.BankPickupRequirements;
import shortestpath.transport.Transport;

public class PathTileOverlay extends Overlay
{
	private static final int TRANSPORT_LABEL_GAP = 3;
	private final Client client;
	private final ShortestPathPlugin plugin;
	private int playerTileLabelOffset = 0;

	@Inject
	public PathTileOverlay(Client client, ShortestPathPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(Overlay.PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	private void renderTransports(Graphics2D graphics)
	{
		for (int a : plugin.getTransports().keys())
		{
			if (a == Transport.UNDEFINED_ORIGIN)
			{
				continue; // skip teleports
			}

			boolean drawStart = false;

			Point ca = tileCenter(a);

			if (ca == null)
			{
				continue;
			}

			StringBuilder s = new StringBuilder();
			for (Transport b : plugin.getTransports().getOrDefault(a, TransportAvailability.EMPTY_TRANSPORTS))
			{
				if (b == null || (b.getType() != null && b.getType().isTeleport()))
				{
					continue; // skip teleports
				}
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

		if (plugin.drawTransports)
		{
			renderTransports(graphics);
		}

		if (plugin.drawCollisionMap)
		{
			renderCollisionMap(graphics);
		}

		if (plugin.drawTiles && plugin.getPathfinder() != null && !plugin.getDisplayPath().isEmpty())
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

			List<PathStep> path = plugin.getDisplayPath();
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
				for (int target : plugin.getPathfinder().getTargets())
				{
					if (!path.isEmpty() && target != path.get(path.size() - 1).getPackedPosition())
					{
						drawTile(graphics, target, colorCalculating, -1, showTiles);
					}
				}
			}

			if (plugin.isPathUnreachable())
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

	private static boolean directionChanges(int previous, int current, int next)
	{
		int dx1 = WorldPointUtil.unpackWorldX(current) - WorldPointUtil.unpackWorldX(previous);
		int dy1 = WorldPointUtil.unpackWorldY(current) - WorldPointUtil.unpackWorldY(previous);
		int dx2 = WorldPointUtil.unpackWorldX(next) - WorldPointUtil.unpackWorldX(current);
		int dy2 = WorldPointUtil.unpackWorldY(next) - WorldPointUtil.unpackWorldY(current);
		return dx1 != dx2 || dy1 != dy2;
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
			int s = plugin.getDisplayPath().size();
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
		if (nextStep == null || !plugin.showTransportInfo ||
			WorldPointUtil.unpackWorldPlane(location) != client.getTopLevelWorldView().getPlane())
		{
			return;
		}

		// Sailing: teleports are suppressed while aboard a boat. When the path is
		// unreachable as a result, show a one-time hint on the player tile.
		if (pathIndex == 0 && plugin.getPathfinderConfig().isOnSailingBoat()
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

		// Workaround for weird pathing inside PoH to instead show info on the player
		// tile
		LocalPoint playerLocalPoint = client.getLocalPlayer().getLocalLocation();
		int playerPackedPoint = WorldPointUtil.fromLocalInstance(client, playerLocalPoint);
		int px = WorldPointUtil.unpackWorldX(playerPackedPoint);
		int py = WorldPointUtil.unpackWorldY(playerPackedPoint);
		int tx = WorldPointUtil.unpackWorldX(location);
		int ty = WorldPointUtil.unpackWorldY(location);
		boolean transportAndPlayerInsidePoh = ShortestPathPlugin.isInsidePoh(tx, ty)
			&& ShortestPathPlugin.isInsidePoh(px, py);
		Set<Transport> candidateTransports = plugin.transportsForEdge(currentStep, nextStep);

		// When inside POH, only show the POH exit info once (not per-transport)
		if (transportAndPlayerInsidePoh)
		{
			String pohExitInfo = plugin.getPohExitInfo(locationEnd, path, pathIndex);
			if (pohExitInfo == null)
			{
				return;
			}

			// Find the display name of the teleport that brought us to POH using bank-aware
			// lookup
			String text = null;
			for (Transport transport : candidateTransports)
			{
				text = transport.getDisplayInfo();
				if (text != null && !text.isEmpty())
				{
					break;
				}
			}
			if (text == null || text.isEmpty())
			{
				return;
			}
			text = text + " (Exit: " + pohExitInfo + ")";

			Point p = Perspective.localToCanvas(client, playerLocalPoint, client.getTopLevelWorldView().getPlane());
			if (p == null)
			{
				return;
			}

			drawLabel(graphics, p, text, 0);
			return;
		}

		// Check if this is a bank step and items need to be picked up
		Set<Integer> bankLocations = plugin.getPathfinderConfig().getDestinations("bank");
		if (bankLocations != null && plugin.getPathfinderConfig().bank != null)
		{
			List<String> bankPickupItems = BankPickupRequirements.getRequiredBankItems(
				client,
				plugin.getPathfinderConfig().bank,
				plugin.getPathfinderConfig(),
				bankLocations,
				path,
				pathIndex
			);
			if (!bankPickupItems.isEmpty())
			{
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

		// Teleports ("use this item/spell") get a pulsing highlight on the tile you cast from, drawn
		// once for the edge even if several teleport options share it.
		boolean teleportEdge = false;
		for (Transport transport : transportsToShow)
		{
			if (transport.getType() != null && transport.getType().isTeleport())
			{
				teleportEdge = true;
				break;
			}
		}
		if (teleportEdge && plugin.showTeleportPulse)
		{
			drawTeleportPulse(graphics, location);
		}

		for (Transport transport : transportsToShow)
		{
			String text = transport.getDisplayInfo();
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
