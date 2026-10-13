package shortestpath.transport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import shortestpath.Util;
import shortestpath.WorldPointUtil;
import shortestpath.transport.parser.TransportRecord;
import shortestpath.transport.parser.TsvParser;

/**
 * The single typed resource-loading path for the plugin's bundled data.
 * <p>
 * Every classpath resource the plugin reads is anchored on this class and
 * exposed through one typed static entry point per resource. Each entry point
 * owns its resource's domain mapping <em>and its failure policy</em> — the
 * policies are not uniform: transports fail fast (NPE on a missing resource,
 * {@code RuntimeException} on {@link IOException}), league regions degrade to
 * an empty map with a warning, and the collision map hands the caller a raw
 * stream so the zip walk keeps its own failure posture. These postures are
 * behaviour; they are preserved verbatim from the incumbent readers.
 * <p>
 * The class is a loader, not a universal TSV iterator — per-resource row
 * idioms that diverge from {@link TsvParser} stay as private methods here
 * rather than being normalized.
 */
public final class ResourceLoader
{
	private static final TsvParser tsvParser = new TsvParser();

	private ResourceLoader()
	{
	}

	private static void addTransports(
		Map<Integer, Set<Transport>> transports, String path, TransportType transportType,
		int radiusThreshold)
	{
		try
		{
			String s = new String(
				Util.readAllBytes(Objects.requireNonNull(ResourceLoader.class.getResourceAsStream(path))),
				StandardCharsets.UTF_8);
			addTransportsFromContents(transports, s, transportType, radiusThreshold);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static void addTransportsFromContents(
		Map<Integer, Set<Transport>> transports,
		String contents,
		TransportType transportType,
		int radiusThreshold)
	{
		List<TransportRecord> records = tsvParser.parse(contents);

		Set<Transport> newTransports = new LinkedHashSet<>();
		for (TransportRecord record : records)
		{
			Transport transport = new Transport(record, transportType);
			newTransports.add(transport);
		}

		/*
		 * A transport with origin A and destination B is one-way and must
		 * be duplicated as origin B and destination A to become two-way.
		 * Example: key-locked doors
		 *
		 * A transport with origin A and a missing destination is one-way,
		 * but can go from origin A to all destinations with a missing origin.
		 * Example: fairy ring AIQ -> <blank>
		 *
		 * A transport with a missing origin and destination B is one-way,
		 * but can go from all origins with a missing destination to destination B.
		 * Example: fairy ring <blank> -> AIQ
		 *
		 * Identical transports from origin A to destination A are skipped, and
		 * non-identical transports from origin A to destination A can be skipped
		 * by specifying a radius threshold to ignore almost identical coordinates.
		 * Example: fairy ring AIQ -> AIQ
		 */
		Set<Transport> transportOrigins = new LinkedHashSet<>();
		Set<Transport> transportDestinations = new LinkedHashSet<>();
		for (Transport transport : newTransports)
		{
			int origin = transport.getOrigin();
			int destination = transport.getDestination();
			// Logic to determine ordinary transport vs teleport vs permutation (e.g. fairy
			// ring)
			if ((origin == Transport.UNDEFINED_ORIGIN && destination == Transport.UNDEFINED_DESTINATION)
				|| (origin == Transport.LOCATION_PERMUTATION && destination == Transport.LOCATION_PERMUTATION))
			{
				continue;
			}
			else if (origin != Transport.LOCATION_PERMUTATION && origin != Transport.UNDEFINED_ORIGIN
				&& destination == Transport.LOCATION_PERMUTATION)
			{
				transportOrigins.add(transport);
			}
			else if (origin == Transport.LOCATION_PERMUTATION && destination != Transport.UNDEFINED_DESTINATION)
			{
				transportDestinations.add(transport);
			}
			if (origin != Transport.LOCATION_PERMUTATION
				&& destination != Transport.UNDEFINED_DESTINATION && destination != Transport.LOCATION_PERMUTATION
				&& (origin == Transport.UNDEFINED_ORIGIN || origin != destination))
			{
				transports.computeIfAbsent(origin, k -> new LinkedHashSet<>()).add(transport);
			}
		}
		for (Transport origin : transportOrigins)
		{
			for (Transport destination : transportDestinations)
			{
				// The radius threshold prevents transport permutations from including (almost)
				// same origin and destination
				if (WorldPointUtil.distanceBetween2D(origin.getOrigin(), destination.getDestination()) > radiusThreshold)
				{
					Transport combined = new Transport(origin, destination);
					transports
						.computeIfAbsent(origin.getOrigin(), k -> new LinkedHashSet<>())
						.add(combined);
				}
			}
		}
	}

	/**
	 * Loads every bundled transport TSV (one per {@link TransportType} with a
	 * resource path) and returns the origin-keyed transport map with
	 * requirement interning applied. A missing resource throws
	 * {@link NullPointerException}; an {@link IOException} is wrapped in
	 * {@link RuntimeException}.
	 */
	public static HashMap<Integer, Set<Transport>> transports()
	{
		HashMap<Integer, Set<Transport>> transports = new HashMap<>();

		for (TransportType type : TransportType.values())
		{
			if (type.hasResourcePath())
			{
				addTransports(transports, type.getResourcePath(), type,
					type.hasRadiusThreshold() ? type.getRadiusThreshold() : 0);
			}
		}

		internRequirements(transports);

		return transports;
	}

	/**
	 * Deduplicates the requirement objects shared across the loaded transports, so identical
	 * {@code TransportItems}/{@code VarRequirement} requirements point at one shared instance
	 * instead of a per-transport copy (issue #491). The interning pools are local and discarded
	 * once loading finishes.
	 */
	private static void internRequirements(Map<Integer, Set<Transport>> transports)
	{
		LoadInterner interner = new LoadInterner();
		Set<Transport> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Set<Transport> set : transports.values())
		{
			for (Transport transport : set)
			{
				if (visited.add(transport))
				{
					transport.internRequirements(interner);
				}
			}
		}
	}
}
