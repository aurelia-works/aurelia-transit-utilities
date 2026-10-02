package com.aureliatransit.utilities.depot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DepotHealthTest {

	private static DepotHealth.DepotInfo depot(String name, DepotHealth.Status status, long start, long end, long failedSidings, int routes, int sidings, int empty, List<String> shortRoutes, List<String> deleted) {
		return new DepotHealth.DepotInfo(name.hashCode(), name, status, start, end, failedSidings, routes, sidings, empty, shortRoutes, deleted);
	}

	private static DepotHealth.DepotInfo healthy(String name) {
		return depot(name, DepotHealth.Status.SUCCESSFUL, 0, 0, 0, 1, 2, 0, List.of(), List.of());
	}

	private static List<String> keys(DepotHealth.DepotInfo depot) {
		return DepotHealth.check(depot).stream().map(DepotHealth.Problem::key).toList();
	}

	@Test
	void healthyDepotHasNoProblems() {
		assertTrue(DepotHealth.check(healthy("A")).isEmpty());
	}

	@Test
	void pathNotFoundNamesThePlatforms() {
		final List<DepotHealth.Problem> problems = DepotHealth.check(depot("A", DepotHealth.Status.PATH_NOT_FOUND, 11, 22, 0, 1, 1, 0, List.of(), List.of()));
		assertEquals("path_between", problems.get(0).key());
		assertEquals(List.of(11L, 22L), problems.get(0).args());
		assertEquals(List.of("path_not_found"), keys(depot("A", DepotHealth.Status.PATH_NOT_FOUND, 0, 0, 0, 1, 1, 0, List.of(), List.of())));
	}

	@Test
	void setupMistakesAreFound() {
		final List<String> keys = keys(depot("A", DepotHealth.Status.SUCCESSFUL, 0, 0, 2, 0, 3, 1, List.of("R1"), List.of("R2")));
		assertEquals(List.of("sidings_failed", "no_routes", "sidings_without_train", "route_too_short", "route_deleted_platform"), keys);
	}

	@Test
	void noSidingsIsReportedOnce() {
		assertEquals(List.of("no_sidings"), keys(depot("A", DepotHealth.Status.NO_SIDINGS, 0, 0, 0, 1, 0, 0, List.of(), List.of())));
		assertEquals(List.of("no_sidings"), keys(depot("A", DepotHealth.Status.SUCCESSFUL, 0, 0, 0, 1, 0, 0, List.of(), List.of())));
	}

	@Test
	void neverGeneratedAndUnknownAreWarnings() {
		assertEquals(1, DepotHealth.rank(DepotHealth.check(depot("A", DepotHealth.Status.NONE, 0, 0, 0, 1, 1, 0, List.of(), List.of()))));
		assertEquals(DepotHealth.Status.UNKNOWN, DepotHealth.status("SOMETHING_NEW"));
		assertEquals(DepotHealth.Status.UNKNOWN, DepotHealth.status(null));
		assertEquals(DepotHealth.Status.PATH_NOT_FOUND, DepotHealth.status("PATH_NOT_FOUND"));
	}

	@Test
	void worstFirstOrdering() {
		final List<DepotHealth.DepotInfo> depots = new ArrayList<>(List.of(
				healthy("b healthy"),
				depot("z warning", DepotHealth.Status.NONE, 0, 0, 0, 1, 1, 0, List.of(), List.of()),
				healthy("a healthy"),
				depot("y error", DepotHealth.Status.PATH_NOT_FOUND, 1, 2, 0, 1, 1, 0, List.of(), List.of())
		));
		depots.sort(DepotHealth.worstFirst());
		assertEquals(List.of("y error", "z warning", "a healthy", "b healthy"), depots.stream().map(DepotHealth.DepotInfo::name).toList());
	}
}
