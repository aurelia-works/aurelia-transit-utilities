package com.aureliatransit.utilities.perf;

import java.util.Set;

/**
 * Which MTR versions the performance mixins were checked against. On any other version they are not applied at all,
 * so an MTR update can never crash the game through ATU: MTR simply runs as it is.
 */
public final class PerfCompat {

	/** MTR versions whose renderer classes were read and matched against the mixin targets. */
	private static final Set<String> CHECKED = Set.of("4.0.5");

	private PerfCompat() {
	}

	/** {@code version} as Fabric reports it, e.g. "4.0.5+1.20.1" or "FABRIC-4.0.5+1.20.1". */
	public static boolean isChecked(String version) {
		return version != null && CHECKED.contains(baseVersion(version));
	}

	static String baseVersion(String version) {
		String result = version.trim();
		final int dash = result.lastIndexOf('-');
		if (result.regionMatches(true, 0, "fabric-", 0, 7) || result.regionMatches(true, 0, "forge-", 0, 6)) {
			result = result.substring(dash + 1);
		}
		final int plus = result.indexOf('+');
		return plus < 0 ? result : result.substring(0, plus);
	}
}
