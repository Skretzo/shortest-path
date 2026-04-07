package shortestpath.transport;

import lombok.Value;

@Value
public class TransportCoordinateItem {
    String id;
    String label;
    String coordinate;
    String source;
}
