package shortestpath.pathfinder.exact;

import java.util.Arrays;
import java.util.Collection;
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
	private static final TeleportCapability[] CAPABILITIES = TeleportCapability.values();

	private final boolean allowTransports;
	private final boolean bankPathEnabled;
	/** Bank tiles this account may use, in unsigned order: a subset of the static bank tiles. */
	private final int[] accessibleBankTiles;
	private final int bankVisitCost;
	private final View[] local = new View[2];
	/** The global teleports castable with each {@link TeleportCapability}, by ordinal, in each bank layer. */
	private final View[][] global;
	private final long fingerprint;

	private PreparedRoutingAccount(boolean allowTransports, boolean bankPathEnabled, int[] accessibleBankTiles,
		int bankVisitCost, View[] local, View[][] global)
	{
		this.allowTransports = allowTransports;
		this.bankPathEnabled = bankPathEnabled;
		this.accessibleBankTiles = accessibleBankTiles;
		this.bankVisitCost = bankVisitCost;
		this.local[0] = local[0];
		this.local[1] = local[1];
		this.global = global;
		this.fingerprint = computeFingerprint();
	}

	/**
	 * @param accessibleBankTiles the bank tiles whose requirements this account meets, as resolved by
	 * {@code PathfinderConfig.bankAccessible}; the static data knows every bank, so this decides
	 * which of them may switch a route into the banked layer
	 * @param bankVisitCost what switching into the banked layer costs, as legacy's bank-visit edge
	 * ({@code PathfinderConfig.getBankVisitCost})
	 */
	public static PreparedRoutingAccount compile(TransportAvailability carried, TransportAvailability banked,
		boolean bankPathEnabled, Collection<Integer> accessibleBankTiles, int bankVisitCost,
		boolean allowTransports, ToIntFunction<Transport> additionalCost)
	{
		int[] banks = sortedUnsigned(accessibleBankTiles);
		ExactCosts.validate(bankVisitCost);
		View[] local = new View[2];
		View[][] global = new View[CAPABILITIES.length][2];
		for (int bankedState = 0; bankedState < 2; bankedState++)
		{
			TransportAvailability availability = bankedState != 0 ? banked : carried;
			local[bankedState] = allowTransports
				? View.local(availability.getTransportsPacked(), additionalCost) : View.empty(true);
			for (TeleportCapability capability : CAPABILITIES)
				global[capability.ordinal()][bankedState] = allowTransports
					? View.global(availability.getUsableTeleports(), additionalCost, capability) : View.empty(false);
		}
		return new PreparedRoutingAccount(allowTransports, bankPathEnabled, banks, bankVisitCost, local, global);
	}

	private static int[] sortedUnsigned(Collection<Integer> tiles)
	{
		int[] result = new int[tiles.size()];
		int size = 0;
		for (int tile : tiles)
			result[size++] = tile ^ Integer.MIN_VALUE;
		Arrays.sort(result);
		int unique = 0;
		for (int i = 0; i < size; i++)
			if (unique == 0 || result[unique - 1] != result[i])
				result[unique++] = result[i];
		for (int i = 0; i < unique; i++)
			result[i] ^= Integer.MIN_VALUE;
		return Arrays.copyOf(result, unique);
	}

	public boolean allowTransports()
	{
		return allowTransports;
	}
	public boolean bankPathEnabled()
	{
		return bankPathEnabled;
	}
	/**
	 * Whether this account may bank at {@code packedTile}. Only meaningful for a tile the static
	 * data marks as a bank; the forward search asks only there, so the binary search stays rare.
	 */
	public boolean bankAccessible(int packedTile)
	{
		int low = 0, high = accessibleBankTiles.length - 1;
		while (low <= high)
		{
			int middle = (low + high) >>> 1;
			int compare = Integer.compareUnsigned(accessibleBankTiles[middle], packedTile);
			if (compare == 0) return true;
			if (compare < 0) low = middle + 1;
			else high = middle - 1;
		}
		return false;
	}
	/** The cost of switching into the banked layer at an accessible bank. */
	public int bankVisitCost()
	{
		return bankVisitCost;
	}
	public int accessibleBankCount()
	{
		return accessibleBankTiles.length;
	}
	public int accessibleBankTile(int index)
	{
		return accessibleBankTiles[index];
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
	/**
	 * The local transport's action class: entries in one layer share a class exactly when they are
	 * the same game action (type, object and menu option, display text, consumption and item
	 * requirements) to the same destination at the same cost, differing at most in origin. Taking
	 * any of them leads to the same state, so a route may take the action from whichever origin.
	 */
	public int localActionClass(boolean banked, int index)
	{
		return view(local, banked).actionClasses[index];
	}
	/** The global teleports castable outside the wilderness: every global this account has. */
	public int globalCount(boolean banked)
	{
		return globalCount(TeleportCapability.ALL, banked);
	}
	public int globalDestination(boolean banked, int index)
	{
		return globalDestination(TeleportCapability.ALL, banked, index);
	}
	public int globalCost(boolean banked, int index)
	{
		return globalCost(TeleportCapability.ALL, banked, index);
	}
	public int globalType(boolean banked, int index)
	{
		return globalView(TeleportCapability.ALL, banked).types[index];
	}
	public int globalMaxWilderness(boolean banked, int index)
	{
		return globalMaxWilderness(TeleportCapability.ALL, banked, index);
	}
	/** The global teleports castable with {@code capability}. */
	public int globalCount(TeleportCapability capability, boolean banked)
	{
		return globalView(capability, banked).count;
	}
	public int globalDestination(TeleportCapability capability, boolean banked, int index)
	{
		return globalView(capability, banked).destinations[index];
	}
	public int globalCost(TeleportCapability capability, boolean banked, int index)
	{
		return globalView(capability, banked).costs[index];
	}
	public int globalMaxWilderness(TeleportCapability capability, boolean banked, int index)
	{
		return globalView(capability, banked).maxWilderness[index];
	}
	public Transport globalTransport(TeleportCapability capability, boolean banked, int index)
	{
		return globalView(capability, banked).transports[index];
	}

	View localView(boolean banked)
	{
		return view(local, banked);
	}
	View globalView(boolean banked)
	{
		return globalView(TeleportCapability.ALL, banked);
	}
	View globalView(TeleportCapability capability, boolean banked)
	{
		return view(global[capability.ordinal()], banked);
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
		hash = u32(hash, bankVisitCost);
		hash = u32(hash, accessibleBankTiles.length);
		for (int tile : accessibleBankTiles)
			hash = u32(hash, tile);
		for (int kind = 0; kind <= CAPABILITIES.length; kind++)
		{
			for (int banked = 0; banked < 2; banked++)
			{
				View view = kind == 0 ? local[banked] : global[kind - 1][banked];
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
		final int[] actionClasses;
		/** The transport each row represents; parallel to the other arrays so a search can
		 *  report which transport produced an edge, not just its effect. */
		final Transport[] transports;

		private View(boolean local, int[] origins, int[] destinations, int[] costs, int[] types, int[] maxWilderness,
			int[] actionClasses, Transport[] transports)
		{
			this.local = local;
			this.count = destinations.length;
			this.origins = origins;
			this.destinations = destinations;
			this.costs = costs;
			this.types = types;
			this.maxWilderness = maxWilderness;
			this.actionClasses = actionClasses;
			this.transports = transports;
		}

		static View empty(boolean local)
		{
			return new View(local, new int[0], new int[0], new int[0], new int[0], new int[0], new int[0],
				new Transport[0]);
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

		static View global(Transport[] transports, ToIntFunction<Transport> additionalCost,
			TeleportCapability capability)
		{
			Builder builder = new Builder(false);
			for (Transport transport : transports)
			{
				if (capability.canCast(transport))
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
		private int[] actionClasses = new int[16];
		private Transport[] transports = new Transport[16];
		private final java.util.Map<java.util.List<Object>, Integer> actionClassIds = new java.util.HashMap<>();

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
				actionClasses = Arrays.copyOf(actionClasses, capacity);
				transports = Arrays.copyOf(transports, capacity);
			}
			origins[size] = origin;
			destinations[size] = transport.getDestination();
			costs[size] = ExactCosts.add(transport.getDuration(), additionalCost.applyAsInt(transport));
			types[size] = transport.getType().ordinal();
			maxWilderness[size] = transport.getMaxWildernessLevel();
			actionClasses[size] = actionClass(transport, costs[size]);
			transports[size] = transport;
			size++;
		}

		private int actionClass(Transport transport, int cost)
		{
			// An action without an object description is never grouped: a class of its own, below zero.
			if (!local || transport.getObjectInfo() == null) return -1 - size;
			java.util.List<Object> key = java.util.Arrays.asList(transport.getType(), transport.getDestination(), cost,
				transport.getMaxWildernessLevel(), transport.getObjectInfo(), transport.getDisplayInfo(),
				transport.isConsumable(), transport.getItemRequirements());
			return actionClassIds.computeIfAbsent(key, k -> actionClassIds.size());
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
			int[] sortedActionClasses = new int[size];
			Transport[] sortedTransports = new Transport[size];
			for (int i = 0; i < size; i++)
			{
				int from = order[i];
				sortedOrigins[i] = origins[from];
				sortedDestinations[i] = destinations[from];
				sortedCosts[i] = costs[from];
				sortedTypes[i] = types[from];
				sortedMaxWilderness[i] = maxWilderness[from];
				sortedActionClasses[i] = actionClasses[from];
				sortedTransports[i] = transports[from];
			}
			return new View(local, sortedOrigins, sortedDestinations, sortedCosts, sortedTypes, sortedMaxWilderness,
				sortedActionClasses, sortedTransports);
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
