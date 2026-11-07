package shortestpath.pathfinder.sailing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.sailing.SeaArea;

/**
 * One boat kind's sea as a graph of straight runs (the corner graph): where a shortest route between two points of the
 * sea can bend, and which of those places see each other. A route over open water is a straight run until it has to
 * bend round a headland, and it bends only at a corner of the coast sticking out into the water, so the nodes are those
 * corners and the edges the straight runs between them. Sailing is then a search over a few thousand nodes rather than
 * the sea's tiles.
 * <p>
 * The coast is the kind's water outline ({@link SailingWaters#landing}, with every part fitted), traced on the tiles'
 * edges and smoothed by up to {@link #TOLERANCE} tiles, only ever cutting into land, so no passage
 * narrows; rocks smaller than the smoothing are dropped. So the graph is a model of the sea, not the sea: each run is
 * sailed on the tiles when the graph is built, with the moves of a boat that can slow down where it must
 * ({@link SailingMoves#withSlow}), as round a tight bend, past a pier, or over land the smoothing shaved off. A run takes
 * the moves' open-water bound ({@link SailingMoves#openWaterTicks}) and the ticks that sailing it took past that bound;
 * one that can't be sailed isn't a run. A route found on the graph is sailed again on the tiles to draw it.
 * <p>
 * The nodes are:
 * <ul>
 * <li>corners: a convex vertex of the smoothed coast, set {@link #MARGIN} tiles out into the water, so
 * routes keep a little way off the coast; also the corners of the open water round each restricted area, for boats
 * without the part;</li>
 * <li>launches: each mooring's, the tile in docking range of its berth the boat can be on with the most room round it.
 * Boarding puts the boat there; arriving in docking range, it docks.</li>
 * </ul>
 * A run joins two nodes that see each other past the smoothed coast, and is tangent to the coast at each corner it
 * joins (a route never bends at a corner it could cut). Each run carries the restricted areas it crosses, so a boat
 * without a part never takes it. Points that aren't nodes, such as a boat at sea or a target, link to the nodes they see
 * when a query asks ({@link #sees}, {@link #tangent}).
 * <p>
 * The generator (shortest-path-tooling's {@code sailingData} task) builds one per kind, by the settings below, and
 * writes it into the collision map's sailing layer as {@value #ENTRY_PREFIX}{@code <KIND>}.
 */
public final class SeaGraph
{
	/** The layer entry for a kind's graph is this followed by the kind's name. */
	public static final String ENTRY_PREFIX = "graph-";
	/** Bump when the format or the rules the graph is built by change. */
	public static final int VERSION = 1;
	/** How far the coast is smoothed away from moorings, at most, in tiles. */
	public static final double TOLERANCE = 6;
	/** How close to a berth the coast keeps every tile's detail, in tiles. */
	public static final double NEAR = 16;
	/** How far into the water a corner's node sits, along its bisector, when there's room. */
	public static final double MARGIN = 2.5;
	/** How far the boat's moves sailing a run may stray from it, in tiles. */
	public static final int CORRIDOR = 8;
	// Cell size of the coast's index, in tiles
	private static final int CELL = 16;
	private static final SeaArea[] AREAS = SeaArea.values();

	private static Object cachedMap;
	private static Map<SailingVessel.Kind, SeaGraph> cached;

	final SailingVessel.Kind kind;
	/** Node positions, in tiles: tile (x, y) covers [x - 0.5, x + 0.5] by [y - 0.5, y + 0.5]. */
	final double[] x, y;
	// A corner's coast vertex and its neighbours on the ring, for tangency; NaN for a launch
	final double[] vx, vy, prevX, prevY, nextX, nextY;
	// Runs from each node, both ways, the areas each crosses (bit per SeaArea ordinal), and the ticks it takes on the
	// tiles past its open-water bound
	final int[] runOffsets, runTargets, runExtra;
	final byte[] runAreas;
	/** The launch node of each mooring by id, or -1 where this kind can't cast off. */
	final int[] launch;
	// The moorings launched from each node, by id
	final int[] dockOffsets, dockMooring;
	// Tiles of the shortest way from each mooring's launch to each other's, with every part fitted (legTiles(...));
	// Float.POSITIVE_INFINITY for none. moorings * moorings, by id
	final float[] legTiles;
	final int moorings;
	// The smoothed coast: each segment's ends
	private final double[] ax, ay, bx, by;
	private final double[] rings;
	private final int[] ringStarts;
	// The segments in each cell of the index
	private final int cellMinX, cellMinY, cellsWide, cellsHigh;
	private final int[] cellOffsets, cellSegments;

	SeaGraph(SailingVessel.Kind kind, double[] x, double[] y, double[] vx, double[] vy, double[] prevX, double[] prevY,
		double[] nextX, double[] nextY, int[] runOffsets, int[] runTargets, byte[] runAreas, int[] runExtra, int[] launch,
		float[] legTiles, double[] rings, int[] ringStarts)
	{
		this.kind = kind;
		this.x = x;
		this.y = y;
		this.vx = vx;
		this.vy = vy;
		this.prevX = prevX;
		this.prevY = prevY;
		this.nextX = nextX;
		this.nextY = nextY;
		this.runOffsets = runOffsets;
		this.runTargets = runTargets;
		this.runAreas = runAreas;
		this.runExtra = runExtra;
		this.launch = launch;
		int n = x.length;
		dockOffsets = new int[n + 1];
		for (int m = 0; m < launch.length; m++) if (launch[m] >= 0) dockOffsets[launch[m] + 1]++;
		for (int i = 0; i < n; i++) dockOffsets[i + 1] += dockOffsets[i];
		dockMooring = new int[dockOffsets[n]];
		int[] docked = Arrays.copyOf(dockOffsets, n);
		for (int m = 0; m < launch.length; m++) if (launch[m] >= 0) dockMooring[docked[launch[m]]++] = m;
		this.legTiles = legTiles;
		this.moorings = launch.length;
		this.rings = rings;
		this.ringStarts = ringStarts;
		int segments = rings.length / 2;
		ax = new double[segments];
		ay = new double[segments];
		bx = new double[segments];
		by = new double[segments];
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (int r = 0; r + 1 < ringStarts.length; r++)
		{
			int first = ringStarts[r], end = ringStarts[r + 1];
			for (int p = first; p < end; p++)
			{
				int q = p + 1 == end ? first : p + 1;
				ax[p] = rings[2 * p];
				ay[p] = rings[2 * p + 1];
				bx[p] = rings[2 * q];
				by[p] = rings[2 * q + 1];
				minX = Math.min(minX, ax[p]);
				minY = Math.min(minY, ay[p]);
				maxX = Math.max(maxX, ax[p]);
				maxY = Math.max(maxY, ay[p]);
			}
		}
		if (segments == 0)
		{
			minX = minY = maxX = maxY = 0;
		}
		cellMinX = (int) Math.floor(minX / CELL) - 1;
		cellMinY = (int) Math.floor(minY / CELL) - 1;
		cellsWide = (int) Math.floor(maxX / CELL) - cellMinX + 2;
		cellsHigh = (int) Math.floor(maxY / CELL) - cellMinY + 2;
		int[] counts = new int[cellsWide * cellsHigh + 1];
		CellVisitor count = cell ->
		{
			counts[cell + 1]++;
			return true;
		};
		for (int s = 0; s < segments; s++) walk(ax[s], ay[s], bx[s], by[s], count);
		for (int c = 0; c < cellsWide * cellsHigh; c++) counts[c + 1] += counts[c];
		cellOffsets = counts.clone();
		cellSegments = new int[counts[cellsWide * cellsHigh]];
		int[] fill = Arrays.copyOf(counts, counts.length - 1);
		for (int s = 0; s < segments; s++)
		{
			int segment = s;
			walk(ax[s], ay[s], bx[s], by[s], cell ->
			{
				cellSegments[fill[cell]++] = segment;
				return true;
			});
		}
	}

	/** Each kind's graph from {@code map}'s sailing layer; read once per collision data. Empty for a map without them. */
	public static synchronized Map<SailingVessel.Kind, SeaGraph> all(CollisionMap map)
	{
		if (cached == null || cachedMap != map.dataIdentity())
		{
			Map<SailingVessel.Kind, SeaGraph> graphs = new EnumMap<>(SailingVessel.Kind.class);
			Map<String, byte[]> layer = map.layers(SailingWaters.LAYER);
			for (SailingVessel.Kind kind : SailingVessel.Kind.values())
			{
				byte[] data = layer.get(ENTRY_PREFIX + kind.name());
				if (data != null) graphs.put(kind, read(kind, data));
			}
			cached = graphs;
			cachedMap = map.dataIdentity();
		}
		return cached;
	}

	/** {@code kind}'s graph from {@code map}, or {@code null} if it has none. */
	public static SeaGraph of(CollisionMap map, SailingVessel.Kind kind)
	{
		return all(map).get(kind);
	}

	public SailingVessel.Kind kind()
	{
		return kind;
	}

	/** The number of nodes. */
	public int size()
	{
		return x.length;
	}

	/**
	 * The straight-line tiles of the shortest way from casting off at {@code from} to docking at {@code to} on this
	 * graph, with every part fitted, or {@link Float#POSITIVE_INFINITY} if it doesn't join them. At a boat's top speed,
	 * a bound on its time.
	 */
	public float legTiles(SailingMooring from, SailingMooring to)
	{
		if (from.id() >= moorings || to.id() >= moorings) return Float.POSITIVE_INFINITY;
		return legTiles[from.id() * moorings + to.id()];
	}

	/** Whether the graph joins casting off at {@code from} to docking at {@code to}. */
	public boolean joins(SailingMooring from, SailingMooring to)
	{
		return Float.isFinite(legTiles(from, to));
	}

	/**
	 * The restricted areas of the tiles a straight run between the points passes over, as a bit set: every tile the line
	 * touches, both neighbours where it passes exactly through a tile corner (Amanatides-Woo over the tiles, tile (x, y)
	 * covering [x - 0.5, x + 0.5]).
	 */
	static int areasAlong(SailingWaters waters, double x0, double y0, double x1, double y1)
	{
		// Shifted so tile (x, y) is the unit cell [x, x + 1)
		int[] mask = {0};
		cellsAlong(x0 + 0.5, y0 + 0.5, x1 + 0.5, y1 + 0.5, 1, (x, y) ->
		{
			mask[0] |= areaBit(waters, x, y);
			return true;
		});
		return mask[0];
	}

	private static int areaBit(SailingWaters waters, int x, int y)
	{
		SeaArea area = waters.area(shortestpath.WorldPointUtil.packWorldPoint(x, y, 0));
		return area == null ? 0 : 1 << area.ordinal();
	}

	/** The bit set of {@code areas}, as runs carry them. */
	static int mask(Set<SeaArea> areas)
	{
		int mask = 0;
		for (SeaArea area : areas) mask |= 1 << area.ordinal();
		return mask;
	}

	/** The areas of the bit set {@code mask}. */
	static Set<SeaArea> areas(int mask)
	{
		Set<SeaArea> areas = java.util.EnumSet.noneOf(SeaArea.class);
		for (SeaArea area : AREAS) if ((mask & (1 << area.ordinal())) != 0) areas.add(area);
		return areas;
	}

	/** Whether a straight run between the points crosses or touches no smoothed coast. */
	public boolean sees(double x0, double y0, double x1, double y1)
	{
		return walk(x0, y0, x1, y1, cell ->
		{
			for (int k = cellOffsets[cell]; k < cellOffsets[cell + 1]; k++)
			{
				int s = cellSegments[k];
				if (meets(x0, y0, x1, y1, ax[s], ay[s], bx[s], by[s])) return false;
			}
			return true;
		});
	}

	/**
	 * Whether a run from node {@code i} towards ({@code tx}, {@code ty}) may bend there: the coast vertex's neighbours
	 * both lie on one side of the line from the vertex to the point. Always, for a launch.
	 */
	public boolean tangent(int i, double tx, double ty)
	{
		if (Double.isNaN(vx[i])) return true;
		double a = cross(vx[i], vy[i], tx, ty, prevX[i], prevY[i]);
		double b = cross(vx[i], vy[i], tx, ty, nextX[i], nextY[i]);
		return a * b >= 0;
	}

	interface CellVisitor
	{
		/** False to stop. */
		boolean visit(int cell);
	}

	interface GridVisitor
	{
		/** False to stop. */
		boolean visit(int x, int y);
	}

	// Visits the index cells a segment passes through; false if the visitor stopped. Cells off the index are skipped
	private boolean walk(double x0, double y0, double x1, double y1, CellVisitor visitor)
	{
		return cellsAlong(x0, y0, x1, y1, CELL, (cx, cy) ->
		{
			int ix = cx - cellMinX, iy = cy - cellMinY;
			return ix < 0 || iy < 0 || ix >= cellsWide || iy >= cellsHigh || visitor.visit(iy * cellsWide + ix);
		});
	}

	/**
	 * Visits the cells of a grid of {@code size} a segment passes through, in order (Amanatides-Woo), and both neighbours
	 * where it passes exactly through a cell corner, cell (x, y) covering [x * size, (x + 1) * size); false if the visitor
	 * stopped.
	 */
	static boolean cellsAlong(double x0, double y0, double x1, double y1, double size, GridVisitor visitor)
	{
		int cx = (int) Math.floor(x0 / size), cy = (int) Math.floor(y0 / size);
		int ex = (int) Math.floor(x1 / size), ey = (int) Math.floor(y1 / size);
		double dx = x1 - x0, dy = y1 - y0;
		int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1;
		double tMaxX = dx == 0 ? Double.MAX_VALUE : ((dx > 0 ? (cx + 1) * size : cx * size) - x0) / dx;
		double tMaxY = dy == 0 ? Double.MAX_VALUE : ((dy > 0 ? (cy + 1) * size : cy * size) - y0) / dy;
		double tDeltaX = dx == 0 ? Double.MAX_VALUE : size / Math.abs(dx), tDeltaY = dy == 0 ? Double.MAX_VALUE : size / Math.abs(dy);
		int guard = Math.abs(ex - cx) + Math.abs(ey - cy) + 2;
		if (!visitor.visit(cx, cy)) return false;
		while ((cx != ex || cy != ey) && guard-- > 0)
		{
			if (Math.abs(tMaxX - tMaxY) < 1e-12)
			{
				if (!visitor.visit(cx + sx, cy) || !visitor.visit(cx, cy + sy)) return false;
				cx += sx;
				cy += sy;
				tMaxX += tDeltaX;
				tMaxY += tDeltaY;
			}
			else if (tMaxX < tMaxY)
			{
				cx += sx;
				tMaxX += tDeltaX;
			}
			else
			{
				cy += sy;
				tMaxY += tDeltaY;
			}
			if (!visitor.visit(cx, cy)) return false;
		}
		return true;
	}

	/** How near a node a boat of {@code kind} sailing to or from it comes, in tiles: its hull's reach, and at least 3. */
	static double near(SailingVessel.Kind kind)
	{
		return Math.max(3, kind.reach());
	}

	static double cross(double ax, double ay, double bx, double by, double cx, double cy)
	{
		return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
	}

	// Whether (px, py) lies strictly inside segment a-b
	private static boolean onInterior(double ax, double ay, double bx, double by, double px, double py)
	{
		if (cross(ax, ay, bx, by, px, py) != 0) return false;
		if ((px == ax && py == ay) || (px == bx && py == by)) return false;
		return Math.min(ax, bx) <= px && px <= Math.max(ax, bx) && Math.min(ay, by) <= py && py <= Math.max(ay, by);
	}

	/** Whether two segments cross or one touches the other's interior; sharing an end point doesn't count. */
	static boolean meets(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy)
	{
		double o1 = cross(ax, ay, bx, by, cx, cy), o2 = cross(ax, ay, bx, by, dx, dy);
		double o3 = cross(cx, cy, dx, dy, ax, ay), o4 = cross(cx, cy, dx, dy, bx, by);
		if (((o1 > 0 && o2 < 0) || (o1 < 0 && o2 > 0)) && ((o3 > 0 && o4 < 0) || (o3 < 0 && o4 > 0))) return true;
		return onInterior(ax, ay, bx, by, cx, cy) || onInterior(ax, ay, bx, by, dx, dy) || onInterior(cx, cy, dx, dy, ax, ay)
			|| onInterior(cx, cy, dx, dy, bx, by);
	}

	/**
	 * The graph as the layer entry holds it: each field as a column, the runs once each from their lower node as gaps
	 * between targets, and the coast's points (all tile corners) in half tiles, as gaps along their ring.
	 */
	public byte[] write()
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes))
		{
			out.writeInt(VERSION);
			int n = x.length;
			writeVarint(out, n);
			for (double v : x) out.writeFloat((float) v);
			for (double v : y) out.writeFloat((float) v);
			for (int i = 0; i < n; i++) out.writeBoolean(!Double.isNaN(vx[i]));
			for (double[] field : new double[][] {vx, vy, prevX, prevY, nextX, nextY})
			{
				for (double v : field) if (!Double.isNaN(v)) out.writeShort(halfTiles(v));
			}
			ByteArrayOutputStream areas = new ByteArrayOutputStream(), extras = new ByteArrayOutputStream();
			DataOutputStream extra = new DataOutputStream(extras);
			for (int i = 0; i < n; i++)
			{
				int count = 0;
				for (int k = runOffsets[i]; k < runOffsets[i + 1]; k++) if (runTargets[k] > i) count++;
				writeVarint(out, count);
				int previous = i;
				for (int k = runOffsets[i]; k < runOffsets[i + 1]; k++)
				{
					if (runTargets[k] <= i) continue;
					writeVarint(out, zigzag(runTargets[k] - previous));
					previous = runTargets[k];
					areas.write(runAreas[k]);
					writeVarint(extra, zigzag(runExtra[k]));
				}
			}
			areas.writeTo(out);
			extras.writeTo(out);
			writeVarint(out, moorings);
			for (int m = 0; m < moorings; m++) writeVarint(out, zigzag(launch[m]));
			for (float tiles : legTiles) out.writeFloat(tiles);
			writeVarint(out, ringStarts.length);
			for (int r = 0; r < ringStarts.length; r++) writeVarint(out, ringStarts[r] - (r == 0 ? 0 : ringStarts[r - 1]));
			for (int axis = 0; axis < 2; axis++)
			{
				int previous = 0;
				for (int k = axis; k < rings.length; k += 2)
				{
					int v = halfTiles(rings[k]);
					writeVarint(out, zigzag(v - previous));
					previous = v;
				}
			}
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	/** Reads {@code kind}'s graph from the layer entry {@code data}. */
	static SeaGraph read(SailingVessel.Kind kind, byte[] data)
	{
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data)))
		{
			int version = in.readInt();
			if (version != VERSION) throw new IllegalStateException("sea graph version " + version + ", expected " + VERSION);
			int n = readVarint(in);
			double[] x = new double[n], y = new double[n];
			for (int i = 0; i < n; i++) x[i] = in.readFloat();
			for (int i = 0; i < n; i++) y[i] = in.readFloat();
			boolean[] corner = new boolean[n];
			for (int i = 0; i < n; i++) corner[i] = in.readBoolean();
			double[][] fields = new double[6][n];
			for (double[] field : fields)
			{
				for (int i = 0; i < n; i++) field[i] = corner[i] ? in.readUnsignedShort() / 2.0 : Double.NaN;
			}
			int[][] upper = new int[n][];
			int[] degree = new int[n];
			int runs = 0;
			for (int i = 0; i < n; i++)
			{
				upper[i] = new int[readVarint(in)];
				int previous = i;
				for (int k = 0; k < upper[i].length; k++)
				{
					previous += unzigzag(readVarint(in));
					upper[i][k] = previous;
					degree[i]++;
					degree[previous]++;
				}
				runs += upper[i].length;
			}
			byte[] areas = new byte[runs];
			in.readFully(areas);
			int[] extras = new int[runs];
			for (int k = 0; k < runs; k++) extras[k] = unzigzag(readVarint(in));
			int[] runOffsets = new int[n + 1];
			for (int i = 0; i < n; i++) runOffsets[i + 1] = runOffsets[i] + degree[i];
			int[] runTargets = new int[runOffsets[n]], runExtra = new int[runOffsets[n]];
			byte[] runAreas = new byte[runOffsets[n]];
			int[] fill = Arrays.copyOf(runOffsets, n);
			int run = 0;
			for (int i = 0; i < n; i++)
			{
				for (int j : upper[i])
				{
					runTargets[fill[i]] = j;
					runExtra[fill[i]] = extras[run];
					runAreas[fill[i]++] = areas[run];
					runTargets[fill[j]] = i;
					runExtra[fill[j]] = extras[run];
					runAreas[fill[j]++] = areas[run++];
				}
			}
			int moorings = readVarint(in);
			int[] launch = new int[moorings];
			for (int m = 0; m < moorings; m++) launch[m] = unzigzag(readVarint(in));
			float[] legTiles = new float[moorings * moorings];
			for (int k = 0; k < legTiles.length; k++) legTiles[k] = in.readFloat();
			int[] ringStarts = new int[readVarint(in)];
			for (int r = 0; r < ringStarts.length; r++) ringStarts[r] = (r == 0 ? 0 : ringStarts[r - 1]) + readVarint(in);
			double[] rings = new double[2 * ringStarts[ringStarts.length - 1]];
			for (int axis = 0; axis < 2; axis++)
			{
				int previous = 0;
				for (int k = axis; k < rings.length; k += 2)
				{
					previous += unzigzag(readVarint(in));
					rings[k] = previous / 2.0;
				}
			}
			return new SeaGraph(kind, x, y, fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], runOffsets,
				runTargets, runAreas, runExtra, launch, legTiles, rings, ringStarts);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	// A coast point, a tile corner, in half tiles
	private static int halfTiles(double v)
	{
		double half = v * 2;
		if (half != Math.rint(half) || half < 0 || half > 0xFFFF) throw new IllegalStateException("not a tile corner: " + v);
		return (int) half;
	}

	private static int zigzag(int v)
	{
		return (v << 1) ^ (v >> 31);
	}

	private static int unzigzag(int v)
	{
		return (v >>> 1) ^ -(v & 1);
	}

	private static void writeVarint(DataOutputStream out, int v) throws IOException
	{
		while ((v & ~0x7F) != 0)
		{
			out.writeByte((v & 0x7F) | 0x80);
			v >>>= 7;
		}
		out.writeByte(v);
	}

	private static int readVarint(DataInputStream in) throws IOException
	{
		int v = 0;
		for (int shift = 0; ; shift += 7)
		{
			int b = in.readUnsignedByte();
			v |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) return v;
		}
	}
}
