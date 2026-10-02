package com.aureliatransit.utilities.depot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Why a depot's trains might not run, from data MTR already has: its last path generation result (as MTR's own depot
 * screen shows it) plus setup mistakes that cause those results.
 */
public final class DepotHealth {

	private DepotHealth() {
	}

	/** MTR's {@code Depot.GeneratedStatus}, by name. */
	public enum Status {NONE, SUCCESSFUL, NO_SIDINGS, TWO_PLATFORMS_REQUIRED, PATH_NOT_FOUND, UNKNOWN}

	/** Everything the checker needs about one depot, already read from MTR. */
	public record DepotInfo(
			long id,
			String name,
			Status status,
			long failedStartPlatformId,
			long failedEndPlatformId,
			long failedSidingCount,
			int routeCount,
			int sidingCount,
			int sidingsWithoutTrain,
			List<String> routesWithTooFewStops,
			List<String> routesWithDeletedPlatforms
	) {
	}

	public enum Severity {ERROR, WARNING}

	/** {@code key} is a translation key suffix; {@code args} fill it in. */
	public record Problem(Severity severity, String key, List<Object> args) {
	}

	public static List<Problem> check(DepotInfo depot) {
		final List<Problem> problems = new ArrayList<>();
		switch (depot.status()) {
			case PATH_NOT_FOUND -> {
				if (depot.failedStartPlatformId() != 0 && depot.failedEndPlatformId() != 0) {
					problems.add(new Problem(Severity.ERROR, "path_between", List.of(depot.failedStartPlatformId(), depot.failedEndPlatformId())));
				} else {
					problems.add(new Problem(Severity.ERROR, "path_not_found", List.of()));
				}
			}
			case NO_SIDINGS -> problems.add(new Problem(Severity.ERROR, "no_sidings", List.of()));
			case TWO_PLATFORMS_REQUIRED -> problems.add(new Problem(Severity.ERROR, "two_platforms", List.of()));
			case NONE -> problems.add(new Problem(Severity.WARNING, "never_generated", List.of()));
			case UNKNOWN -> problems.add(new Problem(Severity.WARNING, "unknown_status", List.of()));
			default -> {
			}
		}
		if (depot.failedSidingCount() > 0) {
			problems.add(new Problem(Severity.ERROR, "sidings_failed", List.of(depot.failedSidingCount())));
		}
		if (depot.routeCount() == 0) {
			problems.add(new Problem(Severity.ERROR, "no_routes", List.of()));
		}
		if (depot.sidingCount() == 0 && depot.status() != Status.NO_SIDINGS) {
			problems.add(new Problem(Severity.ERROR, "no_sidings", List.of()));
		}
		if (depot.sidingsWithoutTrain() > 0) {
			problems.add(new Problem(Severity.WARNING, "sidings_without_train", List.of(depot.sidingsWithoutTrain(), depot.sidingCount())));
		}
		depot.routesWithTooFewStops().forEach(route -> problems.add(new Problem(Severity.ERROR, "route_too_short", List.of(route))));
		depot.routesWithDeletedPlatforms().forEach(route -> problems.add(new Problem(Severity.ERROR, "route_deleted_platform", List.of(route))));
		return problems;
	}

	/** Errors first, then warnings, then healthy; by name inside each group. */
	public static Comparator<DepotInfo> worstFirst() {
		return Comparator.comparingInt((DepotInfo depot) -> rank(check(depot))).thenComparing(depot -> depot.name().toLowerCase(java.util.Locale.ROOT));
	}

	public static int rank(List<Problem> problems) {
		if (problems.stream().anyMatch(problem -> problem.severity() == Severity.ERROR)) {
			return 0;
		}
		return problems.isEmpty() ? 2 : 1;
	}

	public static Status status(String mtrName) {
		try {
			return Status.valueOf(mtrName);
		} catch (IllegalArgumentException | NullPointerException e) {
			return Status.UNKNOWN;
		}
	}
}
