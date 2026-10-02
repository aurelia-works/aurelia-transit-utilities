package com.aureliatransit.utilities.overlap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Station zones and platforms of one transport mode, in MTR's station order, and MTR's assignment rule
 * ({@code Data.mapAreasAndSavedRails}): a platform belongs to the first station whose zone contains its midpoint.
 */
public final class Zones {

	public record Station(long id, Box box) {
	}

	public record Platform(long id, Point midpoint) {
	}

	/** A platform inside two or more zones. {@code stationIds} are in MTR order; the first is MTR's pick. */
	public record Conflict(long platformId, Point midpoint, List<Long> stationIds) {

		public long currentStationId() {
			return stationIds.get(0);
		}
	}

	private final List<Station> stations;
	private final List<Platform> platforms;

	public Zones(List<Station> stations, List<Platform> platforms) {
		this.stations = List.copyOf(stations);
		this.platforms = List.copyOf(platforms);
	}

	public List<Station> stations() {
		return stations;
	}

	public List<Platform> platforms() {
		return platforms;
	}

	/** Station id per platform id; platforms in no zone are absent. */
	public Map<Long, Long> assignment() {
		final Map<Long, Long> result = new LinkedHashMap<>();
		for (final Platform platform : platforms) {
			for (final Station station : stations) {
				if (station.box().contains(platform.midpoint())) {
					result.put(platform.id(), station.id());
					break;
				}
			}
		}
		return result;
	}

	public List<Conflict> conflicts() {
		final List<Conflict> result = new ArrayList<>();
		for (final Platform platform : platforms) {
			final List<Long> containing = new ArrayList<>();
			for (final Station station : stations) {
				if (station.box().contains(platform.midpoint())) {
					containing.add(station.id());
				}
			}
			if (containing.size() > 1) {
				result.add(new Conflict(platform.id(), platform.midpoint(), containing));
			}
		}
		return result;
	}

	public Zones withBox(long stationId, Box box) {
		final List<Station> newStations = new ArrayList<>();
		for (final Station station : stations) {
			newStations.add(station.id() == stationId ? new Station(stationId, box) : station);
		}
		return new Zones(newStations, platforms);
	}

	public Box box(long stationId) {
		for (final Station station : stations) {
			if (station.id() == stationId) {
				return station.box();
			}
		}
		return null;
	}

	/** Platforms whose station differs between this and {@code other}: platform id to (before, after); null = no station. */
	public Map<Long, Long[]> changesTo(Zones other) {
		final Map<Long, Long> before = assignment();
		final Map<Long, Long> after = other.assignment();
		final Map<Long, Long[]> result = new LinkedHashMap<>();
		for (final Platform platform : platforms) {
			final Long a = before.get(platform.id());
			final Long b = after.get(platform.id());
			if (!Objects.equals(a, b)) {
				result.put(platform.id(), new Long[]{a, b});
			}
		}
		return result;
	}
}
