package com.aureliatransit.utilities.perf;

import org.junit.jupiter.api.Test;

import static com.aureliatransit.utilities.perf.TrainLod.Tier.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainLodTest {

	@Test
	void tiersByDistance() {
		assertEquals(FULL, TrainLod.tier(80, false, 5, 10));
		assertEquals(NO_INTERIOR, TrainLod.tier(81, false, 5, 10));
		assertEquals(NO_INTERIOR, TrainLod.tier(160, false, 5, 10));
		assertEquals(BOX, TrainLod.tier(161, false, 5, 10));
	}

	@Test
	void ridingIsAlwaysFull() {
		assertEquals(FULL, TrainLod.tier(500, true, 5, 10));
	}

	@Test
	void zeroTurnsATierOff() {
		assertEquals(NO_INTERIOR, TrainLod.tier(500, false, 5, 0));
		assertEquals(BOX, TrainLod.tier(500, false, 0, 10));
		assertEquals(FULL, TrainLod.tier(500, false, 0, 0));
	}

	@Test
	void boxCloserThanInteriorSkipsTheMiddleTier() {
		assertEquals(BOX, TrainLod.tier(100, false, 8, 4));
		assertEquals(FULL, TrainLod.tier(60, false, 8, 4));
	}
}
