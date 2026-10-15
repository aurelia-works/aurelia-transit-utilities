package com.aureliatransit.utilities.announce;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class PlatformCallsTest {

	private static final Predicate<String> NO_CLIPS = key -> false;

	private static PlatformCalls.Arrival train(String key, long arrival, long deviation, boolean terminating) {
		return new PlatformCalls.Arrival(key, "2", "Route 1||R1", "東京駅|Alpha|AT03", arrival, arrival + 20_000, deviation, terminating, 4);
	}

	/** Runs the calls once a second from {@code from} to {@code to} (ms) and collects what was said. */
	private static List<Announcement> run(PlatformCalls calls, List<PlatformCalls.Arrival> arrivals, long from, long to) {
		final List<Announcement> said = new ArrayList<>();
		for (long now = from; now <= to; now += 1000) {
			final Announcement announcement = calls.update(arrivals, now, NO_CLIPS);
			if (announcement != null) {
				said.add(announcement);
			}
		}
		return said;
	}

	@Test
	void approachingThenArrivingOnce() {
		final List<Announcement> said = run(new PlatformCalls(), List.of(train("a", 120_000, 0, false)), 0, 130_000);
		assertEquals(List.of(Announcement.Kind.PLATFORM_APPROACHING, Announcement.Kind.PLATFORM_ARRIVING), said.stream().map(Announcement::kind).toList());
		assertEquals("Platform 2: the next train is the Route 1 service to Alpha. It arrives in about a minute.", said.get(0).lines().get(0));
		assertEquals("This train has 4 cars.", said.get(0).lines().get(1));
		assertEquals("Platform 2: the train now arriving is the Route 1 service to Alpha.", said.get(1).lines().get(0));
		assertEquals(List.of("chime"), said.get(1).clips());
	}

	@Test
	void lateArrivalSkipsStraightToArriving() {
		final List<Announcement> said = run(new PlatformCalls(), List.of(train("a", 10_000, 0, true)), 0, 40_000);
		assertEquals(1, said.size());
		assertEquals(Announcement.Kind.PLATFORM_ARRIVING, said.get(0).kind());
		assertEquals("This train terminates here. Please do not board.", said.get(0).lines().get(1));
	}

	@Test
	void delayIsAnnouncedOnceBeforeApproaching() {
		final List<Announcement> said = run(new PlatformCalls(), List.of(train("a", 600_000, 180_000, false)), 0, 610_000);
		assertEquals(List.of(Announcement.Kind.PLATFORM_DELAYED, Announcement.Kind.PLATFORM_APPROACHING, Announcement.Kind.PLATFORM_ARRIVING), said.stream().map(Announcement::kind).toList());
		assertEquals("Platform 2: we are sorry, the Route 1 service to Alpha is delayed by about 3 minutes.", said.get(0).lines().get(0));
	}

	@Test
	void onlyTheNextTrainIsCalledAndDepartedTrainsAreSkipped() {
		final PlatformCalls calls = new PlatformCalls();
		final List<PlatformCalls.Arrival> arrivals = List.of(train("gone", -60_000, 0, false), train("later", 100_000, 0, false), train("first", 50_000, 0, false));
		final Announcement first = calls.update(arrivals, 0, NO_CLIPS);
		assertEquals(Announcement.Kind.PLATFORM_APPROACHING, first.kind());
		assertNull(calls.update(arrivals, 1000, NO_CLIPS), "nothing new until the next call is due");
	}

	@Test
	void clipsAreUsedWhenThePackHasThem() {
		final Announcement announcement = new PlatformCalls().update(List.of(train("a", 30_000, 0, false)), 0, key -> key.equals("platform_approaching"));
		assertEquals(List.of("platform_approaching"), announcement.clips());
	}

	@Test
	void serviceNameWithoutRouteOrDestination() {
		assertEquals("the service", PlatformCalls.service(new PlatformCalls.Arrival("k", "", "", "", 0, 0, 0, false, 0)));
		final Announcement announcement = new PlatformCalls().update(List.of(new PlatformCalls.Arrival("k", "", "", "Beta", 30_000, 40_000, 0, false, 0)), 0, NO_CLIPS);
		assertEquals(List.of("The next train is the service to Beta. It arrives in about a minute."), announcement.lines());
	}
}
