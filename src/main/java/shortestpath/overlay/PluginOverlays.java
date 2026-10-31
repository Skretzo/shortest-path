package shortestpath.overlay;

import net.runelite.client.ui.overlay.OverlayManager;

/**
 * The plugin's overlay set as one composite: the shell keeps the nine
 * overlay references injected but delegates every lifecycle call here, so
 * the add/remove/debug-toggle idioms each have a single owner. Register
 * order is render order, so the field order below mirrors the order the
 * plugin used to add them in.
 *
 * <p>This package is a leaf: nothing here may reference the plugin type.
 */
public final class PluginOverlays
{
	private final PathTileOverlay pathOverlay;
	private final PathMinimapOverlay pathMinimapOverlay;
	private final PathMapOverlay pathMapOverlay;
	private final PathMapTooltipOverlay pathMapTooltipOverlay;
	private final BankItemHighlightOverlay bankItemHighlightOverlay;
	private final SpellbookHighlightOverlay spellbookHighlightOverlay;
	private final InventoryHighlightOverlay inventoryHighlightOverlay;
	private final ScrollBookHighlightOverlay scrollBookHighlightOverlay;
	private final DebugOverlayPanel debugOverlayPanel;

	public PluginOverlays(
		PathTileOverlay pathOverlay,
		PathMinimapOverlay pathMinimapOverlay,
		PathMapOverlay pathMapOverlay,
		PathMapTooltipOverlay pathMapTooltipOverlay,
		BankItemHighlightOverlay bankItemHighlightOverlay,
		SpellbookHighlightOverlay spellbookHighlightOverlay,
		InventoryHighlightOverlay inventoryHighlightOverlay,
		ScrollBookHighlightOverlay scrollBookHighlightOverlay,
		DebugOverlayPanel debugOverlayPanel)
	{
		this.pathOverlay = pathOverlay;
		this.pathMinimapOverlay = pathMinimapOverlay;
		this.pathMapOverlay = pathMapOverlay;
		this.pathMapTooltipOverlay = pathMapTooltipOverlay;
		this.bankItemHighlightOverlay = bankItemHighlightOverlay;
		this.spellbookHighlightOverlay = spellbookHighlightOverlay;
		this.inventoryHighlightOverlay = inventoryHighlightOverlay;
		this.scrollBookHighlightOverlay = scrollBookHighlightOverlay;
		this.debugOverlayPanel = debugOverlayPanel;
	}

	/**
	 * Adds every overlay in declaration order — register order is render
	 * order — with the debug panel conditional on the visibility flag the
	 * caller resolved from settings.
	 */
	public void register(OverlayManager overlayManager, boolean drawDebug)
	{
		overlayManager.add(pathOverlay);
		overlayManager.add(pathMinimapOverlay);
		overlayManager.add(pathMapOverlay);
		overlayManager.add(pathMapTooltipOverlay);
		overlayManager.add(bankItemHighlightOverlay);
		overlayManager.add(spellbookHighlightOverlay);
		overlayManager.add(inventoryHighlightOverlay);
		overlayManager.add(scrollBookHighlightOverlay);
		if (drawDebug)
		{
			overlayManager.add(debugOverlayPanel);
		}
	}

	/**
	 * Removes all nine overlays unconditionally, matching the plugin's
	 * shut-down semantics — a never-registered debug panel removes as a
	 * no-op.
	 */
	public void unregister(OverlayManager overlayManager)
	{
		overlayManager.remove(pathOverlay);
		overlayManager.remove(pathMinimapOverlay);
		overlayManager.remove(pathMapOverlay);
		overlayManager.remove(pathMapTooltipOverlay);
		overlayManager.remove(bankItemHighlightOverlay);
		overlayManager.remove(spellbookHighlightOverlay);
		overlayManager.remove(inventoryHighlightOverlay);
		overlayManager.remove(scrollBookHighlightOverlay);
		overlayManager.remove(debugOverlayPanel);
	}

	/**
	 * The debug-panel toggle idiom: add iff {@code drawDebug}, else remove.
	 * The single owner of the config-change and profile-change paths.
	 */
	public void syncDebug(OverlayManager overlayManager, boolean drawDebug)
	{
		if (drawDebug)
		{
			overlayManager.add(debugOverlayPanel);
		}
		else
		{
			overlayManager.remove(debugOverlayPanel);
		}
	}
}
