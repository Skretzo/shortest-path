package shortestpath.reachability.mode;

/**
 * Strategy for applying pathfinder-related settings (teleport rules, bank routing, client stubs)
 * for reachability verification.
 */
public interface RouteMode {
    /** Stable id matching the {@code teleports} column in route CSVs (e.g. {@code ALL}, {@code BANK}). */
    String id();

    void apply(RouteModeContext ctx);

    /**
     * Value stubbed for {@code VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE} in this mode (for dashboard display).
     */
    default int lumbridgeDiaryEliteStub() {
        return 1;
    }
}
