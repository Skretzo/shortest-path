package shortestpath.widgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.game.SpriteManager;

import shortestpath.WorldPointUtil;

/**
 * Pins the load-bearing idioms the geometry move carries: the self-assigning
 * {@code minimapRectangle} bounds check that clears all four minimap caches
 * on any bounds change, the mask-sprite fallback chain, the
 * {@code menuOpened(Point)} single-owner write seam feeding
 * {@code getSelectedWorldPoint}, and the detached-instance sentinels — a
 * {@code forTesting()} geometry answers null/{@code Integer.MIN_VALUE}/
 * {@link WorldPointUtil#UNDEFINED} and never throws.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class WidgetGeometryTest
{
	@Mock
	Client client;

	@Mock
	SpriteManager spriteManager;

	private WidgetGeometry geometry;

	@Before
	public void setUp()
	{
		geometry = new WidgetGeometry(client, spriteManager);
	}

	/**
	 * A mask with an opaque centre on a transparent field — the edge
	 * tracer walks the boundary between the (0,0) outside colour and the
	 * filled pixels, producing a non-empty polygon.
	 */
	private static BufferedImage maskImage()
	{
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int y = 2; y < 6; y++)
		{
			for (int x = 2; x < 6; x++)
			{
				image.setRGB(x, y, 0xFFFFFFFF);
			}
		}
		return image;
	}

	private Widget stubFixedMinimap(Rectangle bounds)
	{
		Widget minimap = mock(Widget.class);
		when(client.isResized()).thenReturn(false);
		when(client.getWidget(InterfaceID.Toplevel.MINIMAP)).thenReturn(minimap);
		when(minimap.isHidden()).thenReturn(false);
		when(minimap.getBounds()).thenReturn(bounds);
		return minimap;
	}

	private Widget stubResizeableMinimap(Rectangle bounds)
	{
		Widget minimap = mock(Widget.class);
		when(client.isResized()).thenReturn(true);
		when(client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT)).thenReturn(0);
		when(client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP)).thenReturn(minimap);
		when(minimap.isHidden()).thenReturn(false);
		when(minimap.getBounds()).thenReturn(bounds);
		return minimap;
	}

	@Test
	public void detachedInstanceCollapsesToSentinels()
	{
		WidgetGeometry detached = WidgetGeometry.forTesting();

		assertNull(detached.getMinimapClipArea());
		assertEquals(Integer.MIN_VALUE, detached.mapWorldPointToGraphicsPointX(0));
		assertEquals(Integer.MIN_VALUE, detached.mapWorldPointToGraphicsPointY(0));
		assertEquals(WorldPointUtil.UNDEFINED, detached.calculateMapPoint(10, 20));
		assertEquals(WorldPointUtil.UNDEFINED, detached.getSelectedWorldPoint());

		// The write seam records without a client and still never throws.
		detached.menuOpened(new Point(4, 4));
		assertEquals(WorldPointUtil.UNDEFINED, detached.getSelectedWorldPoint());
	}

	@Test
	public void clientOnlyInstanceAnswersMapReads()
	{
		// The client-only seam covers the world-map conversions — the
		// sprite-free surface — without wiring a sprite manager.
		WidgetGeometry clientOnly = new WidgetGeometry(client);
		Widget map = mock(Widget.class);
		WorldMap worldMap = mock(WorldMap.class);
		when(client.getWorldMap()).thenReturn(worldMap);
		when(worldMap.getWorldMapZoom()).thenReturn(8f);
		when(worldMap.getWorldMapPosition()).thenReturn(new Point(3200, 3200));
		when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(map);
		when(map.getBounds()).thenReturn(new Rectangle(0, 0, 512, 352));

		int packed = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		assertEquals(260, clientOnly.mapWorldPointToGraphicsPointX(packed));
		assertEquals(172, clientOnly.mapWorldPointToGraphicsPointY(packed));
	}

	@Test
	public void minimapClipCachesWhileBoundsHold()
	{
		stubFixedMinimap(new Rectangle(12, 34, 200, 150));
		when(spriteManager.getSprite(SpriteID.FIXED_MAP_MASK, 0)).thenReturn(maskImage());

		Shape first = geometry.getMinimapClipArea();

		assertNotNull(first);
		assertSame(first, geometry.getMinimapClipArea());
		verify(spriteManager, times(1)).getSprite(SpriteID.FIXED_MAP_MASK, 0);
	}

	@Test
	public void boundsChangeClearsAllFourMinimapCaches()
	{
		Widget fixedMinimap = stubFixedMinimap(new Rectangle(0, 0, 100, 100));
		Widget resizeableMinimap = stubResizeableMinimap(new Rectangle(0, 0, 300, 200));
		when(spriteManager.getSprite(SpriteID.FIXED_MAP_MASK, 0)).thenReturn(maskImage());
		when(spriteManager.getSprite(SpriteID.RESIZE_MAP_MASK, 0)).thenReturn(maskImage());

		// Fixed mode caches the clip and the mask sprite.
		when(client.isResized()).thenReturn(false);
		Shape fixedClip = geometry.getMinimapClipArea();
		assertSame(fixedClip, geometry.getMinimapClipArea());

		// Switching to the resizeable widget moves the bounds — every
		// cache clears and the resizeable pair repopulates.
		when(client.isResized()).thenReturn(true);
		Shape resizeableClip = geometry.getMinimapClipArea();
		assertNotSame(fixedClip, resizeableClip);
		assertSame(resizeableClip, geometry.getMinimapClipArea());

		// Back to fixed: the resizeable bounds are still recorded, so the
		// fixed widget's bounds invalidate again — fixed clip and sprite
		// both recompute rather than resurrecting the stale entries.
		when(client.isResized()).thenReturn(false);
		Shape fixedClipAgain = geometry.getMinimapClipArea();
		assertNotSame(fixedClip, fixedClipAgain);
		assertSame(fixedClipAgain, geometry.getMinimapClipArea());

		verify(spriteManager, times(2)).getSprite(SpriteID.FIXED_MAP_MASK, 0);
		verify(spriteManager, times(1)).getSprite(SpriteID.RESIZE_MAP_MASK, 0);
		verify(fixedMinimap, times(4)).getBounds();
		verify(resizeableMinimap, times(2)).getBounds();
	}

	@Test
	public void hiddenMinimapYieldsNullClip()
	{
		Widget minimap = stubFixedMinimap(new Rectangle(0, 0, 100, 100));
		when(minimap.isHidden()).thenReturn(true);

		assertNull(geometry.getMinimapClipArea());
	}

	@Test
	public void masklessArmFallsBackToEllipseUncached()
	{
		stubFixedMinimap(new Rectangle(10, 20, 100, 80));
		// Sprite not loaded yet — getSprite answers null and the clip
		// degrades to the elliptical approximation each call.
		when(spriteManager.getSprite(SpriteID.FIXED_MAP_MASK, 0)).thenReturn(null);

		Shape clip = geometry.getMinimapClipArea();

		assertTrue(clip instanceof Ellipse2D.Double);
		assertNotSame(clip, geometry.getMinimapClipArea());
	}

	@Test
	public void menuOpenedPointDrivesSelectionWhileMenuOpen()
	{
		Widget map = mock(Widget.class);
		WorldMap worldMap = mock(WorldMap.class);
		when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(map);
		when(client.getWorldMap()).thenReturn(worldMap);
		when(worldMap.getWorldMapZoom()).thenReturn(8f);
		when(worldMap.getWorldMapPosition()).thenReturn(new Point(3200, 3200));
		when(map.getBounds()).thenReturn(new Rectangle(0, 0, 512, 352));
		// The live mouse sits somewhere else — selection must prefer the
		// recorded menu point while a menu is open.
		when(client.getMouseCanvasPosition()).thenReturn(new Point(276, 188));
		when(client.isMenuOpen()).thenReturn(true);

		geometry.menuOpened(new Point(268, 180));

		assertEquals(WorldPointUtil.packWorldPoint(3201, 3199, 0), geometry.getSelectedWorldPoint());
	}

	@Test
	public void closedMenuFallsBackToMousePosition()
	{
		Widget map = mock(Widget.class);
		WorldMap worldMap = mock(WorldMap.class);
		when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(map);
		when(client.getWorldMap()).thenReturn(worldMap);
		when(worldMap.getWorldMapZoom()).thenReturn(8f);
		when(worldMap.getWorldMapPosition()).thenReturn(new Point(3200, 3200));
		when(map.getBounds()).thenReturn(new Rectangle(0, 0, 512, 352));
		when(client.getMouseCanvasPosition()).thenReturn(new Point(276, 188));
		when(client.isMenuOpen()).thenReturn(false);

		geometry.menuOpened(new Point(268, 180));

		assertEquals(WorldPointUtil.packWorldPoint(3202, 3198, 0), geometry.getSelectedWorldPoint());
	}

	@Test
	public void noWorldMapAndNoSceneTileYieldsUndefined()
	{
		when(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).thenReturn(null);
		WorldView top = mock(WorldView.class);
		when(client.getTopLevelWorldView()).thenReturn(top);
		when(top.getSelectedSceneTile()).thenReturn(null);

		assertEquals(WorldPointUtil.UNDEFINED, geometry.getSelectedWorldPoint());
	}
}
