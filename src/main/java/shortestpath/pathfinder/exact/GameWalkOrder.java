package shortestpath.pathfinder.exact;

import shortestpath.pathfinder.CollisionMap;

/**
 * The order the game's own walking expands a tile's neighbours — W, E, S, N, SW, SE, NW, NE —
 * as the {@link CollisionMap#ordinaryWalkingMask} bit and step delta of each direction. Shared
 * by the walk passes that model that order — the leg canonicaliser and the in-game walk
 * rewriter — which would silently disagree with the game if the tables ever drifted apart.
 * Package-private:
 * the arrays are mutable, so they are not exposed to callers.
 */
final class GameWalkOrder
{
	// CollisionMap.ordinaryWalkingMask bits (0-N, 1-NE, 2-E, 3-SE, 4-S, 5-SW, 6-W, 7-NW) in the
	// game's expansion order W, E, S, N, SW, SE, NW, NE; the first four are cardinal.
	static final int[] ORDER_BITS = {6, 2, 4, 0, 5, 3, 7, 1};
	static final int[] ORDER_DX = {-1, 1, 0, 0, -1, 1, -1, 1};
	static final int[] ORDER_DY = {0, 0, -1, 1, -1, -1, 1, 1};

	private GameWalkOrder()
	{
	}
}
