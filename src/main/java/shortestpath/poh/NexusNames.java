package shortestpath.poh;

import java.util.*;
import java.util.regex.*;
import net.runelite.client.util.Text;

/**
 * Turns the lines of the Portal Nexus interfaces into destinations. A line
 * may carry a hotkey prefix ("1 : Varrock"), an alternative name in
 * brackets ("Frozen Waste Plateau (Ghorrock)") or an older name the alias
 * table knows; it is matched against the portals' transport names with the
 * same normalization the keybind hints use. Lines naming no destination
 * (titles, amulet slots, empty slots) are skipped.
 */
final class NexusNames
{
	private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^()]*)\\)");
	private static final Map<String, PohNexusPortal> BY_NAME = byName();

	private NexusNames()
	{
	}

	static Set<PohNexusPortal> portals(Collection<String> lines)
	{
		Set<PohNexusPortal> portals = EnumSet.noneOf(PohNexusPortal.class);
		for (String line : lines)
		{
			PohNexusPortal portal = portal(line);
			if (portal != null)
			{
				portals.add(portal);
			}
		}
		return portals;
	}

	static PohNexusPortal portal(String line)
	{
		if (line == null)
		{
			return null;
		}
		String name = PohService.stripKeyPrefix(Text.removeTags(line).trim());
		PohNexusPortal portal = lookup(name);
		if (portal != null)
		{
			return portal;
		}
		Matcher matcher = PARENTHETICAL.matcher(name);
		while (matcher.find())
		{
			portal = lookup(matcher.group(1));
			if (portal != null)
			{
				return portal;
			}
		}
		return null;
	}

	private static PohNexusPortal lookup(String name)
	{
		String normalized = PohService.normalize(name);
		return normalized.isEmpty() ? null : BY_NAME.get(normalized);
	}

	private static Map<String, PohNexusPortal> byName()
	{
		Map<String, PohNexusPortal> byName = new HashMap<>();
		for (PohNexusPortal portal : PohNexusPortal.values())
		{
			for (String displayInfo : portal.displayInfos())
			{
				byName.putIfAbsent(PohService.normalize(displayInfo), portal);
			}
			for (String label : portal.toString().split(" / "))
			{
				byName.putIfAbsent(PohService.normalize(label), portal);
			}
		}
		return Collections.unmodifiableMap(byName);
	}
}
