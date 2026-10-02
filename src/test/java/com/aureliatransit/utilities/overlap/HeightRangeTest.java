package com.aureliatransit.utilities.overlap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeightRangeTest {

	@Test
	void blankFieldsMeanNoLimit() {
		final HeightRange range = HeightRange.parse(" ", "").orElseThrow();
		assertEquals(Long.MIN_VALUE, range.bottom());
		assertEquals(Long.MAX_VALUE, range.top());
		assertEquals("any height", range.describe());
		assertEquals("", range.bottomField());
		assertEquals("", range.topField());
	}

	@Test
	void parsesAndDescribes() {
		assertEquals("Y -40 to -20", HeightRange.parse("-40", "-20").orElseThrow().describe());
		assertEquals("Y 10 and up", HeightRange.parse("10", "").orElseThrow().describe());
		assertEquals("up to Y 40", HeightRange.parse("", "40").orElseThrow().describe());
		assertEquals("Y 5 to 5", HeightRange.parse("5", "5").orElseThrow().describe());
	}

	@Test
	void rejectsBadInput() {
		assertTrue(HeightRange.parse("20", "10").isEmpty());
		assertTrue(HeightRange.parse("abc", "").isEmpty());
		assertTrue(HeightRange.parse("1.5", "").isEmpty());
		assertTrue(HeightRange.parse("", "99999999999").isEmpty());
	}

	@Test
	void roundTripsMtrUnboundedZone() {
		final Box mtrDrawn = new Box(0, Long.MIN_VALUE, 0, 10, Long.MAX_VALUE, 10);
		assertEquals("any height", HeightRange.of(mtrDrawn).describe());
		final Box limited = HeightRange.parse("-40", "-20").orElseThrow().applyTo(mtrDrawn);
		assertEquals(new Box(0, -40, 0, 10, -20, 10), limited);
	}

	@Test
	void limitingHeightSeparatesStackedStations() {
		// Overground station 1 and subway station 2 share the same map area; the subway platform belongs to 1 today.
		final Box area = new Box(0, Long.MIN_VALUE, 0, 20, Long.MAX_VALUE, 20);
		final Zones before = new Zones(
				List.of(new Zones.Station(1, area), new Zones.Station(2, area)),
				List.of(new Zones.Platform(100, new Point(10, 70, 10)), new Zones.Platform(200, new Point(10, -30, 10)))
		);
		assertEquals(1L, before.assignment().get(200L));
		final Zones after = before.withBox(1, HeightRange.parse("0", "").orElseThrow().applyTo(area));
		assertEquals(1L, after.assignment().get(100L));
		assertEquals(2L, after.assignment().get(200L));
		assertEquals(1, before.changesTo(after).size());
	}
}
