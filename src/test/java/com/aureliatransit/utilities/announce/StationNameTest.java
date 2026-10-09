package com.aureliatransit.utilities.announce;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StationNameTest {

	@Test
	void splitsEnglishCjkAndDropsStationCodes() {
		final StationName name = StationName.of("東京駅|Alpha|AT03");
		assertEquals("Alpha", name.english());
		assertEquals("東京駅", name.cjk());
		assertEquals("alpha", name.slug());
		// ATU's own station codes tool appends "|AT03"; the clip slug must not change because of it.
		assertEquals("alpha", StationName.of("Alpha|AT03").slug());
		assertEquals("Holt", StationName.of("[WIP]|Holt").english());
		assertEquals("Holt", StationName.of("WIP|Holt").english());
	}

	@Test
	void slugsMatchTheRecordedClips() {
		// Same slugs as announce/out/next_<slug>.ogg made by make_announcements.py.
		assertEquals("olympia_high_speed_rail_terminal_1", StationName.of("Olympia High Speed Rail Terminal 1").slug());
		assertEquals("irlo_bronson", StationName.of("Irlo Bronson").slug());
		assertEquals("a_b", StationName.slug("  A--B!! "));
	}

	@Test
	void namesWithoutLatinLettersHaveNoSlug() {
		final StationName cjkOnly = StationName.of("東京駅");
		assertEquals("東京駅", cjkOnly.english());
		assertEquals("", cjkOnly.slug());
		// Cyrillic is neither CJK nor a-z: shown as is, no clip (chime), like onboard.js.
		assertEquals("Москва Курская", StationName.of("Москва Курская").english());
		assertEquals("", StationName.of("Москва Курская").slug());
		assertEquals("", StationName.of(null).english());
	}

	@Test
	void routeNameDropsTheHiddenPart() {
		assertEquals("Red Line|紅線", StationName.routeName("Red Line|紅線||R1"));
		assertEquals("Red Line", StationName.routeName("Red Line"));
		assertEquals("", StationName.routeName(null));
	}
}
