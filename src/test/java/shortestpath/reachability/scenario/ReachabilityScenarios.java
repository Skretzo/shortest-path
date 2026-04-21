package shortestpath.reachability.scenario;

/**
 * Built-in reachability scenarios selectable via {@code -Dreachability.scenario=...}.
 */
public final class ReachabilityScenarios {
    private static final ReachabilityScenario DEFAULT = new DefaultReachabilityScenario();
    private static final ReachabilityScenario PROFILER = new ProfilerReachabilityScenario();

    private ReachabilityScenarios() {
    }

    public static ReachabilityScenario fromSystemProperty(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        for (ReachabilityScenario scenario : new ReachabilityScenario[] { DEFAULT, PROFILER }) {
            if (scenario.id().equalsIgnoreCase(value)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown reachability scenario: " + value);
    }
}
