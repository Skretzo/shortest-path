package shortestpath.pathfinder.sailing;

import java.util.Arrays;
import static net.runelite.api.Constants.REGION_SIZE;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.SplitFlagMap;

/** A number per tile of the map, made a region at a time as tiles are written, for one search's costs. */
final class TileInts
{
	private static final int REGION_SHIFT = Integer.numberOfTrailingZeros(REGION_SIZE);
	private static final int REGION_MASK = REGION_SIZE - 1;
	private static final int PLANES = 4;

	private final SplitFlagMap.RegionExtent extent;
	private final int unset, offMap;
	private final int[][] pages;

	/**
	 * @param unset  what a tile holds until it is written
	 * @param offMap what a tile off the map always holds
	 */
	TileInts(SplitFlagMap.RegionExtent extent, int unset, int offMap)
	{
		this.extent = extent;
		this.unset = unset;
		this.offMap = offMap;
		pages = new int[(extent.getWidth() + 1) * (extent.getHeight() + 1) * PLANES][];
	}

	int get(int tile)
	{
		int region = region(tile);
		if (region < 0) return offMap;
		int[] page = pages[region];
		return page == null ? unset : page[index(tile)];
	}

	/** Writes {@code tile}; a tile off the map keeps holding what it holds. */
	void set(int tile, int value)
	{
		int region = region(tile);
		if (region < 0) return;
		int[] page = pages[region];
		if (page == null)
		{
			page = new int[REGION_SIZE * REGION_SIZE];
			Arrays.fill(page, unset);
			pages[region] = page;
		}
		page[index(tile)] = value;
	}

	private int region(int tile)
	{
		int regionX = (WorldPointUtil.unpackWorldX(tile) >> REGION_SHIFT) - extent.getMinX();
		int regionY = (WorldPointUtil.unpackWorldY(tile) >> REGION_SHIFT) - extent.getMinY();
		int plane = WorldPointUtil.unpackWorldPlane(tile);
		if (regionX < 0 || regionY < 0 || regionX > extent.getWidth() || regionY > extent.getHeight()
			|| plane >= PLANES) return -1;
		return (regionX + regionY * (extent.getWidth() + 1)) * PLANES + plane;
	}

	private static int index(int tile)
	{
		return ((WorldPointUtil.unpackWorldY(tile) & REGION_MASK) << REGION_SHIFT) | (WorldPointUtil.unpackWorldX(tile) & REGION_MASK);
	}
}
