package com.aureliatransit.utilities.announce;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Announcements for a player waiting on a platform, from MTR's arrival list (the data MTR's own departure screens
 * show). For the next train of the platform: "approaching" about a minute out, "now arriving" in the last
 * {@link #ARRIVING_SECONDS} seconds, and once "delayed" when MTR predicts it at least {@link #DELAY_MINUTES} minutes
 * late. Each departure gets each call at most once; a player who arrives late hears only the calls still due.
 */
public final class PlatformCalls {

	public static final long APPROACHING_SECONDS = 60;
	public static final long ARRIVING_SECONDS = 15;
	public static final long DELAY_MINUTES = 2;
	/** Departures are forgotten this long after they leave, so the memory stays small. */
	private static final long FORGET_AFTER_MILLIS = 120_000;

	/**
	 * One upcoming train at the platform. {@code key} identifies the departure (route, departure index, platform);
	 * times are milliseconds on the same clock as {@code now}.
	 */
	public record Arrival(String key, String platformName, String routeName, String destination, long arrivalMillis, long departureMillis, long deviationMillis, boolean terminating, int carCount) {
	}

	private static final class Said {
		private boolean approaching;
		private boolean arriving;
		private boolean delayed;
		private long departureMillis;
	}

	private final Map<String, Said> said = new HashMap<>();

	public void reset() {
		said.clear();
	}

	/** At most one announcement per call; the next one follows on a later update. */
	public Announcement update(List<Arrival> arrivals, long now, Predicate<String> hasClip) {
		forgetOld(now);
		final Arrival next = arrivals.stream().filter(arrival -> arrival.departureMillis() > now).min(Comparator.comparingLong(Arrival::arrivalMillis)).orElse(null);
		if (next == null) {
			return null;
		}
		final Said state = said.computeIfAbsent(next.key(), key -> new Said());
		state.departureMillis = next.departureMillis();
		final long secondsAway = Math.floorDiv(next.arrivalMillis() - now, 1000);
		if (!state.arriving && secondsAway <= ARRIVING_SECONDS) {
			// Too late for the earlier calls: skip them.
			state.arriving = state.approaching = state.delayed = true;
			return arriving(next, hasClip);
		}
		if (!state.delayed && next.deviationMillis() >= DELAY_MINUTES * 60_000 && secondsAway <= 15 * 60) {
			state.delayed = true;
			return delayed(next, hasClip);
		}
		if (!state.approaching && secondsAway <= APPROACHING_SECONDS) {
			state.approaching = true;
			return approaching(next, hasClip);
		}
		return null;
	}

	private void forgetOld(long now) {
		final Iterator<Map.Entry<String, Said>> iterator = said.entrySet().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getValue().departureMillis + FORGET_AFTER_MILLIS < now) {
				iterator.remove();
			}
		}
	}

	static Announcement approaching(Arrival arrival, Predicate<String> hasClip) {
		final List<String> lines = new ArrayList<>();
		lines.add(capitalise(platform(arrival) + "the next train is " + service(arrival) + ". It arrives in about a minute."));
		if (arrival.carCount() > 0) {
			lines.add("This train has " + arrival.carCount() + (arrival.carCount() == 1 ? " car." : " cars."));
		}
		return new Announcement(Announcement.Kind.PLATFORM_APPROACHING, lines, List.of(clip("platform_approaching", hasClip)));
	}

	static Announcement arriving(Arrival arrival, Predicate<String> hasClip) {
		final List<String> lines = new ArrayList<>();
		lines.add(capitalise(platform(arrival) + "the train now arriving is " + service(arrival) + "."));
		lines.add(arrival.terminating() ? "This train terminates here. Please do not board." : "Please stand back from the platform edge.");
		return new Announcement(Announcement.Kind.PLATFORM_ARRIVING, lines, List.of(clip("platform_arriving", hasClip)));
	}

	static Announcement delayed(Arrival arrival, Predicate<String> hasClip) {
		final long minutes = Math.round(arrival.deviationMillis() / 60_000.0);
		final String line = platform(arrival) + "we are sorry, " + service(arrival) + " is delayed by about " + minutes + (minutes == 1 ? " minute." : " minutes.");
		return new Announcement(Announcement.Kind.PLATFORM_DELAYED, List.of(capitalise(line)), List.of(clip("platform_delayed", hasClip)));
	}

	private static String clip(String key, Predicate<String> hasClip) {
		return hasClip.test(key) ? key : "chime";
	}

	/** "Platform 2: " or nothing when the platform has no name. */
	private static String platform(Arrival arrival) {
		final String name = arrival.platformName() == null ? "" : arrival.platformName().trim();
		return name.isEmpty() ? "" : "Platform " + name + ": ";
	}

	/** "the Route 1 service to Alpha". */
	static String service(Arrival arrival) {
		final String route = StationName.of(StationName.routeName(arrival.routeName())).english().trim();
		final String destination = StationName.of(arrival.destination()).english().trim();
		final StringBuilder builder = new StringBuilder("the ");
		if (!route.isEmpty()) {
			builder.append(route).append(' ');
		}
		builder.append("service");
		if (!destination.isEmpty()) {
			builder.append(" to ").append(destination);
		}
		return builder.toString();
	}

	private static String capitalise(String line) {
		return line.isEmpty() ? line : Character.toUpperCase(line.charAt(0)) + line.substring(1);
	}
}
