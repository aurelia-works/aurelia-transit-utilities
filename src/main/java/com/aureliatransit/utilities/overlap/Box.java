package com.aureliatransit.utilities.overlap;

/** A station zone as MTR tests it: inclusive on X, Y and Z ({@code Utilities.isBetween} with padding 0). */
public record Box(long minX, long minY, long minZ, long maxX, long maxY, long maxZ) {

	public static Box ofCorners(long x1, long y1, long z1, long x2, long y2, long z2) {
		return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
	}

	public boolean contains(Point point) {
		return point.x() >= minX && point.x() <= maxX && point.y() >= minY && point.y() <= maxY && point.z() >= minZ && point.z() <= maxZ;
	}

	public boolean isEmpty() {
		return minX > maxX || minY > maxY || minZ > maxZ;
	}

	/** Number of blocks; saturates instead of overflowing for world-height zones. */
	public double volume() {
		return isEmpty() ? 0 : (double) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
	}

	/** Which face a shrink moves, as seen on the map: west = -X, east = +X, north = -Z, south = +Z. */
	public enum Side {WEST, EAST, BELOW, ABOVE, NORTH, SOUTH}

	/** The largest box inside this one that no longer contains {@code point}, by moving one face past it. */
	public Box cutAway(Point point, Side side) {
		return switch (side) {
			case WEST -> new Box(point.x() + 1, minY, minZ, maxX, maxY, maxZ);
			case EAST -> new Box(minX, minY, minZ, point.x() - 1, maxY, maxZ);
			case BELOW -> new Box(minX, point.y() + 1, minZ, maxX, maxY, maxZ);
			case ABOVE -> new Box(minX, minY, minZ, maxX, point.y() - 1, maxZ);
			case NORTH -> new Box(minX, minY, point.z() + 1, maxX, maxY, maxZ);
			case SOUTH -> new Box(minX, minY, minZ, maxX, maxY, point.z() - 1);
		};
	}
}
