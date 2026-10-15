package com.aureliatransit.utilities.depot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DepotHealthTest {

	private static DepotHealth.DepotInfo depot(String name, DepotHealth.Status status, long start, long end, long failedSidings, int routes, int sidings, int empty, List<String> shortRoutes, List<String> deleted) {
		return new DepotHealth.DepotInfo(name.hashCode(), name, status, start, end, failedSidings, routes, sidings, empty, shortRoutes, deleted, false, List.of(), List.of());
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

	@Test
	void doctorChecks() {
		final DepotHealth.DepotInfo depot = new DepotHealth.DepotInfo(1, "A", DepotHealth.Status.SUCCESSFUL, 0, 0, 0, 1, 2, 0, List.of(), List.of(), true, List.of("Platform 3 (Route 1)"), List.of("Siding 2"));
		final List<DepotHealth.Problem> problems = DepotHealth.check(depot);
		assertEquals(List.of("no_departures", "siding_isolated", "platform_no_station"), problems.stream().map(DepotHealth.Problem::key).toList());
		assertEquals(List.of("Siding 2"), problems.get(1).args());
		assertEquals(DepotHealth.Severity.WARNING, problems.get(2).severity());
		assertEquals(0, DepotHealth.rank(problems));
	}

	@Test
	void noDeparturesIsNotRepeatedForADepotWithoutRoutes() {
		final DepotHealth.DepotInfo depot = new DepotHealth.DepotInfo(1, "A", DepotHealth.Status.SUCCESSFUL, 0, 0, 0, 0, 2, 0, List.of(), List.of(), true, List.of(), List.of());
		assertEquals(List.of("no_routes"), DepotHealth.check(depot).stream().map(DepotHealth.Problem::key).toList());
	}
}
