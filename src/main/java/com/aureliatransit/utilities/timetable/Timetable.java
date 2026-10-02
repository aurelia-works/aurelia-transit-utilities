package com.aureliatransit.utilities.timetable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Departure times of one depot as milliseconds of the day (0 to 86 399 999), the unit of MTR's
 * {@code Depot.getRealTimeDepartures()}. Pure: callers convert between local clock time and MTR's stored value.
 */
public final class Timetable {

	public static final long DAY = 86_400_000L;
	public static final long MINUTE = 60_000L;
	/** A generator never makes more than this many departures (a 1-minute gap all day is 1440). */
	public static final int MAX_DEPARTURES = 2000;
	/** MTR's trains-per-hour slider: 0..20 quarter trains per hour, so at most 5 an hour (EditDepotScreen). */
	public static final int MAX_FREQUENCY = 20;

	private static final Pattern TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$");

	private Timetable() {
	}

	public static OptionalLong parseTime(String text) {
		final Matcher matcher = TIME.matcher(text == null ? "" : text.strip());
		if (!matcher.matches()) {
			return OptionalLong.empty();
		}
		final int hours = Integer.parseInt(matcher.group(1));
		final int minutes = Integer.parseInt(matcher.group(2));
		final int seconds = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
		if (hours > 23 || minutes > 59 || seconds > 59) {
			return OptionalLong.empty();
		}
		return OptionalLong.of(((hours * 60L + minutes) * 60 + seconds) * 1000);
	}

	/** Whole minutes, at least 1, at most a day. */
	public static OptionalLong parseMinutes(String text) {
		try {
			final long minutes = Long.parseLong(text == null ? "" : text.strip());
			return minutes < 1 || minutes > 1440 ? OptionalLong.empty() : OptionalLong.of(minutes * MINUTE);
		} catch (NumberFormatException e) {
			return OptionalLong.empty();
		}
	}

	public static String format(long millisOfDay) {
		final long seconds = Math.floorMod(millisOfDay, DAY) / 1000;
		final long secondPart = seconds % 60;
		return secondPart == 0
				? String.format(Locale.ROOT, "%02d:%02d", seconds / 3600, seconds / 60 % 60)
				: String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, secondPart);
	}

	/**
	 * Departures from {@code first} to {@code last} inclusive, every {@code gap}. When {@code last} is before
	 * {@code first} the service runs past midnight. Capped at {@link #MAX_DEPARTURES}.
	 */
	public static List<Long> generate(long first, long last, long gap) {
		final List<Long> result = new ArrayList<>();
		if (gap <= 0) {
			return result;
		}
		final long span = Math.floorMod(last - first, DAY);
		for (long offset = 0; offset <= span && result.size() < MAX_DEPARTURES; offset += gap) {
			result.add(Math.floorMod(first + offset, DAY));
		}
		return result;
	}

	/** Sorted, without duplicates, wrapped into one day. */
	public static List<Long> normalise(List<Long> times) {
		final TreeSet<Long> set = new TreeSet<>();
		times.forEach(time -> set.add(Math.floorMod(time, DAY)));
		return new ArrayList<>(set);
	}

	public static List<Long> merge(List<Long> a, List<Long> b) {
		final List<Long> all = new ArrayList<>(a);
		all.addAll(b);
		return normalise(all);
	}

	/** Moves every departure by {@code delta}, wrapping around midnight. */
	public static List<Long> shift(List<Long> times, long delta) {
		final List<Long> result = new ArrayList<>();
		times.forEach(time -> result.add(time + delta));
		return normalise(result);
	}

	/** Removes departures in [from, to] inclusive; wraps past midnight when {@code to < from}. A "rest period". */
	public static List<Long> removeBetween(List<Long> times, long from, long to) {
		final long span = Math.floorMod(to - from, DAY);
		final List<Long> result = new ArrayList<>();
		times.forEach(time -> {
			if (Math.floorMod(time - from, DAY) > span) {
				result.add(time);
			}
		});
		return normalise(result);
	}

	/** The longest wait between consecutive departures, around midnight included; 0 with fewer than 2. */
	public static long longestGap(List<Long> times) {
		final List<Long> sorted = normalise(times);
		if (sorted.size() < 2) {
			return 0;
		}
		long longest = sorted.get(0) + DAY - sorted.get(sorted.size() - 1);
		for (int i = 1; i < sorted.size(); i++) {
			longest = Math.max(longest, sorted.get(i) - sorted.get(i - 1));
		}
		return longest;
	}

	/** For "repeat infinitely" loops: N trains spread evenly over one round trip, starting at {@code first}. */
	public static List<Long> evenlySpaced(long first, long roundTrip, int trains) {
		final List<Long> result = new ArrayList<>();
		if (trains < 1 || roundTrip <= 0) {
			return result;
		}
		for (int i = 0; i < Math.min(trains, MAX_DEPARTURES); i++) {
			result.add(Math.floorMod(first + Math.round((double) roundTrip * i / trains), DAY));
		}
		return normalise(result);
	}

	/** MTR frequency value (quarter trains per hour) for a wanted gap; 0 = no trains. Gaps under 12 min clamp to 20. */
	public static int frequencyForGap(long gapMillis) {
		if (gapMillis <= 0) {
			return 0;
		}
		return (int) Math.max(1, Math.min(MAX_FREQUENCY, Math.round(240.0 * MINUTE / gapMillis)));
	}

	/** The gap MTR actually runs for a frequency value, in milliseconds; 0 for no trains. */
	public static long gapForFrequency(int frequency) {
		return frequency <= 0 ? 0 : 240 * MINUTE / frequency;
	}

	/**
	 * Owner idea 22: depot departures so trains reach the first station at the wanted times, given the travel time
	 * from the depot to the first station.
	 */
	public static List<Long> departuresForFirstStation(List<Long> firstStationTimes, long travelTime) {
		return shift(firstStationTimes, -travelTime);
	}
}
