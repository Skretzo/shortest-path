package shortestpath.reachability.mode;

import net.runelite.api.gameval.VarbitID;
import shortestpath.TestShortestPathConfig;

/**
 * Template method: config and client stubs, then rebuild pathfinder, then bank assignment, then refresh.
 * Order matches the previous {@code ReachabilityDashboardTest} apply-mode behavior.
 */
public abstract class AbstractRouteMode implements RouteMode {
    private final String id;

    protected AbstractRouteMode(String id) {
        this.id = id;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final void apply(RouteModeContext ctx) {
        configureConfig(ctx.config());
        configureClient(ctx);
        ctx.rebuildPathfinder();
        configureBank(ctx);
        ctx.pathfinder().refresh();
    }

    protected abstract void configureConfig(TestShortestPathConfig cfg);

    /**
     * Default: elite Lumbridge diary complete (bypass dramen staff requirement for fairy rings when not in BANK mode).
     */
    protected void configureClient(RouteModeContext ctx) {
        ctx.stubVarbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 1);
    }

    protected void configureBank(RouteModeContext ctx) {
        ctx.setPathfinderBank(null);
    }
}
