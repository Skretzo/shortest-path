package shortestpath.widgets;

import net.runelite.api.Client;
import net.runelite.client.game.SpriteManager;

/**
 * Builds {@link GameWidgets} and {@link WidgetGeometry} instances detached
 * from Guice for tests and harnesses. The production instances are the
 * injected singletons.
 */
public final class WidgetTestServices
{
	private WidgetTestServices()
	{
	}

	/**
	 * A detached {@link GameWidgets}. Without a client it collapses to the
	 * empty arms (an empty list, {@code false}) and never throws.
	 */
	public static GameWidgets gameWidgets()
	{
		return new GameWidgets(null);
	}

	/**
	 * A detached {@link WidgetGeometry}. Without a client it collapses to the
	 * no-op arms (null clip areas and the {@code Integer.MIN_VALUE} and
	 * {@link shortestpath.WorldPointUtil#UNDEFINED} sentinels) and never throws.
	 */
	public static WidgetGeometry geometry()
	{
		return new WidgetGeometry(null, null);
	}

	/**
	 * A {@link WidgetGeometry} for callers that exercise the widget-backed
	 * reads: the handed client and sprite manager drive the minimap and
	 * world-map lookups while the instance stays detached from Guice.
	 */
	public static WidgetGeometry geometry(Client client, SpriteManager spriteManager)
	{
		return new WidgetGeometry(client, spriteManager);
	}
}
