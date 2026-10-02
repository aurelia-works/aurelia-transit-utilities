package com.aureliatransit.utilities.dwell;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform dwell times in milliseconds, with the same limits as MTR's platform screen
 * ({@code SavedRailScreenBase}): half-second steps, at least 0.5 s, at most 10 minutes.
 */
public final class DwellTime {

	public static final long STEP_MILLIS = 500;
	public static final long MIN_MILLIS = 500;
	public static final long MAX_MILLIS = 10 * 60 * 1000;

	private static final Pattern MINUTES_SECONDS = Pattern.compile("^(\\d{1,2})\\s*(?::|m)\\s*(\\d{1,2}(?:\\.\\d+)?)?\\s*s?$");
	private static final Pattern SECONDS = Pattern.compile("^(\\d{1,4}(?:\\.\\d+)?)\\s*s?$");

	private DwellTime() {
	}

	/**
	 * Accepts "30", "30s", "12.5", "1:30", "1m30", "1m30s", "2m". Returns empty for anything else or when the value
	 * is outside MTR's range; values in range are rounded to the nearest half second.
	 */
	public static OptionalLong parseMillis(String text) {
		if (text == null) {
			return OptionalLong.empty();
		}
		final String trimmed = text.strip().toLowerCase(Locale.ROOT);
		double seconds;
		final Matcher minutesSeconds = MINUTES_SECONDS.matcher(trimmed);
		final Matcher plainSeconds = SECONDS.matcher(trimmed);
		if (minutesSeconds.matches()) {
			final double secondsPart = minutesSeconds.group(2) == null ? 0 : Double.parseDouble(minutesSeconds.group(2));
			if (secondsPart >= 60) {
				return OptionalLong.empty();
			}
			seconds = Integer.parseInt(minutesSeconds.group(1)) * 60 + secondsPart;
		} else if (plainSeconds.matches()) {
			seconds = Double.parseDouble(plainSeconds.group(1));
		} else {
			return OptionalLong.empty();
		}
		final long millis = Math.round(seconds * 2) * STEP_MILLIS;
		return millis < MIN_MILLIS || millis > MAX_MILLIS ? OptionalLong.empty() : OptionalLong.of(millis);
	}

	/** Adds {@code deltaMillis} and clamps to MTR's range. */
	public static long adjust(long millis, long deltaMillis) {
		return clamp(millis + deltaMillis);
	}

	public static long clamp(long millis) {
		final long stepped = Math.round(millis / (double) STEP_MILLIS) * STEP_MILLIS;
		return Math.max(MIN_MILLIS, Math.min(MAX_MILLIS, stepped));
	}

	/** "45 s", "12.5 s", "1:30", "2:05.5". */
	public static String format(long millis) {
		final long halfSeconds = Math.round(millis / (double) STEP_MILLIS);
		final long wholeSeconds = halfSeconds / 2;
		final String half = halfSeconds % 2 == 0 ? "" : ".5";
		if (wholeSeconds < 60) {
			return wholeSeconds + half + " s";
		}
		return String.format(Locale.ROOT, "%d:%02d%s", wholeSeconds / 60, wholeSeconds % 60, half);
	}
}
