package com.aureliatransit.utilities.overlap;

import java.util.Optional;

/**
 * The Y range of a station or depot zone. MTR's dashboard draws zones from {@code Long.MIN_VALUE} to
 * {@code Long.MAX_VALUE} ("no limit"); a blank field means the same here.
 */
public record HeightRange(long bottom, long top) {

	public static final long NO_BOTTOM = Long.MIN_VALUE;
	public static final long NO_TOP = Long.MAX_VALUE;
	/** Generous limits for typed values; anything outside is a typo, not a build height. */
	public static final long MIN_TYPED = -30_000_000;
	public static final long MAX_TYPED = 30_000_000;

	/** Parsed field: empty Optional = invalid; {@code null} inside = no limit. */
	public static Optional<Long> parseBound(String text) {
		final String trimmed = text == null ? "" : text.strip();
		if (trimmed.isEmpty()) {
			return Optional.of(Long.MIN_VALUE);
		}
		try {
			final long value = Long.parseLong(trimmed);
			return value < MIN_TYPED || value > MAX_TYPED ? Optional.empty() : Optional.of(value);
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	/** Both fields together; empty when either is invalid or bottom is above top. */
	public static Optional<HeightRange> parse(String bottomText, String topText) {
		final Optional<Long> bottom = parseBound(bottomText);
		final Optional<Long> top = parseBound(topText);
		if (bottom.isEmpty() || top.isEmpty()) {
			return Optional.empty();
		}
		final long bottomValue = bottom.get();
		final long topValue = top.get() == Long.MIN_VALUE ? NO_TOP : top.get();
		return bottomValue > topValue ? Optional.empty() : Optional.of(new HeightRange(bottomValue, topValue));
	}

	public boolean hasBottom() {
		return bottom != NO_BOTTOM;
	}

	public boolean hasTop() {
		return top != NO_TOP;
	}

	public Box applyTo(Box box) {
		return new Box(box.minX(), bottom, box.minZ(), box.maxX(), top, box.maxZ());
	}

	public static HeightRange of(Box box) {
		return new HeightRange(box.minY(), box.maxY());
	}

	/** "any height", "Y 10 to 40", "Y 10 and up", "up to Y 40". */
	public String describe() {
		if (!hasBottom() && !hasTop()) {
			return "any height";
		} else if (!hasTop()) {
			return "Y " + bottom + " and up";
		} else if (!hasBottom()) {
			return "up to Y " + top;
		}
		return "Y " + bottom + " to " + top;
	}

	public String bottomField() {
		return hasBottom() ? Long.toString(bottom) : "";
	}

	public String topField() {
		return hasTop() ? Long.toString(top) : "";
	}
}
