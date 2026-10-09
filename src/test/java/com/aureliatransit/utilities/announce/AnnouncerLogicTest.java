package com.aureliatransit.utilities.announce;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncerLogicTest {

	private static final int RED = 0xFF0000;
	private static final int BLUE = 0x0000FF;
	private static final int GREEN = 0x00FF00;
	private static final Predicate<String> ALL_CLIPS = clip -> true;
	private static final Predicate<String> NO_STATION_CLIPS = Set.of("chime", "terminus")::contains;

	private static RideSnapshot ride(int stopIndex, String next, double speed, double door, double distance) {
		return new RideSnapshot(1, stopIndex, stopIndex + 1, next, false, "Red Line", RED, "Red Line", RED, "Zeta", List.of(), speed, door, distance, false);
	}

	private static RideSnapshot withRoute(RideSnapshot r, boolean terminating, String nextRoute, int nextRouteColor, String destination, List<RideSnapshot.Interchange> interchanges) {
		return new RideSnapshot(r.vehicleId(), r.thisPlatformId(), r.nextPlatformId(), r.nextStation(), terminating, r.route(), r.routeColor(), nextRoute, nextRouteColor, destination, interchanges, r.speedMetresPerSecond(), r.doorValue(), r.distanceToNextStop(), r.announcementsDisabled());
	}

	/** Stands at the platform with the doors open, then leaves: the usual start of a leg. */
	private static void dwell(AnnouncerLogic logic, int stopIndex, String next, long now) {
		assertNull(logic.update(ride(stopIndex, next, 0, 1, 500), now, ALL_CLIPS));
		assertNull(logic.update(ride(stopIndex, next, 0, 0, 500), now + 1000, ALL_CLIPS));
	}

	@Test
	void normalLegSaysNextOnceThenArrivingOnce() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		dwell(logic, 0, "東京駅|Alpha|AT03", 0);
		final Announcement next = logic.update(ride(0, "東京駅|Alpha|AT03", 5, 0, 500), 5000, ALL_CLIPS);
		assertEquals(Announcement.Kind.NEXT, next.kind());
		assertEquals(List.of("The next station is Alpha."), next.lines());
		assertEquals(List.of("next_alpha"), next.clips());
		assertNull(logic.update(ride(0, "東京駅|Alpha|AT03", 15, 0, 400), 10_000, ALL_CLIPS));

		// 15 m/s * 20 s = 300 blocks: arriving window.
		final Announcement arriving = logic.update(ride(0, "東京駅|Alpha|AT03", 15, 0, 290), 40_000, ALL_CLIPS);
		assertEquals(Announcement.Kind.ARRIVING, arriving.kind());
		assertEquals(List.of("Arriving at Alpha."), arriving.lines());
		assertEquals(List.of("chime"), arriving.clips());
		assertNull(logic.update(ride(0, "東京駅|Alpha|AT03", 10, 0, 100), 45_000, ALL_CLIPS));
	}

	@Test
	void brakingIntoThePlatformDoesNotAnnounceTheFollowingStation() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		dwell(logic, 0, "Alpha", 0);
		assertNotNull(logic.update(ride(0, "Alpha", 10, 0, 800), 5000, ALL_CLIPS));
		// Head enters Alpha's platform: MTR moves to the next leg while the train still brakes.
		assertNull(logic.update(ride(1, "Beta", 8, 0, 900), 60_000, ALL_CLIPS));
		assertNull(logic.update(ride(1, "Beta", 3, 0, 880), 61_000, ALL_CLIPS));
		assertNull(logic.update(ride(1, "Beta", 0, 0, 870), 62_000, ALL_CLIPS));
		assertNull(logic.update(ride(1, "Beta", 0, 1, 870), 63_000, ALL_CLIPS));
		final Announcement next = logic.update(ride(1, "Beta", 5, 0, 860), 90_000, ALL_CLIPS);
		assertEquals(List.of("The next station is Beta."), next.lines());
	}

	@Test
	void boardingAMovingTrainStaysQuietUntilTheNextStop() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		assertNull(logic.update(ride(0, "Alpha", 20, 0, 900), 0, ALL_CLIPS));
		assertNull(logic.update(ride(0, "Alpha", 20, 0, 100), 40_000, ALL_CLIPS));
	}

	@Test
	void lastLegAddsTheTerminusClipOnce() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		dwell(logic, 4, "Zeta", 0);
		final Announcement next = logic.update(withRoute(ride(4, "Zeta", 5, 0, 900), true, "", 0, "", List.of()), 5000, ALL_CLIPS);
		assertEquals(List.of("next_zeta", "terminus"), next.clips());
		assertEquals(List.of("The next station is Zeta.", "This is the last stop."), next.lines());
		final Announcement arriving = logic.update(withRoute(ride(4, "Zeta", 10, 0, 150), true, "", 0, "", List.of()), 60_000, ALL_CLIPS);
		assertEquals(List.of("Arriving at Zeta.", "This is the last stop. Please take all your belongings."), arriving.lines());
		// At the terminus MTR has no next stop: nothing more, however long the train dwells.
		assertNull(logic.update(ride(5, "", 0, 1, Double.NaN), 70_000, ALL_CLIPS));
		assertNull(logic.update(ride(5, "", 0, 0, Double.NaN), 90_000, ALL_CLIPS));
	}

	@Test
	void unknownStationFallsBackToTheChime() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		dwell(logic, 0, "Gamma", 0);
		assertEquals(List.of("chime"), logic.update(ride(0, "Gamma", 5, 0, 900), 5000, NO_STATION_CLIPS).clips());
		// No Latin name, no slug: chime even if a pack had a "next_" clip.
		final AnnouncerLogic cjk = new AnnouncerLogic();
		dwell(cjk, 0, "東京駅", 0);
		assertEquals(List.of("chime"), cjk.update(ride(0, "東京駅", 5, 0, 900), 5000, ALL_CLIPS).clips());
	}

	@Test
	void routeChangeSaysContinuesInsteadOfLastStop() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		dwell(logic, 4, "Zeta", 0);
		final Announcement next = logic.update(withRoute(ride(4, "Zeta", 5, 0, 900), true, "Blue Line|藍線||B", BLUE, "東京駅|Alpha", List.of()), 5000, ALL_CLIPS);
		assertEquals(List.of("The next station is Zeta.", "This train continues as Blue Line to Alpha."), next.lines());
		assertEquals(List.of("next_zeta"), next.clips());
	}

	@Test
	void changeHereListsOtherRoutesAtThatStationOnly() {
		final List<RideSnapshot.Interchange> interchanges = List.of(
				new RideSnapshot.Interchange("Alpha", RED, "Red Line"),
				new RideSnapshot.Interchange("Alpha", BLUE, "Blue Line|藍線"),
				new RideSnapshot.Interchange("Alpha", GREEN, "Green Line||G1"),
				new RideSnapshot.Interchange("Alpha", BLUE, "Blue Line|藍線"),
				new RideSnapshot.Interchange("Alpha", GREEN, ""),
				new RideSnapshot.Interchange("Alpha East", GREEN, "Purple Line"));
		final RideSnapshot ride = withRoute(ride(0, "Alpha", 5, 0, 900), false, "Red Line", RED, "Zeta", interchanges);
		assertEquals(List.of("Blue Line", "Green Line"), AnnouncerLogic.changeHere(ride));
		assertEquals(List.of("The next station is Alpha.", "Change here for Blue Line and Green Line."), AnnouncerLogic.next(ride, ALL_CLIPS).lines());
		assertEquals("A, B and C", AnnouncerLogic.joinNames(List.of("A", "B", "C")));
		// Same name but another colour is a different route, as in MTR.
		final RideSnapshot otherColour = withRoute(ride, false, "Red Line", RED, "Zeta", List.of(new RideSnapshot.Interchange("Alpha", GREEN, "Red Line")));
		assertEquals(List.of("Red Line"), AnnouncerLogic.changeHere(otherColour));
	}

	@Test
	void shortLegPlaysOnlyNextStation() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		dwell(logic, 0, "Alpha", 0);
		assertNotNull(logic.update(ride(0, "Alpha", 10, 0, 400), 5000, ALL_CLIPS));
		assertNull(logic.update(ride(0, "Alpha", 10, 0, 150), 15_000, ALL_CLIPS));
		assertNull(logic.update(ride(0, "Alpha", 10, 0, 50), 30_000, ALL_CLIPS));
	}

	@Test
	void departingCloseToThePlatformGoesStraightToArriving() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		dwell(logic, 0, "Alpha", 0);
		assertEquals(Announcement.Kind.ARRIVING, logic.update(ride(0, "Alpha", 3, 0, 50), 5000, ALL_CLIPS).kind());
		assertNull(logic.update(ride(0, "Alpha", 3, 0, 40), 6000, ALL_CLIPS));
	}

	@Test
	void loopRouteAnnouncesEveryLap() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		long now = 0;
		for (int lap = 0; lap < 2; lap++) {
			for (int stop = 0; stop < 2; stop++) {
				final String next = stop == 0 ? "Beta" : "Alpha";
				dwell(logic, stop, next, now);
				final Announcement announcement = logic.update(ride(stop, next, 5, 0, 900), now + 5000, ALL_CLIPS);
				assertEquals("The next station is " + next + ".", announcement.lines().get(0), "lap " + lap + " stop " + stop);
				now += 120_000;
			}
		}
	}

	@Test
	void unknownDistanceNeverSaysArriving() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		dwell(logic, 0, "Alpha", 0);
		assertNotNull(logic.update(ride(0, "Alpha", 5, 0, Double.NaN), 5000, ALL_CLIPS));
		assertNull(logic.update(ride(0, "Alpha", 5, 0, Double.NaN), 60_000, ALL_CLIPS));
	}

	@Test
	void routeWithAnnouncementsDisabledStaysQuiet() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		final RideSnapshot base = ride(0, "Alpha", 0, 1, 900);
		final RideSnapshot disabled = new RideSnapshot(1, 0, 1, "Alpha", false, "Red Line", RED, "Red Line", RED, "Zeta", List.of(), 5, 0, 900, true);
		assertNull(logic.update(base, 0, ALL_CLIPS));
		assertNull(logic.update(disabled, 5000, ALL_CLIPS));
		assertNull(logic.update(new RideSnapshot(1, 0, 1, "Alpha", false, "Red Line", RED, "Red Line", RED, "Zeta", List.of(), 5, 0, 100, true), 60_000, ALL_CLIPS));
	}

	@Test
	void changingVehicleStartsAFreshRide() {
		final AnnouncerLogic logic = new AnnouncerLogic();
		dwell(logic, 0, "Alpha", 0);
		assertNotNull(logic.update(ride(0, "Alpha", 5, 0, 900), 5000, ALL_CLIPS));
		final RideSnapshot otherTrain = new RideSnapshot(2, 0, 1, "Alpha", false, "Red Line", RED, "Red Line", RED, "Zeta", List.of(), 0, 1, 900, false);
		assertNull(logic.update(otherTrain, 6000, ALL_CLIPS));
		assertNotNull(logic.update(new RideSnapshot(2, 0, 1, "Alpha", false, "Red Line", RED, "Red Line", RED, "Zeta", List.of(), 5, 0, 900, false), 9000, ALL_CLIPS));
	}
}
