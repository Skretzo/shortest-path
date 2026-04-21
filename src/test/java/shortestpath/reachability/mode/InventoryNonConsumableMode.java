package shortestpath.reachability.mode;

import shortestpath.TeleportationItem;
import shortestpath.TestShortestPathConfig;

final class InventoryNonConsumableMode extends AbstractRouteMode {
    InventoryNonConsumableMode() {
        super("INVENTORY_NON_CONSUMABLE");
    }

    @Override
    protected void configureConfig(TestShortestPathConfig cfg) {
        cfg.setUseTeleportationItemsValue(TeleportationItem.INVENTORY_NON_CONSUMABLE);
        cfg.setIncludeBankPathValue(false);
    }
}
