package shortestpath.reachability.mode;

import shortestpath.TeleportationItem;
import shortestpath.TestShortestPathConfig;

final class AllTeleportsMode extends AbstractRouteMode {
    AllTeleportsMode() {
        super("ALL");
    }

    @Override
    protected void configureConfig(TestShortestPathConfig cfg) {
        cfg.setUseTeleportationItemsValue(TeleportationItem.ALL);
        cfg.setIncludeBankPathValue(false);
    }
}
