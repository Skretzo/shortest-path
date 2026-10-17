package shortestpath;

import javax.swing.JLabel;

/**
 * A category row component whose detail well search can force open: when
 * a query matches one of the row's detail/member labels rather than its
 * own name, {@code CategoryPanel.applyQuery} calls
 * {@link #setSearchExpanded()} so the matched control is visible, and
 * {@link #clearSearchExpanded()} on an empty query restores the collapsed
 * state (only expansions search itself performed collapse back).
 */
interface SearchExpandable
{
	void setSearchExpanded();

	void clearSearchExpanded();

	/**
	 * The label holding the component's own display name — a query
	 * contained here counts as a name match (no force-expand needed).
	 */
	JLabel getNameLabel();
}
