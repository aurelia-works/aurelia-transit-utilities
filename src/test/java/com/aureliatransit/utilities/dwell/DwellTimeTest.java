package com.aureliatransit.utilities.dwell;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class DwellTimeTest {

	private static long parse(String text) {
		final OptionalLong value = DwellTime.parseMillis(text);
		assertTrue(value.isPresent(), text);
		return value.getAsLong();
	}

	@Test
	void parsesCommonForms() {
		assertEquals(30_000, parse("30"));
		assertEquals(30_000, parse(" 30s "));
		assertEquals(12_500, parse("12.5"));
		assertEquals(90_000, parse("1:30"));
		assertEquals(90_000, parse("1m30"));
		assertEquals(90_000, parse("1m30s"));
		assertEquals(120_000, parse("2m"));
		assertEquals(65_500, parse("1:05.5"));
		assertEquals(600_000, parse("10:00"));
		assertEquals(500, parse("0.5"));
	}

	@Test
	void roundsToHalfSeconds() {
		assertEquals(12_500, parse("12.3"));
		assertEquals(12_000, parse("12.2"));
		assertEquals(500, parse("0.3"));
	}

	@Test
	void rejectsOutOfRangeAndGarbage() {
		for (final String text : new String[]{"0", "0.2", "10:00.5", "601", "11m", "1:60", "-5", "abc", "", "1:2:3", null}) {
			assertTrue(DwellTime.parseMillis(text).isEmpty(), String.valueOf(text));
		}
	}

	@Test
	void adjustClampsToMtrRange() {
		assertEquals(35_000, DwellTime.adjust(30_000, 5_000));
		assertEquals(500, DwellTime.adjust(3_000, -5_000));
		assertEquals(600_000, DwellTime.adjust(598_000, 5_000));
		// Odd values stored by other tools are snapped to MTR's half-second grid.
		assertEquals(10_000, DwellTime.clamp(10_123));
	}

	@Test
	void formats() {
		assertEquals("0.5 s", DwellTime.format(500));
		assertEquals("45 s", DwellTime.format(45_000));
		assertEquals("12.5 s", DwellTime.format(12_500));
		assertEquals("1:00", DwellTime.format(60_000));
		assertEquals("2:05.5", DwellTime.format(125_500));
		assertEquals("10:00", DwellTime.format(600_000));
	}
}
