package com.aureliatransit.utilities.timetable;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.aureliatransit.utilities.timetable.Timetable.MINUTE;
import static org.junit.jupiter.api.Assertions.*;

class TimetableTest {

	private static long t(String text) {
		return Timetable.parseTime(text).orElseThrow();
	}

	@Test
	void parsesAndFormatsTimes() {
		assertEquals(6 * 60 * MINUTE, t("06:00"));
		assertEquals(t("6:00"), t("06:00:00"));
		assertEquals("23:59:30", Timetable.format(t("23:59:30")));
		assertEquals("00:05", Timetable.format(Timetable.DAY + 5 * MINUTE));
		assertTrue(Timetable.parseTime("24:00").isEmpty());
		assertTrue(Timetable.parseTime("12:60").isEmpty());
		assertTrue(Timetable.parseTime("noon").isEmpty());
		assertEquals(15 * MINUTE, Timetable.parseMinutes("15").orElseThrow());
		assertTrue(Timetable.parseMinutes("0").isEmpty());
	}

	@Test
	void generatesEvenService() {
		final List<Long> times = Timetable.generate(t("06:00"), t("07:00"), 15 * MINUTE);
		assertEquals(List.of(t("06:00"), t("06:15"), t("06:30"), t("06:45"), t("07:00")), times);
		// Longest gap wraps around midnight: 07:00 back to 06:00 next day.
		assertEquals(23 * 60 * MINUTE, Timetable.longestGap(times));
	}

	@Test
	void nightServiceCrossesMidnight() {
		final List<Long> times = Timetable.normalise(Timetable.generate(t("23:30"), t("00:30"), 30 * MINUTE));
		assertEquals(List.of(t("00:00"), t("00:30"), t("23:30")), times);
	}

	@Test
	void restPeriodAndShift() {
		final List<Long> allDay = Timetable.generate(t("00:00"), t("23:30"), 30 * MINUTE);
		assertEquals(48, allDay.size());
		// Rest from 01:00 to 04:59: no trains in that window.
		final List<Long> rested = Timetable.removeBetween(allDay, t("01:00"), t("04:59"));
		assertEquals(40, rested.size());
		assertEquals(4 * 60 * MINUTE + 30 * MINUTE, Timetable.longestGap(rested));
		// Rest across midnight.
		// Rest across midnight 23:45-00:15 removes only the 00:00 train.
		final List<Long> night = Timetable.removeBetween(allDay, t("23:45"), t("00:15"));
		assertEquals(47, night.size());
		assertFalse(night.contains(t("00:00")));
		assertEquals(List.of(t("00:10"), t("23:50")), Timetable.shift(List.of(t("00:00"), t("23:40")), 10 * MINUTE));
	}

	@Test
	void mergeRemovesDuplicates() {
		assertEquals(List.of(t("06:00"), t("06:30"), t("07:00")), Timetable.merge(List.of(t("06:00"), t("06:30")), List.of(t("06:30"), t("07:00"))));
	}

	@Test
	void generatorIsBounded() {
		assertEquals(Timetable.MAX_DEPARTURES, Timetable.generate(0, Timetable.DAY - 1, 1).size());
		assertTrue(Timetable.generate(0, 10, 0).isEmpty());
	}

	@Test
	void loopTrainsEvenlySpaced() {
		// 3 trains on a 24-minute loop: one every 8 minutes.
		assertEquals(List.of(t("06:00"), t("06:08"), t("06:16")), Timetable.evenlySpaced(t("06:00"), 24 * MINUTE, 3));
		assertTrue(Timetable.evenlySpaced(0, 0, 3).isEmpty());
	}

	@Test
	void frequencyMatchesMtrSlider() {
		assertEquals(4, Timetable.frequencyForGap(60 * MINUTE));
		assertEquals(16, Timetable.frequencyForGap(15 * MINUTE));
		assertEquals(20, Timetable.frequencyForGap(5 * MINUTE), "MTR caps at 5 trains an hour");
		assertEquals(1, Timetable.frequencyForGap(1000 * MINUTE));
		assertEquals(0, Timetable.frequencyForGap(0));
		assertEquals(12 * MINUTE, Timetable.gapForFrequency(20));
		assertEquals(0, Timetable.gapForFrequency(0));
	}

	@Test
	void firstStationHelper() {
		assertEquals(List.of(t("05:57"), t("06:27")), Timetable.departuresForFirstStation(List.of(t("06:00"), t("06:30")), 3 * MINUTE));
		assertEquals(List.of(t("23:58")), Timetable.departuresForFirstStation(List.of(t("00:01")), 3 * MINUTE));
	}
}
