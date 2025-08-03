package shortestpath;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TeleportationItem {
    NONE("None"),
    INVENTORY("Inventory"),
    INVENTORY_NON_CONSUMABLE("Inventory (perm)"),
    ALL("All"),
    ALL_NON_CONSUMABLE("All (perm)"),
    INVENTORY_AND_BANK("Inventory and Bank"),
    INVENTORY_AND_BANK_NON_CONSUMABLE("Inventory and Bank (perm)");

    private final String type;

    @Override
    public String toString() {
        return type;
    }

    public static TeleportationItem fromType(String type) {
        for (TeleportationItem teleportationItem : values()) {
            if (teleportationItem.type.equals(type)) {
                return teleportationItem;
            }
        }
        return null;
    }
}
