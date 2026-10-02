package com.aureliatransit.utilities.codes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StationCodesTest {

	@Test
	void recognisesCodes() {
		assertTrue(StationCodes.isCode("JY17"));
		assertTrue(StationCodes.isCode("A01"));
		assertTrue(StationCodes.isCode("BL-5"));
		assertFalse(StationCodes.isCode("Shinjuku"));
		assertFalse(StationCodes.isCode("新宿"));
		assertFalse(StationCodes.isCode("jy17"));
		assertFalse(StationCodes.isCode("JY"));
		assertFalse(StationCodes.isCode("ABCDE1"));
		assertEquals("JY", StationCodes.prefixOf("JY17"));
	}

	@Test
	void numbersAlongARoute() {
		assertEquals(List.of("JY01", "JY02", "JY03"), StationCodes.number("jy", 3, 1, 1, 2));
		assertEquals(List.of("JC20", "JC18", "JC16"), StationCodes.number("JC", 3, 20, -2, 2));
		assertEquals(List.of("A5", "A10"), StationCodes.number("A", 2, 5, 5, 1));
		assertTrue(StationCodes.number("JY", 3, 1, -1, 2).isEmpty(), "would go negative");
		assertTrue(StationCodes.number("J1", 3, 1, 1, 2).isEmpty(), "bad prefix");
		assertTrue(StationCodes.number("JY", 2, 9999, 1, 4).isEmpty(), "past 9999");
	}

	@Test
	void addsReplacesAndKeepsOtherLines() {
		assertEquals("新宿|Shinjuku|JY17", StationCodes.withCode("新宿|Shinjuku", "JY17"));
		// Same line again: replaced, not duplicated.
		assertEquals("新宿|Shinjuku|JY18", StationCodes.withCode("新宿|Shinjuku|JY17", "JY18"));
		// Interchange: both lines' codes kept.
		assertEquals("新宿|Shinjuku|JY17|JC05", StationCodes.withCode("新宿|Shinjuku|JY17", "JC05"));
		assertEquals("Alpha|A01", StationCodes.withCode("Alpha", "A01"));
		assertThrows(IllegalArgumentException.class, () -> StationCodes.withCode("Alpha", "nope"));
	}

	@Test
	void removesCodes() {
		assertEquals("新宿|Shinjuku|JC05", StationCodes.withoutCodes("新宿|Shinjuku|JY17|JC05", "JY"));
		assertEquals("新宿|Shinjuku", StationCodes.withoutCodes("新宿|Shinjuku|JY17|JC05", null));
		// A station literally named like a code keeps its name.
		assertEquals("A1", StationCodes.withoutCodes("A1", null));
		assertEquals(List.of("JY17", "JC05"), StationCodes.codesOf("新宿|Shinjuku|JY17|JC05"));
	}
}
