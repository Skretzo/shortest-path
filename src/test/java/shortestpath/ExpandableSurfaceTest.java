package shortestpath;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.swing.JLabel;

import org.junit.Test;

/**
 * Lock semantics of {@link ExpandableSurface}: a lock (disabled expander)
 * landing on an open surface collapses it rather than leaving it stuck
 * open behind a dead chevron.
 */
public class ExpandableSurfaceTest
{
	private static ExpandableSurface surface()
	{
		ExpandableSurface surface = new ExpandableSurface(null)
		{
			@Override
			public JLabel getNameLabel()
			{
				return new JLabel("test");
			}
		};
		surface.newExpander();
		return surface;
	}

	@Test
	public void searchExpandsAnEnabledSurface()
	{
		ExpandableSurface surface = surface();
		surface.setSearchExpanded();
		assertTrue(surface.isDetailOpen());
		assertTrue(surface.detail.isVisible());
		surface.clearSearchExpanded();
		assertFalse(surface.isDetailOpen());
	}

	/**
	 * Pins the deliberate current behaviour (see
	 * {@code ShortestPathPanelLayoutTest.memberLabelSearchExpandsTheGroup}):
	 * a member-label search reveals the control even inside a master-off
	 * (disabled) group.
	 */
	@Test
	public void searchStillRevealsAMemberInsideALockedSurface()
	{
		ExpandableSurface surface = surface();
		surface.expander.setEnabled(false);
		surface.setSearchExpanded();
		assertTrue(surface.isDetailOpen());
	}

	@Test
	public void lockingAnOpenSurfaceCollapsesIt()
	{
		ExpandableSurface surface = surface();
		surface.setDetailOpen(true);
		assertTrue(surface.isDetailOpen());
		surface.expander.setEnabled(false);
		assertFalse("a lock must not leave the detail stuck open", surface.isDetailOpen());
		assertFalse(surface.detail.isVisible());
		// unlocking does not reopen it; the user owns that
		surface.expander.setEnabled(true);
		assertFalse(surface.isDetailOpen());
	}

	@Test
	public void lockingASearchOpenedSurfaceCollapsesAndDropsSearchOwnership()
	{
		ExpandableSurface surface = surface();
		surface.setSearchExpanded();
		surface.expander.setEnabled(false);
		assertFalse(surface.isDetailOpen());
		surface.expander.setEnabled(true);
		surface.clearSearchExpanded();
		assertFalse(surface.isDetailOpen());
	}
}
