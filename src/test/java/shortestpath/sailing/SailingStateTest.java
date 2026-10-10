package shortestpath.sailing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import shortestpath.WorldPointUtil;

/**
 * The {@link SailingState} snapshot is the single read seam every sailing
 * consumer shares, so its value semantics are the contract: {@code EMPTY}'s
 * documented defaults for the never-refreshed case, and a material-equality
 * check that ignores positional fields so ordinary boat movement can never
 * mint a route-restarting change fact.
 */
public class SailingStateTest
{
	private static SailingState state(boolean aboard, int baseSpeed, int speedCap, int acceleration,
		boolean hasBoat, boolean hasBounds,
		int boundsX, int boundsY, int boundsWidth, int boundsHeight,
		int hullConfigId, int boatPoint, int pivotX, int pivotY, int heading)
	{
		return new SailingState(aboard, baseSpeed, speedCap, acceleration,
			hasBoat, hasBounds, boundsX, boundsY, boundsWidth, boundsHeight,
			hullConfigId, boatPoint, pivotX, pivotY, heading);
	}

	private static SailingState material()
	{
		return state(true, 192, 384, 96, true, true, 1, 2, 3, 2, 42,
			WorldPointUtil.packWorldPoint(3010, 3120, 0), 16, -24, 7);
	}

	@Test
	public void emptyCarriesTheDocumentedDefaults()
	{
		SailingState empty = SailingState.EMPTY;
		assertFalse(empty.isAboard());
		assertEquals(0, empty.getBaseSpeed());
		assertEquals(0, empty.getSpeedCap());
		assertEquals(0, empty.getAcceleration());
		assertFalse(empty.hasBoat());
		assertFalse(empty.hasBounds());
		assertEquals(0, empty.getBoundsX());
		assertEquals(0, empty.getBoundsY());
		assertEquals(0, empty.getBoundsWidth());
		assertEquals(0, empty.getBoundsHeight());
		assertEquals(-1, empty.getHullConfigId());
		assertEquals(WorldPointUtil.UNDEFINED, empty.getBoatPoint());
		assertEquals(0, empty.getPivotX());
		assertEquals(0, empty.getPivotY());
		assertEquals(-1, empty.getHeading());
	}

	@Test
	public void constructionRoundTripsEveryField()
	{
		int boatPoint = WorldPointUtil.packWorldPoint(3010, 3120, 0);
		SailingState state = state(true, 192, 384, 96, true, true, 1, 2, 3, 2, 42,
			boatPoint, 16, -24, 7);

		assertTrue(state.isAboard());
		assertEquals(192, state.getBaseSpeed());
		assertEquals(384, state.getSpeedCap());
		assertEquals(96, state.getAcceleration());
		assertTrue(state.hasBoat());
		assertTrue(state.hasBounds());
		assertEquals(1, state.getBoundsX());
		assertEquals(2, state.getBoundsY());
		assertEquals(3, state.getBoundsWidth());
		assertEquals(2, state.getBoundsHeight());
		assertEquals(42, state.getHullConfigId());
		assertEquals(boatPoint, state.getBoatPoint());
		assertEquals(16, state.getPivotX());
		assertEquals(-24, state.getPivotY());
		assertEquals(7, state.getHeading());
	}

	@Test
	public void materiallyEqualsIgnoresPositionalFields()
	{
		SailingState a = material();
		SailingState moved = state(true, 192, 384, 96, true, true, 1, 2, 3, 2, 42,
			WorldPointUtil.packWorldPoint(3011, 3121, 0), 0, 5, 11);

		assertTrue("identical snapshots are materially equal", a.materiallyEquals(material()));
		assertTrue("boat movement never counts as a material change", a.materiallyEquals(moved));
		assertTrue(moved.materiallyEquals(a));
	}

	@Test
	public void materiallyEqualsFlagsAboardAndBoatChanges()
	{
		SailingState a = material();

		assertFalse(a.materiallyEquals(state(false, 192, 384, 96, true, true, 1, 2, 3, 2, 42,
			a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
		assertFalse("losing the boat entity is material",
			a.materiallyEquals(state(true, 192, 384, 96, false, true, 1, 2, 3, 2, 42,
				a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
		assertFalse("losing the bounds is material",
			a.materiallyEquals(state(true, 192, 384, 96, true, false, 1, 2, 3, 2, 42,
				a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
	}

	@Test
	public void materiallyEqualsFlagsBoundsChanges()
	{
		SailingState a = material();

		assertFalse(a.materiallyEquals(state(true, 192, 384, 96, true, true, 2, 2, 3, 2, 42,
			a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
		assertFalse(a.materiallyEquals(state(true, 192, 384, 96, true, true, 1, 3, 3, 2, 42,
			a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
		assertFalse(a.materiallyEquals(state(true, 192, 384, 96, true, true, 1, 2, 4, 2, 42,
			a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
		assertFalse(a.materiallyEquals(state(true, 192, 384, 96, true, true, 1, 2, 3, 3, 42,
			a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
	}

	@Test
	public void materiallyEqualsFlagsBaseSpeedChanges()
	{
		SailingState a = material();

		assertFalse("the route sails at the base speed, so a change there is material",
			a.materiallyEquals(state(true, 128, 384, 96, true, true, 1, 2, 3, 2, 42,
				a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
	}

	@Test
	public void materiallyEqualsIgnoresUnconsumedFields()
	{
		SailingState a = material();

		// Captured for snapshot fidelity but read by no routing consumer —
		// a change on any of them must not mint a route-restarting fact.
		assertTrue("speed cap is not a routing input",
			a.materiallyEquals(state(true, 192, 256, 96, true, true, 1, 2, 3, 2, 42,
				a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
		assertTrue("acceleration is not a routing input",
			a.materiallyEquals(state(true, 192, 384, 64, true, true, 1, 2, 3, 2, 42,
				a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
		assertTrue("the hull config id is not a routing input",
			a.materiallyEquals(state(true, 192, 384, 96, true, true, 1, 2, 3, 2, 43,
				a.getBoatPoint(), a.getPivotX(), a.getPivotY(), a.getHeading())));
		assertTrue("heading is not a routing input",
			a.materiallyEquals(state(true, 192, 384, 96, true, true, 1, 2, 3, 2, 42,
				a.getBoatPoint(), a.getPivotX(), a.getPivotY(), 11)));
	}

	@Test
	public void materiallyEqualsRejectsNull()
	{
		assertFalse(material().materiallyEquals(null));
	}
}
