package com.aureliatransit.utilities.overlap;

public record Point(long x, long y, long z) {

	/** MTR's {@code SavedRailBase.getMidPosition}: (p1 + p2) / 2 with Java long division (rounds toward zero). */
	public static Point midpoint(long x1, long y1, long z1, long x2, long y2, long z2) {
		return new Point((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2);
	}
}
