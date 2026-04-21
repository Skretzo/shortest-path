package shortestpath.reachability.mode;

import shortestpath.TeleportationItem;
import shortestpath.TestShortestPathConfig;

final class NoTeleportsMode extends AbstractRouteMode {
    NoTeleportsMode() {
        super("NONE");
    }

    @Override
    protected void configureConfig(TestShortestPathConfig cfg) {
        cfg.setUseTeleportationItemsValue(TeleportationItem.NONE);
        cfg.setIncludeBankPathValue(false);
    }
}
