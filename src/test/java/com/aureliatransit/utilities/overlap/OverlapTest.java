package com.aureliatransit.utilities.overlap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OverlapTest {

	private static Zones.Station station(long id, long x1, long z1, long x2, long z2) {
		return new Zones.Station(id, Box.ofCorners(x1, -64, z1, x2, 319, z2));
	}

	private static Zones.Platform platform(long id, long x, long y, long z) {
		return new Zones.Platform(id, new Point(x, y, z));
	}

	@Test
	void midpointRoundsTowardZeroLikeMtr() {
		assertEquals(new Point(-1, 0, 2), Point.midpoint(-3, 0, 2, 0, 1, 3));
		assertEquals(new Point(1, 0, -2), Point.midpoint(0, 0, -5, 3, 0, 1));
	}

	@Test
	void boxIsInclusiveOnAllAxes() {
		final Box box = Box.ofCorners(10, 0, 10, 0, 5, 0);
		assertTrue(box.contains(new Point(0, 0, 0)));
		assertTrue(box.contains(new Point(10, 5, 10)));
		assertFalse(box.contains(new Point(11, 5, 10)));
		assertFalse(box.contains(new Point(5, 6, 5)));
	}

	@Test
	void firstStationInMtrOrderWins() {
		final Zones zones = new Zones(List.of(station(1, 0, 0, 20, 20), station(2, 10, 0, 30, 20)), List.of(platform(100, 15, 0, 10), platform(101, 25, 0, 10)));
		assertEquals(Map.of(100L, 1L, 101L, 2L), zones.assignment());
		final List<Zones.Conflict> conflicts = zones.conflicts();
		assertEquals(1, conflicts.size());
		assertEquals(List.of(1L, 2L), conflicts.get(0).stationIds());
		assertEquals(1L, conflicts.get(0).currentStationId());
	}

	@Test
	void planCutsTheOtherZoneAndMovesNothingElse() {
		// Station 1 spans x 0-20, station 2 spans x 10-30; platform 100 at x=15 should belong to station 2.
		final Zones zones = new Zones(
				List.of(station(1, 0, 0, 20, 20), station(2, 10, 0, 30, 20)),
				List.of(platform(100, 15, 0, 10), platform(101, 5, 0, 10), platform(102, 25, 0, 10))
		);
		final OverlapResolver.Plan plan = OverlapResolver.plan(zones, zones.conflicts().get(0), 2);
		assertNotNull(plan);
		assertTrue(plan.reachesGoal());
		assertTrue(plan.sideEffects().isEmpty());
		assertEquals(1, plan.cuts().size());
		final OverlapResolver.Cut cut = plan.cuts().get(0);
		assertEquals(1, cut.stationId());
		// Cutting station 1's east face back to x=14 keeps platform 101 and loses the least.
		assertEquals(Box.Side.EAST, cut.side());
		assertEquals(14, cut.after().maxX());
	}

	@Test
	void keepingTheCurrentStationCutsTheLaterZone() {
		final Zones zones = new Zones(List.of(station(1, 0, 0, 20, 20), station(2, 10, 0, 30, 20)), List.of(platform(100, 15, 0, 10)));
		final OverlapResolver.Plan plan = OverlapResolver.plan(zones, zones.conflicts().get(0), 1);
		assertTrue(plan.reachesGoal());
		assertEquals(2, plan.cuts().get(0).stationId());
		assertEquals(Box.Side.WEST, plan.cuts().get(0).side());
		assertEquals(16, plan.cuts().get(0).after().minX());
	}

	@Test
	void stackedStationsAreSeparatedVertically() {
		// Overground station 1 covers the whole height; subway station 2 is a box underneath. Both platforms share x/z.
		final Zones zones = new Zones(
				List.of(station(1, 0, 0, 20, 20), new Zones.Station(2, Box.ofCorners(0, -40, 0, 20, -20, 20))),
				List.of(platform(100, 10, 70, 10), platform(200, 10, -30, 10))
		);
		final Zones.Conflict conflict = zones.conflicts().get(0);
		assertEquals(200, conflict.platformId());
		final OverlapResolver.Plan plan = OverlapResolver.plan(zones, conflict, 2);
		assertTrue(plan.reachesGoal());
		assertTrue(plan.sideEffects().isEmpty(), "the overground platform must stay put");
		assertEquals(Box.Side.BELOW, plan.cuts().get(0).side());
		assertEquals(-29, plan.cuts().get(0).after().minY());
	}

	@Test
	void sideEffectsAreReportedWhenUnavoidable() {
		// Platforms 100 and 101 sit in the same block column inside both zones: giving 100 to station 2 drags 101 along.
		final Zones zones = new Zones(
				List.of(station(1, 0, 0, 20, 20), station(2, 10, 0, 30, 20)),
				List.of(platform(100, 15, 0, 10), platform(101, 15, 0, 10))
		);
		final Zones.Conflict conflict = zones.conflicts().get(0);
		final OverlapResolver.Plan plan = OverlapResolver.plan(zones, conflict, 2);
		assertTrue(plan.reachesGoal());
		assertEquals(1, plan.sideEffects().size());
		assertArrayEquals(new Long[]{1L, 2L}, plan.sideEffects().get(101L));
	}

	@Test
	void threeWayOverlapCutsBothOthers() {
		final Zones zones = new Zones(
				List.of(station(1, 0, 0, 20, 20), station(2, 10, 0, 30, 20), station(3, 12, 0, 18, 20)),
				List.of(platform(100, 15, 0, 10))
		);
		final OverlapResolver.Plan plan = OverlapResolver.plan(zones, zones.conflicts().get(0), 3);
		assertTrue(plan.reachesGoal());
		assertEquals(List.of(1L, 2L), plan.cuts().stream().map(OverlapResolver.Cut::stationId).toList());
	}

	@Test
	void singleBlockZoneCannotBeCut() {
		final Zones zones = new Zones(
				List.of(new Zones.Station(1, Box.ofCorners(5, 0, 5, 5, 0, 5)), station(2, 0, 0, 10, 10)),
				List.of(platform(100, 5, 0, 5))
		);
		assertNull(OverlapResolver.plan(zones, zones.conflicts().get(0), 2));
	}

	@Test
	void targetMustContainThePlatform() {
		final Zones zones = new Zones(List.of(station(1, 0, 0, 20, 20), station(2, 10, 0, 30, 20), station(3, 100, 0, 110, 10)), List.of(platform(100, 15, 0, 10)));
		assertThrows(IllegalArgumentException.class, () -> OverlapResolver.plan(zones, zones.conflicts().get(0), 3));
	}

	@Test
	void unboundedHeightZonesLikeMtrDrawsThem() {
		// MTR's dashboard draws zones from Long.MIN_VALUE to Long.MAX_VALUE in Y.
		final Box unbounded = new Box(0, Long.MIN_VALUE, 0, 20, Long.MAX_VALUE, 20);
		assertTrue(unbounded.volume() > 1e20, "volume must not overflow");
		assertTrue(unbounded.contains(new Point(5, -2000, 5)));
		final Zones zones = new Zones(
				List.of(new Zones.Station(1, unbounded), new Zones.Station(2, new Box(10, Long.MIN_VALUE, 0, 30, Long.MAX_VALUE, 20))),
				List.of(platform(100, 15, 0, 10), platform(101, 5, 0, 10))
		);
		final OverlapResolver.Plan plan = OverlapResolver.plan(zones, zones.conflicts().get(0), 2);
		assertTrue(plan.reachesGoal());
		assertTrue(plan.sideEffects().isEmpty());
		assertEquals(Box.Side.EAST, plan.cuts().get(0).side());
	}
}
