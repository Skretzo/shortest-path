package shortestpath.pathfinder;

import java.util.Objects;
import shortestpath.Transport;
import shortestpath.TransportType;

/**
 * A unique identifier for a Transport based on its origin, destination, and type.
 * Used for excluding specific transports in alternative path calculations.
 */
public class TransportId {
    private final int origin;
    private final int destination;
    private final TransportType type;

    public TransportId(Transport transport) {
        this.origin = transport.getOrigin();
        this.destination = transport.getDestination();
        this.type = transport.getType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransportId that = (TransportId) o;
        return origin == that.origin && 
               destination == that.destination && 
               type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, destination, type);
    }

    @Override
    public String toString() {
        return String.format("TransportId{origin=%d, destination=%d, type=%s}", origin, destination, type);
    }
}