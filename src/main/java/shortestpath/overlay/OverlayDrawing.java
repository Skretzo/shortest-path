package shortestpath.overlay;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

import shortestpath.PrimitiveIntHashMap;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.transport.Transport;

/**
 * The shared display-transport walk: iterate the all-display map in
 * occupied-slot order, skip the undefined-origin teleport bucket, resolve
 * the origin's available array (empty when absent), and yield each
 * non-null, non-teleport transport together with its identity availability.
 * <p>
 * The {@code Arrays.asList(availableAtOrigin).contains(b)} read is identity
 * matching — {@link Transport} has no {@code equals} — so the arrays the
 * plugin returned pass through untouched; copying or canonicalizing them
 * would silently break the availability colours. Iterating {@code keys()} on
 * the same map instance the inner {@code getOrDefault} reads preserves the
 * occupied-slot order both arms relied on.
 * <p>
 * Per-overlay origin gates, destination gates, glyph accumulation, and
 * drawing all stay in the {@link TransportVisitor}: the walker owns only the
 * uniform skeleton. {@code beginOrigin} runs once per origin — it is the
 * gate and the slot where a caller computes per-origin values (the scene
 * arm's tile centre, the map arm's projected origin) without projecting
 * twice. {@code endOrigin} runs once per visited origin even when nothing
 * was yielded — the scene arm's explicit colour reset and label draw are
 * unconditional in the original.
 * <p>
 * This package is a leaf: nothing here may reference the plugin type.
 */
final class OverlayDrawing
{
	private OverlayDrawing()
	{
	}

	interface TransportVisitor
	{
		/**
		 * The origin gate plus per-origin preamble, called exactly once per
		 * origin before its transports are walked. Return {@code false} to
		 * skip the origin entirely.
		 */
		boolean beginOrigin(int origin);

		/** Called once per yielded transport. */
		void visit(int origin, Transport transport, boolean available);

		/**
		 * Called once per visited origin after its transports — including
		 * origins that yielded nothing.
		 */
		default void endOrigin(int origin)
		{
		}
	}

	static void forEachDisplayTransport(PrimitiveIntHashMap<Transport[]> allTransports,
		PrimitiveIntHashMap<Transport[]> availableTransports, TransportVisitor visitor)
	{
		for (int a : allTransports.keys())
		{
			// skip the teleport bucket; then the per-overlay origin gate
			if (a == Transport.UNDEFINED_ORIGIN || !visitor.beginOrigin(a))
			{
				continue;
			}

			Transport[] availableAtOrigin = availableTransports.getOrDefault(a, TransportAvailability.EMPTY_TRANSPORTS);

			for (Transport b : allTransports.getOrDefault(a, TransportAvailability.EMPTY_TRANSPORTS))
			{
				if (b == null || (b.getType() != null && b.getType().isTeleport()))
				{
					continue; // skip teleports
				}

				visitor.visit(a, b, Arrays.asList(availableAtOrigin).contains(b));
			}

			visitor.endOrigin(a);
		}
	}

	/**
	 * The shared unreached-target pass every path overlay runs after its route
	 * drawing: yield each requested target the returned path does not already end
	 * on, so the caller can mark it (calculating colour, tooltip, tile). The
	 * {@code !path.isEmpty()} guard is part of the contract — no markers render
	 * before a route exists.
	 */
	static void forEachUnreachedTarget(ActiveSearch search, List<PathStep> path, IntConsumer draw)
	{
		for (int target : search.getTargets().points())
		{
			if (!path.isEmpty() && target != path.get(path.size() - 1).getPackedPosition())
			{
				draw.accept(target);
			}
		}
	}

	/**
	 * The shared {@code " (Exit: …)"} suffix both overlays append to a
	 * transport label when the transport leads into a POH — the tile
	 * overlay's per-transport hint and the world-map tooltip row. Returns
	 * {@code null} when there is no exit info to append, so each caller
	 * keeps its own append target and its own argument order.
	 */
	static String pohExitSuffix(String pohExitInfo)
	{
		return pohExitInfo == null ? null : " (Exit: " + pohExitInfo + ")";
	}
}
