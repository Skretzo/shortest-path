package shortestpath;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

import java.util.Objects;

@Value
class PathParameters {
    private final PluginIdentifier requester;
    private final WorldPoint start;
    private final WorldPoint target;
    private final boolean markerHidden; // Show/hide the target marker on the world map
    private final boolean visible;

    public PathParameters toggleVisibility() {
        return new PathParameters(requester, start, target, markerHidden, !visible);
    }

    public boolean isStartSet() {
        return start != null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || !(other instanceof PathParameters)) {
            return false;
        }

        PathParameters otherParams = (PathParameters) other;
        return Objects.equals(requester, otherParams.requester)
                && Objects.equals(target, otherParams.target)
                && Objects.equals(start, otherParams.start)
                && markerHidden == otherParams.markerHidden
                && visible == otherParams.visible;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 37 + WorldPointUtil.packWorldPoint(start);
        hash = hash * 37 + WorldPointUtil.packWorldPoint(target);
        hash = hash * 37 + requester.hashCode();
        hash = hash * 37 + (markerHidden ? 1 : 0);
        return hash;
    }
}
