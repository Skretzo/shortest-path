package shortestpath.settings;

import lombok.*;

@Getter
@RequiredArgsConstructor
public enum TeleportationItem
{
	NONE("None"),
	INVENTORY("Inventory"),
	INVENTORY_NON_CONSUMABLE("Inventory (perm)"),
	INVENTORY_AND_BANK("Inventory and Bank"),
	INVENTORY_AND_BANK_NON_CONSUMABLE("Inventory and Bank (perm)"),
	INVENTORY_ALL_AND_BANK_NON_CONSUMABLE("Inventory (all) and bank (perm)"),
	UNLOCKED("Unlocked"),
	UNLOCKED_NON_CONSUMABLE("Unlocked (perm)"),
	ALL("All"),
	ALL_NON_CONSUMABLE("All (perm)"),
	;

	private final String type;

	@Override
	public String toString()
	{
		return type;
	}
}
