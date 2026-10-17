package shortestpath.transport;

import shortestpath.WorldPointUtil;

/**
 * The six hot-air-balloon destination routes — one constant per destination
 * row of {@code hot_air_balloons.tsv} (the {@code Use Basket} departure rows
 * carry no destination and are not routes). The player's log-basket state is
 * declared per route through the {@code balloonLogBasketRoutes} config set:
 * selecting a route means its basket is stocked, which satisfies the
 * {@code UNLOCK_BALLOON_LOG_BASKET} OR-branch on that row and waives the
 * route's log item — unselected routes still demand their specific log.
 */
public enum BalloonRoute
{
	ENTRANA("Entrana", 2808, 3354, 0, 1511, "logs"),
	TAVERLEY("Taverley", 2941, 3420, 0, 1511, "logs"),
	CRAFTING_GUILD("Crafting Guild", 2923, 3302, 0, 1521, "oak logs"),
	VARROCK("Varrock", 3299, 3482, 0, 1519, "willow logs"),
	CASTLE_WARS("Castle Wars", 2459, 3110, 0, 1515, "yew logs"),
	GRAND_TREE("Grand Tree", 2478, 3456, 0, 1513, "magic logs"),
	;

	private final String label;
	private final int packedDestination;
	private final int logItemId;
	private final String logLabel;

	BalloonRoute(String label, int x, int y, int plane, int logItemId, String logLabel)
	{
		this.label = label;
		this.packedDestination = WorldPointUtil.packWorldPoint(x, y, plane);
		this.logItemId = logItemId;
		this.logLabel = logLabel;
	}

	/**
	 * The destination tile this route lands on, packed the same way the TSV
	 * row's Destination column is.
	 */
	public int getPackedDestination()
	{
		return packedDestination;
	}

	/**
	 * The destination name as the TSV row's Display info cell writes it.
	 */
	public String getLabel()
	{
		return label;
	}

	/**
	 * The item id of the log this route's basket consumes.
	 */
	public int getLogItemId()
	{
		return logItemId;
	}

	/**
	 * The lowercase display name of that log ("oak logs").
	 */
	public String getLogLabel()
	{
		return logLabel;
	}

	/**
	 * The per-route checkbox text, e.g. {@code "Grand Tree (magic logs)"}.
	 */
	public String displayLabel()
	{
		return label + " (" + logLabel + ")";
	}
}
