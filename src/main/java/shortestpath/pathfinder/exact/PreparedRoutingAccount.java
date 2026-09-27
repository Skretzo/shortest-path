package shortestpath.pathfinder.exact;

import java.util.Arrays;
import java.util.function.ToIntFunction;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/** Immutable account/config snapshot used by the exact graph. */
public final class PreparedRoutingAccount
{
	private static final long FNV_OFFSET = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;

	private final boolean allowTransports;
	private final boolean bankPathEnabled;
	private final View[] local = new View[2];
	private final View[] global = new View[2];
	private final View[] wildernessGlobal = new View[2];
	private final long fingerprint;

	private PreparedRoutingAccount(
		boolean allowTransports, boolean bankPathEnabled, View[] local, View[] global, View[] wildernessGlobal)
	{
		this.allowTransports = allowTransports;
		this.bankPathEnabled = bankPathEnabled;
		this.local[0] = local[0];
		this.local[1] = local[1];
		this.global[0] = global[0];
		this.global[1] = global[1];
		this.wildernessGlobal[0] = wildernessGlobal[0];
		this.wildernessGlobal[1] = wildernessGlobal[1];
		this.fingerprint = computeFingerprint();
	}

	public static PreparedRoutingAccount compile(TransportAvailability carried, TransportAvailability banked,
		boolean bankPathEnabled, boolean allowTransports, ToIntFunction<Transport> additionalCost)
	{
		if (!allowTransports)
		{
			return new PreparedRoutingAccount(false, bankPathEnabled, new View[] {View.empty(true), View.empty(true)},
				new View[] {View.empty(false), View.empty(false)}, new View[] {View.empty(false), View.empty(false)});
		}

		View[] local = new View[2];
		View[] global = new View[2];
		View[] wilderness = new View[2];
		for (int bankedState = 0; bankedState < 2; bankedState++)
		{
			TransportAvailability availability = bankedState != 0 ? banked : carried;
			local[bankedState] = View.local(availability.getTransportsPacked(), additionalCost);
			global[bankedState] = View.global(availability.getUsableTeleports(), additionalCost, false);
			wilderness[bankedState] = View.global(availability.getUsableTeleports(), additionalCost, true);
		}
		return new PreparedRoutingAccount(true, bankPathEnabled, local, global, wilderness);
	}

	public boolean allowTransports()
	{
		return allowTransports;
	}
	public boolean bankPathEnabled()
	{
		return bankPathEnabled;
	}
	public long fingerprint()
	{
		return fingerprint;
	}
	public String fingerprintHex()
	{
		return String.format("%016x", fingerprint);
	}

	public int localCount(boolean banked)
	{
		return view(local, banked).count;
	}
	public int localOrigin(boolean banked, int index)
	{
		return view(local, banked).origins[index];
	}
	public int localDestination(boolean banked, int index)
	{
		return view(local, banked).destinations[index];
	}
	public int localCost(boolean banked, int index)
	{
		return view(local, banked).costs[index];
	}
	public int localType(boolean banked, int index)
	{
		return view(local, banked).types[index];
	}
	public int localMaxWilderness(boolean banked, int index)
	{
		return view(local, banked).maxWilderness[index];
	}
	public int globalCount(boolean banked)
	{
		return view(global, banked).count;
	}
	public int globalDestination(boolean banked, int index)
	{
		return view(global, banked).destinations[index];
	}
	public int globalCost(boolean banked, int index)
	{
		return view(global, banked).costs[index];
	}
	public int globalType(boolean banked, int index)
	{
		return view(global, banked).types[index];
	}
	public int globalMaxWilderness(boolean banked, int index)
	{
		return view(global, banked).maxWilderness[index];
	}
	public int wildernessGlobalCount(boolean banked)
	{
		return view(wildernessGlobal, banked).count;
	}
	public int wildernessGlobalDestination(boolean banked, int index)
	{
		return view(wildernessGlobal, banked).destinations[index];
	}
	public int wildernessGlobalCost(boolean banked, int index)
	{
		return view(wildernessGlobal, banked).costs[index];
	}
	public int wildernessGlobalType(boolean banked, int index)
	{
		return view(wildernessGlobal, banked).types[index];
	}
	public int wildernessGlobalMaxWilderness(boolean banked, int index)
	{
		return view(wildernessGlobal, banked).maxWilderness[index];
	}

	View localView(boolean banked)
	{
		return view(local, banked);
	}
	View globalView(boolean banked)
	{
		return view(global, banked);
	}

	private static View view(View[] views, boolean banked)
	{
		return views[banked ? 1 : 0];
	}

	private long computeFingerprint()
	{
		long hash = FNV_OFFSET;
		hash = mix(hash, allowTransports ? 1 : 0);
		hash = mix(hash, bankPathEnabled ? 1 : 0);
		for (int kind = 0; kind < 3; kind++)
		{
			for (int banked = 0; banked < 2; banked++)
			{
				View view = kind == 0 ? local[banked] : kind == 1 ? global[banked] : wildernessGlobal[banked];
				hash = mix(hash, kind);
				hash = mix(hash, banked);
				for (int i = 0; i < view.count; i++)
				{
					if (kind == 0)
						hash = u32(hash, view.origins[i]);
					hash = u32(hash, view.destinations[i]);
					hash = u32(hash, view.costs[i]);
					hash = u32(hash, view.types[i]);
					hash = u32(hash, view.maxWilderness[i]);
				}
			}
		}
		return hash;
	}

	private static long u32(long hash, int value)
	{
		for (int shift = 0; shift < 32; shift += 8)
			hash = mix(hash, value >>> shift);
		return hash;
	}

	private static long mix(long hash, int value)
	{
		return (hash ^ (value & 0xffL)) * FNV_PRIME;
	}

	static String transportTypeName(int ordinal)
	{
		return TransportType.values()[ordinal].name();
	}

	static final class View
	{
		final boolean local;
		final int count;
		final int[] origins;
		final int[] destinations;
		final int[] costs;
		final int[] types;
		final int[] maxWilderness;

		private View(boolean local, int[] origins, int[] destinations, int[] costs, int[] types, int[] maxWilderness)
		{
			this.local = local;
			this.count = destinations.length;
			this.origins = origins;
			this.destinations = destinations;
			this.costs = costs;
			this.types = types;
			this.maxWilderness = maxWilderness;
		}

		static View empty(boolean local)
		{
			return new View(local, new int[0], new int[0], new int[0], new int[0], new int[0]);
		}

		static View local(PrimitiveIntHashMap<Transport[]> map, ToIntFunction<Transport> additionalCost)
		{
			Builder builder = new Builder(true);
			int[] origins = map.keys();
			Arrays.sort(origins);
			for (int origin : origins)
			{
				Transport[] transports = map.getOrDefault(origin, TransportAvailability.EMPTY_TRANSPORTS);
				for (Transport transport : transports)
					builder.add(origin, transport, additionalCost);
			}
			return builder.build();
		}

		static View global(Transport[] transports, ToIntFunction<Transport> additionalCost, boolean wildernessOnly)
		{
			Builder builder = new Builder(false);
			for (Transport transport : transports)
			{
				if (!wildernessOnly || transport.getMaxWildernessLevel() >= 30)
				{
					builder.add(0, transport, additionalCost);
				}
			}
			return builder.build();
		}
	}

	private static final class Builder
	{
		private final boolean local;
		private int size;
		private int[] origins = new int[16];
		private int[] destinations = new int[16];
		private int[] costs = new int[16];
		private int[] types = new int[16];
		private int[] maxWilderness = new int[16];

		Builder(boolean local)
		{
			this.local = local;
		}

		void add(int origin, Transport transport, ToIntFunction<Transport> additionalCost)
		{
			if (size == destinations.length)
			{
				int capacity = size * 2;
				origins = Arrays.copyOf(origins, capacity);
				destinations = Arrays.copyOf(destinations, capacity);
				costs = Arrays.copyOf(costs, capacity);
				types = Arrays.copyOf(types, capacity);
				maxWilderness = Arrays.copyOf(maxWilderness, capacity);
			}
			origins[size] = origin;
			destinations[size] = transport.getDestination();
			costs[size] = ExactCosts.add(transport.getDuration(), additionalCost.applyAsInt(transport));
			types[size] = transport.getType().ordinal();
			maxWilderness[size] = transport.getMaxWildernessLevel();
			size++;
		}

		View build()
		{
			// Origins arrive in signed order but lookups need unsigned order, so plane 2/3 tiles
			// are out of place; an insertion sort was quadratic here.
			Integer[] order = new Integer[size];
			for (int i = 0; i < size; i++)
				order[i] = i;
			Arrays.sort(order, this::compare);
			int[] sortedOrigins = new int[size];
			int[] sortedDestinations = new int[size];
			int[] sortedCosts = new int[size];
			int[] sortedTypes = new int[size];
			int[] sortedMaxWilderness = new int[size];
			for (int i = 0; i < size; i++)
			{
				int from = order[i];
				sortedOrigins[i] = origins[from];
				sortedDestinations[i] = destinations[from];
				sortedCosts[i] = costs[from];
				sortedTypes[i] = types[from];
				sortedMaxWilderness[i] = maxWilderness[from];
			}
			return new View(local, sortedOrigins, sortedDestinations, sortedCosts, sortedTypes, sortedMaxWilderness);
		}

		private int compare(int left, int right)
		{
			int result = Integer.compareUnsigned(origins[left], origins[right]);
			if (result != 0)
				return result;
			result = Integer.compareUnsigned(destinations[left], destinations[right]);
			if (result != 0)
				return result;
			result = Integer.compare(costs[left], costs[right]);
			if (result != 0)
				return result;
			result = Integer.compare(types[left], types[right]);
			return result != 0 ? result : Integer.compare(maxWilderness[left], maxWilderness[right]);
		}
	}
}
