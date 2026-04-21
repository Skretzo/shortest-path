package shortestpath.reachability.mode;

import shortestpath.TeleportationItem;
import shortestpath.TestShortestPathConfig;

final class InventoryMode extends AbstractRouteMode {
    InventoryMode() {
        super("INVENTORY");
    }

    @Override
    protected void configureConfig(TestShortestPathConfig cfg) {
        cfg.setUseTeleportationItemsValue(TeleportationItem.INVENTORY);
        cfg.setIncludeBankPathValue(false);
    }
}
