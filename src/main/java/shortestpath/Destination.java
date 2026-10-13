package shortestpath;

import java.util.Map;
import java.util.Set;

import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.transport.ResourceLoader;

/**
 * Compatibility forwarder to {@link ResourceLoader}. Destination coordinate
 * sets are grouped by feature category and stored in tab-separated value
 * (TSV) resources under {@code /destinations/**}; the loading machinery and
 * its per-resource failure policy live there now. Retained so incumbent call
 * sites keep compiling while callers re-point at the loader's typed entry
 * points.
 */
public class Destination
{
	/**
	 * Loads a predefined subset of destination categories from bundled TSV resources.
	 *
	 * @return a map from destination category key to a set of packed world point integers.
	 */
	public static Map<String, Set<Integer>> loadAllFromResources()
	{
		return ResourceLoader.destinations();
	}

	/**
	 * Per-packed-point requirements for bank tiles, parsed from {@code bank.tsv} columns
	 * Skills, Quests, Varbits, and VarPlayers (same TSV format as transports).
	 * Tiles with no requirement rows behave as {@link DestinationRequirements#EMPTY}.
	 */
	public static Map<Integer, DestinationRequirements> loadBankRequirementsFromResources()
	{
		return ResourceLoader.bankRequirements();
	}
}
