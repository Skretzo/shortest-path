package shortestpath;

import java.util.List;
import org.junit.Test;
import shortestpath.SnapshotAssertions.SnapshotState;
import shortestpath.pathfinder.PathStep;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class SnapshotAssertionsTest
{
	@Test
	public void routeSnapshotIncludesCoordinatesAndTransports()
	{
		int start = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		int walked = WorldPointUtil.packWorldPoint(3201, 3200, 0);
		int destination = WorldPointUtil.packWorldPoint(3300, 3300, 0);
		Transport teleport = new Transport.TransportBuilder()
			.origin(walked)
			.destination(destination)
			.type(TransportType.TELEPORTATION_ITEM)
			.duration(4)
			.displayInfo("Example teleport")
			.build();

		SnapshotState state = SnapshotState.withTransports((origin, bankVisited) ->
			origin == walked ? List.of(teleport) : List.of());

		SnapshotAssertions.assertSnapshot(
			state,
			"snapshot-assertions-route",
			List.of(
				new PathStep(start, false),
				new PathStep(walked, false),
				new PathStep(destination, true)));

		SnapshotAssertions.assertSnapshot(
			"snapshot-assertions-coordinate-list",
			List.of(start, walked, destination, 66));
	}
}
