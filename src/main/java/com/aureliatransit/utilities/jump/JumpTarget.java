package com.aureliatransit.utilities.jump;

import com.aureliatransit.utilities.overlap.Point;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Where "jump to" puts the player, and the /tp command for it. */
public final class JumpTarget {

	private JumpTarget() {
	}

	public enum Kind {STATION, PLATFORM, DEPOT, SIDING}

	/**
	 * A platform or siding: one block above its midpoint (standing on the track). A station or depot: the first of its
	 * platforms or sidings, because its zone centre has no known height (MTR draws zones with unlimited Y).
	 */
	public static Optional<Point> standOn(List<Point> railMidpoints) {
		return railMidpoints.isEmpty() ? Optional.empty() : Optional.of(new Point(railMidpoints.get(0).x(), railMidpoints.get(0).y() + 1, railMidpoints.get(0).z()));
	}

	public static String command(Point point) {
		return "tp @s " + point.x() + " " + point.y() + " " + point.z();
	}

	/** Zone without rails: its X/Z centre, keeping the player's height ("~"). */
	public static String commandKeepingHeight(long x, long z) {
		return "tp @s " + x + " ~ " + z;
	}

	public static boolean matches(String label, String query) {
		final String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
		return q.isEmpty() || label.toLowerCase(Locale.ROOT).contains(q);
	}
}
