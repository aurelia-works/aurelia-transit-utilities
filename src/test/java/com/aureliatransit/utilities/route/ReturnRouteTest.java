package com.aureliatransit.utilities.route;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReturnRouteTest {

	// Three stations along x; each has an eastbound platform (z 0) and a westbound one (z 4). Station 3 also has a far
	// platform of another line (z 40).
	private static final ReturnRoute.Stop A_EAST = new ReturnRoute.Stop(11, 1, 0, 0);
	private static final ReturnRoute.Stop A_WEST = new ReturnRoute.Stop(12, 1, 0, 4);
	private static final ReturnRoute.Stop B_EAST = new ReturnRoute.Stop(21, 2, 100, 0);
	private static final ReturnRoute.Stop B_WEST = new ReturnRoute.Stop(22, 2, 100, 4);
	private static final ReturnRoute.Stop C_EAST = new ReturnRoute.Stop(31, 3, 200, 0);
	private static final ReturnRoute.Stop C_WEST = new ReturnRoute.Stop(32, 3, 200, 4);
	private static final ReturnRoute.Stop C_OTHER_LINE = new ReturnRoute.Stop(33, 3, 200, 40);
	private static final Map<Long, List<ReturnRoute.Stop>> STATIONS = Map.of(
			1L, List.of(A_EAST, A_WEST),
			2L, List.of(B_EAST, B_WEST),
			3L, List.of(C_OTHER_LINE, C_WEST, C_EAST)
	);

	private static List<Long> picks(List<ReturnRoute.Choice> choices) {
		return choices.stream().map(ReturnRoute.Choice::platformId).toList();
	}

	@Test
	void reversesAndMovesMiddleStopsToTheOppositeTrack() {
		final List<ReturnRoute.Choice> plan = ReturnRoute.plan(List.of(A_EAST, B_EAST, C_EAST), STATIONS, false);
		assertEquals(List.of(31L, 22L, 11L), picks(plan), "ends keep their platform, the middle stop moves");
		assertFalse(plan.get(0).moved());
		assertTrue(plan.get(1).moved());
	}

	@Test
	void circularRoutesMoveEveryStop() {
		assertEquals(List.of(32L, 22L, 12L), picks(ReturnRoute.plan(List.of(A_EAST, B_EAST, C_EAST), STATIONS, true)));
	}

	@Test
	void nearestOtherPlatformComesFirstAndCycleWraps() {
		final ReturnRoute.Choice choice = ReturnRoute.plan(List.of(A_EAST, C_EAST, B_EAST), STATIONS, false).get(1);
		assertEquals(List.of(31L, 32L, 33L), choice.options(), "own platform, then nearest, then the far line");
		assertEquals(32L, choice.platformId());
		assertEquals(33L, choice.cycle().platformId());
		assertEquals(31L, choice.cycle().cycle().platformId());
		assertEquals(32L, choice.cycle().cycle().cycle().platformId());
	}

	@Test
	void singlePlatformAndStationlessStopsStay() {
		final ReturnRoute.Stop lonely = new ReturnRoute.Stop(41, 4, 300, 0);
		final ReturnRoute.Stop nowhere = new ReturnRoute.Stop(51, 0, 400, 0);
		final List<ReturnRoute.Choice> plan = ReturnRoute.plan(List.of(A_EAST, lonely, nowhere, C_EAST), Map.of(4L, List.of(lonely)), false);
		assertEquals(List.of(31L, 51L, 41L, 11L), picks(plan));
		assertSame(plan.get(1), plan.get(1).cycle());
	}

	@Test
	void emptyRouteGivesEmptyPlan() {
		assertTrue(ReturnRoute.plan(List.of(), STATIONS, false).isEmpty());
	}
}
