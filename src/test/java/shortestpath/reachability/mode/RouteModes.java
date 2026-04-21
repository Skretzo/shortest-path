package shortestpath.reachability.mode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of {@link RouteMode} instances by id (case-insensitive).
 */
public final class RouteModes {
    private static final RouteModes INSTANCE = buildDefaults();

    private final Map<String, RouteMode> byId;

    private RouteModes(Map<String, RouteMode> byId) {
        this.byId = byId;
    }

    public RouteMode get(String id) {
        String key = id.trim().toUpperCase(Locale.ROOT);
        RouteMode mode = byId.get(key);
        if (mode == null) {
            throw new IllegalArgumentException("Unknown route mode: " + id);
        }
        return mode;
    }

    /** Singleton registry so CSV-loaded modes and scenario defaults share the same instances (reference equality). */
    public static RouteModes defaults() {
        return INSTANCE;
    }

    private static RouteModes buildDefaults() {
        Map<String, RouteMode> map = new LinkedHashMap<>();
        register(map, new NoTeleportsMode());
        register(map, new InventoryNonConsumableMode());
        register(map, new InventoryMode());
        register(map, new AllTeleportsMode());
        register(map, new BankMode());
        return new RouteModes(Collections.unmodifiableMap(map));
    }

    private static void register(Map<String, RouteMode> map, RouteMode mode) {
        map.put(mode.id(), mode);
    }
}
