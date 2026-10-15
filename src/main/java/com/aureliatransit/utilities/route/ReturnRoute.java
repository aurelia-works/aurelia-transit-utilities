package com.aureliatransit.utilities.route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Plans the opposite direction of a route: its stops in reverse order, each moved to another platform of the same
 * station. The ends of a non-circular route keep their platform (trains turn back there); every other stop moves to
 * the nearest other platform of its station, which is the opposite track in the usual layouts. The player can cycle
 * any stop through its station's platforms before anything is saved.
 */
public final class ReturnRoute {

	private ReturnRoute() {
	}

	/** A platform with its station ({@code 0} = in no station) and the middle of its rail. */
	public record Stop(long platformId, long stationId, double x, double z) {
	}

	/** One stop of the planned return route. {@code options} always contains {@code platformId}. */
	public record Choice(long originalPlatformId, long stationId, long platformId, List<Long> options) {

		public Choice {
			options = List.copyOf(options);
		}

		public boolean moved() {
			return platformId != originalPlatformId;
		}

		/** The next platform of the same station, wrapping around. */
		public Choice cycle() {
			if (options.size() < 2) {
				return this;
			}
			final int index = options.indexOf(platformId);
			return new Choice(originalPlatformId, stationId, options.get((index + 1) % options.size()), options);
		}
	}

	/**
	 * @param stops              the route's stops in order
	 * @param platformsByStation every platform of the route's transport mode, by station id
	 * @param circular           circular routes have no ends, so every stop moves
	 */
	public static List<Choice> plan(List<Stop> stops, Map<Long, List<Stop>> platformsByStation, boolean circular) {
		final List<Choice> choices = new ArrayList<>();
		for (int i = stops.size() - 1; i >= 0; i--) {
			final Stop stop = stops.get(i);
			final boolean end = !circular && (i == 0 || i == stops.size() - 1);
			final List<Long> options = options(stop, platformsByStation);
			final long pick = end || options.size() < 2 ? stop.platformId() : options.get(1);
			choices.add(new Choice(stop.platformId(), stop.stationId(), pick, options));
		}
		return choices;
	}

	/** The stop's own platform first, then the other platforms of its station, nearest first. */
	static List<Long> options(Stop stop, Map<Long, List<Stop>> platformsByStation) {
		final List<Long> options = new ArrayList<>();
		options.add(stop.platformId());
		if (stop.stationId() == 0) {
			return options;
		}
		final List<Stop> others = new ArrayList<>(platformsByStation.getOrDefault(stop.stationId(), List.of()));
		others.removeIf(other -> other.platformId() == stop.platformId());
		others.sort(Comparator.comparingDouble((Stop other) -> distanceSquared(stop, other)).thenComparingLong(Stop::platformId));
		others.forEach(other -> options.add(other.platformId()));
		return options;
	}

	private static double distanceSquared(Stop a, Stop b) {
		final double dx = a.x() - b.x();
		final double dz = a.z() - b.z();
		return dx * dx + dz * dz;
	}
}
