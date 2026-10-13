package shortestpath.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility forwarder to {@link ResourceLoader}. Retained so incumbent
 * call sites keep compiling while callers re-point at the loader's typed
 * entry points; the transports machinery lives there now.
 */
public class TransportLoader
{
	public static void addTransportsFromContents(
		Map<Integer, Set<Transport>> transports,
		String contents,
		TransportType transportType,
		int radiusThreshold)
	{
		ResourceLoader.addTransportsFromContents(transports, contents, transportType, radiusThreshold);
	}

	public static HashMap<Integer, Set<Transport>> loadAllFromResources()
	{
		return ResourceLoader.transports();
	}
}
