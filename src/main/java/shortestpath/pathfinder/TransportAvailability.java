package shortestpath.pathfinder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

@Getter
public final class TransportAvailability {
    private final Map<Integer, Set<Transport>> transportsByOrigin;
    private final PrimitiveIntHashMap<Set<Transport>> transportsPacked;
    private final Set<Transport> usableTeleports;

    TransportAvailability(
        Map<Integer, Set<Transport>> transportsByOrigin,
        PrimitiveIntHashMap<Set<Transport>> transportsPacked,
        Set<Transport> usableTeleports
    ) {
        this.transportsByOrigin = transportsByOrigin;
        this.transportsPacked = transportsPacked;
        this.usableTeleports = usableTeleports;
    }

    public Set<Transport> getTransportsAt(int origin) {
        return transportsPacked.getOrDefault(origin, Set.of());
    }

    /*
     * Build a TransportAvailability by incrementally adding available transports.
     */
    static final class Builder {
        private final Map<Integer, Set<Transport>> transportsByOrigin;
        private final PrimitiveIntHashMap<Set<Transport>> transportsPacked;
        private final Set<Transport> usableTeleports;

        Builder(int expectedTransportCount) {
            this.transportsByOrigin = new HashMap<>(expectedTransportCount / 2);
            this.transportsPacked = new PrimitiveIntHashMap<>(expectedTransportCount / 2);
            this.usableTeleports = new HashSet<>(expectedTransportCount / 20);
        }

        void add(Transport transport) {
            if (transport.getOrigin() == WorldPointUtil.UNDEFINED) {
                usableTeleports.add(transport);
                return;
            }

            int origin = transport.getOrigin();
            Set<Transport> transportsAtOrigin = transportsByOrigin.computeIfAbsent(origin, ignored -> new HashSet<>());
            transportsAtOrigin.add(transport);
            transportsPacked.put(origin, transportsAtOrigin);
        }

        void remapPohTransports() {
            int pohLanding = WorldPointUtil.packWorldPoint(1923, 5709, 0);
            Set<Transport> pohTransports = new HashSet<>();
            Set<Integer> pohOriginsToRemove = new HashSet<>();

            for (Map.Entry<Integer, Set<Transport>> entry : transportsByOrigin.entrySet()) {
                int origin = entry.getKey();
                int originX = WorldPointUtil.unpackWorldX(origin);
                int originY = WorldPointUtil.unpackWorldY(origin);
                if (shortestpath.ShortestPathPlugin.isInsidePoh(originX, originY)) {
                    pohTransports.addAll(entry.getValue());
                    pohOriginsToRemove.add(origin);
                }
            }

            for (Integer origin : pohOriginsToRemove) {
                transportsByOrigin.remove(origin);
            }

            if (!pohTransports.isEmpty()) {
                Set<Transport> existingPohTransports = transportsByOrigin.getOrDefault(pohLanding, new HashSet<>());
                existingPohTransports.addAll(pohTransports);
                transportsByOrigin.put(pohLanding, existingPohTransports);
                transportsPacked.put(pohLanding, existingPohTransports);
            }
        }

        TransportAvailability build() {
            return new TransportAvailability(transportsByOrigin, transportsPacked, usableTeleports);
        }
    }
}
