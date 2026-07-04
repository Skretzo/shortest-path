package shortestpath;

/**
 * Which teleport/transport methods the alternative-routes feature considers when searching.
 * Two families, each with two variants:
 * <ul>
 * <li><b>Owned</b> — only methods whose required items the player actually possesses:
 * {@link #OWNED_INVENTORY} (inventory + equipment) or {@link #OWNED_WITH_BANK} (also items stored in
 * the bank, routing through a bank to pick them up).</li>
 * <li><b>All</b> — possession is ignored: {@link #ALL_UNLOCKED} still respects character unlocks
 * (skill levels, quests, varbits/diaries), while {@link #ALL_EVERYTHING} shows every method in the
 * game, including ones known to be unavailable to this character.</li>
 * </ul>
 */
public enum AlternativeRoutesMode
{
	/**
	 * Owned: items in inventory or equipment only (forces the INVENTORY teleport-item setting).
	 */
	OWNED_INVENTORY,
	/**
	 * Owned: inventory + equipment + bank, routing through a bank to withdraw banked items (mirrors
	 * Shortest Path's {@code INVENTORY_AND_BANK} + {@code includeBankPath}).
	 */
	OWNED_WITH_BANK,
	/**
	 * All: possession ignored, but character unlocks (skills, quests, varbits) still apply — methods
	 * the player could use if they obtained the item.
	 */
	ALL_UNLOCKED,
	/**
	 * All: every method in the game, including ones known to be unavailable (quest/skill-locked).
	 */
	ALL_EVERYTHING;

	public boolean isOwned()
	{
		return this == OWNED_INVENTORY || this == OWNED_WITH_BANK;
	}

	/**
	 * The second sub-option of each family (Inventory+bank under Owned, Everything under All).
	 */
	public boolean isSecondVariant()
	{
		return this == OWNED_WITH_BANK || this == ALL_EVERYTHING;
	}
}
