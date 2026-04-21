package shortestpath.reachability.mode;

import net.runelite.api.gameval.VarbitID;
import shortestpath.TeleportationItem;
import shortestpath.TestShortestPathConfig;

/**
 * Bank-start mode: empty inventory, universal bank available; elite Lumbridge diary disabled so fairy-ring
 * tests must pick up dramen staff from the bank.
 */
final class BankMode extends AbstractRouteMode {
    BankMode() {
        super("BANK");
    }

    @Override
    protected void configureConfig(TestShortestPathConfig cfg) {
        cfg.setUseTeleportationItemsValue(TeleportationItem.INVENTORY_AND_BANK);
        cfg.setIncludeBankPathValue(true);
    }

    @Override
    protected void configureClient(RouteModeContext ctx) {
        ctx.stubVarbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0);
    }

    @Override
    public int lumbridgeDiaryEliteStub() {
        return 0;
    }

    @Override
    protected void configureBank(RouteModeContext ctx) {
        ctx.setPathfinderBank(ctx.universalBankContainer());
    }
}
